package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.PendingDeathData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.compat.CorpseAccessoryCompat;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.compat.CorpseAccessoryEntry;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.event.DeathEventHandler;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 接管玩家死亡掉落并将其写入尸体实体。 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class DeathCorpseManager {
    private static final Map<UUID, CaptureContext> CAPTURES = new HashMap<>();
    private static final Map<UUID, CorpseLocationNotice> RESPAWN_LOCATION_NOTICES = new HashMap<>();

    private DeathCorpseManager() {
    }

    /**
     * 在其他死亡监听器修改物品前记录原槽位。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeathStart(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        // 旁观只是登录前的显示状态，不是权限边界；未认证会话不得进入尸体结算链。
        if (!AuthSessionGuard.isAuthenticated(player)) {
            return;
        }
        CAPTURES.computeIfAbsent(player.getUUID(), ignored -> CaptureContext.from(player));
    }

    /**
     * 在所有死亡掉落均已生成后接管集合。清空集合前必须先确认尸体已加入世界，
     * 从而避免实体注册或生成异常时吞掉玩家物品。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPlayerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        CaptureContext context = CAPTURES.get(player.getUUID());
        if (context == null || context.processed) {
            return;
        }

        CorpsePlacement placement = resolvePlacement(context);
        DeathCorpseEntity corpse = DeathCorpseEntities.DEATH_CORPSE.get().create(placement.level());
        if (corpse == null) {
            DreamingFishCore.LOGGER.error("无法为玩家 {} 创建尸体实体，保留原死亡掉落",
                    player.getScoreboardName());
            return;
        }

        corpse.initialize(
                context.corpseId,
                context.ownerId,
                context.ownerName,
                new DeathCorpseInventory(),
                placement.x(),
                placement.y(),
                placement.z(),
                context.yRot,
                !context.awaitingChoice,
                true,
                placement.recoveryX(),
                placement.recoveryY(),
                placement.recoveryZ(),
                placement.dangerRelocated());

        if (!placement.level().addFreshEntity(corpse)) {
            DreamingFishCore.LOGGER.error("玩家 {} 的尸体未能加入世界，保留原死亡掉落",
                    player.getScoreboardName());
            return;
        }

        List<net.minecraft.world.entity.item.ItemEntity> originalDrops = new ArrayList<>(event.getDrops());
        List<CorpseAccessoryEntry> originalAccessories = new ArrayList<>(context.accessoryItems.size());
        for (CorpseAccessoryEntry entry : context.accessoryItems) {
            originalAccessories.add(entry.copy());
        }
        try {
            CorpseAccessoryCompat.reconcile(player, event, context.accessoryItems);
            context.inventory.setAccessoryItems(context.accessoryItems);
            context.inventory.processDrops(event.getDrops());
            corpse.setCorpseInventory(context.inventory);
        } catch (RuntimeException | LinkageError exception) {
            event.getDrops().clear();
            event.getDrops().addAll(originalDrops);
            for (CorpseAccessoryEntry entry : originalAccessories) {
                if (!isRepresentedByDrops(originalDrops, entry.stack())) {
                    CorpseAccessoryCompat.restore(player, entry);
                }
            }
            corpse.discard();
            DreamingFishCore.LOGGER.error("玩家 {} 的尸体物品结算失败，已恢复原死亡掉落",
                    player.getScoreboardName(), exception);
            return;
        }

        boolean hadItems = !corpse.isEmpty();
        event.getDrops().clear();
        context.processed = true;
        PendingDeathData.DeathLocation corpseLocation = locationOf(corpse);
        PendingDeathData.markCorpseCreated(
                player,
                context.corpseId,
                hadItems,
                corpseLocation,
                placement.dangerRelocated());
        if (context.awaitingChoice) {
            // 首个死亡数据包会先让界面出现；尸体生成后立即用其实际（含危险迁移）位置刷新。
            DeathEventHandler.refreshDeathScreenData(player);
        }
        CAPTURES.remove(player.getUUID());

        DreamingFishCore.LOGGER.info("已在 {} 的 {} {} {} 为玩家 {} 创建尸体（物品={}，危险迁移={}）",
                corpseLocation.dimension(), (int) corpseLocation.x(), (int) corpseLocation.y(),
                (int) corpseLocation.z(), player.getScoreboardName(), hadItems,
                placement.dangerRelocated());
    }

    /**
     * 标记本次死亡是否需要等待复活界面选择，并返回预留的尸体 UUID。
     */
    public static UUID configureCapture(ServerPlayer player, boolean awaitingChoice) {
        CaptureContext context = CAPTURES.computeIfAbsent(player.getUUID(), ignored -> CaptureContext.from(player));
        context.awaitingChoice = awaitingChoice;
        context.configured = true;
        return context.corpseId;
    }

    /**
     * 返回本次捕获将采用的尸体生成点。与 LivingDropsEvent 中的实际放置共用同一套
     * 危险区域/虚空回退逻辑，供死亡封禁原因在断开连接前记录坐标。
     */
    public static PendingDeathData.DeathLocation getPlannedCorpseLocation(ServerPlayer player) {
        CaptureContext context = CAPTURES.computeIfAbsent(player.getUUID(), ignored -> CaptureContext.from(player));
        CorpsePlacement placement = resolvePlacement(context);
        return new PendingDeathData.DeathLocation(
                placement.level().dimension().location().toString(),
                placement.x(),
                placement.y(),
                placement.z());
    }

    public static boolean isDeathConfigured(Player player) {
        CaptureContext context = CAPTURES.get(player.getUUID());
        return context != null && context.configured;
    }

    /** 供 Mixin 仅改写 Player.dropEquipment 中的 keepInventory 判断。 */
    public static boolean isCapturing(Player player) {
        CaptureContext context = CAPTURES.get(player.getUUID());
        return context != null && !context.processed;
    }

    /** LivingDeath 被取消或掉落流程提前返回时清理短生命周期上下文。 */
    public static void finishCapture(Player player) {
        CaptureContext context = CAPTURES.remove(player.getUUID());
        if (context != null && context.configured && !context.processed) {
            DreamingFishCore.LOGGER.warn("玩家 {} 的死亡掉落流程未生成尸体，未接管原始掉落",
                    player.getScoreboardName());
        }
    }

    /** 普通复活：只解锁尸体，不把物品放回玩家。 */
    public static boolean finalizeForNormalRespawn(ServerPlayer player, boolean lockCorpse) {
        Optional<DeathCorpseEntity> corpse = findPendingCorpse(player);
        if (corpse.isEmpty()) {
            // 普通复活不应因尸体被管理员移除而将玩家永久卡在死亡界面。
            DreamingFishCore.LOGGER.warn("玩家 {} 普通复活时未找到尸体，继续完成复活",
                    player.getScoreboardName());
            return true;
        }
        DeathCorpseEntity entity = corpse.get();
        entity.setLocked(lockCorpse);
        entity.setResolved(true);
        if (!entity.isEmpty()) {
            CorpseLocationNotice notice = CorpseLocationNotice.from(entity);
            RESPAWN_LOCATION_NOTICES.put(player.getUUID(), notice);
        }
        return true;
    }

    /** 兼容旧调用方；没有显式选择时使用更安全的锁定状态。 */
    public static boolean finalizeForNormalRespawn(ServerPlayer player) {
        return finalizeForNormalRespawn(player, true);
    }

    /** 付费保留：从尸体还原物品，全部成功后才允许扣费并复活。 */
    public static boolean restoreForKeepInventory(ServerPlayer player) {
        Optional<DeathCorpseEntity> corpse = findPendingCorpse(player);
        if (corpse.isEmpty()) {
            if (PendingDeathData.wasCorpseCreated(player) && !PendingDeathData.corpseHadItems(player)) {
                return true;
            }
            DreamingFishCore.LOGGER.error("玩家 {} 付费保留物品时未找到对应尸体",
                    player.getScoreboardName());
            return false;
        }

        DeathCorpseEntity entity = corpse.get();
        if (entity.getOwnerUuid().isPresent() && !entity.getOwnerUuid().get().equals(player.getUUID())) {
            DreamingFishCore.LOGGER.error("玩家 {} 的待处理死亡记录指向了其他玩家的尸体 {}",
                    player.getScoreboardName(), entity.getUUID());
            return false;
        }

        boolean transferred = entity.transferAllToAtomically(player);
        if (transferred) {
            // 只有物品已经完整转移且尸体已安全销毁后才改变状态；失败时保留可再次结算的尸体。
            entity.setResolved(true);
        }
        return transferred;
    }

    private static Optional<DeathCorpseEntity> findPendingCorpse(ServerPlayer player) {
        Optional<UUID> corpseId = PendingDeathData.getCorpseId(player);
        if (corpseId.isEmpty()) {
            return Optional.empty();
        }

        for (ServerLevel level : player.server.getAllLevels()) {
            Entity loaded = level.getEntity(corpseId.get());
            if (loaded instanceof DeathCorpseEntity corpse) {
                return Optional.of(corpse);
            }
        }

        PendingDeathData.DeathLocation location = PendingDeathData.getCorpseLocation(player);
        ResourceLocation dimensionId = ResourceLocation.tryParse(location.dimension());
        if (dimensionId == null) {
            return Optional.empty();
        }

        ResourceKey<net.minecraft.world.level.Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel deathLevel = player.server.getLevel(dimension);
        if (deathLevel == null) {
            return Optional.empty();
        }

        deathLevel.getChunk(BlockPos.containing(location.x(), location.y(), location.z()));
        Entity loaded = deathLevel.getEntity(corpseId.get());
        return loaded instanceof DeathCorpseEntity corpse ? Optional.of(corpse) : Optional.empty();
    }

    /** 在新玩家实体已经创建后发送最终尸体位置，避免消息被死亡界面遮住。 */
    public static void sendQueuedRespawnLocation(ServerPlayer player) {
        CorpseLocationNotice notice = RESPAWN_LOCATION_NOTICES.remove(player.getUUID());
        if (notice == null) {
            return;
        }
        player.sendSystemMessage(notice.toComponent(false));
    }

    /** 尸体运行中跌入虚空时更新待处理记录，并通知已经复活且在线的尸体主人。 */
    public static void onCorpseDangerRelocated(DeathCorpseEntity corpse) {
        if (!(corpse.level() instanceof ServerLevel level)) {
            return;
        }
        corpse.getOwnerUuid().ifPresent(ownerId -> {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
            if (owner == null) {
                return;
            }

            PendingDeathData.DeathLocation location = locationOf(corpse);
            if (PendingDeathData.hasPending(owner)) {
                PendingDeathData.updateCorpseLocation(owner, corpse.getUUID(), location, true);
                DeathEventHandler.refreshDeathScreenData(owner);
            }
            if (corpse.isResolved() && !corpse.isEmpty()) {
                owner.sendSystemMessage(CorpseLocationNotice.from(corpse).toComponent(true));
            }
        });
    }

    private static CorpsePlacement resolvePlacement(CaptureContext context) {
        Optional<BlockPos> sameColumn = findSafeSurface(
                context.level, BlockPos.containing(context.x, context.y, context.z));
        boolean endDimension = isEndDimension(context.level);
        Optional<BlockPos> dimensionSpawn = findSafeSurfaceNear(
                context.level,
                context.level.getSharedSpawnPos(),
                endDimension ? 128 : 16);

        if (context.y >= context.level.getMinBuildHeight()) {
            BlockPos recovery = sameColumn.or(() -> dimensionSpawn)
                    .orElseGet(() -> fallbackSpawn(context.level));
            return new CorpsePlacement(
                    context.level,
                    context.x,
                    context.y,
                    context.z,
                    recovery.getX() + 0.5D,
                    recovery.getY() + 0.05D,
                    recovery.getZ() + 0.5D,
                    false);
        }

        if (sameColumn.isPresent()) {
            return placementAt(context.level, sameColumn.get(), true);
        }
        if (dimensionSpawn.isPresent()) {
            return placementAt(context.level, dimensionSpawn.get(), true);
        }

        // 末地掉入虚空时绝不能把尸体跨维度送回主世界；继续在末地出生岛寻找可站立位置。
        if (endDimension) {
            Optional<BlockPos> endSafeSurface = findSafeSurfaceNear(
                    context.level, new BlockPos(0, context.level.getSeaLevel(), 0), 192);
            return placementAt(
                    context.level,
                    endSafeSurface.orElseGet(() -> fallbackSpawn(context.level)),
                    true);
        }

        ServerLevel overworld = context.level.getServer().overworld();
        Optional<BlockPos> overworldSpawn = findSafeSurface(overworld, overworld.getSharedSpawnPos());
        return placementAt(overworld, overworldSpawn.orElseGet(() -> fallbackSpawn(overworld)), true);
    }

    private static CorpsePlacement placementAt(ServerLevel level, BlockPos position, boolean relocated) {
        double x = position.getX() + 0.5D;
        double y = position.getY() + 0.05D;
        double z = position.getZ() + 0.5D;
        return new CorpsePlacement(level, x, y, z, x, y, z, relocated);
    }

    private static Optional<BlockPos> findSafeSurface(ServerLevel level, BlockPos origin) {
        int x = origin.getX();
        int z = origin.getZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinBuildHeight() || y >= level.getMaxBuildHeight() - 1) {
            return Optional.empty();
        }

        BlockPos feet = new BlockPos(x, y, z);
        BlockPos ground = feet.below();
        if (!level.getWorldBorder().isWithinBounds(feet)
                || !level.getFluidState(feet).isEmpty()
                || level.getFluidState(ground).is(FluidTags.LAVA)
                || level.getBlockState(ground).getCollisionShape(level, ground).isEmpty()
                || !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(feet.immutable());
    }

    /**
     * 在指定位置周围寻找最近的安全地表。用于虚空和危险地形回退，避免只检查单列
     * 导致末地出生岛被误判为空而跨维度生成尸体。
     */
    private static Optional<BlockPos> findSafeSurfaceNear(ServerLevel level, BlockPos origin, int maxRadius) {
        if (level == null || origin == null || maxRadius < 0) {
            return Optional.empty();
        }
        Optional<BlockPos> exact = findSafeSurface(level, origin);
        if (exact.isPresent() || maxRadius == 0) {
            return exact;
        }

        for (int radius = 1; radius <= maxRadius; radius++) {
            int min = -radius;
            int max = radius;
            for (int offset = min; offset <= max; offset++) {
                Optional<BlockPos> north = findSafeSurface(level,
                        new BlockPos(origin.getX() + offset, origin.getY(), origin.getZ() + min));
                if (north.isPresent()) {
                    return north;
                }
                Optional<BlockPos> south = findSafeSurface(level,
                        new BlockPos(origin.getX() + offset, origin.getY(), origin.getZ() + max));
                if (south.isPresent()) {
                    return south;
                }
            }
            for (int offset = min + 1; offset < max; offset++) {
                Optional<BlockPos> west = findSafeSurface(level,
                        new BlockPos(origin.getX() + min, origin.getY(), origin.getZ() + offset));
                if (west.isPresent()) {
                    return west;
                }
                Optional<BlockPos> east = findSafeSurface(level,
                        new BlockPos(origin.getX() + max, origin.getY(), origin.getZ() + offset));
                if (east.isPresent()) {
                    return east;
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isEndDimension(ServerLevel level) {
        return level != null && level.dimension().equals(Level.END);
    }

    private static BlockPos fallbackSpawn(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        return new BlockPos(
                spawn.getX(),
                Math.max(spawn.getY(), level.getMinBuildHeight() + 1),
                spawn.getZ());
    }

    private static PendingDeathData.DeathLocation locationOf(DeathCorpseEntity corpse) {
        return new PendingDeathData.DeathLocation(
                corpse.level().dimension().location().toString(),
                corpse.getX(),
                corpse.getY(),
                corpse.getZ());
    }

    private static boolean isRepresentedByDrops(
            List<net.minecraft.world.entity.item.ItemEntity> drops,
            net.minecraft.world.item.ItemStack expected) {
        for (net.minecraft.world.entity.item.ItemEntity drop : drops) {
            if (drop != null && (net.minecraft.world.item.ItemStack.matches(drop.getItem(), expected)
                    || net.minecraft.world.item.ItemStack.isSameItemSameComponents(drop.getItem(), expected))) {
                return true;
            }
        }
        return false;
    }

    private record CorpsePlacement(ServerLevel level,
                                   double x,
                                   double y,
                                   double z,
                                   double recoveryX,
                                   double recoveryY,
                                   double recoveryZ,
                                   boolean dangerRelocated) {
    }

    private record CorpseLocationNotice(String dimension,
                                        int x,
                                        int y,
                                        int z,
                                        boolean dangerRelocated) {
        private static CorpseLocationNotice from(DeathCorpseEntity corpse) {
            BlockPos position = corpse.blockPosition();
            return new CorpseLocationNotice(
                    corpse.level().dimension().location().toString(),
                    position.getX(),
                    position.getY(),
                    position.getZ(),
                    corpse.wasDangerRelocated());
        }

        private Component toComponent(boolean updated) {
            String key = updated
                    ? "message.dreamingfishcore.corpse.location_updated"
                    : dangerRelocated
                    ? "message.dreamingfishcore.corpse.location_relocated"
                    : "message.dreamingfishcore.corpse.location";
            return Component.translatable(key, dimension, x, y, z).withStyle(ChatFormatting.GOLD);
        }
    }

    private static final class CaptureContext {
        private final UUID corpseId;
        private final UUID ownerId;
        private final String ownerName;
        private final ServerLevel level;
        private final double x;
        private final double y;
        private final double z;
        private final float yRot;
        private final DeathCorpseInventory inventory;
        private final List<CorpseAccessoryEntry> accessoryItems;
        private boolean awaitingChoice;
        private boolean configured;
        private boolean processed;

        private CaptureContext(UUID corpseId,
                               UUID ownerId,
                               String ownerName,
                               ServerLevel level,
                               double x,
                               double y,
                               double z,
                               float yRot,
                               DeathCorpseInventory inventory,
                               List<CorpseAccessoryEntry> accessoryItems) {
            this.corpseId = corpseId;
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.level = level;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yRot = yRot;
            this.inventory = inventory;
            this.accessoryItems = accessoryItems;
        }

        private static CaptureContext from(ServerPlayer player) {
            return new CaptureContext(
                    UUID.randomUUID(),
                    player.getUUID(),
                    player.getGameProfile().getName(),
                    player.serverLevel(),
                    player.getX(),
                    Math.max(player.getY(), player.getRootVehicle().getY()),
                    player.getZ(),
                    player.getYRot(),
                    DeathCorpseInventory.snapshot(player),
                    CorpseAccessoryCompat.snapshot(player));
        }
    }
}
