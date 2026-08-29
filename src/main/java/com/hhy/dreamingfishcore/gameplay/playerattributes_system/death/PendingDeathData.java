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
}
