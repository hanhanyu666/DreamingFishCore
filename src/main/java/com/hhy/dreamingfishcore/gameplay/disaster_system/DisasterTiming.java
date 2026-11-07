package com.hhy.dreamingfishcore.gameplay.disaster_system;

/**
 * 天灾的时间与规模计算（**不碰 Minecraft**，纯函数，方便单测）。
 *
 * <p>两件事：</p>
 * <ul>
 *   <li><b>前夜提醒的时机</b>：日落后（11000 tick 起）播报明天的安排，玩家有一整个晚上准备；</li>
 *   <li><b>大尸潮的规模</b>：随游戏日推进而变强（一个周期内逐步加压），并按在线人数**次线性**放大
 *       ——十个人一起玩不该是单人难度的十倍。</li>
 * </ul>
 */
public final class DisasterTiming {

    /** 一天多少 tick。 */
    public static final long TICKS_PER_DAY = 24_000L;
    /** 傍晚（日落）开始播报明日预告的 tick。11000 ≈ 日落前，玩家还有时间准备。 */
    public static final long EVENING_START = 11_000L;
    /** 傍晚窗口到半夜结束（过了午夜再提醒就成了"今天"了）。 */
    public static final long EVENING_END = 13_000L;

    /** 尸潮规模：每天增长的百分点（10 = 每天 +10%）。 */
    public static final int DAY_GROWTH_PERCENT = 10;
    /** 尸潮规模上限倍率：最多涨到基础值的 3 倍，避免长期存档里变成灭世。 */
    public static final double MAX_DAY_FACTOR = 3.0D;
    /** 每个玩家数的规模倍率（下标 = 玩家数，超出取最后一档）。 */
    private static final double[] PLAYER_FACTORS = {1.0D, 1.0D, 1.2D, 1.35D, 1.5D};

    /**
     * 寒霜的庇护阈值：玩家所在位置的**方块光照 ≥ 7** 就算安全。
     *
     * <p>这个口径被服主调过两次：最早是"周围有比火把更亮（> 14）的光源"，
     * 因为采样漏检改成读光照并放宽到 > 10，最后定为 **≥ 7**（"7 以上"）。
     * 也就是说火把（照到的位置方块光是 14）非常够用，连较暗的光源也行。</p>
     */
    public static final int SHELTER_BLOCK_LIGHT = 7;

    /** 给定位置的光照等级，是否足以庇护玩家免受寒霜。 */
    public static boolean isSheltered(int blockLightLevel) {
        return blockLightLevel >= SHELTER_BLOCK_LIGHT;
    }

    /**
     * 风暴天的**风向**：由日程种子与游戏日决定，所以同一天重启后风向不变
     * （玩家看到的"今天刮西北风"不会因为重启就变）。
     *
     * @return 单位向量 {dx, dz}
     */
    public static double[] windDirection(long seed, long day) {
        long mixed = seed * 0x9E3779B97F4A7C15L ^ (day * 0xBF58476D1CE4E5B9L);
        // 取 3600 分之一度，保证方向足够随机又完全可复现
        double angle = (double) Math.floorMod(mixed, 3_600L) / 3_600.0D * Math.PI * 2.0D;
        return new double[]{Math.cos(angle), Math.sin(angle)};
    }

    /**
     * 每一次施加的基础推力（**每 2 tick 施力一次**，不是每秒一次）。
     *
     * <p>为什么要持续施力：地面上玩家的摩擦会在一两次 tick 内吃掉一次性的速度脉冲，
     * 于是"每秒推一下"在玩家身上几乎看不出来（掉落物因为没有地面摩擦，反而会动）。
     * 每 2 tick 给 0.03，稳态速度约 1.3 格/秒，能明显感到被风推着走。</p>
     */
    public static final double WIND_PUSH = 0.03D;

    /**
     * 阵风曲线：在 0.6~1.0 倍之间缓慢起伏，让风有"一阵一阵"的手感而不是恒定推力。
     *
     * @param tick 可以是任何递增的 tick（用服务器 tick 即可）
     */
    public static double windGustFactor(long tick) {
        double wave = Math.sin(tick / 40.0D);
        return 0.6D + 0.4D * (wave * 0.5D + 0.5D);
    }

    /**
     * 是否算"体型较大"的生物，从而免疫风力。
     *
     * <p>口径用碰撞箱：宽度 ≥ 1.5 或高度 ≥ 2.2。玩家（0.6×1.8）会受影响；
     * 马、劫掠兽、铁傀儡、巨人这类会被豁免——它们本来也不该被风吹着走。</p>
     */
    public static boolean isLargeEntity(double width, double height) {
        return width >= 1.5D || height >= 2.2D;
    }

    private DisasterTiming() {
    }

    /** 当前时间是否落在"傍晚提醒窗口"。 */
    public static boolean isEveningWindow(long dayTime) {
        long timeOfDay = Math.floorMod(dayTime, TICKS_PER_DAY);
        return timeOfDay >= EVENING_START && timeOfDay < EVENING_END;
    }

    /**
     * 天数系数：第 1 天 1.0，每天 +10%，最高 {@link #MAX_DAY_FACTOR} 倍。
     *
     * @param day 游戏日（从 0 开始；0 与 1 都按 1.0 起算，避免开局就是负加成）
     */
    public static double dayFactor(long day) {
        long effective = Math.max(1L, day);
        double factor = 1.0D + (effective - 1L) * DAY_GROWTH_PERCENT / 100.0D;
        return Math.min(MAX_DAY_FACTOR, Math.max(1.0D, factor));
    }

    /**
     * 人数系数：**次线性**（1 人 1.0、2 人 1.2、3 人 1.35、4 人及以上 1.5）。
     *
     * <p>不做线性放大：一来十个人一起玩时，每人身边都刷同样多会瞬间堆到几百只；
     * 二来玩家互相支援本身就降低了单人压力。</p>
     */
    public static double playerFactor(int players) {
        if (players <= 1) {
            return PLAYER_FACTORS[0];
        }
        int index = Math.min(players, PLAYER_FACTORS.length - 1);
        return PLAYER_FACTORS[index];
    }

    /**
     * 大尸潮里**每人**刷多少只。
     *
     * @param base 配置的基础值（默认 12）
     * @return 至少 1，最多 {@code base × 3}
     */
    public static int hordePerPlayer(long day, int players, int base) {
        if (base <= 0) {
            return 0;
        }
        double scaled = base * dayFactor(day) * playerFactor(players);
        int rounded = (int) Math.round(scaled);
        return Math.max(1, Math.min(base * 3, rounded));
    }

    /** 尸潮总预算上限：一次尸潮最多这么多只（防止人多 + 天晚时把服务器压垮）。 */
    public static int hordeTotalCap(int players) {
        return Math.max(12, Math.min(240, players * 40));
    }

    /** 这批实际刷多少：每人数量 × 人数，再受总预算限制（每批按比例）。 */
    public static int hordePerBatch(long day, int players, int base, int batches) {
        if (players <= 0 || batches <= 0) {
            return 0;
        }
        int total = Math.min(hordeTotalCap(players), hordePerPlayer(day, players, base) * players);
        return Math.max(0, total / batches);
    }
}
