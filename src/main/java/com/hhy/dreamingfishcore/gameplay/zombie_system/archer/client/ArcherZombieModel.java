package com.hhy.dreamingfishcore.gameplay.zombie_system.archer.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.zombie_system.archer.ArcherZombieEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.entity.animation.json.AnimationHolder;

/**
 * 射手僵尸的模型：由 {@code tools/convert_archer_zombie.py} 从作者的 Blockbench 工程生成，
 * <b>不要手改本文件</b>——改模型请改 bbmodel 后重跑脚本。
 *
 * <p>几何来自 {@code azombie.bbmodel}，两个动画来自资源包 JSON
 * （{@code neoforge/animations/entity/archer_zombie/}），所以调动画不需要重新编译。</p>
 *
 * <p>坐标约定：Bedrock 是「y 向上、脚底 0、面朝 +z」，原版 Java 是「y 向下、颈部 0、面朝 -z」，
 * 两者相差 {@code (x, 24-y, -z)} —— 这是绕 x 轴 180° 的纯旋转，所以骨骼偏移可直接换算、
 * 旋转角按「x 保持、y/z 取反」映射。</p>
 */
public class ArcherZombieModel extends HierarchicalModel<ArcherZombieEntity> {
    private static final ResourceLocation WALK_ID =
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "archer_zombie/walk");
    private static final ResourceLocation SHOOT_ID =
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "archer_zombie/shoot");

    /**
     * 走路与射击动画。
     *
     * <p>这里必须持有 {@link AnimationHolder} 本身，而不是在类加载时 {@code .get()} 出
     * {@code AnimationDefinition} 缓存成常量——动画是**资源重载之后**才绑定的，
     * 类加载那一刻拿到的只会是空动画，表现就是「模型僵住、永远不播动画」。
     * holder 每次重载都会被重新绑定，所以要在每帧取。</p>
     */
    private static final AnimationHolder WALK = getAnimation(WALK_ID);
    private static final AnimationHolder SHOOT = getAnimation(SHOOT_ID);

    /**
     * 走路动画的两个驱动系数，取值与原版 {@code HumanoidModel} 一致
     * （它就是 {@code animateWalk(WALK, limbSwing, limbSwingAmount, 2.0F, 2.5F)}）。
     *
     * <p>第一个系数把 {@code limbSwing} 映射成动画时间轴的推进速度（{@code limbSwing * 50 * 系数}）：
     * 配小了腿摆得比实际移动慢，看上去就是在"滑步"。第二个是摆幅系数。</p>
     */
    private static final float WALK_SPEED = 2.0F;
    private static final float WALK_SCALE = 2.5F;
    private static final float SHOOT_SPEED = 1.0F;

    private final ModelPart root;
    private final ModelPart head;

    public ArcherZombieModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition root = meshdefinition.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
        body.addOrReplaceChild("body_box_b0000000", CubeListBuilder.create().texOffs(16, 16).addBox(-4F, 0F, -2F, 8F, 12F, 4F), PartPose.ZERO);
        // 带自身旋转的立方体 -> 提升为子骨骼（pivot 为其中心）
        PartDefinition cube_7238f96d = body.addOrReplaceChild("cube_7238f96d", CubeListBuilder.create(), PartPose.offsetAndRotation(-1F, 1.5F, 2.5F, 0.7291F, 0.9407F, 0.9016F));
        cube_7238f96d.addOrReplaceChild("cube_7238f96d", CubeListBuilder.create().texOffs(39, 9).addBox(-2F, -0.5F, -0.5F, 4F, 1F, 1F), PartPose.ZERO);
        // 带自身旋转的立方体 -> 提升为子骨骼（pivot 为其中心）
        PartDefinition cube_76450ee2 = body.addOrReplaceChild("cube_76450ee2", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, 1.5F, 2.5F, -2.3584F, 1.1988F, -2.4579F));
        cube_76450ee2.addOrReplaceChild("cube_76450ee2", CubeListBuilder.create().texOffs(39, 9).addBox(-2F, -0.5F, -0.5F, 4F, 1F, 1F), PartPose.ZERO);
        // 带自身旋转的立方体 -> 提升为子骨骼（pivot 为其中心）
        PartDefinition cube_8624f70a = body.addOrReplaceChild("cube_8624f70a", CubeListBuilder.create(), PartPose.offsetAndRotation(2F, 1.5F, -3.5F, -0.5283F, 1.037F, -0.5297F));
        cube_8624f70a.addOrReplaceChild("cube_8624f70a", CubeListBuilder.create().texOffs(39, 9).addBox(-2F, -0.5F, -0.5F, 4F, 1F, 1F, new CubeDeformation(0.5F)), PartPose.ZERO);
        PartDefinition bone = body.addOrReplaceChild("bone", CubeListBuilder.create(), PartPose.offset(0F, 6.5F, 3.5F));
        // 带自身旋转的立方体 -> 提升为子骨骼（pivot 为其中心）
        PartDefinition cube_fd6e6d7d = bone.addOrReplaceChild("cube_fd6e6d7d", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 0F, -6F, -1.8541F, 1.3914F, -1.927F));
        cube_fd6e6d7d.addOrReplaceChild("cube_fd6e6d7d", CubeListBuilder.create().texOffs(39, 9).addBox(-2F, -0.5F, -0.5F, 4F, 1F, 1F, new CubeDeformation(0.5F)), PartPose.ZERO);
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("head_box_b0000000", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F), PartPose.ZERO);
        PartDefinition left_arm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        left_arm.addOrReplaceChild("left_arm_box_b0000000", CubeListBuilder.create().texOffs(40, 16).addBox(-1F, -2F, -2F, 4F, 12F, 4F), PartPose.ZERO);
        PartDefinition right_arm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        right_arm.addOrReplaceChild("right_arm_box_b0000000", CubeListBuilder.create().texOffs(40, 16).addBox(-3F, -2F, -2F, 4F, 14F, 4F), PartPose.ZERO);
        right_arm.addOrReplaceChild("cube_991ae968", CubeListBuilder.create().texOffs(5, 33).mirror().addBox(-5F, 5F, -3F, 6F, 2F, 6F), PartPose.ZERO);
        right_arm.addOrReplaceChild("cube_a152da99", CubeListBuilder.create().texOffs(33, 34).mirror().addBox(-5F, 8F, -3F, 6F, 2F, 6F), PartPose.ZERO);
        right_arm.addOrReplaceChild("cube_84bce12d", CubeListBuilder.create().texOffs(1, 45).mirror().addBox(-4F, -3F, -3F, 5F, 4F, 6F), PartPose.ZERO);
        right_arm.addOrReplaceChild("cube_273b23fb", CubeListBuilder.create().texOffs(19, 46).addBox(-5F, -4F, -4F, 2F, 2F, 4F), PartPose.ZERO);
        // 带自身旋转的立方体 -> 提升为子骨骼（pivot 为其中心）
        PartDefinition cube_0e844941 = right_arm.addOrReplaceChild("cube_0e844941", CubeListBuilder.create(), PartPose.offsetAndRotation(-4F, -0.5F, 2.5F, 0.4448F, 0.4222F, 0.4694F));
        cube_0e844941.addOrReplaceChild("cube_0e844941", CubeListBuilder.create().texOffs(39, 9).addBox(-2F, -0.5F, -0.5F, 4F, 1F, 1F), PartPose.ZERO);
        // 带自身旋转的立方体 -> 提升为子骨骼（pivot 为其中心）
        PartDefinition cube_b808b0dc = right_arm.addOrReplaceChild("cube_b808b0dc", CubeListBuilder.create(), PartPose.offsetAndRotation(-1F, -2.5F, 1.5F, -0.8678F, -0.6363F, -2.1557F));
        cube_b808b0dc.addOrReplaceChild("cube_b808b0dc", CubeListBuilder.create().texOffs(39, 49).addBox(-2F, -0.5F, -0.5F, 4F, 1F, 1F), PartPose.ZERO);
        PartDefinition left_leg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12F, 0F));
        left_leg.addOrReplaceChild("left_leg_box_b0000000", CubeListBuilder.create().texOffs(0, 16).addBox(-1.9F, 0F, -2F, 4F, 12F, 4F), PartPose.ZERO);
        PartDefinition right_leg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12F, 0F));
        right_leg.addOrReplaceChild("right_leg_box_b0000000", CubeListBuilder.create().texOffs(0, 16).addBox(-2.1F, 0F, -2F, 4F, 12F, 4F), PartPose.ZERO);
        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(
            ArcherZombieEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        // 走路：由肢体摆动驱动循环动画（原版 animateWalk 会把 limbSwing 映射到动画时间轴）。
        this.animateWalk(WALK, limbSwing, limbSwingAmount, WALK_SPEED, WALK_SCALE);
        // 射击：抬手蓄力的整个过程播一遍单次动画，播完停住。
        this.animate(entity.shootAnimationState, SHOOT, ageInTicks, SHOOT_SPEED);

        // 头部跟随视线。这两个角度是原版模型空间（y 向下）的，直接沿用原版约定。
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;
    }
}
