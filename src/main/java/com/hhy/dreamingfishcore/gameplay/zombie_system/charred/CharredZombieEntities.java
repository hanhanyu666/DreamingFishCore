package com.hhy.dreamingfishcore.gameplay.zombie_system.charred;

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
 * 焦尸的注册入口：实体类型 + 刷怪蛋 + 属性。
 *
 * <p>结构与 {@code SiegeZombieEntities} / {@code ArcherZombieEntities} 保持一致。刻意**不注册**
 * 生成位置规则（{@code RegisterSpawnPlacementsEvent}）：生成位置只在自然生成时被读取，而焦尸按
 * 需求本版不接入刷怪池，现在注册一条不会被任何逻辑读到的规则属于死代码。将来要进刷怪池时，
 * 在 {@code ZombieSpawnPoolRewriter} 与这里各补一段即可。</p>
 */
public final class CharredZombieEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, DreamingFishCore.MODID);
    private static final DeferredRegister<Item> SPAWN_EGGS =
            DeferredRegister.create(BuiltInRegistries.ITEM, DreamingFishCore.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CharredZombieEntity>> CHARRED_ZOMBIE =
            ENTITIES.register("charred_zombie", () -> EntityType.Builder
                    .<CharredZombieEntity>of(CharredZombieEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("charred_zombie"));

    /** 刷怪蛋配色：炭黑底 + 余烬橙。 */
    public static final DeferredHolder<Item, Item> CHARRED_ZOMBIE_SPAWN_EGG = SPAWN_EGGS.register(
            "charred_zombie_spawn_egg",
            () -> new DeferredSpawnEggItem(CHARRED_ZOMBIE, 0x241C18, 0xFF7A18, new Item.Properties()));

    private CharredZombieEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        SPAWN_EGGS.register(modEventBus);
        modEventBus.addListener(CharredZombieEntities::createAttributes);
    }

    private static void createAttributes(EntityAttributeCreationEvent event) {
        event.put(CHARRED_ZOMBIE.get(), CharredZombieEntity.createAttributes().build());
    }
}
