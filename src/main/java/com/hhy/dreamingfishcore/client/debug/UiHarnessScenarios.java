package com.hhy.dreamingfishcore.client.debug;

import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationViewData;
import com.hhy.dreamingfishcore.gameplay.organization_system.client.cache.OrganizationClientCache;
import com.hhy.dreamingfishcore.gameplay.research_system.ResearchTableMenu;
import com.hhy.dreamingfishcore.gameplay.research_system.client.ResearchTableClientCache;
import com.hhy.dreamingfishcore.gameplay.research_system.client.Screen_ResearchTable;
import com.hhy.dreamingfishcore.gameplay.research_system.network.Packet_ResearchTableOpen;
import com.hhy.dreamingfishcore.server.server_ui_system.client.serverscreen.ServerScreenUI;
import com.hhy.dreamingfishcore.server.server_ui_system.client.terminal.TerminalScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

    /** 研究桌演示用的容器 id：服务端没有这个容器，关界面时发出的关闭包会被服务端忽略。 */
    private static final int RESEARCH_TABLE_CONTAINER_ID = 127;
    /** 研究桌演示用的课题：10 条真实存在的原版物品（与服务端默认配置的 10~15 条对齐）。 */
    private static final List<String> RESEARCH_TABLE_OFFER = List.of(
            "minecraft:oak_fence", "minecraft:bookshelf", "minecraft:lantern", "minecraft:glass_pane",
            "minecraft:stone_bricks", "minecraft:chest", "minecraft:crafting_table", "minecraft:white_wool",
            "minecraft:oak_planks", "minecraft:iron_door");

    private UiHarnessScenarios() {
    }

    private static final java.util.Map<String, String> POSTCARD_NAMES = java.util.Map.ofEntries(
            java.util.Map.entry("forest", "繁花森林"), java.util.Map.entry("pines", "针叶林"),
            java.util.Map.entry("mountains", "裸岩山峰"), java.util.Map.entry("snowy", "积雪的针叶林"),
            java.util.Map.entry("cherry", "樱花树林"), java.util.Map.entry("village", "平原"),
            java.util.Map.entry("desert", "沙漠"), java.util.Map.entry("badlands", "恶地"),
            java.util.Map.entry("coast", "沙滩"), java.util.Map.entry("river", "河流"),
            java.util.Map.entry("swamp", "沼泽"), java.util.Map.entry("jungle", "丛林"),
            java.util.Map.entry("savanna", "热带草原"), java.util.Map.entry("mushroom", "蘑菇岛"),
            java.util.Map.entry("cave", "繁茂洞穴"), java.util.Map.entry("nether", "下界荒地"),
            java.util.Map.entry("end", "末地"));

    private static void showPostcard(net.minecraft.client.Minecraft minecraft, String scene, String time, boolean rain,
                                     boolean first, long durationMs) {
        minecraft.setScreen(null);
        com.hhy.dreamingfishcore.client.ui.notification.RegionPostcard.preview(scene, time, rain);
        com.hhy.dreamingfishcore.client.ui.notification.NotificationManager.show(
                com.hhy.dreamingfishcore.client.ui.notification.Notification.builder()
                        .title(Component.literal(POSTCARD_NAMES.getOrDefault(scene, scene)))
                        .message(Component.literal(first ? "首次发现  ·  + 120 经验  ·  已探索 14" : ""))
                        .position(com.hhy.dreamingfishcore.client.ui.notification.NotificationPosition.CENTER_TOP)
                        .theme(com.hhy.dreamingfishcore.client.ui.notification.NotificationTheme.GOLD)
                        .queuePolicy(com.hhy.dreamingfishcore.client.ui.notification.NotificationQueuePolicy.REPLACE)
                        .durationMs(durationMs).build());
    }

    static void registerAll() {
        UiHarness.register("hud", minecraft -> minecraft.setScreen(null));
        // 上方居中的区域明信片：postcard:<场景>:<day|dusk|night>[:rain]，postcard_plain 为再次进入（只有地名），
        // postcard_anim 只显示 3.4 秒，按不同等待时间连续截图可拼出入场与退场动画
        // 按真实群系走一遍判定（群系标签 + 群系颜色 + 世界当前时间），例如 postcard_biome:minecraft:dark_forest
        for (String biome : List.of("minecraft:forest", "minecraft:birch_forest", "minecraft:dark_forest",
                "minecraft:flower_forest", "minecraft:swamp", "minecraft:cherry_grove", "minecraft:badlands",
                "minecraft:snowy_slopes", "minecraft:lush_caves", "minecraft:plains")) {
            UiHarness.register("postcard_biome:" + biome, minecraft -> {
                minecraft.setScreen(null);
                com.hhy.dreamingfishcore.client.ui.notification.RegionPostcard.preview(null, null, false);
                net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.parse(biome);
                com.hhy.dreamingfishcore.client.ui.notification.NotificationManager.show(
                        com.hhy.dreamingfishcore.client.ui.notification.Notification.builder()
                                .title(Component.translatable(id.toLanguageKey("biome")))
                                .message(Component.literal("首次发现  ·  + 120 经验  ·  已探索 14"))
                                .position(com.hhy.dreamingfishcore.client.ui.notification.NotificationPosition.CENTER_TOP)
                                .theme(com.hhy.dreamingfishcore.client.ui.notification.NotificationTheme.GOLD)
                                .queuePolicy(com.hhy.dreamingfishcore.client.ui.notification.NotificationQueuePolicy.REPLACE)
                                .biome(biome)
                                .durationMs(20_000L).build());
            });
        }
        for (String scene : com.hhy.dreamingfishcore.client.ui.notification.RegionPostcard.sceneNames()) {
            for (String time : List.of("day", "dusk", "night")) {
                String id = scene + ":" + time;
                UiHarness.register("postcard:" + id, minecraft -> showPostcard(minecraft, scene, time, false, true, 20_000L));
                UiHarness.register("postcard:" + id + ":rain", minecraft -> showPostcard(minecraft, scene, time, true, true, 20_000L));
                UiHarness.register("postcard_plain:" + id, minecraft -> showPostcard(minecraft, scene, time, false, false, 20_000L));
                UiHarness.register("postcard_anim:" + id, minecraft -> showPostcard(minecraft, scene, time, false, true, 3_400L));
            }
        }
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
        // 右上角系统消息（事件卡）：文字与服务端默认配置一致，并带上服务端会发的事件信息
        UiHarness.register("system_messages", minecraft -> {
            minecraft.setScreen(null);
            com.hhy.dreamingfishcore.client.ui.notification.NotificationManager.clearAll();
            for (SampleMessage sample : sampleSystemMessages(minecraft)) {
                com.hhy.dreamingfishcore.server.server_ui_system.client.SystemMessageDisplay.addMessage(sample.text(),
                        sample.color(), sample.event());
            }
        });
        UiHarness.register("canvas_test", minecraft -> minecraft.setScreen(new UiCanvasTestScreen()));
        UiHarness.register("gallery", minecraft -> minecraft.setScreen(new UiGalleryScreen()));
        UiHarness.register("gallery_dialog", minecraft -> {
            UiGalleryScreen screen = new UiGalleryScreen();
            minecraft.setScreen(screen);
            screen.openDialogForTest();
        });
        UiHarness.register("title", minecraft -> minecraft.setScreen(new TitleScreen()));
        // 模拟其他模组往标题界面加的按钮：带提示的图标按钮、没有任何文字的图标按钮、纯文本按钮
        UiHarness.register("title_mod_buttons", minecraft -> minecraft.setScreen(new TitleScreen() {
            @Override
            protected void init() {
                super.init();
                addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.empty(), button -> {
                        }).bounds(0, 0, 20, 20)
                        .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("机械动力配置"))).build());
                addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.empty(), button -> {
                        }).bounds(0, 0, 20, 20).build());
                addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("Voxy"), button -> {
                        }).bounds(0, 0, 40, 20).build());
            }
        }));
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
        // 清空后放入一组典型聊天；chat_busy_open 再打开聊天界面
        UiHarness.register("chat_busy", minecraft -> {
            minecraft.setScreen(null);
            fillSampleChat(minecraft);
        });
        UiHarness.register("chat_busy_open", minecraft -> {
            fillSampleChat(minecraft);
            minecraft.setScreen(new ChatScreen(""));
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
        // 首次启动的加载画面：进度 4 秒走完一轮；_fallback 版按界面着色器未就绪时的样子绘制
        UiHarness.register("loading_startup", minecraft -> minecraft.setScreen(new StartupPreview(false)));
        UiHarness.register("loading_startup_fallback", minecraft -> minecraft.setScreen(new StartupPreview(true)));
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

        UiHarness.register("research_table", UiHarnessScenarios::openResearchTable);

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

    /** 在普通界面里预览首次启动的加载画面。 */
    private static final class StartupPreview extends net.minecraft.client.gui.screens.Screen {
        private final com.hhy.dreamingfishcore.client.ui.loading.LoadingSurface surface =
                new com.hhy.dreamingfishcore.client.ui.loading.LoadingSurface().startup(true);
        private final boolean fallback;
        private final long started = net.minecraft.Util.getMillis();

        StartupPreview(boolean fallback) {
            super(Component.literal("startup preview"));
            this.fallback = fallback;
        }

        @Override
        protected void init() {
            com.hhy.dreamingfishcore.client.ui.framework.render.SdfRenderer.forceFallback(fallback);
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int progress = (int) ((net.minecraft.Util.getMillis() - started) / 40L % 101L);
            surface.progress(progress).render(graphics, width, height);
        }

        @Override
        public void removed() {
            com.hhy.dreamingfishcore.client.ui.framework.render.SdfRenderer.forceFallback(false);
            surface.host().close();
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

    /**
     * 研究桌：把"有内容"的界面截下来（课题列表、提交槽、背包都要有东西）。
     *
     * <p>研究桌是**自绘的容器界面**：槽位内容放在真实的 {@link ResearchTableMenu} 里，界面靠
     * {@code MenuAccess} 拿到菜单再画。所以这里也要在客户端自己造一份菜单，并把
     * {@code player.containerMenu} 指过去——这正是原版客户端收到开屏包时做的两步
     * （见 {@code MenuScreens#create}）。少做一步，{@code Screen_ResearchTable#tick}
     * 就会认为"服务端已经把界面关了"而立刻收屏。</p>
     *
     * <p>状态走 {@link Packet_ResearchTableOpen}：与服务端下发的是同一条路，界面拿到的东西
     * 和真实运行时一模一样。场景里没有真的研究桌方块（服务端不会回推任何东西），但仍然每 tick
     * 重写一遍，免得被别的同步顶掉。</p>
     */
    private static void openResearchTable(net.minecraft.client.Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        // 用玩家脚下的坐标：界面会把它随"打开请求"发给服务端，也是菜单与快照对上的依据。
        BlockPos tablePos = minecraft.player.blockPosition();
        ResearchTableMenu menu = new ResearchTableMenu(RESEARCH_TABLE_CONTAINER_ID,
                minecraft.player.getInventory(), tablePos);
        // 先指菜单再开屏：否则旧界面在 setScreen 里 removed() 时会按"我还开着"去关容器，
        // 把刚建好的菜单顶掉（研究桌的 tick 会让界面立刻收屏）。
        minecraft.player.containerMenu = menu;
        minecraft.setScreen(new Screen_ResearchTable(menu, minecraft.player.getInventory(),
                Component.literal("研究桌")));
        applyResearchTableState(minecraft, menu, tablePos);
        UiHarness.whileStep(() -> applyResearchTableState(minecraft, menu, tablePos));
    }

    /** 每 tick 重写一遍研究桌的演示状态：菜单指向、提交槽、背包、服务端那份快照。 */
    private static void applyResearchTableState(net.minecraft.client.Minecraft minecraft,
                                                ResearchTableMenu menu, BlockPos tablePos) {
        if (minecraft.player == null) {
            return;
        }
        minecraft.player.containerMenu = menu;
        // 提交槽：20 个铁锭。堆叠上限 64 ÷ 除数 4 = 需要 16 个，所以这一叠正好是"够提交"的状态。
        menu.getSubmitContainer().setItem(ResearchTableMenu.SUBMIT_SLOT, new ItemStack(Items.IRON_INGOT, 20));
        fillResearchTableInventory(minecraft.player.getInventory());
        // 快照：10 条课题、消耗 100 点、手上 50 点（标题上的"当前"会变红、警告行给出原因）。
        ResearchTableClientCache.accept(new Packet_ResearchTableOpen(tablePos, RESEARCH_TABLE_OFFER, 100, 50,
                List.of(), "§c经验不足：需要 100 点，你当前有 50 点", true, 4, true,
                "§a可以提交：16 个铁锭"));
    }

    /** 背包里放几样东西：菜单的背包槽直接指向玩家背包，所以放了这里背包区就不是空的。 */
    private static void fillResearchTableInventory(Inventory inventory) {
        inventory.setItem(9, new ItemStack(Items.STONE, 64));        // 主背包第一行
        inventory.setItem(10, new ItemStack(Items.TORCH, 32));
        inventory.setItem(11, new ItemStack(Items.BREAD, 5));
        inventory.setItem(22, new ItemStack(Items.IRON_INGOT, 20));  // 与提交槽同一种物品，"背包 N"能算出来
        inventory.setItem(35, new ItemStack(Items.DIAMOND, 7));
        inventory.setItem(0, new ItemStack(Items.IRON_PICKAXE));     // 快捷栏
        inventory.setItem(1, new ItemStack(Items.OAK_FENCE, 16));
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

    /** 清空聊天后放入一组典型消息：系统通知、带 Rank 与称号的玩家、引用并 @ 自己的回复、无 Rank 的玩家。 */
    private static void fillSampleChat(net.minecraft.client.Minecraft minecraft) {
        com.hhy.dreamingfishcore.client.ui.chat.ImmersiveChatManager.clearVisibleMessages();
        java.util.UUID self = minecraft.player != null ? minecraft.player.getUUID() : java.util.UUID.randomUUID();
        long now = System.currentTimeMillis();
        minecraft.gui.getChat().addMessage(Component.literal("§7[系统] 海岸医院的发电机重新启动了"));
        com.hhy.dreamingfishcore.client.ui.chat.ImmersiveChatManager.receivePlayerMessage(self, "OPERATOR", 0xFF5555,
                "萌新鱼友", 0x9FD46C, "Dev", "有人看到灯塔那边的白色弧线了吗？", now);
        com.hhy.dreamingfishcore.client.ui.chat.ImmersiveChatManager.receivePlayerMessage(java.util.UUID.randomUUID(),
                "FISH+", 0x55FFFF, "逐光会", 0xFFC857, "Lighthouse", "看到了，@Dev 我们在医院门口集合，带上抑制剂。", now,
                "Dev", "有人看到灯塔那边的白色弧线了吗？");
        com.hhy.dreamingfishcore.client.ui.chat.ImmersiveChatManager.receivePlayerMessage(java.util.UUID.randomUUID(),
                "", 0xFFFFFF, "", 0xFFFFFF, "听海", "收到，我从北港过去，大概三分钟。", now);
        minecraft.gui.getChat().addMessage(Component.literal("§e白芷 加入了队伍「灯塔守望会」"));
    }

    private record SampleMessage(Component text, int color,
                                 com.hhy.dreamingfishcore.client.ui.notification.SystemEvent event) {
    }

    /** 与 ChangeJoinMessage、SystemMessageEventHandler 的默认配置拼出来的文字一致；最后一条最新。 */
    private static List<SampleMessage> sampleSystemMessages(net.minecraft.client.Minecraft minecraft) {
        java.util.UUID self = minecraft.player != null ? minecraft.player.getUUID() : java.util.UUID.randomUUID();
        net.minecraft.network.chat.MutableComponent advancement = Component.literal("[FISH+] ")
                .withStyle(net.minecraft.ChatFormatting.AQUA)
                .append(Component.literal("Lighthouse").withStyle(net.minecraft.ChatFormatting.WHITE))
                .append(Component.literal(" 完成了进度").withStyle(net.minecraft.ChatFormatting.WHITE))
                .append(Component.literal("[石器时代]").withStyle(net.minecraft.ChatFormatting.GREEN));
        net.minecraft.network.chat.MutableComponent challenge = Component.literal("Dev")
                .append(Component.literal(" 完成了挑战").withStyle(net.minecraft.ChatFormatting.WHITE))
                .append(Component.literal("[资深怪物猎人]").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
        return List.of(
                new SampleMessage(Component.literal("§b[§bFISH+§b]§b鱼友§6林潮§b不想和你VAN辣！"), 0x55FFFF,
                        event(com.hhy.dreamingfishcore.server.server_ui_system.network.SystemMessageKind.LEAVE, null, "林潮",
                                ItemStack.EMPTY, "")),
                new SampleMessage(Component.literal("§7[§a+§7]§b鱼友§e听海§b来和你VAN辣！"), 0xAAAAAA,
                        event(com.hhy.dreamingfishcore.server.server_ui_system.network.SystemMessageKind.JOIN, null, "听海",
                                ItemStack.EMPTY, "")),
                new SampleMessage(Component.literal("§c[§cOPERATOR§c]§b鱼友§6Dev§b来和你VAN辣！"), 0xFF5555,
                        event(com.hhy.dreamingfishcore.server.server_ui_system.network.SystemMessageKind.JOIN, self, "Dev",
                                ItemStack.EMPTY, "")),
                new SampleMessage(Component.literal("白芷 被僵尸杀死了"), 0xAAAAAA,
                        event(com.hhy.dreamingfishcore.server.server_ui_system.network.SystemMessageKind.DEATH, null, "白芷",
                                ItemStack.EMPTY, "")),
                new SampleMessage(advancement, 0x55FF55,
                        event(com.hhy.dreamingfishcore.server.server_ui_system.network.SystemMessageKind.TASK, null, "Lighthouse",
                                new ItemStack(Items.STONE_PICKAXE), "石器时代")),
                new SampleMessage(challenge, 0xAA00AA,
                        event(com.hhy.dreamingfishcore.server.server_ui_system.network.SystemMessageKind.CHALLENGE, self, "Dev",
                                new ItemStack(Items.DIAMOND_SWORD), "资深怪物猎人")));
    }

    private static com.hhy.dreamingfishcore.client.ui.notification.SystemEvent event(
            com.hhy.dreamingfishcore.server.server_ui_system.network.SystemMessageKind kind, java.util.UUID id, String player,
            ItemStack icon, String headline) {
        java.util.UUID playerId = id != null ? id : java.util.UUID.nameUUIDFromBytes(player.getBytes(
                java.nio.charset.StandardCharsets.UTF_8));
        return new com.hhy.dreamingfishcore.client.ui.notification.SystemEvent(kind, playerId, player, icon,
                Component.literal(headline));
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
