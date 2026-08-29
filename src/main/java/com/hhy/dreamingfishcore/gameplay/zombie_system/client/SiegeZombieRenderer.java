package com.hhy.dreamingfishcore.gameplay.zombie_system.client;

import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieEntity;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;

/** Uses the vanilla zombie model/animation with the twelve supplied community skins. */
public final class SiegeZombieRenderer
        extends AbstractZombieRenderer<SiegeZombieEntity, ZombieModel<SiegeZombieEntity>> {
    private static final ResourceLocation[] SKINS = {
            texture("zombiedf1.png"),
            texture("zombiedf2.png"),
            texture("zombiedf3.png"),
            texture("zombiedf4.png"),
            texture("zombiedf5.png"),
            texture("qingmozangbi.png"),
            texture("left2mine_zombie.png"),
            texture("zombie_girl.png"),
            texture("hanhanyu_z.png"),
            texture("qingmo_z.png"),
            texture("wither_light_z.png"),
            texture("jijituan_z.png")
    };

    public SiegeZombieRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)),
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)));
        this.addLayer(new SiegeZombieEyesLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(Zombie entity) {
        return entity instanceof SiegeZombieEntity siegeZombie
                ? SKINS[Math.floorMod(siegeZombie.getSkinVariantIndex(), SKINS.length)]
                : SKINS[0];
    }

    private static ResourceLocation texture(String fileName) {
        return ResourceLocation.fromNamespaceAndPath(
                "dreamingfishcore",
                "textures/entity/siege_zombie/" + fileName);
    }
}
