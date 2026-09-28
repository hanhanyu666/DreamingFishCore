package com.hhy.dreamingfishcore.gameplay.clue_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.init.CommonInit;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 随机丧尸线索掉落的服务器配置。
 *
 * <p>文件位置：{@code config/dreamingfishcore/clue_drop.json}。概率使用**小数百分比**，
 * 与 {@code 0.01%} 这样的说法一一对应（整数百分比表达不了低于 1% 的概率）。
 * 服主可以不改代码直接调整概率或整体关闭掉落。</p>
 *
 * <p>与项目其他配置一致：文件缺失时写入默认值；文件损坏时只使用内存默认值，
 * 不覆盖服主原有的文件。</p>
 */
public final class ClueDropConfig {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static final boolean DEFAULT_ENABLED = true;
    /** 0.01%：约一万次击杀掉一张残页。 */
    public static final double DEFAULT_NORMAL_ZOMBIE_DROP_PERCENT = 0.01D;
    public static final double DEFAULT_SIEGE_ZOMBIE_DROP_PERCENT = 0.01D;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static volatile ClueDropConfig current = defaults();

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private boolean enabled = DEFAULT_ENABLED;
    private double normalZombieDropPercent = DEFAULT_NORMAL_ZOMBIE_DROP_PERCENT;
    private double siegeZombieDropPercent = DEFAULT_SIEGE_ZOMBIE_DROP_PERCENT;

    public ClueDropConfig() {
    }

    public static synchronized ClueDropConfig init() {
        current = load();
        return current;
    }

    public static synchronized ClueDropConfig reload() {
        current = load();
        return current;
    }

    /** 运行期查询使用的快照；不会读取磁盘。 */
    public static ClueDropConfig current() {
        return current;
    }

    public static ClueDropConfig load() {
        return load(getConfigPath());
    }

    /** 供测试与工具使用的路径重载版本。 */
    public static synchronized ClueDropConfig load(Path path) {
        ClueDropConfig defaults = defaults();

        if (Files.notExists(path)) {
            try {
                JsonDataStore.writeAtomic(path, GSON, defaults);
            } catch (IOException exception) {
                DreamingFishCore.LOGGER.warn("线索掉落配置默认文件创建失败：{}", path, exception);
            }
            return defaults;
        }

        try {
            if (Files.size(path) == 0L) {
                throw new IOException("配置文件为空");
            }

            ClueDropConfig loaded = JsonDataStore.read(
                    path,
                    GSON,
                    ClueDropConfig.class,
                    ClueDropConfig::new);
            if (loaded.validateAndNormalize()) {
                try {
                    JsonDataStore.writeAtomic(path, GSON, loaded);
                } catch (IOException exception) {
                    DreamingFishCore.LOGGER.warn("线索掉落配置修正结果写回失败，将继续使用内存中的配置：{}", path, exception);
                }
            }
            return loaded;
        } catch (Exception exception) {
            // 损坏文件保持原样，避免把服主的内容覆盖成默认值。
            DreamingFishCore.LOGGER.warn("线索掉落配置损坏，本次仅使用内存默认值：{}", path, exception);
            return defaults;
        }
    }

    public static Path getConfigPath() {
        return CommonInit.CONFIG_DIRECTORY.resolve("clue_drop.json");
    }

    private static ClueDropConfig defaults() {
        return new ClueDropConfig();
    }

    /** 掉落总开关。 */
    public boolean isEnabled() {
        return enabled;
    }

    /** 普通丧尸（僵尸、尸壳）掉落概率百分比，0 表示不掉。 */
    public double getNormalZombieDropPercent() {
        return enabled ? normalZombieDropPercent : 0.0D;
    }

    /** 攻城丧尸掉落概率百分比，0 表示不掉。 */
    public double getSiegeZombieDropPercent() {
        return enabled ? siegeZombieDropPercent : 0.0D;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    /**
     * 校验并就地修正配置。
     *
     * @return 是否发生了修正（需要写回文件）
     */
    private boolean validateAndNormalize() {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("不支持的线索掉落配置版本：" + schemaVersion);
        }

        boolean changed = false;
        double normalizedNormal = clampPercent(normalZombieDropPercent);
        if (normalizedNormal != normalZombieDropPercent) {
            normalZombieDropPercent = normalizedNormal;
            changed = true;
        }

        double normalizedSiege = clampPercent(siegeZombieDropPercent);
        if (normalizedSiege != siegeZombieDropPercent) {
            siegeZombieDropPercent = normalizedSiege;
            changed = true;
        }

        return changed;
    }

    /**
     * 把概率限制在 {@code [0, 100]}。
     *
     * <p>NaN 与无穷大回落到 0：这类值只可能来自手改坏的文件，按“不掉落”处理比按 100% 更安全。</p>
     */
    static double clampPercent(double percent) {
        if (Double.isNaN(percent) || Double.isInfinite(percent)) {
            return 0.0D;
        }
        if (percent < 0.0D) {
            return 0.0D;
        }
        return Math.min(percent, 100.0D);
    }
}
