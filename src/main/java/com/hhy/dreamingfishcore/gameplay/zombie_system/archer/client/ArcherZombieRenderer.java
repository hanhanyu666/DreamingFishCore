package com.hhy.dreamingfishcore.gameplay.zombie_system.archer.client;

import com.hhy.dreamingfishcore.gameplay.zombie_system.archer.ArcherZombieEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;

/**
 * 射手僵尸渲染器：与原版僵尸同一套模型层、同一套装备层，只有贴图与模型子类不同。
 *
 * <p>贴图目前是**占位资源**——直接复制了围攻僵尸的一张皮肤，保证用原版僵尸 UV 能正常渲染；
 * 正式美术到位后只要替换 {@code textures/entity/archer_zombie/archer_zombie.png} 即可，
 * 不需要改代码。若要像围攻僵尸那样做多皮肤，参考 {@code SiegeZombieRenderer} 用 UUID 派生的
 * 稳定索引即可。</p>
 */
public final class ArcherZombieRenderer
        extends AbstractZombieRenderer<ArcherZombieEntity, ArcherZombieModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "dreamingfishcore",
            "textures/entity/archer_zombie/archer_zombie.png");

    public ArcherZombieRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new ArcherZombieModel(context.bakeLayer(ModelLayers.ZOMBIE)),
                new ArcherZombieModel(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new ArcherZombieModel(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)));
    }

    @Override
    public ResourceLocation getTextureLocation(Zombie entity) {
        return TEXTURE;
    }
}
