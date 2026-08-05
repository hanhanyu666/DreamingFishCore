package com.hhy.dreamingfishcore.gameplay.task_system.network;


import com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryTaskData;
import com.hhy.dreamingfishcore.gameplay.task_system.TaskPlayerData;
import com.hhy.dreamingfishcore.gameplay.task_system.client.cache.TaskClientCache;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

//全量包，进服申请一次
public class Packet_SyncFullTaskData implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<Packet_SyncFullTaskData> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.hhy.dreamingfishcore.DreamingFishCore.MODID, "task_system/packet_sync_full_task_data"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_SyncFullTaskData> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, packet) -> Packet_SyncFullTaskData.encode(packet, buf), Packet_SyncFullTaskData::decode);

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }
    private final UUID playerUUID;
    private Map<Integer, TaskPlayerData> taskPlayerData = new HashMap<>();
    private Map<Integer, StoryStageData> storyStageData = new HashMap<>();

    public Packet_SyncFullTaskData(UUID playerUUID, Map<Integer, TaskPlayerData> playerData, Map<Integer, StoryStageData> stageData) {
        this.playerUUID = playerUUID;
        this.taskPlayerData = new HashMap<>(playerData);
        this.storyStageData = new HashMap<>(stageData);
    }

    public static void encode(Packet_SyncFullTaskData packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.playerUUID);

        // ========== 编码玩家任务 ==========
        buf.writeInt(packet.taskPlayerData.size());
        for (Map.Entry<Integer, TaskPlayerData> entry : packet.taskPlayerData.entrySet()) {
            int taskId = entry.getKey();
            TaskPlayerData task = entry.getValue();

            buf.writeInt(taskId);
            buf.writeUtf(safeUtf(task.getTaskName()));
            buf.writeUtf(safeUtf(task.getTaskContent()));
            buf.writeLong(task.getTaskStartTime());
            buf.writeLong(task.getTaskEndTime());
            buf.writeBoolean(task.isPlayerFinished(packet.playerUUID));
        }

        // ========== 编码故事阶段数据 ==========
        buf.writeInt(packet.storyStageData.size()); // 阶段数量
        for (Map.Entry<Integer, StoryStageData> stageEntry : packet.storyStageData.entrySet()) {
            StoryStageData stage = stageEntry.getValue();

            // 编码阶段基本信息
            buf.writeUtf(stage.getStageId());
            buf.writeVarInt(stage.getStageNumber());
            buf.writeUtf(stage.getStageName());
            buf.writeUtf(stage.getStageDescription());

            // 编码怪物数值调整
            StoryStageData.MonsterModifier modifier = stage.getMonsterModifier();
            if (modifier != null) {
                buf.writeBoolean(true); // 有 modifier
                buf.writeFloat(modifier.getHealthMultiplier());
                buf.writeFloat(modifier.getDamageMultiplier());
                buf.writeFloat(modifier.getSpeedMultiplier());
                buf.writeFloat(modifier.getKnockbackResistance());
            } else {
                buf.writeBoolean(false);
            }

            // 编码该阶段的任务列表
            List<StoryTaskData> tasks = stage.getTasks();
            if (tasks == null) {
                buf.writeInt(0);
            } else {
                buf.writeInt(tasks.size());
                for (StoryTaskData task : tasks) {
                    buf.writeUtf(task.getTaskKey());
                    buf.writeVarInt(task.getTaskId());
                    buf.writeUtf(task.getTaskName());
                    buf.writeUtf(task.getTaskContent());
                    buf.writeLong(task.getStartTime());
                    buf.writeLong(task.getEndTime());
                    buf.writeBoolean(task.isTaskState());
                    buf.writeBoolean(task.isCompleted());
                    buf.writeBoolean(task.isFailed());
                    buf.writeBoolean(task.isClientPlayerFinished());
                    buf.writeVarInt(task.getFinishedPlayerCount());
                }
            }
        }
    }

    public static Packet_SyncFullTaskData decode(FriendlyByteBuf buf) {
        UUID playerUUID = buf.readUUID();

        // ========== 解码玩家任务 ==========
        int playerTaskSize = buf.readInt();
        Map<Integer, TaskPlayerData> playerTaskMap = new HashMap<>();

        for (int i = 0; i < playerTaskSize; i++) {
            int taskId = buf.readInt();
            String taskName = buf.readUtf();
            String taskContent = buf.readUtf();
            long startTime = buf.readLong();
            long endTime = buf.readLong();
            boolean isPlayerFinished = buf.readBoolean();

            TaskPlayerData task = new TaskPlayerData(taskId, taskName, taskContent, startTime, endTime);
            task.setClientPlayerFinished(isPlayerFinished);
            playerTaskMap.put(taskId, task);
        }

        // ========== 解码故事阶段数据 ==========
        int stageCount = buf.readInt();
        Map<Integer, StoryStageData> stageMap = new HashMap<>();

        for (int i = 0; i < stageCount; i++) {
            // 解码阶段基本信息
            String stageId = buf.readUtf();
            int stageNumber = buf.readVarInt();
            String stageName = buf.readUtf();
            String stageDescription = buf.readUtf();

            StoryStageData stage = new StoryStageData(stageId, stageNumber, stageName, stageDescription);

            // 解码怪物数值调整
            boolean hasModifier = buf.readBoolean();
            if (hasModifier) {
                float healthMult = buf.readFloat();
                float damageMult = buf.readFloat();
                float speedMult = buf.readFloat();
                float knockbackResist = buf.readFloat();
                stage.setMonsterModifier(new StoryStageData.MonsterModifier(healthMult, damageMult, speedMult, knockbackResist));
            }

            // 解码任务列表
            int taskCount = buf.readInt();
            List<StoryTaskData> tasks = new ArrayList<>();
            for (int j = 0; j < taskCount; j++) {
                String taskKey = buf.readUtf();
                int taskId = buf.readVarInt();
                String taskName = buf.readUtf();
                String taskContent = buf.readUtf();
                long startTime = buf.readLong();
                long endTime = buf.readLong();
                boolean taskState = buf.readBoolean();
                boolean isCompleted = buf.readBoolean();
                boolean isFailed = buf.readBoolean();
                boolean isPlayerFinished = buf.readBoolean();

                StoryTaskData task = new StoryTaskData(
                        taskKey, taskId, taskName, taskContent, startTime, endTime);
                task.setTaskState(taskState);
                task.setCompleted(isCompleted);
                task.setFailed(isFailed);
                task.setClientPlayerFinished(isPlayerFinished);
                task.setFinishedPlayerCount(buf.readVarInt());

                tasks.add(task);
            }
            stage.setTasks(tasks);
            stageMap.put(stageNumber, stage);
        }

        return new Packet_SyncFullTaskData(playerUUID, playerTaskMap, stageMap);
    }

    public static void handle(Packet_SyncFullTaskData packet, IPayloadContext context) {
        context.enqueueWork(() -> TaskClientCache.update(
                packet.getTaskPlayerData(),
                packet.getStoryStageData()
        ));
    }

    private static String safeUtf(String value) {
        return value == null ? "" : value;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public Map<Integer, TaskPlayerData> getTaskPlayerData() {
        return taskPlayerData;
    }

    public Map<Integer, StoryStageData> getStoryStageData() {
        return storyStageData;
    }
}
