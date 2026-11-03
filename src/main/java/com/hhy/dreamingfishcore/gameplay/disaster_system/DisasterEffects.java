package com.hhy.dreamingfishcore.gameplay.disaster_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule.DayPlan;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule.EventType;
import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 天灾的实际效果。第一版刻意选"看得见、不失控"的做法：
 *
 * <table border="1">
 *   <caption>三种天灾的效果</caption>
 *   <tr><th>天灾</th><th>效果</th></tr>
 *   <tr><td>大尸潮</td><td>跨天时在每名生存玩家周围分批刷出一波围攻僵尸（12 只/人，分 4 批，约 8 秒刷完）</td></tr>
 *   <tr><td>寒霜之夜</td><td>当天夜晚，暴露在天空下的玩家每 5 秒被上一次缓慢（不造成伤害），并飘雪花</td></tr>
 *   <tr><td>风暴天</td><td>跨天时把主世界天气设为雷雨，持续约一个游戏日</td></tr>
 * </table>
 *
 * <p>为什么第一版不给寒霜加真实伤害：天灾应该是"压力"而不是"处决"，而且模组的温度/伤害系统
 * 各有各的口径，贸然加伤害会和其它系统打架。等玩法验证过再决定要不要加。</p>
 *
 * <p>范围限定在主世界：其它维度（例如搜打撤的虚空竞技场）不该被天灾干扰，
 * 否则"进去打一局"会变成"进去被天灾制裁"。</p>
 */
public final class DisasterEffects {

    /** 大尸潮：每名玩家刷多少只。 */
    public static final int HORDE_PER_PLAYER = 12;
    /** 大尸潮：分几批刷（避免一 tick 生成十几只造成的卡顿尖峰）。 */
    public static final int HORDE_BATCHES = 4;
    /** 大尸潮：每批之间的间隔（tick）。 */
    public static final int HORDE_BATCH_INTERVAL = 40;
    /** 寒霜：检查/扣血间隔（tick）。5 秒 1 点，属于"缓慢掉血"而不是瞬杀。 */
    private static final int FROST_INTERVAL = 100;
    private static final int FROST_EFFECT_TICKS = 120;
    /** 寒霜：每次扣多少点真伤（1 点 = 半颗心）。 */
    public static final float FROST_DAMAGE = 1.0F;

    /** 大尸潮剩余待刷批次。 */
    private static int hordeBatchesLeft;
    private static int hordeBatchTimer;
    /** 寒霜的节流计数。 */
    private static int frostTimer;
    /** 当天是否已经播报过"寒霜之夜开始"的提示。 */
    private static boolean frostAnnounced;

    private DisasterEffects() {
    }

    /** 跨天时调用：一次性效果（雷雨、尸潮排期）。 */
    public static void onDayStarted(MinecraftServer server, DayPlan plan) {
        if (server == null || plan == null) {
            return;
        }
        frostTimer = 0;
        frostAnnounced = false;
        hordeBatchesLeft = 0;
        hordeBatchTimer = 0;

        if (plan.contains(EventType.STORM_DAY)) {
            // 约一个游戏日的雷雨；thunder 时间给一半，避免整段时间都在打雷
            server.overworld().setWeatherParameters(24_000, 12_000, true, true);
            DreamingFishCore.LOGGER.info("[disaster] 第 {} 天风暴天：主世界天气已切为雷雨", plan.day());
        }
        if (plan.contains(EventType.ZOMBIE_HORDE)) {
            hordeBatchesLeft = HORDE_BATCHES;
            hordeBatchTimer = 0;
            DreamingFishCore.LOGGER.info("[disaster] 第 {} 天大尸潮：{} 批围攻僵尸排期",
                    plan.day(), HORDE_BATCHES);
        }
    }

    /** 每 tick 调用（内部自己节流，绝大多数 tick 什么也不做）。 */
    public static void tick(MinecraftServer server, DayPlan plan) {
        if (server == null || plan == null) {
            return;
        }
        tickHorde(server);
        tickFrost(server, plan);
        tickWind(server, plan);
    }

    // ---------------------------------------------------------------- 大尸潮

    private static void tickHorde(MinecraftServer server) {
        if (hordeBatchesLeft <= 0) {
            return;
        }
        if (--hordeBatchTimer > 0) {
            return;
        }
        hordeBatchTimer = HORDE_BATCH_INTERVAL;
        hordeBatchesLeft--;

        ServerLevel level = server.overworld();
        // 规模随天数与在线人数缩放（人数是次线性放大），并受总预算上限约束
        long gameDay = DisasterService.today(server);
        int online = eligible(server).size();
        int perBatch = Math.max(1, DisasterTiming.hordePerBatch(gameDay, online, HORDE_PER_PLAYER, HORDE_BATCHES));
        int spawned = 0;
        for (ServerPlayer player : eligible(server)) {
            for (int index = 0; index < perBatch; index++) {
                if (spawnHordeZombie(level, player.getRandom())) {
                    spawned++;
                }
            }
            if (hordeBatchesLeft == 0) {
                player.sendSystemMessage(Component.literal("§4[天灾] 大尸潮已经抵达，别停下。"));
            }
        }
        if (spawned > 0) {
            DreamingFishCore.LOGGER.info("[disaster] 大尸潮刷出 {} 只（剩余批次 {}）",
                    spawned, hordeBatchesLeft);
        }
    }

    /** 在玩家周围 12~28 格的地表刷一只，优先围攻僵尸（更有尸潮味），失败回退普通僵尸。 */
    private static boolean spawnHordeZombie(ServerLevel level, RandomSource random) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return false;
        }
        ServerPlayer anchor = players.get(random.nextInt(players.size()));
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = 12.0D + random.nextDouble() * 16.0D;
        BlockPos base = BlockPos.containing(
                anchor.getX() + Math.cos(angle) * distance,
                anchor.getY() + 8.0D,
                anchor.getZ() + Math.sin(angle) * distance);
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base);

        Mob zombie = SiegeZombieEntities.SIEGE_ZOMBIE.get().create(level);
        if (zombie == null) {
            zombie = EntityType.ZOMBIE.create(level);
        }
        if (zombie == null) {
            return false;
        }
        zombie.moveTo(surface.getX() + 0.5D, surface.getY(), surface.getZ() + 0.5D,
                random.nextFloat() * 360.0F, 0.0F);
        zombie.setPersistenceRequired();     // 不因距离被清掉，让"尸潮"真的压过来
        return level.addFreshEntity(zombie);
    }

    // ---------------------------------------------------------------- 寒霜之夜

    private static void tickFrost(MinecraftServer server, DayPlan plan) {
        if (!plan.contains(EventType.FROST_NIGHT)) {
            return;
        }
        if (++frostTimer < FROST_INTERVAL) {
            return;
        }
        frostTimer = 0;

        boolean night = isNight(server);
        if (!night) {
            return;     // 寒霜之夜只在夜里发作
        }
        int affected = 0;
        int hurt = 0;
        for (ServerPlayer player : eligible(server)) {
            if (!(player.level() instanceof ServerLevel level)) {
                continue;
            }
            // 判定看光照：所在位置方块光 > 10 就是安全的（火把照到的地方也算）
            if (hasBrightLightNearby(level, player.blockPosition())) {
                continue;
            }
            affected++;
            if (isExposedToSky(player)) {
                level.sendParticles(ParticleTypes.SNOWFLAKE,
                        player.getX(), player.getY() + 1.6D, player.getZ(),
                        8, 0.5D, 0.4D, 0.5D, 0.01D);
            }
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    FROST_EFFECT_TICKS, 0, true, false, true));
            // 冻伤：原版 DamageTypes.FREEZE 走"无视护甲"的标签，所以这就是真伤；
            // 死亡信息也是"冻死"，和寒霜之夜对得上。
            if (player.hurt(level.damageSources().freeze(), FROST_DAMAGE)) {
                hurt++;
            }
        }
        if (hurt > 0 && !frostAnnounced) {
            frostAnnounced = true;
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§b[天灾] 寒霜之夜：没有光的地方会冻伤人——点起火把，或者找一间有光的屋子。"), false);
        }
    }

    /**
     * 玩家当前位置的光照是否足以庇护。
     *
     * <p><b>不再扫描周围方块。</b>早先的实现按 2 格步长采样光源方块，于是"放在玩家旁边第 1 格"
     * 的海晶灯（奇数偏移）永远采样不到——玩家站在灯边照样冻伤，这是服主实测报出来的 bug。</p>
     *
     * <p>现在直接读玩家所在位置的**方块光照**：光照本身就是周边光源的聚合结果，
     * 既不会漏检，也省掉了每 5 秒几百次方块读取。阈值见
     * {@link DisasterTiming#SHELTER_BLOCK_LIGHT}（方块光 &gt; 10 即安全，普通火把也够）。</p>
     */
    static boolean hasBrightLightNearby(ServerLevel level, BlockPos center) {
        return DisasterTiming.isSheltered(
                level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, center));
    }

    // ---------------------------------------------------------------- 风暴天

    /** 风力每多少 tick 推一次（1 秒一次，够有存在感又不至于把玩家推得到处跑）。 */
    private static final int WIND_INTERVAL = 20;
    /** 只推动玩家周围这个半径内的生物。 */
    private static final double WIND_RADIUS = 48.0D;

    /**
     * 风暴天的风：随机风向（由日程种子 + 当天日期推导，重启不变）、小力度、一阵一阵。
     *
     * <p>免疫规则（服主定的）：</p>
     * <ul>
     *   <li><b>体型较大</b>的生物（碰撞箱宽 ≥ 1.5 或高 ≥ 2.2）—— 马、劫掠兽、铁傀儡、巨人；</li>
     *   <li><b>蹲下的玩家</b>；</li>
     *   <li><b>创造 / 旁观</b>玩家；</li>
     *   <li>乘客（骑在别的生物上的）与已死亡实体。</li>
     * </ul>
     *
     * <p>范围只取玩家周围 48 格，而不是扫描整个世界：风暴是"身边的风"，
     * 而且 `getEntities` 带范围是一次空间查询，开销可控。</p>
     */
    private static void tickWind(MinecraftServer server, DayPlan plan) {
        if (!plan.contains(EventType.STORM_DAY)) {
            return;
        }
        if (Math.floorMod(server.getTickCount(), WIND_INTERVAL) != 0) {
            return;
        }
        DisasterSchedule.Schedule schedule = DisasterService.schedule();
        if (schedule == null) {
            return;
        }
        long day = DisasterService.today(server);
        double[] wind = DisasterTiming.windDirection(schedule.seed(), day);
        double push = DisasterTiming.WIND_PUSH
                * DisasterTiming.windGustFactor(server.getTickCount());
        ServerLevel level = server.overworld();
        for (ServerPlayer player : eligible(server)) {
            for (net.minecraft.world.entity.Entity entity
                    : level.getEntities(player, player.getBoundingBox().inflate(WIND_RADIUS))) {
                if (!isWindAffected(entity)) {
                    continue;
                }
                entity.push(wind[0] * push, 0.0D, wind[1] * push);
            }
        }
    }

    /** 这个实体是否会被风吹动。 */
    static boolean isWindAffected(net.minecraft.world.entity.Entity entity) {
        if (entity == null || !entity.isAlive() || entity.isPassenger()) {
            return false;
        }
        if (entity instanceof ServerPlayer player) {
            if (player.isCreative() || player.isSpectator()) {
                return false;   // 创造/旁观免疫
            }
            if (player.isCrouching()) {
                return false;   // 蹲下免疫
            }
        }
        return !DisasterTiming.isLargeEntity(entity.getBbWidth(), entity.getBbHeight());
    }

    // ---------------------------------------------------------------- 工具

    /** 参与天灾判定的玩家：生存/冒险、且在主世界。 */
    private static List<ServerPlayer> eligible(MinecraftServer server) {
        return server.getPlayerList().getPlayers().stream()
                .filter(player -> !player.isCreative() && !player.isSpectator())
                .filter(player -> player.level().dimension() == Level.OVERWORLD)
                .toList();
    }

    /** 是否是夜晚（12000~23000 tick 之间）。 */
    static boolean isNight(MinecraftServer server) {
        long timeOfDay = Math.floorMod(server.overworld().getDayTime(), DisasterService.TICKS_PER_DAY);
        return timeOfDay >= 12_000L && timeOfDay <= 23_000L;
    }

    /** 头顶是否直接能看到天（用世界高度图判断，避免为了这个做一次射线检测）。 */
    static boolean isExposedToSky(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos pos = player.blockPosition();
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
        return surface.getY() <= pos.getY() + 1;
    }
}
