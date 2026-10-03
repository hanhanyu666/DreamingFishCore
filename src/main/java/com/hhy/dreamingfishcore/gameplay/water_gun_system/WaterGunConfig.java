package com.hhy.dreamingfishcore.gameplay.water_gun_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.init.CommonInit;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 呲水枪的服务器端数值配置，写在 {@code config/dreamingfishcore/water_gun.json}。
 *
 * <p>沿用项目既有做法：JSON + {@code /dreamingfish zombie reload} 热重载，字段有范围校验，
 * 坏配置只被拒绝并保留上一份有效值。</p>
 */
public final class WaterGunConfig {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String CONFIG_FILE_NAME = "water_gun.json";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static volatile WaterGunConfig current = defaults();

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    /** 总开关；关掉后水枪不再喷水（左键不射、右键不喷自己、也不能装水）。 */
    private boolean enabled = true;

    /**
     * 一次装满的水量，也是每发消耗的基准（每发固定消耗 1 点）。
     *
     * <p>10 发：够把一只金刚僵尸浇到锈透（3 层）并且还有余量灭火，但不足以无脑乱喷。</p>
     */
    private int capacity = 10;

    /** 两发之间的最小间隔（tick，默认 6 = 约 3.3 发/秒）。服务端按它做冷却校验。 */
    private int fireIntervalTicks = 6;

    /** 水柱初速度（格/tick）。 */
    private double jetSpeed = 1.2D;
    /** 水柱每 tick 的下坠（0.02 略有弧度，比直线好看且不影响手感）。 */
    private double jetGravity = 0.02D;
    /** 水柱射程：离开发射点超过这个距离就消散（默认 8 格，短射程是「呲水枪」的定位）。 */
    private double jetMaxRange = 8.0D;

    /** 水柱命中一次金刚僵尸涨几层锈。 */
    private int rustStagesPerHit = 1;

    /** 水柱命中生物/玩家时是否扑灭其身上的火。 */
    private boolean extinguishEntities = true;

    /** 水柱命中方块时是否扑灭火方块与营火。 */
    private boolean extinguishBlockFire = true;

    /** 装水时是否一次装满（关掉则每次只加 {@link #refillAmount}）。 */
    private boolean refillToFull = true;
    /** 非一次装满时每次加多少。 */
    private int refillAmount = 5;

    /** 从炼药锅装水时是否消耗锅里的水（关掉等于无限水源）。 */
    private boolean drainWaterCauldron = true;

    /** Gson 需要一个无参构造器。 */
    public WaterGunConfig() {
    }

    public static synchronized WaterGunConfig init() {
        current = load(getConfigPath());
        return current;
    }

    public static synchronized WaterGunConfig reload() {
        return reload(getConfigPath());
    }

    static synchronized WaterGunConfig reload(Path path) {
        if (Files.notExists(path)) {
            current = load(path);
            return current;
        }
        try {
            WaterGunConfig loaded = readValidated(path);
            current = loaded;
            return loaded;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.warn("呲水枪配置重载失败，继续使用上一份有效配置：{}", path, exception);
            throw new IllegalStateException("呲水枪配置无效，已保留上一份有效设置", exception);
        }
    }

    public static WaterGunConfig current() {
        return current;
    }

    public static Path getConfigPath() {
        return CommonInit.CONFIG_DIRECTORY.resolve(CONFIG_FILE_NAME);
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    static synchronized WaterGunConfig load(Path path) {
        WaterGunConfig defaults = defaults();
        if (Files.notExists(path)) {
            try {
                JsonDataStore.writeAtomic(path, GSON, defaults);
            } catch (IOException exception) {
                DreamingFishCore.LOGGER.warn("呲水枪配置默认文件创建失败：{}", path, exception);
            }
            return defaults;
        }
        try {
            return readValidated(path);
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.warn("呲水枪配置损坏，已仅使用内存默认值：{}", path, exception);
            return defaults;
        }
    }

    private static WaterGunConfig readValidated(Path path) throws IOException {
        if (Files.size(path) == 0L) {
            throw new IOException("配置文件为空");
        }
        WaterGunConfig loaded = JsonDataStore.read(
                path, GSON, WaterGunConfig.class, WaterGunConfig::new);
        loaded.validateAndNormalize();
        return loaded;
    }

    public Resolved resolve() {
        return new Resolved(
                enabled,
                capacity,
                fireIntervalTicks,
                jetSpeed,
                jetGravity,
                jetMaxRange,
                rustStagesPerHit,
                extinguishEntities,
                extinguishBlockFire,
                refillToFull,
                refillAmount,
                drainWaterCauldron);
    }

    /** 不可变快照。 */
    public record Resolved(
            boolean enabled,
            int capacity,
            int fireIntervalTicks,
            double jetSpeed,
            double jetGravity,
            double jetMaxRange,
            int rustStagesPerHit,
            boolean extinguishEntities,
            boolean extinguishBlockFire,
            boolean refillToFull,
            int refillAmount,
            boolean drainWaterCauldron) {
    }

    private static WaterGunConfig defaults() {
        WaterGunConfig config = new WaterGunConfig();
        config.validateAndNormalize();
        return config;
    }

    private void validateAndNormalize() {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("不支持的呲水枪配置版本：" + schemaVersion);
        }
        capacity = requireRange(capacity, 1, 200, "capacity");
        fireIntervalTicks = requireRange(fireIntervalTicks, 0, 20 * 10, "fireIntervalTicks");
        jetSpeed = requireRange(jetSpeed, 0.1D, 10.0D, "jetSpeed");
        jetGravity = requireRange(jetGravity, 0.0D, 0.2D, "jetGravity");
        jetMaxRange = requireRange(jetMaxRange, 1.0D, 64.0D, "jetMaxRange");
        rustStagesPerHit = requireRange(rustStagesPerHit, 0, 9, "rustStagesPerHit");
        refillAmount = requireRange(refillAmount, 1, 200, "refillAmount");
    }

    private static int requireRange(int value, int min, int max, String fieldName) {
        if (value < min || value > max) {
            throw new IllegalStateException(fieldName + " 超出范围 [" + min + ", " + max + "]：" + value);
        }
        return value;
    }

    private static double requireRange(double value, double min, double max, String fieldName) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalStateException(fieldName + " 超出范围 [" + min + ", " + max + "]：" + value);
        }
        return value;
    }
}
