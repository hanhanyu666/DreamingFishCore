package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death;

/** 标准重建可以用最后一点余量完成一次；保留物品仍要求足额支付。 */
public final class TemplateReconstructionRules {
    private TemplateReconstructionRules() { }
    public static boolean canReconstruct(float points) { return Float.isFinite(points) && points > 0; }
    public static int remainingReconstructions(float points, float cost) {
        return canReconstruct(points) && Float.isFinite(cost) && cost > 0 ? (int) Math.ceil(points / cost) : 0;
    }
    public static float standardCharge(float points, float cost) {
        return canReconstruct(points) && Float.isFinite(cost) && cost > 0 ? Math.min(points, cost) : 0;
    }
}
