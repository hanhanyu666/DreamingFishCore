package com.hhy.dreamingfishcore.gameplay.spawner_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.spawner_system.SpawnerService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Locale;

/**
 * 客户端提交一次配置修改。
 *
 * <p>一个包承载全部修改动作（靠 {@link Action} 区分），与组织系统的动作包同构。
 * 客户端传来的数值一律当作不可信输入，服务端用登记表的 setter 重新夹取。</p>
 */
public record Packet_SpawnerConfigRequest(BlockPos pos, Action action, int value,
                                          boolean flag, String text)
        implements CustomPacketPayload {

    /** 可修改的字段与操作。 */
    public enum Action {
        SET_ENTITY,
        SET_DETECTION_RADIUS,
        SET_SPAWN_RADIUS,
        SET_SPAWN_COUNT,
        SET_COOLDOWN,
        SET_BATCHES,
        SET_REDSTONE,
        SET_SELF_DESTRUCT,
        SET_CLUE_ENABLED,
        SET_CLUE_ID,
        SET_REWARD_EXPERIENCE,
        SET_REWARD_COINS,
        ADD_REWARD_ITEM,
        REMOVE_REWARD_ITEM,
        RESET_ROUND
    }

    public static final Type<Packet_SpawnerConfigRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    DreamingFishCore.MODID, "spawner/config_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_SpawnerConfigRequest>
            STREAM_CODEC = StreamCodec.of(
                    Packet_SpawnerConfigRequest::encode,
                    Packet_SpawnerConfigRequest::decode);

    public Packet_SpawnerConfigRequest {
        text = text == null ? "" : text;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, Packet_SpawnerConfigRequest packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeEnum(packet.action());
        buffer.writeVarInt(packet.value());
        buffer.writeBoolean(packet.flag());
        buffer.writeUtf(packet.text(), 128);
    }

    private static Packet_SpawnerConfigRequest decode(RegistryFriendlyByteBuf buffer) {
        return new Packet_SpawnerConfigRequest(
                buffer.readBlockPos(),
                buffer.readEnum(Action.class),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readUtf(128));
    }

    public static void handle(Packet_SpawnerConfigRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                SpawnerService.handleConfigRequest(player, packet.pos(), packet.action(),
                        packet.value(), packet.flag(), packet.text());
            }
        });
    }
}
