package com.hhy.dreamingfishcore.gameplay.raid_system;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * 对局随机源：确定性、可复现、子系统互不干扰。
 *
 * <p>设计稿 §4.1 要求"同一局结果稳定、服务器重启后可恢复、改怪物配置不会让撤离点一起变化、
 * 方便复现玩家举报"。要做到这一点，光有一个 long 种子不够，必须满足三条：</p>
 *
 * <ol>
 *   <li><b>算法确定</b>：只用整数运算（SplitMix64），跨 JVM、跨版本都得到同一串数字，
 *       不用 {@code java.util.Random}（它的实现被允许变化）、更不能用 {@code level.random} 或时间种子；</li>
 *   <li><b>分系统派生子种子</b>：{@link #forSystem(String)} 给每个子系统一条独立序列，
 *       所以调怪物数量不会改变战利品结果；</li>
 *   <li><b>顺序由调用方保证确定</b>：本类只保证"同样的调用顺序得到同样的结果"，
 *       调用方必须按稳定顺序遍历（例如锚点按 id 排序）。</li>
 * </ol>
 */
public final class RaidRandom {

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long MIX_A = 0xBF58476D1CE4E5B9L;
    private static final long MIX_B = 0x94D049BB133111EBL;

    private long state;

    public RaidRandom(long seed) {
        this.state = seed;
    }

    /** 当前内部状态，便于日志与复现（"这个点是用哪个种子抽出来的"）。 */
    public long state() {
        return state;
    }

    // ---------------------------------------------------------------- 种子派生

    /** SplitMix64 的收尾混合：把任意 long 打散成雪崩良好的 long。 */
    public static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * MIX_A;
        z = (z ^ (z >>> 27)) * MIX_B;
        return z ^ (z >>> 31);
    }

    /** 字符串标签的稳定哈希（FNV-1a 64，按 UTF-8 字节），不依赖 {@code String#hashCode}。 */
    public static long hashLabel(String label) {
        long hash = 0xCBF29CE484222325L;
        for (byte value : label.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (value & 0xFFL);
            hash *= 0x100000001B3L;
        }
        return hash;
    }

    /**
     * 本局主种子：{@code hash(服务器种子, 地图 id, 对局号)}。
     *
     * <p>把三样都混进来，是为了"同一张图同一局号"在不同服务器上得到不同结果（防被背板），
     * 而同一服务器上"同图同局号"永远一致（可复现）。</p>
     */
    public static long raidSeed(long serverSeed, String mapId, long raidId) {
        return mix(mix(serverSeed) ^ mix(hashLabel(mapId == null ? "" : mapId)) ^ mix(raidId));
    }

    /** 从本局种子派生一个子系统随机源，例如 {@code random.forSystem("loot")}。 */
    public RaidRandom forSystem(String system) {
        return new RaidRandom(mix(state ^ hashLabel(system == null ? "" : system)));
    }

    // ---------------------------------------------------------------- 取值

    private long next() {
        long z = (state += GOLDEN_GAMMA);
        z = (z ^ (z >>> 30)) * MIX_A;
        z = (z ^ (z >>> 27)) * MIX_B;
        return z ^ (z >>> 31);
    }

    public long nextLong() {
        return next();
    }

    /** [0, bound) 的均匀整数；bound ≤ 0 时返回 0（调用方不该这么用，但不抛异常）。 */
    public int nextInt(int bound) {
        if (bound <= 0) {
            return 0;
        }
        // 拒绝采样去掉取模偏差：小 bound 下偏差可忽略，但"可复现的抽奖"不该有隐性偏差
        long limit = Integer.toUnsignedLong(-1) - (Integer.toUnsignedLong(-1) % bound);
        while (true) {
            long value = next() >>> 32;
            if (value < limit) {
                return (int) (value % bound);
            }
        }
    }

    /** [min, max] 的均匀整数（含两端）；min > max 时返回 min。 */
    public int nextInt(int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + nextInt(max - min + 1);
    }

    /** [0.0, 1.0) 的均匀小数。 */
    public double nextDouble() {
        return (next() >>> 11) * 0x1.0p-53;
    }

    /** [min, max) 的均匀小数；max ≤ min 时返回 min。 */
    public double nextDouble(double min, double max) {
        if (max <= min) {
            return min;
        }
        return min + nextDouble() * (max - min);
    }

    public boolean nextBoolean() {
        return (next() & 1L) != 0L;
    }

    /**
     * 按权重抽一个元素。
     *
     * <p>权重非正的条目不会被抽中；总权重为 0（或列表为空）时返回空——调用方应据此走兜底逻辑，
     * 而不是在这里抛异常，避免一个空池把整局生成炸掉。</p>
     */
    public <T> Optional<T> pickWeighted(List<T> items, ToIntFunction<T> weight) {
        if (items == null || items.isEmpty()) {
            return Optional.empty();
        }
        long total = 0L;
        for (T item : items) {
            int value = weight.applyAsInt(item);
            if (value > 0) {
                total += value;
            }
        }
        if (total <= 0L) {
            return Optional.empty();
        }
        long roll = Math.floorMod(next(), total);
        for (T item : items) {
            int value = weight.applyAsInt(item);
            if (value <= 0) {
                continue;
            }
            roll -= value;
            if (roll < 0L) {
                return Optional.of(item);
            }
        }
        return Optional.of(items.get(items.size() - 1));
    }

    /** 供调试与报告用：连续若干个数，确认两条序列确实不同。 */
    public long[] peek(int count) {
        RaidRandom copy = new RaidRandom(state);
        long[] values = new long[Math.max(0, count)];
        for (int index = 0; index < values.length; index++) {
            values[index] = copy.next();
        }
        return values;
    }
}
