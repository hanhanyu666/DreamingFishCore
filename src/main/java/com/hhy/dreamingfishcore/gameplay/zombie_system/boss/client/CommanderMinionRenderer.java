package com.hhy.dreamingfishcore.gameplay.zombie_system.boss.client;

import com.hhy.dreamingfishcore.gameplay.zombie_system.boss.CommanderMinionEntity;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;

/**
 * 指挥官召唤的近战护卫渲染器：纯原版僵尸几何（含盔甲层与瞳孔层），只是不换皮肤。
 *
 * <p>与 Boss 不同，护卫将来也不会换成自定义骨骼，所以这里直接继承
 * {@link AbstractZombieRenderer} 白拿原版的盔甲层与眼睛层，没有必要走 {@code MobRenderer}。</p>
 */
public final class CommanderMinionRenderer
        extends AbstractZombieRenderer<CommanderMinionEntity, ZombieModel<CommanderMinionEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png");

    public CommanderMinionRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)),
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)));
    }

    @Override
    public ResourceLocation getTextureLocation(Zombie entity) {
        return TEXTURE;
    }
}
