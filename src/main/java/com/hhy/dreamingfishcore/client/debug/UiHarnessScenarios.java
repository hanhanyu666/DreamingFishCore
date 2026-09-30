package com.hhy.dreamingfishcore.client.debug;

import com.hhy.dreamingfishcore.server.server_ui_system.client.serverscreen.ServerScreenUI;
import com.hhy.dreamingfishcore.server.server_ui_system.client.serverscreen.ServerScreenUI_Screen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

/** {@link UiHarness} 可用的截图场景。 */
final class UiHarnessScenarios {
    private static final String[] TERMINAL_PAGES = {
            "profile", "help", "notice", "story", "ranking", "achievement",
            "shop", "territory", "history", "settings", "messages"
    };

    private UiHarnessScenarios() {
    }

    static void registerAll() {
        UiHarness.register("hud", minecraft -> minecraft.setScreen(null));
        UiHarness.register("canvas_test", minecraft -> minecraft.setScreen(new UiCanvasTestScreen()));
        UiHarness.register("gallery", minecraft -> minecraft.setScreen(new UiGalleryScreen()));
        UiHarness.register("gallery_dialog", minecraft -> {
            UiGalleryScreen screen = new UiGalleryScreen();
            minecraft.setScreen(screen);
            screen.openDialogForTest();
        });
        UiHarness.register("title", minecraft -> minecraft.setScreen(new TitleScreen()));
        UiHarness.register("pause", minecraft -> minecraft.setScreen(new PauseScreen(true)));
        UiHarness.register("chat", minecraft -> minecraft.setScreen(new ChatScreen("")));
        UiHarness.register("death", minecraft -> minecraft.setScreen(
                new DeathScreen(Component.literal("Dev 被僵尸杀死了"), false)));

        UiHarness.register("terminal", minecraft -> {
            ServerScreenUI.setShowUI(true);
            minecraft.setScreen(new ServerScreenUI_Screen());
        });
        for (int i = 0; i < TERMINAL_PAGES.length; i++) {
            int page = i;
            UiHarness.register("terminal:" + TERMINAL_PAGES[i], minecraft -> {
                ServerScreenUI.setShowUI(true);
                ServerScreenUI_Screen screen = new ServerScreenUI_Screen();
                minecraft.setScreen(screen);
                screen.setSelectedPageIndex(page);
            });
        }
    }
}
