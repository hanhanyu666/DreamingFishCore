package com.hhy.dreamingfishcore.gameplay.zombie_system.archer.client;

import com.hhy.dreamingfishcore.gameplay.zombie_system.archer.ArcherZombieEntity;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * 射手僵尸的模型：完全复用原版僵尸模型，只额外加一个「蓄力抬手」姿势。
 *
 * <p>原版僵尸的攻击姿势是两条手臂水平前伸（{@code xRot = -PI/2}）。蓄力时把大臂继续往上抬，
 * 并按蓄力进度让两条手臂错开一点角度，读起来像在「举起骨刺、准备投掷」；满蓄力时加一点高频
 * 抖动，给玩家一个「马上要出手」的提示。姿势由同步过来的 {@code isCharging} + 客户端本地
 * 累加的进度驱动。</p>
 */
public final class ArcherZombieModel extends ZombieModel<ArcherZombieEntity> {
    /** 手臂水平前伸的基准角度，与原版僵尸武器姿势一致。 */
    private static final float ARM_FORWARD_ROTATION = -1.5707964F;
    /** 右臂抬手幅度（弧度）。 */
    private static final float RIGHT_ARM_RAISE = 0.85F;
    /** 左臂抬手幅度略小，制造错位感。 */
    private static final float LEFT_ARM_RAISE = 0.62F;
    /** 满蓄力抖动幅度。 */
    private static final float SHIVER = 0.06F;

    public ArcherZombieModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(
            ArcherZombieEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        float progress = entity.getChargeProgress();
        if (progress <= 0.0F) {
            return;
        }
        this.rightArm.xRot = ARM_FORWARD_ROTATION - RIGHT_ARM_RAISE * progress;
        this.leftArm.xRot = ARM_FORWARD_ROTATION - LEFT_ARM_RAISE * progress;
        // 蓄到最后四分之一才开始明显抖动，避免整个过程都在晃。
        float shiver = Mth.sin(ageInTicks * 1.7F) * SHIVER * Math.max(0.0F, progress - 0.75F) * 4.0F;
        this.rightArm.xRot += shiver;
        this.leftArm.xRot -= shiver;
    }
}
