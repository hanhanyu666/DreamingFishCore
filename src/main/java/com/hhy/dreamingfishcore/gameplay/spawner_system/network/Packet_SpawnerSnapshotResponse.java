package com.hhy.dreamingfishcore.gameplay.spawner_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.spawner_system.SpawnerView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端下发的刷怪箱快照。
 *
 * <p>字段是手写编解码的，顺序写错在编译期看不出来，所以配了一条编解码往返的 gametest 把它钉住。</p>
 */
public record Packet_SpawnerSnapshotResponse(SpawnerView view) implements CustomPacketPayload {

    private static final int MAX_REWARD_ITEMS = 64;

    public static final Type<Packet_SpawnerSnapshotResponse> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    DreamingFishCore.MODID, "spawner/snapshot_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_SpawnerSnapshotResponse>
            STREAM_CODEC = StreamCodec.of(
                    Packet_SpawnerSnapshotResponse::encode,
                    Packet_SpawnerSnapshotResponse::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer,
                               Packet_SpawnerSnapshotResponse packet) {
        SpawnerView view = packet.view();
        buffer.writeUtf(view.dimensionId(), 128);
        buffer.writeVarInt(view.x());
        buffer.writeVarInt(view.y());
        buffer.writeVarInt(view.z());
        buffer.writeUtf(view.entityId(), 128);
        buffer.writeVarInt(Math.max(0, view.detectionRadius()));
        buffer.writeVarInt(Math.max(0, view.spawnRadius()));
        buffer.writeVarInt(Math.max(0, view.spawnCount()));
        buffer.writeVarInt(Math.max(0, view.cooldownTicks()));
        buffer.writeVarInt(Math.max(0, view.batches()));
        buffer.writeVarInt(Math.max(0, view.batchesSpawned()));
        buffer.writeVarInt(Math.max(0, view.aliveCount()));
        buffer.writeBoolean(view.redstoneControlled());
        buffer.writeBoolean(view.selfDestructWhenCleared());
        buffer.writeBoolean(view.fixedClueEnabled());
        buffer.writeVarInt(Math.max(0, view.clueId()));
        buffer.writeVarInt(view.rewardExperience());
        buffer.writeVarInt(view.rewardCoins());
        buffer.writeVarInt(view.rewardItems().size());
        for (SpawnerView.RewardLine line : view.rewardItems()) {
            buffer.writeUtf(line.itemId(), 128);
            buffer.writeVarInt(Math.max(1, line.count()));
        }
        buffer.writeBoolean(view.canEdit());
        buffer.writeBoolean(view.inHordeArea());
        buffer.writeBoolean(view.active());
        buffer.writeUtf(view.areaHint(), 256);
    }

    private static Packet_SpawnerSnapshotResponse decode(RegistryFriendlyByteBuf buffer) {
        String dimensionId = buffer.readUtf(128);
        int x = buffer.readVarInt();
        int y = buffer.readVarInt();
        int z = buffer.readVarInt();
        String entityId = buffer.readUtf(128);
        int detectionRadius = buffer.readVarInt();
        int spawnRadius = buffer.readVarInt();
        int spawnCount = buffer.readVarInt();
        int cooldownTicks = buffer.readVarInt();
        int batches = buffer.readVarInt();
        int batchesSpawned = buffer.readVarInt();
        int aliveCount = buffer.readVarInt();
        boolean redstoneControlled = buffer.readBoolean();
        boolean selfDestruct = buffer.readBoolean();
        boolean fixedClue = buffer.readBoolean();
        int clueId = buffer.readVarInt();
        int rewardExperience = buffer.readVarInt();
        int rewardCoins = buffer.readVarInt();
        int rewardCount = Math.min(buffer.readVarInt(), MAX_REWARD_ITEMS);
        List<SpawnerView.RewardLine> rewards = new ArrayList<>(rewardCount);
        for (int index = 0; index < rewardCount; index++) {
            rewards.add(new SpawnerView.RewardLine(buffer.readUtf(128), buffer.readVarInt()));
        }
        boolean canEdit = buffer.readBoolean();
        boolean inHordeArea = buffer.readBoolean();
        boolean active = buffer.readBoolean();
        String areaHint = buffer.readUtf(256);

        return new Packet_SpawnerSnapshotResponse(new SpawnerView(
                dimensionId, x, y, z, entityId,
                detectionRadius, spawnRadius, spawnCount, cooldownTicks,
                batches, batchesSpawned, aliveCount,
                redstoneControlled, selfDestruct, fixedClue, clueId,
                rewardExperience, rewardCoins, List.copyOf(rewards),
                canEdit, inHordeArea, active, areaHint));
    }

    public static void handle(Packet_SpawnerSnapshotResponse packet, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(packet));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(Packet_SpawnerSnapshotResponse packet) {
        com.hhy.dreamingfishcore.gameplay.spawner_system.client.SpawnerConfigClientCache
                .set(packet.view());
    }
}
