package com.hhy.dreamingfishcore.server.persistence.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcRelationManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.RevivalInfoManager;
import com.hhy.dreamingfishcore.gameplay.playerlevel_system.biome.PlayerBiomesDataManager;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.hhy.dreamingfishcore.gameplay.story_system.ContentPackManager;
import com.hhy.dreamingfishcore.gameplay.story_system.WorldHistoryLog;
import com.hhy.dreamingfishcore.gameplay.storybook_system.StoryBookDataManager;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationManager;
import com.hhy.dreamingfishcore.gameplay.task_system.TaskDataManager;
import com.hhy.dreamingfishcore.server.login_system.PlayerLoginDataManager;
import com.hhy.dreamingfishcore.server.notice_system.PlayerNoticeDataManager;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.playerdata_system.PlayerDataManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Loads persistent data once, periodically flushes dirty managers and clears
 * server/world-scoped caches when the server stops.
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
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

        runSafely("加载世界历史日志", () -> WorldHistoryLog.loadWorldData(server));
        runSafely("加载登录数据", PlayerLoginDataManager::loadServerData);
        runSafely("加载玩家数据", () -> PlayerDataManager.loadWorldData(server));
        runSafely("加载玩家属性", () -> PlayerAttributesDataManager.loadWorldData(server));
        runSafely("加载群系探索", () -> PlayerBiomesDataManager.loadWorldData(server));
        runSafely("加载任务地点", TaskLocationManager::load);
        runSafely("加载故事系统", () -> StoryManager.loadWorldData(server));
        runSafely("加载故事内容包", ContentPackManager::loadWorldData);
        runSafely("加载玩家任务", () -> TaskDataManager.loadWorldData(server));
        runSafely("加载随记本", () -> StoryBookDataManager.loadWorldData(server));
        runSafely("加载 NPC 关系", () -> NpcRelationManager.loadWorldData(server));
        runSafely("加载复活信息", () -> RevivalInfoManager.loadWorldData(server));
        runSafely("加载公告已读状态", () -> PlayerNoticeDataManager.loadWorldData(server));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
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
        if (!saveDirtyData(event.getServer())) {
            DreamingFishCore.LOGGER.error("服务器停止前的数据保存不完整；将保留缓存并在玩家退出后重试");
        }
    }

    /**
     * Minecraft 会在 {@link ServerStoppingEvent} 之后才分发玩家退出事件。
     * 退出监听器会结算在线时长、登录元数据和可变属性，因此必须在这里
     * 再保存一次，且仅在所有写入成功后释放世界级缓存。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStopped(ServerStoppedEvent event) {
        if (!saveDirtyData(event.getServer())) {
            DreamingFishCore.LOGGER.error("服务器已停止，但仍有数据未成功写入；保留缓存以避免丢弃未保存状态");
            return;
        }

        clearWorldCaches();
        JsonDataStore.resetSession();
        autoSaveCounter = 0;
    }

    private static boolean saveDirtyData(MinecraftServer server) {
        boolean saved = true;
        saved &= runSaveSafely("保存登录数据", PlayerLoginDataManager::saveIfDirty);
        saved &= runSaveSafely("保存玩家数据", () -> PlayerDataManager.saveIfDirty(server));
        saved &= runSaveSafely("保存玩家属性", () -> PlayerAttributesDataManager.saveIfDirty(server));
        saved &= runSaveSafely("保存群系探索", () -> PlayerBiomesDataManager.saveIfDirty(server));
        saved &= runSaveSafely("保存故事系统", () -> StoryManager.saveIfDirty(server));
        saved &= runSaveSafely("保存玩家任务", () -> TaskDataManager.saveIfDirty(server));
        saved &= runSaveSafely("保存随记本", () -> StoryBookDataManager.saveIfDirty(server));
        saved &= runSaveSafely("保存 NPC 关系", () -> NpcRelationManager.saveIfDirty(server));
        saved &= runSaveSafely("保存复活信息", () -> RevivalInfoManager.saveIfDirty(server));
        saved &= runSaveSafely("保存公告已读状态", () -> PlayerNoticeDataManager.saveIfDirty(server));
        return saved;
    }

    private static void clearWorldCaches() {
        runSafely("清理登录数据缓存", PlayerLoginDataManager::clearServerCache);
        runSafely("清理玩家数据缓存", PlayerDataManager::clearWorldCache);
        runSafely("清理玩家属性缓存", PlayerAttributesDataManager::clearWorldCache);
        runSafely("清理群系探索缓存", PlayerBiomesDataManager::clearWorldCache);
        runSafely("清理故事系统缓存", StoryManager::clearWorldCache);
        runSafely("清理故事内容包缓存", ContentPackManager::clearWorldCache);
        runSafely("清理世界历史日志缓存", WorldHistoryLog::clearWorldCache);
        runSafely("清理任务地点缓存", TaskLocationManager::clearWorldCache);
        runSafely("清理任务缓存", TaskDataManager::clearWorldCache);
        runSafely("清理随记本缓存", StoryBookDataManager::clearWorldCache);
        runSafely("清理 NPC 关系缓存", NpcRelationManager::clearWorldCache);
        runSafely("清理复活信息缓存", RevivalInfoManager::clearWorldCache);
        runSafely("清理公告已读缓存", PlayerNoticeDataManager::clearWorldCache);
    }

    private static boolean runSaveSafely(String actionName, SaveAction action) {
        try {
            if (!action.save()) {
                DreamingFishCore.LOGGER.error("{}失败，保留缓存等待重试", actionName);
                return false;
            }
            return true;
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.error("{}失败", actionName, exception);
            return false;
        }
    }

    private static void runSafely(String actionName, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.error("{}失败", actionName, exception);
        }
    }

    @FunctionalInterface
    private interface SaveAction {
        boolean save();
    }
}
