package com.hhy.dreamingfishcore.gameplay.raid_system.loot.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.raid_system.loot.LooseLootService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C：把本局露天物品节点同步给客户端（用于在世界里显示漂浮的物品）。
 *
 * <p>为什么这条包值得改协议：节点显示必须"客户端知道它在哪、是什么、是否已被拿走"，
 * 而拾取仍然**完全由服务端权威判定**（`LooseLootService` 的自动拾取，
 * 判定规则复用 {@code LooseLootPlanner.canPickup}）。客户端只负责画，不参与判定。</p>
 *
 * <p>整份列表每次全量下发：露天物品点通常只有几十个，全量比增量简单可靠得多，
 * 而且天然解决"客户端漏了一条增量就永远显示错"的问题。</p>
 */
public record Packet_RaidLootSync(List<Entry> nodes) implements CustomPacketPayload {

    /** 一个节点的显示数据（不含任何判定信息，判定在服务端）。 */
    public record Entry(String anchorId, String itemId, double x, double y, double z, float yaw,
                        boolean picked) {
    }

    public static final Type<Packet_RaidLootSync> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "raid/loose_loot_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_RaidLootSync> STREAM_CODEC =
            StreamCodec.of(Packet_RaidLootSync::encode, Packet_RaidLootSync::decode);

    public Packet_RaidLootSync {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, Packet_RaidLootSync packet) {
        buffer.writeVarInt(packet.nodes().size());
        for (Entry entry : packet.nodes()) {
            buffer.writeUtf(entry.anchorId());
            buffer.writeUtf(entry.itemId());
            buffer.writeDouble(entry.x());
            buffer.writeDouble(entry.y());
            buffer.writeDouble(entry.z());
            buffer.writeFloat(entry.yaw());
            buffer.writeBoolean(entry.picked());
        }
    }

    private static Packet_RaidLootSync decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<Entry> nodes = new ArrayList<>(Math.max(0, count));
        for (int index = 0; index < count; index++) {
            nodes.add(new Entry(buffer.readUtf(), buffer.readUtf(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readBoolean()));
        }
        return new Packet_RaidLootSync(nodes);
    }

    /** 客户端：塞进缓存，交给渲染器画。 */
    public static void handle(Packet_RaidLootSync packet, IPayloadContext context) {
        context.enqueueWork(() -> com.hhy.dreamingfishcore.gameplay.raid_system.loot.client
                .RaidLootClientCache.accept(packet.nodes()));
    }

    /** 服务端：把当前节点同步给某个玩家。 */
    public static void sendTo(ServerPlayer player) {
        List<Entry> entries = new ArrayList<>();
        LooseLootService.nodes().forEach(node -> entries.add(new Entry(node.anchorId(), node.itemId(),
                node.x(), node.y(), node.z(), node.yaw(), node.picked())));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new Packet_RaidLootSync(entries));
    }

    /** 服务端：同步给所有人（生成完、拾取后调用）。 */
    public static void sendToAll(net.minecraft.server.MinecraftServer server) {
        if (server == null) {
            return;
        }
        server.getPlayerList().getPlayers().forEach(Packet_RaidLootSync::sendTo);
    }
}
