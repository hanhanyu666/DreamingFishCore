package com.hhy.dreamingfishcore.gameplay.npc_system.entity;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 集中注册实体类型及其基础属性。 */
public final class StoryNpcEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, DreamingFishCore.MODID);
    public static final RegistryObject<EntityType<StoryNpcEntity>> STORY_NPC = ENTITIES.register("story_npc", () ->
            EntityType.Builder.of(StoryNpcEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).clientTrackingRange(10).build("story_npc"));

    private StoryNpcEntities() { }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        bus.addListener(StoryNpcEntities::createAttributes);
    }

    private static void createAttributes(EntityAttributeCreationEvent event) {
        event.put(STORY_NPC.get(), StoryNpcEntity.createAttributes().build());
    }
}
