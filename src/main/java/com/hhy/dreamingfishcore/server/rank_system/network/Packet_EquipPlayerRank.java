package com.hhy.dreamingfishcore.server.rank_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.rank_system.PlayerRankManager;
import com.hhy.dreamingfishcore.server.rank_system.Rank;
import com.hhy.dreamingfishcore.server.rank_system.RankRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Requests a Rank loadout change. Ownership is always validated on the server.
 */
public final class Packet_EquipPlayerRank implements CustomPacketPayload {
    public static final Type<Packet_EquipPlayerRank> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "rank_system/packet_equip_player_rank"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_EquipPlayerRank> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> encode(packet, buffer), Packet_EquipPlayerRank::decode);

    private final String rankName;

    public Packet_EquipPlayerRank(String rankName) {
        this.rankName = rankName == null ? RankRegistry.NO_RANK.getRankName() : rankName;
    }

    public static void encode(Packet_EquipPlayerRank packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.rankName, 64);
    }

    public static Packet_EquipPlayerRank decode(FriendlyByteBuf buffer) {
        return new Packet_EquipPlayerRank(buffer.readUtf(64));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(Packet_EquipPlayerRank packet, IPayloadContext context) {
        context.enqueueWork(() -> handleServer(packet,
                context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null));
    }

    private static void handleServer(Packet_EquipPlayerRank packet, ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!RankRegistry.isRegistered(packet.rankName)) {
            DreamingFishCore.LOGGER.warn("玩家 {} 请求装配未注册的 Rank: {}",
                    player.getScoreboardName(), packet.rankName);
            player.sendSystemMessage(Component.literal("§cRank 装配失败：该 Rank 不存在"));
            return;
        }

        Rank targetRank = RankRegistry.getRankByName(packet.rankName);
        if (!PlayerRankManager.equipPlayerRankServer(player, targetRank)) {
            DreamingFishCore.LOGGER.warn("玩家 {} 请求装配未拥有的 Rank: {}",
                    player.getScoreboardName(), targetRank.getRankName());
            player.sendSystemMessage(Component.literal("§cRank 装配失败：你尚未拥有该 Rank"));
            return;
        }

        String message = targetRank == RankRegistry.NO_RANK
                ? "§7已卸下当前 Rank"
                : "§a已装配 Rank：§r" + targetRank.getRankName();
        player.sendSystemMessage(Component.literal(message));
    }
}
