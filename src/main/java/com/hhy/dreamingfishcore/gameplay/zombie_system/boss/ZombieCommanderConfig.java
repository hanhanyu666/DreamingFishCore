package com.hhy.dreamingfishcore.gameplay.zombie_system.boss;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.init.CommonInit;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 远程僵尸 Boss 的服务器端配置，文件为 {@code config/dreamingfishcore/zombie_commander.json}。
 * 缺失文件通过 JsonDataStore 原子写入默认值；损坏文件不覆盖，坏重载保留上一份有效配置。
 * 此配置不包含自然刷怪或地图破坏选项，也不负责实体、事件或命令接线。
 */
public final class ZombieCommanderConfig {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String CONFIG_FILE_NAME = "zombie_commander.json";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static volatile ZombieCommanderConfig current = defaults();

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    /** Boss 行为总开关。 */
    private boolean enabled = true;
    /** 最大生命值；原版僵尸为 20，240 用于多阶段 Boss 战。 */
    private double maxHealth = 240.0D;
    /** 基础移速；原版僵尸为 0.23，0.26 用于走位与拉开距离。 */
    private double movementSpeed = 0.26D;
    /** 索敌距离（方块）；原版僵尸为 35，40 覆盖完整弹道射程。 */
    private double followRange = 40.0D;
    /** 近战基础伤害；原版僵尸攻击属性为 3，6 用作被贴身后的反击。 */
    private double attackDamage = 6.0D;
    /** 护甲点数；原版僵尸为 2，4 提供适度减伤。 */
    private double armor = 4.0D;
    /** 击退抗性；原版僵尸基础值为 0，0.5 降低 Boss 被连续推退的程度。 */
    private double knockbackResistance = 0.5D;

    /** 偏好的最小交战距离（方块）；不是原版僵尸属性，用于远程走位。 */
    private double preferredMinDistance = 8.0D;
    /** 远程攻击启动距离（方块）；不是原版近战僵尸属性。 */
    private double rangedAttackRange = 24.0D;
    /** 射击蓄力 tick；原版每秒 20 tick，24 为 1.2 秒预警。 */
    private int chargeTicks = 24;
    /** 普通阶段攻击间隔 tick；50 为 2.5 秒，限制射击频率。 */
    private int attackIntervalTicks = 50;
    /** 狂暴阶段攻击间隔 tick；30 为 1.5 秒，提高阶段压力。 */
    private int enragedAttackIntervalTicks = 30;
    /** 普通阶段每轮弹数；原版近战僵尸无连发，用于控制弹幕密度。 */
    private int burstCount = 3;
    /** 狂暴阶段每轮弹数；独立于原版僵尸攻击，用于增加连发压力。 */
    private int enragedBurstCount = 5;
    /** 连发弹间隔 tick；6 为 0.3 秒，给玩家躲避空隙。 */
    private int burstSpacingTicks = 6;

    /** 弹丸基础伤害；原版僵尸攻击属性为 3，此值为远程命中的基础值，难度倍率另算。 */
    private double projectileDamage = 6.0D;
    /** 弹丸速度（方块/tick）；自定义弹道参数，1.25 用于中距离射击。 */
    private double projectileSpeed = 1.25D;
    /** 弹丸重力（每 tick 的竖直速度减量）；自定义弹道参数，0.015 形成轻微下坠。 */
    private double projectileGravity = 0.015D;
    /** 弹丸最大射程（方块）；自定义终止距离，36 必须覆盖开火距离且不超过索敌距离。 */
    private double projectileMaxRange = 36.0D;

    /** 召唤冷却 tick；原版僵尸无此召唤周期，400 为 20 秒。 */
    private int summonCooldownTicks = 400;
    /** 召唤蓄力 tick；40 为 2 秒，提供打断或撤离的预警窗口。 */
    private int summonChargeTicks = 40;
    /** 普通阶段每次请求召唤数；实际数量还受存活随从上限限制。 */
    private int summonCount = 2;
    /** 狂暴阶段每次请求召唤数；用于增加阶段压力，不绕过随从上限。 */
    private int enragedSummonCount = 3;
    /** 同时存活的随从上限；原版僵尸无此 Boss 配额，用于限制场上数量。 */
    private int maxMinions = 6;
    /** 随从寿命 tick；1200 为 60 秒，独立于原版自然消失规则。 */
    private int minionLifetimeTicks = 1200;
    /** 狂暴生命比例阈值；原版僵尸无阶段机制，生命比例小于或等于 0.5 时进入狂暴。 */
    private double enrageHealthFraction = 0.5D;
    /** 击杀经验点数；原版成年僵尸基础经验为 5，80 作为 Boss 击杀奖励。 */
    private int experienceReward = 80;
    /**
     * 蓄力与发射粒子的强度倍率；不是原版僵尸属性。
     * 1.0 为基准密度，调到 0 可完全关闭（集显或低配机可以直接关掉，不影响任何战斗结算）。
     */
    private double particleIntensity = 1.0D;

    /** 近战起手距离（方块）；原版僵尸的 {@code ATTACK_REACH} 约 2.0，2.6 让体型大一号的 Boss 够得着。 */
    private double meleeRange = 2.6D;
    /** 连招中允许的最大距离；目标被第一刀推开后还能接上第二、三刀。 */
    private double meleeChainRange = 3.6D;
    /** 一轮连招打完（或被拉开）后的冷却 tick；40 为 2 秒，避免贴脸时无限连。 */
    private int meleeComboCooldownTicks = 40;

    /** Gson 使用无参构造器，以便部分 JSON 继承未填写字段的默认值。 */
    public ZombieCommanderConfig() {
    }

    public static synchronized ZombieCommanderConfig init() {
        current = load(getConfigPath());
        return current;
    }

    public static synchronized ZombieCommanderConfig reload() {
        return reload(getConfigPath());
    }

    static synchronized ZombieCommanderConfig reload(Path path) {
        if (Files.notExists(path)) {
            current = load(path);
            return current;
        }
        try {
            ZombieCommanderConfig loaded = readValidated(path);
            current = loaded;
            return loaded;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.warn("僵尸指挥官配置重载失败，继续使用上一份有效配置：{}", path, exception);
            throw new IllegalStateException("僵尸指挥官配置无效，已保留上一份有效设置", exception);
        }
    }

    public static ZombieCommanderConfig current() {
        return current;
    }

    public static Path getConfigPath() {
        return CommonInit.CONFIG_DIRECTORY.resolve(CONFIG_FILE_NAME);
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    static synchronized ZombieCommanderConfig load(Path path) {
        ZombieCommanderConfig defaults = defaults();
        if (Files.notExists(path)) {
            try {
                JsonDataStore.writeAtomic(path, GSON, defaults);
            } catch (IOException exception) {
                DreamingFishCore.LOGGER.warn("僵尸指挥官配置默认文件创建失败：{}", path, exception);
            }
            return defaults;
        }
        try {
            return readValidated(path);
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.warn("僵尸指挥官配置损坏，已仅使用内存默认值：{}", path, exception);
            return defaults;
        }
    }

    private static ZombieCommanderConfig readValidated(Path path) throws IOException {
        if (Files.size(path) == 0L) {
            throw new IOException("配置文件为空");
        }
        // JsonDataStore 默认会将 JSON null / 空白内容替换为默认对象，这里明确拒绝，避免坏重载重置配置。
        JsonObject json = JsonDataStore.read(path, GSON, JsonObject.class, () -> {
            throw new IllegalStateException("配置必须是 JSON 对象，不能为 null 或空白内容");
        });
        for (var entry : json.entrySet()) {
            if (entry.getValue().isJsonNull()) {
                throw new IllegalStateException(entry.getKey() + " 不能为 null；省略字段可使用默认值");
            }
        }
        // 通过文本读取整数，避免 JsonPrimitive.getAsInt() 将小数静默截断为合法计数或版本。
        ZombieCommanderConfig loaded = GSON.fromJson(json.toString(), ZombieCommanderConfig.class);
        loaded.validateAndNormalize();
        return loaded;
    }

    /** 返回仅含基础类型的不可变数值快照，不访问任何游戏注册表。 */
    public Resolved resolve() {
        return new Resolved(
                enabled, maxHealth, movementSpeed, followRange, attackDamage, armor, knockbackResistance,
                preferredMinDistance, rangedAttackRange, chargeTicks, attackIntervalTicks,
                enragedAttackIntervalTicks, burstCount, enragedBurstCount, burstSpacingTicks,
                projectileDamage, projectileSpeed, projectileGravity, projectileMaxRange,
                summonCooldownTicks, summonChargeTicks, summonCount, enragedSummonCount,
                maxMinions, minionLifetimeTicks, enrageHealthFraction, experienceReward,
                particleIntensity, meleeRange, meleeChainRange, meleeComboCooldownTicks);
    }

    public record Resolved(
            boolean enabled,
            double maxHealth,
            double movementSpeed,
            double followRange,
            double attackDamage,
            double armor,
            double knockbackResistance,
            double preferredMinDistance,
            double rangedAttackRange,
            int chargeTicks,
            int attackIntervalTicks,
            int enragedAttackIntervalTicks,
            int burstCount,
            int enragedBurstCount,
            int burstSpacingTicks,
            double projectileDamage,
            double projectileSpeed,
            double projectileGravity,
            double projectileMaxRange,
            int summonCooldownTicks,
            int summonChargeTicks,
            int summonCount,
            int enragedSummonCount,
            int maxMinions,
            int minionLifetimeTicks,
            double enrageHealthFraction,
            int experienceReward,
            double particleIntensity,
            double meleeRange,
            double meleeChainRange,
            int meleeComboCooldownTicks) {
    }

    private static ZombieCommanderConfig defaults() {
        ZombieCommanderConfig config = new ZombieCommanderConfig();
        config.validateAndNormalize();
        return config;
    }

    private void validateAndNormalize() {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("不支持的僵尸指挥官配置版本：" + schemaVersion);
        }
        maxHealth = requireRange(maxHealth, 1.0D, 1024.0D, "maxHealth");
        movementSpeed = requireRange(movementSpeed, 0.05D, 1.0D, "movementSpeed");
        followRange = requireRange(followRange, 1.0D, 128.0D, "followRange");
        attackDamage = requireRange(attackDamage, 0.0D, 100.0D, "attackDamage");
        armor = requireRange(armor, 0.0D, 30.0D, "armor");
        knockbackResistance = requireRange(knockbackResistance, 0.0D, 1.0D, "knockbackResistance");
        preferredMinDistance = requireRange(preferredMinDistance, 0.0D, 128.0D, "preferredMinDistance");
        rangedAttackRange = requireRange(rangedAttackRange, 1.0D, 128.0D, "rangedAttackRange");
        projectileMaxRange = requireRange(projectileMaxRange, 1.0D, 128.0D, "projectileMaxRange");
        if (preferredMinDistance >= rangedAttackRange
                || rangedAttackRange > projectileMaxRange || projectileMaxRange > followRange) {
            throw new IllegalStateException(
                    "距离必须满足 preferredMinDistance < rangedAttackRange <= projectileMaxRange <= followRange");
        }
        chargeTicks = requireRange(chargeTicks, 1, 12000, "chargeTicks");
        attackIntervalTicks = requireRange(attackIntervalTicks, 1, 12000, "attackIntervalTicks");
        enragedAttackIntervalTicks = requireRange(
                enragedAttackIntervalTicks, 1, 12000, "enragedAttackIntervalTicks");
        burstCount = requireRange(burstCount, 1, 12, "burstCount");
        enragedBurstCount = requireRange(enragedBurstCount, 1, 12, "enragedBurstCount");
        burstSpacingTicks = requireRange(burstSpacingTicks, 1, 12000, "burstSpacingTicks");
        projectileDamage = requireRange(projectileDamage, 0.0D, 100.0D, "projectileDamage");
        projectileSpeed = requireRange(projectileSpeed, 0.05D, 5.0D, "projectileSpeed");
        projectileGravity = requireRange(projectileGravity, 0.0D, 1.0D, "projectileGravity");
        summonCooldownTicks = requireRange(summonCooldownTicks, 1, 12000, "summonCooldownTicks");
        summonChargeTicks = requireRange(summonChargeTicks, 1, 12000, "summonChargeTicks");
        summonCount = requireRange(summonCount, 1, 12, "summonCount");
        enragedSummonCount = requireRange(enragedSummonCount, 1, 12, "enragedSummonCount");
        maxMinions = requireRange(maxMinions, 1, 24, "maxMinions");
        minionLifetimeTicks = requireRange(minionLifetimeTicks, 20, 12000, "minionLifetimeTicks");
        enrageHealthFraction = requireRange(enrageHealthFraction, 0.0D, 1.0D, "enrageHealthFraction");
        experienceReward = requireRange(experienceReward, 0, 10000, "experienceReward");
        particleIntensity = requireRange(particleIntensity, 0.0D, 3.0D, "particleIntensity");
        meleeRange = requireRange(meleeRange, 0.5D, 8.0D, "meleeRange");
        meleeChainRange = requireRange(meleeChainRange, 0.5D, 12.0D, "meleeChainRange");
        if (meleeChainRange < meleeRange) {
            throw new IllegalStateException(
                    "meleeChainRange(" + meleeChainRange + ") 不能小于 meleeRange(" + meleeRange
                            + ")：否则第一刀打完必然接不上第二刀");
        }
        meleeComboCooldownTicks = requireRange(meleeComboCooldownTicks, 1, 12000, "meleeComboCooldownTicks");
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
