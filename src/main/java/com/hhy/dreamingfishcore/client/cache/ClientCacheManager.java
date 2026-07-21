package com.hhy.dreamingfishcore.client.cache;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.cache.PlayerAttributesClientCache;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData;
import com.hhy.dreamingfishcore.gameplay.task_system.TaskPlayerData;
import com.hhy.dreamingfishcore.gameplay.task_system.client.cache.TaskClientCache;
import com.hhy.dreamingfishcore.server.playerdata_system.PlayerData;
import com.hhy.dreamingfishcore.server.playerdata_system.client.cache.PlayerDataClientCache;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;

/**
 * 客户端缓存生命周期与旧 API 兼容入口。
 *
 * <p>真实缓存已经归还给各业务系统；新代码应直接依赖对应的
 * PlayerDataClientCache、PlayerAttributesClientCache 或 TaskClientCache。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class ClientCacheManager {
    private ClientCacheManager() {
    }

    public static Map<Integer, StoryStageData> getStoryStages() {
        return TaskClientCache.getStoryStages();
    }

    public static void setStoryStages(Map<Integer, StoryStageData> stages) {
        TaskClientCache.setStoryStages(stages);
    }

    public static Map<Integer, TaskPlayerData> getPlayerTasks() {
        return TaskClientCache.getPlayerTasks();
    }

    public static void setPlayerTasks(Map<Integer, TaskPlayerData> tasks) {
        TaskClientCache.setPlayerTasks(tasks);
    }

    public static StoryStageData getStoryStage(int stageId) {
        return TaskClientCache.getStoryStage(stageId);
    }

    public static TaskPlayerData getPlayerTask(int taskId) {
        return TaskClientCache.getPlayerTask(taskId);
    }

    public static boolean hasUnfinishedTasks() {
        return TaskClientCache.hasUnfinishedTasks();
    }

    public static PlayerData getPlayerData(UUID uuid) {
        return PlayerDataClientCache.get(uuid);
    }

    public static PlayerData getOrCreatePlayerData(UUID uuid) {
        return PlayerDataClientCache.getOrCreate(uuid);
    }

    public static void setPlayerData(UUID uuid, PlayerData data) {
        PlayerDataClientCache.put(uuid, data);
    }

    public static PlayerAttributesData getPlayerAttributesData(UUID uuid) {
        return PlayerAttributesClientCache.get(uuid);
    }

    public static PlayerAttributesData getOrCreatePlayerAttributesData(UUID uuid) {
        return PlayerAttributesClientCache.getOrCreate(uuid);
    }

    public static void setPlayerAttributesData(UUID uuid, PlayerAttributesData data) {
        PlayerAttributesClientCache.put(uuid, data);
    }

    public static Integer getExploredBiomesCount(UUID uuid) {
        return PlayerDataClientCache.getExploredBiomesCount(uuid);
    }

    public static void setExploredBiomesCount(UUID uuid, int count) {
        PlayerDataClientCache.setExploredBiomesCount(uuid, count);
    }

    public static Integer getUnlockedRecipesCount(UUID uuid) {
        return PlayerDataClientCache.getUnlockedRecipesCount(uuid);
    }

    public static void setUnlockedRecipesCount(UUID uuid, int count) {
        PlayerDataClientCache.setUnlockedRecipesCount(uuid, count);
    }

    public static float getRespawnPoint(UUID uuid) {
        return PlayerAttributesClientCache.getRespawnPoint(uuid);
    }

    public static void setRespawnPoint(UUID uuid, float respawnPoint) {
        PlayerAttributesClientCache.setRespawnPoint(uuid, respawnPoint);
    }

    public static boolean isInfected(UUID uuid) {
        return PlayerAttributesClientCache.isInfected(uuid);
    }

    public static void setInfected(UUID uuid, boolean infected) {
        PlayerAttributesClientCache.setInfected(uuid, infected);
    }

    public static float getNormalRespawnCost(UUID uuid) {
        return PlayerAttributesClientCache.getNormalRespawnCost(uuid);
    }

    public static float getKeepInventoryCost(UUID uuid) {
        return PlayerAttributesClientCache.getKeepInventoryCost(uuid);
    }

    public static int getRespawnTimes(UUID uuid) {
        return PlayerAttributesClientCache.getRespawnTimes(uuid);
    }

    public static void remove(UUID uuid) {
        PlayerDataClientCache.remove(uuid);
        PlayerAttributesClientCache.remove(uuid);
    }

    public static void clear() {
        PlayerDataClientCache.clear();
        PlayerAttributesClientCache.clear();
        TaskClientCache.clear();
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().level().isClientSide()) {
            clear();
        }
    }
}
