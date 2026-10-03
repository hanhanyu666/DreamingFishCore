package com.hhy.dreamingfishcore.gameplay.zombie_system.archer;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记录每名玩家"上一 tick 是否有水平位移"，供「流血只在移动时结算」使用。
 *
 * <p>为什么需要单独存状态，而不能在效果里现算：{@code LivingEntity.baseTick()} 会在
 * <b>移动之前</b>把上一 tick 的坐标同步进 {@code xo/zo}（{@code walkDistO} 同理），然后立刻
 * 调用 {@code tickEffects()}；也就是说效果结算那一瞬间，"本 tick 的位移"永远是 0。所以必须在
 * 移动<b>之后</b>采样——这里挂在 {@code PlayerTickEvent.Post}（原版在 {@code Player#tick()}
 * 末尾触发，此时 aiStep 已经跑完），下一 tick 的效果结算就能读到上一 tick 的真实位移。</p>
 *
 * <p>同一套采样思路与 {@code SiegeZombieTrackingManager} 的移动声采样保持一致（都是"上一 tick
 * 位置 → 本 tick 位置"的水平位移），只是这里独立存一份：那段逻辑只在玩家通过登录校验、
 * 且丧尸听觉开启时才跑，不适合当作流血判定的依据。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class PlayerMotionTracker {
    /** 低于这个水平位移量视为"站着不动"（0.01 格的平方，正常行走约 0.2 格/tick）。 */
    private static final double MOVING_THRESHOLD_SQR = 1.0E-4D;
    /** 超过这个位移量视为传送/换维度这类非"走动"的位移，不计入移动。 */
    private static final double TELEPORT_THRESHOLD_SQR = 4.0D * 4.0D;

    private static final Map<UUID, Sample> SAMPLES = new ConcurrentHashMap<>();
    private static final Set<UUID> MOVING = ConcurrentHashMap.newKeySet();

    private PlayerMotionTracker() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        UUID playerId = player.getUUID();
        double x = player.getX();
        double z = player.getZ();
        Sample previous = SAMPLES.get(playerId);
        if (previous == null || previous.level() != player.level()) {
            // 第一次见到这名玩家（或刚换维度）：先只记基准点，不判定为移动。
            SAMPLES.put(playerId, new Sample(player.level(), x, z));
            MOVING.remove(playerId);
            return;
        }

        double dx = x - previous.x;
        double dz = z - previous.z;
        double displacementSqr = dx * dx + dz * dz;
        SAMPLES.put(playerId, new Sample(player.level(), x, z));
        if (displacementSqr > MOVING_THRESHOLD_SQR && displacementSqr <= TELEPORT_THRESHOLD_SQR) {
            MOVING.add(playerId);
        } else {
            MOVING.remove(playerId);
        }
    }

    /** 该玩家上一 tick 是否产生了"走动"级别的水平位移。未知玩家按"没在动"处理。 */
    public static boolean isMoving(@Nullable UUID playerId) {
        return playerId != null && MOVING.contains(playerId);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof Player player) {
            forget(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SAMPLES.clear();
        MOVING.clear();
    }

    /** 清掉某名玩家的状态；重复调用安全。 */
    public static void forget(UUID playerId) {
        SAMPLES.remove(playerId);
        MOVING.remove(playerId);
    }

    private record Sample(Level level, double x, double z) {
    }
}
