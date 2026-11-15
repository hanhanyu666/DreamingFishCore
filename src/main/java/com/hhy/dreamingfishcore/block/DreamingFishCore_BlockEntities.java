package com.hhy.dreamingfishcore.block;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 天灾反应器需要的两类注册：方块实体类型 + 容器菜单类型。
 *
 * <p>菜单用 {@link IMenuTypeExtension#create}：开屏时除了容器 id，还要把方块坐标发给客户端，
 * 客户端才能找回对应的方块实体（{@link DisasterReactorMenu#fromNetwork}）。</p>
 */
public final class DreamingFishCore_BlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, DreamingFishCore.MODID);

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(BuiltInRegistries.MENU, DreamingFishCore.MODID);

    /** 天灾反应器的方块实体。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DisasterReactorBlockEntity>>
            DISASTER_REACTOR = BLOCK_ENTITIES.register("disaster_reactor",
                    () -> BlockEntityType.Builder.of(DisasterReactorBlockEntity::new,
                            DreamingFishCore_Blocks.DISASTER_REACTOR.get()).build(null));

    /** 天灾反应器的容器菜单。 */
    public static final DeferredHolder<MenuType<?>, MenuType<DisasterReactorMenu>> DISASTER_REACTOR_MENU =
            MENU_TYPES.register("disaster_reactor",
                    () -> IMenuTypeExtension.create(DisasterReactorMenu::fromNetwork));

    private DreamingFishCore_BlockEntities() {
    }

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
        MENU_TYPES.register(eventBus);
    }
}
