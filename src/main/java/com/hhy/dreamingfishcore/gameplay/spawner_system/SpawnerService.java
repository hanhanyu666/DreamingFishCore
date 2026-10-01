package com.hhy.dreamingfishcore.gameplay.spawner_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.block.DreamingFishCore_Blocks;
import com.hhy.dreamingfishcore.block.SpawnerBlock;
import com.hhy.dreamingfishcore.gameplay.clue_system.ClueGuaranteeService;
import com.hhy.dreamingfishcore.gameplay.playerlevel_system.overalllevel.PlayerLevelManager;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationDefinition;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationManager;
import com.hhy.dreamingfishcore.gameplay.spawner_system.network.Packet_SpawnerConfigRequest;
import com.hhy.dreamingfishcore.gameplay.spawner_system.network.SpawnerSync;
import com.hhy.dreamingfishcore.server.economy_bridge.EconomySystemBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 刷怪箱的服务端业务：交互、启用判定、按批刷怪、剿灭结算与自毁。
 *
 * <p>规则（均已在目标里与用户确认）：</p>
 * <ul>
 *   <li><b>启用</b>：位置在开启尸潮开关的任务地点内，且检测范围内有生存/冒险模式玩家；
 *       勾了红石控制时还必须持续有红石信号。</li>
 *   <li><b>刷怪</b>：第一批在启用时立刻刷；之后必须等上一批<b>全部死亡</b>才开始算 CD，
 *       CD 到了刷下一批，直到批次刷满。</li>
 *   <li><b>剿灭</b>：批次刷满且场上清空 → 给结算瞬间在区域内的生存/冒险玩家发奖励
 *       （每台刷怪箱每人只发一次），按配置决定是否自毁。</li>
 * </ul>
 */
public final class SpawnerService {

    /** 扫描节流：每秒判定一次，够用且不会让登记表遍历变成热路径。 */
    private static final int SCAN_INTERVAL_TICKS = 20;
    /** 落点搜索：以目标点为中心上下各找几格。 */
    private static final int SPAWN_VERTICAL_SEARCH = 3;

    private SpawnerService() {
    }

    // ==================== 交互 ====================

    /**
     * 玩家右键刷怪箱。
     *
     * @param shiftDown 是否潜行（潜行 = 打开配置界面）
     * @return 是否已处理
     */
    public static boolean onInteract(ServerPlayer player, BlockPos pos, boolean shiftDown) {
        if (player == null || pos == null) {
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        String dimensionId = player.level().dimension().location().toString();
        SpawnerEntry entry = SpawnerRegistry
                .find(dimensionId, pos.getX(), pos.getY(), pos.getZ())
                .orElse(null);
        if (entry == null) {
            // 放置时没登记（命令放置、结构生成等）：补登记，让这台设备可用。
            SpawnerRegistry.register(dimensionId, pos.getX(), pos.getY(), pos.getZ());
            entry = SpawnerRegistry
                    .find(dimensionId, pos.getX(), pos.getY(), pos.getZ())
                    .orElse(null);
            if (entry == null) {
                player.displayClientMessage(
                        Component.literal("§c刷怪箱登记失败，请检查服务器日志。"), false);
                return false;
            }
        }

        if (shiftDown) {
            // 潜行 + 右键 = 打开配置界面（服务端校验 + 下发快照，能否编辑也由服务端判断）。
            handleOpenRequest(player, pos);
            return true;
        }

        if (player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.CREATIVE) {
            return cycleSkin(player, pos);
        }
        player.displayClientMessage(Component.literal(describe(entry)), false);
        return true;
    }

    /** 创造模式右键：循环换外观。 */
    private static boolean cycleSkin(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        if (!state.is(DreamingFishCore_Blocks.SPAWNER.get())) {
            return false;
        }
        int next = (state.getValue(SpawnerBlock.SKIN) + 1) % SpawnerBlock.SKIN_COUNT;
        level.setBlock(pos, state.setValue(SpawnerBlock.SKIN, next), Block.UPDATE_ALL);
        player.displayClientMessage(
                Component.literal("§7刷怪箱外观已切换为第 " + (next + 1) + " 套（共 "
                        + SpawnerBlock.SKIN_COUNT + " 套）"), false);
        return true;
    }

    /** 给玩家看的一行状态。 */
    public static String describe(SpawnerEntry entry) {
        return "§6[刷怪箱] §f" + entry.entityId()
                + " §7| 批次 §f" + entry.batchesSpawned() + "/" + entry.batches()
                + " §7| 每批 §f" + entry.spawnCount()
                + " §7| CD §f" + entry.cooldownTicks() + " tick"
                + " §7| 检测 §f" + entry.detectionRadius()
                + " §7| 刷怪半径 §f" + entry.spawnRadius()
                + (entry.redstoneControlled() ? " §7| §c需红石" : "")
                + (entry.selfDestructWhenCleared() ? " §7| §c剿灭后自毁" : "")
                + (entry.fixedClueEnabled() && entry.clueId() > 0
                        ? " §7| 线索 §f#" + entry.clueId() : "");
    }

    // ==================== 周期 ====================

    /** 每服务器 tick 调用；内部按节流与登记表规模决定要不要真的扫描。 */
    public static void tick(MinecraftServer server) {
        if (server == null
                || !SpawnerRegistry.isLoaded()
                || server.getTickCount() % SCAN_INTERVAL_TICKS != 0
                || SpawnerRegistry.count() == 0) {
            return;
        }
        tickNow(server);
    }

    /**
     * 立刻扫描一次，跳过 tick 节流。
     *
     * <p>只给 gametest 用：无头测试不能保证服务器 tick 数正好落在节流点上，
     * 否则测试会随机失败。</p>
     */
    static void tickNow(MinecraftServer server) {
        if (server == null || !SpawnerRegistry.isLoaded() || SpawnerRegistry.count() == 0) {
            return;
        }
        for (SpawnerEntry entry : SpawnerRegistry.all()) {
            try {
                tickOne(server, entry);
            } catch (RuntimeException exception) {
                // 一台刷怪箱出问题不该让整轮扫描断掉。
                DreamingFishCore.LOGGER.error("刷怪箱 {} 的周期判定失败", entry.key(), exception);
            }
        }
    }

    private static void tickOne(MinecraftServer server, SpawnerEntry entry) {
        ServerLevel level = resolveLevel(server, entry.dimensionId());
        if (level == null) {
            return;
        }
        BlockPos pos = new BlockPos(entry.x(), entry.y(), entry.z());
        // 区块没加载就不判定：读不到方块与实体时，宁可不推进也不要误判成"已剿灭"。
        if (!level.isLoaded(pos)) {
            return;
        }
        if (!level.getBlockState(pos).is(DreamingFishCore_Blocks.SPAWNER.get())) {
            SpawnerRegistry.unregister(entry.dimensionId(), entry.x(), entry.y(), entry.z());
            DreamingFishCore.LOGGER.info("刷怪箱 {} 已不存在，摘除登记", entry.key());
            return;
        }

        TaskLocationDefinition location = TaskLocationManager.findLocationAt(level, pos).orElse(null);
        if (location == null || !location.isHorde()) {
            setActive(level, pos, false);
            return;
        }
        if (entry.redstoneControlled() && !level.hasNeighborSignal(pos)) {
            setActive(level, pos, false);
            return;
        }

        int alive = pruneAlive(level, entry);
        List<ServerPlayer> present = qualifyingPlayersNear(server, level, pos, entry.detectionRadius());
        if (present.isEmpty()) {
            // "工作中"的定义就是"有合格玩家在场"：没人时不管结算没结算都显示停机。
            setActive(level, pos, false);
            return;
        }
        setActive(level, pos, true);

        if (entry.completed()) {
            // 已结算过、还要等下一轮 CD：这里什么都不做。
            return;
        }

        long now = level.getGameTime();
        boolean fieldCleared = alive <= 0;
        if (SpawnerEntry.needsCooldownAnchor(entry.batchesSpawned(), entry.batches(),
                fieldCleared, entry.nextBatchAtTick())) {
            entry.anchorCooldown(now);
            SpawnerRegistry.markChanged();
        }
        if (SpawnerEntry.nextBatchDue(entry.batchesSpawned(), entry.batches(),
                fieldCleared, entry.nextBatchAtTick(), now)) {
            int spawned = spawnBatch(level, entry, pos);
            entry.markBatchSpawned();
            SpawnerRegistry.markChanged();
            DreamingFishCore.LOGGER.info("刷怪箱 {} 刷出第 {}/{} 批（{} 只）",
                    entry.key(), entry.batchesSpawned(), entry.batches(), spawned);
            // 刚刷完的这批还在场上，本 tick 不会结算。
            return;
        }

        if (SpawnerEntry.isCleared(entry.batchesSpawned(), entry.batches(), alive)) {
            settleClear(server, level, entry, pos, location);
        }
    }

    // ==================== 生成 ====================

    /** 刷一批怪，返回实际生成的数量。 */
    private static int spawnBatch(ServerLevel level, SpawnerEntry entry, BlockPos origin) {
        EntityType<?> type = resolveEntityType(entry.entityId());
        if (type == null) {
            DreamingFishCore.LOGGER.warn("刷怪箱 {} 的实体 id 无法解析：{}",
                    entry.key(), entry.entityId());
            return 0;
        }
        RandomSource random = level.getRandom();
        int spawned = 0;
        for (int index = 0; index < entry.spawnCount(); index++) {
            BlockPos target = findSafeSpot(level, pickSpawnPosition(random, origin, entry.spawnRadius()));
            if (target == null) {
                continue;
            }
            if (spawnOne(level, type, target, random, entry)) {
                spawned++;
            }
        }
        return spawned;
    }

    /** 在半径内随机挑一个落点（纯函数，便于单测）。 */
    static BlockPos pickSpawnPosition(RandomSource random, BlockPos origin, int radius) {
        int bound = radius * 2 + 1;
        int dx = random.nextInt(bound) - radius;
        int dz = random.nextInt(bound) - radius;
        return origin.offset(dx, 0, dz);
    }

    /** 找一个"脚下有实体方块、上方两格可站"的落点；找不到返回 null。 */
    static BlockPos findSafeSpot(ServerLevel level, BlockPos target) {
        if (target == null) {
            return null;
        }
        for (int offset = 0; offset <= SPAWN_VERTICAL_SEARCH; offset++) {
            for (int direction : new int[]{offset, -offset}) {
                BlockPos candidate = target.offset(0, direction, 0);
                if (isStandable(level, candidate)) {
                    return candidate;
                }
                if (offset == 0) {
                    break;
                }
            }
        }
        return null;
    }

    private static boolean isStandable(ServerLevel level, BlockPos feet) {
        if (!level.isLoaded(feet)) {
            return false;
        }
        BlockPos ground = feet.below();
        if (!level.getBlockState(ground).isSolidRender(level, ground)) {
            return false;
        }
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    /** 生成一只指定类型的怪；样板照 {@code Command_Npc} 与 {@code SiegeZombieEntity#finalizeSpawn}。 */
    private static boolean spawnOne(ServerLevel level, EntityType<?> type, BlockPos pos,
                                    RandomSource random, SpawnerEntry entry) {
        if (!(type.create(level) instanceof Mob mob)) {
            return false;
        }
        float yaw = random.nextFloat() * 360.0F;
        mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yaw, 0.0F);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        // SPAWNER 是"刷怪箱来源"的正名；尸潮区域会为它豁免自动刷怪禁令（见 ZombieTaskLocationRules）。
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.SPAWNER, null);
        mob.setPersistenceRequired();
        if (!level.addFreshEntity(mob)) {
            return false;
        }
        entry.trackSpawned(mob.getUUID());
        return true;
    }

    static EntityType<?> resolveEntityType(String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(entityId.trim());
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    // ==================== 在场与清理 ====================

    /**
     * 检测范围内是否有生存/冒险模式玩家。
     *
     * <p>创造与旁观不算：他们要能从天上俯视布置，不能因为自己站在旁边就把尸潮刷起来。</p>
     */
    private static List<ServerPlayer> qualifyingPlayersNear(MinecraftServer server, ServerLevel level,
                                                            BlockPos pos, int radius) {
        List<ServerPlayer> found = new ArrayList<>();
        for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
            if (candidate.level() != level || !isQualifying(candidate)) {
                continue;
            }
            BlockPos at = candidate.blockPosition();
            if (withinRange(at.getX() - pos.getX(), at.getZ() - pos.getZ(), radius)
                    && Math.abs(at.getY() - pos.getY()) <= radius) {
                found.add(candidate);
            }
        }
        return found;
    }

    /** 结算范围：整个尸潮区域内的合格玩家（与任务地点结算口径一致）。 */
    private static List<ServerPlayer> qualifyingPlayersInLocation(MinecraftServer server,
                                                                  ServerLevel level,
                                                                  TaskLocationDefinition location) {
        List<ServerPlayer> found = new ArrayList<>();
        for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
            if (candidate.level() != level || !isQualifying(candidate)) {
                continue;
            }
            if (location.contains(level.dimension(), candidate.blockPosition())) {
                found.add(candidate);
            }
        }
        return found;
    }

    static boolean isQualifying(ServerPlayer player) {
        if (player == null || !player.isAlive()) {
            return false;
        }
        net.minecraft.world.level.GameType mode = player.gameMode.getGameModeForPlayer();
        return mode == net.minecraft.world.level.GameType.SURVIVAL
                || mode == net.minecraft.world.level.GameType.ADVENTURE;
    }

    /** 水平半径判定（与聚居地过滤装置同一套语义）。纯函数，便于单测。 */
    static boolean withinRange(int deltaX, int deltaZ, int radius) {
        if (radius <= 0) {
            return false;
        }
        long dx = deltaX;
        long dz = deltaZ;
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    /** 丢掉已经消失的追踪项，返回仍在场的数量。 */
    private static int pruneAlive(ServerLevel level, SpawnerEntry entry) {
        List<String> alive = entry.aliveEntityIds();
        List<String> remaining = new ArrayList<>(alive.size());
        for (String raw : alive) {
            UUID id = parseUuid(raw);
            if (id == null) {
                continue;
            }
            var entity = level.getEntity(id);
            if (entity != null && entity.isAlive() && !entity.isRemoved()) {
                remaining.add(raw);
            }
        }
        if (remaining.size() != alive.size()) {
            alive.clear();
            alive.addAll(remaining);
            SpawnerRegistry.markChanged();
        }
        return remaining.size();
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    // ==================== 剿灭结算 ====================

    private static void settleClear(MinecraftServer server, ServerLevel level, SpawnerEntry entry,
                                    BlockPos pos, TaskLocationDefinition location) {
        entry.markCompleted();
        List<ServerPlayer> rewarded = qualifyingPlayersInLocation(server, level, location);
        int granted = 0;
        for (ServerPlayer player : rewarded) {
            if (entry.hasRewarded(player.getUUID())) {
                continue;
            }
            grantRewards(player, entry);
            entry.markRewarded(player.getUUID());
            granted++;
        }
        DreamingFishCore.LOGGER.info("刷怪箱 {} 达成剿灭：结算给 {} 名玩家（共 {} 人在场）",
                entry.key(), granted, rewarded.size());

        if (entry.selfDestructWhenCleared()) {
            level.removeBlock(pos, false);
            SpawnerRegistry.unregister(entry.dimensionId(), entry.x(), entry.y(), entry.z());
            DreamingFishCore.LOGGER.info("刷怪箱 {} 已按配置自毁", entry.key());
            return;
        }
        // 不自毁：立刻排下一轮的 CD，让它可以反复使用；已领奖励名单保留。
        entry.resetRound(level.getGameTime());
        SpawnerRegistry.markChanged();
    }

    /** 发放一台刷怪箱的剿灭奖励：物品、经验、梦鱼币、固定线索。 */
    private static void grantRewards(ServerPlayer player, SpawnerEntry entry) {
        for (SpawnerEntry.RewardEntry reward : entry.rewardItems()) {
            ItemStack stack = resolveItemStack(reward);
            if (stack.isEmpty()) {
                continue;
            }
            if (!player.addItem(stack)) {
                player.drop(stack, false);
            }
        }
        if (entry.rewardExperience() > 0) {
            PlayerLevelManager.addPlayerExperienceServer(player, entry.rewardExperience());
        }
        if (entry.rewardCoins() > 0) {
            EconomySystemBridge.MutationResult result = EconomySystemBridge.credit(
                    player, entry.rewardCoins(), "spawner/reward", "尸潮剿灭奖励");
            if (result != EconomySystemBridge.MutationResult.SUCCESS
                    && result != EconomySystemBridge.MutationResult.NOT_AVAILABLE) {
                DreamingFishCore.LOGGER.warn("给玩家 {} 发放尸潮奖励梦鱼币失败：{}",
                        player.getScoreboardName(), result);
            }
        }
        if (entry.fixedClueEnabled() && entry.clueId() > 0) {
            ClueGuaranteeService.grant(player, entry.clueId());
        }
        player.displayClientMessage(
                Component.literal("§6尸潮已剿灭：奖励已结算。"), false);
    }

    private static ItemStack resolveItemStack(SpawnerEntry.RewardEntry reward) {
        ResourceLocation id = ResourceLocation.tryParse(reward.itemId());
        if (id == null) {
            return ItemStack.EMPTY;
        }
        var item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, reward.count());
    }

    // ==================== 配置界面（服务端权威） ====================

    /** 编辑权限：创造模式或 2 级权限（刷怪箱是服主布置的剧情设备）。 */
    public static boolean canEdit(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.CREATIVE
                || player.hasPermissions(2);
    }

    /** 玩家请求打开配置界面：校验方块与权限，下发快照并（有权限时）开屏。 */
    public static void handleOpenRequest(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        SpawnerEntry entry = resolveEditableEntry(level, pos);
        if (entry == null) {
            SpawnerSync.sendResult(player, false, "那里不是刷怪箱。");
            return;
        }
        boolean canEdit = canEdit(player);
        SpawnerSync.sendSnapshot(player, buildView(level, entry, canEdit));
        if (canEdit) {
            SpawnerSync.openConfigScreen(player, pos);
        } else {
            SpawnerSync.sendResult(player, false, "需要创造模式或 2 级权限才能修改刷怪箱。");
        }
    }

    /** 玩家提交一次配置修改。 */
    public static void handleConfigRequest(ServerPlayer player, BlockPos pos,
                                           Packet_SpawnerConfigRequest.Action action,
                                           int value, boolean flag, String text) {
        if (!canEdit(player)) {
            SpawnerSync.sendResult(player, false, "没有修改权限。");
            return;
        }
        ServerLevel level = player.serverLevel();
        // 防止隔着半个世界远程改别人的刷怪箱。
        if (player.blockPosition().distSqr(pos) > 64.0D * 64.0D) {
            SpawnerSync.sendResult(player, false, "离刷怪箱太远了（超过 64 格）。");
            return;
        }
        SpawnerEntry entry = resolveEditableEntry(level, pos);
        if (entry == null) {
            SpawnerSync.sendResult(player, false, "那里不是刷怪箱。");
            return;
        }
        String message = applyEdit(entry, action, value, flag, text, level.getGameTime());
        if (message == null) {
            SpawnerSync.sendResult(player, false, "这个修改不被接受。");
            return;
        }
        SpawnerRegistry.markChanged();
        SpawnerSync.sendResult(player, true, message);
        SpawnerSync.sendSnapshot(player, buildView(level, entry, true));
    }

    /** 找出某个坐标上的可编辑条目：方块必须是刷怪箱，登记缺失时补登记。 */
    private static SpawnerEntry resolveEditableEntry(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null
                || !level.getBlockState(pos).is(DreamingFishCore_Blocks.SPAWNER.get())) {
            return null;
        }
        String dimensionId = level.dimension().location().toString();
        SpawnerEntry entry = SpawnerRegistry
                .find(dimensionId, pos.getX(), pos.getY(), pos.getZ()).orElse(null);
        if (entry == null) {
            SpawnerRegistry.register(dimensionId, pos.getX(), pos.getY(), pos.getZ());
            entry = SpawnerRegistry
                    .find(dimensionId, pos.getX(), pos.getY(), pos.getZ()).orElse(null);
        }
        return entry;
    }

    /** 组装下发视图。 */
    static SpawnerView buildView(ServerLevel level, SpawnerEntry entry, boolean canEdit) {
        BlockPos pos = new BlockPos(entry.x(), entry.y(), entry.z());
        boolean inHordeArea = TaskLocationManager.isHordeArea(level, pos);
        BlockState state = level.getBlockState(pos);
        boolean active = state.is(DreamingFishCore_Blocks.SPAWNER.get())
                && state.getValue(SpawnerBlock.ACTIVE);
        return SpawnerView.of(entry, canEdit, inHordeArea, active);
    }

    /**
     * 应用一次修改并回一句给玩家看的结果。
     *
     * <p>全部数值都走 {@link SpawnerEntry} 的 setter（自带上下限）：
     * 客户端提交的任何数值都当作不可信输入，这里只做"能不能解析"的检查。</p>
     *
     * @return 结果文案；返回 {@code null} 表示这次修改不被接受
     */
    static String applyEdit(SpawnerEntry entry, Packet_SpawnerConfigRequest.Action action,
                            int value, boolean flag, String text, long now) {
        switch (action) {
            case SET_ENTITY -> {
                if (resolveEntityType(text) == null) {
                    return null;
                }
                entry.setEntityId(text);
                return "刷的怪改为 " + entry.entityId();
            }
            case SET_DETECTION_RADIUS -> {
                entry.setDetectionRadius(value);
                return "检测范围 = " + entry.detectionRadius() + " 格";
            }
            case SET_SPAWN_RADIUS -> {
                entry.setSpawnRadius(value);
                return "刷怪半径 = " + entry.spawnRadius() + " 格";
            }
            case SET_SPAWN_COUNT -> {
                entry.setSpawnCount(value);
                return "每批数量 = " + entry.spawnCount();
            }
            case SET_COOLDOWN -> {
                entry.setCooldownTicks(value);
                return "刷怪冷却 = " + entry.cooldownTicks() + " tick（"
                        + Math.round(entry.cooldownTicks() / 20.0F) + " 秒）";
            }
            case SET_BATCHES -> {
                entry.setBatches(value);
                return "批次 = " + entry.batches();
            }
            case SET_REDSTONE -> {
                entry.setRedstoneControlled(flag);
                return "红石控制：" + (flag ? "需要持续供电" : "忽略红石");
            }
            case SET_SELF_DESTRUCT -> {
                entry.setSelfDestructWhenCleared(flag);
                return "剿灭后自毁：" + (flag ? "开" : "关");
            }
            case SET_CLUE_ENABLED -> {
                entry.setFixedClueEnabled(flag);
                return "固定线索：" + (flag ? "开" : "关");
            }
            case SET_CLUE_ID -> {
                entry.setClueId(value);
                return "线索编号 = " + entry.clueId() + (entry.clueId() == 0 ? "（未指定，不发放）" : "");
            }
            case SET_REWARD_EXPERIENCE -> {
                entry.setRewardExperience(value);
                return "奖励经验 = " + entry.rewardExperience();
            }
            case SET_REWARD_COINS -> {
                entry.setRewardCoins(value);
                return "奖励梦鱼币 = " + entry.rewardCoins();
            }
            case ADD_REWARD_ITEM -> {
                SpawnerEntry.RewardEntry reward = parseReward(text);
                if (reward == null) {
                    return null;
                }
                if (entry.rewardItems().size() >= SpawnerEntry.REWARD_ITEM_ENTRIES_MAX) {
                    return "奖励条目已达上限（" + SpawnerEntry.REWARD_ITEM_ENTRIES_MAX + " 条）";
                }
                entry.rewardItems().add(reward);
                return "已添加奖励物品 " + reward.itemId() + " ×" + reward.count();
            }
            case REMOVE_REWARD_ITEM -> {
                List<SpawnerEntry.RewardEntry> items = entry.rewardItems();
                if (value < 0 || value >= items.size()) {
                    return null;
                }
                SpawnerEntry.RewardEntry removed = items.remove(value);
                return "已移除奖励物品 " + removed.itemId();
            }
            case RESET_ROUND -> {
                entry.resetRound(now);
                return "已重置本轮（批次归零，等冷却后重开）";
            }
            default -> {
                return null;
            }
        }
    }

    /** 解析 "minecraft:apple" 或 "minecraft:apple x8" 形式的奖励物品。 */
    private static SpawnerEntry.RewardEntry parseReward(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String raw = text.trim();
        int count = 1;
        int separator = raw.lastIndexOf(' ');
        if (separator > 0) {
            String tail = raw.substring(separator + 1).trim().toLowerCase(java.util.Locale.ROOT);
            String digits = tail.startsWith("x") ? tail.substring(1) : tail;
            try {
                count = Integer.parseInt(digits);
                raw = raw.substring(0, separator).trim();
            } catch (NumberFormatException exception) {
                // 不是数量后缀，按纯物品 id 处理。
            }
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null || BuiltInRegistries.ITEM.getOptional(id).isEmpty()) {
            return null;
        }
        return new SpawnerEntry.RewardEntry(id.toString(), count);
    }

    // ==================== 工具 ====================

    private static void setActive(ServerLevel level, BlockPos pos, boolean active) {
        BlockState state = level.getBlockState(pos);
        if (state.is(DreamingFishCore_Blocks.SPAWNER.get())
                && state.getValue(SpawnerBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(SpawnerBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    /** 把存档里的维度字符串解析成服务端世界；解析失败返回 null。 */
    static ServerLevel resolveLevel(MinecraftServer server, String dimensionId) {
        if (server == null || dimensionId == null || dimensionId.isBlank()) {
            return null;
        }
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(dimensionId));
            return server.getLevel(key);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
