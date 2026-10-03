package com.hhy.dreamingfishcore.gameplay.zombie_system.charred;

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
 * 焦尸的服务器端数值配置，写在 {@code config/dreamingfishcore/charred_zombie.json}。
 *
 * <p>沿用 {@code ArcherZombieConfig} / {@code ZombieSpeciesConfig} 的做法：JSON 而不是静态
 * {@code ModConfigSpec}，改完用 {@code /dreamingfish zombie reload} 热重载。所有数值都有范围校验，
 * 坏配置只会被拒绝并保留上一份有效值。</p>
 *
 * <p>注意：本版**不接入自然生成**（按需求「先只做刷怪蛋 + 命令」），所以配置里没有生成权重字段；
 * 要进刷怪池是后续独立的一步。</p>
 */
public final class CharredZombieConfig {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String CONFIG_FILE_NAME = "charred_zombie.json";

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

    private static volatile CharredZombieConfig current = defaults();

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    /** 总开关；关掉后焦尸不再做任何伤害闸门判定（等同于一只普通僵尸）。 */
    private boolean enabled = true;

    /**
     * 最大生命值。
     *
     * <p>比原版僵尸（20）略高，但远低于「只有火能打动它」给人的心理预期——因为火焰附加、
     * 打火石、岩浆这些反制手段的每秒伤害很低：着火 tick 是每秒 1 点（原版无敌帧限制），
     * 再乘易燃体质的 2 倍就是每秒 2 点。22 点血意味着一次打火石（8 秒）打不死它，需要两次，
     * 或者换岩浆/营火。要调强度先动这里。</p>
     */
    private double maxHealth = 22.0D;
    /** 移动速度（原版僵尸 0.23；0.20 略慢，换取「只受火伤」的硬度）。 */
    private double movementSpeed = 0.20D;
    /** 索敌距离（原版僵尸 35）。 */
    private double followRange = 35.0D;

    /**
     * 白天是否像普通僵尸一样自燃。
     *
     * <p>默认 {@code false}。日光燃烧走的是 {@code on_fire} 伤害类型，也就是**免费的火焰伤害**——
     * 若保留日光自燃，玩家只要等到白天就能免费看它烧死，「必须用火」这条核心机制形同虚设。
     * 关掉之后它全天都能活动，想杀它就得玩家自己带火。</p>
     */
    private boolean burnInDaylight = false;

    /**
     * 泡水 600 tick 后是否腐化成溺尸。
     *
     * <p>默认 {@code true}，刻意保留：免疫溺水伤害不等于不会变质，水把它浇灭、变成溺尸是符合
     * 直觉的收场，也给玩家留了一条「不想打就把它引下水」的路。关掉它（{@code false}）会让焦尸
     * 变成完全无法用环境处理的存在。</p>
     */
    private boolean convertInWater = true;

    /** 打断免疫时是否播放灰烬粒子 + 熄灭音效。关掉后「打不动」会没有任何反馈。 */
    private boolean immuneFeedbackEnabled = true;

    /**
     * 额外算作「火」的伤害类型（{@code minecraft:is_fire} 标签之外的部分）。
     *
     * <p>原版 {@code is_fire} 标签只含 {@code in_fire / on_fire / lava / hot_floor / campfire}，
     * 而玩家直觉里「火球」当然算火，所以默认把烈焰人与恶魂的火球补进来。想更严格就清空这个数组。</p>
     */
    private List<String> extraFireDamageTypes =
            List.of("minecraft:fireball", "minecraft:unattributed_fireball");

    /**
     * 永远有效的兜底伤害类型（不吃「只受火焰伤害」的免疫）。
     *
     * <p>默认放开虚空、溺水、摔落三类。理由是留一条退出通道：不放开的话，玩家把焦尸推进虚空
     * 或深水也毫无效果，一旦它卡在某个位置就只剩 OP 的 {@code /kill} 能清。想做成「除了火谁也
     * 弄不死」的硬核版本，把数组清空即可。</p>
     */
    private List<String> alwaysEffectiveDamageTypes =
            List.of("minecraft:out_of_world", "minecraft:drown", "minecraft:fall");

    /** 易燃体质：受到的火焰类伤害倍率（默认 2.0，即火伤翻倍）。 */
    private double fireDamageMultiplier = 2.0D;

    /** 是否开启「着火时变强」。 */
    private boolean burningBoostEnabled = true;
    /** 着火时的移速加成（{@code ADD_MULTIPLIED_TOTAL}，0.15 = +15%）。 */
    private double burningSpeedBonus = 0.15D;
    /** 着火时的攻击力加成（{@code ADD_VALUE}，2.0 = +2 点近战伤害）。 */
    private double burningAttackBonus = 2.0D;

    /** 是否在被火焰类伤害命中时附加「易损」。 */
    private boolean vulnerableEnabled = true;
    /** 「易损」持续时间（tick，默认 100 = 5 秒）。 */
    private int vulnerableDurationTicks = 100;
    /** 「易损」等级（0 = I 级，每级额外 +10% 受伤）。 */
    private int vulnerableAmplifier = 0;
    /** 「易损」每级的受伤加成（0.10 = +10%）。 */
    private double vulnerableDamageBonusPerLevel = 0.10D;
    /** 「易损」期间是否让焦尸发光（便于玩家看清「它现在打得动了」）。 */
    private boolean vulnerableGlowing = true;

    /** Gson 需要一个无参构造器。 */
    public CharredZombieConfig() {
    }

    public static synchronized CharredZombieConfig init() {
        current = load(getConfigPath());
        return current;
    }

    /** 热重载；损坏的配置不会覆盖上一份有效值。 */
    public static synchronized CharredZombieConfig reload() {
        return reload(getConfigPath());
    }

    static synchronized CharredZombieConfig reload(Path path) {
        if (Files.notExists(path)) {
            current = load(path);
            return current;
        }
        try {
            CharredZombieConfig loaded = readValidated(path);
            current = loaded;
            return loaded;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.warn("焦尸配置重载失败，继续使用上一份有效配置：{}", path, exception);
            throw new IllegalStateException("焦尸配置无效，已保留上一份有效设置", exception);
        }
    }

    public static CharredZombieConfig current() {
        return current;
    }

    public static Path getConfigPath() {
        return CommonInit.CONFIG_DIRECTORY.resolve(CONFIG_FILE_NAME);
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    static synchronized CharredZombieConfig load(Path path) {
        CharredZombieConfig defaults = defaults();
        if (Files.notExists(path)) {
            try {
                JsonDataStore.writeAtomic(path, GSON, defaults);
            } catch (IOException exception) {
                DreamingFishCore.LOGGER.warn("焦尸配置默认文件创建失败：{}", path, exception);
            }
            return defaults;
        }
        try {
            return readValidated(path);
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.warn("焦尸配置损坏，已仅使用内存默认值：{}", path, exception);
            return defaults;
        }
    }

    private static CharredZombieConfig readValidated(Path path) throws IOException {
        if (Files.size(path) == 0L) {
            throw new IOException("配置文件为空");
        }
        CharredZombieConfig loaded = JsonDataStore.read(
                path, GSON, CharredZombieConfig.class, CharredZombieConfig::new);
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
                burnInDaylight,
                convertInWater,
                immuneFeedbackEnabled,
                toDamageTypeKeys(extraFireDamageTypes, "extraFireDamageTypes"),
                toDamageTypeKeys(alwaysEffectiveDamageTypes, "alwaysEffectiveDamageTypes"),
                fireDamageMultiplier,
                burningBoostEnabled,
                burningSpeedBonus,
                burningAttackBonus,
                vulnerableEnabled,
                vulnerableDurationTicks,
                vulnerableAmplifier,
                vulnerableDamageBonusPerLevel,
                vulnerableGlowing);
    }

    /** 不可变快照。伤害类型已经在解析时转成 {@link ResourceKey}，命中判定只做等于比较。 */
    public record Resolved(
            boolean enabled,
            double maxHealth,
            double movementSpeed,
            double followRange,
            boolean burnInDaylight,
            boolean convertInWater,
            boolean immuneFeedbackEnabled,
            List<ResourceKey<DamageType>> extraFireDamageTypes,
            List<ResourceKey<DamageType>> alwaysEffectiveDamageTypes,
            double fireDamageMultiplier,
            boolean burningBoostEnabled,
            double burningSpeedBonus,
            double burningAttackBonus,
            boolean vulnerableEnabled,
            int vulnerableDurationTicks,
            int vulnerableAmplifier,
            double vulnerableDamageBonusPerLevel,
            boolean vulnerableGlowing) {
    }

    private static CharredZombieConfig defaults() {
        CharredZombieConfig config = new CharredZombieConfig();
        config.validateAndNormalize();
        return config;
    }

    /** 校验并归一化。越界直接抛异常，避免无效值进入运行时。 */
    private void validateAndNormalize() {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("不支持的焦尸配置版本：" + schemaVersion);
        }
        maxHealth = requireRange(maxHealth, 1.0D, 200.0D, "maxHealth");
        movementSpeed = requireRange(movementSpeed, 0.05D, 1.0D, "movementSpeed");
        followRange = requireRange(followRange, 1.0D, 128.0D, "followRange");
        fireDamageMultiplier = requireRange(fireDamageMultiplier, 0.0D, 20.0D, "fireDamageMultiplier");
        burningSpeedBonus = requireRange(burningSpeedBonus, 0.0D, 5.0D, "burningSpeedBonus");
        burningAttackBonus = requireRange(burningAttackBonus, 0.0D, 100.0D, "burningAttackBonus");
        vulnerableDurationTicks = requireRange(vulnerableDurationTicks, 0, 20 * 60, "vulnerableDurationTicks");
        vulnerableAmplifier = requireRange(vulnerableAmplifier, 0, 9, "vulnerableAmplifier");
        vulnerableDamageBonusPerLevel = requireRange(
                vulnerableDamageBonusPerLevel, 0.0D, 5.0D, "vulnerableDamageBonusPerLevel");
        // 空列表是合法值（表示不做任何额外放行 / 不额外纳入任何伤害类型），但 null 不是。
        extraFireDamageTypes = normalizeList(extraFireDamageTypes);
        alwaysEffectiveDamageTypes = normalizeList(alwaysEffectiveDamageTypes);
        // 提前把资源位置解析一遍：写错的 ID 在这里就拒绝，而不是等到某次命中才静默不匹配。
        toDamageTypeKeys(extraFireDamageTypes, "extraFireDamageTypes");
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
