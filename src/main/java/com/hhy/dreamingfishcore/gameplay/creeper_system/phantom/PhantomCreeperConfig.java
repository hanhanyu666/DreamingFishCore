package com.hhy.dreamingfishcore.gameplay.creeper_system.phantom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.init.CommonInit;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 幻翼苦力怕的服务器端数值配置，写在 {@code config/dreamingfishcore/phantom_creeper.json}。
 *
 * <p>沿用 {@code CharredZombieConfig} / {@code ArcherZombieConfig} 的做法：JSON 而不是静态
 * {@code ModConfigSpec}，改完用 {@code /dreamingfish zombie reload} 热重载。所有数值都有范围校验，
 * 坏配置会被拒绝并保留上一份有效值。</p>
 *
 * <p>本版**不接入自然生成**，只提供刷怪蛋与 {@code /summon}；要进刷怪池是后续独立的一步
 * （与焦尸/金刚当初的落地顺序一致）。</p>
 */
public final class PhantomCreeperConfig {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String CONFIG_FILE_NAME = "phantom_creeper.json";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    /** 与 {@link #maxHealth} 的基准对齐：原版苦力怕的基础生命上限（Monster 默认 20）。 */
    public static final double DEFAULT_MAX_HEALTH = 20.0D;
    /** 与 {@link #movementSpeed} 的基准对齐：原版苦力怕的基础移速。 */
    public static final double DEFAULT_MOVEMENT_SPEED = 0.25D;
    /** 与 {@link #followRange} 的基准对齐：原版苦力怕的索敌距离（原版苦力怕只有 16）。 */
    public static final double DEFAULT_FOLLOW_RANGE = 16.0D;

    private static volatile PhantomCreeperConfig current = defaults();

    private int schemaVersion = CURRENT_SCHEMA_VERSION;

    /**
     * 这里刻意**没有** {@code enabled} 总开关。
     *
     * <p>焦尸/金刚的开关关掉后，它们退化成「一只普通僵尸」，仍然可玩；而幻翼苦力怕的特殊机制就是飞行
     * 本身——为了飞行在 {@code registerGoals} 里摘掉了 {@code MeleeAttackGoal} 与随机游走，关掉飞行
     * 只会剩一只不会走路的苦力怕。与其给一个坏掉的开关，不如不给：不想让它出现就别刷它。</p>
     */

    /** 最大生命值。默认与原版苦力怕一致。 */
    private double maxHealth = 20.0D;
    /**
     * 地面移速。只在没飞起来时（泡水、被拴住）起作用。
     *
     * <p>飞行速度不走这个属性——原版 {@code MOVEMENT_SPEED} 是给寻路用的，飞行是直接写
     * {@code setDeltaMovement}，所以真正管飞行快慢的是 {@link #flySpeed}。</p>
     */
    private double movementSpeed = 0.25D;
    /** 索敌距离。它会飞，所以给得比原版苦力怕（16）远得多。 */
    private double followRange = 48.0D;

    /**
     * 飞行速度（格 / tick）。
     *
     * <p>0.28 ≈ 每秒 5.6 格，比玩家疾跑（约 0.28 格/tick，但玩家还有疾跑加成）略慢一点，
     * 追得上走路、追不上直线疾跑——玩家靠跑位仍然能拉开距离，只是要靠地形而不是体力。</p>
     */
    private double flySpeed = 0.28D;

    /**
     * 巡航高度：在目标头顶多少格盘旋。
     *
     * <p>3.0 格是「抬头能看见、但近战打不到」的高度。调高它会变成纯骚扰单位，调低会更容易被砍。</p>
     */
    private double hoverHeight = 3.0D;

    /**
     * 进入俯冲的水平距离（格）。
     *
     * <p>进这个范围就放弃巡航、直接朝目标俯冲。设大一点＝更早开始冲，压迫感更强但更好躲；
     * 设小一点＝一直盘旋到很近才冲，更难预判。</p>
     */
    private double diveRange = 6.0D;

    /** 俯冲速度（格 / tick）。比巡航快，是「它真的扑过来了」的主要体感来源。 */
    private double diveSpeed = 0.45D;

    /**
     * 没目标时每 tick 的下沉速度（格 / tick）。
     *
     * <p>没有目标的飞行怪如果完全不沉，会永远悬在半空、变成一个悬浮的装饰物。
     * 给一点下沉让它慢慢落地，落不了地也能自然消失。</p>
     */
    private double idleSinkSpeed = 0.03D;

    /** 是否在俯冲/无目标时播放幻翼的振翅音效（复用原版 {@code entity.phantom.flap}）。 */
    private boolean flapSoundEnabled = true;

    /** Gson 需要一个无参构造器。 */
    public PhantomCreeperConfig() {
    }

    public static synchronized PhantomCreeperConfig init() {
        current = load(getConfigPath());
        return current;
    }

    /** 热重载；损坏的配置不会覆盖上一份有效值。 */
    public static synchronized PhantomCreeperConfig reload() {
        return reload(getConfigPath());
    }

    static synchronized PhantomCreeperConfig reload(Path path) {
        if (Files.notExists(path)) {
            current = load(path);
            return current;
        }
        try {
            PhantomCreeperConfig loaded = readValidated(path);
            current = loaded;
            return loaded;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.warn("幻翼苦力怕配置重载失败，继续使用上一份有效配置：{}", path, exception);
            throw new IllegalStateException("幻翼苦力怕配置无效，已保留上一份有效设置", exception);
        }
    }

    public static PhantomCreeperConfig current() {
        return current;
    }

    public static Path getConfigPath() {
        return CommonInit.CONFIG_DIRECTORY.resolve(CONFIG_FILE_NAME);
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    static synchronized PhantomCreeperConfig load(Path path) {
        PhantomCreeperConfig defaults = defaults();
        if (Files.notExists(path)) {
            try {
                JsonDataStore.writeAtomic(path, GSON, defaults);
            } catch (IOException exception) {
                DreamingFishCore.LOGGER.warn("幻翼苦力怕配置默认文件创建失败：{}", path, exception);
            }
            return defaults;
        }
        try {
            return readValidated(path);
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.warn("幻翼苦力怕配置损坏，已仅使用内存默认值：{}", path, exception);
            return defaults;
        }
    }

    private static PhantomCreeperConfig readValidated(Path path) throws IOException {
        if (Files.size(path) == 0L) {
            throw new IOException("配置文件为空");
        }
        PhantomCreeperConfig loaded = JsonDataStore.read(
                path, GSON, PhantomCreeperConfig.class, PhantomCreeperConfig::new);
        loaded.validateAndNormalize();
        return loaded;
    }

    /** 返回当前配置的不可变快照；实体只持有快照，热重载后由实体自己换新。 */
    public Resolved resolve() {
        return new Resolved(
                maxHealth,
                movementSpeed,
                followRange,
                flySpeed,
                hoverHeight,
                diveRange,
                diveSpeed,
                idleSinkSpeed,
                flapSoundEnabled);
    }

    public record Resolved(
            double maxHealth,
            double movementSpeed,
            double followRange,
            double flySpeed,
            double hoverHeight,
            double diveRange,
            double diveSpeed,
            double idleSinkSpeed,
            boolean flapSoundEnabled) {
    }

    private static PhantomCreeperConfig defaults() {
        PhantomCreeperConfig config = new PhantomCreeperConfig();
        config.validateAndNormalize();
        return config;
    }

    /** 校验并归一化。越界直接抛异常，避免无效值进入运行时。 */
    private void validateAndNormalize() {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("不支持的幻翼苦力怕配置版本：" + schemaVersion);
        }
        maxHealth = requireRange(maxHealth, 1.0D, 200.0D, "maxHealth");
        movementSpeed = requireRange(movementSpeed, 0.05D, 1.0D, "movementSpeed");
        followRange = requireRange(followRange, 1.0D, 128.0D, "followRange");
        flySpeed = requireRange(flySpeed, 0.05D, 1.5D, "flySpeed");
        hoverHeight = requireRange(hoverHeight, 0.0D, 16.0D, "hoverHeight");
        diveRange = requireRange(diveRange, 1.0D, 32.0D, "diveRange");
        diveSpeed = requireRange(diveSpeed, 0.05D, 2.0D, "diveSpeed");
        idleSinkSpeed = requireRange(idleSinkSpeed, 0.0D, 0.5D, "idleSinkSpeed");
        // 俯冲速度低于巡航速度时，俯冲这一段会比盘旋还慢，等于没有「扑过来」的体感。
        if (diveSpeed < flySpeed) {
            throw new IllegalStateException(
                    "diveSpeed 不应小于 flySpeed（俯冲必须比巡航快）：" + diveSpeed + " < " + flySpeed);
        }
    }

    private static double requireRange(double value, double min, double max, String fieldName) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalStateException(fieldName + " 超出范围 [" + min + ", " + max + "]：" + value);
        }
        return value;
    }
}
