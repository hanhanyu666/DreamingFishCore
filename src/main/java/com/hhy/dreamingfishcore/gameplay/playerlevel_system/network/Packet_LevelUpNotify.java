package com.hhy.dreamingfishcore.gameplay.playerlevel_system.network;

import com.hhy.dreamingfishcore.client.ui.notification.Notification;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationManager;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationPosition;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationQueuePolicy;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationTheme;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;


/**
 * 等级升级提示网络包（服务端→客户端）
 * 携带升级后的新等级，用于客户端渲染提示
 */
public class Packet_LevelUpNotify implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<Packet_LevelUpNotify> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.hhy.dreamingfishcore.DreamingFishCore.MODID, "playerdata_system/packet_level_up_notify"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_LevelUpNotify> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, packet) -> Packet_LevelUpNotify.encode(packet, buf), Packet_LevelUpNotify::decode);

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }
    private final int newLevel; // 升级后的新等级

    // 构造方法
    public Packet_LevelUpNotify(int newLevel) {
        this.newLevel = newLevel;
    }

    // 编码（序列化：将数据写入字节流）
    public static void encode(Packet_LevelUpNotify packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.newLevel);
    }

    // 解码（反序列化：从字节流读取数据）
    public static Packet_LevelUpNotify decode(FriendlyByteBuf buf) {
        int newLevel = buf.readInt();
        return new Packet_LevelUpNotify(newLevel);
    }

    // 处理包（客户端执行：收到通知后，显示升级提示）
    public static void handle(Packet_LevelUpNotify packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 仅在客户端执行，调用提示管理器显示文字
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                NotificationManager.show(Notification.builder()
                        .title(Component.literal("§6您的等级提升了！"))
                        .message(Component.literal("§b当前等级：" + packet.newLevel + "，您的属性增加了"))
                        .position(NotificationPosition.TOP_LEFT)
                        .theme(NotificationTheme.DEFAULT)
                        .queuePolicy(NotificationQueuePolicy.STACK)
                        .durationMs(8000L)
                        .build());
            });
        });
    }

    // Getter（可选，若客户端需要获取新等级）
    public int getNewLevel() {
        return newLevel;
    }
}
