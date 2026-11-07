package com.hhy.dreamingfishcore.gameplay.creeper_system.phantom.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.creeper_system.phantom.PhantomCreeperEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * 幻翼苦力怕的专用翅膀模型。
 *
 * <p><b>为什么不用鞘翅模型</b>（早先的做法）：{@code ElytraModel} 的 UV 是按 64×32 的**鞘翅贴图**
 * 排布的，套上 64×64 的幻翼贴图只会出现错位的乱纹；而且它只有 {@code left_wing}/{@code right_wing}
 * 两个部件，与苦力怕模型的部件名对不上，{@code copyPropertiesTo} 拷了个寂寞，
 * 翅膀根本不会动。所以这里自己建几何：</p>
 *
 * <ul>
 *   <li>每侧两段：<b>主翼</b>（14×1×8）+ <b>翼尖</b>（10×1×6，带一点后掠角）；</li>
 *   <li>贴图用专门的 {@code phantom_creeper_wing.png}（64×64，深青膜面 + 翼骨纹 + 亮边）；</li>
 *   <li>{@link #setupAnim} 里按 {@code ageInTicks} 做上下扇动；**被点燃（俯冲撞上）时收拢**，
 *       看起来像"扑上去咬住目标"的姿势。</li>
 * </ul>
 */
public final class PhantomWingModel extends EntityModel<PhantomCreeperEntity> {

    /** 模型层注册信息（在 {@link PhantomCreeperClientSetup} 里注册）。 */
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "phantom_creeper_wings"), "main");

    /** 翼膜贴图（与模型 UV 一一对应）。 */
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "textures/entity/phantom_creeper_wing.png");

    /** 扇动幅度（弧度）。 */
    private static final float FLAP_AMPLITUDE = 0.6F;
    /** 扇动速度。 */
    private static final float FLAP_SPEED = 0.45F;
    /** 俯冲时收拢到的角度。 */
    private static final float TUCK_ANGLE = 0.15F;

    private final ModelPart leftWing;
    private final ModelPart rightWing;

    public PhantomWingModel(ModelPart root) {
        this.leftWing = root.getChild("left_wing");
        this.rightWing = root.getChild("right_wing");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition left = root.addOrReplaceChild("left_wing",
                CubeListBuilder.create().texOffs(0, 0).addBox(0.0F, 0.0F, -4.0F, 14.0F, 1.0F, 8.0F),
                PartPose.offset(1.0F, 0.0F, 0.0F));
        left.addOrReplaceChild("left_tip",
                CubeListBuilder.create().texOffs(0, 20).addBox(0.0F, 0.0F, -3.0F, 10.0F, 1.0F, 6.0F),
                PartPose.offsetAndRotation(14.0F, 0.0F, 0.0F, 0.0F, -0.25F, 0.0F));

        PartDefinition right = root.addOrReplaceChild("right_wing",
                CubeListBuilder.create().texOffs(0, 0).addBox(-14.0F, 0.0F, -4.0F, 14.0F, 1.0F, 8.0F),
                PartPose.offset(-1.0F, 0.0F, 0.0F));
        right.addOrReplaceChild("right_tip",
                CubeListBuilder.create().texOffs(0, 20).addBox(-10.0F, 0.0F, -3.0F, 10.0F, 1.0F, 6.0F),
                PartPose.offsetAndRotation(-14.0F, 0.0F, 0.0F, 0.0F, 0.25F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(PhantomCreeperEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        float amplitude = entity.isIgnited() ? TUCK_ANGLE : FLAP_AMPLITUDE;
        float flap = (float) Math.sin(ageInTicks * FLAP_SPEED) * amplitude;
        this.leftWing.zRot = -flap;
        this.rightWing.zRot = flap;
        // 轻微的前后摆动，让"滑翔"不那么僵
        this.leftWing.xRot = 0.05F;
        this.rightWing.xRot = 0.05F;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                               int packedOverlay, int color) {
        this.leftWing.render(poseStack, buffer, packedLight, packedOverlay);
        this.rightWing.render(poseStack, buffer, packedLight, packedOverlay);
    }
}
