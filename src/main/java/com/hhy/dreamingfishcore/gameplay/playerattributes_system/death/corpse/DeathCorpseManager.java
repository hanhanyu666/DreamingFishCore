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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 接管玩家死亡掉落并将其写入尸体实体。 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class DeathCorpseManager {
    /**
     * 复活结算时尸体还没被实体管理器读回的补偿重试次数。
     *
     * <p>区块刚被 {@code getChunk} 加载时，实体要等实体存储读完并在实体管理器下一次
     * {@code tick()} 才会出现，因此这里的重试必须以 tick 为单位。</p>
     */
    private static final int SETTLEMENT_RETRY_ATTEMPTS = 6;

    private static final Map<UUID, CaptureContext> CAPTURES = new HashMap<>();
    private static final Map<UUID, CorpseLocationNotice> RESPAWN_LOCATION_NOTICES = new HashMap<>();
    private static final Map<UUID, PendingSettlement> PENDING_SETTLEMENTS = new HashMap<>();

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

        // 如果普通复活请求先于掉落事件抵达，玩家记录可能已经完成并被清除。
        // 这种情况下尸体必须直接进入可交互状态，不能留下“等待复活结算”的孤儿尸体。
        boolean awaitingSettlement = context.awaitingChoice
                && PendingDeathData.hasPendingRecord(player);
        corpse.initialize(
                context.corpseId,
                context.ownerId,
                context.ownerName,
                new DeathCorpseInventory(),
                placement.x(),
                placement.y(),
                placement.z(),
                context.yRot,
                !awaitingSettlement,
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
        UUID corpseId = pendingCorpseId(player);
        try {
            Optional<DeathCorpseEntity> corpse = findPendingCorpse(player);
            if (corpse.isPresent()) {
                DeathCorpseEntity entity = corpse.get();
                entity.setLocked(lockCorpse);
                entity.setResolved(true);
                // 本次选择已经直接落到实体上，清掉可能存在的补结算意图。
                PendingDeathData.clearSettledCorpse(player, entity.getUUID());
                if (!entity.isEmpty()) {
                    CorpseLocationNotice notice = CorpseLocationNotice.from(entity);
                    RESPAWN_LOCATION_NOTICES.put(player.getUUID(), notice);
                }
                return true;
            }
            // 尸体可能已被管理员移除、区块数据损坏，或旧版本留下了失效引用。
            // 不能因此把玩家永久卡在死亡界面；改为落下持久化意图并重试定位。
            DreamingFishCore.LOGGER.warn("玩家 {} 普通复活时未找到对应尸体，已改为稍后补结算",
                    player.getScoreboardName());
        } catch (RuntimeException exception) {
            // 尸体查找只影响物品实体的解锁，不应阻止玩家完成普通复活。
            DreamingFishCore.LOGGER.error("玩家 {} 查找待结算尸体时发生异常，将继续完成普通复活",
                    player.getScoreboardName(), exception);
        }
        deferSettlement(player, corpseId, lockCorpse);
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
            // 尸体所在区块没有加载时，实体要等实体管理器下一 tick 才可读；
            // 这里必须说清原因，否则玩家只会看到“复活点不足”并误以为物品已经丢失。
            player.sendSystemMessage(Component.literal(
                    "§c暂时读不到死亡地点的尸体（该区域尚未加载）。请稍后再试一次，"
                            + "或选择“重生”后返回死亡地点取回物品。"));
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
        if (!transferred) {
            player.sendSystemMessage(Component.literal(
                    "§c无法把尸体中的物品完整放回你的物品栏，请整理背包后再试一次，"
                            + "或选择“重生”后返回死亡地点取回。"));
        }
        if (transferred) {
            // 只有物品已经完整转移且尸体已安全销毁后才改变状态；失败时保留可再次结算的尸体。
            entity.setResolved(true);
            PendingDeathData.clearSettledCorpse(player, entity.getUUID());
        }
        return transferred;
    }

    private static Optional<DeathCorpseEntity> findPendingCorpse(ServerPlayer player) {
        Optional<UUID> corpseId = PendingDeathData.getCorpseId(player);
        if (corpseId.isEmpty()) {
            return Optional.empty();
        }
        UUID expectedId = corpseId.get();
        UUID ownerId = player.getUUID();

        for (ServerLevel level : player.server.getAllLevels()) {
            Entity loaded = level.getEntity(expectedId);
            if (loaded instanceof DeathCorpseEntity corpse
                    && isUsableExactReference(corpse, ownerId)) {
                return Optional.of(corpse);
            }
        }

        PendingDeathData.DeathLocation location;
        try {
            location = PendingDeathData.getCorpseLocation(player);
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.warn("玩家 {} 的尸体位置记录无效，无法定位尸体",
                    player.getScoreboardName(), exception);
            return Optional.empty();
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(location.dimension());
        if (dimensionId == null) {
            return Optional.empty();
        }

        ResourceKey<net.minecraft.world.level.Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel deathLevel = player.server.getLevel(dimension);
        if (deathLevel == null) {
            return Optional.empty();
        }

        BlockPos corpseBlock = BlockPos.containing(location.x(), location.y(), location.z());
        deathLevel.getChunk(corpseBlock);
        Entity loaded = deathLevel.getEntity(expectedId);
        if (loaded instanceof DeathCorpseEntity corpse
                && isUsableExactReference(corpse, ownerId)) {
            return Optional.of(corpse);
        }

        // EntityManager 的 UUID 索引在区块刚载入的瞬间可能还没有完成更新；
        // 同时兼容旧记录中 UUID 已变化、但尸体仍保留原主人的情况。
        AABB searchBox = new AABB(
                location.x() - 8.0D, location.y() - 8.0D, location.z() - 8.0D,
                location.x() + 8.0D, location.y() + 8.0D, location.z() + 8.0D);
        DeathCorpseEntity nearby = findOwnedCorpse(
                deathLevel, searchBox, ownerId, location.x(), location.y(), location.z());
        if (nearby != null) {
            return acceptRecoveredCorpse(player, expectedId, nearby);
        }

        // 尸体可能因为虚空救援或旧版本逻辑已经离开记录位置。
        // 只在已加载实体中按 owner UUID 查找，避免把其他玩家的尸体当成目标。
        DeathCorpseEntity loadedByOwner = findOwnedCorpseInLoadedLevels(player, ownerId, location);
        if (loadedByOwner != null) {
            return acceptRecoveredCorpse(player, expectedId, loadedByOwner);
        }
        return Optional.empty();
    }

    private static boolean isUsableExactReference(DeathCorpseEntity corpse, UUID ownerId) {
        // 没有 Owner 字段的实体只可能来自早期版本；此时 UUID 引用本身仍是唯一依据。
        return corpse.getOwnerUuid().isEmpty()
                || corpse.getOwnerUuid().get().equals(ownerId);
    }

    private static boolean isOwnedBy(DeathCorpseEntity corpse, UUID ownerId) {
        return corpse.isAlive()
                && corpse.getOwnerUuid().map(ownerId::equals).orElse(false);
    }

    private static DeathCorpseEntity findOwnedCorpse(ServerLevel level,
                                                     AABB area,
                                                     UUID ownerId,
                                                     double x,
                                                     double y,
                                                     double z) {
        DeathCorpseEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (DeathCorpseEntity candidate : level.getEntitiesOfClass(
                DeathCorpseEntity.class, area, corpse -> isOwnedBy(corpse, ownerId))) {
            double distance = distanceSquared(candidate, x, y, z);
            if (nearest == null || distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static DeathCorpseEntity findOwnedCorpseInLoadedLevels(
            ServerPlayer player,
            UUID ownerId,
            PendingDeathData.DeathLocation location) {
        DeathCorpseEntity nearestSameDimension = null;
        double nearestSameDimensionDistance = Double.MAX_VALUE;
        DeathCorpseEntity firstOtherDimension = null;

        for (ServerLevel level : player.server.getAllLevels()) {
            boolean sameDimension = level.dimension().location().toString().equals(location.dimension());
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof DeathCorpseEntity corpse) || !isOwnedBy(corpse, ownerId)) {
                    continue;
                }
                if (!sameDimension) {
                    if (firstOtherDimension == null) {
                        firstOtherDimension = corpse;
                    }
                    continue;
                }
                double distance = distanceSquared(corpse, location.x(), location.y(), location.z());
                if (nearestSameDimension == null || distance < nearestSameDimensionDistance) {
                    nearestSameDimension = corpse;
                    nearestSameDimensionDistance = distance;
                }
            }
        }
        return nearestSameDimension != null ? nearestSameDimension : firstOtherDimension;
    }

    private static double distanceSquared(Entity entity, double x, double y, double z) {
        double dx = entity.getX() - x;
        double dy = entity.getY() - y;
        double dz = entity.getZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static Optional<DeathCorpseEntity> acceptRecoveredCorpse(
            ServerPlayer player,
            UUID expectedId,
            DeathCorpseEntity corpse) {
        if (!expectedId.equals(corpse.getUUID())) {
            try {
                boolean repaired = PendingDeathData.repairCorpseReference(
                        player,
                        corpse.getUUID(),
                        locationOf(corpse),
                        corpse.wasDangerRelocated(),
                        !corpse.isEmpty());
                if (repaired) {
                    DreamingFishCore.LOGGER.warn("已修复玩家 {} 的尸体引用：{} -> {}",
                            player.getScoreboardName(), expectedId, corpse.getUUID());
                }
            } catch (RuntimeException exception) {
                // 实体本身仍可用于本次结算；修复记录失败不应再次卡住玩家。
                DreamingFishCore.LOGGER.warn("玩家 {} 的尸体引用修复失败，将继续使用已找到的尸体",
                        player.getScoreboardName(), exception);
            }
        }
        return Optional.of(corpse);
    }

    /**
     * 让尸体按当前死亡记录自愈结算状态。
     *
     * <p>结算时如果尸体所在区块没有加载，实体管理器要到之后的 tick 才会把尸体读回，
     * 于是结算定位不到尸体；死亡记录随后被清除，尸体却仍然带着 {@code Resolved=false}
     * 留在世界上。这里以“主人的死亡记录是否仍指向本尸体”为唯一判据：不再指向就说明
     * 这次死亡已经结算（或已被下一次死亡取代），尸体必须恢复为可取回。</p>
     *
     * <p>主人不在线时无法读取其死亡记录，只能保持原状——尸体必须由主人自己在场时领取，
     * 因此不影响物品安全。</p>
     *
     * @return 调用后尸体是否已经处于可结算状态
     */
    public static boolean reconcileSettlement(DeathCorpseEntity corpse) {
        if (corpse == null) {
            return false;
        }
        if (corpse.isResolved() || !(corpse.level() instanceof ServerLevel level)) {
            return corpse.isResolved();
        }
        Optional<UUID> ownerId = corpse.getOwnerUuid();
        if (ownerId.isEmpty()) {
            // 没有归属字段的实体来自早期版本，无法判断它属于哪次死亡。
            return false;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId.get());
        if (owner == null || isAwaitingOwnerChoice(owner, corpse.getUUID())) {
            return false;
        }

        applySettledLock(owner, corpse);
        corpse.setResolved(true);
        DreamingFishCore.LOGGER.warn("尸体 {} 不再对应任何待结算死亡，已恢复为可取回状态（主人 {}，锁定={}）",
                corpse.getUUID(), owner.getScoreboardName(), corpse.isLocked());
        return true;
    }

    /** 主人的死亡记录是否仍在等待这次选择，并且指向这具尸体。 */
    private static boolean isAwaitingOwnerChoice(ServerPlayer owner, UUID corpseId) {
        if (!PendingDeathData.hasPendingRecord(owner)) {
            return false;
        }
        return PendingDeathData.getCorpseId(owner).filter(corpseId::equals).isPresent();
    }

    /** 应用玩家结算时选择的尸体拾取权限；没有记录时保留尸体自身的锁定状态。 */
    private static void applySettledLock(ServerPlayer owner, DeathCorpseEntity corpse) {
        PendingDeathData.getSettledCorpse(owner)
                .filter(settled -> settled.corpseId().equals(corpse.getUUID()))
                .ifPresent(settled -> {
                    corpse.setLocked(settled.locked());
                    PendingDeathData.clearSettledCorpse(owner, corpse.getUUID());
                });
    }

    /**
     * 结算时定位不到尸体：落下持久化的待领取意图，并在接下来几 tick 重试。
     *
     * <p>区块未加载时 {@code getChunk} 只恢复方块数据，实体由实体存储异步读回，
     * 要等实体管理器下一次 {@code tick()} 才可见。重试期间重新请求区块加载，
     * 让常见情况下尸体在玩家复活后立刻恢复可取回。</p>
     */
    private static void deferSettlement(ServerPlayer player, UUID corpseId, boolean lockCorpse) {
        if (corpseId == null) {
            return;
        }
        PendingDeathData.DeathLocation location = null;
        try {
            location = PendingDeathData.getCorpseLocation(player);
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.warn("玩家 {} 的尸体位置记录无效，将依赖尸体自愈结算",
                    player.getScoreboardName(), exception);
        }
        PendingDeathData.markSettledCorpse(player, corpseId, lockCorpse, location);
        PENDING_SETTLEMENTS.put(corpseId, new PendingSettlement(player.getUUID(), SETTLEMENT_RETRY_ATTEMPTS));
        DreamingFishCore.LOGGER.warn("玩家 {} 复活时尸体 {} 尚未载入，已安排重新定位",
                player.getScoreboardName(), corpseId);
    }

    private static UUID pendingCorpseId(ServerPlayer player) {
        try {
            return PendingDeathData.getCorpseId(player).orElse(null);
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.warn("玩家 {} 的死亡记录缺少可用尸体引用，无法补结算",
                    player.getScoreboardName(), exception);
            return null;
        }
    }

    /** 重试定位结算时还没载入的尸体；成功、主人离线或尝试耗尽后出队。 */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING_SETTLEMENTS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, PendingSettlement>> iterator = PENDING_SETTLEMENTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingSettlement> entry = iterator.next();
            PendingSettlement pending = entry.getValue();
            if (finishDeferredSettlement(server, entry.getKey(), pending)) {
                iterator.remove();
            } else if (pending.remainingAttempts() <= 1) {
                DreamingFishCore.LOGGER.warn("尸体 {} 在重试期间仍未载入，等待主人再次靠近时自愈",
                        entry.getKey());
                iterator.remove();
            } else {
                entry.setValue(pending.afterRetry());
            }
        }
    }

    private static boolean finishDeferredSettlement(MinecraftServer server,
                                                    UUID corpseId,
                                                    PendingSettlement pending) {
        ServerPlayer owner = server.getPlayerList().getPlayer(pending.ownerId());
        if (owner == null) {
            return true;
        }
        PendingDeathData.SettledCorpse settled = PendingDeathData.getSettledCorpse(owner).orElse(null);
        if (settled == null || !settled.corpseId().equals(corpseId)) {
            // 意图已被消费或被另一次死亡替换，本实体交给自愈逻辑处理。
            return true;
        }
        requestCorpseChunk(server, settled);

        DeathCorpseEntity corpse = findCorpseById(server, corpseId);
        if (corpse == null) {
            return false;
        }
        if (!isUsableExactReference(corpse, owner.getUUID())) {
            // 记录被破坏到指向别人的尸体时不能动别人的尸体；尸体本身仍由自愈逻辑按其归属处理。
            PendingDeathData.clearSettledCorpse(owner, corpseId);
            DreamingFishCore.LOGGER.error("玩家 {} 的补结算指向了其他玩家的尸体 {}，已放弃补结算",
                    owner.getScoreboardName(), corpseId);
            return true;
        }
        corpse.setLocked(settled.locked());
        corpse.setResolved(true);
        PendingDeathData.clearSettledCorpse(owner, corpseId);
        DreamingFishCore.LOGGER.info("玩家 {} 的死亡结算已补齐：尸体 {} 恢复为可取回状态（锁定={}）",
                owner.getScoreboardName(), corpseId, settled.locked());

        if (corpse.isEmpty()) {
            return true;
        }
        if (owner.isDeadOrDying()) {
            // 玩家还在死亡界面，位置消息要等新玩家实体创建后再发。
            RESPAWN_LOCATION_NOTICES.put(owner.getUUID(), CorpseLocationNotice.from(corpse));
        } else {
            owner.sendSystemMessage(CorpseLocationNotice.from(corpse).toComponent(false));
        }
        return true;
    }

    private static DeathCorpseEntity findCorpseById(MinecraftServer server, UUID corpseId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(corpseId) instanceof DeathCorpseEntity corpse) {
                return corpse;
            }
        }
        return null;
    }

    /** 重新请求尸体所在区块的加载；实体由实体存储在之后 tick 读回。 */
    private static void requestCorpseChunk(MinecraftServer server, PendingDeathData.SettledCorpse settled) {
        ResourceLocation dimensionId = ResourceLocation.tryParse(settled.dimension());
        if (dimensionId == null) {
            return;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (level == null) {
            return;
        }
        try {
            level.getChunk(BlockPos.containing(settled.x(), settled.y(), settled.z()));
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.warn("无法加载尸体 {} 所在区块（{} {} {} {}）",
                    settled.corpseId(), settled.dimension(), settled.x(), settled.y(), settled.z(),
                    exception);
        }
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

    /** 一次补偿结算的重试状态：主人与剩余尝试次数。 */
    private record PendingSettlement(UUID ownerId, int remainingAttempts) {
        private PendingSettlement afterRetry() {
            return new PendingSettlement(ownerId, remainingAttempts - 1);
        }
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
