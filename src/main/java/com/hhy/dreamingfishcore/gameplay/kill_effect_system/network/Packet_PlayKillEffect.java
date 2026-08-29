package com.hhy.dreamingfishcore.gameplay.kill_effect_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.kill_effect_system.client.KillEffectClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;
import java.util.UUID;

/** Client-bound snapshot describing one kill effect instance. */
public record Packet_PlayKillEffect(
        int entityId,
        UUID uuid,
        double x,
        double y,
        double z,
        float width,
        float height,
        long seed,
        int durationTicks) implements CustomPacketPayload {

    private static final float MIN_DIMENSION = 0.05F;
    private static final float MAX_WIDTH = 16.0F;
    private static final float MAX_HEIGHT = 32.0F;
    private static final int MIN_DURATION_TICKS = 1;
    private static final int MAX_DURATION_TICKS = 40;

    public static final Type<Packet_PlayKillEffect> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    DreamingFishCore.MODID, "kill_effect_system/packet_play_kill_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_PlayKillEffect> STREAM_CODEC =
            StreamCodec.of(Packet_PlayKillEffect::encode, Packet_PlayKillEffect::decode);

    public Packet_PlayKillEffect {
        uuid = Objects.requireNonNull(uuid, "uuid");
        x = requireFinite(x, "x");
        y = requireFinite(y, "y");
        z = requireFinite(z, "z");
        width = clampDimension(width, "width", MAX_WIDTH);
        height = clampDimension(height, "height", MAX_HEIGHT);
        durationTicks = Math.max(MIN_DURATION_TICKS, Math.min(MAX_DURATION_TICKS, durationTicks));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, Packet_PlayKillEffect packet) {
        buffer.writeVarInt(packet.entityId);
        buffer.writeUUID(packet.uuid);
        buffer.writeDouble(packet.x);
        buffer.writeDouble(packet.y);
        buffer.writeDouble(packet.z);
        buffer.writeFloat(packet.width);
        buffer.writeFloat(packet.height);
        buffer.writeLong(packet.seed);
        buffer.writeVarInt(packet.durationTicks);
    }

    private static Packet_PlayKillEffect decode(FriendlyByteBuf buffer) {
        return new Packet_PlayKillEffect(
                buffer.readVarInt(),
                buffer.readUUID(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readLong(),
                buffer.readVarInt());
    }

    public static void handle(Packet_PlayKillEffect packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientHandler.start(packet));
    }

    private static double requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
        return value;
    }

    private static float clampDimension(float value, String field, float maximum) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
        return Math.max(MIN_DIMENSION, Math.min(maximum, value));
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientHandler {
        private static void start(Packet_PlayKillEffect packet) {
            KillEffectClientState.start(
                    packet.entityId(),
                    packet.uuid(),
                    packet.x(),
                    packet.y(),
                    packet.z(),
                    packet.width(),
                    packet.height(),
                    packet.seed(),
                    packet.durationTicks());
        }
    }
}
