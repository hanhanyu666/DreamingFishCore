package com.hhy.dreamingfishcore.gameplay.zombie_system.adamant;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 金刚僵尸的注册入口：实体类型 + 刷怪蛋 + 属性。
 *
 * <p>与焦尸/射手僵尸一致，**不注册生成位置规则**：本版不接入刷怪池，注册了不会被任何逻辑读到。
 * 将来要进自然生成时，这里补一段 {@code RegisterSpawnPlacementsEvent} 即可。</p>
 */
public final class AdamantZombieEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, DreamingFishCore.MODID);
    private static final DeferredRegister<Item> SPAWN_EGGS =
            DeferredRegister.create(BuiltInRegistries.ITEM, DreamingFishCore.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<AdamantZombieEntity>> ADAMANT_ZOMBIE =
            ENTITIES.register("adamant_zombie", () -> EntityType.Builder
                    .<AdamantZombieEntity>of(AdamantZombieEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("adamant_zombie"));

    /** 刷怪蛋配色：铁灰底 + 锈橙斑。 */
    public static final DeferredHolder<Item, Item> ADAMANT_ZOMBIE_SPAWN_EGG = SPAWN_EGGS.register(
            "adamant_zombie_spawn_egg",
            () -> new DeferredSpawnEggItem(ADAMANT_ZOMBIE, 0x5A5F66, 0x8B5A2B, new Item.Properties()));

    private AdamantZombieEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        SPAWN_EGGS.register(modEventBus);
        modEventBus.addListener(AdamantZombieEntities::createAttributes);
    }

    private static void createAttributes(EntityAttributeCreationEvent event) {
        event.put(ADAMANT_ZOMBIE.get(), AdamantZombieEntity.createAttributes().build());
    }
}
