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
     * remain in Survival and only apply the narrow TNT/lava/portal-ignition policy.
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
                            "可建造区域：可建设、可在此圈私人领地（仅禁止岩浆、TNT 和传送门点火）", 7000);
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

    /** Stop TNT everywhere inside a task location. Other explosions keep vanilla behavior. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosionStart(ExplosionEvent.Start event) {
        Level level = event.getLevel();
        BlockPos center = BlockPos.containing(event.getExplosion().center());
        if (isTaskLocation(level, center)
                && BuildableTerritoryPolicy.isTntExplosion(event.getExplosion())) {
            event.setCanceled(true);
        }
    }

    /** Remove TNT effects that would enter a task location from outside. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        boolean tntExplosion = BuildableTerritoryPolicy.isTntExplosion(event.getExplosion());
        if (!tntExplosion) {
            // NPCs and authored decorations are still protected by the entity handlers below;
            // ordinary non-TNT explosions are intentionally not filtered here.
            return;
        }
        event.getAffectedBlocks().removeIf(position ->
                isTaskLocation(event.getLevel(), position));
        event.getAffectedEntities().removeIf(entity ->
                isTaskLocation(entity.level(), entity.blockPosition()));
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

    /** Blocks TNT items and lava buckets in every active task location. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDestructiveItemUse(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !isTaskLocation(player.serverLevel(), player.blockPosition())
                || (!BuildableTerritoryPolicy.isTntTool(event.getItemStack())
                && !BuildableTerritoryPolicy.isLavaBucket(event.getItemStack()))) {
            return;
        }
        event.setCanceled(true);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§c任务地点内仅禁止使用 TNT 和放置岩浆。"), true);
    }

    /**
     * Allow ordinary fire-starting, but do not let flint and steel create a Nether portal inside a
     * task location. The shape check mirrors vanilla's {@link BaseFireBlock} portal path, so a
     * flint-and-steel click on a campfire, candle, or ordinary flammable block is left untouched.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFlintAndSteelPortalIgnition(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !BuildableTerritoryPolicy.isFlintAndSteel(event.getItemStack())
                || event.getFace() == null) {
            return;
        }

        Level level = player.serverLevel();
        if (!isNetherPortalDimension(level)) {
            return;
        }

        UseOnContext context = new UseOnContext(player, event.getHand(), event.getHitVec());
        BlockPos clickedPos = event.getPos();
        // FlintAndSteelItem uses the tool-modification branch for campfires, candles, and other
        // blocks. Those actions do not call BaseFireBlock.onPlace and therefore cannot create a
        // Nether portal.
        if (level.getBlockState(clickedPos).getToolModifiedState(
                context, ItemAbilities.FIRESTARTER_LIGHT, true) != null) {
            return;
        }

        BlockPos ignitionPos = clickedPos.relative(event.getFace());
        if (!BaseFireBlock.canBePlacedAt(level, ignitionPos, context.getHorizontalDirection())
                || PortalShape.findEmptyPortalShape(level, ignitionPos, Direction.Axis.X).isEmpty()) {
            return;
        }

        // Check both the clicked frame and the new fire position. This also handles a frame on a
        // task-location boundary where the player is standing just outside the configured box.
        if (!isTaskLocation(level, clickedPos) && !isTaskLocation(level, ignitionPos)) {
            return;
        }

        event.setCanceled(true);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§c任务地点内不能点燃下界传送门。"), true);
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

    private static boolean isBuildable(Object level, BlockPos position) {
        return level instanceof Level actualLevel
                && TaskLocationManager.isBuildable(actualLevel, position);
    }

    private static boolean isStoryProtected(Object level, BlockPos position) {
        return level instanceof Level actualLevel
                && TaskLocationManager.isStoryStructureProtected(actualLevel, position);
    }

    private static boolean isTaskLocation(Object level, BlockPos position) {
        return level instanceof Level actualLevel
                && TaskLocationManager.findLocationAt(actualLevel, position).isPresent();
    }

    private static boolean isNetherPortalDimension(Level level) {
        return level.dimension() == Level.OVERWORLD || level.dimension() == Level.NETHER;
    }

    private static ItemStack heldItem(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        return main.isEmpty() ? player.getOffhandItem() : main;
    }
}
