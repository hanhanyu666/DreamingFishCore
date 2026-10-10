package com.hhy.dreamingfishcore.gameplay.zombie_system.boss;

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

/** 仅注册命令/刷怪蛋生成的 Boss；不接入自然刷怪池。 */
public final class ZombieCommanderEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, DreamingFishCore.MODID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, DreamingFishCore.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<ZombieCommanderEntity>> ZOMBIE_COMMANDER =
            ENTITIES.register("zombie_commander", () -> EntityType.Builder
                    .<ZombieCommanderEntity>of(ZombieCommanderEntity::new, MobCategory.MONSTER)
                    .sized(0.72F, 2.34F).eyeHeight(2.088F)
                    .clientTrackingRange(10).updateInterval(2).build("zombie_commander"));
    public static final DeferredHolder<EntityType<?>, EntityType<CommanderMinionEntity>> COMMANDER_MINION =
            ENTITIES.register("commander_minion", () -> EntityType.Builder
                    .<CommanderMinionEntity>of(CommanderMinionEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(8).updateInterval(3).build("commander_minion"));
    public static final DeferredHolder<Item, Item> ZOMBIE_COMMANDER_SPAWN_EGG = ITEMS.register(
            "zombie_commander_spawn_egg",
            () -> new DeferredSpawnEggItem(ZOMBIE_COMMANDER, 0x242F29, 0xB271DC, new Item.Properties()));

    private ZombieCommanderEntities() { }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        ITEMS.register(bus);
        bus.addListener(ZombieCommanderEntities::createAttributes);
    }

    private static void createAttributes(EntityAttributeCreationEvent event) {
        event.put(ZOMBIE_COMMANDER.get(), ZombieCommanderEntity.createAttributes().build());
        event.put(COMMANDER_MINION.get(), CommanderMinionEntity.createAttributes().build());
    }
}
