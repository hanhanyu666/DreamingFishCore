package com.hhy.dreamingfishcore.gameplay.zombie_system.charred.client;

import com.hhy.dreamingfishcore.gameplay.zombie_system.charred.CharredZombieEntity;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;

/**
 * 焦尸渲染器：原版僵尸模型与动画，单张贴图 + 余烬眼睛高亮层。
 *
 * <p>不像围攻僵尸那样有多套社区皮肤——焦尸是「烧焦的尸体」，皮肤只需要一张炭化的占位图，
 * 换正式美术时替换 {@code textures/entity/charred_zombie/charred_zombie.png} 即可。</p>
 */
public final class CharredZombieRenderer
        extends AbstractZombieRenderer<CharredZombieEntity, ZombieModel<CharredZombieEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "dreamingfishcore", "textures/entity/charred_zombie/charred_zombie.png");

    public CharredZombieRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)),
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)));
        this.addLayer(new CharredZombieEyesLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(Zombie entity) {
        return TEXTURE;
    }
}
