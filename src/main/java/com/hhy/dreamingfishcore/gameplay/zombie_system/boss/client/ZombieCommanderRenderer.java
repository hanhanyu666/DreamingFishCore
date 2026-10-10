package com.hhy.dreamingfishcore.gameplay.zombie_system.boss.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.zombie_system.boss.ZombieCommanderEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 尸潮指挥官渲染器。
 *
 * <p>几何与动画都来自作者自己的 Blockbench 工程（见 {@link ZombieCommanderModel}），骨骼比原版僵尸多了
 * 帽子 / 背旗 / 军刀三条，所以不能复用原版 {@code ModelLayers.ZOMBIE} —— 模型层在这里自定义注册
 * （注册入口在 {@code ClientSetup#registerLayerDefinitions}）。</p>
 *
 * <p>父类用 {@link MobRenderer} 而不是 {@code AbstractZombieRenderer}：后者会挂上原版的内 / 外盔甲层，
 * 而盔甲层是按原版僵尸 UV 与骨骼名烘焙的，套到自定义骨骼上必然错位。{@code MobRenderer} 本身不追加
 * 任何层，仍然白拿受伤红闪、死亡倒下与头部饰品层。</p>
 *
 * <p><b>这里不能用 {@code LivingEntityRenderer}。</b>它虽然构造签名相同，但少了 {@code MobRenderer}
 * 对名牌的判定：{@code LivingEntityRenderer#shouldShowName} 只判距离与可见性、完全不看实体有没有名字，
 * 于是每只怪头上都会永久顶着自己的名字。{@code MobRenderer} 才补上那一层。</p>
 */
public final class ZombieCommanderRenderer
        extends MobRenderer<ZombieCommanderEntity, ZombieCommanderModel> {
    /** 自定义模型层：贴图 128x128，几何由 {@link ZombieCommanderModel#createBodyLayer} 提供。 */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "zombie_commander"),
            "main");

    /** 贴图与几何一起由 {@code tools/convert_commander_model.py} 从 bbmodel 生成。 */
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID,
            "textures/entity/zombie_commander/zombie_commander.png");

    /** 体型比普通僵尸大一号，影子也放大。 */
    private static final float SHADOW_RADIUS = 0.62F;

    public ZombieCommanderRenderer(EntityRendererProvider.Context context) {
        super(context, new ZombieCommanderModel(context.bakeLayer(LAYER)), SHADOW_RADIUS);
    }

    @Override
    public ResourceLocation getTextureLocation(ZombieCommanderEntity entity) {
        return TEXTURE;
    }
}
