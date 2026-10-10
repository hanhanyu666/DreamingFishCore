package com.hhy.dreamingfishcore.gameplay.zombie_system.boss.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.zombie_system.boss.ZombieCommanderEntity;
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
 * 尸潮指挥官的模型：由 {@code tools/convert_commander_model.py} 从 {@code zombie_commander.bbmodel} 生成，
 * <b>不要手改本文件</b>——改模型请改 bbmodel 后重跑脚本。
 *
 * <p>骨骼比原版僵尸多了三条：{@code cap}（大檐帽，挂在头下）、{@code flag}（背旗，挂在躯干下）、
 * {@code saber}（军刀，挂在右手下）。动画共六组，全部来自资源包 JSON
 * （{@code neoforge/animations/entity/zombie_commander/}），调动作不需要重新编译。</p>
 *
 * <p>坐标约定：Bedrock 是「y 向上、脚底 0、面朝 −z」，原版 Java 是「y 向下、颈部 0、面朝 −z」，
 * 相差 {@code (x, 24 - y, z)} —— 面朝方向两边都是 −z，所以 <b>z 不取反</b>。</p>
 */
public class ZombieCommanderModel extends HierarchicalModel<ZombieCommanderEntity> {
    private static final String PATH = "zombie_commander/";

    /**
     * 六组动画的句柄。
     *
     * <p>必须持有 {@link AnimationHolder} 本身，不能在类加载时 {@code .get()} 出
     * {@code AnimationDefinition} 缓存成常量 —— 动画是**资源重载之后**才绑定的，
     * 类加载那一刻拿到的只会是空动画（表现就是模型僵住）。holder 每次重载都会重新绑定。</p>
     */
    private static final AnimationHolder WALK = anim("walk");
    private static final AnimationHolder MELEE_1 = anim("melee_1");
    private static final AnimationHolder MELEE_2 = anim("melee_2");
    private static final AnimationHolder MELEE_3 = anim("melee_3");
    private static final AnimationHolder SHOOT = anim("shoot");
    private static final AnimationHolder SUMMON = anim("summon");

    /** 走路动画的驱动系数，取值与原版 {@code HumanoidModel} 一致（limbSwing * 50 * 系数）。 */
    private static final float WALK_SPEED = 2.0F;
    private static final float WALK_SCALE = 2.5F;
    private static final float ACTION_SPEED = 1.0F;

    private final ModelPart root;
    private final ModelPart head;

    public ZombieCommanderModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
    }

    private static AnimationHolder anim(String name) {
        return getAnimation(ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, PATH + name));
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition root = meshdefinition.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
        body.addOrReplaceChild("body_box_8560a232", CubeListBuilder.create().texOffs(16, 16).addBox(-4F, 0F, -2F, 8F, 12F, 4F), PartPose.ZERO);
        PartDefinition flag = body.addOrReplaceChild("flag", CubeListBuilder.create(), PartPose.offset(4.5F, 4F, 3F));
        flag.addOrReplaceChild("flag_strap_e90e2cc9", CubeListBuilder.create().texOffs(80, 64).addBox(-1.5F, -1F, -0.3F, 2F, 2F, 1F), PartPose.ZERO);
        flag.addOrReplaceChild("flag_pole_5a879719", CubeListBuilder.create().texOffs(64, 72).addBox(0F, -16F, -0.2F, 1F, 24F, 1F), PartPose.ZERO);
        flag.addOrReplaceChild("flag_finial_ea033ff7", CubeListBuilder.create().texOffs(64, 64).addBox(-0.5F, -18F, -0.7F, 2F, 2F, 2F), PartPose.ZERO);
        flag.addOrReplaceChild("flag_cloth_01b083ac", CubeListBuilder.create().texOffs(72, 72).addBox(0.8F, -15F, 0F, 7F, 9F, 1F), PartPose.ZERO);
        flag.addOrReplaceChild("flag_tail_d4a90a11", CubeListBuilder.create().texOffs(88, 72).addBox(0.8F, -6F, 0F, 4F, 4F, 1F), PartPose.ZERO);
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("head_box_841538b8", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F), PartPose.ZERO);
        head.addOrReplaceChild("hat_ac93b314", CubeListBuilder.create().texOffs(32, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F, new CubeDeformation(0.5F)), PartPose.ZERO);
        PartDefinition cap = head.addOrReplaceChild("cap", CubeListBuilder.create(), PartPose.offset(0F, -8F, 0F));
        cap.addOrReplaceChild("cap_brim_6e82d811", CubeListBuilder.create().texOffs(64, 16).addBox(-4.5F, 1F, -7F, 9F, 1F, 2F), PartPose.ZERO);
        cap.addOrReplaceChild("cap_band_46e33112", CubeListBuilder.create().texOffs(64, 24).addBox(-5F, 0F, -5F, 10F, 2F, 10F), PartPose.ZERO);
        cap.addOrReplaceChild("cap_top_210ff95f", CubeListBuilder.create().texOffs(64, 40).addBox(-5.5F, -3F, -5.5F, 11F, 3F, 11F), PartPose.ZERO);
        cap.addOrReplaceChild("cap_badge_f3c1d85a", CubeListBuilder.create().texOffs(64, 58).addBox(-2F, 0F, -6F, 4F, 2F, 1F), PartPose.ZERO);
        PartDefinition right_arm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        right_arm.addOrReplaceChild("right_arm_box_6b424dd3", CubeListBuilder.create().texOffs(40, 16).addBox(-3F, -2F, -2F, 4F, 12F, 4F), PartPose.ZERO);
        PartDefinition saber = right_arm.addOrReplaceChild("saber", CubeListBuilder.create(), PartPose.offsetAndRotation(-1F, 8F, 0F, -1.8326F, 0F, 0F));
        saber.addOrReplaceChild("saber_pommel_76ce6cdc", CubeListBuilder.create().texOffs(114, 0).addBox(-0.5F, -2.8F, -0.5F, 1F, 1F, 1F), PartPose.ZERO);
        saber.addOrReplaceChild("saber_grip_2372192c", CubeListBuilder.create().texOffs(108, 0).addBox(-0.5F, -1.8F, -0.5F, 1F, 4F, 1F), PartPose.ZERO);
        saber.addOrReplaceChild("saber_bow_cb9dc67e", CubeListBuilder.create().texOffs(114, 8).addBox(-2.6F, -2.8F, -0.5F, 1F, 5F, 1F), PartPose.ZERO);
        saber.addOrReplaceChild("saber_guard_36c43c50", CubeListBuilder.create().texOffs(96, 0).addBox(-1.5F, 2.2F, -1.5F, 3F, 1F, 3F), PartPose.ZERO);
        saber.addOrReplaceChild("saber_blade_b3da7107", CubeListBuilder.create().texOffs(96, 8).addBox(-1F, 3.2F, -0.5F, 2F, 9F, 1F), PartPose.ZERO);
        saber.addOrReplaceChild("saber_tip_5ad732ea", CubeListBuilder.create().texOffs(108, 8).addBox(-0.5F, 12.2F, -0.5F, 1F, 1F, 1F), PartPose.ZERO);
        PartDefinition left_arm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        left_arm.addOrReplaceChild("left_arm_box_cf716d04", CubeListBuilder.create().texOffs(64, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F), PartPose.ZERO);
        PartDefinition right_leg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12F, 0F));
        right_leg.addOrReplaceChild("right_leg_box_71c38acd", CubeListBuilder.create().texOffs(0, 16).addBox(-2F, 0F, -2F, 4F, 12F, 4F), PartPose.ZERO);
        PartDefinition left_leg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12F, 0F));
        left_leg.addOrReplaceChild("left_leg_box_0b8d5dbd", CubeListBuilder.create().texOffs(80, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F), PartPose.ZERO);
        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(
            ZombieCommanderEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        // 走路由肢体摆动驱动循环动画；四组主动作都是「播一遍」，由实体同步的状态决定播哪个。
        this.animateWalk(WALK, limbSwing, limbSwingAmount, WALK_SPEED, WALK_SCALE);
        this.animate(entity.melee1AnimationState, MELEE_1, ageInTicks, ACTION_SPEED);
        this.animate(entity.melee2AnimationState, MELEE_2, ageInTicks, ACTION_SPEED);
        this.animate(entity.melee3AnimationState, MELEE_3, ageInTicks, ACTION_SPEED);
        this.animate(entity.shootAnimationState, SHOOT, ageInTicks, ACTION_SPEED);
        this.animate(entity.summonAnimationState, SUMMON, ageInTicks, ACTION_SPEED);

        // 头部跟随视线。动画里的头是「额外叠加」的，所以这里用 += 而不是覆盖，
        // 否则召唤时那个仰头/低头的动作会被视线直接抹掉。
        this.head.yRot += netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot += headPitch * Mth.DEG_TO_RAD;
    }
}
