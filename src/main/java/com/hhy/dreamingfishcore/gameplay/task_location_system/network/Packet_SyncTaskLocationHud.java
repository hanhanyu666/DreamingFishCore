package com.hhy.dreamingfishcore.gameplay.task_location_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationDefinition;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Synchronizes the player's current authored location only when that state changes. */
public record Packet_SyncTaskLocationHud(
        boolean visible,
        String locationName,
        TaskLocationMode mode,
        BlockPos min,
        BlockPos max
) implements CustomPacketPayload {
    private static final int MAX_LOCATION_NAME_LENGTH = 128;

    public static final Type<Packet_SyncTaskLocationHud> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    DreamingFishCore.MODID, "task_location_system/sync_hud"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_SyncTaskLocationHud> STREAM_CODEC =
            StreamCodec.of(Packet_SyncTaskLocationHud::encode, Packet_SyncTaskLocationHud::decode);

    public Packet_SyncTaskLocationHud {
        locationName = visible && locationName != null ? locationName : "";
        if (locationName.length() > MAX_LOCATION_NAME_LENGTH) {
            locationName = locationName.substring(0, MAX_LOCATION_NAME_LENGTH);
        }
        mode = mode == null ? TaskLocationMode.PROTECTED : mode;
        min = visible && min != null ? min.immutable() : BlockPos.ZERO;
        max = visible && max != null ? max.immutable() : BlockPos.ZERO;
    }

    public static Packet_SyncTaskLocationHud show(TaskLocationDefinition location) {
        return new Packet_SyncTaskLocationHud(
                true, location.getName(), location.getMode(),
                location.getMin(), location.getMax());
    }

    public static Packet_SyncTaskLocationHud hide() {
        return new Packet_SyncTaskLocationHud(
                false, "", TaskLocationMode.PROTECTED, BlockPos.ZERO, BlockPos.ZERO);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, Packet_SyncTaskLocationHud packet) {
        buffer.writeBoolean(packet.visible);
        if (!packet.visible) {
            return;
        }
        buffer.writeUtf(packet.locationName, MAX_LOCATION_NAME_LENGTH);
        buffer.writeEnum(packet.mode);
        buffer.writeBlockPos(packet.min);
        buffer.writeBlockPos(packet.max);
    }

    private static Packet_SyncTaskLocationHud decode(RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return hide();
        }
        return new Packet_SyncTaskLocationHud(
                true,
                buffer.readUtf(MAX_LOCATION_NAME_LENGTH),
                buffer.readEnum(TaskLocationMode.class),
                buffer.readBlockPos(),
                buffer.readBlockPos());
    }

    public static void handle(Packet_SyncTaskLocationHud packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientHandler.handle(packet));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Kept behind a client-only holder so dedicated servers never load HUD classes. */
    @OnlyIn(Dist.CLIENT)
    private static final class ClientHandler {
        private ClientHandler() {
        }

        private static void handle(Packet_SyncTaskLocationHud packet) {
            if (packet.visible) {
                com.hhy.dreamingfishcore.gameplay.task_location_system.client.TaskLocationClientState.show(
                        packet.locationName, packet.mode, packet.min, packet.max);
            } else {
                com.hhy.dreamingfishcore.gameplay.task_location_system.client.TaskLocationClientState.clear();
            }
        }
    }
}
