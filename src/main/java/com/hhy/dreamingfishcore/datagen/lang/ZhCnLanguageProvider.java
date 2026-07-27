package com.hhy.dreamingfishcore.datagen.lang;

import com.hhy.dreamingfishcore.item.DreamingFishCore_Items;
import net.minecraft.data.DataGenerator;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class ZhCnLanguageProvider extends LanguageProvider {
    public ZhCnLanguageProvider(DataGenerator gen, String locale) {
        super(gen.getPackOutput(), locale, "zh_cn");
    }

    @Override
    protected void addTranslations() {
        add("key.categories.dreamingfishcore", "梦屿核心");
        add("key.dreamingfishcore.open_screen_o", "打开信息面板");
        add("key.dreamingfishcore.open_terminal_u", "打开服务器终端");
        add("key.dreamingfishcore.fps_marker", "FPS 标点");
        add("itemGroup.dreamingfishcore.tab", "DreamingfishCore");
        add("itemGroup.blueprint.tab", "梦鱼蓝图");
        add(DreamingFishCore_Items.DREAMINGFISH.get(), "启程锦鲤");
        add("item.dreamingfishcore.dreamingfish.tooltip", "来自梦鱼服的纪念信物。");
        add(DreamingFishCore_Items.BLUEPRINT_ITEM.get(), "蓝图");
        add(DreamingFishCore_Items.FRAGMENT_PAGE.get(), "故事碎片");
        add(DreamingFishCore_Items.STORY_BOOK.get(), "随记本");
        add(DreamingFishCore_Items.EASY_AID_KIT.get(), "简易急救包");
        add(DreamingFishCore_Items.ADVANCED_AID_KIT.get(), "高级急救包");
        add(DreamingFishCore_Items.PROFESSIONAL_AID_KIT.get(), "专业急救包");
        add(DreamingFishCore_Items.REVIVAL_CHARM.get(), "复活护符");
        add(DreamingFishCore_Items.GENE_RESURGENCE_POTION.get(), "基因复苏药剂");
    }
}
