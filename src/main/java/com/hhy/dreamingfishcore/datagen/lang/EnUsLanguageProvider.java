package com.hhy.dreamingfishcore.datagen.lang;

import com.hhy.dreamingfishcore.item.DreamingFishCore_Items;
import net.minecraft.data.DataGenerator;
import net.minecraftforge.common.data.LanguageProvider;

public class EnUsLanguageProvider extends LanguageProvider {
    public EnUsLanguageProvider(DataGenerator gen, String locale) {
        super(gen.getPackOutput(), locale, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("key.categories.dreamingfishcore", "Dreaming Fish Core");
        add("key.dreamingfishcore.open_screen_o", "Open Information Panel");
        add("key.dreamingfishcore.open_terminal_u", "Open Server Terminal");
        add("key.dreamingfishcore.fps_marker", "FPS Marker");
        add("itemGroup.dreamingfishcore.tab", "DreamingfishCore");
        add("itemGroup.blueprint.tab", "Dreamingfish Blueprints");
        add(DreamingFishCore_Items.GUITAR.get(), "Guitar");
        add(DreamingFishCore_Items.DREAMINGFISH.get(), "Dreaming Fish");
        add("item.dreamingfishcore.dreamingfish.tooltip", "A token from DreamingFish.");
        add(DreamingFishCore_Items.BLUEPRINT_ITEM.get(), "Blueprint");
        add(DreamingFishCore_Items.BLANK_BLUEPRINT.get(), "Blank Blueprint");
        add(DreamingFishCore_Items.FRAGMENT_PAGE.get(), "Fragment Page");
        add(DreamingFishCore_Items.STORY_BOOK.get(), "Story Book");
        add(DreamingFishCore_Items.EASY_AID_KIT.get(), "Easy Aid Kit");
        add(DreamingFishCore_Items.ADVANCED_AID_KIT.get(), "Advanced Aid Kit");
        add(DreamingFishCore_Items.PROFESSIONAL_AID_KIT.get(), "Professional Aid Kit");
        add(DreamingFishCore_Items.REVIVAL_CHARM.get(), "Revival Charm");
        add(DreamingFishCore_Items.GENE_RESURGENCE_POTION.get(), "Gene Resurgence Potion");
    }
}
