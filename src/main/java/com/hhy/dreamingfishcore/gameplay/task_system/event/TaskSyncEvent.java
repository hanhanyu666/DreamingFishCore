package com.hhy.dreamingfishcore.gameplay.task_system.event;

import com.hhy.dreamingfishcore.gameplay.task_system.TaskDataManager;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.gameplay.task_system.network.Packet_SyncFullTaskData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = DreamingFishCore.MODID, bus =  Mod.EventBusSubscriber.Bus.FORGE)
public class TaskSyncEvent {
    @SubscribeEvent
    public static void onPlayerLogging(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var playerUUID = player.getUUID();
            //从缓存里面获取全量任务数据
            var storyStages = StoryManager.getStagesForPlayer(playerUUID);
            var playerTasks = TaskDataManager.TASK_PLAYER_DATA_CACHE;

            //构建同步数据包
            Packet_SyncFullTaskData syncPacket = new Packet_SyncFullTaskData(
                    playerUUID,
                    playerTasks,
                    storyStages
            );

            //向当前登录玩家发送数据包
            DreamingFishCore_NetworkManager.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> (net.minecraft.server.level.ServerPlayer) player),
                    syncPacket
            );

            DreamingFishCore.LOGGER.info("已向玩家 {}({}) 同步全量任务数据",
                    player.getDisplayName().getString(),
                    playerUUID);
        }
    }
}
