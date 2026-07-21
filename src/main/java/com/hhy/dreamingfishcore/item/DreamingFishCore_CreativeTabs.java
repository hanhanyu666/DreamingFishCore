package com.hhy.dreamingfishcore.item;


import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.blueprint_system.PlayerBlueprintData;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class DreamingFishCore_CreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DreamingFishCore.MODID);

    public static final RegistryObject<CreativeModeTab> DREAMINGFISHCORE_TAB = CREATIVE_TABS.register("dreamingfishcore_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.dreamingfishcore.tab"))
                    .icon(() -> new ItemStack(DreamingFishCore_Items.DREAMINGFISH.get()))
                    .displayItems((params, output) -> {
                        output.accept(DreamingFishCore_Items.FRAGMENT_PAGE.get());
                        output.accept(DreamingFishCore_Items.STORY_BOOK.get());
                        output.accept(DreamingFishCore_Items.DREAMINGFISH.get());
                        output.accept(DreamingFishCore_Items.EASY_AID_KIT.get());
                        output.accept(DreamingFishCore_Items.ADVANCED_AID_KIT.get());
                        output.accept(DreamingFishCore_Items.PROFESSIONAL_AID_KIT.get());
                        output.accept(DreamingFishCore_Items.REVIVAL_CHARM.get());
                        output.accept(DreamingFishCore_Items.GENE_RESURGENCE_POTION.get());
                    })
                    .build());

    public static final RegistryObject<CreativeModeTab> BLUEPRINT_TAB = CREATIVE_TABS.register("blueprint_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.blueprint.tab"))
                    .icon(() -> new ItemStack(DreamingFishCore_Items.BLUEPRINT_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(DreamingFishCore_Items.BLUEPRINT_ITEM.get());
                        addAllBlueprintItems(output);
                    })
                    .build());

    private static void addAllBlueprintItems(CreativeModeTab.Output output) {
        PlayerBlueprintData.initAllBlueprintItems();
        for (ItemStack stack : PlayerBlueprintData.getAllBlueprintItems()) {
            output.accept(stack);
        }
    }

    public static void register(IEventBus eventBus) {
        CREATIVE_TABS.register(eventBus);
    }
}
