package com.hhy.dreamingfishcore.gameplay.zombie_system.adamant.client;

import com.hhy.dreamingfishcore.gameplay.zombie_system.adamant.AdamantZombieEntity;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;

/**
 * 金刚僵尸渲染器：原版僵尸模型，按锈级切四张贴图。
 *
 * <p>锈级读的是 {@code dreamingfishcore:rusted} 效果的等级——效果会自动同步给客户端，所以
 * 这里不需要额外的同步字段。锈迹是「渐进的」，玩家一眼就能看出它现在锈到第几层；这比在
 * HUD 上写数字重要，因为它是一只靠外观传递状态的怪。</p>
 */
public final class AdamantZombieRenderer
        extends AbstractZombieRenderer<AdamantZombieEntity, ZombieModel<AdamantZombieEntity>> {
    /** 下标即锈级：0 = 未生锈，3 = 锈透。 */
    private static final ResourceLocation[] TEXTURES = {
            texture("adamant_zombie.png"),
            texture("adamant_zombie_rust1.png"),
            texture("adamant_zombie_rust2.png"),
            texture("adamant_zombie_rust3.png")
    };

    public AdamantZombieRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)),
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)));
    }

    @Override
    public ResourceLocation getTextureLocation(Zombie entity) {
        if (entity instanceof AdamantZombieEntity adamant) {
            int stage = adamant.rustStage();
            return TEXTURES[Math.max(0, Math.min(TEXTURES.length - 1, stage))];
        }
        return TEXTURES[0];
    }

    private static ResourceLocation texture(String fileName) {
        return ResourceLocation.fromNamespaceAndPath(
                "dreamingfishcore",
                "textures/entity/adamant_zombie/" + fileName);
    }
}
