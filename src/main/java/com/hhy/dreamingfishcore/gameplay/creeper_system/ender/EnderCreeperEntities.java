package com.hhy.dreamingfishcore.gameplay.creeper_system.ender;

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
 * 末影人苦力怕的注册入口：实体类型 + 刷怪蛋 + 属性。
 *
 * <p>与 {@code PhantomCreeperEntities} 同构；不注册生成位置规则，本版不接入刷怪池。</p>
 */
public final class EnderCreeperEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, DreamingFishCore.MODID);
    private static final DeferredRegister<Item> SPAWN_EGGS =
            DeferredRegister.create(BuiltInRegistries.ITEM, DreamingFishCore.MODID);

    /** 命中箱与原版苦力怕一致（0.6 × 1.7），瞬移不改变体积。 */
    public static final DeferredHolder<EntityType<?>, EntityType<EnderCreeperEntity>> ENDER_CREEPER =
            ENTITIES.register("ender_creeper", () -> EntityType.Builder
                    .<EnderCreeperEntity>of(EnderCreeperEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.7F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("ender_creeper"));

    /** 刷怪蛋配色：末影黑紫底 + 苦力怕亮绿斑点。 */
    public static final DeferredHolder<Item, Item> ENDER_CREEPER_SPAWN_EGG = SPAWN_EGGS.register(
            "ender_creeper_spawn_egg",
            () -> new DeferredSpawnEggItem(ENDER_CREEPER, 0x14121C, 0x5FD14E, new Item.Properties()));

    private EnderCreeperEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        SPAWN_EGGS.register(modEventBus);
        modEventBus.addListener(EnderCreeperEntities::createAttributes);
    }

    private static void createAttributes(EntityAttributeCreationEvent event) {
        event.put(ENDER_CREEPER.get(), EnderCreeperEntity.createAttributes().build());
    }
}
