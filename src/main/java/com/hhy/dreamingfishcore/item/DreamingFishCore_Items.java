package com.hhy.dreamingfishcore.item;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.item.items.Item_AidKit;
import com.hhy.dreamingfishcore.item.items.Item_Blueprint;
import com.hhy.dreamingfishcore.item.items.Item_FragmentPage;
import com.hhy.dreamingfishcore.item.items.Item_Guitar;
import com.hhy.dreamingfishcore.item.items.Item_RevivalCharm;
import com.hhy.dreamingfishcore.item.items.Item_StoryBook;
import com.hhy.dreamingfishcore.item.items.Potion_RestoreUnInfected;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public class DreamingFishCore_Items {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, DreamingFishCore.MODID);

    public static final RegistryObject<Item> GUITAR = ITEMS.register("guitar",
            () -> new Item_Guitar(new Item.Properties().stacksTo(1).fireResistant()));

    public static final RegistryObject<Item> DREAMINGFISH = ITEMS.register("dreamingfish",
            () -> new Item(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON)) {
                @Override
                public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
                    super.appendHoverText(stack, level, tooltip, flag);
                    tooltip.add(Component.translatable("item.dreamingfishcore.dreamingfish.tooltip"));
                }
            });

    public static final RegistryObject<Item> BLUEPRINT_ITEM = ITEMS.register("blueprint",
            () -> new Item_Blueprint(new Item.Properties().stacksTo(1).fireResistant()));

    public static final RegistryObject<Item> FRAGMENT_PAGE = ITEMS.register("fragment_page",
            () -> new Item_FragmentPage(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> STORY_BOOK = ITEMS.register("story_book",
            () -> new Item_StoryBook(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final RegistryObject<Item> BLANK_BLUEPRINT = ITEMS.register("blank_blueprint",
            () -> new Item(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<Item> EASY_AID_KIT = ITEMS.register("easy_aid_kit",
            () -> new Item_AidKit(20, 1.0, 20, 1000, 100, "简易急救包"));

    public static final RegistryObject<Item> ADVANCED_AID_KIT = ITEMS.register("advanced_aid_kit",
            () -> new Item_AidKit(15, 2.0, 30, 800, 80, "高级急救包"));

    public static final RegistryObject<Item> PROFESSIONAL_AID_KIT = ITEMS.register("professional_aid_kit",
            () -> new Item_AidKit(10, 3.0, 40, 600, 60, "专业急救包"));

    public static final RegistryObject<Item> REVIVAL_CHARM = ITEMS.register("revival_charm",
            () -> new Item_RevivalCharm(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final RegistryObject<Item> GENE_RESURGENCE_POTION = ITEMS.register("restore_uninfected_potion",
            () -> new Potion_RestoreUnInfected(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
