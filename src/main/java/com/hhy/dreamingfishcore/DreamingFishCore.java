package com.hhy.dreamingfishcore;

import com.hhy.dreamingfishcore.item.DreamingFishCore_CreativeTabs;
import com.hhy.dreamingfishcore.item.DreamingFishCore_Items;
import com.hhy.dreamingfishcore.loot.DreamingFishCore_LootModifiers;
import com.hhy.dreamingfishcore.init.CommonInit;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(DreamingFishCore.MODID)
public class DreamingFishCore {
    public static final boolean isDev = false;
    public static final String MODID = "dreamingfishcore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DreamingFishCore(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        DreamingFishCore_Items.register(modEventBus);
        DreamingFishCore_NetworkManager.register();
        DreamingFishCore_CreativeTabs.register(modEventBus);
        DreamingFishCore_LootModifiers.register(modEventBus);
        CommonInit.initialize();

        LOGGER.info("DreamingfishCore Mod Initialized!");
    }
}
