package com.hhy.dreamingfishcore;

import com.hhy.dreamingfishcore.item.DreamingFishCore_CreativeTabs;
import com.hhy.dreamingfishcore.item.DreamingFishCore_Items;
import com.hhy.dreamingfishcore.loot.DreamingFishCore_LootModifiers;
import com.hhy.dreamingfishcore.effect.DreamingFishCore_Effects;
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
        // 注册方块（聚居地过滤装置等）；方块物品在 DreamingFishCore_Items 里注册
        com.hhy.dreamingfishcore.block.DreamingFishCore_Blocks.register(modEventBus);
        // 注册物品
        DreamingFishCore_Items.register(modEventBus);
        // 注册网络包
        DreamingFishCore_NetworkManager.register(modEventBus);
        // 注册创造物品栏
        DreamingFishCore_CreativeTabs.CREATIVE_TABS.register(modEventBus);
        // 注册状态效果（感染 / 害怕 / 勇气）
        DreamingFishCore_Effects.register(modEventBus);
        DreamingFishCore_LootModifiers.register(modEventBus);
        StoryNpcEntities.register(modEventBus);
        SiegeZombieEntities.register(modEventBus);
        DeathCorpseEntities.register(modEventBus);
        // 注册菜单类型（研究桌的容器菜单）
        com.hhy.dreamingfishcore.gameplay.research_system.ResearchTableMenus.register(modEventBus);
        CorpseAccessoryCompat.initialize();
        CommonInit.initialize();

        // GeckoLib.initialize();

        // 日志信息
        LOGGER.info("DreamingfishCore Mod Initialized!");
    }
}
