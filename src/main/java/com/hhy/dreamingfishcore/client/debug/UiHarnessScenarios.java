package com.hhy.dreamingfishcore.client.debug;

import com.hhy.dreamingfishcore.server.server_ui_system.client.serverscreen.ServerScreenUI;
import com.hhy.dreamingfishcore.server.server_ui_system.client.terminal.TerminalScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

/** {@link UiHarness} 可用的截图场景。 */
final class UiHarnessScenarios {
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

        for (TerminalScreen.Tab tab : TerminalScreen.Tab.values()) {
            String name = tab.name().toLowerCase(java.util.Locale.ROOT);
            UiHarness.register("terminal:" + name, minecraft -> {
                ServerScreenUI.setShowUI(true);
                minecraft.setScreen(new TerminalScreen(false, tab));
            });
        }
        UiHarness.register("terminal", minecraft -> {
            ServerScreenUI.setShowUI(true);
            minecraft.setScreen(new TerminalScreen());
        });
        for (String route : new String[]{"help", "history", "market", "rank", "notice", "task"}) {
            UiHarness.register("terminal/" + route, minecraft -> {
                ServerScreenUI.setShowUI(true);
                TerminalScreen screen = new TerminalScreen(true, TerminalScreen.Tab.HOME);
                minecraft.setScreen(screen);
                screen.openRoute(route);
            });
        }
    }
}
