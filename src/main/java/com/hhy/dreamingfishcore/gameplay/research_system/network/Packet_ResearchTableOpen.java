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
 * 服务端把研究桌的当前状态推给客户端（开屏后的刷新、研究 / 提交之后的反馈都走这一个包）。
 *
 * <p>状态一律由服务端算好：{@code offer} 是**本次固定的课题**，玩家关掉界面再打开会拿到同一批，
 * 免得用反复开界面来刷随机结果；{@code available} 为 false 时界面禁用"开始研究"按钮
 * （蓝图系统没启用、或研究桌被关掉）。</p>
 *
 * <p>提交物品那半边同理：{@code canSubmit} 与 {@code submitStatus} 是服务端读自己那份菜单槽位
 * 算出来的结论与原因，界面只负责显示。{@code submitDivisor} 也一并下发，这样界面能立刻把
 * "需要 16 个铁锭"算出来，不必等服务端再跑一轮往返；真正的判定仍然在服务端。</p>
 *
 * @param pos              研究桌坐标（确认 / 提交时回传，服务端据此复核距离与方块）
 * @param offer            本次可研究的物品清单
 * @param cost             一次研究消耗的经验点数
 * @param playerExperience 玩家当前持有的经验点数
 * @param learned          上一次研究实际学会的物品（用于界面反馈）
 * @param message          给玩家看的一句话（不足经验、研究完成等）
 * @param available        当前能不能花经验研究
 * @param submitDivisor    "提交四分之一组"里的除数（配置值，已夹取）
 * @param canSubmit        当前槽位里的物品能不能提交
 * @param submitStatus     不能提交时的原因；可以提交时是一句确认
 */
public record Packet_ResearchTableOpen(BlockPos pos,
                                       List<String> offer,
                                       int cost,
                                       int playerExperience,
                                       List<String> learned,
                                       String message,
                                       boolean available,
                                       int submitDivisor,
                                       boolean canSubmit,
                                       String submitStatus) implements CustomPacketPayload {

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
        buffer.writeVarInt(packet.submitDivisor());
        buffer.writeBoolean(packet.canSubmit());
        buffer.writeUtf(packet.submitStatus());
    }

    private static Packet_ResearchTableOpen decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        List<String> offer = buffer.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);
        int cost = buffer.readVarInt();
        int playerExperience = buffer.readVarInt();
        List<String> learned = buffer.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);
        String message = buffer.readUtf();
        boolean available = buffer.readBoolean();
        int submitDivisor = buffer.readVarInt();
        boolean canSubmit = buffer.readBoolean();
        String submitStatus = buffer.readUtf();
        return new Packet_ResearchTableOpen(pos, offer, cost, playerExperience, learned, message, available,
                submitDivisor, canSubmit, submitStatus);
    }

    public static void handle(Packet_ResearchTableOpen packet, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (net.neoforged.fml.loading.FMLLoader.getDist().isClient()) {
            context.enqueueWork(() -> acceptClient(packet));
        }
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void acceptClient(Packet_ResearchTableOpen packet) {
        // 界面每次渲染都从缓存读，所以服务端在研究 / 提交完成后再推一份就能自动刷新。
        //
        // 这里**不再自己开屏**：研究桌现在是一个真正的容器菜单，界面由服务端的 openMenu
        // 通过 MenuScreens 的注册项建出来（只有带菜单的界面才能操作槽位）。
        // 之前两条路都在开屏，会互相顶掉对方的界面实例，槽位内容也会跟着丢。
        com.hhy.dreamingfishcore.gameplay.research_system.client.ResearchTableClientCache.accept(packet);
    }
}
