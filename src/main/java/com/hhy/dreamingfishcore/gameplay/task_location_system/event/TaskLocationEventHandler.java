package com.hhy.dreamingfishcore.gameplay.task_location_system.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcManager;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationDefinition;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationManager;
import com.hhy.dreamingfishcore.server.notice_system.NotificationPushHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Forge 事件层：负责选区点击和任务地点的基础地图保护。 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class TaskLocationEventHandler {
    /** 只记录由任务地点系统主动切换过的玩家，离开时才能安全恢复生存模式。 */
    private static final Set<UUID> FORCED_ADVENTURE_PLAYERS = ConcurrentHashMap.newKeySet();
    /** 记录玩家当前所在地点，用于让所有游戏模式只在进入时收到一次场景提示。 */
    private static final Map<UUID, String> PLAYER_LOCATIONS = new ConcurrentHashMap<>();

    private TaskLocationEventHandler() {
    }

    /**
     * 任务地点采用冒险模式作为第一层低成本保护。
     *
     * <p>只根据当前游戏模式判断：任何生存玩家都会切换，不因 OP 权限绕过；
     * 创造、旁观和玩家自己选择的冒险模式保持不变。
     * 现有方块/实体事件保护仍保留，用于处理模组方块、爆炸和非标准破坏路径。</p>
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.level().isClientSide()) {
            return;
        }

        TaskLocationDefinition currentLocation = TaskLocationManager.findLocationAt(
                player.serverLevel(), player.blockPosition()).orElse(null);
        boolean insideLocation = currentLocation != null;
        UUID playerId = player.getUUID();

        if (insideLocation) {
            String previousLocationId = PLAYER_LOCATIONS.put(playerId, currentLocation.getId());
            boolean justEntered = !currentLocation.getId().equals(previousLocationId);
            GameType gameType = player.gameMode.getGameModeForPlayer();

            if (gameType == GameType.SURVIVAL) {
                player.setGameMode(GameType.ADVENTURE);
                FORCED_ADVENTURE_PLAYERS.add(playerId);
                NotificationPushHelper.sendCenterTopNotification(
                        player, currentLocation.getName(), "已切换冒险模式", 5000);
            } else if (justEntered && gameType == GameType.CREATIVE) {
                NotificationPushHelper.sendCenterTopNotification(
                        player, currentLocation.getName(), "您处于创造模式，所以不切换", 5000);
            } else if (justEntered && gameType == GameType.SPECTATOR) {
                NotificationPushHelper.sendCenterTopNotification(
                        player, currentLocation.getName(), "您处于旁观模式，所以不切换", 5000);
            } else if (justEntered && gameType == GameType.ADVENTURE) {
                NotificationPushHelper.sendCenterTopNotification(
                        player, currentLocation.getName(), "当前已是冒险模式", 5000);
            }
            return;
        }

        PLAYER_LOCATIONS.remove(playerId);
        if (FORCED_ADVENTURE_PLAYERS.remove(playerId)
                && player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) {
            player.setGameMode(GameType.SURVIVAL);
            NotificationPushHelper.sendTopLeftNotification(
                    player, "§7已离开剧情区域§r\n已恢复生存模式。", 4000);
        }
    }

    /** 停服前恢复被系统接管的玩家，避免冒险模式被保存成永久状态。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (FORCED_ADVENTURE_PLAYERS.remove(player.getUUID())
                    && player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) {
                player.setGameMode(GameType.SURVIVAL);
            }
        }
        FORCED_ADVENTURE_PLAYERS.clear();
        PLAYER_LOCATIONS.clear();
    }

    /** 退出前先恢复原本模式，避免登录系统把临时冒险模式持久化。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PLAYER_LOCATIONS.remove(player.getUUID());
        if (FORCED_ADVENTURE_PLAYERS.remove(player.getUUID())
                && player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) {
            player.setGameMode(GameType.SURVIVAL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSelectFirstPoint(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasPermissions(3)) {
            return;
        }
        if (TaskLocationManager.selectFirstPoint(player, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSelectSecondPoint(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)
                || !player.hasPermissions(3)) {
            return;
        }
        if (TaskLocationManager.selectSecondPoint(player, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || canBypass(player)) {
            return;
        }
        if (isProtected(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§c该方块属于任务地点，不能破坏。"), true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && canBypass(player)) {
            return;
        }
        if (isProtected(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§c任务地点内不能放置方块。"), true);
            }
        }
    }

    /** 只拦截容器，普通门、按钮和剧情设备仍可以按内容设计正常交互。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onContainerUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer player)
                || canBypass(player)) {
            return;
        }
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof Container
                && TaskLocationManager.isProtected(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§c该容器属于任务地点，不能打开。"), true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (isProtected(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    /** 液体尝试替换任务地点方块时，把结果改回原方块。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (isProtected(event.getLevel(), event.getPos())) {
            event.setNewState(event.getOriginalState());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPistonMove(PistonEvent.Pre event) {
        if (isProtected(event.getLevel(), event.getPos())
                || isProtected(event.getLevel(), event.getFaceOffsetPos())) {
            event.setCanceled(true);
        }
    }

    /** 只过滤爆炸影响的地图方块，保留对敌对怪物和玩家的战斗伤害。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(position ->
                TaskLocationManager.isProtected(event.getLevel(), position));
        event.getAffectedEntities().removeIf(entity ->
                entity.getPersistentData().contains(NpcManager.ENTITY_NPC_ID_TAG)
                        && TaskLocationManager.isProtected(entity.level(), entity.blockPosition()));
    }

    /** 允许攻击敌对怪物，只拦截任务地点内的剧情 NPC 和易损装饰实体。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttackEntity(AttackEntityEvent event) {
        Entity target = event.getTarget();
        if (!(event.getEntity() instanceof ServerPlayer player) || canBypass(player)
                || target instanceof Enemy
                || !(target instanceof ArmorStand
                || target instanceof HangingEntity
                || target.getPersistentData().contains(NpcManager.ENTITY_NPC_ID_TAG))) {
            return;
        }
        if (TaskLocationManager.isProtected(target.level(), target.blockPosition())) {
            event.setCanceled(true);
        }
    }

    /** 枪械、投射物和怪物攻击最终都会经过伤害事件，剧情 NPC 因此不会被意外杀死。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onNpcAttacked(LivingIncomingDamageEvent event) {
        Entity target = event.getEntity();
        if (target.getPersistentData().contains(NpcManager.ENTITY_NPC_ID_TAG)
                && TaskLocationManager.isProtected(target.level(), target.blockPosition())) {
            event.setCanceled(true);
        }
    }

    /** 尊重模组通过 Forge 查询的怪物破坏行为，包括搭路、破门和搬动方块。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (TaskLocationManager.isProtected(entity.level(), entity.blockPosition())) {
            event.setCanGrief(false);
        }
    }

    private static boolean canBypass(ServerPlayer player) {
        return player.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
    }

    private static boolean isProtected(Object level, BlockPos position) {
        return level instanceof Level actualLevel
                && TaskLocationManager.isProtected(actualLevel, position);
    }
}
