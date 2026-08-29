package com.hhy.dreamingfishcore;

import com.hhy.dreamingfishcore.item.DreamingFishCore_CreativeTabs;
import com.hhy.dreamingfishcore.item.DreamingFishCore_Items;
import com.hhy.dreamingfishcore.loot.DreamingFishCore_LootModifiers;
import com.hhy.dreamingfishcore.init.CommonInit;
import com.hhy.dreamingfishcore.gameplay.npc_system.entity.StoryNpcEntities;
import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieEntities;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.DeathCorpseEntities;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.compat.CorpseAccessoryCompat;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(DreamingFishCore.MODID)
public class DreamingFishCore {
    public static final boolean isDev = false;
    public static final String MODID = "dreamingfishcore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DreamingFishCore(IEventBus modEventBus, ModContainer modContainer) {
        // 注册物品
        DreamingFishCore_Items.register(modEventBus);
        // 注册网络包
        DreamingFishCore_NetworkManager.register(modEventBus);
        // 注册创造物品栏
        DreamingFishCore_CreativeTabs.CREATIVE_TABS.register(modEventBus);
        DreamingFishCore_LootModifiers.register(modEventBus);
        StoryNpcEntities.register(modEventBus);
        SiegeZombieEntities.register(modEventBus);
        DeathCorpseEntities.register(modEventBus);
        CorpseAccessoryCompat.initialize();
        CommonInit.initialize();

        // GeckoLib.initialize();

        // 日志信息
        LOGGER.info("DreamingfishCore Mod Initialized!");
    }
}
