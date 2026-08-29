package com.hhy.dreamingfishcore.gameplay.task_location_system.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcManager;
import com.hhy.dreamingfishcore.gameplay.task_location_system.BuildableTerritoryPolicy;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationDefinition;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationManager;
import com.hhy.dreamingfishcore.gameplay.task_location_system.network.Packet_SyncTaskLocationHud;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.notice_system.NotificationPushHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.portal.PortalShape;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.util.TriState;
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
     * Protected task locations use Adventure as the first protection layer; buildable locations
     * remain in Survival and only apply the narrow TNT/mob-explosion/lava/flint policy.
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

            if (justEntered) {
                DreamingFishCore_NetworkManager.sendToClient(
                        player, Packet_SyncTaskLocationHud.show(currentLocation));
            }

            if (currentLocation.forcesAdventure() && gameType == GameType.SURVIVAL) {
                player.setGameMode(GameType.ADVENTURE);
                FORCED_ADVENTURE_PLAYERS.add(playerId);
                NotificationPushHelper.sendCenterTopNotification(
                        player, currentLocation.getName(), "已切换冒险模式", 5000);
            } else if (!currentLocation.forcesAdventure()) {
                // A player may walk directly from a protected location into a buildable one. Keep
                // the documented BUILDABLE contract (Survival) even when the Adventure transition
                // happened before this tick or the player was restored from a stale login state.
                if (gameType == GameType.ADVENTURE) {
                    player.setGameMode(GameType.SURVIVAL);
                }
                FORCED_ADVENTURE_PLAYERS.remove(playerId);
                if (justEntered) {
                    NotificationPushHelper.sendCenterTopNotification(
                            player, currentLocation.getName(),
                            "可建造区域：可建设、可在此圈私人领地（禁 TNT、生物爆炸和岩浆；打火石仅可用于点燃下界传送门）", 7000);
                }
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

        String previousLocationId = PLAYER_LOCATIONS.remove(playerId);
        if (previousLocationId != null) {
            DreamingFishCore_NetworkManager.sendToClient(
                    player, Packet_SyncTaskLocationHud.hide());
            // Only restore a mode that this system changed. A player who entered while already
            // in Adventure, or who changed modes manually, must not be overwritten on exit.
            if (FORCED_ADVENTURE_PLAYERS.remove(playerId)
                    && player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) {
                player.setGameMode(GameType.SURVIVAL);
                NotificationPushHelper.sendTopLeftNotification(
                        player, "§7已离开剧情区域§r\n已恢复生存模式。", 4000);
            }
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
        BuildableTerritoryPolicy.clear(event.getServer());
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
        BuildableTerritoryPolicy.clear(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSelectFirstPoint(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasPermissions(3)) {
            return;
        }
        // The EconomySystem claim wand owns its own right-click selection protocol. Do not let an
        // administrator's task-location selection session consume that wand's clicks.
        if (BuildableTerritoryPolicy.isClaimWand(player.getItemInHand(event.getHand()))) {
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
        if (BuildableTerritoryPolicy.isClaimWand(player.getItemInHand(event.getHand()))) {
            return;
        }
        if (TaskLocationManager.selectSecondPoint(player, event.getPos())) {
            event.setCanceled(true);
        }
    }

    /** 观察 EconomySystem 圈地杖选点：故事区外放行，可建造区提醒，保护区交由确认门禁拦截。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEconomyClaimWandUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!BuildableTerritoryPolicy.allowClaimWandClick(
                player, event.getHand(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    /** 防止直接绕过选点流程确认 EconomySystem 领地。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEconomyClaimConfirmation(CommandEvent event) {
        BuildableTerritoryPolicy.onCommand(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || canBypass(player)) {
            return;
        }
        if (isBlockProtected(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§c该方块属于任务地点，不能破坏。"), true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        boolean taskLocation = isTaskLocation(event.getLevel(), event.getPos());
        boolean blockProtected = isBlockProtected(event.getLevel(), event.getPos());
        boolean hazardousPlacement = BuildableTerritoryPolicy.isTntBlock(event.getPlacedBlock())
                || BuildableTerritoryPolicy.isLavaBlock(event.getPlacedBlock())
                || (event.getEntity() instanceof ServerPlayer player
                && (BuildableTerritoryPolicy.isTntTool(heldItem(player))
                || BuildableTerritoryPolicy.isLavaBucket(heldItem(player))));

        // TNT and lava are the only cross-mode hazards. Creative bypasses ordinary PROTECTED
        // block edits, but it does not bypass this explicit hazard rule.
        if (taskLocation && hazardousPlacement) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§c任务地点内禁止放置 TNT 和岩浆。"), true);
            }
        } else if (blockProtected
                && !(event.getEntity() instanceof ServerPlayer player && canBypass(player))) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§c任务地点内不能放置方块。"), true);
            }
        }
    }

    /** Reject lava placement/flow in either mode; water and other fluids remain available in
     * BUILDABLE locations. PROTECTED still relies on Adventure for player block edits. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }
        BlockPos liquidPos = event.getLiquidPos();
        boolean lavaFlow = BuildableTerritoryPolicy.isLavaFluid(
                event.getNewState().getFluidState())
                || BuildableTerritoryPolicy.isLavaFluid(
                event.getOriginalState().getFluidState())
                || (liquidPos != null && BuildableTerritoryPolicy.isLavaFluid(
                level.getFluidState(liquidPos)));
        if (lavaFlow && (isTaskLocation(level, event.getPos())
                || (liquidPos != null && isTaskLocation(level, liquidPos)))) {
            // A pre-existing source cannot cross into or out of a task location.
            event.setNewState(event.getOriginalState());
        }
    }

    /** Stop TNT and mob explosions in every active task location. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosionStart(ExplosionEvent.Start event) {
        Level level = event.getLevel();
        Explosion explosion = event.getExplosion();
        BlockPos center = BlockPos.containing(explosion.center());
        boolean tntExplosion = BuildableTerritoryPolicy.isTntExplosion(explosion);
        boolean mobExplosion = !tntExplosion
                && BuildableTerritoryPolicy.isMobExplosion(explosion);
        if (isExplosionBlockedAt(level, center, tntExplosion, mobExplosion)) {
            event.setCanceled(true);
        }
    }

    /** Remove TNT and mob effects that would enter any active task location. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Explosion explosion = event.getExplosion();
        boolean tntExplosion = BuildableTerritoryPolicy.isTntExplosion(explosion);
        boolean mobExplosion = !tntExplosion
                && BuildableTerritoryPolicy.isMobExplosion(explosion);
        if (!tntExplosion && !mobExplosion) {
            // NPCs and authored decorations are still protected by the entity handlers below;
            // ordinary non-TNT explosions are intentionally not filtered here.
            return;
        }
        event.getAffectedBlocks().removeIf(position ->
                isExplosionBlockedAt(event.getLevel(), position, tntExplosion, mobExplosion));
        event.getAffectedEntities().removeIf(entity ->
                entity != null && isExplosionBlockedAt(
                        entity.level(), entity.blockPosition(), tntExplosion, mobExplosion));
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
        if (isStoryProtected(target.level(), target.blockPosition())) {
            event.setCanceled(true);
        }
    }

    /** 枪械、投射物和怪物攻击最终都会经过伤害事件，剧情 NPC 因此不会被意外杀死。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onNpcAttacked(LivingIncomingDamageEvent event) {
        Entity target = event.getEntity();
        if (target.getPersistentData().contains(NpcManager.ENTITY_NPC_ID_TAG)
                && isStoryProtected(target.level(), target.blockPosition())) {
            event.setCanceled(true);
        }
    }

    /** Blocks TNT items, lava buckets, and air-use of flint and steel in every active location. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDestructiveItemUse(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !isTaskLocation(player.serverLevel(), player.blockPosition())) {
            return;
        }
        boolean flintAndSteel = BuildableTerritoryPolicy.isFlintAndSteel(event.getItemStack());
        boolean tnt = BuildableTerritoryPolicy.isTntTool(event.getItemStack());
        boolean lava = BuildableTerritoryPolicy.isLavaBucket(event.getItemStack());
        if (!flintAndSteel && !tnt && !lava) {
            return;
        }
        event.setCanceled(true);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                flintAndSteel
                        ? "§c任务地点内打火石仅可用于点燃下界传送门。"
                        : "§c任务地点内禁止使用 TNT 和放置岩浆。"), true);
    }

    /**
     * Reserve flint and steel in task locations for the one allowed use: creating a Nether portal.
     * The shape check mirrors vanilla's {@link BaseFireBlock} portal path, so ordinary fire,
     * campfires, candles, and other flint-and-steel actions are denied while a real portal
     * ignition is allowed through unchanged.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFlintAndSteelUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !BuildableTerritoryPolicy.isFlintAndSteel(event.getItemStack())
                || event.getFace() == null) {
            return;
        }

        Level level = player.serverLevel();
        BlockPos clickedPos = event.getPos();
        BlockPos ignitionPos = clickedPos.relative(event.getFace());
        if (!isTaskLocation(level, clickedPos) && !isTaskLocation(level, ignitionPos)) {
            return;
        }

        UseOnContext context = new UseOnContext(player, event.getHand(), event.getHitVec());
        if (wouldCreateNetherPortal(level, clickedPos, ignitionPos, context)) {
            return;
        }

        // Disable only the item branch. Keeping block use enabled means a chest, bookshelf, or
        // other ordinary block can still handle the same right-click while the flint itself is
        // prevented from creating ordinary fire.
        event.setUseItem(TriState.FALSE);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§c任务地点内打火石仅可用于点燃下界传送门。"), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDestructiveBlockUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || !isTaskLocation(event.getLevel(), event.getPos())) {
            return;
        }
        ItemStack held = player.getItemInHand(event.getHand());
        boolean targetsTnt = BuildableTerritoryPolicy.isTntBlock(
                event.getLevel().getBlockState(event.getPos()));
        if (BuildableTerritoryPolicy.isClaimWand(held)
                || (!targetsTnt && !BuildableTerritoryPolicy.isTntTool(held)
                && !BuildableTerritoryPolicy.isLavaBucket(held))) {
            return;
        }
        // Cancel the event as well as setting both use states. Some item implementations ignore
        // TriState.FALSE and continue from their own interaction hook; cancellation is the final
        // server-side guard against placing TNT or pouring a bucket in a task location.
        event.setCanceled(true);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§c任务地点内仅禁止使用 TNT 和放置岩浆。"), true);
    }

    /** 拦截模组提供的 TNT 工具；两种地点都遵守 TNT 禁令。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTaskLocationToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !isTaskLocation(event.getLevel(), event.getPos())
                || !BuildableTerritoryPolicy.isTntTool(event.getHeldItemStack())) {
            return;
        }
        event.setCanceled(true);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§c任务地点内禁止使用 TNT 工具。"), true);
    }

    private static boolean canBypass(ServerPlayer player) {
        return player.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
    }

    private static boolean isBlockProtected(Object level, BlockPos position) {
        return level instanceof Level actualLevel
                && TaskLocationManager.isBlockProtected(actualLevel, position);
    }

    private static boolean isStoryProtected(Object level, BlockPos position) {
        return level instanceof Level actualLevel
                && TaskLocationManager.isStoryStructureProtected(actualLevel, position);
    }

    private static boolean isTaskLocation(Object level, BlockPos position) {
        return level instanceof Level actualLevel
                && TaskLocationManager.findLocationAt(actualLevel, position).isPresent();
    }

    private static boolean isExplosionBlockedAt(
            Level level, BlockPos position, boolean tntExplosion, boolean mobExplosion) {
        return (tntExplosion || mobExplosion)
                && isTaskLocation(level, position);
    }

    private static boolean wouldCreateNetherPortal(
            Level level, BlockPos clickedPos, BlockPos ignitionPos, UseOnContext context) {
        // FlintAndSteelItem uses the tool-modification branch for campfires, candles, and other
        // blocks. Those actions do not call BaseFireBlock.onPlace and cannot create a portal.
        if (level.dimension() != Level.OVERWORLD && level.dimension() != Level.NETHER) {
            return false;
        }
        if (level.getBlockState(clickedPos).getToolModifiedState(
                context, ItemAbilities.FIRESTARTER_LIGHT, true) != null) {
            return false;
        }
        return BaseFireBlock.canBePlacedAt(level, ignitionPos, context.getHorizontalDirection())
                && PortalShape.findEmptyPortalShape(level, ignitionPos, Direction.Axis.X).isPresent();
    }

    private static ItemStack heldItem(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        return main.isEmpty() ? player.getOffhandItem() : main;
    }
}
