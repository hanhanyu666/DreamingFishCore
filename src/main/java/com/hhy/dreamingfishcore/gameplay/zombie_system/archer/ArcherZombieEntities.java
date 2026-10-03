package com.hhy.dreamingfishcore.gameplay.zombie_system.archer;

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

/**
 * 射手僵尸与骨刺的注册入口。
 *
 * <p>结构与 {@code SiegeZombieEntities} 保持一致：实体类型、刷怪蛋、属性、生成位置规则都在
 * 同一个类里注册，刷怪蛋用 {@link DeferredSpawnEggItem} 避免「实体 → 物品」的注册环。</p>
 */
public final class ArcherZombieEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, DreamingFishCore.MODID);
    private static final DeferredRegister<Item> SPAWN_EGGS =
            DeferredRegister.create(BuiltInRegistries.ITEM, DreamingFishCore.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<ArcherZombieEntity>> ARCHER_ZOMBIE =
            ENTITIES.register("archer_zombie", () -> EntityType.Builder
                    .<ArcherZombieEntity>of(ArcherZombieEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("archer_zombie"));

    public static final DeferredHolder<EntityType<?>, EntityType<BoneSpikeEntity>> BONE_SPIKE =
            ENTITIES.register("bone_spike", () -> EntityType.Builder
                    .<BoneSpikeEntity>of(BoneSpikeEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .build("bone_spike"));

    public static final DeferredHolder<Item, Item> ARCHER_ZOMBIE_SPAWN_EGG = SPAWN_EGGS.register(
            "archer_zombie_spawn_egg",
            () -> new DeferredSpawnEggItem(ARCHER_ZOMBIE, 0x4A5A3A, 0xD8CFA8, new Item.Properties()));

    private ArcherZombieEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        SPAWN_EGGS.register(modEventBus);
        modEventBus.addListener(ArcherZombieEntities::createAttributes);
        modEventBus.addListener(ArcherZombieEntities::registerSpawnPlacements);
    }

    private static void createAttributes(EntityAttributeCreationEvent event) {
        event.put(ARCHER_ZOMBIE.get(), ArcherZombieEntity.createAttributes().build());
    }

    private static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(
                ARCHER_ZOMBIE.get(),
                ON_GROUND,
                MOTION_BLOCKING_NO_LEAVES,
                ArcherZombieEntity::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        // 骨刺是投射物，没有生成位置规则。
    }
}
