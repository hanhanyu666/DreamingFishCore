package com.hhy.dreamingfishcore.gameplay.npc_system.network;

import com.hhy.dreamingfishcore.gameplay.npc_system.NpcDialogueViewData;
import com.hhy.dreamingfishcore.gameplay.npc_system.client.ui.screen.Screen_NpcDialogue;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public class Packet_OpenNpcDialogueGUI implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    /**
     * NPC 档案来自服主可编辑配置；在编码端和解码端同时限制可变列表的长度，
     * 避免一份损坏/恶意配置把一个客户端包扩成任意大的对象列表。
     */
    private static final int MAX_DIALOGUE_LINES = 128;
    private static final int MAX_ACTIONS = 128;

    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<Packet_OpenNpcDialogueGUI> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.hhy.dreamingfishcore.DreamingFishCore.MODID, "npc_system/packet_open_npc_dialogue_gui"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_OpenNpcDialogueGUI> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, packet) -> Packet_OpenNpcDialogueGUI.encode(packet, buf), Packet_OpenNpcDialogueGUI::decode);

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }
    private final NpcDialogueViewData viewData;

    public Packet_OpenNpcDialogueGUI(NpcDialogueViewData viewData) {
        this.viewData = viewData;
    }

    public static void encode(Packet_OpenNpcDialogueGUI packet, FriendlyByteBuf buf) {
        if (packet == null || packet.viewData == null) {
            throw new IllegalArgumentException("NPC 对话数据不能为空");
        }
        NpcDialogueViewData data = packet.viewData;
        requireCount(data.getDialogues().size(), MAX_DIALOGUE_LINES, "NPC 台词");
        requireCount(data.getAvailableActions().size(), MAX_ACTIONS, "NPC 动作");
        buf.writeVarInt(data.getNpcId());
        buf.writeVarInt(data.getEntityId());
        buf.writeUtf(data.getNpcName(), 256);
        buf.writeUtf(data.getNpcIntroduction(), Short.MAX_VALUE);
        buf.writeUtf(data.getNpcGender(), 128);
        buf.writeUtf(data.getNpcProfession(), 128);
        buf.writeVarInt(data.getStoryStageId());
        buf.writeVarInt(data.getDialogues().size());
        for (String dialogue : data.getDialogues()) {
            buf.writeUtf(dialogue, Short.MAX_VALUE);
        }
        buf.writeUtf(data.getThoughtText(), Short.MAX_VALUE);
        buf.writeUtf(data.getWantedItemId(), 256);
        buf.writeVarInt(data.getFavorability());
        buf.writeUtf(data.getRelationName(), 128);
        buf.writeVarInt(data.getAvailableActions().size());
        for (String action : data.getAvailableActions()) {
            buf.writeUtf(action, 128);
        }
    }

    public static Packet_OpenNpcDialogueGUI decode(FriendlyByteBuf buf) {
        int npcId = buf.readVarInt();
        int entityId = buf.readVarInt();
        String npcName = buf.readUtf(256);
        String npcIntroduction = buf.readUtf(Short.MAX_VALUE);
        String npcGender = buf.readUtf(128);
        String npcProfession = buf.readUtf(128);
        int storyStageId = buf.readVarInt();
        int dialogueSize = buf.readVarInt();
        requireCount(dialogueSize, MAX_DIALOGUE_LINES, "NPC 台词");
        List<String> dialogues = new ArrayList<>();
        for (int i = 0; i < dialogueSize; i++) {
            dialogues.add(buf.readUtf(Short.MAX_VALUE));
        }
        String thoughtText = buf.readUtf(Short.MAX_VALUE);
        String wantedItemId = buf.readUtf(256);
        int favorability = buf.readVarInt();
        String relationName = buf.readUtf(128);
        int actionSize = buf.readVarInt();
        requireCount(actionSize, MAX_ACTIONS, "NPC 动作");
        List<String> actions = new ArrayList<>();
        for (int i = 0; i < actionSize; i++) {
            actions.add(buf.readUtf(128));
        }

        return new Packet_OpenNpcDialogueGUI(new NpcDialogueViewData(
                npcId,
                entityId,
                npcName,
                npcIntroduction,
                npcGender,
                npcProfession,
                storyStageId,
                dialogues,
                thoughtText,
                wantedItemId,
                favorability,
                relationName,
                actions
        ));
    }

    public static void handle(Packet_OpenNpcDialogueGUI packet, IPayloadContext context) {
        if (FMLLoader.getDist().isClient()) {
            context.enqueueWork(() -> handleClient(packet));
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(Packet_OpenNpcDialogueGUI packet) {
        Minecraft.getInstance().setScreen(new Screen_NpcDialogue(packet.viewData));
    }

    private static void requireCount(int count, int maximum, String label) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(label + "数量非法：" + count);
        }
    }
}
