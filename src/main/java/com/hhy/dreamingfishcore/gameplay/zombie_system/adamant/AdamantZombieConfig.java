package com.hhy.dreamingfishcore.gameplay.zombie_system.adamant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.init.CommonInit;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 金刚僵尸的服务器端数值配置，写在 {@code config/dreamingfishcore/adamant_zombie.json}。
 *
 * <p>沿用项目既有做法：JSON + {@code /dreamingfish zombie reload} 热重载，全部字段有范围校验，
 * 坏配置只会被拒绝并保留上一份有效值。</p>
 *
 * <p>本版同样**不接入自然生成**（只做刷怪蛋 + 命令）。</p>
 */
public final class AdamantZombieConfig {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String CONFIG_FILE_NAME = "adamant_zombie.json";

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
    /** 与 {@link #attackDamage} 的基准对齐：原版僵尸的基础攻击力。 */
    public static final double DEFAULT_ATTACK_DAMAGE = 3.0D;

    private static volatile AdamantZombieConfig current = defaults();

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    /** 总开关；关掉后它退化成一只普通僵尸（伤害闸门与硬直全部不生效）。 */
    private boolean enabled = true;

    /**
     * 最大生命值。
     *
     * <p>40 点：它是「没水就打不动」的硬墙，血量高一点才有拆墙的过程感。因为生锈后第 1 层就
     * 是正常伤害，真正决定耗时的其实是「涨锈」这一步，而不是血量。</p>
     */
    private double maxHealth = 40.0D;
    /** 移动速度（原版僵尸 0.23）。 */
    private double movementSpeed = 0.23D;
    /** 索敌距离（原版僵尸 35）。 */
    private double followRange = 35.0D;
    /** 近战攻击力（原版僵尸 3.0）。 */
    private double attackDamage = 3.0D;
    /**
     * 击退抗性（原版僵尸 0）。
     *
     * <p>默认 1.0 = 完全免疫击退：铁块被打不会后退，这条比数值本身更能传达「它是堵墙」。</p>
     */
    private double knockbackResistance = 1.0D;

    /**
     * 白天是否自燃。默认 {@code false}：它是金属，不该像普通僵尸那样被日光烧死；
     * 更要紧的是日光燃烧（{@code on_fire}）会成为绕过「必须先浇水」的后门。
     */
    private boolean burnInDaylight = false;

    /**
     * 泡水 600 tick 后是否腐化成溺尸。
     *
     * <p>默认 {@code false}：它的水反应应该是「生锈」而不是「变质成溺尸」，否则玩家把它推进
     * 水里会得到一只溺尸而不是一层锈。</p>
     */
    private boolean convertInWater = false;

    /** 锈级上限。默认 3 层。 */
    private int maxRustStages = 3;
    /** 水柱命中一次涨几层锈（默认 1）。 */
    private int rustPerWaterHit = 1;
    /**
     * 每多一层锈的受伤加成（默认 0.20，即第 2 层 +20%、第 3 层 +40%）。
     *
     * <p>第 1 层是 1.0——「刚生锈就打得动」是这套机制的核心，不该再打折扣。</p>
     */
    private double damageBonusPerStage = 0.20D;

    /** 泡水/淋雨是否也会慢慢生锈（默认开，和「碰水就锈」的设定一致）。 */
    private boolean rustInWaterOrRain = true;
    /** 潮湿状态下每多少 tick 涨一层锈（默认 40 = 2 秒）。 */
    private int rustIntervalTicks = 40;

    /** 是否启用攻击者硬直。 */
    private boolean staggerEnabled = true;
    /** 硬直时长（tick，默认 10 = 0.5 秒）。 */
    private int staggerDurationTicks = 10;

    /** 未生锈被打时是否播放「当啷」火花反馈。关掉后「打不动」会没有任何反馈。 */
    private boolean immuneFeedbackEnabled = true;

    /**
     * 永远有效的兜底伤害类型（不吃「未生锈免疫」）。
     *
     * <p>默认只放开虚空：留一条把卡住的它处理掉的通道，又不会给玩家一个随手可用的后门
     * （火/岩浆/溺水都不算——那些反而应该被免疫，否则「必须先浇水」就没意义了）。</p>
     */
    private List<String> alwaysEffectiveDamageTypes = List.of("minecraft:out_of_world");

    /** Gson 需要一个无参构造器。 */
    public AdamantZombieConfig() {
    }

    public static synchronized AdamantZombieConfig init() {
        current = load(getConfigPath());
        return current;
    }

    /** 热重载；损坏的配置不会覆盖上一份有效值。 */
    public static synchronized AdamantZombieConfig reload() {
        return reload(getConfigPath());
    }

    static synchronized AdamantZombieConfig reload(Path path) {
        if (Files.notExists(path)) {
            current = load(path);
            return current;
        }
        try {
            AdamantZombieConfig loaded = readValidated(path);
            current = loaded;
            return loaded;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.warn("金刚僵尸配置重载失败，继续使用上一份有效配置：{}", path, exception);
            throw new IllegalStateException("金刚僵尸配置无效，已保留上一份有效设置", exception);
        }
    }

    public static AdamantZombieConfig current() {
        return current;
    }

    public static Path getConfigPath() {
        return CommonInit.CONFIG_DIRECTORY.resolve(CONFIG_FILE_NAME);
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    static synchronized AdamantZombieConfig load(Path path) {
        AdamantZombieConfig defaults = defaults();
        if (Files.notExists(path)) {
            try {
                JsonDataStore.writeAtomic(path, GSON, defaults);
            } catch (IOException exception) {
                DreamingFishCore.LOGGER.warn("金刚僵尸配置默认文件创建失败：{}", path, exception);
            }
            return defaults;
        }
        try {
            return readValidated(path);
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.warn("金刚僵尸配置损坏，已仅使用内存默认值：{}", path, exception);
            return defaults;
        }
    }

    private static AdamantZombieConfig readValidated(Path path) throws IOException {
        if (Files.size(path) == 0L) {
            throw new IOException("配置文件为空");
        }
        AdamantZombieConfig loaded = JsonDataStore.read(
                path, GSON, AdamantZombieConfig.class, AdamantZombieConfig::new);
        loaded.validateAndNormalize();
        return loaded;
    }

    /** 返回当前配置的不可变快照；实体只持有快照，热重载后由实体自己换新。 */
    public Resolved resolve() {
        return new Resolved(
                enabled,
                maxHealth,
                movementSpeed,
                followRange,
                attackDamage,
                knockbackResistance,
                burnInDaylight,
                convertInWater,
                maxRustStages,
                rustPerWaterHit,
                damageBonusPerStage,
                rustInWaterOrRain,
                rustIntervalTicks,
                staggerEnabled,
                staggerDurationTicks,
                immuneFeedbackEnabled,
                toDamageTypeKeys(alwaysEffectiveDamageTypes, "alwaysEffectiveDamageTypes"));
    }

    /** 不可变快照。伤害类型已在解析时转成 {@link ResourceKey}，命中判定只做等于比较。 */
    public record Resolved(
            boolean enabled,
            double maxHealth,
            double movementSpeed,
            double followRange,
            double attackDamage,
            double knockbackResistance,
            boolean burnInDaylight,
            boolean convertInWater,
            int maxRustStages,
            int rustPerWaterHit,
            double damageBonusPerStage,
            boolean rustInWaterOrRain,
            int rustIntervalTicks,
            boolean staggerEnabled,
            int staggerDurationTicks,
            boolean immuneFeedbackEnabled,
            List<ResourceKey<DamageType>> alwaysEffectiveDamageTypes) {
    }

    private static AdamantZombieConfig defaults() {
        AdamantZombieConfig config = new AdamantZombieConfig();
        config.validateAndNormalize();
        return config;
    }

    /** 校验并归一化。越界直接抛异常，避免无效值进入运行时。 */
    private void validateAndNormalize() {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("不支持的金刚僵尸配置版本：" + schemaVersion);
        }
        maxHealth = requireRange(maxHealth, 1.0D, 500.0D, "maxHealth");
        movementSpeed = requireRange(movementSpeed, 0.05D, 1.0D, "movementSpeed");
        followRange = requireRange(followRange, 1.0D, 128.0D, "followRange");
        attackDamage = requireRange(attackDamage, 0.0D, 100.0D, "attackDamage");
        knockbackResistance = requireRange(knockbackResistance, 0.0D, 1.0D, "knockbackResistance");
        maxRustStages = requireRange(maxRustStages, 1, 9, "maxRustStages");
        rustPerWaterHit = requireRange(rustPerWaterHit, 1, 9, "rustPerWaterHit");
        damageBonusPerStage = requireRange(damageBonusPerStage, 0.0D, 5.0D, "damageBonusPerStage");
        rustIntervalTicks = requireRange(rustIntervalTicks, 1, 20 * 60, "rustIntervalTicks");
        staggerDurationTicks = requireRange(staggerDurationTicks, 0, 20 * 10, "staggerDurationTicks");
        alwaysEffectiveDamageTypes = normalizeList(alwaysEffectiveDamageTypes);
        // 提前解析一遍：写错的 ID 在这里就拒绝，而不是等到某次命中才静默不匹配。
        toDamageTypeKeys(alwaysEffectiveDamageTypes, "alwaysEffectiveDamageTypes");
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>(values.size());
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            normalized.add(value.trim());
        }
        return List.copyOf(normalized);
    }

    private static List<ResourceKey<DamageType>> toDamageTypeKeys(List<String> values, String fieldName) {
        List<ResourceKey<DamageType>> keys = new ArrayList<>(values.size());
        for (String value : values) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id == null) {
                throw new IllegalStateException(fieldName + " 里有非法的资源位置：" + value);
            }
            keys.add(ResourceKey.create(Registries.DAMAGE_TYPE, id));
        }
        return List.copyOf(keys);
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
