package com.hhy.dreamingfishcore.gameplay.research_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端把研究桌的当前状态推给客户端（开屏 + 研究后的刷新都走这一个包）。
 *
 * <p>状态一律由服务端算好：{@code offer} 是**本次固定的课题**，玩家关掉界面再打开会拿到同一批，
 * 免得用反复开界面来刷随机结果；{@code available} 为 false 时界面禁用研究按钮
 * （蓝图系统没启用、或研究桌被关掉）。</p>
 *
 * @param pos              研究桌坐标（确认时回传，服务端据此复核距离与方块）
 * @param offer            本次可研究的物品清单
 * @param cost             一次研究消耗的经验点数
 * @param playerExperience 玩家当前持有的经验点数
 * @param learned          上一次研究实际学会的物品（用于界面反馈）
 * @param message          给玩家看的一句话（不足经验、研究完成等）
 * @param available        当前能不能研究
 */
public record Packet_ResearchTableOpen(BlockPos pos,
                                       List<String> offer,
                                       int cost,
                                       int playerExperience,
                                       List<String> learned,
                                       String message,
                                       boolean available) implements CustomPacketPayload {

    public static final Type<Packet_ResearchTableOpen> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    DreamingFishCore.MODID, "research_table/open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_ResearchTableOpen> STREAM_CODEC =
            StreamCodec.of(Packet_ResearchTableOpen::encode, Packet_ResearchTableOpen::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, Packet_ResearchTableOpen packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeCollection(packet.offer(), FriendlyByteBuf::writeUtf);
        buffer.writeVarInt(packet.cost());
        buffer.writeVarInt(packet.playerExperience());
        buffer.writeCollection(packet.learned(), FriendlyByteBuf::writeUtf);
        buffer.writeUtf(packet.message());
        buffer.writeBoolean(packet.available());
    }

    private static Packet_ResearchTableOpen decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        List<String> offer = buffer.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);
        int cost = buffer.readVarInt();
        int playerExperience = buffer.readVarInt();
        List<String> learned = buffer.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);
        String message = buffer.readUtf();
        boolean available = buffer.readBoolean();
        return new Packet_ResearchTableOpen(pos, offer, cost, playerExperience, learned, message, available);
    }

    public static void handle(Packet_ResearchTableOpen packet, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (net.neoforged.fml.loading.FMLLoader.getDist().isClient()) {
            context.enqueueWork(() -> openClient(packet));
        }
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void openClient(Packet_ResearchTableOpen packet) {
        // 先落缓存：界面每次渲染都从缓存读，所以研究完成后服务端再推一份就能自动刷新。
        com.hhy.dreamingfishcore.gameplay.research_system.client.ResearchTableClientCache.accept(packet);
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.screen instanceof com.hhy.dreamingfishcore.gameplay.research_system.client.Screen_ResearchTable) {
            // 界面已经开着就只刷新，不重建——重建会把输入焦点与滚动位置清掉。
            return;
        }
        minecraft.setScreen(new com.hhy.dreamingfishcore.gameplay.research_system.client.Screen_ResearchTable(
                packet.pos()));
    }
}
