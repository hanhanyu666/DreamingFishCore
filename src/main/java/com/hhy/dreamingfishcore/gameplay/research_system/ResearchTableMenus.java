package com.hhy.dreamingfishcore.gameplay.research_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 研究桌的菜单类型注册器。
 *
 * <p>用 NeoForge 的 {@link IMenuTypeExtension#create} 而不是原版 {@code MenuType}：
 * 开屏时除了容器 id 之外还要把**研究桌坐标**发给客户端，客户端工厂
 * （{@link ResearchTableMenu#fromNetwork}）从额外数据里读回来。</p>
 */
public final class ResearchTableMenus {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(BuiltInRegistries.MENU, DreamingFishCore.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ResearchTableMenu>> RESEARCH_TABLE =
            MENU_TYPES.register("research_table",
                    () -> IMenuTypeExtension.create(ResearchTableMenu::fromNetwork));

    private ResearchTableMenus() {
    }

    /** 由 {@code DreamingFishCore} 主类在 mod 事件总线上统一挂载。 */
    public static void register(IEventBus eventBus) {
        MENU_TYPES.register(eventBus);
    }
}
