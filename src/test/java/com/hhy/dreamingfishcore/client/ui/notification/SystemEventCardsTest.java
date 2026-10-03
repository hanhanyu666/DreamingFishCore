package com.hhy.dreamingfishcore.client.ui.notification;

import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 右上角事件卡对服务端消息文字的拆解：Rank 标签、“[+]/[-]”前缀与正文。 */
class SystemEventCardsTest {

    @Test
    void rankTagIsLiftedOutOfJoinMessages() {
        SystemEventCards.Parsed parsed = SystemEventCards.parse(
                Component.literal("§c[§cOPERATOR§c]§b鱼友§6Dev§b来和你VAN辣！"));

        assertEquals("OPERATOR", parsed.rank());
        assertEquals(0xFFFF5555, parsed.rankColor());
        assertEquals("鱼友Dev来和你VAN辣！", parsed.body().getString());
    }

    @Test
    void plusAndMinusPrefixesAreDropped() {
        assertEquals("鱼友听海来和你VAN辣！",
                SystemEventCards.parse(Component.literal("§7[§a+§7]§b鱼友§e听海§b来和你VAN辣！")).body().getString());
        assertEquals("", SystemEventCards.parse(Component.literal("§7[§a+§7]§b鱼友§e听海§b来和你VAN辣！")).rank());
        assertEquals("鱼友林潮不想和你VAN辣！",
                SystemEventCards.parse(Component.literal("§7[§c-§7]§b鱼友§e林潮§b不想和你VAN辣！")).body().getString());
    }

    @Test
    void rankTagWorksAcrossComponentChildren() {
        Component message = Component.literal("[FISH+] ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("Lighthouse"))
                .append(Component.literal(" 完成了进度"))
                .append(Component.literal("[石器时代]").withStyle(ChatFormatting.GREEN));
        SystemEventCards.Parsed parsed = SystemEventCards.parse(message);

        assertEquals("FISH+", parsed.rank());
        assertEquals("Lighthouse 完成了进度[石器时代]", parsed.body().getString());
    }

    @Test
    void messagesWithoutTagsAreKeptWhole() {
        SystemEventCards.Parsed parsed = SystemEventCards.parse(Component.literal("白芷 被僵尸杀死了"));

        assertEquals("", parsed.rank());
        assertEquals("白芷 被僵尸杀死了", parsed.body().getString());
    }

    @Test
    void darkConfiguredColorsAreLiftedToReadable() {
        int toned = SystemEventCards.toneColor(0xFFAA00AA);
        float luminance = (0.299F * UiColor.red(toned) + 0.587F * UiColor.green(toned) + 0.114F * UiColor.blue(toned)) / 255.0F;

        assertTrue(luminance > 0.5F, "深紫色调和后应足够亮，实际亮度 " + luminance);
    }
}
