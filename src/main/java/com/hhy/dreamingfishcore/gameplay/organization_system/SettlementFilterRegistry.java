package com.hhy.dreamingfishcore.gameplay.organization_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 聚居地过滤装置的登记表（世界存档数据）。
 *
 * <p>为什么状态不放在方块实体里：维护周期要遍历全服设备、抑制判定也要按坐标快速查询，
 * 而区块里的方块实体只能靠扫描已加载区块枚举。所以这里用一份按
 * {@code 维度|X|Y|Z} 索引的登记表当唯一真相，方块本身只是"这里有一台设备"的标记。
 * 这样不会出现"区块 NBT 与全局表两份真相对不上"的问题，代价是方块被外部手段清除时，
 * 需要由维护周期按坐标校验并摘除（见 {@link SettlementFilterService}）。</p>
 *
 * <p>落盘时机与其他世界数据一致：标脏后由 {@code WorldDataLifecycleEvents} 定期与关服时写入；
 * 读档失败时进入只读保护，绝不用空表覆盖玩家存档。</p>
 */
public final class SettlementFilterRegistry {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String DATA_FILE = "settlement_filters.json";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final Map<String, Device> DEVICES = new LinkedHashMap<>();

    private static boolean loaded;
    private static boolean dirty;
    private static boolean persistenceUnsafe;

    private SettlementFilterRegistry() {
    }

    /** 一台设备的登记事实。 */
    public static final class Device {
        private String dimensionId = "";
        private int x;
        private int y;
        private int z;
        /** 绑定的组织 id；空串表示尚未绑定（设备存在但不工作）。 */
        private String organizationId = "";
        private boolean active;
        /** 上一次完成维护的活动 tick；-1 表示还没排过期。 */
        private long lastMaintenanceActiveTick = -1L;

        public Device() {
        }

        public Device(String dimensionId, int x, int y, int z) {
            this.dimensionId = dimensionId == null ? "" : dimensionId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String dimensionId() {
            return dimensionId == null ? "" : dimensionId;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int z() {
            return z;
        }

        public String organizationId() {
            return organizationId == null ? "" : organizationId;
        }

        public void setOrganizationId(String value) {
            this.organizationId = value == null ? "" : value;
        }

        public boolean bound() {
            return !organizationId().isBlank();
        }

        public boolean active() {
            return active && bound();
        }

        public void setActive(boolean value) {
            this.active = value;
        }

        public long lastMaintenanceActiveTick() {
            return lastMaintenanceActiveTick;
        }

        public void setLastMaintenanceActiveTick(long value) {
            this.lastMaintenanceActiveTick = value;
        }

        public String key() {
            return keyOf(dimensionId(), x, y, z);
        }

        /** 归一：修掉空维度与越界坐标。返回是否发生了修改。 */
        boolean normalize() {
            boolean changed = false;
            if (dimensionId == null || dimensionId.isBlank()) {
                dimensionId = "";
                changed = true;
            }
            if (lastMaintenanceActiveTick < -1L) {
                lastMaintenanceActiveTick = -1L;
                changed = true;
            }
            if (organizationId == null) {
                organizationId = "";
                changed = true;
            }
            return changed;
        }

        boolean valid() {
            return !dimensionId().isBlank();
        }
    }

    /** 存档文档包装，带 schemaVersion 便于将来升级。 */
    private static final class Document {
        private int schemaVersion = CURRENT_SCHEMA_VERSION;
        private List<Device> devices = new ArrayList<>();
    }

    public static String keyOf(String dimensionId, int x, int y, int z) {
        return (dimensionId == null ? "" : dimensionId) + "|" + x + "|" + y + "|" + z;
    }

    // ==================== 生命周期 ====================

    public static synchronized void loadWorldData(MinecraftServer server) {
        DEVICES.clear();
        loaded = false;
        dirty = false;
        persistenceUnsafe = false;
        if (server == null) {
            return;
        }
        try {
            Document document = JsonDataStore.read(
                    WorldDataPaths.resolve(server, DATA_FILE),
                    GSON,
                    Document.class,
                    Document::new);
            if (document == null) {
                enterReadOnlyProtection("聚居地设备数据解析为空");
                return;
            }
            if (document.schemaVersion != CURRENT_SCHEMA_VERSION) {
                enterReadOnlyProtection("聚居地设备数据版本不支持（文件 " + document.schemaVersion
                        + "，本版本 " + CURRENT_SCHEMA_VERSION + "）");
                return;
            }
            int skipped = 0;
            for (Device device : document.devices == null ? List.<Device>of() : document.devices) {
                if (device == null || !device.valid()) {
                    skipped++;
                    continue;
                }
                device.normalize();
                DEVICES.put(device.key(), device);
            }
            loaded = true;
            DreamingFishCore.LOGGER.info("聚居地设备加载完成：{} 台，跳过 {} 条",
                    DEVICES.size(), skipped);
        } catch (IOException | RuntimeException exception) {
            enterReadOnlyProtection("聚居地设备数据加载失败", exception);
        }
    }

    public static synchronized boolean saveIfDirty(MinecraftServer server) {
        if (persistenceUnsafe) {
            if (dirty) {
                DreamingFishCore.LOGGER.error("聚居地设备处于只读保护，拒绝写盘（避免用空表覆盖存档）");
            }
            return false;
        }
        if (!loaded || !dirty || server == null) {
            return true;
        }
        Document document = new Document();
        document.schemaVersion = CURRENT_SCHEMA_VERSION;
        document.devices = new ArrayList<>(DEVICES.values());
        try {
            JsonDataStore.writeAtomic(WorldDataPaths.resolve(server, DATA_FILE), GSON, document);
            dirty = false;
            return true;
        } catch (IOException exception) {
            DreamingFishCore.LOGGER.error("聚居地设备数据写盘失败", exception);
            return false;
        }
    }

    public static synchronized void clearWorldCache() {
        DEVICES.clear();
        loaded = false;
        dirty = false;
        persistenceUnsafe = false;
    }

    public static synchronized boolean isLoaded() {
        return loaded;
    }

    private static void enterReadOnlyProtection(String reason) {
        enterReadOnlyProtection(reason, null);
    }

    private static void enterReadOnlyProtection(String reason, Throwable cause) {
        persistenceUnsafe = true;
        loaded = true;
        if (cause == null) {
            DreamingFishCore.LOGGER.error("{}，本次启动进入只读保护（不会覆盖存档，重启后自动重试）", reason);
        } else {
            DreamingFishCore.LOGGER.error("{}，本次启动进入只读保护（不会覆盖存档，重启后自动重试）", reason, cause);
        }
    }

    private static void markDirty() {
        dirty = true;
    }

    private static boolean writable() {
        return loaded && !persistenceUnsafe;
    }

    // ==================== 查询与变更 ====================

    public static synchronized Optional<Device> find(String dimensionId, int x, int y, int z) {
        return Optional.ofNullable(DEVICES.get(keyOf(dimensionId, x, y, z)));
    }

    public static synchronized List<Device> all() {
        return List.copyOf(DEVICES.values());
    }

    public static synchronized List<Device> devicesOf(String organizationId) {
        if (organizationId == null || organizationId.isBlank()) {
            return List.of();
        }
        return DEVICES.values().stream()
                .filter(device -> organizationId.equals(device.organizationId()))
                .toList();
    }

    public static synchronized int countOf(String organizationId) {
        return devicesOf(organizationId).size();
    }

    /** 正在工作的设备（供抑制判定与终端显示）。 */
    public static synchronized List<Device> activeDevices() {
        return DEVICES.values().stream().filter(Device::active).toList();
    }

    /** 登记一台设备（已存在时保留原绑定与状态）。 */
    public static synchronized boolean register(String dimensionId, int x, int y, int z) {
        if (!writable()) {
            return false;
        }
        String key = keyOf(dimensionId, x, y, z);
        if (DEVICES.containsKey(key)) {
            return true;
        }
        DEVICES.put(key, new Device(dimensionId, x, y, z));
        markDirty();
        return true;
    }

    public static synchronized boolean unregister(String dimensionId, int x, int y, int z) {
        if (!writable()) {
            return false;
        }
        if (DEVICES.remove(keyOf(dimensionId, x, y, z)) != null) {
            markDirty();
            return true;
        }
        return false;
    }

    /** 绑定组织（设备必须先在登记表里）。 */
    public static synchronized boolean bind(String dimensionId, int x, int y, int z, String organizationId) {
        Device device = DEVICES.get(keyOf(dimensionId, x, y, z));
        if (!writable() || device == null) {
            return false;
        }
        device.setOrganizationId(organizationId);
        // 刚绑定还没扣过维护费，先不工作；下一次维护周期决定它能不能开工。
        device.setActive(false);
        device.setLastMaintenanceActiveTick(-1L);
        markDirty();
        return true;
    }

    /** 解除绑定（设备还在，只是不工作）。 */
    public static synchronized boolean unbind(String dimensionId, int x, int y, int z) {
        Device device = DEVICES.get(keyOf(dimensionId, x, y, z));
        if (!writable() || device == null || !device.bound()) {
            return false;
        }
        device.setOrganizationId("");
        device.setActive(false);
        device.setLastMaintenanceActiveTick(-1L);
        markDirty();
        return true;
    }

    /** 更新工作状态与维护时间（由维护周期调用）。 */
    public static synchronized void updateState(String dimensionId, int x, int y, int z,
                                                boolean active, long maintenanceTick) {
        Device device = DEVICES.get(keyOf(dimensionId, x, y, z));
        if (device == null) {
            return;
        }
        if (device.active() != active || device.lastMaintenanceActiveTick() != maintenanceTick) {
            device.setActive(active);
            device.setLastMaintenanceActiveTick(maintenanceTick);
            markDirty();
        }
    }

    /** 清空某个组织的全部设备绑定（组织解散时调用）。 */
    public static synchronized int unbindAllOf(String organizationId) {
        if (organizationId == null || organizationId.isBlank()) {
            return 0;
        }
        int changed = 0;
        for (Device device : DEVICES.values()) {
            if (organizationId.equals(device.organizationId())) {
                device.setOrganizationId("");
                device.setActive(false);
                device.setLastMaintenanceActiveTick(-1L);
                changed++;
            }
        }
        if (changed > 0) {
            markDirty();
        }
        return changed;
    }
}
