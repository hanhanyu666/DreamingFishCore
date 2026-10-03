package com.hhy.dreamingfishcore.server.server_ui_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * 系统消息包的编解码 gametest。
 *
 * <p>为什么不在单测里做：进度图标是 {@link ItemStack}，编码要用带注册表的 {@link RegistryFriendlyByteBuf}，
 * 普通单测环境里没有注册表。</p>
 */
@GameTestHolder(DreamingFishCore.MODID)
@PrefixGameTestTemplate(false)
public class SystemMessagePacketGameTest {

    /** 带事件信息的消息（挑战、玩家、图标、进度名）编码再解码后原样还原。 */
    @GameTest(template = "empty")
    public static void eventFieldsSurviveTheWire(GameTestHelper helper) {
        UUID player = UUID.randomUUID();
        Packet_SystemMessage sent = new Packet_SystemMessage(Component.literal("Dev 完成了挑战[资深怪物猎人]"), 0xAA00AA,
                SystemMessageKind.CHALLENGE, player, "Dev", new ItemStack(Items.DIAMOND_SWORD),
                Component.literal("资深怪物猎人"));
        Packet_SystemMessage received = roundTrip(helper, sent);

        helper.assertValueEqual(received.message().getString(), "Dev 完成了挑战[资深怪物猎人]", "消息文字");
        helper.assertValueEqual(received.borderColor(), 0xAA00AA, "边框颜色");
        helper.assertValueEqual(received.kind(), SystemMessageKind.CHALLENGE, "事件类型");
        helper.assertValueEqual(received.playerId(), player, "玩家 UUID");
        helper.assertValueEqual(received.playerName(), "Dev", "玩家名字");
        helper.assertTrue(received.icon().is(Items.DIAMOND_SWORD), "进度图标应为钻石剑，实际 " + received.icon());
        helper.assertValueEqual(received.headline().getString(), "资深怪物猎人", "进度名称");
        helper.succeed();
    }

    /** 只有文字的旧式消息：没有事件类型，后面的字段不占线路，也不会被读成别的东西。 */
    @GameTest(template = "empty")
    public static void plainMessagesStayPlain(GameTestHelper helper) {
        Packet_SystemMessage received = roundTrip(helper,
                new Packet_SystemMessage(Component.literal("服务器将在 5 分钟后重启"), 0x55FFFF));

        helper.assertValueEqual(received.message().getString(), "服务器将在 5 分钟后重启", "消息文字");
        helper.assertValueEqual(received.borderColor(), 0x55FFFF, "边框颜色");
        helper.assertTrue(received.kind() == null, "旧式消息不应带事件类型");
        helper.assertTrue(received.icon().isEmpty(), "旧式消息不应带图标");
        helper.succeed();
    }

    private static Packet_SystemMessage roundTrip(GameTestHelper helper, Packet_SystemMessage packet) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess());
        try {
            Packet_SystemMessage.STREAM_CODEC.encode(buffer, packet);
            Packet_SystemMessage decoded = Packet_SystemMessage.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(buffer.readableBytes(), 0, "解码后不应有剩余字节");
            return decoded;
        } finally {
            buffer.release();
        }
    }
}
