// Packet_SyncCompleteTask.java
package com.hhy.dreamingfishcore.gameplay.task_system.network;

import com.hhy.dreamingfishcore.gameplay.task_system.TaskDataManager;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public class Packet_SyncCompleteTask implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<Packet_SyncCompleteTask> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.hhy.dreamingfishcore.DreamingFishCore.MODID, "task_system/packet_sync_complete_task"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_SyncCompleteTask> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, packet) -> Packet_SyncCompleteTask.encode(packet, buf), Packet_SyncCompleteTask::decode);

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }
    private final int taskId; //任务ID
    private final boolean isServerTask; // true=故事任务，false=个人任务

    public Packet_SyncCompleteTask(int taskId, boolean isServerTask) {
        this.taskId = taskId;
        this.isServerTask = isServerTask;
    }

    public static void encode(Packet_SyncCompleteTask packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.taskId);
        buf.writeBoolean(packet.isServerTask);
    }

    public static Packet_SyncCompleteTask decode(FriendlyByteBuf buf) {
        int taskId = buf.readInt();
        boolean isServerTask = buf.readBoolean();
        return new Packet_SyncCompleteTask(taskId, isServerTask);
    }

    public static void handle(Packet_SyncCompleteTask packet, IPayloadContext context) {
        // 在服务端主线程执行
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null; // 获取发送请求的玩家
            if (player == null) return;

            // 故事任务由 Java 状态机验证事实后记录，客户端不能通过旧的
            // “完成任务”按钮伪造主线进度；管理员命令仍可用于人工结算。
            if (packet.isServerTask || StoryManager.isStoryTaskNumber(packet.taskId)) {
                DreamingFishCore.LOGGER.warn("拒绝客户端直接完成故事任务：taskId={}, player={}",
                        packet.taskId, player.getScoreboardName());
                return;
            }

            UUID playerUUID = player.getUUID();
            String playerName = player.getGameProfile().getName();

            // 仅允许旧的通用玩家任务走客户端完成入口；主线任务已经在上面拒绝。
            TaskDataManager.playerCompleteOwnTask(packet.taskId, playerName, playerUUID);
        });
    }
}
