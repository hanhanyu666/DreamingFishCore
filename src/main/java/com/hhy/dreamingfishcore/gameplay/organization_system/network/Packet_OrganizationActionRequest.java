package com.hhy.dreamingfishcore.gameplay.organization_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationManager;
import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationRank;
import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Locale;
import java.util.UUID;

/**
 * 客户端发起的组织操作请求。
 *
 * <p>用一个包承载全部动作，靠 {@link Action} 区分：动作多但字段结构一致（目标 id + 文本 + 布尔），
 * 所以不为每个操作单独写包。**所有校验都在服务端**，客户端传来的内容一律当作不可信输入。</p>
 */
public record Packet_OrganizationActionRequest(Action action, String targetId, String text,
                                               boolean flag) implements CustomPacketPayload {

    /** 可发起的操作。 */
    public enum Action {
        CREATE,
        RENAME,
        ANNOUNCE,
        APPLY,
        CANCEL_APPLICATION,
        RESPOND_INVITE,
        REVIEW_APPLICATION,
        INVITE,
        KICK,
        SET_RANK,
        TRANSFER_LEADERSHIP,
        LEAVE,
        DISBAND
    }

    public static final Type<Packet_OrganizationActionRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    DreamingFishCore.MODID, "organization/action_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_OrganizationActionRequest>
            STREAM_CODEC = StreamCodec.of(
                    Packet_OrganizationActionRequest::encode,
                    Packet_OrganizationActionRequest::decode);

    public Packet_OrganizationActionRequest {
        targetId = targetId == null ? "" : targetId;
        text = text == null ? "" : text;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer,
                               Packet_OrganizationActionRequest packet) {
        buffer.writeEnum(packet.action());
        buffer.writeUtf(packet.targetId(), 64);
        buffer.writeUtf(packet.text(), 512);
        buffer.writeBoolean(packet.flag());
    }

    private static Packet_OrganizationActionRequest decode(RegistryFriendlyByteBuf buffer) {
        return new Packet_OrganizationActionRequest(
                buffer.readEnum(Action.class),
                buffer.readUtf(64),
                buffer.readUtf(512),
                buffer.readBoolean());
    }

    public static void handle(Packet_OrganizationActionRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            OrganizationResult result = apply(player, packet);
            OrganizationSync.sendResult(player, result);
            if (result.success()) {
                // 变更后立刻回推快照，界面不需要自己拼接增量。
                OrganizationSync.broadcast(player.getServer());
            }
        });
    }

    private static OrganizationResult apply(ServerPlayer player,
                                            Packet_OrganizationActionRequest packet) {
        MinecraftServer server = player.getServer();
        return switch (packet.action()) {
            case CREATE -> OrganizationManager.create(player, packet.text());
            case RENAME -> OrganizationManager.rename(player, packet.text());
            case ANNOUNCE -> OrganizationManager.setAnnouncement(player, packet.text());
            case APPLY -> OrganizationManager.apply(player, packet.targetId());
            case CANCEL_APPLICATION ->
                    OrganizationManager.cancelApplication(player, packet.targetId());
            case RESPOND_INVITE ->
                    OrganizationManager.respondInvite(player, packet.targetId(), packet.flag());
            case REVIEW_APPLICATION ->
                    OrganizationManager.reviewApplication(player, packet.targetId(), packet.flag());
            case INVITE -> OrganizationManager.invite(
                    player, OrganizationManager.findOnlinePlayer(server, packet.text()));
            case KICK -> OrganizationManager.kick(player, uuidOf(packet.targetId()));
            case SET_RANK -> OrganizationManager.setRank(
                    player, uuidOf(packet.targetId()), rankOf(packet.text()));
            case TRANSFER_LEADERSHIP ->
                    OrganizationManager.transferLeadership(player, uuidOf(packet.targetId()));
            case LEAVE -> OrganizationManager.leave(player);
            case DISBAND -> OrganizationManager.disband(player);
        };
    }

    private static UUID uuidOf(String raw) {
        try {
            return UUID.fromString(raw == null ? "" : raw.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static OrganizationRank rankOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OrganizationRank.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
