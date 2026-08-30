package com.hhy.dreamingfishcore.server.server_ui_system.client.serverscreen;

import com.hhy.dreamingfishcore.client.cache.ClientCacheManager;
import com.hhy.dreamingfishcore.client.cache.EconomyTerminalClientCache;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.courage.PlayerCourageManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.PlayerInfectionManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.strength.client.sync.PlayerStrengthClientSync;
import com.hhy.dreamingfishcore.gameplay.playerlevel_system.overalllevel.PlayerLevelManager;
import com.hhy.dreamingfishcore.gameplay.story_system.network.Packet_WorldHistoryRequest;
import com.hhy.dreamingfishcore.gameplay.story_system.network.Packet_WorldHistoryResponse;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceEntry;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceViewData;
import com.hhy.dreamingfishcore.gameplay.guidance_system.client.cache.GuidanceClientCache;
import com.hhy.dreamingfishcore.gameplay.guidance_system.network.Packet_GuidanceSnapshotRequest;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcConversationViewData;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcMessageRecord;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcMessageViewData;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.client.cache.NpcMessageClientCache;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.network.Packet_NpcMessageReadRequest;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.network.Packet_NpcMessageReplyRequest;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.network.Packet_NpcMessageSnapshotRequest;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryTaskData;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.economy_bridge.network.Packet_EconomyTerminalRequest;
import com.hhy.dreamingfishcore.server.playerdata_system.network.Packet_RequestPlayerStats;
import com.hhy.dreamingfishcore.server.title_system.PlayerTitleManager;
import com.hhy.dreamingfishcore.server.title_system.Title;
import com.hhy.dreamingfishcore.server.playerdata_system.PlayerData;
import com.hhy.dreamingfishcore.server.rank_system.PlayerRankManager;
import com.hhy.dreamingfishcore.server.rank_system.Rank;
import com.hhy.dreamingfishcore.server.rank_system.RankRegistry;
import com.hhy.dreamingfishcore.server.rank_system.network.Packet_EquipPlayerRank;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.hhy.dreamingfishcore.server.notice_system.network.Packet_NoticeListRequest;
import com.hhy.dreamingfishcore.server.notice_system.network.Packet_MarkNoticeReadRequest;
import com.hhy.dreamingfishcore.server.notice_system.network.Packet_NewPlayerGuideViewed;
import com.hhy.dreamingfishcore.server.notice_system.NoticeData;
import com.hhy.dreamingfishcore.server.notice_system.NoticeCategory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.hhy.dreamingfishcore.server.server_ui_system.client.serverscreen.ServerScreenUI_RendererUtils.*;

/**
 * 服务器UI - 虚拟坐标系统
 *
 * 设计原理：
 * 1. 虚拟基准尺寸 640×360（基于 2560×1440 全屏 + GUI缩放4 的内部渲染尺寸）
 * 2. 所有元素按虚拟尺寸设计，运行时自动等比缩放到实际屏幕
 * 3. 保证不同分辨率、不同 GUI 缩放下 UI 显示一致
 *
 * 坐标系统：
 * - 虚拟坐标：设计时使用的 640×360 坐标系
 * - 屏幕坐标：实际渲染到屏幕的像素坐标
 * - uiScale：虚拟坐标到屏幕坐标的缩放比例
 *
 * 保留所有原有特性：动画、美术样式、布局等
 */
@OnlyIn(Dist.CLIENT)
public class ServerScreenUI_Screen extends Screen {

    private static final String VERSION = "§bDreaming§dFish §7v0.1(Private)";

    // ==================== 虚拟基准尺寸 ====================
    // 基准：2560×1440 全屏 + GUI缩放4 → 内部渲染尺寸 640×360
    // 所有 UI 元素按这个尺寸设计，运行时自动缩放
    private static final int BASE_WIDTH = 640;
    private static final int BASE_HEIGHT = 360;

    // ==================== 面板比例 ====================
    private static final float LEFT_PANEL_PERCENT = 0.36f;  // 左侧平板入口区占虚拟宽度的 36%
    private static final float RIGHT_PANEL_PERCENT = 0.40f;  // 右侧内容区占虚拟宽度的 40%

    // ==================== 颜色定义（平板/终端风格） ====================
    private static final int PANEL_BACKGROUND_COLOR = 0xE61B2530;  // 柔和深蓝灰内容底
    private static final int PANEL_BORDER_COLOR = 0xFF7AA8C7;      // 降低饱和度的终端蓝
    private static final int TABLET_SHELL_COLOR = 0xDC0B1118;
    private static final int TABLET_HEADER_COLOR = 0xE6131B24;
    private static final int TABLET_CONTENT_COLOR = 0xF01B2530;
    private static final int TABLET_SHADOW_COLOR = 0x55000000;
    private static final int TABLET_CARD_COLOR = 0xFF24313E;
    private static final int TABLET_CARD_HOVER_COLOR = 0xFF2C3B4A;
    private static final int TABLET_CARD_BORDER_COLOR = 0xFF344555;
    private static final int TABLET_TEXT_COLOR = 0xFFE8EDF2;
    private static final int TABLET_MUTED_TEXT_COLOR = 0xFFA7B2BE;
    // 注意：游戏化卡片配色、进度条颜色等常量已移至 ServerScreenUI_RendererUtils

    // ==================== 动画时间配置 ====================
    // 开启动画
    private long openTime = 0;                                // UI 打开的时间戳
    private static final long ANIMATION_DURATION = 400;        // 打开边框动画持续时间（毫秒）

    // 关闭动画
    private boolean isClosing = false;                         // 是否正在执行关闭动画
    private long closeTime = 0;                                // UI 开始关闭的时间戳
    private static final long CLOSE_ANIMATION_DURATION = 150;  // 关闭动画持续时间（毫秒）

    // 跳过动画标记（从子屏幕返回时使用）
    private boolean skipAnimation = false;

    // ==================== 虚拟坐标系统变量 ====================
    // 这些变量在 calculateVirtualSize() 中每帧重新计算
    private float uiScale;           // 虚拟坐标到屏幕坐标的缩放比例
    private int virtualWidth;        // 虚拟画布宽度
    private int virtualHeight;       // 虚拟画布高度

    // 面板位置（虚拟坐标）
    private int leftPanelWidth;      // 左栏宽度（虚拟像素）
    private int rightPanelWidth;     // 右栏宽度（虚拟像素）
    private int rightCenterX;        // 右栏中心 X 坐标（虚拟像素）
    private int RIGHT_PANEL_START_X; // 右侧面板起点 X 坐标（虚拟像素）
    private int centerCenterX;       // 中间区域中心 X 坐标（虚拟像素）

    // 玩家模型位置（虚拟坐标）
    private int MODEL_HEIGHT;        // 模型总高度（虚拟像素）
    private int MODEL_SIZE;          // 模型缩放大小（虚拟像素）
    private int MODEL_FOOT_Y;        // 模型脚部 Y 坐标（虚拟像素）
    private int MODEL_HEAD_Y;        // 模型头部 Y 坐标（虚拟像素）

    // 关闭动画滑动距离（虚拟坐标）
    private int LEFT_PANEL_SLIDE_DISTANCE;   // 左面板向左滑动的最大距离
    private int RIGHT_PANEL_SLIDE_DISTANCE;  // 右面板向右滑动的最大距离

    // 个人档案 Rank 卡片可点击区域（虚拟坐标）
    private int rankBoxClickX1, rankBoxClickY1;
    private int rankBoxClickX2, rankBoxClickY2;

    // ==================== 左侧灵动岛按钮 ====================
    private static final String NOTICE_UI_NAME = "梦屿广播";
    private static final int NPC_MESSAGE_PAGE_INDEX = 10;
    private static final String GUIDANCE_ICON = "⌖";
    private static final String[] LEFT_BUTTON_ICONS = {"👤", "❓", "📢", "📖", "🏆", "⭐", "🛒", "🏰", "◷", "⚙️", "✉"};
    private static final String[] LEFT_BUTTON_NAMES = {"个人档案", "新玩家帮助", NOTICE_UI_NAME, "故事进展", "玩家与排行", "服务器成就", "服务器商店", "领地", "世界历史", "设置", "NPC 私信"};
    private static final int[] LEFT_BUTTON_COLORS = {
        0xFFAAAAAA,  // 个人档案 - 灰色
        0xFF55FF55,  // 帮助 - 绿色
        0xFF4FC3F7,  // 梦屿广播 - 淡蓝色
        0xFFAAFFAA,  // 故事进展 - 绿色
        0xFF4FC3F7,  // 玩家与排行 - 淡蓝色
        0xFFFFFFAA,  // 服务器成就 - 黄色
        0xFF4FC3F7,  // 服务器商店 - 橙色
        0xFF4FC3F7,  // 领地 - 紫色
        0xFFFFC857,  // 世界历史 - 金色
        0xFF888888,  // 设置 - 深灰色
        0xFF8CCEFF   // NPC 私信 - 终端蓝
    };

    // 左侧按钮可点击区域（虚拟坐标）
    private int[] leftButtonX1 = new int[LEFT_BUTTON_ICONS.length];
    private int[] leftButtonY1 = new int[LEFT_BUTTON_ICONS.length];
    private int[] leftButtonX2 = new int[LEFT_BUTTON_ICONS.length];
    private int[] leftButtonY2 = new int[LEFT_BUTTON_ICONS.length];

    // 主页底部 Dock。-1 代表主页，其他值直接映射到一级模块索引。
    private static final int[] DASHBOARD_DOCK_TARGETS = {-1, 0, 2, NPC_MESSAGE_PAGE_INDEX, 3, 9};
    private final int[] dashboardDockX1 = new int[DASHBOARD_DOCK_TARGETS.length];
    private final int[] dashboardDockY1 = new int[DASHBOARD_DOCK_TARGETS.length];
    private final int[] dashboardDockX2 = new int[DASHBOARD_DOCK_TARGETS.length];
    private final int[] dashboardDockY2 = new int[DASHBOARD_DOCK_TARGETS.length];

    // 当前选中的模块索引，-1 表示一级模块桌面
    private int selectedLeftButtonIndex = -1;
    private boolean newPlayerGuideViewReported = false;
    // 箭头动画时间
    private long arrowAnimTime = 0;
    // 上次选中的按钮索引（用于动画检测）
    private int lastSelectedIndex = -1;
    // 注意：按钮配色和信息区配色常量已移至 ServerScreenUI_RendererUtils

    // ==================== 公告系统数据 ====================
    private static List<NoticeData> cachedNotices = new ArrayList<>();
    private static Set<Integer> cachedReadNoticeIds = new java.util.HashSet<>();
    private static long noticeScrollOffset = 0;  // 滚动偏移量
    private static final int NOTICE_CARD_HEIGHT = 52;  // 旧渲染器兼容高度（虚拟像素）
    private static final int NOTICE_FEED_CARD_HEIGHT = 70;  // 当前终端公告卡片高度（虚拟像素）
    private static final int VISIBLE_NOTICES = 5;  // 可见公告数量
    private static boolean hasUnreadNoticesGlobal = false;  // 全局未读公告标记（用于按钮感叹号）
    /** 0=游戏公告，1=服务器通知。 */
    private int selectedNoticeTab = 0;
    private final int[][] noticeTabClickAreas = {new int[4], new int[4]};
    private int noticeVisibleCardCapacity = 0;
    /** 当前公告详情 ID；-1 表示公告列表。详情页嵌在梦屿广播终端内。 */
    private int selectedNoticeDetailId = -1;
    private long noticeDetailScrollOffset = 0L;
    private int noticeDetailMaxScroll = 0;
    private final int[] noticeDetailBackArea = new int[4];
    private final int[] noticeDetailContentArea = new int[4];
    /** 游戏公告的阶段筛选；首次打开或阶段切换后默认选中当前已开放阶段。 */
    private String selectedNoticeStageId = null;
    private final List<NoticeStageClickArea> noticeStageClickAreas = new ArrayList<>();
    /** 仅用于记录当前阶段侧栏的布局尺寸，点击热区仍由 noticeStageClickAreas 管理。 */
    private int noticeStageFilterHeight = 0;
    private int noticeStageFilterWidth = 0;
    // 注意：公告点击区域已移至 PageRenderer.noticeClickArea

    // ==================== 世界历史数据 ====================
    /** 历史由服务端筛选后发送，客户端不直接读取世界存档文件。 */
    private static List<Packet_WorldHistoryResponse.HistoryEntry> cachedHistory = List.of();
    private static long historyTotalEventCount = 0L;
    private static boolean historyLoaded = false;
    private static boolean historyWritesEnabled = false;
    private static long historyScrollOffset = 0L;
    private static int historyVisibleEntries = 5;
    private static final int HISTORY_CARD_HEIGHT = 32;
    private static final int HISTORY_CARD_GAP = 5;

    // ==================== 经济市场数据 ====================
    private static long marketScrollOffset = 0L;
    private static final int MARKET_ROW_HEIGHT = 38;
    private static final int MARKET_ROW_GAP = 6;
    private static int marketVisibleRows = 5;

    // ==================== 任务系统数据 ====================
    /** 故事任务卡片同时容纳世界任务说明和对应的个人线索。 */
    private static final int TASK_CARD_HEIGHT = 62;
    private static final int VISIBLE_TASKS = 3;  // 可见任务数量
    private static long taskScrollOffset = 0;  // 任务滚动偏移量
    private static boolean taskShowServerTasks = true;  // 保留旧快照兼容状态；故事页现在统一显示所有任务
    private static String selectedStageId = null;  // 当前选中的阶段ID，null表示显示阶段列表
    private static long stageScrollOffset = 0;  // 阶段网格滚动行偏移量
    private int stageGridColumns = 1;
    private int stageGridVisibleRows = 1;
    private int stageGridCardHeight = 64;
    private int stageGridGap = 8;
    private final List<StageClickArea> stageClickAreas = new ArrayList<>();
    private final List<StageClickArea> storyStageNavClickAreas = new ArrayList<>();
    // 注意：任务点击区域已移至 PageRenderer.taskClickArea 和 taskTabArea

    // ==================== NPC 私信 ====================
    private static int selectedMessageNpcId = -1;
    private static long messageThreadScrollOffset = 0L;
    private static long conversationScrollOffset = 0L;
    /** 当前打开的私信记录；空字符串表示会话消息列表。 */
    private static String selectedMessageRecordId = "";
    private static long messageDetailScrollOffset = 0L;
    private int messageDetailMaxScroll = 0;
    private int lastReadRequestedNpcId = -1;
    private final List<ConversationClickArea> conversationClickAreas = new ArrayList<>();
    private final List<MessageClickArea> messageClickAreas = new ArrayList<>();
    private final List<MessageReplyClickArea> messageReplyClickAreas = new ArrayList<>();
    private final int[] conversationListArea = new int[4];
    private final int[] messageThreadArea = new int[4];
    private final int[] messageDetailBackArea = new int[4];
    private final int[] messageDetailContentArea = new int[4];

    // ==================== 帮助系统数据 ====================
    private int selectedHelpTopicIndex = 0;
    private final List<HelpTopicClickArea> helpTopicClickAreas = new ArrayList<>();
    private static final HelpTopic[] HELP_TOPICS = {
        new HelpTopic(
            "01", "身体与肢体", "命中部位决定实际承伤", 0xFFFF6677,
            new String[]{"头部 ×1.2", "胸部 ×1.0", "腿 / 脚 ×0.9"},
            new String[]{
                "身体分为头、胸、腿、脚四个受伤区域；近战与弹射物会按实际命中部位结算。",
                "头部受伤最危险，胸部保持原伤害，腿部和脚部受到的伤害较低。",
                "受伤部位会在体征监测中短暂标出；跌落、火焰等环境伤害不使用肢体倍率。"
            },
            "主动承伤", "近战攻击临身前起跳，可能让命中落到腿或脚，以 ×0.9 承伤；但跳跃会消耗 3 点体力。"),
        new HelpTopic(
            "02", "体力与勇气", "行动资源与心理状态", 0xFFB58BFF,
            new String[]{"疾跑 -4 / 秒", "跳跃 -3", "停歇 +5 / 秒"},
            new String[]{
                "疾跑和跳跃消耗体力；停止消耗 5 秒后开始恢复，耗尽后须恢复到 20 点才能再次疾跑。",
                "光亮、白天和 30 格内的同伴帮助恢复勇气；黑暗、夜晚、敌怪和地下深处会加速恐惧。",
                "环境恢复通常止于 60；10 秒内击杀 5 只敌怪可增加 10 点，目睹附近玩家死亡会失去 10 点。",
                "勇气低于 20% 获得虚弱 I 与缓慢 I；达到 85% 获得力量 I。"
            },
            "夜间行动", "带上光源、尽量结伴，并为撤离预留至少 20 点体力；不要让体力和勇气同时见底。"),
        new HelpTopic(
            "03", "死亡与重生", "复活点数、物品栏与尸体", 0xFFFFC857,
            new String[]{"幸存者 5 点", "感染者 20 点", "每日 +5 点"},
            new String[]{
                "正常重生会扣除基础复活点数，物品留在死亡地点的尸体中，需要返回取回。",
                "也可额外消耗 30 点保留物品栏：幸存者总计 35 点，感染者总计 50 点。",
                "尸体会保存归属与位置；正常重生时可以锁定尸体，保护留下的物品。",
                "目前每个游戏日会补回 5 点复活点数，上限 100；连基础消耗都无法支付时仍需等待救援。"
            },
            "出发前检查", "在个人档案查看剩余复活点数。每日补充是缓冲，不是鼓励把最后一点储备用光。"),
        new HelpTopic(
            "04", "感染与体征", "受伤会推动感染恶化", 0xFF8B5CF6,
            new String[]{"受伤会累积", "白天 -5 / 日", "100% 感染者"},
            new String[]{
                "生命值净下降就会增加感染，增加量约为损失生命的 1/5；被丧尸击败还会额外增加。",
                "在感染者 32 格内停留，每 30 秒也会增加 1 点感染；达到 80% 后获得虚弱 I 与缓慢 I。",
                "根据目前的观察，未完全感染者在有天空的白天会逐渐回落，完整一个白天约降低 5 点；这不是治愈。",
                "感染达到 100% 就会成为感染者，死亡时需要消耗更多复活点数；感染规则会随故事阶段变化。"
            },
            "关注体征", "饱食度触发的普通自然回血最多恢复到最大生命的 70%；金苹果、恢复效果和医疗物资不受此限制。"),
        new HelpTopic(
            "05", "共同推进剧情", "逐光会筹建与你的选择", 0xFF78D6A3,
            new String[]{"全服任务", "个人对话", "加入与否"},
            new String[]{
                "当前阶段：逐光会正在筹建。你可以参与建设，也可以保持独立——两者都能正常游玩。",
                "是否加入逐光会由你自己决定；独立协作者同样能参与救援、调查与公共事务。",
                "回复 NPC、完成承诺或违背立场会改变好感度与关系；重要地点与目标会存入个人任务。",
                "阶段由作者在合适时机推进，不设必须赶上的时限；成功与失败的任务都会被保留。"
            },
            "留意终端", "广播与 NPC 私信是剧情入口：地点、物资与建设需求会随消息和公告送达。"),
        new HelpTopic(
            "06", "选择与后果", "这里没有唯一标准答案", 0xFF8CCEFF,
            new String[]{"可以分歧", "结果会保留", "世界会回应"},
            new String[]{
                "支持、拒绝、隐瞒或公开信息，都可能改变任务的成功与失败。",
                "选择可能影响 NPC 关系、组织身份、可见线索，以及后续出现的人物与事件。",
                "其他玩家可以作出相反决定；最终世界状态取决于全服行动汇合后的结果。",
                "已经发生的阶段变化会进入世界历史，后来者将在这些结果之上继续行动。"
            },
            "先了解再决定", "意见不同时请与其他玩家讨论。不同选择并非错误，分歧本身也是梦屿故事的一部分。")
    };

    // ==================== Rank 管理页面 ====================
    private boolean profileRankManagerOpen = false;
    private final List<Rank> displayedRankOptions = new ArrayList<>();
    private final List<int[]> rankOptionClickAreas = new ArrayList<>();

    private final Minecraft mc = Minecraft.getInstance();

    // 仍负责模型、点击区域等共享渲染工具；旧帮助页正文已从该类移除。
    private ServerScreenUI_PageRenderer pageRenderer;

    public ServerScreenUI_Screen() {
        super(Component.literal("服务器界面"));
    }

    public float getUiScale() { return uiScale; }
    public int getVirtualWidth() { return virtualWidth; }
    public int getVirtualHeight() { return virtualHeight; }
    public int getPanelBackgroundColor() { return PANEL_BACKGROUND_COLOR; }

    @Override
    protected void init() {
        super.init();
        // 从外部子界面返回时，清理子界面占用状态，并跳过终端重新展开动画。
        if (ServerScreenUI.isSubScreenActive()) {
            ServerScreenUI.onSubScreenClosed();
            ServerScreenUI.setReturningFromSubScreen(true);
        }
        pageRenderer = new ServerScreenUI_PageRenderer(this, LEFT_BUTTON_ICONS.length);
        // 检查是否从子屏幕返回，保存跳过动画标记
        skipAnimation = ServerScreenUI.isReturningFromSubScreen();
        if (skipAnimation) {
            ServerScreenUI.setReturningFromSubScreen(false);
        }
        // 记录动画开始时间
        // 如果是从子屏幕返回，跳过动画（将 openTime 设为过去的时间）
        if (skipAnimation) {
            openTime = Util.getMillis() - ANIMATION_DURATION - 1;
        } else {
            openTime = Util.getMillis();
        }
        // 计算缩放比例
        calculateVirtualSize();
        // 请求 EconomySystem 公共 API 摘要（余额 + 领地），未安装时服务端会安全返回未接入状态。
        DreamingFishCore_NetworkManager.sendToServer(new Packet_EconomyTerminalRequest());
        // 请求统计数据（群系 + 配方）
        DreamingFishCore_NetworkManager.sendToServer(new Packet_RequestPlayerStats());
        // 请求公告数据（用于更新感叹号状态）
        DreamingFishCore_NetworkManager.sendToServer(new Packet_NoticeListRequest());
        // 请求公开世界历史，供“世界历史”页面显示。
        DreamingFishCore_NetworkManager.sendToServer(new Packet_WorldHistoryRequest());
        // 短信与个人线索仍分别同步；故事页把线索嵌入对应的世界任务卡。
        DreamingFishCore_NetworkManager.sendToServer(new Packet_NpcMessageSnapshotRequest());
        DreamingFishCore_NetworkManager.sendToServer(new Packet_GuidanceSnapshotRequest());
    }

    /**
     * 计算虚拟尺寸和缩放比例
     *
     * 工作流程：
     * 1. 计算屏幕尺寸到虚拟基准尺寸的缩放比例
     * 2. 使用较小的缩放比例保持宽高比（避免拉伸变形）
     * 3. 计算虚拟画布尺寸（实际屏幕 / 缩放比例）
     * 4. 根据虚拟画布计算各元素位置
     *
     * 示例（2560×1440 全屏 + GUI缩放4）：
     *   this.width = 640, this.height = 360
     *   uiScale = 1.0
     *   virtualWidth = 640, virtualHeight = 360
     *
     * 示例（1920×1080 全屏 + GUI缩放2）：
     *   this.width = 960, this.height = 540
     *   uiScale = 1.5
     *   virtualWidth = 640, virtualHeight = 360
     */
    private void calculateVirtualSize() {
        // 计算屏幕尺寸到基准尺寸的缩放比例
        float scaleX = (float) this.width / BASE_WIDTH;    // 宽度缩放比
        float scaleY = (float) this.height / BASE_HEIGHT;  // 高度缩放比

        // 取较小值，确保内容完整显示（可能有黑边，但不会裁剪）
        uiScale = Math.min(scaleX, scaleY);

        // 反向计算虚拟画布尺寸
        // 如果屏幕比基准大，虚拟画布 = 基准尺寸
        // 如果屏幕比基准小，虚拟画布 > 基准尺寸（反向缩放）
        virtualWidth = (int) (this.width / uiScale);
        virtualHeight = (int) (this.height / uiScale);

        // ==================== 计算面板位置（虚拟坐标） ====================
        // 左栏宽度：虚拟宽度的 20%
        // 例如：virtualWidth = 640 → leftPanelWidth = 128
        leftPanelWidth = (int) (virtualWidth * LEFT_PANEL_PERCENT);

        // 右栏宽度：虚拟宽度的 35%
        // 例如：virtualWidth = 640 → rightPanelWidth = 224
        rightPanelWidth = (int) (virtualWidth * RIGHT_PANEL_PERCENT);

        // 右栏中心 X 坐标：从右边缘向左偏移右栏宽度的一半
        // 例如：virtualWidth=640, rightPanelWidth=224 → rightCenterX = 640 * (1 - 0.175) = 528
        rightCenterX = (int) (virtualWidth * (1.0f - RIGHT_PANEL_PERCENT / 2.0f));

        // 右栏起点 X 坐标：从右边缘向左偏移右栏宽度
        // 例如：virtualWidth=640, rightPanelWidth=224 → RIGHT_PANEL_START_X = 640 * 0.65 = 416
        RIGHT_PANEL_START_X = (int) (virtualWidth * (1.0f - RIGHT_PANEL_PERCENT));

        // 中间区域中心 X 坐标：左栏和右栏之间的区域中心
        // 例如：virtualWidth=640, leftPanelWidth=128, RIGHT_PANEL_START_X=416 → centerCenterX = 272
        centerCenterX = (leftPanelWidth + RIGHT_PANEL_START_X) / 2;

        // ==================== 计算玩家模型位置（虚拟坐标） ====================
        // 模型占据屏幕中间 20% 到 70% 的高度
        // 模型脚部 Y 坐标：虚拟高度的 70%
        MODEL_FOOT_Y = (int) (virtualHeight * 0.75f);
        // 模型头部 Y 坐标：虚拟高度的 20%
        MODEL_HEAD_Y = (int) (virtualHeight * 0.2f);
        // 模型总高度：头到脚的距离
        MODEL_HEIGHT = MODEL_FOOT_Y - MODEL_HEAD_Y;
        // 模型缩放大小：模型高度除以 1.8（renderEntityInInventory 的模型高度系数）
        MODEL_SIZE = (int) (MODEL_HEIGHT / 1.8);

        // ==================== 计算关闭动画滑动距离（虚拟坐标） ====================
        LEFT_PANEL_SLIDE_DISTANCE = (int) (virtualWidth * LEFT_PANEL_PERCENT);    // 左栏宽度
        RIGHT_PANEL_SLIDE_DISTANCE = (int) (virtualWidth * RIGHT_PANEL_PERCENT);  // 右栏宽度
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 检查关闭动画是否完成
        if (isCloseAnimationComplete()) {
            this.onClose();
            return;
        }

        // 每帧重新计算虚拟尺寸（支持窗口大小变化）
        calculateVirtualSize();

        // ==================== 应用全局缩放 ====================
        // 所有后续绘制命令都会被这个缩放影响
        // 虚拟坐标 × uiScale = 屏幕坐标
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(uiScale, uiScale, 1.0f);

        // 绘制所有内容（使用虚拟坐标）
        renderPanels(guiGraphics, mouseX, mouseY);

        // 恢复矩阵状态
        guiGraphics.pose().popPose();

        // ==================== 渲染提示框（使用屏幕坐标） ====================
        renderTooltips(guiGraphics, mouseX, mouseY);
    }

    /**
     * 获得动画播放进度（0.0 ~ 1.0）
     *
     * @return 打开时从 0 渐变到 1，关闭时从 1 渐变到 0
     */
    private float getAnimationProgress() {
        if (isClosing) {
            // 关闭动画：从 1 递减到 0
            long elapsed = Util.getMillis() - closeTime;
            float progress = 1.0f - Math.min(1.0f, (float) elapsed / CLOSE_ANIMATION_DURATION);
            return Math.max(0.0f, progress);
        } else {
            // 打开动画：从 0 递增到 1
            long elapsed = Util.getMillis() - openTime;
            return Math.min(1.0f, (float) elapsed / ANIMATION_DURATION);
        }
    }

    private float getTerminalRevealProgress() {
        long elapsed = Util.getMillis() - openTime;
        float progress = Math.min(1.0f, (float) elapsed / 520.0f);
        return 1.0f - (float) Math.pow(1.0f - progress, 3);
    }

    private void drawTerminalRevealSweep(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        float progress = getTerminalRevealProgress();
        if (progress >= 1.0f) return;
        int sweepX = x + (int) (width * progress);
        guiGraphics.fill(RenderType.gui(), sweepX - 2, y, sweepX + 1, y + height, 0x667AA8C7);
        guiGraphics.fill(RenderType.gui(), sweepX + 1, y, sweepX + 14, y + height, 0x117AA8C7);
    }

    /**
     * 绘制左右两栏，全部基于虚拟坐标计算
     *
     * 布局结构：
     * ┌──────────────┬─────────────────────┬─────────────────┐
     * │   左栏 20%   │      中间区域        │    右栏 35%     │
     * │              │                     │                 │
     * │  DreamingFish│  玩家名[幸存者]     │ Rank & Title   │
     * │              │  (模型头上方)        │                 │
     * │  (边框动画)  │   玩家3D模型         │   属性进度条    │
     * │              │   (20%-60%)          │                 │
     * │              │   等级圆+经验值      │                 │
     * │              │   (60%-100%)         │                 │
     * └──────────────┴─────────────────────┴─────────────────┘
     */
    private void renderPanels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        // 动画进度
        float animationProgress = getAnimationProgress();

        // ==================== 计算关闭动画的横向偏移量（虚拟坐标） ====================
        int leftOffsetX = 0;   // 左栏向左偏移量
        int rightOffsetX = 0;  // 右栏向右偏移量

        if (isClosing) {
            // 关闭时：左栏向左滑出，右栏向右滑出
            float closeProgress = 1.0f - animationProgress;  // 0 → 1
            // 缓动函数：1 - (1 - t)^3，让动画更自然
            closeProgress = 1.0f - (float) Math.pow(1.0f - closeProgress, 3);

            // 计算偏移量（随动画进度增加）
            leftOffsetX = (int) (closeProgress * LEFT_PANEL_SLIDE_DISTANCE);    // 向左
            rightOffsetX = (int) (closeProgress * RIGHT_PANEL_SLIDE_DISTANCE);  // 向右
        }

        // ==================== 平板外壳（参考新闻流界面的悬浮设备感） ====================
        int tabletMarginX = Math.max(12, virtualWidth / 28);
        int tabletMarginY = Math.max(10, virtualHeight / 14);
        int tabletX = tabletMarginX;
        int tabletY = tabletMarginY;
        int tabletWidth = virtualWidth - tabletMarginX * 2;
        int tabletHeight = virtualHeight - tabletMarginY * 2;
        int headerHeight = 34;

        guiGraphics.fill(RenderType.gui(), 0, 0, virtualWidth, virtualHeight, 0x66000000);
        drawSoftRect(guiGraphics, tabletX + 4, tabletY + 5, tabletWidth, tabletHeight, 6, TABLET_SHADOW_COLOR, 0x00000000);
        drawSoftRect(guiGraphics, tabletX, tabletY, tabletWidth, tabletHeight, 6, TABLET_SHELL_COLOR, 0x66526372);
        drawSoftRect(guiGraphics, tabletX + 6, tabletY + 6, tabletWidth - 12, headerHeight, 4, TABLET_HEADER_COLOR, 0x224A5A68);
        drawSoftRect(guiGraphics, tabletX + 6, tabletY + headerHeight, tabletWidth - 12, tabletHeight - headerHeight - 6, 4, TABLET_CONTENT_COLOR, 0x22384755);

        if (selectedLeftButtonIndex >= 0) {
            drawText(guiGraphics, "<", tabletX + 18, tabletY + 15, 0xFFE8EDF6);
        }

        if (selectedLeftButtonIndex < 0) {
            drawBrandTitle(guiGraphics, tabletX + 18, tabletY + 15);
        } else {
            String pageTitle = profileRankManagerOpen
                ? "Rank 管理"
                : (selectedLeftButtonIndex < LEFT_BUTTON_NAMES.length
                    ? LEFT_BUTTON_NAMES[selectedLeftButtonIndex]
                    : "梦屿终端");
            drawText(guiGraphics, pageTitle, tabletX + 34, tabletY + 15, TABLET_TEXT_COLOR);
        }

        int onlinePlayers = mc.player != null && mc.player.connection != null ?
            mc.player.connection.getOnlinePlayers().size() : 0;
        drawTopDateTime(guiGraphics, tabletX + 6, tabletY + 12, tabletWidth - 12, 16);
        drawTabletStatusBar(guiGraphics, tabletX + tabletWidth - 148, tabletY + 12, 130, 16, onlinePlayers, 20, 20.0f);

        boolean revealContent = !skipAnimation && !isClosing && getAnimationProgress() < 1.0f;
        if (revealContent) {
            int revealRight = tabletX + 6 + (int) ((tabletWidth - 12) * getTerminalRevealProgress());
            guiGraphics.enableScissor(
                (int) ((tabletX + 6) * uiScale),
                (int) ((tabletY + headerHeight) * uiScale),
                (int) (revealRight * uiScale),
                (int) ((tabletY + tabletHeight - 6) * uiScale)
            );
        }

        if (selectedLeftButtonIndex < 0) {
            renderModuleDashboard(guiGraphics, mouseX, mouseY, tabletX + 16, tabletY + headerHeight + 16,
                tabletWidth - 32, tabletHeight - headerHeight - 28);
            if (revealContent) {
                guiGraphics.disableScissor();
                drawTerminalRevealSweep(guiGraphics, tabletX + 6, tabletY + headerHeight, tabletWidth - 12, tabletHeight - headerHeight - 6);
            }
            return;
        }

        renderModulePage(guiGraphics, mouseX, mouseY, tabletX + 16, tabletY + headerHeight + 14,
            tabletWidth - 32, tabletHeight - headerHeight - 24);
        if (revealContent) {
            guiGraphics.disableScissor();
            drawTerminalRevealSweep(guiGraphics, tabletX + 6, tabletY + headerHeight, tabletWidth - 12, tabletHeight - headerHeight - 6);
        }
    }

    private void renderModulePage(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y, int width, int height) {
        switch (selectedLeftButtonIndex) {
            case 0 -> {
                if (profileRankManagerOpen) {
                    renderRankManagementPage(guiGraphics, mouseX, mouseY, x, y, width, height);
                } else {
                    renderProfilePage(guiGraphics, mouseX, mouseY, x, y, width, height);
                }
            }
            case 2 -> renderNoticeFeedPage(guiGraphics, mouseX, mouseY, x, y, width, height);
            case 3 -> renderStoryTaskPage(guiGraphics, mouseX, mouseY, x, y, width, height);
            case 1 -> renderHelpTerminalPage(guiGraphics, mouseX, mouseY, x, y, width, height);
            case 4 -> renderPlaceholderPage(guiGraphics, x, y, width, height, "玩家与排行", "排行面板还在接入数据");
            case 5 -> renderPlaceholderPage(guiGraphics, x, y, width, height, "服务器成就", "成就面板还在接入数据");
            case 6 -> renderMarketPage(guiGraphics, x, y, width, height);
            case 8 -> renderHistoryPage(guiGraphics, x, y, width, height);
            case NPC_MESSAGE_PAGE_INDEX -> renderNpcMessagePage(guiGraphics, mouseX, mouseY, x, y, width, height);
            default -> renderPlaceholderPage(guiGraphics, x, y, width, height, LEFT_BUTTON_NAMES[selectedLeftButtonIndex], "该模块将打开独立界面");
        }
    }

    private void renderProfilePage(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y, int width, int height) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        int heroW = Math.max(138, Math.min(176, (int) (width * 0.30f)));
        int gap = 10;
        int contentH = height;
        int rightX = x + heroW + gap;
        int rightW = width - heroW - gap;

        drawSoftRect(guiGraphics, x, y, heroW, contentH, 3, 0xFF121B24, 0xFF344555);
        drawText(guiGraphics, "PLAYER", x + 12, y + 12, TABLET_MUTED_TEXT_COLOR);

        float infection = PlayerInfectionManager.getCurrentInfectionClient(player);
        String status = infection >= 100 ? "感染者" : "幸存者";
        int statusColor = infection >= 100 ? 0xFFFF6677 : 0xFF50D890;
        String playerName = player.getScoreboardName();
        if (mc.font.width(playerName) > heroW - 24) {
            playerName = ServerScreenUI_RendererUtils.truncateText(mc.font, playerName, heroW - 24 - mc.font.width("...")) + "...";
        }
        drawText(guiGraphics, playerName, x + 12, y + 28, TABLET_TEXT_COLOR);
        drawSoftRect(guiGraphics, x + 12, y + 44, mc.font.width(status) + 14, 15, 2, 0x332E3C49, 0x224A5A68);
        drawText(guiGraphics, status, x + 19, y + 48, statusColor);

        PlayerData playerData = ClientCacheManager.getPlayerData(player.getUUID());
        long registrationTime = 0L;
        long totalPlayTime = 0L;
        if (playerData != null) {
            registrationTime = playerData.getRegistrationTime() > 0 ? playerData.getRegistrationTime() : playerData.getLastLoginTime();
            totalPlayTime = playerData.getTotalPlayTime();
        }
        if (playerData != null && playerData.isZhuiguangMember()) {
            String organizationIdentity = "逐光会成员";
            int organizationBadgeWidth = Math.min(heroW - 24, mc.font.width(organizationIdentity) + 14);
            drawSoftRect(guiGraphics, x + 12, y + 63, organizationBadgeWidth, 15, 2, 0x332E3C49, 0x224A5A68);
            drawText(guiGraphics, organizationIdentity, x + 19, y + 67, 0xFFFFC857);
        }
        Rank rank = PlayerRankManager.getPlayerRankClient(player);
        Title title = PlayerTitleManager.getPlayerTitleClient(player);

        int modelSize = Math.max(38, Math.min(56, contentH / 4));
        pageRenderer.renderPlayerModel(guiGraphics, 10, mouseX, mouseY, x + heroW / 2, y + contentH - 28, modelSize, uiScale);

        int level = PlayerLevelManager.getPlayerLevelClient(player);
        long exp = PlayerLevelManager.getPlayerExperienceClient(player);
        long expNeed = PlayerLevelManager.getExperienceNeededForNextLevelClient(player);
        float progress = PlayerLevelManager.getExperienceProgressClient(player);

        int levelCardH = 44;
        drawSoftRect(guiGraphics, rightX, y, rightW, levelCardH, 2, TABLET_CARD_COLOR, TABLET_CARD_BORDER_COLOR);
        guiGraphics.fill(RenderType.gui(), rightX, y, rightX + 4, y + levelCardH, 0xFFFFB84D);
        drawText(guiGraphics, "LEVEL " + level, rightX + 12, y + 7, TABLET_TEXT_COLOR);
        drawText(guiGraphics, "EXP " + exp + "/" + expNeed, rightX + 12, y + 23, TABLET_MUTED_TEXT_COLOR);
        drawProgressBar(guiGraphics, rightX + 110, y + 22, rightW - 124, 6, progress, 0xFFFFB84D);

        int tileY = y + levelCardH + gap;
        int tileW = (rightW - gap * 2) / 3;
        int tileH = 42;
        EconomyTerminalClientCache.Snapshot economySnapshot = EconomyTerminalClientCache.get();
        String balanceText = !economySnapshot.loaded()
            ? "同步中..."
            : (economySnapshot.available() && economySnapshot.compatible()
                ? String.valueOf(economySnapshot.balance())
                : "未接入");
        String territoryText = !economySnapshot.loaded()
            ? "同步中..."
            : (economySnapshot.available() && economySnapshot.compatible()
                ? economySnapshot.ownedTerritoryCount() + " 个"
                : "未接入");
        drawStatTile(guiGraphics, rightX, tileY, tileW, tileH, "梦鱼币", balanceText, 0xFFFFC857);
        drawStatTile(guiGraphics, rightX + tileW + gap, tileY, tileW, tileH, "领地", territoryText, 0xFF48C78E);
        drawStatTile(guiGraphics, rightX + (tileW + gap) * 2, tileY, tileW, tileH, "探索", String.valueOf(ClientCacheManager.getExploredBiomesCount(player.getUUID())), 0xFF4FC3F7);
        int[] goldBoxClick = pageRenderer.getGoldBoxClick();
        goldBoxClick[0] = rightX;
        goldBoxClick[1] = tileY;
        goldBoxClick[2] = rightX + tileW;
        goldBoxClick[3] = tileY + tileH;
        int[] territoryBoxClick = pageRenderer.getTerritoryBoxClick();
        territoryBoxClick[0] = rightX + tileW + gap;
        territoryBoxClick[1] = tileY;
        territoryBoxClick[2] = rightX + tileW * 2 + gap;
        territoryBoxClick[3] = tileY + tileH;

        tileY += tileH + gap;
        drawStatTile(guiGraphics, rightX, tileY, tileW, tileH, "蓝图", String.valueOf(ClientCacheManager.getUnlockedRecipesCount(player.getUUID())), 0xFF8EA7FF);
        int rankTileX = rightX + tileW + gap;
        float virtualMouseX = mouseX / uiScale;
        float virtualMouseY = mouseY / uiScale;
        boolean rankTileHovered = virtualMouseX >= rankTileX && virtualMouseX <= rankTileX + tileW
            && virtualMouseY >= tileY && virtualMouseY <= tileY + tileH;
        drawStatTile(guiGraphics, rankTileX, tileY, tileW, tileH, "Rank",
            rank == RankRegistry.NO_RANK ? "未装配" : rank.getRankName(), 0xFF000000 | rank.getRankColor(), rankTileHovered);
        rankBoxClickX1 = rankTileX;
        rankBoxClickY1 = tileY;
        rankBoxClickX2 = rankTileX + tileW;
        rankBoxClickY2 = tileY + tileH;
        drawStatTile(guiGraphics, rightX + (tileW + gap) * 2, tileY, tileW, tileH, "称号", title.getTitleName(), 0xFF000000 | title.getColor());

        int attrY = tileY + tileH + gap;
        int attrBottom = y + contentH;
        int attrH = Math.max(0, attrBottom - attrY);
        drawSoftRect(guiGraphics, rightX, attrY, rightW, attrH, 2, TABLET_CARD_COLOR, TABLET_CARD_BORDER_COLOR);
        boolean infected = ClientCacheManager.isInfected(player.getUUID());
        float respawnPoint = ClientCacheManager.getRespawnPoint(player.getUUID());
        int deathCost = infected ? 20 : 5;
        int respawnTimes = (int) (respawnPoint / deathCost);
        String respawnWarning = respawnTimes <= 0 ? "警告: 无法复活" : (respawnTimes < 2 ? "警告: 复活不足" : "");
        drawText(guiGraphics, "身体状态", rightX + 12, attrY + 9, TABLET_TEXT_COLOR);
        if (!respawnWarning.isEmpty()) {
            drawText(guiGraphics, respawnWarning, rightX + rightW - mc.font.width(respawnWarning) - 12, attrY + 9,
                respawnTimes <= 0 ? 0xFFFF6677 : 0xFFFFC857);
        }
        int innerX = rightX + 12;
        int innerY = attrY + 28;
        int columnGap = 10;
        int rowAreaH = Math.max(0, attrY + attrH - 8 - innerY);
        int rowGap = rowAreaH < 62 ? 4 : 6;
        int rowH = Math.max(8, Math.min(18, (rowAreaH - rowGap * 2) / 3));
        int barW = (rightW - 24 - columnGap) / 2;
        int strength = PlayerStrengthClientSync.getCurrentStrengthClient(player);
        int maxStrength = PlayerStrengthClientSync.getMaxStrengthClient(player);
        if (maxStrength <= 0) maxStrength = 100;
        float courage = PlayerCourageManager.getCurrentCourageClient(player);
        float maxCourage = PlayerCourageManager.getMaxCourageClient(player);
        if (maxCourage <= 0) maxCourage = 100;
        drawMiniBar(guiGraphics, innerX, innerY, barW, rowH, "生命", player.getHealth() / player.getMaxHealth(),
            String.format("%.0f/%.0f", player.getHealth(), player.getMaxHealth()), 0xFFFF6677);
        drawMiniBar(guiGraphics, innerX + barW + columnGap, innerY, barW, rowH, "饥饿", player.getFoodData().getFoodLevel() / 20.0f,
            player.getFoodData().getFoodLevel() + "/20", 0xFFFFC857);
        drawMiniBar(guiGraphics, innerX, innerY + rowH + rowGap, barW, rowH, "体力", (float) strength / maxStrength,
            strength + "/" + maxStrength, 0xFF50D890);
        drawMiniBar(guiGraphics, innerX + barW + columnGap, innerY + rowH + rowGap, barW, rowH, "勇气", courage / maxCourage,
            String.format("%.0f/%.0f", courage, maxCourage), 0xFFB58BFF);
        drawMiniBar(guiGraphics, innerX, innerY + (rowH + rowGap) * 2, barW, rowH, "感染", infection / 100.0f,
            String.format("%.1f/100", infection), 0xFF8B5CF6);
        drawMiniBar(guiGraphics, innerX + barW + columnGap, innerY + (rowH + rowGap) * 2, barW, rowH, "分裂", respawnPoint / 100.0f,
            String.format("%.1f/%d次", respawnPoint, respawnTimes), infected ? 0xFFFF6677 : 0xFF7AA8C7);
    }

    private void renderRankManagementPage(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                          int x, int y, int width, int height) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        float virtualMouseX = mouseX / uiScale;
        float virtualMouseY = mouseY / uiScale;
        Rank equippedRank = PlayerRankManager.getPlayerRankClient(player);
        Set<String> ownedRankNames = PlayerRankManager.getOwnedRankNamesClient(player);

        displayedRankOptions.clear();
        rankOptionClickAreas.clear();
        displayedRankOptions.add(RankRegistry.NO_RANK);
        for (Rank registeredRank : RankRegistry.getRegisteredRanks()) {
            if (registeredRank != RankRegistry.NO_RANK && ownedRankNames.contains(registeredRank.getRankName())) {
                displayedRankOptions.add(registeredRank);
            }
        }

        int headerH = 48;
        drawSoftRect(guiGraphics, x, y, width, headerH, 2, 0xFF202B36, TABLET_CARD_BORDER_COLOR);
        guiGraphics.fill(RenderType.gui(), x, y, x + 4, y + headerH, 0xFFFFAA00);
        drawText(guiGraphics, "Rank 装配", x + 14, y + 9, TABLET_TEXT_COLOR);
        String equippedText = equippedRank == RankRegistry.NO_RANK ? "当前未装配" : "当前装配: " + equippedRank.getRankName();
        drawText(guiGraphics, equippedText, x + 14, y + 27,
            equippedRank == RankRegistry.NO_RANK ? TABLET_MUTED_TEXT_COLOR : 0xFF000000 | equippedRank.getRankColor());
        String ownedCountText = "已拥有 " + ownedRankNames.size();
        drawText(guiGraphics, ownedCountText, x + width - mc.font.width(ownedCountText) - 14, y + 19, TABLET_MUTED_TEXT_COLOR);

        int listY = y + headerH + 10;
        int columns = width >= 400 ? 2 : 1;
        int gap = 10;
        int cardW = (width - gap * (columns - 1)) / columns;
        int cardH = 58;

        for (int index = 0; index < displayedRankOptions.size(); index++) {
            Rank rank = displayedRankOptions.get(index);
            int column = index % columns;
            int row = index / columns;
            int cardX = x + column * (cardW + gap);
            int cardY = listY + row * (cardH + gap);
            if (cardY + cardH > y + height) break;

            boolean equipped = equippedRank.getRankName().equals(rank.getRankName());
            boolean hovered = virtualMouseX >= cardX && virtualMouseX <= cardX + cardW
                && virtualMouseY >= cardY && virtualMouseY <= cardY + cardH;
            int accentColor = rank == RankRegistry.NO_RANK ? 0xFF8B96A3 : 0xFF000000 | rank.getRankColor();
            int backgroundColor = equipped ? 0xFF2D3741 : (hovered ? TABLET_CARD_HOVER_COLOR : TABLET_CARD_COLOR);
            drawSoftRect(guiGraphics, cardX, cardY, cardW, cardH, 2, backgroundColor,
                equipped || hovered ? accentColor : TABLET_CARD_BORDER_COLOR);
            guiGraphics.fill(RenderType.gui(), cardX, cardY, cardX + 4, cardY + cardH, accentColor);

            String rankName = rank == RankRegistry.NO_RANK ? "未装配" : rank.getRankName();
            drawText(guiGraphics, rankName, cardX + 14, cardY + 12, accentColor);
            drawText(guiGraphics, rank == RankRegistry.NO_RANK ? "隐藏聊天与展示中的 Rank" : "已拥有",
                cardX + 14, cardY + 34, TABLET_MUTED_TEXT_COLOR);

            String actionText = equipped ? "已装配" : "装配";
            int actionW = mc.font.width(actionText) + 14;
            int actionX = cardX + cardW - actionW - 10;
            drawSoftRect(guiGraphics, actionX, cardY + 19, actionW, 20, 2,
                equipped ? 0x332E9D68 : 0x3324313E, equipped ? 0xFF50D890 : accentColor);
            drawCenteredText(guiGraphics, actionText, actionX + actionW / 2, cardY + 25,
                equipped ? 0xFF50D890 : TABLET_TEXT_COLOR);

            rankOptionClickAreas.add(new int[]{cardX, cardY, cardX + cardW, cardY + cardH});
        }
    }

    /**
     * 返回玩家当前已经进入的故事阶段。
     *
     * <p>故事定义可以提前配置后续阶段，但未推进前不应在终端中提前剧透。
     * 当前阶段及其之前的阶段属于已开放档案；若当前阶段标记尚未同步，
     * 安全地只展示第一阶段。服务端定义和数据仍完整保留，推进阶段后会自动出现。</p>
     */
    private java.util.Map<Integer, com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData>
            getPlayerVisibleStoryStages(
                    java.util.Map<Integer, com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData> allStages) {
        if (allStages == null || allStages.isEmpty()) {
            return java.util.Map.of();
        }

        int currentStageNumber = allStages.values().stream()
                .filter(stage -> stage != null && stage.isCurrentStage())
                .mapToInt(com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData::getStageNumber)
                .filter(number -> number > 0)
                .min()
                .orElse(1);
        int maximumVisibleStage = Math.max(1, currentStageNumber);

        java.util.List<java.util.Map.Entry<Integer,
                com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData>> entries =
                new java.util.ArrayList<>(allStages.entrySet());
        entries.removeIf(entry -> entry == null
                || entry.getValue() == null
                || entry.getValue().getStageNumber() <= 0
                || entry.getValue().getStageNumber() > maximumVisibleStage);
        entries.sort(java.util.Comparator
                .comparingInt((java.util.Map.Entry<Integer,
                        com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData> entry) ->
                        entry.getValue().getStageNumber())
                .thenComparingInt(entry -> entry.getKey() == null
                        ? Integer.MAX_VALUE : entry.getKey()));

        java.util.Map<Integer, com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData> visible =
                new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<Integer,
                com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData> entry : entries) {
            visible.put(entry.getKey(), entry.getValue());
        }

        // 对旧客户端/损坏快照兜底：至少保留编号最小的一个阶段，避免故事页完全空白。
        if (visible.isEmpty()) {
            java.util.Map.Entry<Integer,
                    com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData> first = allStages.entrySet().stream()
                    .filter(entry -> entry != null && entry.getValue() != null)
                    .min(java.util.Comparator.comparingInt(entry -> entry.getValue().getStageNumber()))
                    .orElse(null);
            if (first != null) {
                visible.put(first.getKey(), first.getValue());
            }
        }
        return java.util.Collections.unmodifiableMap(visible);
    }

    private void renderStoryTaskPage(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y, int width, int height) {
        float virtualMouseX = mouseX / uiScale;
        float virtualMouseY = mouseY / uiScale;
        var storyStages = getPlayerVisibleStoryStages(ClientCacheManager.getStoryStages());
        if (selectedStageId != null && storyStages.values().stream()
                .noneMatch(stage -> stage != null && selectedStageId.equals(stage.getStageId()))) {
            // 世界阶段推进或客户端快照更新后，回收已经不可见的旧阶段选择，避免剧透/空白页。
            selectedStageId = null;
            stageScrollOffset = 0L;
            taskScrollOffset = 0L;
        }
        int totalStoryTasks = storyStages.values().stream()
                .filter(java.util.Objects::nonNull)
                .mapToInt(stage -> stage.getTasks() == null ? 0 : stage.getTasks().size())
                .sum();
        drawText(guiGraphics, "故事进展", x + 4, y + 2, TABLET_TEXT_COLOR);
        String overview = storyStages.size() + " 个阶段 · " + totalStoryTasks + " 项世界任务";
        drawText(guiGraphics, overview, x + width - mc.font.width(overview) - 4, y + 2,
            totalStoryTasks > 0 ? TABLET_MUTED_TEXT_COLOR : 0xFFFFC857);

        // 个人引导已经作为任务并入世界故事，不再占用单独的顶部标签。
        int[] taskTabArea = pageRenderer.getTaskTabArea();
        taskTabArea[0] = taskTabArea[1] = taskTabArea[2] = taskTabArea[3] = 0;
        if (width > 8) {
            guiGraphics.fill(RenderType.gui(), x + 4, y + 20, x + width - 4, y + 21, 0x334A5A68);
        }

        // 旧状态字段只为兼容已有快照；当前故事页始终是统一任务视图。
        taskShowServerTasks = true;
        int contentY = y + 28;
        int contentH = Math.max(0, y + height - contentY);
        if (selectedStageId == null) {
            renderStoryStageList(guiGraphics, virtualMouseX, virtualMouseY, x, contentY, width, contentH, storyStages);
        } else {
            renderStoryStageTasks(guiGraphics, virtualMouseX, virtualMouseY, x, contentY, width, contentH, storyStages);
        }
    }

    private void drawSegmentButton(GuiGraphics guiGraphics, int x, int y, int width, int height, String text,
                                   boolean active, float mouseX, float mouseY, int accentColor) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        int bg = active ? 0xFF2D4050 : (hovered ? TABLET_CARD_HOVER_COLOR : 0xFF202B36);
        int border = active || hovered ? accentColor : TABLET_CARD_BORDER_COLOR;
        drawSoftRect(guiGraphics, x, y, width, height, 2, bg, border);
        drawCenteredText(guiGraphics, text, x + width / 2, y + 7, active ? TABLET_TEXT_COLOR : TABLET_MUTED_TEXT_COLOR);
    }

    private void renderRespawnSummary(GuiGraphics guiGraphics, LocalPlayer player, int x, int y, int width) {
        boolean infected = ClientCacheManager.isInfected(player.getUUID());
        float respawnPoint = ClientCacheManager.getRespawnPoint(player.getUUID());
        int deathCost = infected ? 20 : 5;
        int respawnTimes = (int) (respawnPoint / deathCost);
        int color = infected ? 0xFFFF6677 : (respawnTimes <= 0 ? 0xFFFF6677 : 0xFF50D890);
        drawText(guiGraphics, infected ? "感染状态: 感染者" : "感染状态: 幸存者", x, y, color);
        String respawnText = "分裂 " + String.format("%.1f/100", respawnPoint) + "  可重生 " + respawnTimes + " 次";
        if (mc.font.width(respawnText) > width) {
            respawnText = ServerScreenUI_RendererUtils.truncateText(mc.font, respawnText, width - mc.font.width("...")) + "...";
        }
        drawText(guiGraphics, respawnText, x, y + 14, TABLET_MUTED_TEXT_COLOR);
    }

    private void renderStoryStageList(GuiGraphics guiGraphics, float mouseX, float mouseY, int x, int y, int width, int height,
                                      java.util.Map<Integer, com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData> storyStages) {
        stageClickAreas.clear();
        storyStageNavClickAreas.clear();

        // 左栏采用梦屿广播的“阶段筛选”样式：窄面板、强调色竖线和紧凑按钮，
        // 让故事页与广播页保持同一套阅读节奏。
        int sidebarW = Math.max(132, Math.min(154, (int) (width * 0.25f)));
        if (width < 300) {
            sidebarW = Math.max(112, width / 3);
        }
        int listX = x + sidebarW + 10;
        int listW = Math.max(1, width - sidebarW - 10);
        drawSoftRect(guiGraphics, x, y, sidebarW, height, 3, 0xFF18232D, TABLET_CARD_BORDER_COLOR);
        guiGraphics.fill(RenderType.gui(), x, y + 6, x + 3, y + Math.max(6, height - 6), 0xFF4FC3F7);
        drawText(guiGraphics, "任务目录", x + 12, y + 9, TABLET_TEXT_COLOR);
        drawText(guiGraphics, "世界故事", x + 12, y + 24, 0xFF4FC3F7);
        guiGraphics.fill(RenderType.gui(), x + 10, y + 39, x + sidebarW - 10, y + 40, 0x334FC3F7);

        java.util.List<Integer> sortedStageIds = new java.util.ArrayList<>(storyStages.keySet());
        java.util.Collections.sort(sortedStageIds);
        var currentStage = storyStages.values().stream()
            .filter(java.util.Objects::nonNull)
            .filter(com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData::isCurrentStage)
            .findFirst()
            .orElse(null);

        int navY = y + 48;
        final int navHeight = 22;
        final int navGap = 5;
        for (Integer stageKey : sortedStageIds) {
            var stage = storyStages.get(stageKey);
            if (stage == null || navY + navHeight > y + height - 78) {
                break;
            }
            String stageLabel = getNoticeStageButtonLabel(stage);
            boolean selected = currentStage != null
                && java.util.Objects.equals(currentStage.getStageId(), stage.getStageId());
            drawSegmentButton(guiGraphics, x + 8, navY, sidebarW - 16, navHeight,
                fitText(stageLabel, sidebarW - 28), selected, mouseX, mouseY, 0xFF4FC3F7);
            storyStageNavClickAreas.add(new StageClickArea(
                x + 8, navY, x + sidebarW - 8, navY + navHeight, stage.getStageId()));
            navY += navHeight + navGap;
        }

        int taskCount = storyStages.values().stream()
            .filter(java.util.Objects::nonNull)
            .mapToInt(stage -> stage.getTasks() == null ? 0 : stage.getTasks().size())
            .sum();
        int progressLabelY = Math.max(y + 58, y + height - 63);
        drawText(guiGraphics, "推进方式", x + 12, progressLabelY, TABLET_TEXT_COLOR);
        drawText(guiGraphics, fitText("完成玩家比例共同推进故事", sidebarW - 24), x + 12,
            progressLabelY + 16, 0xFF78D6A3);
        drawText(guiGraphics, GuidanceClientCache.isLoaded() ? "线索已并入任务卡" : "线索同步中…",
            x + 12, progressLabelY + 32, TABLET_MUTED_TEXT_COLOR);
        String archiveCount = storyStages.size() + " 个阶段 · " + taskCount + " 项任务";
        drawText(guiGraphics, fitText(archiveCount, sidebarW - 24), x + 12, y + height - 17,
            TABLET_MUTED_TEXT_COLOR);

        drawText(guiGraphics, "世界故事任务", listX, y + 1, TABLET_TEXT_COLOR);
        String stageHint = currentStage == null ? "阶段尚未同步" : "当前 · " + currentStage.getStageName();
        drawText(guiGraphics, fitText(stageHint, Math.max(24, listW - mc.font.width("世界故事任务") - 18)),
            listX + Math.max(0, listW - mc.font.width(stageHint)), y + 1, TABLET_MUTED_TEXT_COLOR);

        int gap = 8;
        int stageTop = y + 19;
        int bottom = y + height;
        // 故事阶段列表占满内容区；个人引导不再另起一块，而是在阶段任务卡中显示。
        int stageHeight = Math.max(56, bottom - stageTop);
        stageHeight = Math.min(stageHeight, Math.max(0, bottom - stageTop));

        int minCardWidth = 132;
        int columns = listW >= minCardWidth * 2 + gap ? 2 : 1;
        int cardW = columns == 2 ? Math.max(minCardWidth, (listW - gap) / 2) : listW;
        int cardH = columns == 2 ? 66 : 64;
        int rowsVisible = Math.max(1, (stageHeight + gap) / (cardH + gap));
        int totalRows = (sortedStageIds.size() + columns - 1) / columns;
        int maxRowOffset = Math.max(0, totalRows - rowsVisible);
        stageScrollOffset = Math.max(0L, Math.min(maxRowOffset, stageScrollOffset));
        int startIndex = Math.min(sortedStageIds.size(), (int) stageScrollOffset * columns);
        int visible = Math.min(Math.max(0, sortedStageIds.size() - startIndex), rowsVisible * columns);

        stageGridColumns = columns;
        stageGridVisibleRows = rowsVisible;
        stageGridCardHeight = cardH;
        stageGridGap = gap;

        int firstY = stageTop;
        int lastY = stageTop;
        for (int i = 0; i < visible; i++) {
            int stageIndex = startIndex + i;
            var stage = storyStages.get(sortedStageIds.get(stageIndex));
            if (stage == null) continue;
            int row = i / columns;
            int column = i % columns;
            int cardX = listX + column * (cardW + gap);
            int cardY = stageTop + row * (cardH + gap);
            boolean hovered = mouseX >= cardX && mouseX <= cardX + cardW
                && mouseY >= cardY && mouseY <= cardY + cardH;
            renderStoryStageCard(guiGraphics, cardX, cardY, cardW, cardH, stage, hovered,
                stage.isCurrentStage());
            stageClickAreas.add(new StageClickArea(cardX, cardY, cardX + cardW, cardY + cardH,
                stage.getStageId()));
            lastY = Math.max(lastY, cardY + cardH);
        }

        int[] stageArea = pageRenderer.getStageClickArea();
        if (stageClickAreas.isEmpty()) {
            stageArea[0] = stageArea[1] = stageArea[2] = stageArea[3] = 0;
        } else {
            stageArea[0] = listX;
            stageArea[1] = firstY;
            stageArea[2] = listX + listW;
            stageArea[3] = lastY;
        }
        if (storyStages.isEmpty()) {
            drawSoftRect(guiGraphics, listX, stageTop, listW, Math.min(70, stageHeight), 2,
                TABLET_CARD_COLOR, TABLET_CARD_BORDER_COLOR);
            drawCenteredText(guiGraphics, "暂无故事阶段", listX + listW / 2,
                stageTop + Math.min(36, stageHeight / 2), TABLET_MUTED_TEXT_COLOR);
        }

    }

    private void renderStoryStageCard(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                      com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData stage,
                                      boolean hovered, boolean current) {
        int bg = hovered ? TABLET_CARD_HOVER_COLOR : TABLET_CARD_COLOR;
        drawSoftRect(guiGraphics, x, y, width, height, 2, bg, hovered ? PANEL_BORDER_COLOR : TABLET_CARD_BORDER_COLOR);
        guiGraphics.fill(RenderType.gui(), x, y, x + 4, y + height, PANEL_BORDER_COLOR);

        String name = stage.getStageName();
        int badgeWidth = current ? mc.font.width("当前") + 12 : 0;
        int nameWidth = Math.max(24, width - 24 - badgeWidth);
        if (mc.font.width(name) > nameWidth) {
            name = ServerScreenUI_RendererUtils.truncateText(mc.font, name,
                nameWidth - mc.font.width("...")) + "...";
        }
        drawText(guiGraphics, name, x + 12, y + 8, TABLET_TEXT_COLOR);
        if (current) {
            drawSoftRect(guiGraphics, x + width - badgeWidth - 8, y + 6, badgeWidth, 16,
                2, 0x333C8F72, 0xFF50D890);
            drawCenteredText(guiGraphics, "当前", x + width - badgeWidth / 2 - 8, y + 10,
                0xFF50D890);
        }

        String desc = stage.getStageDescription();
        if (mc.font.width(desc) > width - 24) {
            desc = ServerScreenUI_RendererUtils.truncateText(mc.font, desc, width - 24 - mc.font.width("...")) + "...";
        }
        drawText(guiGraphics, desc, x + 12, y + 24, TABLET_MUTED_TEXT_COLOR);

        int total = stage.getTotalTaskCount();
        int personalResolved = stage.getClientPlayerCompletedTaskCount();
        int globalPercent = Math.round(Math.max(0.0f, Math.min(1.0f,
            stage.getGlobalProgressPercentage())) * 100.0f);
        int progressGap = 8;
        int progressWidth = Math.min(118, Math.max(24, (width - 24 - progressGap) / 2));
        int personalX = x + 12 + progressWidth + progressGap;
        drawText(guiGraphics, "全服 " + globalPercent + "%", x + 12, y + 40, TABLET_MUTED_TEXT_COLOR);
        drawText(guiGraphics, "我的 " + personalResolved + "/" + total, personalX, y + 40, TABLET_MUTED_TEXT_COLOR);
        drawProgressBar(guiGraphics, x + 12, y + 51, progressWidth, 4,
            stage.getGlobalProgressPercentage(), PANEL_BORDER_COLOR);
        drawProgressBar(guiGraphics, personalX, y + 51, progressWidth, 4,
            stage.getClientPlayerProgressPercentage(), 0xFF50D890);
    }

    private void renderStoryStageTasks(GuiGraphics guiGraphics, float mouseX, float mouseY, int x, int y, int width, int height,
                                       java.util.Map<Integer, com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData> storyStages) {
        storyStageNavClickAreas.clear();
        var selectedStage = storyStages.values().stream()
            .filter(stage -> String.valueOf(stage.getStageId()).equals(selectedStageId))
            .findFirst()
            .orElse(null);
        if (selectedStage == null) {
            renderPlaceholderPage(guiGraphics, x, y, width, height, "阶段不存在", "返回故事列表后重新选择");
            return;
        }

        int backW = 34;
        drawSegmentButton(guiGraphics, x, y, backW, 22, "<", false, mouseX, mouseY, PANEL_BORDER_COLOR);
        int[] backArea = pageRenderer.getBackButtonArea();
        backArea[0] = x;
        backArea[1] = y;
        backArea[2] = x + backW;
        backArea[3] = y + 22;

        String stageProgress = "全服 " + selectedStage.getCompletedTaskCount() + "/" + selectedStage.getTotalTaskCount()
            + " · 个人 " + selectedStage.getClientPlayerCompletedTaskCount() + "/" + selectedStage.getTotalTaskCount();
        int stageProgressX = x + width - mc.font.width(stageProgress);
        drawText(guiGraphics, fitDashboardText(selectedStage.getStageName(),
            Math.max(24, stageProgressX - (x + 44) - 8)), x + 44, y + 7, TABLET_TEXT_COLOR);
        drawText(guiGraphics, stageProgress, stageProgressX, y + 7, TABLET_MUTED_TEXT_COLOR);
        java.util.List<StoryTaskData> tasks = selectedStage.getTasks();
        if (tasks == null) tasks = new java.util.ArrayList<>();
        List<GuidanceViewData> guidanceEntries = GuidanceClientCache.getEntries();

        int cardY = y + 34;
        int gap = 8;
        int maxTaskOffset = Math.max(0, tasks.size() - VISIBLE_TASKS);
        taskScrollOffset = Math.max(0L, Math.min(maxTaskOffset, taskScrollOffset));
        int visible = Math.min(VISIBLE_TASKS, Math.max(0, tasks.size() - (int) taskScrollOffset));
        int firstY = cardY;
        int lastY = cardY;
        for (int i = 0; i < visible; i++) {
            int taskIndex = i + (int) taskScrollOffset;
            var task = tasks.get(taskIndex);
            int currentY = cardY + i * (TASK_CARD_HEIGHT + gap);
            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= currentY && mouseY <= currentY + TASK_CARD_HEIGHT;
            renderTaskTerminalCard(
                guiGraphics,
                x,
                currentY,
                width,
                TASK_CARD_HEIGHT,
                task,
                findGuidanceForTask(task, guidanceEntries),
                hovered);
            lastY = currentY + TASK_CARD_HEIGHT;
        }

        if (tasks.isEmpty()) {
            drawSoftRect(guiGraphics, x, cardY, width, 70, 2,
                    TABLET_CARD_COLOR, TABLET_CARD_BORDER_COLOR);
            drawCenteredText(guiGraphics, "当前阶段还没有已发布任务", x + width / 2,
                    cardY + 28, TABLET_MUTED_TEXT_COLOR);
            lastY = cardY + 70;
        }

        int[] taskArea = pageRenderer.getTaskClickArea();
        taskArea[0] = x;
        taskArea[1] = firstY;
        taskArea[2] = x + width;
        taskArea[3] = lastY;
    }

    private void renderTaskTerminalCard(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int width,
            int height,
            StoryTaskData task,
            GuidanceViewData guidance,
            boolean hovered) {
        boolean personalTask = task.isPersonalTask();
        boolean playerFinished = task.isClientPlayerFinished();
        boolean worldCompleted = task.isCompleted();
        boolean worldPublished = task.isTaskState();
        boolean failed = task.isFailed();
        int accent = 0xFF7AA8C7;
        int stateColor = failed
                ? 0xFFE05B62
                : playerFinished
                ? 0xFF50D890
                : worldCompleted
                ? 0xFF9BB8C9
                : accent;
        drawSoftRect(guiGraphics, x, y, width, height, 2,
                hovered ? TABLET_CARD_HOVER_COLOR : TABLET_CARD_COLOR,
                hovered ? stateColor : TABLET_CARD_BORDER_COLOR);
        guiGraphics.fill(RenderType.gui(), x, y, x + 4, y + height, stateColor);

        // 个人部分与世界部分是两层状态：个人完成后仍可能等待其他玩家，
        // 世界任务未发布时也要明确告诉玩家它只是尚未解锁，而不是卡死。
        String state;
        if (failed) {
            state = "FAILED";
        } else if (personalTask && worldCompleted) {
            state = "世界已推进";
        } else if (personalTask && playerFinished) {
            state = "已完成 · 等待全员";
        } else if (personalTask && !worldPublished) {
            state = "个人任务";
        } else if (personalTask) {
            state = "个人进行中";
        } else if (playerFinished) {
            state = "你已完成";
        } else if (worldCompleted) {
            state = "世界已完成";
        } else {
            state = "进行中";
        }
        drawText(guiGraphics, state, x + 12, y + 8, stateColor);
        int stateWidth = mc.font.width(state);
        int titleX = x + 12 + stateWidth + 12;
        String displayTitle = task.getTaskName();
        int titleLimit = Math.max(24, width - (titleX - x) - 12);
        if (mc.font.width(displayTitle) > titleLimit) {
            displayTitle = ServerScreenUI_RendererUtils.truncateText(
                    mc.font, displayTitle, Math.max(1, titleLimit - mc.font.width("..."))) + "...";
        }
        drawText(guiGraphics, displayTitle, titleX, y + 8, TABLET_TEXT_COLOR);

        String displayContent = guidance != null && !guidance.content().isBlank()
                ? guidance.content()
                : task.getTaskContent();
        int contentLimit = Math.max(24, width - 24);
        if (mc.font.width(displayContent) > contentLimit) {
            displayContent = ServerScreenUI_RendererUtils.truncateText(
                    mc.font, displayContent, Math.max(1, contentLimit - mc.font.width("..."))) + "...";
        }
        drawText(guiGraphics, displayContent, x + 12, y + 27, TABLET_MUTED_TEXT_COLOR);

        if (guidance != null && height >= 56) {
            String guidanceText = "⌖ " + guidance.title();
            if (guidance.hasLocation() && !guidance.locationLabel().isBlank()) {
                guidanceText += " · " + guidance.locationLabel();
            }
            int guidanceColor = guidance.status() == GuidanceEntry.Status.ACTIVE
                    ? 0xFF78D6A3
                    : TABLET_MUTED_TEXT_COLOR;
            String doneText;
            if (personalTask) {
                int expected = task.getPersonalExpectedPlayerCount();
                if (expected > 0) {
                    int completed = Math.min(task.getFinishedPlayerCount(), expected);
                    doneText = "全服 " + completed + "/" + expected;
                } else {
                    doneText = "个人待分配";
                }
            } else {
                doneText = task.getFinishedPlayerCount() > 0
                        ? task.getFinishedPlayerCount() + "人在场"
                        : "";
            }
            int doneWidth = doneText.isBlank() ? 0 : mc.font.width(doneText) + 8;
            int guidanceWidth = Math.max(24, width - 24 - doneWidth);
            drawText(guiGraphics, fitText(guidanceText, guidanceWidth),
                    x + 12, y + 44, guidanceColor);
            if (!doneText.isBlank()) {
                drawText(guiGraphics, doneText, x + width - mc.font.width(doneText) - 12,
                        y + 44, TABLET_MUTED_TEXT_COLOR);
            }
        } else if (personalTask || task.getFinishedPlayerCount() > 0) {
            String doneText;
            if (personalTask) {
                int expected = task.getPersonalExpectedPlayerCount();
                doneText = expected > 0
                        ? "全服 " + Math.min(task.getFinishedPlayerCount(), expected) + "/" + expected
                        : "个人待分配";
            } else {
                doneText = task.getFinishedPlayerCount() + "人在场";
            }
            drawText(guiGraphics, doneText, x + width - mc.font.width(doneText) - 12,
                    y + height - 11, TABLET_MUTED_TEXT_COLOR);
        }
    }

    /**
     * 找到与世界任务对应的最新个人线索。活动线索优先，
     * 这样完成前后任务卡会自然显示当前需要执行的那一步。
     */
    private GuidanceViewData findGuidanceForTask(StoryTaskData task, List<GuidanceViewData> entries) {
        if (task == null || entries == null || entries.isEmpty()) {
            return null;
        }
        List<String> linkedIds = guidanceDefinitionIdsForTask(task.getTaskKey());
        return entries.stream()
                .filter(java.util.Objects::nonNull)
                .filter(entry -> task.getTaskKey().equals(entry.definitionId())
                        || linkedIds.contains(entry.definitionId()))
                .sorted(java.util.Comparator
                        .comparing((GuidanceViewData entry) -> entry.status() != GuidanceEntry.Status.ACTIVE)
                        .thenComparing(java.util.Comparator.comparingLong(
                                GuidanceViewData::createdAtEpochMillis).reversed()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 内置开场任务与个人线索的显示关联。关联只影响客户端呈现，
     * 世界任务的完成仍由服务端剧情事件写入。
     */
    private List<String> guidanceDefinitionIdsForTask(String taskKey) {
        if ("dreamingfishcore:opening/settle_in_abydos".equals(taskKey)) {
            return List.of("dreamingfishcore:guidance/opening/travel_to_abydos");
        }
        if ("dreamingfishcore:opening/meet_baizhi".equals(taskKey)) {
            return List.of("dreamingfishcore:guidance/opening/talk_to_baizhi");
        }
        if ("dreamingfishcore:opening/choose_zhuiguang_path".equals(taskKey)) {
            return List.of(
                    "dreamingfishcore:guidance/opening/contact_zhoucen",
                    "dreamingfishcore:guidance/opening/choose_membership");
        }
        if ("dreamingfishcore:opening/build_zhuiguang_base".equals(taskKey)) {
            return List.of(
                    "dreamingfishcore:guidance/opening/build_zhuiguang_base",
                    "dreamingfishcore:guidance/watch_zhuiguang_foundation");
        }
        return List.of();
    }

    private void renderNpcMessagePage(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int width,
            int height) {
        conversationClickAreas.clear();
        messageClickAreas.clear();
        messageReplyClickAreas.clear();
        float virtualMouseX = mouseX / uiScale;
        float virtualMouseY = mouseY / uiScale;

        if (!NpcMessageClientCache.isLoaded()) {
            renderPlaceholderPage(guiGraphics, x, y, width, height, "正在同步 NPC 私信", "终端正在建立私人频道");
            return;
        }

        List<NpcConversationViewData> conversations = NpcMessageClientCache.getConversations();
        if (conversations.isEmpty()) {
            drawSoftRect(guiGraphics, x, y, width, height, 3, 0xFF202B36, TABLET_CARD_BORDER_COLOR);
            drawCenteredText(guiGraphics, "暂无 NPC 私信", x + width / 2, y + height / 2 - 14, TABLET_TEXT_COLOR);
            drawCenteredText(guiGraphics, "与剧情 NPC 交谈，或等待对方向终端发送消息", x + width / 2,
                    y + height / 2 + 6, TABLET_MUTED_TEXT_COLOR);
            return;
        }

        NpcConversationViewData selected = conversations.stream()
                .filter(conversation -> conversation.npcId() == selectedMessageNpcId)
                .findFirst()
                .orElse(null);
        if (selected == null) {
            selected = conversations.get(0);
            selectedMessageNpcId = selected.npcId();
            messageThreadScrollOffset = 0L;
            clearNpcMessageDetail();
            lastReadRequestedNpcId = -1;
        }
        if (selected.unreadCount() > 0 && lastReadRequestedNpcId != selected.npcId()) {
            lastReadRequestedNpcId = selected.npcId();
            DreamingFishCore_NetworkManager.sendToServer(new Packet_NpcMessageReadRequest(selected.npcId()));
        } else if (selected.unreadCount() == 0 && lastReadRequestedNpcId == selected.npcId()) {
            // 服务端已经确认已读；允许同一会话稍后到达的新消息再次触发已读请求。
            lastReadRequestedNpcId = -1;
        }

        int gap = 10;
        int listWidth = Math.max(126, Math.min(176, (int) (width * 0.30f)));
        int threadX = x + listWidth + gap;
        int threadWidth = width - listWidth - gap;

        drawSoftRect(guiGraphics, x, y, listWidth, height, 3, 0xFF18232D, TABLET_CARD_BORDER_COLOR);
        drawText(guiGraphics, "会话", x + 10, y + 9, TABLET_TEXT_COLOR);
        drawText(guiGraphics, conversations.size() + " 位 NPC", x + listWidth - mc.font.width(conversations.size() + " 位 NPC") - 10,
                y + 9, TABLET_MUTED_TEXT_COLOR);

        int listY = y + 28;
        int conversationCardHeight = 42;
        int conversationGap = 6;
        int visibleConversations = Math.max(1, (height - 34 + conversationGap) / (conversationCardHeight + conversationGap));
        int maxConversationOffset = Math.max(0, conversations.size() - visibleConversations);
        conversationScrollOffset = Math.max(0L, Math.min(maxConversationOffset, conversationScrollOffset));
        int visibleCount = Math.min(visibleConversations, conversations.size() - (int) conversationScrollOffset);
        for (int index = 0; index < visibleCount; index++) {
            NpcConversationViewData conversation = conversations.get(index + (int) conversationScrollOffset);
            int cardY = listY + index * (conversationCardHeight + conversationGap);
            boolean active = conversation.npcId() == selected.npcId();
            boolean hovered = virtualMouseX >= x + 7 && virtualMouseX <= x + listWidth - 7
                    && virtualMouseY >= cardY && virtualMouseY <= cardY + conversationCardHeight;
            int accent = conversation.unreadCount() > 0 ? 0xFF8CCEFF : 0xFF60798D;
            drawSoftRect(guiGraphics, x + 7, cardY, listWidth - 14, conversationCardHeight, 3,
                    active ? 0xFF294052 : (hovered ? TABLET_CARD_HOVER_COLOR : TABLET_CARD_COLOR),
                    active || hovered ? accent : TABLET_CARD_BORDER_COLOR);
            drawText(guiGraphics, fitText(conversation.npcName(), listWidth - 54), x + 15, cardY + 7, TABLET_TEXT_COLOR);
            drawText(guiGraphics, fitText(latestMessagePreview(conversation), listWidth - 30), x + 15, cardY + 24,
                    TABLET_MUTED_TEXT_COLOR);
            if (conversation.unreadCount() > 0) {
                String unread = String.valueOf(Math.min(99, conversation.unreadCount()));
                drawSoftRect(guiGraphics, x + listWidth - 30, cardY + 7, 17, 14, 7, 0xFF4F9FCC, 0x00000000);
                drawCenteredText(guiGraphics, unread, x + listWidth - 22, cardY + 10, 0xFFFFFFFF);
            }
            conversationClickAreas.add(new ConversationClickArea(
                    x + 7, cardY, x + listWidth - 7, cardY + conversationCardHeight, conversation.npcId()));
        }
        setArea(conversationListArea, x, listY, x + listWidth, y + height);

        drawSoftRect(guiGraphics, threadX, y, threadWidth, height, 3, 0xFF202B36, TABLET_CARD_BORDER_COLOR);
        drawText(guiGraphics, selected.npcName(), threadX + 12, y + 8, TABLET_TEXT_COLOR);
        String relation = selected.relationName() + " · 好感 " + selected.favorability();
        drawText(guiGraphics, relation, threadX + threadWidth - mc.font.width(relation) - 12, y + 8, 0xFF78D6A3);

        NpcMessageViewData openedMessage = findMessageByRecordId(selected, selectedMessageRecordId);
        if (isNpcMessageDetailOpen() && openedMessage == null) {
            clearNpcMessageDetail();
        } else if (openedMessage != null) {
            setArea(messageThreadArea, 0, 0, 0, 0);
            renderNpcMessageDetail(guiGraphics, virtualMouseX, virtualMouseY, selected, openedMessage,
                    threadX, y + 28, threadWidth, height - 28);
            return;
        }

        NpcMessageViewData replySource = findLatestReplySource(selected.messages());
        int replyAreaHeight = replySource == null ? 24 : 34;
        int messagesY = y + 28;
        int messagesHeight = Math.max(40, height - 28 - replyAreaHeight);
        int messageCardHeight = 48;
        int messageGap = 6;
        int visibleMessages = Math.max(1, (messagesHeight + messageGap) / (messageCardHeight + messageGap));
        int maxMessageOffset = Math.max(0, selected.messages().size() - visibleMessages);
        messageThreadScrollOffset = Math.max(0L, Math.min(maxMessageOffset, messageThreadScrollOffset));
        int endExclusive = selected.messages().size() - (int) messageThreadScrollOffset;
        int startInclusive = Math.max(0, endExclusive - visibleMessages);
        int drawIndex = 0;
        for (int index = startInclusive; index < endExclusive; index++) {
            NpcMessageViewData message = selected.messages().get(index);
            boolean outgoing = message.direction() == NpcMessageRecord.Direction.PLAYER_TO_NPC;
            int bubbleWidth = Math.max(96, (int) (threadWidth * 0.78f));
            int bubbleX = outgoing ? threadX + threadWidth - bubbleWidth - 10 : threadX + 10;
            int bubbleY = messagesY + drawIndex * (messageCardHeight + messageGap);
            int accent = outgoing ? 0xFF78D6A3 : 0xFF8CCEFF;
            boolean hovered = virtualMouseX >= bubbleX && virtualMouseX <= bubbleX + bubbleWidth
                    && virtualMouseY >= bubbleY && virtualMouseY <= bubbleY + messageCardHeight;
            drawSoftRect(guiGraphics, bubbleX, bubbleY, bubbleWidth, messageCardHeight, 3,
                    hovered ? (outgoing ? 0xFF2B4740 : 0xFF294352) : (outgoing ? 0xFF243A35 : 0xFF233541),
                    hovered ? accent : 0xFF334A59);
            guiGraphics.fill(RenderType.gui(), outgoing ? bubbleX + bubbleWidth - 3 : bubbleX, bubbleY + 5,
                    outgoing ? bubbleX + bubbleWidth : bubbleX + 3, bubbleY + messageCardHeight - 5, accent);
            String author = outgoing ? "你" : selected.npcName();
            drawText(guiGraphics, author, bubbleX + 10, bubbleY + 6, accent);
            drawText(guiGraphics, formatHistoryDate(message.sentAtEpochMillis()),
                    bubbleX + bubbleWidth - mc.font.width(formatHistoryDate(message.sentAtEpochMillis())) - 9,
                    bubbleY + 6, TABLET_MUTED_TEXT_COLOR);
            drawText(guiGraphics, fitText(message.content().replace('\n', ' '), bubbleWidth - 38),
                    bubbleX + 10, bubbleY + 25, TABLET_TEXT_COLOR);
            drawText(guiGraphics, ">", bubbleX + bubbleWidth - 14, bubbleY + 25,
                    hovered ? accent : TABLET_MUTED_TEXT_COLOR);
            messageClickAreas.add(new MessageClickArea(
                    bubbleX, bubbleY, bubbleX + bubbleWidth, bubbleY + messageCardHeight, message.recordId()));
            drawIndex++;
        }
        setArea(messageThreadArea, threadX, messagesY, threadX + threadWidth, messagesY + messagesHeight);

        int replyY = y + height - replyAreaHeight + 5;
        if (replySource == null) {
            drawText(guiGraphics, "当前没有可用回复；新的选项会随关系与消息出现。", threadX + 12, replyY + 4,
                    TABLET_MUTED_TEXT_COLOR);
        } else {
            int replyCount = Math.min(3, replySource.availableReplies().size());
            int replyGap = 6;
            int replyWidth = Math.max(44, (threadWidth - 20 - replyGap * (replyCount - 1)) / replyCount);
            int replyX = threadX + 10;
            for (int index = 0; index < replyCount; index++) {
                var reply = replySource.availableReplies().get(index);
                boolean hovered = virtualMouseX >= replyX && virtualMouseX <= replyX + replyWidth
                        && virtualMouseY >= replyY && virtualMouseY <= replyY + 22;
                drawSoftRect(guiGraphics, replyX, replyY, replyWidth, 22, 3,
                        hovered ? 0xFF31516A : 0xFF263B4B, hovered ? 0xFF8CCEFF : 0xFF3B586B);
                drawCenteredText(guiGraphics, fitText(reply.text(), replyWidth - 12), replyX + replyWidth / 2,
                        replyY + 7, TABLET_TEXT_COLOR);
                messageReplyClickAreas.add(new MessageReplyClickArea(
                        replyX, replyY, replyX + replyWidth, replyY + 22,
                        replySource.recordId(), reply.id()));
                replyX += replyWidth + replyGap;
            }
        }
    }

    /** 在会话面板内打开单封私信，完整正文采用像素滚动，避免长消息只剩一行摘要。 */
    private void renderNpcMessageDetail(
            GuiGraphics guiGraphics,
            float virtualMouseX,
            float virtualMouseY,
            NpcConversationViewData conversation,
            NpcMessageViewData message,
            int x,
            int y,
            int width,
            int height) {
        int backX = x + 10;
        int backY = y;
        int backWidth = 46;
        int backHeight = 20;
        boolean backHovered = virtualMouseX >= backX && virtualMouseX <= backX + backWidth
                && virtualMouseY >= backY && virtualMouseY <= backY + backHeight;
        drawSoftRect(guiGraphics, backX, backY, backWidth, backHeight, 3,
                backHovered ? TABLET_CARD_HOVER_COLOR : 0xFF263746,
                backHovered ? 0xFF8CCEFF : TABLET_CARD_BORDER_COLOR);
        drawCenteredText(guiGraphics, "< 返回", backX + backWidth / 2, backY + 6, TABLET_TEXT_COLOR);
        setArea(messageDetailBackArea, backX, backY, backX + backWidth, backY + backHeight);

        String subject = message.subject().isBlank() ? "私信详情" : message.subject();
        drawText(guiGraphics, fitText(subject, Math.max(24, width - backWidth - 42)),
                backX + backWidth + 8, y + 2, TABLET_TEXT_COLOR);

        boolean outgoing = message.direction() == NpcMessageRecord.Direction.PLAYER_TO_NPC;
        int accent = outgoing ? 0xFF78D6A3 : 0xFF8CCEFF;
        String route = outgoing ? "你  →  " + conversation.npcName() : conversation.npcName() + "  →  你";
        drawText(guiGraphics, route, x + 12, y + 25, accent);
        String sentAt = formatHistoryDate(message.sentAtEpochMillis());
        drawText(guiGraphics, sentAt, x + width - mc.font.width(sentAt) - 12, y + 25,
                TABLET_MUTED_TEXT_COLOR);

        boolean hasReplies = message.direction() == NpcMessageRecord.Direction.NPC_TO_PLAYER
                && !message.replied() && !message.availableReplies().isEmpty();
        int footerHeight = hasReplies ? 38 : 24;
        int bodyY = y + 39;
        int bodyHeight = Math.max(38, height - 39 - footerHeight);
        drawSoftRect(guiGraphics, x + 10, bodyY, width - 20, bodyHeight, 3,
                0xFF182630, TABLET_CARD_BORDER_COLOR);
        drawText(guiGraphics, "正文", x + 20, bodyY + 7, accent);

        int textX = x + 20;
        int textWidth = Math.max(24, width - 40);
        int hintY = bodyY + bodyHeight - mc.font.lineHeight - 6;
        int viewportTop = bodyY + 22;
        int viewportBottom = Math.max(viewportTop, hintY - 5);
        int viewportHeight = Math.max(0, viewportBottom - viewportTop);
        int lineHeight = mc.font.lineHeight + 2;
        List<FormattedCharSequence> lines = mc.font.split(Component.literal(message.content()), textWidth);
        int contentHeight = lines.size() * lineHeight;
        messageDetailMaxScroll = Math.max(0, contentHeight - viewportHeight);
        messageDetailScrollOffset = Math.max(0L,
                Math.min(messageDetailMaxScroll, messageDetailScrollOffset));
        setArea(messageDetailContentArea, textX, viewportTop, x + width - 20, viewportBottom);

        if (viewportHeight > 0 && !lines.isEmpty()) {
            int pixelOffset = (int) messageDetailScrollOffset;
            int firstLine = Math.min(lines.size(), pixelOffset / lineHeight);
            int lineOffset = pixelOffset % lineHeight;
            int visibleLines = Math.max(1, (viewportHeight + lineHeight - 1) / lineHeight + 1);
            int lastLine = Math.min(lines.size(), firstLine + visibleLines);
            guiGraphics.enableScissor(
                    (int) (textX * uiScale),
                    (int) (viewportTop * uiScale),
                    (int) ((x + width - 20) * uiScale),
                    (int) (viewportBottom * uiScale));
            for (int lineIndex = firstLine; lineIndex < lastLine; lineIndex++) {
                int lineY = viewportTop + (lineIndex - firstLine) * lineHeight - lineOffset;
                if (lineY + mc.font.lineHeight >= viewportTop && lineY <= viewportBottom) {
                    guiGraphics.drawString(mc.font, lines.get(lineIndex), textX, lineY,
                            TABLET_TEXT_COLOR, false);
                }
            }
            guiGraphics.disableScissor();
        }

        if (messageDetailMaxScroll > 0 && messageDetailScrollOffset < messageDetailMaxScroll) {
            String scrollHint = "滚轮查看更多";
            drawText(guiGraphics, scrollHint, x + width - mc.font.width(scrollHint) - 20, hintY,
                    TABLET_MUTED_TEXT_COLOR);
        }

        int footerY = y + height - footerHeight + 6;
        if (!hasReplies) {
            String status = outgoing ? "已发送" : (message.replied() ? "你已回复这封私信" : "这封私信无需回复");
            drawText(guiGraphics, status, x + 12, footerY + 3, TABLET_MUTED_TEXT_COLOR);
            return;
        }

        int replyCount = Math.min(3, message.availableReplies().size());
        int replyGap = 6;
        int replyWidth = Math.max(44, (width - 20 - replyGap * (replyCount - 1)) / replyCount);
        int replyX = x + 10;
        for (int index = 0; index < replyCount; index++) {
            var reply = message.availableReplies().get(index);
            boolean hovered = virtualMouseX >= replyX && virtualMouseX <= replyX + replyWidth
                    && virtualMouseY >= footerY && virtualMouseY <= footerY + 22;
            drawSoftRect(guiGraphics, replyX, footerY, replyWidth, 22, 3,
                    hovered ? 0xFF31516A : 0xFF263B4B, hovered ? 0xFF8CCEFF : 0xFF3B586B);
            drawCenteredText(guiGraphics, fitText(reply.text(), replyWidth - 12), replyX + replyWidth / 2,
                    footerY + 7, TABLET_TEXT_COLOR);
            messageReplyClickAreas.add(new MessageReplyClickArea(
                    replyX, footerY, replyX + replyWidth, footerY + 22, message.recordId(), reply.id()));
            replyX += replyWidth + replyGap;
        }
    }

    private NpcMessageViewData findLatestReplySource(List<NpcMessageViewData> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            NpcMessageViewData message = messages.get(index);
            if (message.direction() == NpcMessageRecord.Direction.NPC_TO_PLAYER
                    && !message.replied()
                    && !message.availableReplies().isEmpty()) {
                return message;
            }
        }
        return null;
    }

    private NpcMessageViewData findMessageByRecordId(
            NpcConversationViewData conversation,
            String recordId) {
        if (conversation == null || recordId == null || recordId.isBlank()) {
            return null;
        }
        for (NpcMessageViewData message : conversation.messages()) {
            if (recordId.equals(message.recordId())) {
                return message;
            }
        }
        return null;
    }

    private String latestMessagePreview(NpcConversationViewData conversation) {
        if (conversation.messages().isEmpty()) {
            return "暂无消息";
        }
        NpcMessageViewData latest = conversation.messages().get(conversation.messages().size() - 1);
        String prefix = latest.direction() == NpcMessageRecord.Direction.PLAYER_TO_NPC ? "你: " : "";
        return prefix + latest.content().replace('\n', ' ');
    }

    private int drawWrappedText(
            GuiGraphics guiGraphics,
            String text,
            int x,
            int y,
            int width,
            int color,
            int maximumLines) {
        List<FormattedCharSequence> lines = mc.font.split(Component.literal(text == null ? "" : text), Math.max(16, width));
        int count = Math.min(Math.max(0, maximumLines), lines.size());
        for (int index = 0; index < count; index++) {
            guiGraphics.drawString(mc.font, lines.get(index), x, y + index * 11, color, false);
        }
        return y + count * 11;
    }

    private void setArea(int[] area, int x1, int y1, int x2, int y2) {
        area[0] = x1;
        area[1] = y1;
        area[2] = x2;
        area[3] = y2;
    }

    private boolean isInside(int[] area, double x, double y) {
        return area != null && area.length >= 4
                && x >= area[0] && x <= area[2]
                && y >= area[1] && y <= area[3];
    }

    private record ConversationClickArea(int x1, int y1, int x2, int y2, int npcId) {
        private boolean contains(double x, double y) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2;
        }
    }

    private record MessageClickArea(int x1, int y1, int x2, int y2, String messageRecordId) {
        private boolean contains(double x, double y) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2;
        }
    }

    private record MessageReplyClickArea(
            int x1,
            int y1,
            int x2,
            int y2,
            String messageRecordId,
            String replyId) {
        private boolean contains(double x, double y) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2;
        }
    }

    private record StageClickArea(int x1, int y1, int x2, int y2, String stageId) {
        private boolean contains(double x, double y) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2;
        }
    }

    private record NoticeStageClickArea(int x1, int y1, int x2, int y2, String stageId) {
        private boolean contains(double x, double y) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2;
        }
    }

    private record HelpTopicClickArea(int x1, int y1, int x2, int y2, int topicIndex) {
        private boolean contains(double x, double y) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2;
        }
    }

    private record HelpTopic(
        String number,
        String title,
        String subtitle,
        int accent,
        String[] metrics,
        String[] facts,
        String tipTitle,
        String tipText) {
    }

    private void renderNoticeFeedPage(
            GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y, int width, int height) {
        double virtualMouseX = mouseX / uiScale;
        double virtualMouseY = mouseY / uiScale;
        clearNoticeInteractionAreas();

        // 标题和分类切换共用一行，避免把广播页的首屏空间浪费在一整行 tab 上。
        String pageTitle = "梦屿广播";
        String subtitle = "剧情消息与服务器公告";
        int titleX = x + 4;
        drawText(guiGraphics, pageTitle, titleX, y + 1, TABLET_TEXT_COLOR);
        drawText(guiGraphics, subtitle, titleX, y + 15, TABLET_MUTED_TEXT_COLOR);

        int titleBlockWidth = Math.max(mc.font.width(pageTitle), mc.font.width(subtitle));
        int tabStartX = titleX + titleBlockWidth + 16;
        int tabAvailableWidth = x + width - tabStartX - 4;
        int headerBottom;
        if (tabAvailableWidth >= 128) {
            headerBottom = renderNoticeCategoryTabs(
                guiGraphics, tabStartX, y, tabAvailableWidth, virtualMouseX, virtualMouseY);
        } else {
            // 极窄窗口的安全回退：分类仍可用，但放到标题下方，避免与标题重叠。
            headerBottom = renderNoticeCategoryTabs(
                guiGraphics, titleX, y + 31, Math.max(1, width - 8), virtualMouseX, virtualMouseY);
        }

        int contentY = Math.min(y + height, headerBottom + 8);
        if (contentY > y && width > 8) {
            guiGraphics.fill(RenderType.gui(), x + 4, contentY - 4,
                x + width - 4, contentY - 3, 0x334A5A68);
        }
        int stageWidth = 0;
        if (selectedNoticeTab == 0) {
            // 只绘制当前世界已经开放的阶段；未来阶段定义仍保留在服务端快照中。
            stageWidth = renderNoticeStageFilters(
                guiGraphics, contentY, x, width, Math.max(0, y + height - contentY),
                virtualMouseX, virtualMouseY);
        } else {
            noticeStageFilterWidth = 0;
            noticeStageFilterHeight = 0;
        }

        int contentGap = stageWidth > 0 ? 10 : 0;
        int contentX = x + stageWidth + contentGap;
        int contentWidth = Math.max(1, width - stageWidth - contentGap);
        int contentHeight = Math.max(0, y + height - contentY);
        int cardGap = 8;
        int cardH = NOTICE_FEED_CARD_HEIGHT;
        int columns = contentWidth > 430 ? 2 : 1;
        int cardW = Math.max(1, (contentWidth - cardGap * (columns - 1)) / columns);

        if (isNoticeDetailOpen()) {
            NoticeData detail = findNoticeById(selectedNoticeDetailId);
            boolean detailMatchesStage = detail != null
                && (!detail.isGameNotice()
                    || selectedNoticeStageId == null
                    || selectedNoticeStageId.isEmpty()
                    || selectedNoticeStageId.equals(detail.getStoryStageId()));
            if (detail == null || !isClientVisibleNotice(detail) || !detailMatchesStage) {
                clearNoticeDetail();
            } else {
                renderNoticeDetailPage(guiGraphics, virtualMouseX, virtualMouseY,
                    detail, contentX, contentY, contentWidth, contentHeight);
                return;
            }
        }

        List<NoticeData> currentNotices = getNoticesForSelectedTab();
        int maxRows = Math.max(1, (contentHeight + cardGap) / (cardH + cardGap));
        int maxCards = Math.max(1, maxRows * columns);
        noticeVisibleCardCapacity = maxCards;
        int maxScrollOffset = Math.max(0, currentNotices.size() - maxCards);
        noticeScrollOffset = Math.max(0, Math.min(maxScrollOffset, noticeScrollOffset));
        int visible = Math.min(currentNotices.size() - (int) noticeScrollOffset, maxCards);
        if (currentNotices.isEmpty()) {
            int emptyHeight = contentHeight <= 0 ? 0 : Math.min(Math.max(76, contentHeight), 96);
            if (emptyHeight <= 0) {
                return;
            }
            drawSoftRect(guiGraphics, contentX, contentY, contentWidth, emptyHeight, 3,
                TABLET_CARD_COLOR, TABLET_CARD_BORDER_COLOR);
            String emptyText = selectedNoticeTab == 0
                && selectedNoticeStageId != null
                && !selectedNoticeStageId.isEmpty()
                ? "该阶段暂无游戏公告"
                : (selectedNoticeTab == 0 ? "暂无梦屿广播" : "暂无服务器公告");
            drawCenteredText(guiGraphics, emptyText,
                contentX + contentWidth / 2, contentY + emptyHeight / 2 - 4,
                TABLET_MUTED_TEXT_COLOR);
            int[] noticeClickArea = pageRenderer.getNoticeClickArea();
            setArea(noticeClickArea, 0, 0, 0, 0);
            return;
        }

        int firstY = contentY;
        int lastY = contentY;
        for (int i = 0; i < visible; i++) {
            int index = i + (int) noticeScrollOffset;
            NoticeData notice = currentNotices.get(index);
            boolean isRead = cachedReadNoticeIds.contains(notice.getNoticeId());
            int col = i % columns;
            int row = i / columns;
            int cardX = contentX + col * (cardW + cardGap);
            int cardY = contentY + row * (cardH + cardGap);
            renderNoticeFeedCard(guiGraphics, cardX, cardY, cardW, cardH, notice, isRead);
            lastY = cardY + cardH;
        }

        int[] noticeClickArea = pageRenderer.getNoticeClickArea();
        noticeClickArea[0] = contentX;
        noticeClickArea[1] = firstY;
        noticeClickArea[2] = contentX + contentWidth;
        noticeClickArea[3] = lastY;
    }

    /** 绘制标题右侧的公告分类按钮，按钮宽度由标签和数量文本自然计算。 */
    private int renderNoticeCategoryTabs(
            GuiGraphics guiGraphics, int x, int y, int availableWidth,
            double virtualMouseX, double virtualMouseY) {
        final int tabHeight = 24;
        final int tabGap = 6;
        final String[] labels = {"梦屿广播", "服务器公告"};
        final int[] naturalWidths = new int[labels.length];
        for (int tab = 0; tab < labels.length; tab++) {
            NoticeCategory category = tab == 0 ? NoticeCategory.GAME : NoticeCategory.MAINTENANCE;
            int total = countNotices(category);
            int unread = countUnreadNotices(category);
            String countText = unread > 0 ? total + " 条 · " + unread + " 未读" : total + " 条";
            naturalWidths[tab] = mc.font.width(labels[tab]) + mc.font.width(countText) + 28;
        }

        int safeWidth = Math.max(1, availableWidth);
        int naturalTotalWidth = naturalWidths[0] + naturalWidths[1] + tabGap;
        // 只有两枚按钮都能保留足够的可读宽度时才横排；窄窗口自动改为纵排。
        boolean stack = safeWidth < 140 || safeWidth < naturalTotalWidth;
        // 横排时将整组按钮靠右放置，留出标题左侧的呼吸空间并对齐内容边界。
        int cursorX = x + (!stack ? Math.max(0, safeWidth - naturalTotalWidth) : 0);
        int maxBottom = y + tabHeight;
        for (int tab = 0; tab < labels.length; tab++) {
            NoticeCategory category = tab == 0 ? NoticeCategory.GAME : NoticeCategory.MAINTENANCE;
            int total = countNotices(category);
            int unread = countUnreadNotices(category);
            String countText = unread > 0 ? total + " 条 · " + unread + " 未读" : total + " 条";
            int tabWidth = stack
                ? Math.min(naturalWidths[tab], safeWidth)
                : naturalWidths[tab];
            tabWidth = Math.max(1, tabWidth);
            int tabX = stack ? x : cursorX;
            // 横排时两个按钮共享同一条基线；只有窄窗口的纵排模式才递增 Y。
            int tabY = stack ? y + tab * (tabHeight + tabGap) : y;
            int[] tabArea = noticeTabClickAreas[tab];
            setArea(tabArea, tabX, tabY, tabX + tabWidth, tabY + tabHeight);

            boolean selected = selectedNoticeTab == tab;
            boolean hovered = virtualMouseX >= tabX && virtualMouseX <= tabX + tabWidth
                && virtualMouseY >= tabY && virtualMouseY <= tabY + tabHeight;
            drawSoftRect(guiGraphics, tabX, tabY, tabWidth, tabHeight, 3,
                selected ? TABLET_CARD_COLOR : (hovered ? TABLET_CARD_HOVER_COLOR : 0xFF18232D),
                selected || hovered ? 0xFF4FC3F7 : TABLET_CARD_BORDER_COLOR);
            // 选中态已经由圆角边框和背景表达，不再绘制铺到边缘的矩形下划线，
            // 避免把按钮底部的两个圆角切成直角。

            int innerWidth = Math.max(1, tabWidth - 18);
            int countWidth = Math.min(mc.font.width(countText), Math.max(1, innerWidth / 2));
            String shownCount = fitNoticeText(countText, countWidth);
            int shownCountWidth = Math.min(mc.font.width(shownCount), innerWidth);
            int labelWidth = Math.max(1, innerWidth - shownCountWidth - 4);
            String shownLabel = fitNoticeText(labels[tab], labelWidth);
            drawText(guiGraphics, shownLabel, tabX + 9, tabY + 8,
                selected ? TABLET_TEXT_COLOR : TABLET_MUTED_TEXT_COLOR);
            drawText(guiGraphics, shownCount,
                Math.max(tabX + 9, tabX + tabWidth - shownCountWidth - 9), tabY + 8,
                unread > 0 ? 0xFFFFC857 : TABLET_MUTED_TEXT_COLOR);
            maxBottom = Math.max(maxBottom, tabY + tabHeight);
            if (!stack) {
                cursorX = tabX + tabWidth + tabGap;
            }
        }
        return maxBottom;
    }

    private void renderNoticeDetailPage(
            GuiGraphics guiGraphics,
            double virtualMouseX,
            double virtualMouseY,
            NoticeData notice,
            int x,
            int y,
            int width,
            int height) {
        int backW = 36;
        int backH = 20;
        boolean backHovered = virtualMouseX >= x && virtualMouseX <= x + backW
            && virtualMouseY >= y && virtualMouseY <= y + backH;
        drawSegmentButton(guiGraphics, x, y, backW, backH, "<", false,
            (float) virtualMouseX, (float) virtualMouseY, PANEL_BORDER_COLOR);
        if (backHovered) {
            drawSoftRect(guiGraphics, x, y, backW, backH, 2, TABLET_CARD_HOVER_COLOR, PANEL_BORDER_COLOR);
            drawCenteredText(guiGraphics, "<", x + backW / 2, y + 6, TABLET_TEXT_COLOR);
        }
        setArea(noticeDetailBackArea, x, y, x + backW, y + backH);

        String title = fitNoticeText(safeNoticeText(notice.getNoticeTitle(), "无标题"),
            Math.max(24, width - backW - 54));
        drawText(guiGraphics, title, x + backW + 10, y + 2, TABLET_TEXT_COLOR);
        String metadata;
        if (notice.isGameNotice()) {
            // 剧情日期属于后台运营元数据，不在玩家界面展开，避免破坏叙事沉浸感。
            metadata = "梦屿广播";
        } else {
            metadata = "服务器公告 · 发布时间 "
                + ServerScreenUI_RendererUtils.formatDateTime(notice.getPublishTime());
        }
        drawText(guiGraphics, fitNoticeText(metadata, Math.max(24, width - backW - 54)),
            x + backW + 10, y + 15, TABLET_MUTED_TEXT_COLOR);

        int panelY = y + 30;
        int panelHeight = Math.max(42, height - 30);
        drawSoftRect(guiGraphics, x, panelY, width, panelHeight, 3,
            0xFF121C27, TABLET_CARD_BORDER_COLOR);
        drawText(guiGraphics, "正文", x + 10, panelY + 7, 0xFF4FC3F7);

        String content = safeNoticeText(notice.getNoticeContent(), "暂无内容");
        int textX = x + 10;
        int textWidth = Math.max(24, width - 20);
        int hintY = panelY + panelHeight - mc.font.lineHeight - 6;
        int viewportTop = panelY + 22;
        int viewportBottom = Math.max(viewportTop, hintY - 5);
        int viewportHeight = Math.max(0, viewportBottom - viewportTop);
        int lineHeight = mc.font.lineHeight + 2;
        List<FormattedCharSequence> lines = mc.font.split(Component.literal(content), textWidth);
        int contentHeight = lines.size() * lineHeight;
        noticeDetailMaxScroll = Math.max(0, contentHeight - viewportHeight);
        noticeDetailScrollOffset = Math.max(0L,
            Math.min(noticeDetailMaxScroll, noticeDetailScrollOffset));
        setArea(noticeDetailContentArea, textX, viewportTop, x + width - 10, viewportBottom);

        if (viewportHeight > 0 && !lines.isEmpty()) {
            int pixelOffset = (int) noticeDetailScrollOffset;
            int firstLine = Math.min(lines.size(), pixelOffset / lineHeight);
            int lineOffset = pixelOffset % lineHeight;
            int visibleLines = Math.max(1, (viewportHeight + lineHeight - 1) / lineHeight + 1);
            int lastLine = Math.min(lines.size(), firstLine + visibleLines);
            guiGraphics.enableScissor(
                (int) (textX * uiScale),
                (int) (viewportTop * uiScale),
                (int) ((x + width - 10) * uiScale),
                (int) (viewportBottom * uiScale));
            for (int lineIndex = firstLine; lineIndex < lastLine; lineIndex++) {
                int lineY = viewportTop + (lineIndex - firstLine) * lineHeight - lineOffset;
                if (lineY + mc.font.lineHeight >= viewportTop && lineY <= viewportBottom) {
                    guiGraphics.drawString(mc.font, lines.get(lineIndex), textX, lineY,
                        TABLET_TEXT_COLOR, false);
                }
            }
            guiGraphics.disableScissor();
        }

        // 公告在进入详情页的瞬间就记为已查看；滚轮只负责阅读较长正文，不参与剧情判定。
        String hint = noticeDetailMaxScroll > 0 && noticeDetailScrollOffset < noticeDetailMaxScroll
            ? "已查看 · 可滚轮浏览全文"
            : "已查看";
        drawText(guiGraphics, hint, x + width - mc.font.width(hint) - 10, hintY,
            TABLET_MUTED_TEXT_COLOR);
    }

    /** 绘制游戏公告左侧的阶段快捷按钮，并返回侧栏宽度。 */
    private int renderNoticeStageFilters(
            GuiGraphics guiGraphics, int y, int x, int width, int height,
            double virtualMouseX, double virtualMouseY) {
        noticeStageClickAreas.clear();
        noticeStageFilterWidth = 0;
        noticeStageFilterHeight = 0;
        if (selectedNoticeTab != 0) {
            return 0;
        }

        List<com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData> stages =
            getNoticeStageOptions();
        if (stages.isEmpty()) {
            return 0;
        }
        ensureNoticeStageSelection(stages);

        final int buttonHeight = 24;
        final int buttonGap = 8;
        final int sidePadding = 10;
        final int topPadding = 9;
        final int headerHeight = 26;
        final int bottomPadding = 10;

        // 恢复最初的终端左栏比例：约占内容区五分之一，并纵向铺满剩余高度。
        int desiredSidebarWidth = Math.max(112, Math.min(136, width / 5));
        int availableSidebarWidth = Math.max(0, width - 14);
        int sidebarWidth = Math.min(desiredSidebarWidth, availableSidebarWidth);
        int sidebarHeight = Math.max(0, height);
        if (sidebarHeight == 0 || sidebarWidth <= sidePadding * 2) {
            return 0;
        }
        noticeStageFilterWidth = sidebarWidth;
        noticeStageFilterHeight = sidebarHeight;

        drawSoftRect(guiGraphics, x, y, sidebarWidth, sidebarHeight, 3,
            0xFF18232D, TABLET_CARD_BORDER_COLOR);
        int innerX = x + sidePadding;
        int innerWidth = Math.max(1, sidebarWidth - sidePadding * 2);
        drawText(guiGraphics, "故事阶段", innerX, y + topPadding, TABLET_TEXT_COLOR);
        String openLabel = "已开放";
        drawText(guiGraphics, openLabel,
            innerX + innerWidth - mc.font.width(openLabel), y + topPadding,
            0xFF78D6A3);
        guiGraphics.fill(RenderType.gui(), innerX, y + headerHeight - 1,
            innerX + innerWidth, y + headerHeight, 0x334FC3F7);

        int buttonY = y + headerHeight + 8;
        for (var stage : stages) {
            if (buttonY + buttonHeight > y + sidebarHeight - bottomPadding) {
                break;
            }
            String stageId = safeNoticeText(stage.getStageId(), "");
            String label = getNoticeStageButtonLabel(stage);
            int naturalWidth = mc.font.width(label) + 20;
            int buttonWidth = Math.max(1, Math.min(innerWidth, naturalWidth));
            int buttonX = innerX + (innerWidth - buttonWidth) / 2;
            boolean selected = stageId.equals(selectedNoticeStageId);
            drawSegmentButton(guiGraphics, buttonX, buttonY, buttonWidth, buttonHeight,
                fitNoticeText(label, buttonWidth - 14), selected,
                (float) virtualMouseX, (float) virtualMouseY, 0xFF4FC3F7);
            noticeStageClickAreas.add(new NoticeStageClickArea(
                buttonX, buttonY, buttonX + buttonWidth,
                buttonY + buttonHeight, stageId));
            buttonY += buttonHeight + buttonGap;
        }
        return sidebarWidth;
    }

    private List<com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData> getNoticeStageOptions() {
        var stageMap = ClientCacheManager.getStoryStages();
        if (stageMap == null || stageMap.isEmpty()) {
            return List.of();
        }
        List<com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData> stages =
            new ArrayList<>(getPlayerVisibleStoryStages(stageMap).values());
        // 故事定义会校验阶段 ID，但客户端仍要对损坏/旧版本快照保持防御性。
        stages.removeIf(stage -> stage == null
            || safeNoticeText(stage.getStageId(), "").isEmpty());
        stages.sort(java.util.Comparator.comparingInt(
            com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData::getStageNumber));
        return stages;
    }

    private void ensureNoticeStageSelection(
            List<com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData> stages) {
        if (selectedNoticeStageId == null || selectedNoticeStageId.isEmpty()) {
            for (var stage : stages) {
                if (stage.isCurrentStage()) {
                    selectedNoticeStageId = safeNoticeText(stage.getStageId(), "");
                    return;
                }
            }
            // 阶段包尚未标注 current 时，选择已开放列表中的第一项，避免把隐藏阶段混入列表。
            if (!stages.isEmpty()) {
                selectedNoticeStageId = safeNoticeText(stages.get(0).getStageId(), "");
            }
            return;
        }
        if (!selectedNoticeStageId.isEmpty()
                && stages.stream().noneMatch(stage -> selectedNoticeStageId.equals(
                    safeNoticeText(stage.getStageId(), "")))) {
            selectedNoticeStageId = null;
            ensureNoticeStageSelection(stages);
        }
    }

    private String getNoticeStageButtonLabel(
            com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData stage) {
        if (stage == null) {
            return "未知阶段";
        }
        String number = stage.getStageNumber() > 0
            ? String.valueOf(stage.getStageNumber()) : "?";
        String name = safeNoticeText(stage.getStageName(), "");
        return name.isEmpty() ? "第" + number + "阶段" : "第" + number + "阶段 · " + name;
    }

    private void clearNoticeInteractionAreas() {
        for (int[] area : noticeTabClickAreas) {
            setArea(area, 0, 0, 0, 0);
        }
        noticeStageClickAreas.clear();
        setArea(noticeDetailBackArea, 0, 0, 0, 0);
        setArea(noticeDetailContentArea, 0, 0, 0, 0);
        if (pageRenderer != null) {
            int[] noticeClickArea = pageRenderer.getNoticeClickArea();
            setArea(noticeClickArea, 0, 0, 0, 0);
        }
    }

    private void renderNoticeFeedCard(GuiGraphics guiGraphics, int x, int y, int width, int height, NoticeData notice, boolean isRead) {
        int accent = isRead ? 0xFF9AA3B2 : 0xFF4FC3F7;
        drawSoftRect(guiGraphics, x, y, width, height, 2, isRead ? 0xFF202B36 : TABLET_CARD_COLOR, TABLET_CARD_BORDER_COLOR);
        guiGraphics.fill(RenderType.gui(), x, y, x + 4, y + height, accent);
        String category = notice != null && notice.isGameNotice() ? "梦屿广播" : "服务器公告";
        String state = isRead ? "已读" : "未读";
        drawText(guiGraphics, category + " · " + state, x + 12, y + 7, accent);

        String dateText;
        if (notice != null && notice.isGameNotice()) {
            // 游戏公告只显示所属阶段，不显示“危机第 X 日”等剧情日期。
            dateText = getNoticeStageLabel(notice);
        } else {
            dateText = "发布时间 " + (notice == null
                ? "未标注"
                : ServerScreenUI_RendererUtils.formatDateTime(notice.getPublishTime()));
        }
        dateText = fitNoticeText(dateText, Math.max(24, width - 100));
        drawText(guiGraphics, dateText, x + width - mc.font.width(dateText) - 10, y + 7, TABLET_MUTED_TEXT_COLOR);

        String title = safeNoticeText(notice == null ? null : notice.getNoticeTitle(), "无标题");
        title = fitNoticeText(title, width - 24);
        drawText(guiGraphics, title, x + 12, y + 22, TABLET_TEXT_COLOR);

        String metadata = notice != null && notice.isGameNotice()
            ? "剧情线索 · 点击查看正文"
            : "服务器状态与服务信息";
        drawText(guiGraphics, fitNoticeText(metadata, width - 24), x + 12, y + 39, TABLET_MUTED_TEXT_COLOR);

        String content = safeNoticeText(notice == null ? null : notice.getNoticeContent(), "暂无内容")
            .replace('\n', ' ');
        drawText(guiGraphics, fitNoticeText(content, width - 24), x + 12, y + 55, TABLET_MUTED_TEXT_COLOR);
    }

    private List<NoticeData> getNoticesForSelectedTab() {
        NoticeCategory category = selectedNoticeTab == 0 ? NoticeCategory.GAME : NoticeCategory.MAINTENANCE;
        List<NoticeData> filtered = new ArrayList<>();
        for (NoticeData notice : cachedNotices) {
            if (isClientVisibleNotice(notice) && notice.getCategory() == category) {
                if (category == NoticeCategory.GAME
                        && selectedNoticeStageId != null
                        && !selectedNoticeStageId.isEmpty()
                        && !selectedNoticeStageId.equals(notice.getStoryStageId())) {
                    continue;
                }
                filtered.add(notice);
            }
        }
        return filtered;
    }

    /**
     * 客户端再次按当前阶段收窄公告缓存，兼容旧服务端/阶段同步稍晚的情况。
     * 服务器通知没有阶段门槛；游戏公告只允许已开放阶段进入终端统计和列表。
     */
    private boolean isClientVisibleNotice(NoticeData notice) {
        if (notice == null) {
            return false;
        }
        if (!notice.isGameNotice()) {
            return true;
        }
        String stageId = safeNoticeText(notice.getStoryStageId(), "");
        if (stageId.isEmpty()) {
            return false;
        }
        return getPlayerVisibleStoryStages(ClientCacheManager.getStoryStages()).values().stream()
            .anyMatch(stage -> stage != null && stageId.equals(stage.getStageId()));
    }

    private int countNotices(NoticeCategory category) {
        int count = 0;
        for (NoticeData notice : cachedNotices) {
            if (isClientVisibleNotice(notice) && notice.getCategory() == category) count++;
        }
        return count;
    }

    private int countUnreadNotices(NoticeCategory category) {
        int count = 0;
        for (NoticeData notice : cachedNotices) {
            if (isClientVisibleNotice(notice) && notice.getCategory() == category
                    && !cachedReadNoticeIds.contains(notice.getNoticeId())) {
                count++;
            }
        }
        return count;
    }

    private String getNoticeStageLabel(NoticeData notice) {
        String stageId = notice == null ? "" : safeNoticeText(notice.getStoryStageId(), "");
        if (stageId.isBlank()) return "未指定";
        var stages = ClientCacheManager.getStoryStages();
        if (stages != null) {
            for (var stage : stages.values()) {
                if (stage != null && stageId.equals(stage.getStageId())) {
                    return "第" + stage.getStageNumber() + "阶段 · "
                        + safeNoticeText(stage.getStageName(), "未命名");
                }
            }
        }
        return stageId;
    }

    private String safeNoticeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String fitNoticeText(String value, int maxWidth) {
        String safe = safeNoticeText(value, "");
        if (maxWidth <= 0 || mc.font.width(safe) <= maxWidth) return safe;
        int ellipsisWidth = mc.font.width("...");
        if (maxWidth <= ellipsisWidth) return "...";
        return ServerScreenUI_RendererUtils.truncateText(mc.font, safe, maxWidth - ellipsisWidth) + "...";
    }

    private void renderHelpTerminalPage(
        GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y, int width, int height) {
        reportNewPlayerGuideViewed();
        selectedHelpTopicIndex = Math.max(0, Math.min(HELP_TOPICS.length - 1, selectedHelpTopicIndex));
        HelpTopic topic = HELP_TOPICS[selectedHelpTopicIndex];
        helpTopicClickAreas.clear();

        double virtualMouseX = mouseX / uiScale;
        double virtualMouseY = mouseY / uiScale;
        int gap = 10;
        int leftW = Math.max(150, Math.min(178, (int) (width * 0.30F)));
        int rightX = x + leftW + gap;
        int rightW = width - leftW - gap;

        drawSoftRect(guiGraphics, x, y, leftW, height, 3, 0xFF18232D, TABLET_CARD_BORDER_COLOR);
        drawText(guiGraphics, "SURVIVAL MANUAL", x + 11, y + 9, TABLET_MUTED_TEXT_COLOR);
        drawText(guiGraphics, "梦屿生存手册", x + 11, y + 23, TABLET_TEXT_COLOR);
        String progress = (selectedHelpTopicIndex + 1) + " / " + HELP_TOPICS.length;
        drawText(guiGraphics, progress, x + leftW - mc.font.width(progress) - 11, y + 23, topic.accent());

        int navGap = 5;
        int navY = y + 43;
        int navHeight = Math.max(20, Math.min(34,
            (height - 51 - navGap * (HELP_TOPICS.length - 1)) / HELP_TOPICS.length));
        for (int index = 0; index < HELP_TOPICS.length; index++) {
            HelpTopic item = HELP_TOPICS[index];
            int itemY = navY + index * (navHeight + navGap);
            boolean active = index == selectedHelpTopicIndex;
            boolean hovered = virtualMouseX >= x + 7 && virtualMouseX <= x + leftW - 7
                && virtualMouseY >= itemY && virtualMouseY <= itemY + navHeight;
            int background = active ? 0xFF2B3C49 : hovered ? TABLET_CARD_HOVER_COLOR : 0xFF202B36;
            int border = active || hovered ? item.accent() : TABLET_CARD_BORDER_COLOR;
            drawSoftRect(guiGraphics, x + 7, itemY, leftW - 14, navHeight, 3, background, border);
            guiGraphics.fill(RenderType.gui(), x + 7, itemY + 4, x + 10, itemY + navHeight - 4,
                active ? item.accent() : 0xFF425463);
            drawText(guiGraphics, item.number(), x + 16, itemY + 6,
                active ? item.accent() : TABLET_MUTED_TEXT_COLOR);
            drawText(guiGraphics, fitText(item.title(), leftW - 62), x + 40, itemY + 6,
                active ? TABLET_TEXT_COLOR : TABLET_MUTED_TEXT_COLOR);
            if (navHeight >= 29) {
                drawText(guiGraphics, fitText(item.subtitle(), leftW - 34), x + 16, itemY + 18,
                    active ? 0xFFB9C7D2 : 0xFF7F8E9B);
            }
            helpTopicClickAreas.add(new HelpTopicClickArea(
                x + 7, itemY, x + leftW - 7, itemY + navHeight, index));
        }

        drawSoftRect(guiGraphics, rightX, y, rightW, height, 3, 0xFF202B36, TABLET_CARD_BORDER_COLOR);
        guiGraphics.fill(RenderType.gui(), rightX, y + 8, rightX + 4, y + 36, topic.accent());
        drawSoftRect(guiGraphics, rightX + 12, y + 8, 32, 16, 3, 0xFF293642, topic.accent());
        drawCenteredText(guiGraphics, topic.number(), rightX + 28, y + 12, topic.accent());
        drawText(guiGraphics, topic.title(), rightX + 52, y + 8, TABLET_TEXT_COLOR);
        drawText(guiGraphics, fitText(topic.subtitle(), rightW - 66), rightX + 52, y + 23,
            TABLET_MUTED_TEXT_COLOR);

        int innerX = rightX + 10;
        int innerW = rightW - 20;
        int metricGap = 6;
        int metricY = y + 43;
        int metricH = 30;
        int metricW = (innerW - metricGap * 2) / 3;
        for (int index = 0; index < 3; index++) {
            int metricX = innerX + index * (metricW + metricGap);
            drawSoftRect(guiGraphics, metricX, metricY, metricW, metricH, 3,
                0xFF26333F, 0xFF3B4C5A);
            guiGraphics.fill(RenderType.gui(), metricX + 7, metricY + metricH - 5,
                metricX + metricW - 7, metricY + metricH - 3, topic.accent());
            drawCenteredText(guiGraphics, fitText(topic.metrics()[index], metricW - 12),
                metricX + metricW / 2, metricY + 7, TABLET_TEXT_COLOR);
        }

        int tipH = Math.max(40, Math.min(44, height / 5));
        int tipY = y + height - tipH;
        int factY = metricY + metricH + 7;
        int factH = Math.max(54, tipY - factY - 7);
        drawHelpFactCard(guiGraphics, innerX, factY, innerW, factH, topic);
        drawHelpTipCard(guiGraphics, innerX, tipY, innerW, tipH, topic);
    }

    private void drawHelpFactCard(
        GuiGraphics guiGraphics, int x, int y, int width, int height, HelpTopic topic) {
        drawSoftRect(guiGraphics, x, y, width, height, 3, 0xFF1B2731, 0xFF334451);
        drawText(guiGraphics, "机制要点", x + 11, y + 7, topic.accent());
        int cursorY = y + 23;
        int bottom = y + height - 5;
        for (String fact : topic.facts()) {
            List<FormattedCharSequence> lines = mc.font.split(
                Component.literal(fact), Math.max(32, width - 34));
            int lineCount = Math.min(2, lines.size());
            if (lineCount <= 0 || cursorY + lineCount * 10 > bottom) {
                break;
            }
            drawSoftRect(guiGraphics, x + 11, cursorY + 3, 4, 4, 2,
                topic.accent(), 0x00000000);
            for (int lineIndex = 0; lineIndex < lineCount; lineIndex++) {
                guiGraphics.drawString(mc.font, lines.get(lineIndex), x + 22,
                    cursorY + lineIndex * 10, TABLET_MUTED_TEXT_COLOR, false);
            }
            cursorY += lineCount * 10 + 3;
        }
    }

    private void drawHelpTipCard(
        GuiGraphics guiGraphics, int x, int y, int width, int height, HelpTopic topic) {
        drawSoftRect(guiGraphics, x, y, width, height, 3, 0xFF26343D, topic.accent());
        guiGraphics.fill(RenderType.gui(), x, y + 6, x + 4, y + height - 6, topic.accent());
        drawText(guiGraphics, "生存提示 · " + topic.tipTitle(), x + 12, y + 7, topic.accent());
        drawWrappedText(guiGraphics, topic.tipText(), x + 12, y + 21,
            width - 24, TABLET_TEXT_COLOR, 2);
    }

    private void renderMarketPage(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        EconomyTerminalClientCache.Snapshot snapshot = EconomyTerminalClientCache.get();
        if (!snapshot.loaded()) {
            renderPlaceholderPage(guiGraphics, x, y, width, height, "服务器商店", "正在同步市场数据...");
            return;
        }
        if (!snapshot.available() || !snapshot.compatible()) {
            renderPlaceholderPage(guiGraphics, x, y, width, height, "服务器商店", snapshot.statusText());
            return;
        }

        int summaryHeight = 48;
        int gap = 8;
        int cardGap = 7;
        int cardWidth = (width - cardGap * 3) / 4;
        drawMarketSummaryCard(guiGraphics, x, y, cardWidth, summaryHeight, "梦鱼币", String.valueOf(snapshot.balance()), 0xFFFFC857);
        drawMarketSummaryCard(guiGraphics, x + (cardWidth + cardGap), y, cardWidth, summaryHeight, "出售", String.valueOf(snapshot.salesOrderCount()), 0xFF4FC3F7);
        drawMarketSummaryCard(guiGraphics, x + (cardWidth + cardGap) * 2, y, cardWidth, summaryHeight, "求购", String.valueOf(snapshot.demandOrderCount()), 0xFFFFB84D);
        drawMarketSummaryCard(guiGraphics, x + (cardWidth + cardGap) * 3, y, cardWidth, summaryHeight, "我的挂单", String.valueOf(snapshot.ownOrderCount()), 0xFFB58BFF);

        int listY = y + summaryHeight + gap;
        int listHeight = height - summaryHeight - gap;
        List<EconomyTerminalClientCache.MarketOrderView> orders = snapshot.marketOrders();
        marketVisibleRows = Math.max(1, (listHeight + MARKET_ROW_GAP) / (MARKET_ROW_HEIGHT + MARKET_ROW_GAP));
        int visibleRows = marketVisibleRows;
        int maxOffset = Math.max(0, orders.size() - visibleRows);
        marketScrollOffset = Math.max(0, Math.min(marketScrollOffset, maxOffset));

        if (orders.isEmpty()) {
            drawSoftRect(guiGraphics, x, listY, width, listHeight, 3, TABLET_CARD_COLOR, TABLET_CARD_BORDER_COLOR);
            drawCenteredText(guiGraphics, "当前没有有效市场挂单", x + width / 2, listY + listHeight / 2 - 5, TABLET_MUTED_TEXT_COLOR);
            return;
        }

        int start = (int) marketScrollOffset;
        int end = Math.min(orders.size(), start + visibleRows);
        int currentY = listY;
        for (int i = start; i < end; i++) {
            drawMarketOrderRow(guiGraphics, x, currentY, width, MARKET_ROW_HEIGHT, orders.get(i));
            currentY += MARKET_ROW_HEIGHT + MARKET_ROW_GAP;
        }

        if (orders.size() > visibleRows) {
            String pageText = (start + 1) + "-" + end + " / " + orders.size();
            drawText(guiGraphics, pageText, x + width - mc.font.width(pageText) - 8,
                y + summaryHeight - 14, TABLET_MUTED_TEXT_COLOR);
        }
    }

    private void drawMarketSummaryCard(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                       String label, String value, int accent) {
        drawSoftRect(guiGraphics, x, y, width, height, 3, TABLET_CARD_COLOR, TABLET_CARD_BORDER_COLOR);
        guiGraphics.fill(RenderType.gui(), x, y + 6, x + 3, y + height - 6, accent);
        drawText(guiGraphics, label, x + 11, y + 8, TABLET_MUTED_TEXT_COLOR);
        drawText(guiGraphics, fitDashboardText(value, width - 22), x + 11, y + 27, TABLET_TEXT_COLOR);
    }

    private void drawMarketOrderRow(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                    EconomyTerminalClientCache.MarketOrderView order) {
        boolean sale = "SALES".equals(order.type());
        int accent = sale ? 0xFF4FC3F7 : 0xFFFFB84D;
        String typeText = sale ? "出售" : "求购";
        drawSoftRect(guiGraphics, x, y, width, height, 3, 0xFF202B36, TABLET_CARD_BORDER_COLOR);
        guiGraphics.fill(RenderType.gui(), x, y + 5, x + 3, y + height - 5, accent);

        int badgeWidth = mc.font.width(typeText) + 14;
        drawSoftRect(guiGraphics, x + 10, y + 7, badgeWidth, 16, 3, 0xFF283744, 0xFF3B5062);
        drawText(guiGraphics, typeText, x + 17, y + 11, accent);

        int itemX = x + badgeWidth + 20;
        int priceAreaWidth = Math.max(116, width / 4);
        int itemMaxWidth = Math.max(50, width - (itemX - x) - priceAreaWidth - 18);
        String itemName = resolveMarketItemName(order.itemId());
        drawText(guiGraphics, fitDashboardText(itemName + " ×" + order.quantity(), itemMaxWidth),
            itemX, y + 7, TABLET_TEXT_COLOR);
        String owner = order.ownerName().isBlank() ? "匿名玩家" : order.ownerName();
        drawText(guiGraphics, fitDashboardText("发布者 · " + owner, itemMaxWidth),
            itemX, y + 23, TABLET_MUTED_TEXT_COLOR);

        String priceText = order.totalPrice() + " 梦鱼币";
        int priceX = x + width - mc.font.width(priceText) - 12;
        drawText(guiGraphics, priceText, priceX, y + 7, 0xFFFFC857);
        String expireText = formatMarketExpiry(order.expirationTime());
        drawText(guiGraphics, expireText, x + width - mc.font.width(expireText) - 12,
            y + 23, TABLET_MUTED_TEXT_COLOR);
    }

    private String resolveMarketItemName(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return itemId;
        try {
            var item = BuiltInRegistries.ITEM.get(id);
            if (item != null) {
                String name = new ItemStack(item).getHoverName().getString();
                if (!name.isBlank()) return name;
            }
        } catch (Throwable ignored) {
        }
        return id.getPath().replace('_', ' ');
    }

    private String formatMarketExpiry(long expirationTime) {
        long remaining = Math.max(0L, expirationTime - System.currentTimeMillis());
        long minutes = remaining / 60_000L;
        if (minutes >= 60L) {
            return "剩余 " + (minutes / 60L) + "时" + (minutes % 60L) + "分";
        }
        return "剩余 " + minutes + "分";
    }

    private void renderPlaceholderPage(GuiGraphics guiGraphics, int x, int y, int width, int height, String title, String hint) {
        drawSoftRect(guiGraphics, x, y, width, height, 3, TABLET_CARD_COLOR, TABLET_CARD_BORDER_COLOR);
        drawCenteredText(guiGraphics, title, x + width / 2, y + height / 2 - 12, TABLET_TEXT_COLOR);
        drawCenteredText(guiGraphics, hint, x + width / 2, y + height / 2 + 6, TABLET_MUTED_TEXT_COLOR);
    }

    private void reportNewPlayerGuideViewed() {
        if (newPlayerGuideViewReported || mc.player == null || mc.getConnection() == null) {
            return;
        }
        newPlayerGuideViewReported = true;
        DreamingFishCore_NetworkManager.sendToServer(new Packet_NewPlayerGuideViewed());
    }

    /**
     * 绘制当前世界已经公开发生的重大事件。
     *
     * <p>这里展示的是服务端提供的只读视图。内部世界旗标、内容热重载等运营记录不会进入该列表，
     * 避免历史页面提前泄露仍在调查中的剧情判断。</p>
     */
    private void renderHistoryPage(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        int summaryHeight = 38;
        int gap = 8;
        drawSoftRect(guiGraphics, x, y, width, summaryHeight, 2, TABLET_CARD_COLOR, TABLET_CARD_BORDER_COLOR);
        guiGraphics.fill(RenderType.gui(), x, y, x + 4, y + summaryHeight, 0xFFFFC857);

        drawText(guiGraphics, "梦屿世界年表", x + 13, y + 7, TABLET_TEXT_COLOR);
        String statusText;
        int statusColor;
        if (!historyLoaded) {
            statusText = "正在同步服务器记录";
            statusColor = TABLET_MUTED_TEXT_COLOR;
        } else if (!historyWritesEnabled) {
            statusText = "历史日志处于只读保护";
            statusColor = 0xFFFF7A88;
        } else {
            statusText = "持续记录中";
            statusColor = 0xFF50D890;
        }
        drawText(guiGraphics, statusText, x + 13, y + 22, statusColor);

        String countText = historyTotalEventCount + " 条公开历史 / 当前载入 " + cachedHistory.size() + " 条";
        drawText(guiGraphics, countText, x + width - mc.font.width(countText) - 12, y + 14, TABLET_MUTED_TEXT_COLOR);

        int listY = y + summaryHeight + gap;
        int listHeight = height - summaryHeight - gap;
        historyVisibleEntries = Math.max(1, (listHeight + HISTORY_CARD_GAP) / (HISTORY_CARD_HEIGHT + HISTORY_CARD_GAP));

        if (!historyLoaded) {
            renderHistoryEmptyState(guiGraphics, x, listY, width, listHeight, "正在读取世界历史...");
            return;
        }
        if (cachedHistory.isEmpty()) {
            renderHistoryEmptyState(guiGraphics, x, listY, width, listHeight, "这个世界的公开历史尚未开始");
            return;
        }

        int maxOffset = Math.max(0, cachedHistory.size() - historyVisibleEntries);
        historyScrollOffset = Math.max(0L, Math.min(historyScrollOffset, maxOffset));
        int startIndex = cachedHistory.size() - 1 - (int) historyScrollOffset;
        int cardsToDraw = Math.min(historyVisibleEntries, startIndex + 1);

        guiGraphics.enableScissor(
                (int) (x * uiScale),
                (int) (listY * uiScale),
                (int) ((x + width) * uiScale),
                (int) ((listY + listHeight) * uiScale));
        for (int index = 0; index < cardsToDraw; index++) {
            Packet_WorldHistoryResponse.HistoryEntry entry = cachedHistory.get(startIndex - index);
            int cardY = listY + index * (HISTORY_CARD_HEIGHT + HISTORY_CARD_GAP);
            renderHistoryCard(guiGraphics, x, cardY, width, HISTORY_CARD_HEIGHT, entry);
        }
        guiGraphics.disableScissor();

        if (cachedHistory.size() > historyVisibleEntries) {
            int scrollTrackX = x + width - 3;
            int thumbHeight = Math.max(12, listHeight * historyVisibleEntries / cachedHistory.size());
            int travel = Math.max(0, listHeight - thumbHeight);
            int thumbY = listY + (maxOffset == 0 ? 0 : (int) (travel * historyScrollOffset / maxOffset));
            guiGraphics.fill(RenderType.gui(), scrollTrackX, listY, scrollTrackX + 2, listY + listHeight, 0x44344555);
            guiGraphics.fill(RenderType.gui(), scrollTrackX, thumbY, scrollTrackX + 2, thumbY + thumbHeight, 0xFFFFC857);
        }
    }

    private void renderHistoryEmptyState(
            GuiGraphics guiGraphics, int x, int y, int width, int height, String text) {
        drawSoftRect(guiGraphics, x, y, width, height, 2, 0x5524313E, TABLET_CARD_BORDER_COLOR);
        drawCenteredText(guiGraphics, text, x + width / 2, y + height / 2 - mc.font.lineHeight / 2, TABLET_MUTED_TEXT_COLOR);
    }

    private void renderHistoryCard(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int width,
            int height,
            Packet_WorldHistoryResponse.HistoryEntry entry) {
        HistoryPresentation presentation = getHistoryPresentation(entry);
        drawSoftRect(guiGraphics, x, y, width - 6, height, 2, TABLET_CARD_COLOR, TABLET_CARD_BORDER_COLOR);
        guiGraphics.fill(RenderType.gui(), x, y, x + 4, y + height, presentation.color());

        int badgeWidth = 28;
        drawSoftRect(guiGraphics, x + 11, y + 7, badgeWidth, 18, 2, 0x442E3C49, 0x55344555);
        drawCenteredText(guiGraphics, presentation.icon(), x + 11 + badgeWidth / 2, y + 12, presentation.color());

        int textX = x + 48;
        int timeWidth = mc.font.width(formatHistoryDate(entry.recordedAtEpochMillis()));
        int textWidth = Math.max(20, width - 66 - timeWidth);
        drawText(guiGraphics, fitText(presentation.title(), textWidth), textX, y + 6, TABLET_TEXT_COLOR);
        drawText(guiGraphics, fitText(presentation.subtitle(), textWidth), textX, y + 19, TABLET_MUTED_TEXT_COLOR);
        drawText(guiGraphics, formatHistoryDate(entry.recordedAtEpochMillis()), x + width - timeWidth - 12, y + 12, TABLET_MUTED_TEXT_COLOR);
    }

    private HistoryPresentation getHistoryPresentation(Packet_WorldHistoryResponse.HistoryEntry entry) {
        String subject = getHistorySubjectName(entry.subjectId());
        String actor = formatHistoryActor(entry.actor());
        return switch (entry.type()) {
            case "STAGE_CHANGED" -> new HistoryPresentation("章", "故事进入「" + subject + "」", actor + " 发布了新的世界阶段", 0xFFB58BFF);
            case "OPERATION_ROUND_STARTED" -> new HistoryPresentation("议", "一次公共讨论形成记录", "梦屿正在等待世界作出回应", 0xFF4FC3F7);
            case "OPERATION_ROUND_PUBLISHED" -> new HistoryPresentation("答", "世界回应了玩家的讨论", actor + " 发布了新的回应", 0xFF50D890);
            case "TASK_PUBLISHED" -> new HistoryPresentation("令", "新的行动「" + subject + "」已发布", "等待参与者前往现场", 0xFFFFC857);
            case "TASK_SUCCEEDED" -> new HistoryPresentation("成", "行动「" + subject + "」成功", formatParticipantText(entry), 0xFF50D890);
            case "TASK_FAILED" -> new HistoryPresentation("失", "行动「" + subject + "」失败", formatParticipantText(entry) + "，结果已写入历史", 0xFFFF7A88);
            case "ENDING_CHANGED" -> new HistoryPresentation("终", "世界进入「" + subject + "」", "这个选择将长期留在梦屿", 0xFFFFC857);
            default -> new HistoryPresentation("记", subject, actor, 0xFF7AA8C7);
        };
    }

    private String formatParticipantText(Packet_WorldHistoryResponse.HistoryEntry entry) {
        String count = entry.details().get("participantCount");
        return count == null ? "参与者共同完成了这次行动" : count + " 名参与者被记录在场";
    }

    private String getHistorySubjectName(String subjectId) {
        if (subjectId == null || subjectId.isBlank()) {
            return "未命名事件";
        }
        for (var stage : ClientCacheManager.getStoryStages().values()) {
            if (subjectId.equals(stage.getStageId())) {
                return stage.getStageName();
            }
            if (stage.getTasks() == null) {
                continue;
            }
            for (var task : stage.getTasks()) {
                if (subjectId.equals(task.getTaskKey())) {
                    return task.getTaskName();
                }
            }
        }
        int separator = subjectId.indexOf(':');
        String path = separator >= 0 ? subjectId.substring(separator + 1) : subjectId;
        return path.replace('_', ' ').replace('-', ' ');
    }

    private String formatHistoryActor(String actor) {
        return actor == null || actor.isBlank() || "system".equalsIgnoreCase(actor) ? "梦屿系统" : actor;
    }

    private String formatHistoryDate(long epochMillis) {
        if (epochMillis <= 0L) {
            return "--";
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MM.dd HH:mm"));
    }

    private String fitText(String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (mc.font.width(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = mc.font.width("...");
        return ServerScreenUI_RendererUtils.truncateText(mc.font, text, Math.max(0, maxWidth - ellipsisWidth)) + "...";
    }

    private record HistoryPresentation(String icon, String title, String subtitle, int color) {
    }

    private void drawProfileTimeRow(GuiGraphics guiGraphics, int x, int y, int width, String label, String value, int accentColor) {
        drawSoftRect(guiGraphics, x, y, width, 17, 2, 0x6624313E, 0x22344555);
        guiGraphics.fill(RenderType.gui(), x, y, x + 3, y + 17, accentColor);
        drawText(guiGraphics, label, x + 8, y + 5, TABLET_MUTED_TEXT_COLOR);
        int valueX = x + 38;
        int valueMaxWidth = Math.max(16, width - 44);
        String displayValue = value;
        if (mc.font.width(displayValue) > valueMaxWidth) {
            displayValue = ServerScreenUI_RendererUtils.truncateText(mc.font, displayValue, valueMaxWidth - mc.font.width("...")) + "...";
        }
        drawText(guiGraphics, displayValue, valueX, y + 5, TABLET_TEXT_COLOR);
    }

    private void drawIdentityLine(GuiGraphics guiGraphics, int x, int y, int width, String label, String value, int accentColor) {
        drawSoftRect(guiGraphics, x, y, width, 16, 2, 0x3324313E, 0x22344555);
        guiGraphics.fill(RenderType.gui(), x, y, x + 3, y + 16, accentColor);
        drawText(guiGraphics, label, x + 8, y + 5, TABLET_MUTED_TEXT_COLOR);
        int valueX = x + 44;
        int valueMaxWidth = Math.max(16, width - 50);
        String displayValue = value;
        if (mc.font.width(displayValue) > valueMaxWidth) {
            displayValue = ServerScreenUI_RendererUtils.truncateText(mc.font, displayValue, valueMaxWidth - mc.font.width("...")) + "...";
        }
        drawText(guiGraphics, displayValue, valueX, y + 5, accentColor);
    }

    private String formatProfileDate(long epochMs) {
        if (epochMs <= 0) {
            return "--";
        }
        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
        return time.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
    }

    private String formatProfileDateTime(long epochMs) {
        if (epochMs <= 0) {
            return "--";
        }
        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
        return time.format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"));
    }

    private String formatPlayDuration(long millis) {
        if (millis <= 0) {
            return "0分";
        }
        long totalMinutes = millis / 60000L;
        long days = totalMinutes / 1440L;
        long hours = (totalMinutes % 1440L) / 60L;
        long minutes = totalMinutes % 60L;
        if (days > 0) {
            return days + "天" + hours + "时";
        }
        if (hours > 0) {
            return hours + "时" + minutes + "分";
        }
        return Math.max(1, minutes) + "分";
    }

    private void drawStatTile(GuiGraphics guiGraphics, int x, int y, int width, int height, String label, String value, int accentColor) {
        drawStatTile(guiGraphics, x, y, width, height, label, value, accentColor, false);
    }

    private void drawStatTile(GuiGraphics guiGraphics, int x, int y, int width, int height,
                              String label, String value, int accentColor, boolean hovered) {
        drawSoftRect(guiGraphics, x, y, width, height, 2,
            hovered ? TABLET_CARD_HOVER_COLOR : TABLET_CARD_COLOR,
            hovered ? accentColor : TABLET_CARD_BORDER_COLOR);
        guiGraphics.fill(RenderType.gui(), x, y, x + 4, y + height, accentColor);
        drawText(guiGraphics, label, x + 12, y + 9, TABLET_MUTED_TEXT_COLOR);
        int valueMaxWidth = Math.max(18, width - 24);
        String displayValue = value;
        if (mc.font.width(displayValue) > valueMaxWidth) {
            displayValue = ServerScreenUI_RendererUtils.truncateText(mc.font, displayValue, valueMaxWidth - mc.font.width("...")) + "...";
        }
        drawText(guiGraphics, displayValue, x + 12, y + 27, TABLET_TEXT_COLOR);
    }

    private void drawMiniBar(GuiGraphics guiGraphics, int x, int y, int width, int height,
                             String label, float progress, String value, int color) {
        int valueMaxWidth = Math.max(18, width - mc.font.width(label) - 10);
        String displayValue = value;
        if (mc.font.width(displayValue) > valueMaxWidth) {
            displayValue = ServerScreenUI_RendererUtils.truncateText(mc.font, displayValue, valueMaxWidth - mc.font.width("...")) + "...";
        }
        int valueWidth = mc.font.width(displayValue);
        drawText(guiGraphics, label, x, y, TABLET_MUTED_TEXT_COLOR);
        drawText(guiGraphics, displayValue, x + width - valueWidth, y, TABLET_TEXT_COLOR);
        drawProgressBar(guiGraphics, x, y + height - 6, width, 5, progress, color);
    }

    private void drawSoftRect(GuiGraphics guiGraphics, int x, int y, int width, int height, int radius, int fillColor, int borderColor) {
        ServerScreenUI_RoundedRenderer.draw(guiGraphics, x, y, width, height,
                radius, fillColor, borderColor);
    }

    private void drawText(GuiGraphics guiGraphics, String text, int x, int y, int color) {
        guiGraphics.drawString(mc.font, text, x, y, color, false);
    }

    private void drawBrandTitle(GuiGraphics guiGraphics, int x, int y) {
        drawText(guiGraphics, "Dreaming", x, y, 0xFFB58BFF);
        int fishX = x + mc.font.width("Dreaming");
        drawText(guiGraphics, "Fish", fishX, y, 0xFF4FC3F7);
        drawText(guiGraphics, " Terminal", fishX + mc.font.width("Fish"), y, 0xFFFFC857);
    }

    private void drawTopDateTime(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        String time = now.format(DateTimeFormatter.ofPattern("HH:mm"));
        String dateTime = date + "  " + time;
        int textWidth = mc.font.width(dateTime);
        int centerX = x + width / 2;
        int dateWidth = mc.font.width(date);
        int startX = centerX - textWidth / 2;
        drawText(guiGraphics, date, startX, y + 4, TABLET_MUTED_TEXT_COLOR);
        drawText(guiGraphics, time, startX + dateWidth + mc.font.width("  "), y + 4, TABLET_TEXT_COLOR);
    }

    private void drawCenteredText(GuiGraphics guiGraphics, String text, int centerX, int y, int color) {
        drawText(guiGraphics, text, centerX - mc.font.width(text) / 2, y, color);
    }

    /**
     * 绘制左侧按钮区域 + 服务器信息区域
     */
    private void renderModuleDashboard(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y, int width, int height) {
        float virtualMouseX = mouseX / uiScale;
        float virtualMouseY = mouseY / uiScale;

        // 主页只保留高频信息。个人引导并入故事，设置留在 Dock。
        int dockHeight = 30;
        int dockGap = 7;
        int contentHeight = Math.max(132, height - dockHeight - dockGap);
        int gap = 8;

        int leftWidth = Math.max(132, (int) (width * 0.30f));
        int centerWidth = Math.max(146, (int) (width * 0.29f));
        int rightWidth = Math.max(120, width - leftWidth - centerWidth - gap * 2);
        int leftX = x;
        int centerX = leftX + leftWidth + gap;
        int rightX = centerX + centerWidth + gap;

        int profileHeight = Math.max(84, (int) (contentHeight * 0.54f));
        int economyCardHeight = Math.max(78, contentHeight - profileHeight - gap);
        int storyHeight = Math.max(72,
            Math.min(contentHeight - gap - 48, (int) (contentHeight * 0.58f)));
        int noticeHeight = Math.max(48, contentHeight - storyHeight - gap);
        int messageHeight = Math.max(72,
            Math.min(contentHeight - gap - 42, (int) ((contentHeight - gap) * 0.54f)));
        int shortcutHeight = Math.max(42, contentHeight - messageHeight - gap);
        int shortcutWidth = (rightWidth - gap) / 2;

        // 左列：个人档案 + 经济系统统一入口。
        setDashboardClickArea(0, leftX, y, leftWidth, profileHeight);
        drawDashboardProfileCard(guiGraphics, leftX, y, leftWidth, profileHeight,
            isDashboardHovered(virtualMouseX, virtualMouseY, leftX, y, leftWidth, profileHeight));

        int leftY2 = y + profileHeight + gap;
        setDashboardClickArea(6, leftX, leftY2, leftWidth, economyCardHeight);
        drawDashboardEconomyCard(guiGraphics, leftX, leftY2, leftWidth, economyCardHeight,
            isDashboardHovered(virtualMouseX, virtualMouseY, leftX, leftY2, leftWidth, economyCardHeight));

        // 首页已移除领地、排行、成就和设置卡；设置仍可从 Dock 进入。
        setDashboardClickArea(7, -1000, -1000, 0, 0);
        setDashboardClickArea(4, -1000, -1000, 0, 0);
        setDashboardClickArea(5, -1000, -1000, 0, 0);
        setDashboardClickArea(9, -1000, -1000, 0, 0);

        // 中列：故事与广播各自获得完整宽度；个人引导摘要归入故事卡。
        setDashboardClickArea(3, centerX, y, centerWidth, storyHeight);
        drawDashboardStoryCard(guiGraphics, centerX, y, centerWidth, storyHeight,
            isDashboardHovered(virtualMouseX, virtualMouseY, centerX, y, centerWidth, storyHeight));

        int centerY2 = y + storyHeight + gap;
        setDashboardClickArea(2, centerX, centerY2, centerWidth, noticeHeight);
        drawDashboardNoticeCard(guiGraphics, centerX, centerY2, centerWidth, noticeHeight,
            isDashboardHovered(virtualMouseX, virtualMouseY, centerX, centerY2, centerWidth, noticeHeight));

        // 右列：短信保持大卡；原个人引导区域拆成历史与帮助两块。
        setDashboardClickArea(NPC_MESSAGE_PAGE_INDEX, rightX, y, rightWidth, messageHeight);
        drawDashboardMessageCard(guiGraphics, rightX, y, rightWidth, messageHeight,
            isDashboardHovered(virtualMouseX, virtualMouseY, rightX, y, rightWidth, messageHeight));

        int shortcutY = y + messageHeight + gap;
        setDashboardClickArea(8, rightX, shortcutY, shortcutWidth, shortcutHeight);
        drawDashboardHistoryCard(guiGraphics, rightX, shortcutY, shortcutWidth, shortcutHeight,
            isDashboardHovered(virtualMouseX, virtualMouseY, rightX, shortcutY, shortcutWidth, shortcutHeight));
        int helpX = rightX + shortcutWidth + gap;
        int helpWidth = rightWidth - shortcutWidth - gap;
        setDashboardClickArea(1, helpX, shortcutY, helpWidth, shortcutHeight);
        drawDashboardHelpCard(guiGraphics, helpX, shortcutY, helpWidth, shortcutHeight,
            isDashboardHovered(virtualMouseX, virtualMouseY, helpX, shortcutY, helpWidth, shortcutHeight));
        drawDashboardDock(guiGraphics, virtualMouseX, virtualMouseY,
            x, y + height - dockHeight, width, dockHeight);
    }

    private float getDashboardTileReveal(int tileX, int dashboardX, int dashboardWidth) {
        if (skipAnimation || isClosing) return 1.0f;
        long elapsed = Util.getMillis() - openTime;
        float positionDelay = (float) (tileX - dashboardX) / Math.max(1, dashboardWidth) * 180.0f;
        float progress = (elapsed - positionDelay) / 360.0f;
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        return 1.0f - (float) Math.pow(1.0f - progress, 3);
    }

    private void setDashboardClickArea(int index, int x, int y, int width, int height) {
        if (index < 0 || index >= leftButtonX1.length) {
            return;
        }
        leftButtonX1[index] = x;
        leftButtonY1[index] = y;
        leftButtonX2[index] = x + width;
        leftButtonY2[index] = y + height;
    }

    private boolean isDashboardHovered(float mouseX, float mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private String fitDashboardText(String text, int maxWidth) {
        if (text == null || text.isBlank()) return "--";
        if (mc.font.width(text) <= maxWidth) return text;
        int ellipsisWidth = mc.font.width("...");
        return ServerScreenUI_RendererUtils.truncateText(mc.font, text, Math.max(4, maxWidth - ellipsisWidth)) + "...";
    }

    private String getDashboardMarketSubtitle() {
        EconomyTerminalClientCache.Snapshot snapshot = EconomyTerminalClientCache.get();
        if (!snapshot.loaded()) return "同步市场数据...";
        if (!snapshot.available() || !snapshot.compatible()) return snapshot.statusText();
        int total = snapshot.salesOrderCount() + snapshot.demandOrderCount();
        if (total <= 0) return "暂无市场挂单";
        return "出售 " + snapshot.salesOrderCount() + " · 求购 " + snapshot.demandOrderCount();
    }

    private String getDashboardTerritorySubtitle() {
        EconomyTerminalClientCache.Snapshot snapshot = EconomyTerminalClientCache.get();
        if (!snapshot.loaded()) return "同步领地数据...";
        if (!snapshot.available() || !snapshot.compatible()) return snapshot.statusText();
        if (!snapshot.currentTerritoryName().isBlank()) {
            String relation = localizeEconomyRelationship(snapshot.currentRelationship());
            return relation.isBlank()
                ? "当前 · " + snapshot.currentTerritoryName()
                : "当前 · " + snapshot.currentTerritoryName() + " · " + relation;
        }
        return snapshot.ownedTerritoryCount() > 0
            ? "拥有 " + snapshot.ownedTerritoryCount() + " 个领地"
            : "暂无领地";
    }

    private String localizeEconomyRelationship(String relationship) {
        return switch (relationship == null ? "NONE" : relationship) {
            case "OWNER" -> "领主";
            case "MEMBER" -> "成员";
            default -> "";
        };
    }

    private void drawDashboardEconomyCard(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                          boolean isHovered) {
        EconomyTerminalClientCache.Snapshot snapshot = EconomyTerminalClientCache.get();
        int accent = 0xFFFFC857;
        int bgColor = isHovered ? 0xFF2A3540 : 0xFF202B36;
        int borderColor = isHovered ? accent : 0xFF3A4753;
        drawSoftRect(guiGraphics, x, y, width, height, 5, bgColor, borderColor);

        // 轻量强调线：让它更像信息 Widget，而不是一块放大的普通按钮。
        guiGraphics.fill(RenderType.gui(), x, y + 8, x + 3, y + height - 8, 0xFF8A753D);

        drawText(guiGraphics, "💰", x + 12, y + 10, accent);
        drawText(guiGraphics, "经济系统", x + 31, y + 10, TABLET_TEXT_COLOR);

        int marketTotal = snapshot.salesOrderCount() + snapshot.demandOrderCount();
        String stateText;
        int stateColor;
        if (!snapshot.loaded()) {
            stateText = "● 同步中";
            stateColor = 0xFFFFC857;
        } else if (!snapshot.available() || !snapshot.compatible()) {
            stateText = "● 暂不可用";
            stateColor = 0xFFFF7A88;
        } else if (marketTotal > 0) {
            stateText = "● 市场活跃";
            stateColor = 0xFF67D391;
        } else {
            stateText = "● 市场空闲";
            stateColor = TABLET_MUTED_TEXT_COLOR;
        }
        drawText(guiGraphics, stateText, x + width - mc.font.width(stateText) - 11, y + 10, stateColor);

        int dividerY = y + 28;
        guiGraphics.fill(RenderType.gui(), x + 11, dividerY, x + width - 11, dividerY + 1, 0xFF33424F);

        int statsLeft = x + 11;
        int statsWidth = width - 22;
        int statWidth = statsWidth / 3;
        int labelY = y + 36;

        drawEconomyMetric(guiGraphics, statsLeft, labelY, statWidth, "梦鱼币",
            economyMetricValue(snapshot, snapshot.balance()), 0xFFFFC857);
        drawEconomyMetric(guiGraphics, statsLeft + statWidth, labelY, statWidth, "领地",
            economyMetricValue(snapshot, snapshot.ownedTerritoryCount()), 0xFF67D391);
        drawEconomyMetric(guiGraphics, statsLeft + statWidth * 2, labelY, statsWidth - statWidth * 2, "市场挂单",
            economyMetricValue(snapshot, marketTotal), 0xFF5FC8F5);

        int separatorTop = y + 35;
        int separatorBottom = Math.min(y + height - 22, y + 61);
        if (separatorBottom > separatorTop) {
            guiGraphics.fill(RenderType.gui(), statsLeft + statWidth - 1, separatorTop,
                statsLeft + statWidth, separatorBottom, 0xFF33424F);
            guiGraphics.fill(RenderType.gui(), statsLeft + statWidth * 2 - 1, separatorTop,
                statsLeft + statWidth * 2, separatorBottom, 0xFF33424F);
        }

        String footer;
        if (!snapshot.loaded()) {
            footer = "正在读取经济数据...";
        } else if (!snapshot.available() || !snapshot.compatible()) {
            footer = snapshot.statusText().isBlank() ? "经济功能暂时不可用" : snapshot.statusText();
        } else if (!snapshot.currentTerritoryName().isBlank()) {
            String relation = localizeEconomyRelationship(snapshot.currentRelationship());
            footer = relation.isBlank()
                ? "当前位置 · " + snapshot.currentTerritoryName()
                : "当前位置 · " + snapshot.currentTerritoryName() + " · " + relation;
        } else {
            footer = "出售 " + snapshot.salesOrderCount() + " · 求购 " + snapshot.demandOrderCount();
        }

        String enterText = isHovered ? "进入 ›" : "打开 ›";
        int enterWidth = mc.font.width(enterText);
        int footerMaxWidth = Math.max(24, width - 34 - enterWidth);
        drawText(guiGraphics, fitDashboardText(footer, footerMaxWidth), x + 12, y + height - 15, TABLET_MUTED_TEXT_COLOR);
        drawText(guiGraphics, enterText, x + width - enterWidth - 11, y + height - 15,
            isHovered ? accent : 0xFF8293A1);
    }

    private void drawEconomyMetric(GuiGraphics guiGraphics, int x, int labelY, int width,
                                   String label, String value, int valueColor) {
        drawText(guiGraphics, label, x + 3, labelY, TABLET_MUTED_TEXT_COLOR);
        drawText(guiGraphics, fitDashboardText(value, Math.max(18, width - 7)), x + 3, labelY + 14, valueColor);
    }

    private String economyMetricValue(EconomyTerminalClientCache.Snapshot snapshot, int value) {
        if (!snapshot.loaded() || !snapshot.available() || !snapshot.compatible()) return "--";
        if (value >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000.0);
        if (value >= 10_000) return String.format(java.util.Locale.ROOT, "%.1fK", value / 1_000.0);
        return String.valueOf(value);
    }

    private void drawDashboardHistoryCard(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                          boolean isHovered) {
        int accent = 0xFFFFC857;
        drawSoftRect(guiGraphics, x, y, width, height, 4,
            isHovered ? 0xFF2B3742 : 0xFF222E3A,
            isHovered ? accent : 0xFF3B4650);
        guiGraphics.fill(RenderType.gui(), x, y + 7, x + 2, y + height - 7, 0x668A753D);

        drawText(guiGraphics, LEFT_BUTTON_ICONS[8], x + 10, y + 10, accent);
        drawText(guiGraphics, "历史", x + 29, y + 10, TABLET_TEXT_COLOR);
        String state = !historyLoaded ? "同步中" : historyTotalEventCount + " 条";
        int stateX = x + width - mc.font.width(state) - 10;
        if (stateX > x + 55) {
            drawText(guiGraphics, state, stateX, y + 10, TABLET_MUTED_TEXT_COLOR);
        }

        int dividerY = y + 28;
        if (height >= 48) {
            guiGraphics.fill(RenderType.gui(), x + 10, dividerY, x + width - 10, dividerY + 1, 0xFF36434F);
        }

        int footerY = y + height - 15;
        int bodyY = y + 37;
        if (!historyLoaded) {
            drawText(guiGraphics, fitDashboardText("正在读取世界年表", width - 20), x + 10, bodyY,
                TABLET_MUTED_TEXT_COLOR);
        } else if (cachedHistory.isEmpty()) {
            drawText(guiGraphics, fitDashboardText("尚未留下公开历史", width - 20), x + 10, bodyY,
                TABLET_TEXT_COLOR);
            if (bodyY + 16 < footerY - 3) {
                drawText(guiGraphics, fitDashboardText("故事事件会记录在这里", width - 20), x + 10, bodyY + 16,
                    TABLET_MUTED_TEXT_COLOR);
            }
        } else {
            Packet_WorldHistoryResponse.HistoryEntry latest = cachedHistory.get(cachedHistory.size() - 1);
            HistoryPresentation presentation = getHistoryPresentation(latest);
            drawText(guiGraphics, fitDashboardText(presentation.title(), width - 20), x + 10, bodyY,
                TABLET_TEXT_COLOR);
            if (bodyY + 16 < footerY - 3) {
                drawText(guiGraphics, fitDashboardText(presentation.subtitle(), width - 20), x + 10, bodyY + 16,
                    TABLET_MUTED_TEXT_COLOR);
            }
        }

        if (height >= 66) {
            drawText(guiGraphics, isHovered ? "打开世界年表 ›" : "查看共同经历 ›", x + 10, footerY,
                isHovered ? accent : 0xFF8E9CA8);
        }
    }

    private void drawDashboardHelpCard(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                       boolean isHovered) {
        int accent = 0xFF67D391;
        drawSoftRect(guiGraphics, x, y, width, height, 4,
            isHovered ? 0xFF293B37 : 0xFF222E3A,
            isHovered ? accent : 0xFF3B4650);
        guiGraphics.fill(RenderType.gui(), x, y + 7, x + 2, y + height - 7, 0x6667D391);

        drawText(guiGraphics, LEFT_BUTTON_ICONS[1], x + 10, y + 10, accent);
        drawText(guiGraphics, "帮助", x + 29, y + 10, TABLET_TEXT_COLOR);
        String state = "新玩家";
        int stateX = x + width - mc.font.width(state) - 10;
        if (stateX > x + 55) {
            drawText(guiGraphics, state, stateX, y + 10, TABLET_MUTED_TEXT_COLOR);
        }

        int dividerY = y + 28;
        if (height >= 48) {
            guiGraphics.fill(RenderType.gui(), x + 10, dividerY, x + width - 10, dividerY + 1, 0xFF36434F);
        }

        int footerY = y + height - 15;
        int bodyY = y + 37;
        drawText(guiGraphics, fitDashboardText("从梦屿基础开始", width - 20), x + 10, bodyY,
            TABLET_TEXT_COLOR);
        if (bodyY + 16 < footerY - 3) {
            drawText(guiGraphics, fitDashboardText("操作、生存与服务器规则", width - 20), x + 10, bodyY + 16,
                TABLET_MUTED_TEXT_COLOR);
        }
        if (height >= 66) {
            drawText(guiGraphics, isHovered ? "打开新玩家指南 ›" : "查看入门帮助 ›", x + 10, footerY,
                isHovered ? accent : 0xFF8E9CA8);
        }
    }

    private void drawDashboardProfileCard(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                          boolean isHovered) {
        LocalPlayer player = mc.player;
        drawSoftRect(guiGraphics, x, y, width, height, 5,
            isHovered ? 0xFF1E2B36 : 0xFF18232D,
            isHovered ? 0xFF5B7890 : 0xFF314150);
        drawText(guiGraphics, "个人档案", x + 12, y + 10, TABLET_MUTED_TEXT_COLOR);

        if (player == null) {
            drawText(guiGraphics, "等待玩家数据...", x + 12, y + 30, TABLET_TEXT_COLOR);
            return;
        }

        Rank rank = PlayerRankManager.getPlayerRankClient(player);
        Title title = PlayerTitleManager.getPlayerTitleClient(player);
        int rankColor = rank == null ? 0xFF7AA8C7 : (0xFF000000 | (rank.getRankColor() & 0x00FFFFFF));

        int avatarSize = Math.max(28, Math.min(40, height / 3));
        int avatarX = x + 12;
        int avatarY = y + 28;
        PlayerInfo playerInfo = player.connection != null ? player.connection.getPlayerInfo(player.getUUID()) : null;
        if (playerInfo != null) {
            PlayerFaceRenderer.draw(guiGraphics, playerInfo.getSkin(), avatarX, avatarY, avatarSize);
        } else {
            drawSoftRect(guiGraphics, avatarX, avatarY, avatarSize, avatarSize, 3, TABLET_CARD_COLOR, TABLET_CARD_BORDER_COLOR);
        }

        int infoX = avatarX + avatarSize + 10;
        int infoWidth = Math.max(24, x + width - 12 - infoX);
        drawText(guiGraphics, fitDashboardText(player.getScoreboardName(), infoWidth), infoX, avatarY + 1, rankColor);
        String rankName = rank == null ? "NO RANK" : rank.getRankName();
        drawText(guiGraphics, fitDashboardText(rankName, infoWidth), infoX, avatarY + 15, rankColor);

        if (title != null && title.getTitleName() != null && !title.getTitleName().isBlank()) {
            int titleColor = 0xFF000000 | (title.getColor() & 0x00FFFFFF);
            drawText(guiGraphics, fitDashboardText(title.getTitleName(), infoWidth), infoX, avatarY + 29, titleColor);
        }

        int level = PlayerLevelManager.getPlayerLevelClient(player);
        float infection = PlayerInfectionManager.getCurrentInfectionClient(player);
        String status = infection >= 100 ? "感染者" : "幸存者";
        int statusColor = infection >= 100 ? 0xFFFF6677 : 0xFF50D890;
        int metaY = Math.min(y + height - 34, avatarY + avatarSize + 12);
        String levelText = "LV." + level;
        int levelBadgeWidth = Math.max(34, mc.font.width(levelText) + 16);
        drawSoftRect(guiGraphics, x + 12, metaY, levelBadgeWidth, 16, 3, 0xFF25323D, 0xFF344555);
        drawText(guiGraphics, levelText, x + 20, metaY + 4, 0xFFFFC857);
        int statusWidth = mc.font.width(status) + 16;
        int statusX = x + 12 + levelBadgeWidth + 6;
        drawSoftRect(guiGraphics, statusX, metaY, statusWidth, 16, 3, 0xFF25323D, 0xFF344555);
        drawText(guiGraphics, status, statusX + 8, metaY + 4, statusColor);

        PlayerData playerData = ClientCacheManager.getPlayerData(player.getUUID());
        long totalPlayTime = playerData == null ? 0L : playerData.getTotalPlayTime();
        String profileFooter = playerData != null && playerData.isZhuiguangMember()
            ? "逐光会成员 · 游玩 " + formatPlayDuration(totalPlayTime)
            : "游玩 " + formatPlayDuration(totalPlayTime);
        drawText(guiGraphics, fitDashboardText(profileFooter, width - 24),
            x + 12, y + height - 14, TABLET_MUTED_TEXT_COLOR);
    }

    private void drawDashboardStoryCard(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                        boolean isHovered) {
        int accent = 0xFF9BEA9B;
        drawSoftRect(guiGraphics, x, y, width, height, 5,
            isHovered ? 0xFF22343A : 0xFF1C2B31,
            isHovered ? accent : 0xFF314650);
        drawText(guiGraphics, LEFT_BUTTON_ICONS[3], x + 12, y + 11, accent);
        drawText(guiGraphics, "故事进展", x + 32, y + 11, TABLET_TEXT_COLOR);

        var stages = ClientCacheManager.getStoryStages();
        var stage = stages.values().stream()
            .filter(com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData::isCurrentStage)
            .findFirst()
            .orElse(null);
        String storyState;
        int storyStateColor;
        if (stage == null) {
            storyState = "故事同步中";
            storyStateColor = TABLET_MUTED_TEXT_COLOR;
        } else {
            int percent = Math.round(Math.max(0.0f, Math.min(1.0f,
                stage.getGlobalProgressPercentage())) * 100.0f);
            storyState = "全服进度 " + percent + "%";
            storyStateColor = percent >= 100 ? 0xFF78D6A3 : TABLET_MUTED_TEXT_COLOR;
        }
        drawText(guiGraphics, storyState, x + width - mc.font.width(storyState) - 12, y + 11,
            storyStateColor);
        if (stage == null) {
            drawText(guiGraphics, "当前阶段尚未同步", x + 12, y + 38, TABLET_MUTED_TEXT_COLOR);
        } else {
            String stageTitle = "阶段 " + stage.getStageNumber() + " · " + stage.getStageName();
            drawText(guiGraphics, fitDashboardText(stageTitle, width - 24), x + 12, y + 35, TABLET_TEXT_COLOR);
            if (height >= 126) {
                drawText(guiGraphics, fitDashboardText(stage.getStageDescription(), width - 24),
                    x + 12, y + 51, TABLET_MUTED_TEXT_COLOR);
            }
        }

        GuidanceViewData latestGuidance = GuidanceClientCache.getEntries().stream()
            .filter(entry -> entry.status() == GuidanceEntry.Status.ACTIVE)
            .findFirst()
            .orElseGet(() -> GuidanceClientCache.getEntries().stream().findFirst().orElse(null));
        int progressY = y + height - 12;
        int guidanceY = y + (height >= 126 ? 72 : 55);
        if (guidanceY <= progressY - 24) {
            String guidanceTitle = latestGuidance == null
                ? "等待新的故事线索"
                : "线索 · " + latestGuidance.title();
            drawText(guiGraphics, GUIDANCE_ICON, x + 12, guidanceY, 0xFF78D6A3);
            drawText(guiGraphics, fitDashboardText(guidanceTitle, width - 42), x + 30, guidanceY,
                latestGuidance == null ? TABLET_MUTED_TEXT_COLOR : TABLET_TEXT_COLOR);
        }

        if (stage != null) {
            float progress = Math.max(0.0f, Math.min(1.0f, stage.getGlobalProgressPercentage()));
            int percent = Math.round(progress * 100.0f);
            drawText(guiGraphics, "全服进度 " + percent + "%", x + 12, progressY - 13, TABLET_MUTED_TEXT_COLOR);
            drawProgressBar(guiGraphics, x + 12, progressY, Math.max(20, width - 24), 5, progress, accent);
        }
    }

    private void drawDashboardNoticeCard(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                         boolean isHovered) {
        int accent = 0xFF4FC3F7;
        drawSoftRect(guiGraphics, x, y, width, height, 5,
            isHovered ? 0xFF233440 : 0xFF1D2B35,
            isHovered ? accent : 0xFF314650);
        drawText(guiGraphics, LEFT_BUTTON_ICONS[2], x + 12, y + 10, accent);
        drawText(guiGraphics, NOTICE_UI_NAME, x + 32, y + 10, TABLET_TEXT_COLOR);

        NoticeData latest = cachedNotices.stream()
            .filter(this::isClientVisibleNotice)
            .max(java.util.Comparator.comparingLong(NoticeData::getPublishTime))
            .orElse(null);
        int unreadCount = 0;
        for (NoticeData notice : cachedNotices) {
            if (isClientVisibleNotice(notice)
                    && !cachedReadNoticeIds.contains(notice.getNoticeId())) unreadCount++;
        }

        String stateText = unreadCount > 0 ? unreadCount + " 条未读" : "全部已读";
        drawText(guiGraphics, stateText, x + width - mc.font.width(stateText) - 11, y + 10,
            unreadCount > 0 ? 0xFFFFC857 : 0xFF78D6A3);

        int footerY = y + height - 15;
        if (height >= 54) {
            guiGraphics.fill(RenderType.gui(), x + 12, y + 28, x + width - 12, y + 29, 0xFF34434E);
        }

        if (latest == null) {
            int emptyY = height >= 70 ? y + 38 : y + height - 16;
            drawText(guiGraphics, "暂无公告", x + 12, emptyY, TABLET_TEXT_COLOR);
            if (emptyY + 16 + mc.font.lineHeight <= footerY - 2) {
                drawText(guiGraphics, fitDashboardText("新的服务器广播会显示在这里", width - 24),
                    x + 12, emptyY + 16, TABLET_MUTED_TEXT_COLOR);
            }
        } else {
            int titleY = height >= 70 ? y + 38 : y + height - 16;
            String categoryLabel = latest.isGameNotice() ? "梦屿广播" : "服务器公告";
            String title = "【" + categoryLabel + "】 "
                + safeNoticeText(latest.getNoticeTitle(), "无标题");
            drawText(guiGraphics, fitDashboardText(title, width - 24), x + 12,
                titleY, TABLET_TEXT_COLOR);
            if (titleY + 16 + mc.font.lineHeight <= footerY - 2) {
                String content = latest.getNoticeContent() == null ? "" : latest.getNoticeContent().replace('\n', ' ');
                if (!content.isBlank()) {
                    drawText(guiGraphics, fitDashboardText(content, width - 24), x + 12, titleY + 16,
                        TABLET_MUTED_TEXT_COLOR);
                }
            }
        }

        if (height >= 76) {
            drawText(guiGraphics, isHovered ? "打开广播 ›" : "查看全部广播 ›", x + 12, footerY,
                isHovered ? accent : 0xFF8293A1);
        }
    }

    private void drawDashboardMessageCard(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                          boolean isHovered) {
        int accent = 0xFF8CCEFF;
        drawSoftRect(guiGraphics, x, y, width, height, 5,
            isHovered ? 0xFF213846 : 0xFF1B2D38,
            isHovered ? accent : 0xFF315063);
        drawText(guiGraphics, LEFT_BUTTON_ICONS[NPC_MESSAGE_PAGE_INDEX], x + 12, y + 10, accent);
        drawText(guiGraphics, "NPC 私信", x + 32, y + 10, TABLET_TEXT_COLOR);

        int unreadCount = NpcMessageClientCache.getUnreadCount();
        String state = !NpcMessageClientCache.isLoaded()
            ? "同步中"
            : (unreadCount > 0 ? unreadCount + " 条未读" : "全部已读");
        drawText(guiGraphics, state, x + width - mc.font.width(state) - 12, y + 10,
            unreadCount > 0 ? 0xFFFFC857 : TABLET_MUTED_TEXT_COLOR);

        List<NpcConversationViewData> conversations = NpcMessageClientCache.getConversations();
        if (conversations.isEmpty()) {
            drawText(guiGraphics, "还没有建立 NPC 私人频道", x + 12, y + 37, TABLET_MUTED_TEXT_COLOR);
            drawText(guiGraphics, "与剧情 NPC 交谈后会在这里留下记录", x + 12, y + 54, TABLET_MUTED_TEXT_COLOR);
        } else {
            NpcConversationViewData latest = conversations.get(0);
            drawText(guiGraphics, fitDashboardText(latest.npcName() + " · " + latest.relationName(), width - 24),
                x + 12, y + 38, 0xFFB8DDF4);
            drawText(guiGraphics, fitDashboardText(latestMessagePreview(latest), width - 24),
                x + 12, y + 56, TABLET_MUTED_TEXT_COLOR);
        }
        drawText(guiGraphics, isHovered ? "进入私人频道 ›" : "查看会话 ›", x + 12, y + height - 16,
            isHovered ? accent : 0xFF8293A1);
    }

    private void drawDashboardDock(GuiGraphics guiGraphics, float mouseX, float mouseY,
                                   int x, int y, int width, int height) {
        String[] icons = {"⌂", LEFT_BUTTON_ICONS[0], LEFT_BUTTON_ICONS[2], LEFT_BUTTON_ICONS[NPC_MESSAGE_PAGE_INDEX], LEFT_BUTTON_ICONS[3], LEFT_BUTTON_ICONS[9]};
        String[] labels = {"主页", "档案", "广播", "短信", "故事", "设置"};
        int dockWidth = Math.min(width - 48, 276);
        int dockX = x + (width - dockWidth) / 2;
        drawSoftRect(guiGraphics, dockX, y, dockWidth, height, 5, 0xFF141C24, 0xFF334657);

        int horizontalInset = 5;
        int verticalInset = 3;
        int innerGap = 2;
        int itemAreaWidth = dockWidth - horizontalInset * 2
            - innerGap * (DASHBOARD_DOCK_TARGETS.length - 1);
        int itemWidth = itemAreaWidth / DASHBOARD_DOCK_TARGETS.length;
        int itemX = dockX + horizontalInset
            + (itemAreaWidth - itemWidth * DASHBOARD_DOCK_TARGETS.length) / 2;
        for (int i = 0; i < DASHBOARD_DOCK_TARGETS.length; i++) {
            int target = DASHBOARD_DOCK_TARGETS[i];
            boolean hovered = isDashboardHovered(mouseX, mouseY, itemX, y + 2,
                itemWidth, height - 4);
            boolean active = target == -1;
            if (hovered || active) {
                drawSoftRect(guiGraphics, itemX + 2, y + verticalInset, itemWidth - 4,
                    height - verticalInset * 2, 4,
                    active ? 0xFF294052 : 0xFF263744,
                    hovered ? 0xFF4A6276 : 0x00000000);
            }

            dashboardDockX1[i] = itemX;
            dashboardDockY1[i] = y + 2;
            dashboardDockX2[i] = itemX + itemWidth;
            dashboardDockY2[i] = y + height - 2;

            int iconWidth = mc.font.width(icons[i]);
            drawText(guiGraphics, icons[i], itemX + (itemWidth - iconWidth) / 2, y + 4,
                active ? 0xFF8CCEFF : TABLET_TEXT_COLOR);
            int labelWidth = mc.font.width(labels[i]);
            drawText(guiGraphics, labels[i], itemX + (itemWidth - labelWidth) / 2, y + height - 13,
                active ? 0xFF8CCEFF : TABLET_MUTED_TEXT_COLOR);

            if ((target == 2 && hasUnreadNoticesGlobal)
                    || (target == NPC_MESSAGE_PAGE_INDEX && NpcMessageClientCache.getUnreadCount() > 0)
                    || (target == 3 && hasOpenStoryProgress())) {
                drawSoftRect(guiGraphics, itemX + itemWidth - 7, y + 5, 4, 4, 2,
                    0xFFFF7A88, 0x00000000);
            }
            itemX += itemWidth + innerGap;
        }
    }

    private boolean hasOpenStoryProgress() {
        return ClientCacheManager.getStoryStages().values().stream()
            .filter(com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData::isCurrentStage)
            .findFirst()
            .map(stage -> stage.getGlobalProgressPercentage() < 1.0f)
            .orElse(false);
    }

    private void drawDashboardPlayerBar(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        LocalPlayer player = mc.player;
        drawSoftRect(guiGraphics, x, y, width, height, 2, 0xFF202B36, TABLET_CARD_BORDER_COLOR);
        if (player == null) return;

        int avatarSize = Math.max(18, height - 12);
        int avatarX = x + 10;
        int avatarY = y + (height - avatarSize) / 2;
        PlayerInfo playerInfo = player.connection != null ? player.connection.getPlayerInfo(player.getUUID()) : null;
        if (playerInfo != null) {
            PlayerFaceRenderer.draw(guiGraphics, playerInfo.getSkin(), avatarX, avatarY, avatarSize);
        } else {
            drawSoftRect(guiGraphics, avatarX, avatarY, avatarSize, avatarSize, 2, TABLET_CARD_COLOR, TABLET_CARD_BORDER_COLOR);
        }

        PlayerData playerData = ClientCacheManager.getPlayerData(player.getUUID());
        long registrationTime = 0L;
        long totalPlayTime = 0L;
        if (playerData != null) {
            registrationTime = playerData.getRegistrationTime() > 0 ? playerData.getRegistrationTime() : playerData.getLastLoginTime();
            totalPlayTime = playerData.getTotalPlayTime();
        }

        String playerText = player.getScoreboardName();
        int textY = y + (height - mc.font.lineHeight) / 2;
        int cursorX = avatarX + avatarSize + 10;
        drawText(guiGraphics, playerText, cursorX, textY, TABLET_TEXT_COLOR);

        int registerX = x + Math.max(width / 3, 170);
        drawText(guiGraphics, "注册", registerX, textY, TABLET_MUTED_TEXT_COLOR);
        drawText(guiGraphics, formatProfileDateTime(registrationTime), registerX + 32, textY, TABLET_TEXT_COLOR);

        int playX = x + Math.max(width * 2 / 3, registerX + 128);
        drawText(guiGraphics, "游玩时长", playX, textY, TABLET_MUTED_TEXT_COLOR);
        drawText(guiGraphics, formatPlayDuration(totalPlayTime), playX + 56, textY, TABLET_TEXT_COLOR);
    }

    /**
     * 绘制左侧按钮区域 + 服务器信息区域
     */
    private void renderLeftDynamicIsland(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // ==================== 布局参数 ====================
        int sideMargin = 14;
        int cardSpacing = 6;
        int columnCount = 2;
        int cardWidth = (leftPanelWidth - sideMargin * 2 - cardSpacing) / columnCount;
        int cardHeight = 42;

        int totalButtons = LEFT_BUTTON_ICONS.length;

        // 按钮位置：位于平板内容区内，参考新闻流卡片排布
        int headerY = Math.max(44, virtualHeight / 10);
        int buttonStartY = headerY + 30;

        // 滑入动画
        int animOffsetY = 0;
        if (!isClosing && !skipAnimation) {
            float animDuration = 500f;
            float progress = Math.min(1.0f, (float) (Util.getMillis() - openTime) / animDuration);
            progress = 1.0f - (float) Math.pow(1.0f - progress, 3);
            animOffsetY = (int) ((1.0f - progress) * 50);
        }

        // 更新动画时间
        arrowAnimTime = Util.getMillis();

        // 转换鼠标坐标
        float virtualMouseX = mouseX / uiScale;
        float virtualMouseY = mouseY / uiScale;

        // ==================== 绘制按钮列表 ====================
        int currentButtonStartY = buttonStartY + animOffsetY;

        String terminalTitle = "DreamingFish Terminal";
        guiGraphics.drawString(mc.font, terminalTitle, sideMargin, headerY, 0xFF20242C);
        guiGraphics.drawString(mc.font, "选择一个服务器模块", sideMargin, headerY + mc.font.lineHeight + 4, 0xFF687080);

        for (int i = 0; i < totalButtons; i++) {
            int row = i / columnCount;
            int column = i % columnCount;
            int buttonX = sideMargin + column * (cardWidth + cardSpacing);
            int buttonY = currentButtonStartY + row * (cardHeight + cardSpacing);

            // 存储点击区域
            leftButtonX1[i] = buttonX;
            leftButtonY1[i] = buttonY;
            leftButtonX2[i] = buttonX + cardWidth;
            leftButtonY2[i] = buttonY + cardHeight;

            boolean isSelected = (i == selectedLeftButtonIndex);
            boolean isHovered = (virtualMouseX >= buttonX && virtualMouseX <= buttonX + cardWidth &&
                                virtualMouseY >= buttonY && virtualMouseY <= buttonY + cardHeight);

            // 检查是否有未读/未完成内容
            boolean hasUnread = false;
            if (i == 2 && hasUnreadNoticesGlobal) {
                // 公告按钮：未读公告（使用全局标记）
                hasUnread = true;
            } else if (i == 3) {
                // 故事按钮：未完成任务
                hasUnread = com.hhy.dreamingfishcore.client.cache.ClientCacheManager.hasUnfinishedTasks();
            }

            drawTabletLauncherCard(guiGraphics, buttonX, buttonY, cardWidth, cardHeight,
                isSelected, isHovered, LEFT_BUTTON_ICONS[i], LEFT_BUTTON_NAMES[i], LEFT_BUTTON_COLORS[i], hasUnread);
        }

        // ==================== 绘制服务器信息区域（版本号上方） ====================
        int infoHeight = 34;
        int versionBottomMargin = 8;  // 版本号和信息区之间的间距
        int versionHeight = mc.font.lineHeight + 10;
        int infoY = virtualHeight - versionHeight - infoHeight - versionBottomMargin;

        // 服务器信息区域滑入动画（从下往上，比按钮稍晚一点）
        int infoAnimOffsetY = 0;
        if (!isClosing && !skipAnimation) {
            float infoAnimDuration = 600f;
            float infoProgress = Math.min(1.0f, (float) (Util.getMillis() - openTime) / infoAnimDuration);
            infoProgress = 1.0f - (float) Math.pow(1.0f - infoProgress, 3);
            infoAnimOffsetY = (int) ((1.0f - infoProgress) * 40);  // 从下往上滑入 40 像素
        }

        // 获取服务器数据
        int onlinePlayers = mc.player != null && mc.player.connection != null ?
            mc.player.connection.getOnlinePlayers().size() : 0;
        int maxPlayers = 20;
        float tps = 20.0f;

        drawTabletServerInfo(guiGraphics, sideMargin, infoY, leftPanelWidth - sideMargin * 2, infoHeight,
            infoAnimOffsetY, onlinePlayers, maxPlayers, tps);
    }

    /**
     * 绘制平板首页功能卡片。
     */
    private void drawTabletLauncherCard(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                        boolean isSelected, boolean isHovered, String icon, String label,
                                        int accentColor, boolean hasUnread) {
        int bgColor = isSelected ? 0xFFFFFFFF : (isHovered ? 0xFFF8FAFF : 0xFFF1F3F8);
        int borderColor = isSelected ? (0xFF000000 | (accentColor & 0x00FFFFFF)) : (isHovered ? 0xFFB8C3D6 : 0xFFE0E4EC);

        drawSoftRect(guiGraphics, x, y, width, height, 2, bgColor, borderColor);
        guiGraphics.fill(RenderType.gui(), x + 1, y + height - 3, x + width - 1, y + height - 1,
            0xFF000000 | (accentColor & 0x00FFFFFF));

        int iconColor = 0xFF000000 | (accentColor & 0x00FFFFFF);
        guiGraphics.drawString(mc.font, icon, x + 8, y + 8, iconColor);

        String displayLabel = label;
        int maxLabelWidth = width - 14;
        if (mc.font.width(displayLabel) > maxLabelWidth) {
            displayLabel = ServerScreenUI_RendererUtils.truncateText(mc.font, displayLabel, maxLabelWidth - mc.font.width("...")) + "...";
        }
        guiGraphics.drawString(mc.font, displayLabel, x + 8, y + 24, isSelected ? 0xFF161A22 : 0xFF343946);

        if (hasUnread) {
            drawSoftRect(guiGraphics, x + width - 12, y + 6, 6, 6, 2, 0xFFFF7A88, 0xFFFFB8C0);
        }
    }

    /**
     * 绘制平板底部状态条。
     */
    private void drawTabletServerInfo(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                      int offsetY, int onlinePlayers, int maxPlayers, float tps) {
        y += offsetY;
        drawSoftRect(guiGraphics, x, y, width, height, 2, 0xFF202B36, TABLET_CARD_BORDER_COLOR);
        drawText(guiGraphics, "ONLINE", x + 8, y + 6, TABLET_MUTED_TEXT_COLOR);
        drawText(guiGraphics, onlinePlayers + "/" + maxPlayers, x + 8, y + 18, TABLET_TEXT_COLOR);

        String tpsText = String.format("TPS %.1f", tps);
        int tpsWidth = mc.font.width(tpsText);
        drawText(guiGraphics, tpsText, x + width - tpsWidth - 8, y + 18, TABLET_TEXT_COLOR);
    }

    private void drawTabletStatusBar(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                     int onlinePlayers, int maxPlayers, float tps) {
        drawSoftRect(guiGraphics, x + 2, y + 7, 4, 4, 2, 0xFF67D391, 0x00000000);
        int onlineLabelX = x + 10;
        drawText(guiGraphics, "在线", onlineLabelX, y + 4, TABLET_MUTED_TEXT_COLOR);
        String onlineText = onlinePlayers + "/" + maxPlayers;
        drawText(guiGraphics, onlineText, onlineLabelX + mc.font.width("在线") + 12, y + 4, TABLET_TEXT_COLOR);
        String tpsText = String.format("TPS %.1f", tps);
        drawText(guiGraphics, tpsText, x + width - mc.font.width(tpsText) - 8, y + 4, TABLET_TEXT_COLOR);
    }

    private String formatWorldTime() {
        if (mc.level == null) {
            return "--:--";
        }
        long dayTime = mc.level.getDayTime() % 24000L;
        int hour = (int) ((dayTime / 1000L + 6L) % 24L);
        int minute = (int) ((dayTime % 1000L) * 60L / 1000L);
        return String.format("%02d:%02d", hour, minute);
    }

    /**
     * 绘制圆角矩形
     * @param x 左上角 X 坐标
     * @param y 左上角 Y 坐标
     * @param width 宽度
     * @param height 高度
     * @param radius 圆角半径
     * @param fillColor 填充颜色（ARGB）
     * @param borderColor 边框颜色（ARGB）
     */
    private void drawRoundedRect(GuiGraphics guiGraphics, int x, int y, int width, int height, int radius, int fillColor, int borderColor) {
        ServerScreenUI_RendererUtils.drawRoundedRect(guiGraphics, x, y, width, height, radius, fillColor, borderColor);
    }

    /**
     * 绘制带直角边框的矩形
     * @param x 左上角 X 坐标
     * @param y 左上角 Y 坐标
     * @param width 宽度
     * @param height 高度
     * @param radius 圆角半径（未使用，保留参数兼容性）
     * @param fillColor 填充颜色（ARGB）
     * @param borderColor 边框颜色（ARGB）
     */
    private void drawRoundedRectOutline(GuiGraphics guiGraphics, int x, int y, int width, int height, int radius, int fillColor, int borderColor) {
        ServerScreenUI_RendererUtils.drawRoundedRectOutline(guiGraphics, x, y, width, height, radius, fillColor, borderColor);
    }

    /**
     * 绘制信息框（简单的半透明边框）
     * @param x 左上角 X 坐标
     * @param y 左上角 Y 坐标
     * @param width 宽度
     * @param height 高度
     */

    /**
     * 绘制游戏化卡片背景
     * @param x 左上角 X 坐标
     * @param y 左上角 Y 坐标
     * @param width 宽度
     * @param height 高度
     * @param themeColor 主题色（用于左侧装饰条和渐变）
     * @param isHovered 是否鼠标悬停
     */
    private void drawGameCard(GuiGraphics guiGraphics, int x, int y, int width, int height, int themeColor, boolean isHovered) {
        ServerScreenUI_RendererUtils.drawGameCard(guiGraphics, x, y, width, height, themeColor, isHovered);
    }

    private void drawDoubleBorderBox(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        ServerScreenUI_RendererUtils.drawDoubleBorderBox(guiGraphics, x, y, width, height);
    }

    /**
     * 渲染圆角盒子（参考 ConnectScreenMixin）
     */
    private void renderRoundedBox(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
        ServerScreenUI_RendererUtils.renderRoundedBox(guiGraphics, x1, y1, x2, y2, color);
    }

    /**
     * 绘制渐变梦幻色框 (已移至 RendererUtils)
     */
    private void drawGradientBox(GuiGraphics guiGraphics, int x, int y, int width, int height, int gradientType) {
        ServerScreenUI_RendererUtils.drawGradientBox(guiGraphics, x, y, width, height, gradientType);
    }

    /**
     * 获取渐变色 (已移至 RendererUtils)
     */
    private int getGradientColor(int type, float ratio) {
        return ServerScreenUI_RendererUtils.getGradientColor(type, ratio);
    }

    /**
     * 绘制圆角进度条 (已移至 RendererUtils)
     */
    private void drawRoundedProgressBar(GuiGraphics guiGraphics, int x, int y, int width, int height, float pct, int color) {
        ServerScreenUI_RendererUtils.drawRoundedProgressBar(guiGraphics, x, y, width, height, pct, color);
    }

    /**
     * 根据 Rank 等级获取对应颜色 (已移至 RendererUtils)
     */
    private int getRankColor(int rankLevel) {
        return ServerScreenUI_RendererUtils.getRankColor(rankLevel);
    }

    /**
     * 根据感染值百分比计算动态颜色 (已移至 RendererUtils)
     */
    private int getInfectionColor(float infectionPercent) {
        return ServerScreenUI_RendererUtils.getInfectionColor(infectionPercent);
    }

    /**
     * 检查关闭动画是否完成
     * @return true 如果关闭动画已完成
     */
    private boolean isCloseAnimationComplete() {
        if (!isClosing) return false;
        long elapsed = Util.getMillis() - closeTime;
        return elapsed >= CLOSE_ANIMATION_DURATION;
    }

    // ==================== 键盘事件处理 ====================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC 键：Rank 管理返回个人档案，二级界面返回终端主页，主页关闭 UI
        if (keyCode == 256) {
            if (selectedLeftButtonIndex == 0 && profileRankManagerOpen) {
                profileRankManagerOpen = false;
                rankOptionClickAreas.clear();
                displayedRankOptions.clear();
                return true;
            }
            if (selectedLeftButtonIndex == 2 && isNoticeDetailOpen()) {
                clearNoticeDetail();
                return true;
            }
            if (selectedLeftButtonIndex == NPC_MESSAGE_PAGE_INDEX && isNpcMessageDetailOpen()) {
                clearNpcMessageDetail();
                return true;
            }
            if (selectedLeftButtonIndex >= 0) {
                selectedLeftButtonIndex = -1;
                profileRankManagerOpen = false;
                selectedStageId = null;
                taskScrollOffset = 0;
                stageScrollOffset = 0;
                return true;
            }
            if (isClosing) return true;  // 如果已经在关闭中，不再响应
            isClosing = true;
            closeTime = Util.getMillis();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 将屏幕坐标转换为虚拟坐标
        double virtualMouseX = mouseX / uiScale;
        double virtualMouseY = mouseY / uiScale;

        int tabletMarginX = Math.max(12, virtualWidth / 28);
        int tabletMarginY = Math.max(10, virtualHeight / 14);
        if (selectedLeftButtonIndex >= 0 &&
            virtualMouseX >= tabletMarginX + 12 && virtualMouseX <= tabletMarginX + 30 &&
            virtualMouseY >= tabletMarginY + 10 && virtualMouseY <= tabletMarginY + 30) {
            if (selectedLeftButtonIndex == 0 && profileRankManagerOpen) {
                profileRankManagerOpen = false;
                rankOptionClickAreas.clear();
                displayedRankOptions.clear();
                return true;
            }
            if (selectedLeftButtonIndex == 2 && isNoticeDetailOpen()) {
                clearNoticeDetail();
                return true;
            }
            if (selectedLeftButtonIndex == NPC_MESSAGE_PAGE_INDEX && isNpcMessageDetailOpen()) {
                clearNpcMessageDetail();
                return true;
            }
            selectedLeftButtonIndex = -1;
            profileRankManagerOpen = false;
            selectedStageId = null;
            taskScrollOffset = 0;
            stageScrollOffset = 0;
            return true;
        }

        // ==================== 检查一级模块按钮点击 ====================
        if (selectedLeftButtonIndex < 0) {
            // Dock 优先于主页卡片处理，避免重叠区域抢占点击。
            for (int i = 0; i < DASHBOARD_DOCK_TARGETS.length; i++) {
                if (virtualMouseX >= dashboardDockX1[i] && virtualMouseX <= dashboardDockX2[i]
                        && virtualMouseY >= dashboardDockY1[i] && virtualMouseY <= dashboardDockY2[i]) {
                    int target = DASHBOARD_DOCK_TARGETS[i];
                    if (target >= 0 && target < LEFT_BUTTON_ICONS.length) {
                        selectedLeftButtonIndex = target;
                        handleLeftButtonClick(target);
                    }
                    return true;
                }
            }
        }
        if (selectedLeftButtonIndex < 0) {
            for (int i = 0; i < LEFT_BUTTON_ICONS.length; i++) {
                if (virtualMouseX >= leftButtonX1[i] && virtualMouseX <= leftButtonX2[i] &&
                    virtualMouseY >= leftButtonY1[i] && virtualMouseY <= leftButtonY2[i]) {
                    selectedLeftButtonIndex = i;
                    handleLeftButtonClick(i);
                    return true;
                }
            }
        }

        // 计算右侧面板偏移（考虑动画）
        int rightOffsetY = 0;
        if (!isClosing) {
            float rightAnimDuration = 800f;
            float rightProgress = Math.min(1.0f, (float) (Util.getMillis() - openTime) / rightAnimDuration);
            rightProgress = 1.0f - (float) Math.pow(1.0f - rightProgress, 3);
            rightOffsetY = (int) ((1.0f - rightProgress) * 100);
        }

        // 检查是否点击了金币框（仅主页面可点击）
        int[] goldBoxClick = pageRenderer.getGoldBoxClick();
        if (selectedLeftButtonIndex == 0 && !profileRankManagerOpen
                && virtualMouseX >= goldBoxClick[0] && virtualMouseX <= goldBoxClick[2]
                && virtualMouseY >= goldBoxClick[1] + rightOffsetY && virtualMouseY <= goldBoxClick[3] + rightOffsetY) {
            return true;
        }

        // 检查是否点击了领地框（仅主页面可点击）
        int[] territoryBoxClick = pageRenderer.getTerritoryBoxClick();
        if (selectedLeftButtonIndex == 0 && !profileRankManagerOpen
                && virtualMouseX >= territoryBoxClick[0] && virtualMouseX <= territoryBoxClick[2]
                && virtualMouseY >= territoryBoxClick[1] + rightOffsetY && virtualMouseY <= territoryBoxClick[3] + rightOffsetY) {
            return true;
        }

        if (selectedLeftButtonIndex == 0 && !profileRankManagerOpen
                && virtualMouseX >= rankBoxClickX1 && virtualMouseX <= rankBoxClickX2
                && virtualMouseY >= rankBoxClickY1 && virtualMouseY <= rankBoxClickY2) {
            profileRankManagerOpen = true;
            return true;
        }

        // ==================== 检查公告分类与列表点击 ====================
        if (selectedLeftButtonIndex == 2) {  // 梦屿广播页面
            // 详情态优先处理返回和 tab，完全禁用上一帧可能残留的列表热区。
            if (isNoticeDetailOpen()) {
                for (NoticeStageClickArea area : noticeStageClickAreas) {
                    if (area.contains(virtualMouseX, virtualMouseY)) {
                        selectedNoticeStageId = area.stageId().isEmpty() ? "" : area.stageId();
                        noticeScrollOffset = 0L;
                        clearNoticeDetail();
                        return true;
                    }
                }
                int[] detailBackArea = noticeDetailBackArea;
                if (virtualMouseX >= detailBackArea[0] && virtualMouseX <= detailBackArea[2]
                        && virtualMouseY >= detailBackArea[1]
                        && virtualMouseY <= detailBackArea[3]) {
                    clearNoticeDetail();
                    return true;
                }
                for (int tab = 0; tab < noticeTabClickAreas.length; tab++) {
                    int[] tabArea = noticeTabClickAreas[tab];
                    if (virtualMouseX >= tabArea[0] && virtualMouseX <= tabArea[2]
                            && virtualMouseY >= tabArea[1]
                            && virtualMouseY <= tabArea[3]) {
                        clearNoticeDetail();
                        selectedNoticeTab = tab;
                        noticeScrollOffset = 0L;
                        return true;
                    }
                }
                return true;
            }

            for (NoticeStageClickArea area : noticeStageClickAreas) {
                if (area.contains(virtualMouseX, virtualMouseY)) {
                    selectedNoticeStageId = area.stageId().isEmpty() ? "" : area.stageId();
                    noticeScrollOffset = 0L;
                    return true;
                }
            }

            for (int tab = 0; tab < noticeTabClickAreas.length; tab++) {
                int[] tabArea = noticeTabClickAreas[tab];
                if (virtualMouseX >= tabArea[0] && virtualMouseX <= tabArea[2]
                        && virtualMouseY >= tabArea[1]
                        && virtualMouseY <= tabArea[3]) {
                    if (selectedNoticeTab != tab) {
                        selectedNoticeTab = tab;
                        noticeScrollOffset = 0L;
                        clearNoticeDetail();
                    }
                    return true;
                }
            }

            List<NoticeData> currentNotices = getNoticesForSelectedTab();
            if (currentNotices.isEmpty()) return true;
            int[] noticeArea = pageRenderer.getNoticeClickArea();
            if (virtualMouseX >= noticeArea[0] && virtualMouseX <= noticeArea[2] &&
                virtualMouseY >= noticeArea[1] && virtualMouseY <= noticeArea[3]) {
                // 计算点击的是哪个公告卡片
                int cardMargin = 8;
                int cardHeight = NOTICE_FEED_CARD_HEIGHT;
                int areaWidth = noticeArea[2] - noticeArea[0];
                int columns = areaWidth > 430 ? 2 : 1;
                int cardWidth = (areaWidth - cardMargin * (columns - 1)) / columns;
                int relativeX = (int) virtualMouseX - noticeArea[0];
                int relativeY = (int) virtualMouseY - noticeArea[1];
                int clickedColumn = relativeX / (cardWidth + cardMargin);
                int clickedRow = relativeY / (cardHeight + cardMargin);
                int clickedCardIndex = clickedRow * columns + clickedColumn;

                int cellX = relativeX - clickedColumn * (cardWidth + cardMargin);
                int cellY = relativeY - clickedRow * (cardHeight + cardMargin);
                int maxCards = Math.min(noticeVisibleCardCapacity, currentNotices.size()
                    - (int) noticeScrollOffset);

                if (clickedColumn >= 0 && clickedColumn < columns && cellX >= 0 && cellX < cardWidth
                        && cellY >= 0 && cellY < cardHeight
                        && clickedCardIndex >= 0 && clickedCardIndex < maxCards) {
                    int noticeIndex = (int) (clickedCardIndex + noticeScrollOffset);
                    if (noticeIndex < currentNotices.size()) {
                        NoticeData clickedNotice = currentNotices.get(noticeIndex);
                        // 进入梦屿广播内部详情页
                        openNoticeDetail(clickedNotice);
                        return true;
                    }
                }
            }
        }

        // ==================== NPC 私信页面点击 ====================
        if (selectedLeftButtonIndex == NPC_MESSAGE_PAGE_INDEX) {
            for (ConversationClickArea area : conversationClickAreas) {
                if (area.contains(virtualMouseX, virtualMouseY)) {
                    selectedMessageNpcId = area.npcId();
                    messageThreadScrollOffset = 0L;
                    clearNpcMessageDetail();
                    lastReadRequestedNpcId = -1;
                    NpcConversationViewData conversation = NpcMessageClientCache.getConversation(area.npcId());
                    if (conversation != null && conversation.unreadCount() > 0) {
                        lastReadRequestedNpcId = area.npcId();
                        DreamingFishCore_NetworkManager.sendToServer(new Packet_NpcMessageReadRequest(area.npcId()));
                    }
                    return true;
                }
            }
            if (isNpcMessageDetailOpen()) {
                if (isInside(messageDetailBackArea, virtualMouseX, virtualMouseY)) {
                    clearNpcMessageDetail();
                    return true;
                }
                for (MessageReplyClickArea area : messageReplyClickAreas) {
                    if (area.contains(virtualMouseX, virtualMouseY)) {
                        clearNpcMessageDetail();
                        messageThreadScrollOffset = 0L;
                        lastReadRequestedNpcId = -1;
                        DreamingFishCore_NetworkManager.sendToServer(
                                new Packet_NpcMessageReplyRequest(area.messageRecordId(), area.replyId()));
                        return true;
                    }
                }
                // 详情态屏蔽上一帧可能残留的消息卡和回复热区。
                return true;
            }
            for (MessageClickArea area : messageClickAreas) {
                if (area.contains(virtualMouseX, virtualMouseY)) {
                    openNpcMessageDetail(area.messageRecordId());
                    return true;
                }
            }
            for (MessageReplyClickArea area : messageReplyClickAreas) {
                if (area.contains(virtualMouseX, virtualMouseY)) {
                    messageThreadScrollOffset = 0L;
                    lastReadRequestedNpcId = -1;
                    DreamingFishCore_NetworkManager.sendToServer(
                        new Packet_NpcMessageReplyRequest(area.messageRecordId(), area.replyId()));
                    return true;
                }
            }
        }

        if (selectedLeftButtonIndex == 1) {
            for (HelpTopicClickArea area : helpTopicClickAreas) {
                if (area.contains(virtualMouseX, virtualMouseY)) {
                    selectedHelpTopicIndex = area.topicIndex();
                    return true;
                }
            }
        }

        // ==================== 检查任务页面点击 ====================
        if (selectedLeftButtonIndex == 3) {  // 故事/任务页面
            // 左侧阶段导航与右侧阶段卡片使用同一套详情入口。
            for (StageClickArea area : storyStageNavClickAreas) {
                if (area.contains(virtualMouseX, virtualMouseY)) {
                    selectedStageId = area.stageId();
                    taskScrollOffset = 0L;
                    return true;
                }
            }

            // 检查返回按钮点击（仅在选中阶段时显示）
            if (selectedStageId != null) {
                int[] backButtonArea = pageRenderer.getBackButtonArea();
                if (virtualMouseX >= backButtonArea[0] && virtualMouseX <= backButtonArea[2] &&
                    virtualMouseY >= backButtonArea[1] && virtualMouseY <= backButtonArea[3]) {
                    // 返回阶段列表
                    selectedStageId = null;
                    stageScrollOffset = 0;
                    taskScrollOffset = 0;
                    return true;
                }
            }

            // 世界任务：检查右侧阶段列表点击
            if (selectedStageId == null) {
                for (StageClickArea area : stageClickAreas) {
                    if (area.contains(virtualMouseX, virtualMouseY)) {
                        selectedStageId = area.stageId();
                        taskScrollOffset = 0;
                        return true;
                    }
                }
            }

        }

        if (selectedLeftButtonIndex == 0 && profileRankManagerOpen) {
            int optionCount = Math.min(displayedRankOptions.size(), rankOptionClickAreas.size());
            for (int index = 0; index < optionCount; index++) {
                int[] area = rankOptionClickAreas.get(index);
                if (virtualMouseX >= area[0] && virtualMouseX <= area[2]
                        && virtualMouseY >= area[1] && virtualMouseY <= area[3]) {
                    Rank selectedRank = displayedRankOptions.get(index);
                    Rank currentRank = mc.player == null
                        ? RankRegistry.NO_RANK
                        : PlayerRankManager.getPlayerRankClient(mc.player);
                    if (!currentRank.getRankName().equals(selectedRank.getRankName())) {
                        DreamingFishCore_NetworkManager.sendToServer(new Packet_EquipPlayerRank(selectedRank.getRankName()));
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 处理左侧按钮点击事件
     * @param index 按钮索引
     */
    private void handleLeftButtonClick(int index) {
        switch (index) {
            case 0: // 个人档案（主页，默认显示）
                // 当前页面，不跳转
                break;
            case 1: // 帮助
                // 帮助页面，不跳转
                break;
            case 2: // 梦屿广播
                clearNoticeDetail();
                // 每次进入广播时优先定位当前故事阶段，避免新阶段玩家先看到旧阶段内容。
                selectedNoticeStageId = null;
                noticeScrollOffset = 0L;
                // 请求公告列表
                DreamingFishCore_NetworkManager.sendToServer(new Packet_NoticeListRequest());
                break;
            case 3: // 故事进展
                taskShowServerTasks = true;
                selectedStageId = null;
                stageScrollOffset = 0L;
                taskScrollOffset = 0L;
                DreamingFishCore_NetworkManager.sendToServer(new Packet_GuidanceSnapshotRequest());
                break;
            case 4: // 玩家与排行
                // 排行面板保留为独立一级入口。
                break;
            case 5: // 服务器成就
                // 显示成就页面（不打开新界面）
                break;
            case 6: // 服务器商店 / 市场行情
                marketScrollOffset = 0;
                DreamingFishCore_NetworkManager.sendToServer(new Packet_EconomyTerminalRequest());
                // 刷新只读市场快照。购买/下单需等待 EconomySystem 后续公共写 API。
                break;
            case 7: // 领地
                // 领地系统已剥离，按钮保留为占位入口。
                break;
            case 8: // 世界历史
                DreamingFishCore_NetworkManager.sendToServer(new Packet_WorldHistoryRequest());
                break;
            case 9: // 设置
                // Minecraft 原版设置
                this.onClose();
                mc.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(mc.screen, mc.options));
                break;
            case NPC_MESSAGE_PAGE_INDEX:
                messageThreadScrollOffset = 0L;
                conversationScrollOffset = 0L;
                clearNpcMessageDetail();
                lastReadRequestedNpcId = -1;
                DreamingFishCore_NetworkManager.sendToServer(new Packet_NpcMessageSnapshotRequest());
                break;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 公告列表页面滚动
        if (selectedLeftButtonIndex == 2) {
            if (isNoticeDetailOpen()) {
                double virtualMouseX = mouseX / uiScale;
                double virtualMouseY = mouseY / uiScale;
                if (isInside(noticeDetailContentArea, virtualMouseX, virtualMouseY)) {
                    long scrollStep = Math.max(1L, (long) (mc.font.lineHeight + 2) * 3L);
                    long newOffset = noticeDetailScrollOffset - Math.round(scrollY * scrollStep);
                    noticeDetailScrollOffset = Math.max(0L,
                        Math.min((long) noticeDetailMaxScroll, newOffset));
                }
                // 详情态不触碰公告列表滚动偏移，避免上一帧列表热区串页。
                return true;
            }
            int totalNotices = getNoticesForSelectedTab().size();
            int visibleCapacity = noticeVisibleCardCapacity > 0 ? noticeVisibleCardCapacity : VISIBLE_NOTICES;
            int maxScrollOffset = Math.max(0, totalNotices - visibleCapacity);

            if (maxScrollOffset > 0) {
                int newOffset = (int) (noticeScrollOffset - scrollY);
                noticeScrollOffset = Math.max(0, Math.min(maxScrollOffset, newOffset));
                return true;
            }
        }

        // 任务列表页面滚动
        if (selectedLeftButtonIndex == 3) {
            if (taskShowServerTasks && selectedStageId == null) {
                // 故事任务 - 阶段列表滚动
                var storyStages = getPlayerVisibleStoryStages(
                    com.hhy.dreamingfishcore.client.cache.ClientCacheManager.getStoryStages());
                int totalStages = storyStages.size();
                int columns = Math.max(1, stageGridColumns);
                int visibleRows = Math.max(1, stageGridVisibleRows);
                int totalRows = (totalStages + columns - 1) / columns;
                int maxRowOffset = Math.max(0, totalRows - visibleRows);

                if (maxRowOffset > 0) {
                    long newOffset = stageScrollOffset - Math.round(scrollY);
                    stageScrollOffset = Math.max(0L,
                        Math.min((long) maxRowOffset, newOffset));
                    return true;
                }
            } else if (taskShowServerTasks && selectedStageId != null) {
                // 故事任务 - 选中阶段的任务列表滚动
                var storyStages = getPlayerVisibleStoryStages(
                    com.hhy.dreamingfishcore.client.cache.ClientCacheManager.getStoryStages());
                com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData selectedStage = null;
                for (com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData stage : storyStages.values()) {
                    if (String.valueOf(stage.getStageId()).equals(selectedStageId)) {
                        selectedStage = stage;
                        break;
                    }
                }

                if (selectedStage != null) {
                    java.util.List<com.hhy.dreamingfishcore.gameplay.story_system.StoryTaskData> stageTasks = selectedStage.getTasks();
                    if (stageTasks == null) stageTasks = new java.util.ArrayList<>();
                    int totalTasks = stageTasks.size();
                    int maxScrollOffset = Math.max(0, totalTasks - VISIBLE_TASKS);

                    if (maxScrollOffset > 0) {
                        int newOffset = (int) (taskScrollOffset - scrollY);
                        taskScrollOffset = Math.max(0, Math.min(maxScrollOffset, newOffset));
                        return true;
                    }
                }
            }
        }

        // 帮助页面使用滚轮快速切换章节。
        if (selectedLeftButtonIndex == 1 && scrollY != 0.0D) {
            int direction = scrollY < 0.0D ? 1 : -1;
            int nextTopic = Math.max(0, Math.min(HELP_TOPICS.length - 1,
                selectedHelpTopicIndex + direction));
            if (nextTopic != selectedHelpTopicIndex) {
                selectedHelpTopicIndex = nextTopic;
                return true;
            }
        }

        // 服务器商店市场列表滚动。
        if (selectedLeftButtonIndex == 6) {
            int totalOrders = EconomyTerminalClientCache.get().marketOrders().size();
            int maxScrollOffset = Math.max(0, totalOrders - marketVisibleRows);
            if (maxScrollOffset > 0) {
                int newOffset = (int) (marketScrollOffset - scrollY);
                marketScrollOffset = Math.max(0, Math.min(maxScrollOffset, newOffset));
                return true;
            }
        }

        // 世界历史按“最新事件在上”排列，向下滚动查看更早的记录。
        if (selectedLeftButtonIndex == 8 && cachedHistory.size() > historyVisibleEntries) {
            int maxScrollOffset = Math.max(0, cachedHistory.size() - historyVisibleEntries);
            int newOffset = (int) (historyScrollOffset - scrollY);
            historyScrollOffset = Math.max(0, Math.min(maxScrollOffset, newOffset));
            return true;
        }

        if (selectedLeftButtonIndex == NPC_MESSAGE_PAGE_INDEX) {
            double virtualMouseX = mouseX / uiScale;
            double virtualMouseY = mouseY / uiScale;
            List<NpcConversationViewData> conversations = NpcMessageClientCache.getConversations();
            if (isInside(conversationListArea, virtualMouseX, virtualMouseY)) {
                int visible = Math.max(1, conversationClickAreas.size());
                int maximum = Math.max(0, conversations.size() - visible);
                conversationScrollOffset = Math.max(0L,
                    Math.min(maximum, conversationScrollOffset - Math.round(scrollY)));
                return maximum > 0;
            }
            if (isNpcMessageDetailOpen()) {
                if (isInside(messageDetailContentArea, virtualMouseX, virtualMouseY)) {
                    long scrollStep = Math.max(1L, (long) (mc.font.lineHeight + 2) * 3L);
                    long newOffset = messageDetailScrollOffset - Math.round(scrollY * scrollStep);
                    messageDetailScrollOffset = Math.max(0L,
                            Math.min((long) messageDetailMaxScroll, newOffset));
                }
                return true;
            }
            NpcConversationViewData selected = NpcMessageClientCache.getConversation(selectedMessageNpcId);
            if (selected != null && isInside(messageThreadArea, virtualMouseX, virtualMouseY)) {
                int visible = Math.max(1, (messageThreadArea[3] - messageThreadArea[1] + 6) / 54);
                int maximum = Math.max(0, selected.messages().size() - visible);
                messageThreadScrollOffset = Math.max(0L,
                    Math.min(maximum, messageThreadScrollOffset + Math.round(scrollY)));
                return maximum > 0;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean isNpcMessageDetailOpen() {
        return selectedMessageRecordId != null && !selectedMessageRecordId.isBlank();
    }

    private void openNpcMessageDetail(String recordId) {
        if (recordId == null || recordId.isBlank()) {
            return;
        }
        selectedMessageRecordId = recordId;
        messageDetailScrollOffset = 0L;
        messageDetailMaxScroll = 0;
        setArea(messageDetailBackArea, 0, 0, 0, 0);
        setArea(messageDetailContentArea, 0, 0, 0, 0);
    }

    private void clearNpcMessageDetail() {
        selectedMessageRecordId = "";
        messageDetailScrollOffset = 0L;
        messageDetailMaxScroll = 0;
        messageClickAreas.clear();
        messageReplyClickAreas.clear();
        setArea(messageDetailBackArea, 0, 0, 0, 0);
        setArea(messageDetailContentArea, 0, 0, 0, 0);
    }

    private boolean isNoticeDetailOpen() {
        return selectedNoticeDetailId >= 0;
    }

    private NoticeData findNoticeById(int noticeId) {
        for (NoticeData notice : cachedNotices) {
            if (notice != null && notice.getNoticeId() == noticeId) {
                return notice;
            }
        }
        return null;
    }

    private void clearNoticeDetail() {
        selectedNoticeDetailId = -1;
        noticeDetailScrollOffset = 0L;
        noticeDetailMaxScroll = 0;
        setArea(noticeDetailBackArea, 0, 0, 0, 0);
        setArea(noticeDetailContentArea, 0, 0, 0, 0);
        if (pageRenderer != null) {
            setArea(pageRenderer.getNoticeClickArea(), 0, 0, 0, 0);
        }
    }

    /** 打开终端内的公告详情页，同时保留现有已读标记逻辑。 */
    private void openNoticeDetail(NoticeData notice) {
        if (notice == null) return;
        // 发送标记已读数据包
        DreamingFishCore_NetworkManager.sendToServer(new Packet_MarkNoticeReadRequest(notice.getNoticeId()));
        // 更新本地已读状态
        cachedReadNoticeIds.add(notice.getNoticeId());
        com.hhy.dreamingfishcore.server.notice_system.client.cache.NoticeClientCache.markRead(
            notice.getNoticeId());

        // 更新全局未读标记
        hasUnreadNoticesGlobal = false;
        for (NoticeData n : cachedNotices) {
            if (isClientVisibleNotice(n)
                    && !cachedReadNoticeIds.contains(n.getNoticeId())) {
                hasUnreadNoticesGlobal = true;
                break;
            }
        }
        selectedNoticeDetailId = notice.getNoticeId();
        noticeDetailScrollOffset = 0L;
        noticeDetailMaxScroll = 0;
        setArea(noticeDetailBackArea, 0, 0, 0, 0);
        setArea(noticeDetailContentArea, 0, 0, 0, 0);
    }

    @Override
    public void onClose() {
        // 如果正在打开子屏幕，不调用 toggleUI()
        if (ServerScreenUI.isOpeningSubScreen()) {
            super.onClose();
            return;
        }
        // 正常关闭流程
        if (ServerScreenUI.isShowUI()) {
            ServerScreenUI.toggleUI();
        }
        super.onClose();
    }

    /**
     * 设置选中的页面索引（用于从子屏幕返回时恢复页面状态）
     */
    public void setSelectedPageIndex(int index) {
        this.selectedLeftButtonIndex = index;
    }

    /** 设置公告页的分类标签（0=游戏公告，1=服务器通知）。 */
    public void setNoticeTab(int tab) {
        clearNoticeDetail();
        this.selectedNoticeTab = Math.max(0, Math.min(1, tab));
        this.noticeScrollOffset = 0L;
    }

    public int getNoticeTab() {
        return selectedNoticeTab;
    }

    @Override
    public boolean isPauseScreen() {
        return false;  // 不暂停游戏
    }

    /**
     * 渲染鼠标悬浮提示框
     * @param guiGraphics 图形上下文
     * @param mouseX 鼠标 X 坐标（屏幕坐标）
     * @param mouseY 鼠标 Y 坐标（屏幕坐标）
     */
    private net.minecraft.network.chat.MutableComponent buildEconomyTooltip() {
        EconomyTerminalClientCache.Snapshot snapshot = EconomyTerminalClientCache.get();
        if (!snapshot.loaded()) {
            return Component.literal("§e正在同步经济数据...");
        }
        if (!snapshot.available() || !snapshot.compatible()) {
            return Component.literal("§e梦鱼币数据不可用")
                .append("\n")
                .append(Component.literal("§7" + snapshot.statusText()));
        }
        return Component.literal("§6梦鱼币余额：§f" + snapshot.balance());
    }

    private net.minecraft.network.chat.MutableComponent buildTerritoryTooltip() {
        EconomyTerminalClientCache.Snapshot snapshot = EconomyTerminalClientCache.get();
        if (!snapshot.loaded()) {
            return Component.literal("§e正在同步领地数据...");
        }
        if (!snapshot.available() || !snapshot.compatible()) {
            return Component.literal("§e领地数据不可用")
                .append("\n")
                .append(Component.literal("§7" + snapshot.statusText()));
        }

        net.minecraft.network.chat.MutableComponent tooltip = Component.literal("§a拥有领地：§f" + snapshot.ownedTerritoryCount());
        if (!snapshot.currentTerritoryName().isBlank()) {
            tooltip.append("\n")
                .append(Component.literal("§7当前位置：§f" + snapshot.currentTerritoryName()));
            String relation = localizeEconomyRelationship(snapshot.currentRelationship());
            if (!relation.isBlank()) {
                tooltip.append("\n")
                    .append(Component.literal("§7身份：§f" + relation));
            }
        } else {
            tooltip.append("\n")
                .append(Component.literal("§7当前位置不属于任何领地"));
        }
        return tooltip;
    }

    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 这组 tooltip 只属于个人档案概览页；Rank 管理仍复用索引 0，必须显式排除。
        if (selectedLeftButtonIndex != 0 || profileRankManagerOpen || pageRenderer == null) {
            return;
        }

        // 将屏幕鼠标坐标转换为虚拟坐标
        double virtualMouseX = mouseX / uiScale;
        double virtualMouseY = mouseY / uiScale;

        // 计算右侧面板偏移（考虑动画）
        int rightOffsetY = 0;
        if (!isClosing) {
            float rightAnimDuration = 800f;
            float rightProgress = Math.min(1.0f, (float) (Util.getMillis() - openTime) / rightAnimDuration);
            rightProgress = 1.0f - (float) Math.pow(1.0f - rightProgress, 3);
            rightOffsetY = (int) ((1.0f - rightProgress) * 100);
        }

        // 检查鼠标是否悬浮在金币框上（仅主页面显示tooltip）
        int[] goldBoxClick = pageRenderer.getGoldBoxClick();
        if (selectedLeftButtonIndex == 0 &&
            virtualMouseX >= goldBoxClick[0] && virtualMouseX <= goldBoxClick[2] &&
            virtualMouseY >= goldBoxClick[1] + rightOffsetY && virtualMouseY <= goldBoxClick[3] + rightOffsetY) {
            Component tooltip = buildEconomyTooltip();

            // 渲染提示框（使用屏幕坐标）
            guiGraphics.renderTooltip(mc.font, tooltip, mouseX, mouseY);
        }

        // 检查鼠标是否悬浮在领地框上（仅主页面显示tooltip）
        int[] territoryBoxClick = pageRenderer.getTerritoryBoxClick();
        if (selectedLeftButtonIndex == 0 &&
            virtualMouseX >= territoryBoxClick[0] && virtualMouseX <= territoryBoxClick[2] &&
            virtualMouseY >= territoryBoxClick[1] + rightOffsetY && virtualMouseY <= territoryBoxClick[3] + rightOffsetY) {
            Component tooltip = buildTerritoryTooltip();

            // 渲染提示框（使用屏幕坐标）
            guiGraphics.renderTooltip(mc.font, tooltip, mouseX, mouseY);
        }
    }

    // ==================== 感染度/分裂次数信息框方法 ====================

    /**
     * 获取感染度信息框的高度
     */
    private int getInfectionInfoBoxHeight() {
        int innerMargin = 6;
        int lineHeight = mc.font.lineHeight;
        // 固定高度以保持一致性
        return innerMargin * 2 + lineHeight * 6 + 5 * 3;  // 6行文字
    }

    // ==================== 公告系统方法 ====================

    /**
     * 设置公告数据（从网络包调用）
     */
    public static void setNoticeData(List<NoticeData> notices, Set<Integer> readNoticeIds) {
        // 只在公告列表真正变化时重置滚动位置
        boolean dataChanged = !cachedNotices.equals(notices);

        cachedNotices = notices != null ? new ArrayList<>(notices) : new ArrayList<>();
        cachedReadNoticeIds = readNoticeIds != null ? readNoticeIds : new java.util.HashSet<>();
        com.hhy.dreamingfishcore.server.notice_system.client.cache.NoticeClientCache.set(
            cachedNotices, cachedReadNoticeIds);

        // 只在数据变化时重置滚动位置
        if (dataChanged) {
            noticeScrollOffset = 0;
        }

        // 更新全局未读标记
        hasUnreadNoticesGlobal = false;
        for (NoticeData notice : cachedNotices) {
            if (notice != null && !cachedReadNoticeIds.contains(notice.getNoticeId())) {
                hasUnreadNoticesGlobal = true;
                break;
            }
        }
    }

    /** 接收服务端筛选后的公开世界历史。 */
    public static void setHistoryData(
            List<Packet_WorldHistoryResponse.HistoryEntry> entries,
            long totalEventCount,
            boolean loaded,
            boolean writesEnabled) {
        List<Packet_WorldHistoryResponse.HistoryEntry> safeEntries = entries == null
                ? List.of()
                : List.copyOf(entries);
        if (!cachedHistory.equals(safeEntries)) {
            historyScrollOffset = 0L;
        }
        cachedHistory = safeEntries;
        historyTotalEventCount = Math.max(0L, totalEventCount);
        historyLoaded = loaded;
        historyWritesEnabled = writesEnabled;
    }

}
