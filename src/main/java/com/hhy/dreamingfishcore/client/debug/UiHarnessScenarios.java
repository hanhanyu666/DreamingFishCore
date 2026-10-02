package com.hhy.dreamingfishcore.client.debug;

import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationViewData;
import com.hhy.dreamingfishcore.gameplay.organization_system.client.cache.OrganizationClientCache;
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
        UiHarness.register("hud_busy", minecraft -> {
            minecraft.setScreen(null);
            var notifications = com.hhy.dreamingfishcore.client.ui.notification.NotificationManager.class;
            com.hhy.dreamingfishcore.client.ui.notification.NotificationManager.show(
                    com.hhy.dreamingfishcore.client.ui.notification.Notification.builder()
                            .title(Component.literal("【逐光会】丧尸开始恢复破坏能力"))
                            .message(Component.literal("按 U 打开终端，在“梦屿广播”中查看详情"))
                            .position(com.hhy.dreamingfishcore.client.ui.notification.NotificationPosition.TOP_LEFT)
                            .theme(com.hhy.dreamingfishcore.client.ui.notification.NotificationTheme.GOLD)
                            .durationMs(20_000L).build());
            com.hhy.dreamingfishcore.client.ui.notification.NotificationManager.show(
                    com.hhy.dreamingfishcore.client.ui.notification.Notification.builder()
                            .title(Component.literal("第二阶段 · 灯塔熄灭"))
                            .message(Component.literal("海岸医院请求所有幸存者协助转运伤员"))
                            .position(com.hhy.dreamingfishcore.client.ui.notification.NotificationPosition.CENTER_TOP)
                            .theme(com.hhy.dreamingfishcore.client.ui.notification.NotificationTheme.GOLD)
                            .durationMs(20_000L).build());
            com.hhy.dreamingfishcore.server.server_ui_system.client.SystemMessageDisplay.addMessage(
                    Component.literal("§c[OPERATOR]§6鱼友§bDev§f来和你VAN辣！"));
            var server = minecraft.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> {
                    var source = server.createCommandSourceStack().withSuppressedOutput();
                    server.getCommands().performPrefixedCommand(source, "effect give @a minecraft:speed 120 1");
                    server.getCommands().performPrefixedCommand(source, "effect give @a minecraft:night_vision 30");
                    server.getCommands().performPrefixedCommand(source, "effect give @a minecraft:regeneration 8");
                });
            }
            notifications.getName();
        });
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
        UiHarness.register("markers", minecraft -> {
            minecraft.setScreen(null);
            if (minecraft.player == null) {
                return;
            }
            var look = minecraft.player.getLookAngle();
            var eye = minecraft.player.getEyePosition();
            long now = net.minecraft.Util.getMillis();
            // 一个在视野内，一个在身后（显示为边缘箭头）
            com.hhy.dreamingfishcore.gameplay.marker_system.MarkerManager.addOrReplace(java.util.UUID.randomUUID(),
                    "Lighthouse", eye.add(look.scale(12.0)).add(2.0, 0.0, 0.0), now);
            com.hhy.dreamingfishcore.gameplay.marker_system.MarkerManager.addOrReplace(java.util.UUID.randomUUID(),
                    "守望者07", eye.subtract(look.scale(30.0)), now);
        });
        UiHarness.register("chat_busy", minecraft -> {
            minecraft.setScreen(null);
            var chat = com.hhy.dreamingfishcore.client.ui.chat.ImmersiveChatManager.class;
            java.util.UUID self = minecraft.player != null ? minecraft.player.getUUID() : java.util.UUID.randomUUID();
            long now = System.currentTimeMillis();
            minecraft.gui.getChat().addMessage(Component.literal("§7[系统] 海岸医院的发电机重新启动了"));
            com.hhy.dreamingfishcore.client.ui.chat.ImmersiveChatManager.receivePlayerMessage(self, "OPERATOR", 0xFF5555,
                    "萌新鱼友", 0x9FD46C, "Dev", "有人看到灯塔那边的白色弧线了吗？", now);
            com.hhy.dreamingfishcore.client.ui.chat.ImmersiveChatManager.receivePlayerMessage(java.util.UUID.randomUUID(),
                    "FISH+", 0x55FFFF, "逐光会", 0xFFC857, "Lighthouse", "看到了，@Dev 我们在医院门口集合，带上抑制剂。", now,
                    "Dev", "有人看到灯塔那边的白色弧线了吗？");
            chat.getName();
        });
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
        UiHarness.register("worlds", minecraft -> minecraft.setScreen(
                new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(new TitleScreen())));
        // 先退出存档再打开世界列表（存档打开时自身被锁，列表读不出来）；只能放在步骤最后
        UiHarness.register("worlds_offline", minecraft -> {
            if (minecraft.level != null) {
                minecraft.level.disconnect();
            }
            minecraft.disconnect();
            minecraft.setScreen(new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(new TitleScreen()));
        });
        UiHarness.register("servers", minecraft -> {
            minecraft.options.skipMultiplayerWarning = true;
            var servers = new net.minecraft.client.multiplayer.ServerList(minecraft);
            servers.load();
            if (servers.size() == 0) {
                servers.add(new net.minecraft.client.multiplayer.ServerData("梦屿 · 守望主服", "127.0.0.1:25565",
                        net.minecraft.client.multiplayer.ServerData.Type.OTHER), false);
                servers.add(new net.minecraft.client.multiplayer.ServerData("逐光会 · 测试服", "test.invalid",
                        net.minecraft.client.multiplayer.ServerData.Type.OTHER), false);
                servers.save();
            }
            minecraft.setScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(new TitleScreen()));
        });
        UiHarness.register("reload",minecraft -> minecraft.reloadResourcePacks());
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
            minecraft.setScreen(new Screen_StoryFragment(entry.getClueId(), entry.getLegacyId(),
                    entry.getStageId(), entry.getChapterId(), entry.getTitle(), entry.getContent(),
                    entry.getTime(), entry.getAuthorName(), entry.getSource(),
                    entry.getObservationSpan(), entry.getSample(), entry.getConditions()));
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
        // 组织页：用样例快照覆盖服务端同步回来的空名录
        UiHarness.register("organization_member", minecraft -> openOrganization(minecraft, true));
        UiHarness.register("organization_outsider", minecraft -> openOrganization(minecraft, false));
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

    private static void openOrganization(net.minecraft.client.Minecraft minecraft, boolean member) {
        ServerScreenUI.setShowUI(true);
        String self = minecraft.player == null ? "" : minecraft.player.getUUID().toString();
        var snapshot = sampleOrganizations(self, member);
        OrganizationClientCache.set(snapshot);
        minecraft.setScreen(new TerminalScreen(true, TerminalScreen.Tab.ORGANIZATION));
        UiHarness.whileStep(() -> {
            var current = OrganizationClientCache.get();
            if (current != snapshot) {
                OrganizationClientCache.set(snapshot);
            }
        });
    }

    private static OrganizationViewData.Snapshot sampleOrganizations(String self, boolean member) {
        List<OrganizationViewData.MemberLine> members = List.of(
                new OrganizationViewData.MemberLine(self, "Dev", "LEADER", "会长", true),
                new OrganizationViewData.MemberLine("a1", "林潮", "ADMIN", "管理员", true),
                new OrganizationViewData.MemberLine("a2", "白芷", "OFFICER", "干部", false),
                new OrganizationViewData.MemberLine("a3", "周岑", "MEMBER", "成员", true),
                new OrganizationViewData.MemberLine("a4", "听海", "MEMBER", "成员", false));
        OrganizationViewData.Detail detail = new OrganizationViewData.Detail(
                "lighthouse", "灯塔守望会", "每晚八点在灯塔下集合巡夜。\n过滤装置的维护费由资金池统一支付，请大家量力捐款。",
                "LEADER", "会长", true, true, true, true, members,
                List.of(new OrganizationViewData.MemberLine("b1", "拾荒的阿洛", "MEMBER", "成员", true)),
                List.of(new OrganizationViewData.MemberLine("b2", "雾港旅人", "MEMBER", "成员", false)),
                System.currentTimeMillis(), 1280, 10000, true, true, true, 4, 2,
                List.of(new OrganizationViewData.TerritoryLine("t1", "灯塔聚居地", "minecraft:overworld",
                                -320, 410, -256, 470, 3840, false, true),
                        new OrganizationViewData.TerritoryLine("t2", "", "minecraft:overworld", 0, 0, 0, 0, 0, true, false)),
                List.of(new OrganizationViewData.TerritoryLine("t3", "海岸菜园", "minecraft:overworld",
                        -180, 520, -150, 548, 840, false, false)),
                List.of(new OrganizationViewData.DeviceLine("minecraft:overworld", -290, 72, 436, true),
                        new OrganizationViewData.DeviceLine("minecraft:overworld", -270, 70, 455, false)));
        List<OrganizationViewData.Summary> organizations = List.of(
                new OrganizationViewData.Summary("lighthouse", "灯塔守望会", 5, member ? "Dev" : "林潮",
                        member ? OrganizationViewData.Relation.MEMBER : OrganizationViewData.Relation.INVITED),
                new OrganizationViewData.Summary("dawn", "逐光后援队", 12, "江晚", OrganizationViewData.Relation.NONE),
                new OrganizationViewData.Summary("coast", "海岸互助社", 7, "听潮", OrganizationViewData.Relation.APPLIED),
                new OrganizationViewData.Summary("north", "北港拾荒者", 3, "阿洛", OrganizationViewData.Relation.NONE));
        return new OrganizationViewData.Snapshot(true, 32, 12, 200, 150, member ? "lighthouse" : "",
                organizations, member ? detail : null);
    }

    private static List<StoryBookEntryViewData> sampleFragments() {
        String body = "潮水退去的第三天，我们在灯塔下找到了第一份模板备份。\n\n"
                + "它被封在铅盒里，编号已经模糊。医生说只要核心没有被感染，就能重建一个完整的人——"
                + "但她没说重建出来的，还是不是原来的那个人。\n\n"
                + "逐光会的人在夜里来过。他们没有带走任何东西，只在墙上留下了一道白色的弧线。";
        return List.of(
                new StoryBookEntryViewData("dreamingfishcore:clue/harness_lead_box", 1, 1, 0, "灯塔下的铅盒", body, "第 3 日 · 黄昏", "守望者 07", "灯塔值守记录", "1 夜", "1 只铅盒", "退潮后的浅滩", true),
                new StoryBookEntryViewData("dreamingfishcore:clue/harness_white_arc", 2, 1, 0, "白色弧线", body, "第 4 日 · 深夜", "未知", "墙面痕迹", "1 夜", "1 处痕迹", "无人值守时发现", false),
                new StoryBookEntryViewData("dreamingfishcore:clue/harness_coast_hospital", 3, 2, 1, "海岸医院", body, "第 9 日", "林医生", "海岸医院随访", "3 天", "12 例", "院内隔离观察", false),
                new StoryBookEntryViewData("dreamingfishcore:clue/harness_inhibitor_page", 4, 2, 1, "抑制剂配方残页", body, "第 11 日", "药剂师", "药剂科手稿", "未标注", "1 页残稿", "抢救时掉落", true),
                new StoryBookEntryViewData("dreamingfishcore:clue/harness_zhuguang_letter", 5, 3, 2, "逐光会的信", body, "第 20 日", "逐光会", "逐光会公开信", "1 封", "1 封", "张贴于公告栏", false));
    }
}
