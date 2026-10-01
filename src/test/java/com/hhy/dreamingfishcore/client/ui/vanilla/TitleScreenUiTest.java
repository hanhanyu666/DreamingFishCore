package com.hhy.dreamingfishcore.client.ui.vanilla;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitleScreenUiTest {
    @Test
    void literalAndEmptyMessagesRemainAuxiliary() {
        assertFalse(TitleScreenUi.isPrimaryMessage(Component.literal("模组工具")));
        assertFalse(TitleScreenUi.isPrimaryMessage(Component.literal("menu.singleplayer")));
        assertFalse(TitleScreenUi.isPrimaryMessage(Component.empty()));
        assertFalse(TitleScreenUi.isPrimaryMessage(Component.empty().append(Component.literal("工具"))));
    }

    @Test
    void primaryTranslatedMessagesAreRecognized() {
        for (String key : new String[]{"menu.singleplayer", "menu.multiplayer", "menu.online", "menu.options", "menu.quit"}) {
            assertTrue(TitleScreenUi.isPrimaryMessage(Component.translatable(key)), key);
        }
    }

    @Test
    void otherTranslatedMessagesRemainAuxiliary() {
        for (String key : new String[]{"narrator.button.language", "narrator.button.accessibility", "fml.menu.mods", "mod.custom.button"}) {
            assertFalse(TitleScreenUi.isPrimaryMessage(Component.translatable(key)), key);
        }
    }
}
