package com.hhy.dreamingfishcore.gameplay.zombie_system.archer.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.zombie_system.archer.ArcherZombieEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 射手僵尸渲染器。
 *
 * <p>几何与动画都来自作者自己的 Blockbench 工程（见 {@link ArcherZombieModel}），骨骼名与比例是
 * 自定义的，所以不能像围攻僵尸那样复用原版 {@code ModelLayers.ZOMBIE}——模型层在这里自定义注册
 * （注册入口在 {@code ClientSetup#registerLayerDefinitions}）。</p>
 *
 * <p>父类用 {@link MobRenderer} 而不是 {@code AbstractZombieRenderer}：后者会挂上原版的内/外盔甲层，
 * 而盔甲层是按原版僵尸 UV 与骨骼名烘焙的，套到自定义骨骼上必然错位。{@code MobRenderer} 本身
 * 不追加任何层，仍然白拿受伤红闪、死亡倒下与头部饰品层。</p>
 *
 * <p><b>这里不能用 {@code LivingEntityRenderer}。</b>它虽然构造签名相同，但少了 {@code MobRenderer}
 * 对名牌的判定：{@code LivingEntityRenderer#shouldShowName} 只判距离与可见性，完全不看实体有没有
 * 名字，于是**每只怪头上都会永久顶着自己的名字**（看起来就像被命名牌命名过）。
 * {@code MobRenderer} 才补上「名字常显，或有自定义名且准星正对着它」这一层。</p>
 */
public final class ArcherZombieRenderer
        extends MobRenderer<ArcherZombieEntity, ArcherZombieModel> {
    /** 自定义模型层：贴图 64x64，由 {@code ArcherZombieModel#createBodyLayer} 提供几何。 */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "archer_zombie"),
            "main");

    /** 贴图由 {@code tools/convert_archer_zombie.py} 从 bbmodel 内嵌贴图烘焙而来。 */
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID,
            "textures/entity/archer_zombie/archer_zombie.png");

    /** 阴影半径，与原版僵尸一致。 */
    private static final float SHADOW_RADIUS = 0.5F;

    public ArcherZombieRenderer(EntityRendererProvider.Context context) {
        super(context, new ArcherZombieModel(context.bakeLayer(LAYER)), SHADOW_RADIUS);
    }

    @Override
    public ResourceLocation getTextureLocation(ArcherZombieEntity entity) {
        return TEXTURE;
    }
}
