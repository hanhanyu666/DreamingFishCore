package com.hhy.dreamingfishcore.gameplay.frontier_system;

import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationManager;
import net.minecraft.server.level.ServerPlayer;

/**
 * 外缘规则（纯逻辑 + 一个 Minecraft 侧的判定入口）。
 *
 * <p>当前"外缘"的定义很简单：**启用了尸潮开关的任务地点**（`TaskLocationManager.isHordeArea`）。
 * 这是现有的、玩家已经理解的概念；等外缘地图真正铺开，只需要改这一个方法
 * （比如再加"某个维度算外缘"）。</p>
 */
public final class FrontierRules {

    private FrontierRules() {
    }

    /**
     * 这次是否应该收惩罚（纯函数，可单测）。
     *
     * @param switchOn   配置开关是否打开
     * @param inFrontier 玩家/地点是否在外缘内
     */
    public static boolean shouldCharge(boolean switchOn, boolean inFrontier) {
        return !switchOn || inFrontier;
    }

    /**
     * 按外缘规则折算代价：开关关闭或身在外缘 → 原价；开关打开且在外缘之外 → 免费。
     *
     * @param cost 原价（负数按 0 处理）
     */
    public static float charge(float cost, boolean switchOn, boolean inFrontier) {
        if (cost <= 0.0F) {
            return 0.0F;
        }
        return shouldCharge(switchOn, inFrontier) ? cost : 0.0F;
    }

    /** 这个玩家现在是否处在外缘（当前实现 = 在启用了尸潮开关的任务地点内）。 */
    public static boolean isInFrontier(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return TaskLocationManager.isHordeArea(player.serverLevel(), player.blockPosition());
    }

    /** 重生代价的外缘折算：给死亡结算调用的一步入口。 */
    public static float gateRespawnCost(float cost, ServerPlayer player) {
        return charge(cost, FrontierConfig.penaltiesOnlyInFrontier(), isInFrontier(player));
    }
}
