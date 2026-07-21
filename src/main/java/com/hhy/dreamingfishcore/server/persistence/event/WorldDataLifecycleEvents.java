package com.hhy.dreamingfishcore.server.persistence.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcRelationManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.RevivalInfoManager;
import com.hhy.dreamingfishcore.gameplay.playerlevel_system.biome.PlayerBiomesDataManager;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.hhy.dreamingfishcore.gameplay.storybook_system.StoryBookDataManager;
import com.hhy.dreamingfishcore.gameplay.task_system.TaskDataManager;
import com.hhy.dreamingfishcore.server.login_system.PlayerLoginDataManager;
import com.hhy.dreamingfishcore.server.notice_system.PlayerNoticeDataManager;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.playerdata_system.PlayerDataManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Loads persistent data once, periodically flushes dirty managers and clears
 * server/world-scoped caches when the server stops.
 */
@Mod.EventBusSubscriber(modid = DreamingFishCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldDataLifecycleEvents {
    private static final int AUTO_SAVE_INTERVAL_TICKS = 1200;
    private static int autoSaveCounter;

    private WorldDataLifecycleEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        JsonDataStore.resetSession();
        autoSaveCounter = 0;

        runSafely("加载登录数据", PlayerLoginDataManager::loadServerData);
        runSafely("加载玩家数据", () -> PlayerDataManager.loadWorldData(server));
        runSafely("加载玩家属性", () -> PlayerAttributesDataManager.loadWorldData(server));
        runSafely("加载群系探索", () -> PlayerBiomesDataManager.loadWorldData(server));
        runSafely("加载故事系统", () -> StoryManager.loadWorldData(server));
        runSafely("加载玩家任务", () -> TaskDataManager.loadWorldData(server));
        runSafely("加载随记本", () -> StoryBookDataManager.loadWorldData(server));
        runSafely("加载 NPC 关系", () -> NpcRelationManager.loadWorldData(server));
        runSafely("加载复活信息", () -> RevivalInfoManager.loadWorldData(server));
        runSafely("加载公告已读状态", () -> PlayerNoticeDataManager.loadWorldData(server));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        StoryManager.tickActiveTime(event.getServer());
        if (++autoSaveCounter >= AUTO_SAVE_INTERVAL_TICKS) {
            autoSaveCounter = 0;
            saveDirtyData(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            saveDirtyData(level.getServer());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStopping(ServerStoppingEvent event) {
        saveDirtyData(event.getServer());
        clearWorldCaches();
        JsonDataStore.resetSession();
        autoSaveCounter = 0;
    }

    private static void saveDirtyData(MinecraftServer server) {
        runSafely("保存登录数据", PlayerLoginDataManager::saveIfDirty);
        runSafely("保存玩家数据", () -> PlayerDataManager.saveIfDirty(server));
        runSafely("保存玩家属性", () -> PlayerAttributesDataManager.saveIfDirty(server));
        runSafely("保存群系探索", () -> PlayerBiomesDataManager.saveIfDirty(server));
        runSafely("保存故事系统", () -> StoryManager.saveIfDirty(server));
        runSafely("保存玩家任务", () -> TaskDataManager.saveIfDirty(server));
        runSafely("保存随记本", () -> StoryBookDataManager.saveIfDirty(server));
        runSafely("保存 NPC 关系", () -> NpcRelationManager.saveIfDirty(server));
        runSafely("保存复活信息", () -> RevivalInfoManager.saveIfDirty(server));
        runSafely("保存公告已读状态", () -> PlayerNoticeDataManager.saveIfDirty(server));
    }

    private static void clearWorldCaches() {
        runSafely("清理登录数据缓存", PlayerLoginDataManager::clearServerCache);
        runSafely("清理玩家数据缓存", PlayerDataManager::clearWorldCache);
        runSafely("清理玩家属性缓存", PlayerAttributesDataManager::clearWorldCache);
        runSafely("清理群系探索缓存", PlayerBiomesDataManager::clearWorldCache);
        runSafely("清理故事系统缓存", StoryManager::clearWorldCache);
        runSafely("清理任务缓存", TaskDataManager::clearWorldCache);
        runSafely("清理随记本缓存", StoryBookDataManager::clearWorldCache);
        runSafely("清理 NPC 关系缓存", NpcRelationManager::clearWorldCache);
        runSafely("清理复活信息缓存", RevivalInfoManager::clearWorldCache);
        runSafely("清理公告已读缓存", PlayerNoticeDataManager::clearWorldCache);
    }

    private static void runSafely(String actionName, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.error("{}失败", actionName, exception);
        }
    }
}
