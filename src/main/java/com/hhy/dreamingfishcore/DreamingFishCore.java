package com.hhy.dreamingfishcore;

import com.hhy.dreamingfishcore.core.npc_system.NpcManager;
import com.hhy.dreamingfishcore.core.playerattributes_system.limb_health_system.LimbDamageConfig;
import com.hhy.dreamingfishcore.init.Init;
import com.hhy.dreamingfishcore.item.DreamingFishCore_CreativeTabs;
import com.hhy.dreamingfishcore.item.DreamingFishCore_Items;
import com.hhy.dreamingfishcore.loot.DreamingFishCore_LootModifiers;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.notice.NoticeManager;
import com.hhy.dreamingfishcore.server.notice.PlayerNoticeDataManager;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(DreamingFishCore.MODID)
public class DreamingFishCore {
    public static final boolean isDev = true;
    public static final String MODID = "dreamingfishcore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DreamingFishCore(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        DreamingFishCore_Items.register(modEventBus);
        DreamingFishCore_NetworkManager.register();
        DreamingFishCore_CreativeTabs.register(modEventBus);
        DreamingFishCore_LootModifiers.register(modEventBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        new Init();
        NoticeManager.loadFromConfig();
        PlayerNoticeDataManager.init();
        NpcManager.init();
        LimbDamageConfig.init();

        LOGGER.info("DreamingfishCore Mod Initialized!");
    }
}
