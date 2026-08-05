package com.hhy.dreamingfishcore.gameplay.marker_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.marker_system.MarkerManager;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class Packet_RequestMarker implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<Packet_RequestMarker> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    DreamingFishCore.MODID, "marker_system/packet_request_marker"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_RequestMarker> STREAM_CODEC =
            StreamCodec.of(Packet_RequestMarker::encode, Packet_RequestMarker::decode);

    private final double x;
    private final double y;
    private final double z;

    public Packet_RequestMarker(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(FriendlyByteBuf buf, Packet_RequestMarker packet) {
        buf.writeDouble(packet.x);
        buf.writeDouble(packet.y);
        buf.writeDouble(packet.z);
    }

    public static Packet_RequestMarker decode(FriendlyByteBuf buf) {
        return new Packet_RequestMarker(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(Packet_RequestMarker packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) {
                return;
            }

            Vec3 markerPosition = new Vec3(packet.x, packet.y, packet.z);
            if (!isMarkerValid(sender, markerPosition)) {
                DreamingFishCore_NetworkManager.sendToClient(new Packet_MarkerRejected("标点失败：目标无效或区块未加载"), sender);
                return;
            }

            long createdAtMs = System.currentTimeMillis();
            Packet_ShowMarker markerPacket = new Packet_ShowMarker(
                    sender.getUUID(),
                    sender.getGameProfile().getName(),
                    markerPosition.x,
                    markerPosition.y,
                    markerPosition.z,
                    createdAtMs
            );
            Component chatNotice = Component.literal(sender.getGameProfile().getName() + "玩家标记了一处地点");

            for (ServerPlayer viewer : sender.getServer().getPlayerList().getPlayers()) {
                if (viewer.level() == sender.level()
                        && viewer.distanceToSqr(sender) <= MarkerManager.NEARBY_PLAYER_RANGE_SQR) {
                    viewer.sendSystemMessage(chatNotice);
                    DreamingFishCore_NetworkManager.sendToClient(markerPacket, viewer);
                }
            }
        });
    }

    private static boolean isMarkerValid(ServerPlayer sender, Vec3 markerPosition) {
        if (!Double.isFinite(markerPosition.x) || !Double.isFinite(markerPosition.y) || !Double.isFinite(markerPosition.z)) {
            return false;
        }

        if (!(sender.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        BlockPos pos = BlockPos.containing(markerPosition);
        if (serverLevel.isOutsideBuildHeight(pos)) {
            return false;
        }

        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        return serverLevel.getChunkSource().hasChunk(chunkX, chunkZ);
    }
}
