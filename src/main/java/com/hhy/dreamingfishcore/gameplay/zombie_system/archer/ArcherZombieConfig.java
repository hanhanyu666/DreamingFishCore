package com.hhy.dreamingfishcore.gameplay.zombie_system.archer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.init.CommonInit;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import net.minecraft.world.Difficulty;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 射手僵尸的服务器端数值配置，写在 {@code config/dreamingfishcore/archer_zombie.json}。
 *
 * <p>刻意沿用 {@link com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieSpeciesConfig} 的做法：
 * 用 JSON 而不是静态 {@code ModConfigSpec}，服主改完调用 {@code /dreamingfish zombie reload}
 * 即可热重载，不用重编译模组、也不用重启服务器。所有数值都有范围校验，写错了只会拒绝这一份
 * 配置并保留上一份有效值，不会把一份越界配置送进正在跑的 AI。</p>
 *
 * <p>字段含义与默认值见各自注释；{@link Resolved} 是不可变快照，实体与投射物只读取快照。</p>
 */
public final class ArcherZombieConfig {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String CONFIG_FILE_NAME = "archer_zombie.json";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    /** 与 {@link #maxHealth} 的基准对齐：原版僵尸的基础生命上限。 */
    public static final double DEFAULT_MAX_HEALTH = 20.0D;
    /** 与 {@link #movementSpeed} 的基准对齐：原版僵尸的基础移速。 */
    public static final double DEFAULT_MOVEMENT_SPEED = 0.23D;
    /** 与 {@link #followRange} 的基准对齐：原版僵尸的基础索敌距离。 */
    public static final double DEFAULT_FOLLOW_RANGE = 35.0D;

    private static volatile ArcherZombieConfig current = defaults();

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    /** 总开关；关掉后射手僵尸不再进入任何 AI（自然生成也会停止）。 */
    private boolean enabled = true;
    /** 是否允许主世界自然生成。 */
    private boolean naturalSpawn = true;
    /**
     * 射手僵尸占「自定义丧尸权重」的百分比。
     *
     * <p>它是在既有原版/围攻僵尸拆分之上再切一刀：把原本分给自定义丧尸的那部分权重按这个
     * 百分比拆成「围攻僵尸 + 射手僵尸」。取 0 时完全退回改动前的行为，取 100 时自定义丧尸
     * 全部变成射手僵尸。这样既不会稀释原版僵尸的权重，也不会改变僵尸家族的总权重。</p>
     */
    private int spawnPercentOfCustomZombie = 40;

    /** 最大生命值（原版僵尸 20；射手僵尸是远程压制单位，血量刻意压到普通僵尸之下）。 */
    private double maxHealth = 16.0D;
    /** 移动速度（原版僵尸 0.23；0.21 比普通僵尸略慢）。 */
    private double movementSpeed = 0.21D;
    /** 索敌距离（原版僵尸 35）。 */
    private double followRange = 35.0D;

    /**
     * 近战交接距离：目标进入这个距离以内就放弃远程、交回原版近战追击。
     * 用来避免「贴脸还在慢慢蓄力」的呆滞表现，同时保证它仍然是一只普通僵尸。
     */
    private double meleeHandOffDistance = 2.5D;
    /** 期望保持的最小距离：比它近就后撤，除非已经进入近战交接距离。 */
    private double preferredMinDistance = 6.0D;
    /** 射程：目标在这个距离内且视线通畅才开火；比它远则继续接近。 */
    private double rangedAttackRange = 16.0D;

    /**
     * 蓄力/抬手时长（tick），期间几乎不移动。
     *
     * <p>默认 16 tick（0.8 秒）是<b>与 shoot 动画的长度对齐</b>的：抬手动作完全由 Blockbench 的
     * 射击动画负责，蓄力一结束动画正好播完——既不会「动作播到一半就出手」，也不会「出手后还在抬手」。
     * 单独改这个值会让动作与节奏脱节，要改建议连动画长度一起改。</p>
     */
    private int chargeTicks = 16;
    /** 两次发射之间的固定间隔（tick），即攻击冷却。 */
    private int attackIntervalTicks = 40;

    /** 骨刺命中伤害。 */
    private double projectileDamage = 4.0D;
    /** 骨刺初速度（格/tick）。 */
    private double projectileSpeed = 1.6D;
    /** 瞄准散布（角度，越大越不准）。 */
    private double projectileInaccuracy = 4.0D;
    /** 每 tick 的重力加速度，决定飞行下坠幅度（0 = 直线）。 */
    private double projectileGravity = 0.045D;
    /** 骨刺离开发射点超过这个距离就消散。 */
    private double projectileMaxRange = 24.0D;

    /**
     * 骨刺伤害按游戏难度缩放的倍率。
     *
     * <p>骨刺用的是自定义伤害类型 {@code dreamingfishcore:bone_spike}，其 {@code scaling} 声明为
     * {@code never}，所以原版那套硬编码的难度缩放不会叠加——难度影响完全由这四个值决定。
     * 默认值在基础伤害 4 时正好复刻原版 mob_projectile 的手感（简单 3 / 普通 4 / 困难 6），
     * 但现在可以单独调，不必改代码。</p>
     */
    private double difficultyDamageMultiplierPeaceful = 0.0D;
    private double difficultyDamageMultiplierEasy = 0.75D;
    private double difficultyDamageMultiplierNormal = 1.0D;
    private double difficultyDamageMultiplierHard = 1.5D;

    /** 是否附加减速。 */
    private boolean slowEnabled = true;
    /** 减速持续时间（tick）。 */
    private int slowDurationTicks = 60;
    /** 减速等级（0 = 缓慢 I）。 */
    private int slowAmplifier = 0;

    /** 是否附加流血。 */
    private boolean bleedEnabled = true;
    /** 流血持续时间（tick）。 */
    private int bleedDurationTicks = 100;
    /** 流血等级（0 = 每秒 1 点，等级每 +1 多 1 点）。 */
    private int bleedAmplifier = 0;

    /** Gson 需要一个无参构造器。 */
    public ArcherZombieConfig() {
    }

    public static synchronized ArcherZombieConfig init() {
        current = load(getConfigPath());
        return current;
    }

    /** 热重载；损坏的配置不会覆盖上一份有效值。 */
    public static synchronized ArcherZombieConfig reload() {
        return reload(getConfigPath());
    }

    static synchronized ArcherZombieConfig reload(Path path) {
        if (Files.notExists(path)) {
            current = load(path);
            return current;
        }
        try {
            ArcherZombieConfig loaded = readValidated(path);
            current = loaded;
            return loaded;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.warn("射手僵尸配置重载失败，继续使用上一份有效配置：{}", path, exception);
            throw new IllegalStateException("射手僵尸配置无效，已保留上一份有效设置", exception);
        }
    }

    public static ArcherZombieConfig current() {
        return current;
    }

    public static Path getConfigPath() {
        return CommonInit.CONFIG_DIRECTORY.resolve(CONFIG_FILE_NAME);
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** 按当前生效配置返回骨刺伤害的难度倍率；投射物命中时用它换算最终伤害。 */
    public static double damageMultiplierFor(@Nullable Difficulty difficulty) {
        return current().damageMultiplier(difficulty);
    }

    /**
     * 某一份具体配置的难度倍率。
     *
     * <p>与静态入口分开，是为了让单元测试能直接验证"加载进来的那份文件"，
     * 而不是被 {@code current} 这份全局快照挡住。</p>
     */
    public double damageMultiplier(@Nullable Difficulty difficulty) {
        Difficulty resolved = difficulty == null ? Difficulty.NORMAL : difficulty;
        return switch (resolved) {
            case PEACEFUL -> difficultyDamageMultiplierPeaceful;
            case EASY -> difficultyDamageMultiplierEasy;
            case NORMAL -> difficultyDamageMultiplierNormal;
            case HARD -> difficultyDamageMultiplierHard;
        };
    }

    static synchronized ArcherZombieConfig load(Path path) {
        ArcherZombieConfig defaults = defaults();
        if (Files.notExists(path)) {
            try {
                JsonDataStore.writeAtomic(path, GSON, defaults);
            } catch (IOException exception) {
                DreamingFishCore.LOGGER.warn("射手僵尸配置默认文件创建失败：{}", path, exception);
            }
            return defaults;
        }
        try {
            return readValidated(path);
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.warn("射手僵尸配置损坏，已仅使用内存默认值：{}", path, exception);
            return defaults;
        }
    }

    private static ArcherZombieConfig readValidated(Path path) throws IOException {
        if (Files.size(path) == 0L) {
            throw new IOException("配置文件为空");
        }
        ArcherZombieConfig loaded = JsonDataStore.read(
                path, GSON, ArcherZombieConfig.class, ArcherZombieConfig::new);
        loaded.validateAndNormalize();
        return loaded;
    }

    /** 返回当前配置的不可变快照；实体与投射物只持有快照。 */
    public Resolved resolve() {
        return new Resolved(
                enabled,
                naturalSpawn,
                spawnPercentOfCustomZombie,
                maxHealth,
                movementSpeed,
                followRange,
                meleeHandOffDistance,
                preferredMinDistance,
                rangedAttackRange,
                chargeTicks,
                attackIntervalTicks,
                projectileDamage,
                projectileSpeed,
                projectileInaccuracy,
                projectileGravity,
                projectileMaxRange,
                slowEnabled,
                slowDurationTicks,
                slowAmplifier,
                bleedEnabled,
                bleedDurationTicks,
                bleedAmplifier);
    }

    /** 不可变快照。 */
    public record Resolved(
            boolean enabled,
            boolean naturalSpawn,
            int spawnPercentOfCustomZombie,
            double maxHealth,
            double movementSpeed,
            double followRange,
            double meleeHandOffDistance,
            double preferredMinDistance,
            double rangedAttackRange,
            int chargeTicks,
            int attackIntervalTicks,
            double projectileDamage,
            double projectileSpeed,
            double projectileInaccuracy,
            double projectileGravity,
            double projectileMaxRange,
            boolean slowEnabled,
            int slowDurationTicks,
            int slowAmplifier,
            boolean bleedEnabled,
            int bleedDurationTicks,
            int bleedAmplifier) {

        /** 超过这个距离就不再追（比射程大一些，留出接近的空间）。 */
        public double maxPursuitDistance() {
            return Math.min(128.0D, Math.max(rangedAttackRange * 1.5D, followRange));
        }
    }

    private static ArcherZombieConfig defaults() {
        ArcherZombieConfig config = new ArcherZombieConfig();
        config.validateAndNormalize();
        return config;
    }

    /** 校验并归一化。越界直接抛异常，避免无效值进入运行时。 */
    private void validateAndNormalize() {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("不支持的射手僵尸配置版本：" + schemaVersion);
        }
        spawnPercentOfCustomZombie = requireRange(
                spawnPercentOfCustomZombie, 0, 100, "spawnPercentOfCustomZombie");
        maxHealth = requireRange(maxHealth, 1.0D, 200.0D, "maxHealth");
        movementSpeed = requireRange(movementSpeed, 0.05D, 1.0D, "movementSpeed");
        followRange = requireRange(followRange, 1.0D, 128.0D, "followRange");
        meleeHandOffDistance = requireRange(meleeHandOffDistance, 1.0D, 8.0D, "meleeHandOffDistance");
        preferredMinDistance = requireRange(preferredMinDistance, 2.0D, 32.0D, "preferredMinDistance");
        rangedAttackRange = requireRange(rangedAttackRange, 4.0D, 64.0D, "rangedAttackRange");
        if (preferredMinDistance <= meleeHandOffDistance) {
            throw new IllegalStateException("preferredMinDistance 必须大于 meleeHandOffDistance");
        }
        if (rangedAttackRange <= preferredMinDistance) {
            throw new IllegalStateException("rangedAttackRange 必须大于 preferredMinDistance");
        }
        chargeTicks = requireRange(chargeTicks, 1, 200, "chargeTicks");
        attackIntervalTicks = requireRange(attackIntervalTicks, 1, 20 * 60, "attackIntervalTicks");
        projectileDamage = requireRange(projectileDamage, 0.0D, 100.0D, "projectileDamage");
        projectileSpeed = requireRange(projectileSpeed, 0.1D, 10.0D, "projectileSpeed");
        projectileInaccuracy = requireRange(projectileInaccuracy, 0.0D, 45.0D, "projectileInaccuracy");
        projectileGravity = requireRange(projectileGravity, 0.0D, 0.12D, "projectileGravity");
        projectileMaxRange = requireRange(projectileMaxRange, 4.0D, 128.0D, "projectileMaxRange");
        difficultyDamageMultiplierPeaceful = requireRange(
                difficultyDamageMultiplierPeaceful, 0.0D, 5.0D, "difficultyDamageMultiplierPeaceful");
        difficultyDamageMultiplierEasy = requireRange(
                difficultyDamageMultiplierEasy, 0.0D, 5.0D, "difficultyDamageMultiplierEasy");
        difficultyDamageMultiplierNormal = requireRange(
                difficultyDamageMultiplierNormal, 0.0D, 5.0D, "difficultyDamageMultiplierNormal");
        difficultyDamageMultiplierHard = requireRange(
                difficultyDamageMultiplierHard, 0.0D, 5.0D, "difficultyDamageMultiplierHard");
        slowDurationTicks = requireRange(slowDurationTicks, 0, 20 * 60, "slowDurationTicks");
        slowAmplifier = requireRange(slowAmplifier, 0, 9, "slowAmplifier");
        bleedDurationTicks = requireRange(bleedDurationTicks, 0, 20 * 60, "bleedDurationTicks");
        bleedAmplifier = requireRange(bleedAmplifier, 0, 9, "bleedAmplifier");
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
