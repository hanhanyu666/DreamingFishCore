package com.hhy.dreamingfishcore.gameplay.zombie_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND;
import static net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;

/** Registry entries for the story-driven siege zombie species. */
public final class SiegeZombieEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, DreamingFishCore.MODID);
    private static final DeferredRegister<Item> SPAWN_EGGS =
            DeferredRegister.create(BuiltInRegistries.ITEM, DreamingFishCore.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<SiegeZombieEntity>> SIEGE_ZOMBIE =
            ENTITIES.register("siege_zombie", () -> EntityType.Builder.of(SiegeZombieEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("siege_zombie"));

    /** Uses NeoForge's deferred supplier so entity and item registration cannot form a cycle. */
    public static final DeferredHolder<Item, Item> SIEGE_ZOMBIE_SPAWN_EGG = SPAWN_EGGS.register(
            "siege_zombie_spawn_egg",
            () -> new DeferredSpawnEggItem(SIEGE_ZOMBIE, 0x3E4A35, 0x8B2F2F, new Item.Properties()));

    private SiegeZombieEntities() {
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        SPAWN_EGGS.register(bus);
        bus.addListener(SiegeZombieEntities::createAttributes);
        bus.addListener(SiegeZombieEntities::registerSpawnPlacements);
    }

    private static void createAttributes(EntityAttributeCreationEvent event) {
        event.put(SIEGE_ZOMBIE.get(), SiegeZombieEntity.createAttributes().build());
    }

    private static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(
                SIEGE_ZOMBIE.get(),
                ON_GROUND,
                MOTION_BLOCKING_NO_LEAVES,
                SiegeZombieEntity::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
