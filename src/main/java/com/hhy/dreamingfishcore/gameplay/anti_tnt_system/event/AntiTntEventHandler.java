package com.hhy.dreamingfishcore.gameplay.anti_tnt_system.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 建筑服简单防护规则集合（集中在一个文件里方便维护）。
 *
 * <p>目前包含两条规则：
 * <ol>
 *   <li><b>反 TNT</b>：任何形式的 TNT 使用都把玩家踢下线，提示“嘿嘿你要干嘛”。</li>
 *   <li><b>矿物玩笑提醒</b>：玩家主手持有非煤矿物（原矿/锭/粗矿/宝石/粒/矿物方块）时，
 *       发一句“再拿 X 个就ban你”的玩笑话，实际不会 ban。</li>
 * </ol>
 *
 * <p>管理员（权限等级 &gt;= 2）豁免上述所有规则，方便服主测试与维护。
 * 如需对所有人生效，删除 canBypass 判断即可。</p>
 */
@Mod.EventBusSubscriber(modid = DreamingFishCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AntiTntEventHandler {

    /** 踢人提示语。§c 是红色样式码。 */
    private static final String KICK_MESSAGE = "§c嘿嘿你要干嘛";

    /** 矿物玩笑提醒冷却（玩家UUID -> 下次允许提醒的游戏时间tick），避免每 tick 刷屏。 */
    private static final Map<UUID, Long> ORE_WARN_COOLDOWN = new ConcurrentHashMap<>();

    /** 矿物提醒间隔：5 秒 = 100 tick。 */
    private static final long ORE_WARN_COOLDOWN_TICKS = 100L;

    private AntiTntEventHandler() {
    }

    /**
     * 每 tick 统一处理玩家手持物品检查。
     *
     * <p>只在服务端、tick 末尾（END 阶段）检查一次。
     * 先做反 TNT 检测（持有即踢），未踢再检查矿物玩笑提醒。</p>
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)
                || player.level().isClientSide()) {
            return;
        }
        if (canBypass(player)) {
            return;
        }
        // 反 TNT：主手或副手持有 TNT 直接踢
        if (isTnt(player.getMainHandItem()) || isTnt(player.getOffhandItem())) {
            punish(player);
            return;
        }
        // 矿物玩笑提醒：只看主手
        warnIfHoldingValuableOre(player);
    }

    // ==================== 反 TNT ====================

    /**
     * 右键点击方块时拦截 TNT 放置/点燃，避免同一 tick 内 TNT 已被放下后才被 tick 检测到。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        handleUse(event);
    }

    /**
     * 右键点击空气时拦截 TNT 使用（保险，TNT 在空中右键一般无效果，但仍拦截）。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        handleUse(event);
    }

    /** 统一处理右键使用 TNT：取消事件并踢人。 */
    private static void handleUse(PlayerInteractEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || canBypass(player)) {
            return;
        }
        InteractionHand hand = event.getHand();
        boolean holdingTnt = (hand == InteractionHand.MAIN_HAND && isTnt(player.getMainHandItem()))
                || (hand == InteractionHand.OFF_HAND && isTnt(player.getOffhandItem()));
        if (holdingTnt) {
            event.setCanceled(true);
            punish(player);
        }
    }

    /**
     * 放置 TNT 方块时兜底拦截（例如其它模组/机制绕过了右键事件直接放下方块）。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || canBypass(player)) {
            return;
        }
        if (event.getState().getBlock() == Blocks.TNT) {
            event.setCanceled(true);
            punish(player);
        }
    }

    /** 判断物品栈是否是 TNT。 */
    private static boolean isTnt(ItemStack stack) {
        return stack.is(Items.TNT);
    }

    /**
     * 清除玩家主/副手的 TNT 后将其踢下线。
     *
     * <p>清除主/副手是为了避免玩家重新登录时手里仍持有 TNT 而被再次踢出，
     * 形成永远进不去服务器的死循环。背包里未拿在手上的 TNT 不会触发踢人，故不处理。</p>
     */
    private static void punish(ServerPlayer player) {
        if (isTnt(player.getMainHandItem())) {
            player.getMainHandItem().setCount(0);
        }
        if (isTnt(player.getOffhandItem())) {
            player.getOffhandItem().setCount(0);
        }
        DreamingFishCore.LOGGER.info("[AntiTNT] 玩家 {} 试图使用 TNT，已被踢出。",
                player.getName().getString());
        player.connection.disconnect(Component.literal(KICK_MESSAGE));
    }

    // ==================== 矿物玩笑提醒 ====================

    /**
     * 玩家主手持有非煤矿物时，发一句“再拿 X 个就ban你”的玩笑话（实际不ban）。
     *
     * <p>用冷却避免每 tick 重复发送刷屏。X 为随机 1~50，每次提醒不同，增加趣味。
     * 消息发到 actionbar（屏幕中下方提示条），不打扰聊天记录。</p>
     */
    private static void warnIfHoldingValuableOre(ServerPlayer player) {
        ItemStack hand = player.getMainHandItem();
        if (!isValuableOre(hand)) {
            return;
        }
        long now = player.level().getGameTime();
        Long nextWarn = ORE_WARN_COOLDOWN.get(player.getUUID());
        if (nextWarn != null && now < nextWarn) {
            return; // 还在冷却中
        }
        ORE_WARN_COOLDOWN.put(player.getUUID(), now + ORE_WARN_COOLDOWN_TICKS);

        int left = 1 + ThreadLocalRandom.current().nextInt(50); // 1~50
        player.displayClientMessage(Component.literal(
                "§e⚠ 检测到矿物 §f" + hand.getHoverName().getString()
                        + "§e！再拿 §c" + left + "§e 个就ban你！（开玩笑的啦~）"), true);
    }

    /**
     * 判断物品栈是否为“有价值的矿物”（除煤以外）。
     *
     * <p>覆盖：原矿、锭、粗矿、宝石、粒（用 Forge 通用 tag，可覆盖原版与模组），
     * 以及矿物方块和红石/下界合金散项（用具体物品列表，避免误判干草块、黏液块等非矿物存储块）。</p>
     */
    private static boolean isValuableOre(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        // 煤相关全部排除（煤、煤矿石、深板岩煤矿石、煤块）
        if (item == Items.COAL
                || item == Items.COAL_ORE
                || item == Items.DEEPSLATE_COAL_ORE
                || item == Items.COAL_BLOCK) {
            return false;
        }
        // 原矿 / 锭 / 粗矿 / 宝石 / 粒：用 Forge 通用 tag，覆盖模组矿物
        if (stack.is(Tags.Items.ORES)
                || stack.is(Tags.Items.INGOTS)
                || stack.is(Tags.Items.RAW_MATERIALS)
                || stack.is(Tags.Items.GEMS)
                || stack.is(Tags.Items.NUGGETS)) {
            return true;
        }
        // 矿物方块 + 散项矿物（用具体列表，避免误伤建筑常用方块）
        return item == Items.IRON_BLOCK
                || item == Items.GOLD_BLOCK
                || item == Items.DIAMOND_BLOCK
                || item == Items.EMERALD_BLOCK
                || item == Items.REDSTONE_BLOCK
                || item == Items.LAPIS_BLOCK
                || item == Items.COPPER_BLOCK
                || item == Items.NETHERITE_BLOCK
                || item == Items.RAW_IRON_BLOCK
                || item == Items.RAW_COPPER_BLOCK
                || item == Items.RAW_GOLD_BLOCK
                || item == Items.REDSTONE
                || item == Items.NETHERITE_SCRAP
                || item == Items.ANCIENT_DEBRIS;
    }

    // ==================== 公共逻辑 ====================

    /** 管理员（权限等级 >= 2）豁免。 */
    private static boolean canBypass(ServerPlayer player) {
        return player.hasPermissions(2);
    }

    /** 玩家退出时清理矿物提醒冷却，避免 map 无限增长。 */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ORE_WARN_COOLDOWN.remove(player.getUUID());
        }
    }
}
