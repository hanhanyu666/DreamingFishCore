package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 尸体实体注册。 */
public final class DeathCorpseEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(
            BuiltInRegistries.ENTITY_TYPE, DreamingFishCore.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<DeathCorpseEntity>> DEATH_CORPSE =
            ENTITIES.register("death_corpse", () -> EntityType.Builder
                    .of(DeathCorpseEntity::new, MobCategory.MISC)
                    .sized(2.0F, 0.5F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .build("death_corpse"));

    private DeathCorpseEntities() {
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}
