package com.hhy.dreamingfishcore.client.debug;

import com.hhy.dreamingfishcore.server.server_ui_system.client.serverscreen.ServerScreenUI;
import com.hhy.dreamingfishcore.server.server_ui_system.client.terminal.TerminalScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcDialogueViewData;
import com.hhy.dreamingfishcore.gameplay.npc_system.client.ui.screen.Screen_NpcDialogue;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.client.ui.screen.Screen_RevivalCharm;
import com.hhy.dreamingfishcore.gameplay.storybook_system.StoryBookEntryViewData;
import com.hhy.dreamingfishcore.gameplay.storybook_system.client.ui.screen.Screen_StoryBookCatalog;
import com.hhy.dreamingfishcore.gameplay.storybook_system.client.ui.screen.Screen_StoryFragment;
import com.hhy.dreamingfishcore.server.login_system.client.Screen_LoginUI;

import java.util.List;

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
        UiHarness.register("death", minecraft -> {
            var player = minecraft.player;
            com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.client.cache.DeathScreenDataStorage.setData(
                    62.5F, 12.5F, 35.0F, false, Component.literal("Dev 在灯塔下被感染者撕碎了"),
                    player != null ? player.getX() : 0, player != null ? player.getY() : 64, player != null ? player.getZ() : 0,
                    "minecraft:overworld", java.util.UUID.randomUUID());
            minecraft.setScreen(new DeathScreen(Component.literal("Dev 被僵尸杀死了"), false));
        });

        UiHarness.register("loading_message", minecraft -> minecraft.setScreen(
                new net.minecraft.client.gui.screens.GenericMessageScreen(Component.literal("正在保存世界"))));
        UiHarness.register("loading_waiting", minecraft -> minecraft.setScreen(
                net.minecraft.client.gui.screens.GenericWaitingScreen.createWaiting(Component.literal("正在连接 Realms"),
                        Component.literal("取消"), () -> minecraft.setScreen(null))));
        UiHarness.register("loading_progress", minecraft -> {
            var screen = new net.minecraft.client.gui.screens.ProgressScreen(false);
            minecraft.setScreen(screen);
            screen.progressStartNoAbort(Component.literal("正在准备出生点区域"));
            screen.progressStagePercentage(42);
        });
        UiHarness.register("reload", minecraft -> minecraft.reloadResourcePacks());
        UiHarness.register("disconnect", minecraft -> minecraft.setScreen(new net.minecraft.client.gui.screens.DisconnectedScreen(
                new net.minecraft.client.gui.screens.TitleScreen(), Component.literal("连接丢失"),
                Component.literal("Connection timed out: no further information"))));
        UiHarness.register("disconnect_death", minecraft -> minecraft.setScreen(new net.minecraft.client.gui.screens.DisconnectedScreen(
                new net.minecraft.client.gui.screens.TitleScreen(), Component.literal("连接丢失"),
                Component.literal("你的复活点数耗尽，正在等待一名幸存者。\n尸体位置：主世界 -297, 71, -458"))));
        UiHarness.register("disconnect_ban", minecraft -> minecraft.setScreen(new net.minecraft.client.gui.screens.DisconnectedScreen(
                new net.minecraft.client.gui.screens.TitleScreen(), Component.literal("连接丢失"),
                Component.literal("You are banned from this server.\nReason: 恶意破坏公共设施\nYour ban will be removed on 2026-10-07"))));
        UiHarness.register("login",minecraft -> minecraft.setScreen(new Screen_LoginUI(false)));
        UiHarness.register("register", minecraft -> minecraft.setScreen(new Screen_LoginUI(true)));
        UiHarness.register("revival", minecraft -> minecraft.setScreen(new Screen_RevivalCharm()));
        UiHarness.register("storybook", minecraft -> minecraft.setScreen(new Screen_StoryBookCatalog(sampleFragments())));
        UiHarness.register("storybook_chapter", minecraft -> {
            Screen_StoryBookCatalog screen = new Screen_StoryBookCatalog(sampleFragments());
            minecraft.setScreen(screen);
            screen.openChapter(1);
        });
        UiHarness.register("fragment", minecraft -> {
            StoryBookEntryViewData entry = sampleFragments().get(0);
            minecraft.setScreen(new Screen_StoryFragment(entry.getFragmentId(), entry.getStageId(), entry.getChapterId(),
                    entry.getTitle(), entry.getContent(), entry.getTime(), entry.getAuthorName()));
        });
        UiHarness.register("dialogue", minecraft -> minecraft.setScreen(new Screen_NpcDialogue(new NpcDialogueViewData(
                1, -1, "林医生", "海岸医院仅存的外科医生，负责模板重建的最后一道核验。", "女", "医生", 2,
                List.of("你醒了。别急着起身——模板刚完成重建，神经同步还需要一点时间。",
                        "外面的感染潮比上周更靠近海岸了。逐光会的人说他们找到了新的安全区，但我不太相信他们。",
                        "如果你要出去，记得带上足够的抑制剂。"),
                "她的目光在你手腕的读数上停留了片刻。", "minecraft:golden_apple", 42, "信任",
                List.of("DIALOGUE", "ABOUT", "FOLLOW", "HOSPITAL_REVIEW")))));

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

    private static List<StoryBookEntryViewData> sampleFragments() {
        String body = "潮水退去的第三天，我们在灯塔下找到了第一份模板备份。\n\n"
                + "它被封在铅盒里，编号已经模糊。医生说只要核心没有被感染，就能重建一个完整的人——"
                + "但她没说重建出来的，还是不是原来的那个人。\n\n"
                + "逐光会的人在夜里来过。他们没有带走任何东西，只在墙上留下了一道白色的弧线。";
        return List.of(
                new StoryBookEntryViewData(1, 1, 0, "灯塔下的铅盒", body, "第 3 日 · 黄昏", "守望者 07", true),
                new StoryBookEntryViewData(2, 1, 0, "白色弧线", body, "第 4 日 · 深夜", "未知", false),
                new StoryBookEntryViewData(3, 2, 1, "海岸医院", body, "第 9 日", "林医生", false),
                new StoryBookEntryViewData(4, 2, 1, "抑制剂配方残页", body, "第 11 日", "药剂师", true),
                new StoryBookEntryViewData(5, 3, 2, "逐光会的信", body, "第 20 日", "逐光会", false));
    }
}
