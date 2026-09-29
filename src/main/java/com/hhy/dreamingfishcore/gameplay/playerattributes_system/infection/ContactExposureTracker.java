package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 接触暴露的临时记录（CONTEXT.md「接触暴露」）。
 *
 * <p>暴露量刻意<b>不写入玩家存档</b>：它描述的是"你现在正待在传播范围里"这一现场事实，
 * 一旦离开范围就会逐渐衰减。若把它持久化，退出重进、换维度或停服都会残留一段无从解释的风险，
 * 反而制造"离开后仍无限累积"的假象。因此这里只保存会话内的状态，并在登出、死亡、
 * 换维度时清空。</p>
 *
 * <p>规则数值全部取自 {@link InfectionRules}，本类只负责状态与"警告是否升级"的判定。</p>
 */
public final class ContactExposureTracker {
    private ContactExposureTracker() {
    }

    private static final Map<UUID, Snapshot> EXPOSURE = new ConcurrentHashMap<>();

    private record Snapshot(float charge, int warningStep) {
    }

    /**
     * 一次暴露判定的结果。
     *
     * @param charge        判定后的暴露量
     * @param warningStep   判定后的警告档位（0 表示无需警告）
     * @param escalated     警告档位是否比上一次提高（用来避免每 20 秒重复同一句话）
     * @param converts      本次判定是否达到转化阈值
     */
    public record Step(float charge, int warningStep, boolean escalated, boolean converts) {
    }

    /** 推进一次暴露判定，并返回新状态。 */
    public static Step advance(UUID playerId, boolean exposed) {
        if (playerId == null) {
            return new Step(0.0F, 0, false, false);
        }
        Snapshot previous = EXPOSURE.get(playerId);
        float previousCharge = previous == null ? 0.0F : previous.charge();
        int previousStep = previous == null ? 0 : previous.warningStep();

        float charge = InfectionRules.advanceExposure(previousCharge, exposed);
        boolean converts = InfectionRules.convertsOnThreshold(charge);
        int warningStep = InfectionRules.exposureWarningStep(charge);
        if (converts) {
            // 转化后重新累积：连续站在传播范围里仍会再次触发，但每次都必须重新攒满。
            charge = 0.0F;
            warningStep = 0;
        }

        if (charge <= 0.0F) {
            EXPOSURE.remove(playerId);
        } else {
            EXPOSURE.put(playerId, new Snapshot(charge, warningStep));
        }
        return new Step(charge, warningStep, warningStep > previousStep, converts);
    }

    public static float chargeOf(UUID playerId) {
        Snapshot snapshot = playerId == null ? null : EXPOSURE.get(playerId);
        return snapshot == null ? 0.0F : snapshot.charge();
    }

    /** 清除一名玩家的暴露记录（登出、死亡、换维度）。 */
    public static void clear(UUID playerId) {
        if (playerId != null) {
            EXPOSURE.remove(playerId);
        }
    }

    /** 停服时清空，避免跨世界/跨服务器残留。 */
    public static void clearAll() {
        EXPOSURE.clear();
    }
}
