package com.hhy.dreamingfishcore.server.server_ui_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.ui.notification.SystemEvent;
import com.hhy.dreamingfishcore.server.server_ui_system.client.SystemMessageDisplay;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 通用系统消息数据包 - 将所有系统消息显示在右上角
 *
 * <p>进服、离开、进度与死亡会额外带上事件类型、当事玩家与进度图标，右上角据此画成带头像或图标的事件卡；
 * 只有文字的旧消息照常显示为普通卡片。</p>
 */
public class Packet_SystemMessage implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<Packet_SystemMessage> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.hhy.dreamingfishcore.DreamingFishCore.MODID, "packet_system_message"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, Packet_SystemMessage> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, packet) -> Packet_SystemMessage.encode(packet, buf), Packet_SystemMessage::decode);

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }
    private final Component message;
    private final int borderColor; // 边框颜色
    @Nullable
    private final SystemMessageKind kind;
    @Nullable
    private final UUID playerId;
    private final String playerName;
    private final ItemStack icon;
    private final Component headline;

    public Packet_SystemMessage(Component message, int borderColor) {
        this(message, borderColor, null, null, "", ItemStack.EMPTY, Component.empty());
    }

    // 向后兼容：只传消息时使用默认颜色（Rank 颜色）
    public Packet_SystemMessage(Component message) {
        this(message, -1); // -1 表示使用本地玩家 Rank 颜色
    }

    /**
     * 带事件信息的系统消息。
     *
     * @param icon     进度的图标，其他事件传 {@link ItemStack#EMPTY}
     * @param headline 进度名称，其他事件传空组件
     */
    public Packet_SystemMessage(Component message, int borderColor, @Nullable SystemMessageKind kind,
                                @Nullable UUID playerId, String playerName, ItemStack icon, Component headline) {
        this.message = message;
        this.borderColor = borderColor;
        this.kind = kind;
        this.playerId = playerId;
        this.playerName = playerName == null ? "" : playerName;
        this.icon = icon == null ? ItemStack.EMPTY : icon;
        this.headline = headline == null ? Component.empty() : headline;
    }

    Component message() {
        return message;
    }

    int borderColor() {
        return borderColor;
    }

    @Nullable
    SystemMessageKind kind() {
        return kind;
    }

    @Nullable
    UUID playerId() {
        return playerId;
    }

    String playerName() {
        return playerName;
    }

    ItemStack icon() {
        return icon;
    }

    Component headline() {
        return headline;
    }

    public static void encode(Packet_SystemMessage packet, RegistryFriendlyByteBuf buf) {
        ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, packet.message);
        buf.writeInt(packet.borderColor);
        buf.writeByte(packet.kind == null ? -1 : packet.kind.ordinal());
        if (packet.kind == null) {
            return;
        }
        buf.writeBoolean(packet.playerId != null);
        if (packet.playerId != null) {
            buf.writeUUID(packet.playerId);
        }
        buf.writeUtf(packet.playerName);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, packet.icon);
        ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, packet.headline);
    }

    public static Packet_SystemMessage decode(RegistryFriendlyByteBuf buf) {
        Component message = ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf);
        int borderColor = buf.readInt();
        SystemMessageKind kind = SystemMessageKind.byId(buf.readByte());
        if (kind == null) {
            return new Packet_SystemMessage(message, borderColor);
        }
        UUID playerId = buf.readBoolean() ? buf.readUUID() : null;
        String playerName = buf.readUtf();
        ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        Component headline = ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf);
        return new Packet_SystemMessage(message, borderColor, kind, playerId, playerName, icon, headline);
    }

    public static void handle(Packet_SystemMessage packet, IPayloadContext context) {
        context.enqueueWork(() -> new ClientRunnable(packet).run());
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientRunnable implements Runnable {
        private final Packet_SystemMessage packet;

        ClientRunnable(Packet_SystemMessage packet) {
            this.packet = packet;
        }

        @Override
        public void run() {
            try {
                net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
                if (minecraft != null && minecraft.isSameThread() && minecraft.player != null) {
                    SystemEvent event = packet.kind == null ? null : new SystemEvent(packet.kind, packet.playerId,
                            packet.playerName, packet.icon, packet.headline);
                    SystemMessageDisplay.addMessage(packet.message, packet.borderColor, event);
                }
            } catch (Exception e) {
                DreamingFishCore.LOGGER.error("显示系统消息失败", e);
            }
        }
    }
}
