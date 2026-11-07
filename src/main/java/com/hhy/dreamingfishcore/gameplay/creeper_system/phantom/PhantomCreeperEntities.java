package com.hhy.dreamingfishcore.gameplay.creeper_system.phantom;

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
 * 幻翼苦力怕的注册入口：实体类型 + 刷怪蛋 + 属性。
 *
 * <p>结构与 {@code CharredZombieEntities} / {@code AdamantZombieEntities} 一致：不注册生成位置规则，
 * 本版不接入刷怪池，只提供刷怪蛋与 {@code /summon}。</p>
 */
public final class PhantomCreeperEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, DreamingFishCore.MODID);
    private static final DeferredRegister<Item> SPAWN_EGGS =
            DeferredRegister.create(BuiltInRegistries.ITEM, DreamingFishCore.MODID);

    /**
     * 命中箱沿用原版苦力怕的 0.6 × 1.7。
     *
     * <p>视觉上它是趴平的（渲染时才转 90°），但命中箱不跟着转身：玩家打它、它炸玩家都按一个直立
     * 苦力怕的体积算，手感与原版一致，也免得出现「看着在头顶、实际打不到」的判定错位。</p>
     */
    public static final DeferredHolder<EntityType<?>, EntityType<PhantomCreeperEntity>> PHANTOM_CREEPER =
            ENTITIES.register("phantom_creeper", () -> EntityType.Builder
                    .<PhantomCreeperEntity>of(PhantomCreeperEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.7F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("phantom_creeper"));

    /** 刷怪蛋配色：幻翼的青灰底 + 苦力怕的亮绿斑点。 */
    public static final DeferredHolder<Item, Item> PHANTOM_CREEPER_SPAWN_EGG = SPAWN_EGGS.register(
            "phantom_creeper_spawn_egg",
            () -> new DeferredSpawnEggItem(PHANTOM_CREEPER, 0x4C6B7A, 0x5FD14E, new Item.Properties()));

    private PhantomCreeperEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        SPAWN_EGGS.register(modEventBus);
        modEventBus.addListener(PhantomCreeperEntities::createAttributes);
    }

    private static void createAttributes(EntityAttributeCreationEvent event) {
        event.put(PHANTOM_CREEPER.get(), PhantomCreeperEntity.createAttributes().build());
    }
}
