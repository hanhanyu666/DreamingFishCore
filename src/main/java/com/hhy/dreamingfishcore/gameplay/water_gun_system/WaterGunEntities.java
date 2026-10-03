package com.hhy.dreamingfishcore.gameplay.water_gun_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 水柱的实体注册。物品本身注册在 {@code DreamingFishCore_Items} 里。 */
public final class WaterGunEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, DreamingFishCore.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<WaterJetEntity>> WATER_JET =
            ENTITIES.register("water_jet", () -> EntityType.Builder
                    .<WaterJetEntity>of(WaterJetEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F)
                    .clientTrackingRange(6)
                    .updateInterval(2)
                    .build("water_jet"));

    private WaterGunEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}
