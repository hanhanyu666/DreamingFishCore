package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 玩家一次待处理死亡的持久化记录。
 *
 * <p>新版本保存尸体 UUID，物品本体由尸体实体随区块持久化；旧版本的物品栏快照
 * 仍保留读取能力，避免升级后卡住尚未结算的死亡记录。</p>
 */
public final class PendingDeathData {
    private static final String ROOT_KEY = "DreamingFishCore_PendingDeath";
    private static final String STATE_PENDING = "pending";
    private static final String STATE_RESOLVING = "resolving";
    private static final int SCHEMA_VERSION = 3;

    /**
     * 已结算但尚未定位到的尸体。
     *
     * <p>尸体所在区块未加载时，复活结算可能找不到实体；死亡记录随后会被完成并清除。
     * 一旦丢掉尸体引用，尸体实体会带着 {@code Resolved=false} 永久停在“等待复活结算”，
     * 玩家再也无法领取。这里独立保存“这次死亡已经结算、这具尸体还等着被恢复为可取回”
     * 的意图，供 {@code DeathCorpseManager} 重试定位和尸体加载后自愈使用。</p>
     */
    private static final String SETTLED_CORPSE_KEY = "DreamingFishCore_SettledCorpse";

    private static final String LEGACY_PENDING = "DreamingFishCore_DeathPending";
    private static final String LEGACY_RESPAWN_POINT = "DreamingFishCore_DeathRespawnPoint";
    private static final String LEGACY_NORMAL_COST = "DreamingFishCore_DeathNormalCost";
    private static final String LEGACY_KEEP_COST = "DreamingFishCore_DeathKeepInventoryCost";
    private static final String LEGACY_INFECTED = "DreamingFishCore_DeathIsInfected";
    private static final String LEGACY_X = "DreamingFishCore_DeathX";
    private static final String LEGACY_Y = "DreamingFishCore_DeathY";
    private static final String LEGACY_Z = "DreamingFishCore_DeathZ";
    private static final String LEGACY_DIMENSION = "DreamingFishCore_DeathDimension";
    private static final String LEGACY_MESSAGE = "DreamingFishCore_DeathMessage";

    private PendingDeathData() {
    }

    public static UUID begin(ServerPlayer player, float respawnPoint, float normalCost,
                             float keepInventoryCost, boolean infected, Component deathMessage,
                             UUID corpseId) {
        UUID deathId = UUID.randomUUID();
        CompoundTag record = new CompoundTag();
        record.putInt("SchemaVersion", SCHEMA_VERSION);
        record.putUUID("DeathId", deathId);
        record.putUUID("CorpseId", corpseId);
        record.putString("State", STATE_PENDING);
        record.putFloat("RespawnPoint", respawnPoint);
        record.putFloat("NormalCost", normalCost);
        record.putFloat("KeepInventoryCost", keepInventoryCost);
        record.putBoolean("IsInfected", infected);
        record.putDouble("DeathX", player.getX());
        record.putDouble("DeathY", player.getY());
        record.putDouble("DeathZ", player.getZ());
        record.putString("DeathDimension", player.level().dimension().location().toString());
        record.putString("DeathMessage", Component.Serializer.toJson(deathMessage, player.registryAccess()));

        player.getPersistentData().put(ROOT_KEY, record);
        clearLegacyTags(player.getPersistentData());

        return deathId;
    }

    public static boolean hasPending(ServerPlayer player) {
        CompoundTag record = getRecord(player);
        if (record == null && player.getPersistentData().getBoolean(LEGACY_PENDING)) {
            record = migrateLegacyData(player);
        }
        if (record == null) {
            return false;
        }

        String state = record.getString("State");
        return STATE_PENDING.equals(state) || STATE_RESOLVING.equals(state);
    }

    /**
     * 不执行旧数据迁移的轻量检查，供死亡掉落 Mixin 使用。
     */
    public static boolean hasPendingRecord(Player player) {
        CompoundTag record = getRecord(player);
        return record != null && (STATE_PENDING.equals(record.getString("State"))
                || STATE_RESOLVING.equals(record.getString("State")));
    }

    public static UUID getDeathId(ServerPlayer player) {
        CompoundTag record = requireRecord(player);
        return record.hasUUID("DeathId") ? record.getUUID("DeathId") : new UUID(0L, 0L);
    }

    public static boolean hasCorpseReference(ServerPlayer player) {
        CompoundTag record = requireRecord(player);
        return record.hasUUID("CorpseId");
    }

    public static java.util.Optional<UUID> getCorpseId(ServerPlayer player) {
        CompoundTag record = requireRecord(player);
        return record.hasUUID("CorpseId")
                ? java.util.Optional.of(record.getUUID("CorpseId"))
                : java.util.Optional.empty();
    }

    public static void markCorpseCreated(ServerPlayer player,
                                         UUID corpseId,
                                         boolean hadItems,
                                         DeathLocation location,
                                         boolean dangerRelocated) {
        CompoundTag record = getRecord(player);
        if (record == null || !record.hasUUID("CorpseId") || !record.getUUID("CorpseId").equals(corpseId)) {
            return;
        }
        record.putBoolean("CorpseCreated", true);
        record.putBoolean("CorpseHadItems", hadItems);
        writeCorpseLocation(record, location, dangerRelocated);
    }

    /** 更新尸体因虚空保护等原因发生移动后的实际位置。 */
    public static void updateCorpseLocation(ServerPlayer player,
                                            UUID corpseId,
                                            DeathLocation location,
                                            boolean dangerRelocated) {
        CompoundTag record = getRecord(player);
        if (record == null || !record.hasUUID("CorpseId") || !record.getUUID("CorpseId").equals(corpseId)) {
            return;
        }
        writeCorpseLocation(record, location, dangerRelocated);
    }

    /**
     * 当服务器已经载入尸体实体、但玩家记录中的 UUID 过期时修复引用。
     *
     * <p>这里只接受当前仍处于待结算状态的记录，避免把已经完成的死亡记录重新挂回玩家身上。</p>
     */
    public static boolean repairCorpseReference(ServerPlayer player,
                                                UUID corpseId,
                                                DeathLocation location,
                                                boolean dangerRelocated,
                                                boolean hadItems) {
        if (player == null || corpseId == null) {
            return false;
        }
        CompoundTag record = getRecord(player);
        if (record == null) {
            return false;
        }
        String state = record.getString("State");
        if (!STATE_PENDING.equals(state) && !STATE_RESOLVING.equals(state)) {
            return false;
        }

        record.putUUID("CorpseId", corpseId);
        record.putBoolean("CorpseCreated", true);
        record.putBoolean("CorpseHadItems", hadItems);
        if (location != null) {
            writeCorpseLocation(record, location, dangerRelocated);
        }
        return true;
    }

    public static boolean wasCorpseCreated(ServerPlayer player) {
        return requireRecord(player).getBoolean("CorpseCreated");
    }

    public static boolean corpseHadItems(ServerPlayer player) {
        return requireRecord(player).getBoolean("CorpseHadItems");
    }

    /**
     * 仅在玩家重新进入服务器、重新展示死亡界面时恢复中断的结算。
     */
    public static void recoverInterruptedResolution(ServerPlayer player) {
        CompoundTag record = requireRecord(player);
        if (STATE_RESOLVING.equals(record.getString("State"))) {
            record.putString("State", STATE_PENDING);
            DreamingFishCore.LOGGER.warn("玩家 {} 上次死亡结算被中断，已恢复为待选择状态",
                    player.getScoreboardName());
        }
    }

    public static boolean beginResolution(ServerPlayer player, UUID deathId) {
        if (deathId == null || !hasPending(player)) {
            return false;
        }
        CompoundTag record = requireRecord(player);
        if (!record.hasUUID("DeathId") || !record.getUUID("DeathId").equals(deathId)
                || !STATE_PENDING.equals(record.getString("State"))) {
            return false;
        }
        record.putString("State", STATE_RESOLVING);
        return true;
    }

    public static void rollbackResolution(ServerPlayer player, UUID deathId) {
        CompoundTag record = getRecord(player);
        if (record != null && record.hasUUID("DeathId") && record.getUUID("DeathId").equals(deathId)) {
            record.putString("State", STATE_PENDING);
        }
    }

    public static void complete(ServerPlayer player, UUID deathId) {
        CompoundTag record = getRecord(player);
        if (record != null && record.hasUUID("DeathId") && record.getUUID("DeathId").equals(deathId)) {
            player.getPersistentData().remove(ROOT_KEY);
        }
        clearLegacyTags(player.getPersistentData());
    }

    public static void clear(ServerPlayer player) {
        player.getPersistentData().remove(ROOT_KEY);
        clearLegacyTags(player.getPersistentData());
    }

    public static boolean captureInventory(Player player) {
        CompoundTag record = getRecord(player);
        if (record == null) {
            return false;
        }
        ListTag inventory = player.getInventory().save(new ListTag());
        record.put("Inventory", inventory);
        record.putBoolean("InventorySnapshotReady", true);
        return true;
    }

    public static boolean ensureInventorySnapshot(ServerPlayer player) {
        CompoundTag record = requireRecord(player);
        if (record.getBoolean("InventorySnapshotReady")
                && record.contains("Inventory", Tag.TAG_LIST)) {
            return true;
        }
        DreamingFishCore.LOGGER.warn("玩家 {} 的死亡记录缺少物品快照，使用当前物品栏修复",
                player.getScoreboardName());
        return captureInventory(player);
    }

    public static ListTag getInventorySnapshot(ServerPlayer player) {
        CompoundTag record = requireRecord(player);
        return (ListTag) record.getList("Inventory", Tag.TAG_COMPOUND).copy();
    }

    public static DeathLocation getDeathLocation(ServerPlayer player) {
        CompoundTag record = requireRecord(player);
        String dimension = record.getString("DeathDimension");
        if (dimension.isBlank()) {
            dimension = player.level().dimension().location().toString();
        }
        return new DeathLocation(
                dimension,
                record.getDouble("DeathX"),
                record.getDouble("DeathY"),
                record.getDouble("DeathZ"));
    }

    /**
     * 尸体当前持久化位置；旧记录没有该字段时回退到死亡位置。
     */
    public static DeathLocation getCorpseLocation(ServerPlayer player) {
        CompoundTag record = requireRecord(player);
        String dimension = record.getString("CorpseDimension");
        if (dimension.isBlank()) {
            return getDeathLocation(player);
        }
        return new DeathLocation(
                dimension,
                record.getDouble("CorpseX"),
                record.getDouble("CorpseY"),
                record.getDouble("CorpseZ"));
    }

    public static boolean wasCorpseDangerRelocated(ServerPlayer player) {
        return requireRecord(player).getBoolean("CorpseDangerRelocated");
    }

    /**
     * 记录“这次死亡已经结算、尸体还没被定位到”。
     *
     * <p>必须在死亡记录被清除之后依然存在，否则找不到尸体的那次结算会永久丢失尸体引用。</p>
     */
    public static void markSettledCorpse(ServerPlayer player,
                                         UUID corpseId,
                                         boolean locked,
                                         DeathLocation location) {
        if (player == null || corpseId == null) {
            return;
        }
        SettledCorpse.write(player.getPersistentData(), new SettledCorpse(
                corpseId,
                locked,
                location == null ? "" : location.dimension(),
                location == null ? 0.0D : location.x(),
                location == null ? 0.0D : location.y(),
                location == null ? 0.0D : location.z()));
    }

    public static java.util.Optional<SettledCorpse> getSettledCorpse(ServerPlayer player) {
        return player == null ? java.util.Optional.empty() : SettledCorpse.read(player.getPersistentData());
    }

    /** 只在标记仍指向同一具尸体时清除，避免误删随后发生的另一次结算。 */
    public static boolean clearSettledCorpse(ServerPlayer player, UUID corpseId) {
        return player != null && SettledCorpse.clear(player.getPersistentData(), corpseId);
    }

    public static Component getDeathMessage(ServerPlayer player) {
        String json = requireRecord(player).getString("DeathMessage");
        if (!json.isBlank()) {
            try {
                Component message = Component.Serializer.fromJson(json, player.registryAccess());
                if (message != null) {
                    return message;
                }
            } catch (RuntimeException exception) {
                DreamingFishCore.LOGGER.warn("无法解析玩家 {} 的死亡消息", player.getScoreboardName(), exception);
            }
        }
        return Component.literal("您 died");
    }

    private static CompoundTag migrateLegacyData(ServerPlayer player) {
        CompoundTag persistentData = player.getPersistentData();
        CompoundTag record = new CompoundTag();
        UUID deathId = UUID.randomUUID();
        record.putInt("SchemaVersion", SCHEMA_VERSION);
        record.putUUID("DeathId", deathId);
        record.putString("State", STATE_PENDING);
        record.putFloat("RespawnPoint", persistentData.getFloat(LEGACY_RESPAWN_POINT));
        record.putFloat("NormalCost", persistentData.getFloat(LEGACY_NORMAL_COST));
        record.putFloat("KeepInventoryCost", persistentData.getFloat(LEGACY_KEEP_COST));
        record.putBoolean("IsInfected", persistentData.getBoolean(LEGACY_INFECTED));
        record.putDouble("DeathX", persistentData.getDouble(LEGACY_X));
        record.putDouble("DeathY", persistentData.getDouble(LEGACY_Y));
        record.putDouble("DeathZ", persistentData.getDouble(LEGACY_Z));
        String dimension = persistentData.getString(LEGACY_DIMENSION);
        record.putString("DeathDimension", dimension.isBlank()
                ? player.level().dimension().location().toString()
                : dimension);
        record.putString("DeathMessage", persistentData.getString(LEGACY_MESSAGE));

        persistentData.put(ROOT_KEY, record);
        captureInventory(player);
        clearLegacyTags(persistentData);
        DreamingFishCore.LOGGER.info("已将玩家 {} 的旧死亡状态迁移为持久化记录 {}",
                player.getScoreboardName(), deathId);
        return record;
    }

    private static CompoundTag requireRecord(ServerPlayer player) {
        CompoundTag record = getRecord(player);
        if (record == null) {
            throw new IllegalStateException("玩家没有待处理死亡记录：" + player.getUUID());
        }
        return record;
    }

    private static CompoundTag getRecord(Player player) {
        CompoundTag persistentData = player.getPersistentData();
        if (!persistentData.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
            return null;
        }
        return persistentData.getCompound(ROOT_KEY);
    }

    private static void writeCorpseLocation(CompoundTag record,
                                            DeathLocation location,
                                            boolean dangerRelocated) {
        record.putString("CorpseDimension", location.dimension());
        record.putDouble("CorpseX", location.x());
        record.putDouble("CorpseY", location.y());
        record.putDouble("CorpseZ", location.z());
        record.putBoolean("CorpseDangerRelocated", dangerRelocated);
    }

    private static void clearLegacyTags(CompoundTag persistentData) {
        persistentData.remove(LEGACY_PENDING);
        persistentData.remove(LEGACY_RESPAWN_POINT);
        persistentData.remove(LEGACY_NORMAL_COST);
        persistentData.remove(LEGACY_KEEP_COST);
        persistentData.remove(LEGACY_INFECTED);
        persistentData.remove(LEGACY_X);
        persistentData.remove(LEGACY_Y);
        persistentData.remove(LEGACY_Z);
        persistentData.remove(LEGACY_DIMENSION);
        persistentData.remove(LEGACY_MESSAGE);
    }

    public record DeathLocation(String dimension, double x, double y, double z) {
    }

    /**
     * 一次已经结算、但当时没能定位到实体的尸体。
     *
     * <p>{@code locked} 保留玩家在死亡界面上选择的尸体拾取权限，避免延迟结算时丢掉选择；
     * 位置用于重试期间重新请求区块加载。</p>
     */
    public record SettledCorpse(UUID corpseId,
                                boolean locked,
                                String dimension,
                                double x,
                                double y,
                                double z) {

        static void write(CompoundTag persistentData, SettledCorpse settled) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("CorpseId", settled.corpseId());
            tag.putBoolean("Locked", settled.locked());
            tag.putString("Dimension", settled.dimension() == null ? "" : settled.dimension());
            tag.putDouble("X", settled.x());
            tag.putDouble("Y", settled.y());
            tag.putDouble("Z", settled.z());
            persistentData.put(SETTLED_CORPSE_KEY, tag);
        }

        static java.util.Optional<SettledCorpse> read(CompoundTag persistentData) {
            if (persistentData == null || !persistentData.contains(SETTLED_CORPSE_KEY, Tag.TAG_COMPOUND)) {
                return java.util.Optional.empty();
            }
            CompoundTag tag = persistentData.getCompound(SETTLED_CORPSE_KEY);
            if (!tag.hasUUID("CorpseId")) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new SettledCorpse(
                    tag.getUUID("CorpseId"),
                    tag.getBoolean("Locked"),
                    tag.getString("Dimension"),
                    tag.getDouble("X"),
                    tag.getDouble("Y"),
                    tag.getDouble("Z")));
        }

        static boolean clear(CompoundTag persistentData, UUID corpseId) {
            java.util.Optional<SettledCorpse> settled = read(persistentData);
            if (settled.isEmpty() || (corpseId != null && !settled.get().corpseId().equals(corpseId))) {
                return false;
            }
            persistentData.remove(SETTLED_CORPSE_KEY);
            return true;
        }
    }
}
