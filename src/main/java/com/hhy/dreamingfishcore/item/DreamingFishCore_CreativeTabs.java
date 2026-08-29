package com.hhy.dreamingfishcore.item;


import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.blueprint_system.PlayerBlueprintData;
import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieEntities;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class DreamingFishCore_CreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DreamingFishCore.MODID);

    public static final DeferredHolder<CreativeModeTab, ? extends CreativeModeTab> dreamingfishcore_TAB = CREATIVE_TABS.register("dreamingfishcore_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.dreamingfishcore.tab")) // 物品栏名称
                    .icon(() -> new ItemStack(DreamingFishCore_Items.DREAMINGFISH.get())) // 设置图标
                    .displayItems((params, output) -> {
                        output.accept(DreamingFishCore_Items.FRAGMENT_PAGE.get());
                        output.accept(DreamingFishCore_Items.STORY_BOOK.get());
                        output.accept(DreamingFishCore_Items.DREAMINGFISH.get());
                        output.accept(DreamingFishCore_Items.EASY_AID_KIT.get());
                        output.accept(DreamingFishCore_Items.ADVANCED_AID_KIT.get());
                        output.accept(DreamingFishCore_Items.PROFESSIONAL_AID_KIT.get());
                        output.accept(DreamingFishCore_Items.REVIVAL_CHARM.get());
                        output.accept(DreamingFishCore_Items.GENE_RESURGENCE_POTION.get());
                        output.accept(SiegeZombieEntities.SIEGE_ZOMBIE_SPAWN_EGG.get());
                    })
                    .build()
    );

    public static final DeferredHolder<CreativeModeTab, ? extends CreativeModeTab> BLUEPRINT_TAB = CREATIVE_TABS.register("blueprint_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.blueprint.tab")) // 物品栏名称
                    .icon(() -> new ItemStack(DreamingFishCore_Items.BLUEPRINT_ITEM.get())) // 设置图标
                    .displayItems((params, output) -> {
                        output.accept(DreamingFishCore_Items.BLUEPRINT_ITEM.get());
                        addAllBlueprintItems(output);
                    })
                    .build()
    );

    private static void addAllBlueprintItems(CreativeModeTab.Output output) {
        PlayerBlueprintData.initAllBlueprintItems();
        // 为每个物品创建蓝图
        for (ItemStack stack : PlayerBlueprintData.getAllBlueprintItems()) {
            output.accept(stack);
        }
    }

    public static void register(IEventBus eventBus) {
        CREATIVE_TABS.register(eventBus);
    }
}
