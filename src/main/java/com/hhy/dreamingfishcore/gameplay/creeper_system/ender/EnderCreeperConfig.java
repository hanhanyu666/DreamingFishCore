package com.hhy.dreamingfishcore.gameplay.creeper_system.ender;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.init.CommonInit;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 末影人苦力怕的服务器端数值配置，写在 {@code config/dreamingfishcore/ender_creeper.json}。
 *
 * <p>与幻翼苦力怕同一套做法：JSON + 范围校验 + {@code /dreamingfish zombie reload} 热重载，
 * 坏配置拒绝并保留上一份有效值。本版不接入自然生成。</p>
 */
public final class EnderCreeperConfig {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String CONFIG_FILE_NAME = "ender_creeper.json";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    /** 与 {@link #maxHealth} 的基准对齐：原版苦力怕的基础生命上限。 */
    public static final double DEFAULT_MAX_HEALTH = 20.0D;
    /** 与 {@link #movementSpeed} 的基准对齐：原版苦力怕的基础移速。 */
    public static final double DEFAULT_MOVEMENT_SPEED = 0.25D;
    /** 与 {@link #followRange} 的基准对齐：原版苦力怕的索敌距离。 */
    public static final double DEFAULT_FOLLOW_RANGE = 16.0D;

    private static volatile EnderCreeperConfig current = defaults();

    private int schemaVersion = CURRENT_SCHEMA_VERSION;

    /** 最大生命值。默认与原版苦力怕一致。 */
    private double maxHealth = 20.0D;
    /** 地面移速。它主要靠瞬移拉近，这个值只影响贴身之后的最后几步。 */
    private double movementSpeed = 0.28D;
    /** 索敌距离。给得比原版苦力怕（16）远，否则玩家在 20 格外它就看不见了，「会瞬移」也没意义。 */
    private double followRange = 40.0D;

    /** 瞬移总开关；关掉后它就是一只普通苦力怕（近战追击 + 引爆）。 */
    private boolean teleportEnabled = true;
    /** 两次瞬移之间的冷却（tick，默认 80 = 4 秒）。 */
    private int teleportCooldownTicks = 80;
    /**
     * 触发瞬移的最小距离（格）。
     *
     * <p>比这还近就不瞬移了——贴脸时再闪走只会让它永远炸不到人，变成纯逃跑怪。</p>
     */
    private double teleportMinDistance = 5.0D;
    /**
     * 触发瞬移的最大距离（格）。
     *
     * <p>超过这个距离就不追了（连瞬移都够不着），避免它隔着半张地图反复闪。</p>
     */
    private double teleportMaxDistance = 32.0D;
    /**
     * 落点相对目标的搜索半径（格）。
     *
     * <p>落点取在目标<b>背后</b>（沿目标视线反方向），再在这个半径内随机偏一点，
     * 所以默认 3.0 意味着「闪到你身后 1.5~3 格」。调大＝落得更远、更好躲。</p>
     */
    private double landingRadius = 3.0D;

    /**
     * 被击中时闪走的概率（0~1，默认 0.35）。
     *
     * <p>这是末影人最标志性的行为之一，但频率太高会变成「打不死」：每次命中都闪走，近战永远接不上。
     * 设成 0 就是完全不会躲的靶子。</p>
     */
    private double dodgeChance = 0.35D;
    /** 被击中闪走时的距离范围（格）。 */
    private double dodgeRadius = 8.0D;

    /**
     * 是否让它身上持续冒少量末影人传送粒子。
     *
     * <p>纯粹是<b>可读性</b>：它和原版苦力怕用同一张贴图，不闪的时候玩家看不出区别，
     * 会被当成普通苦力怕贴脸。默认开，但频率很低（每 10 tick 两颗），不至于变成光污染。</p>
     */
    private boolean auraParticles = true;

    /**
     * 是否像末影人一样怕水（浸在水里持续受伤）。
     *
     * <p>默认<b>关</b>。开着等于给了一条极便宜的解法——下雨天它就自己化了；关着则要靠走位与地形
     * 处理它。这是明确的设计取舍，不是遗漏：想要经典末影人克制关系就把这个打开。</p>
     */
    private boolean waterHurts = false;
    /** 怕水时每 20 tick 扣多少点生命（默认 1，与末影人一致）。 */
    private double waterDamagePerSecond = 1.0D;

    /** Gson 需要一个无参构造器。 */
    public EnderCreeperConfig() {
    }

    public static synchronized EnderCreeperConfig init() {
        current = load(getConfigPath());
        return current;
    }

    /** 热重载；损坏的配置不会覆盖上一份有效值。 */
    public static synchronized EnderCreeperConfig reload() {
        return reload(getConfigPath());
    }

    static synchronized EnderCreeperConfig reload(Path path) {
        if (Files.notExists(path)) {
            current = load(path);
            return current;
        }
        try {
            EnderCreeperConfig loaded = readValidated(path);
            current = loaded;
            return loaded;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.warn("末影人苦力怕配置重载失败，继续使用上一份有效配置：{}", path, exception);
            throw new IllegalStateException("末影人苦力怕配置无效，已保留上一份有效设置", exception);
        }
    }

    public static EnderCreeperConfig current() {
        return current;
    }

    public static Path getConfigPath() {
        return CommonInit.CONFIG_DIRECTORY.resolve(CONFIG_FILE_NAME);
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    static synchronized EnderCreeperConfig load(Path path) {
        EnderCreeperConfig defaults = defaults();
        if (Files.notExists(path)) {
            try {
                JsonDataStore.writeAtomic(path, GSON, defaults);
            } catch (IOException exception) {
                DreamingFishCore.LOGGER.warn("末影人苦力怕配置默认文件创建失败：{}", path, exception);
            }
            return defaults;
        }
        try {
            return readValidated(path);
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.warn("末影人苦力怕配置损坏，已仅使用内存默认值：{}", path, exception);
            return defaults;
        }
    }

    private static EnderCreeperConfig readValidated(Path path) throws IOException {
        if (Files.size(path) == 0L) {
            throw new IOException("配置文件为空");
        }
        EnderCreeperConfig loaded = JsonDataStore.read(
                path, GSON, EnderCreeperConfig.class, EnderCreeperConfig::new);
        loaded.validateAndNormalize();
        return loaded;
    }

    /** 返回当前配置的不可变快照；实体只持有快照，热重载后由实体自己换新。 */
    public Resolved resolve() {
        return new Resolved(
                maxHealth,
                movementSpeed,
                followRange,
                teleportEnabled,
                teleportCooldownTicks,
                teleportMinDistance,
                teleportMaxDistance,
                landingRadius,
                dodgeChance,
                dodgeRadius,
                auraParticles,
                waterHurts,
                waterDamagePerSecond);
    }

    public record Resolved(
            double maxHealth,
            double movementSpeed,
            double followRange,
            boolean teleportEnabled,
            int teleportCooldownTicks,
            double teleportMinDistance,
            double teleportMaxDistance,
            double landingRadius,
            double dodgeChance,
            double dodgeRadius,
            boolean auraParticles,
            boolean waterHurts,
            double waterDamagePerSecond) {
    }

    private static EnderCreeperConfig defaults() {
        EnderCreeperConfig config = new EnderCreeperConfig();
        config.validateAndNormalize();
        return config;
    }

    /** 校验并归一化。越界直接抛异常，避免无效值进入运行时。 */
    private void validateAndNormalize() {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("不支持的末影人苦力怕配置版本：" + schemaVersion);
        }
        maxHealth = requireRange(maxHealth, 1.0D, 200.0D, "maxHealth");
        movementSpeed = requireRange(movementSpeed, 0.05D, 1.0D, "movementSpeed");
        followRange = requireRange(followRange, 1.0D, 128.0D, "followRange");
        teleportCooldownTicks = requireRange(teleportCooldownTicks, 0, 20 * 60, "teleportCooldownTicks");
        teleportMinDistance = requireRange(teleportMinDistance, 0.0D, 64.0D, "teleportMinDistance");
        teleportMaxDistance = requireRange(teleportMaxDistance, 1.0D, 128.0D, "teleportMaxDistance");
        landingRadius = requireRange(landingRadius, 1.0D, 16.0D, "landingRadius");
        dodgeChance = requireRange(dodgeChance, 0.0D, 1.0D, "dodgeChance");
        dodgeRadius = requireRange(dodgeRadius, 1.0D, 32.0D, "dodgeRadius");
        waterDamagePerSecond = requireRange(waterDamagePerSecond, 0.0D, 20.0D, "waterDamagePerSecond");
        // 最小距离不小于最大距离时，触发窗口是空的——瞬移永远不生效，而且表面上一切正常。
        if (teleportMinDistance >= teleportMaxDistance) {
            throw new IllegalStateException(
                    "teleportMinDistance 必须小于 teleportMaxDistance，否则瞬移窗口为空："
                            + teleportMinDistance + " >= " + teleportMaxDistance);
        }
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
