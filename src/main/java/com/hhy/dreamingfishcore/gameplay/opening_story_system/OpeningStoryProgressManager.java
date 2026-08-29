package com.hhy.dreamingfishcore.gameplay.opening_story_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceManager;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceSeed;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcMessageManager;
import com.hhy.dreamingfishcore.gameplay.story_system.OpeningStoryDefinitionCatalog;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationDefinition;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationManager;
import com.hhy.dreamingfishcore.gameplay.task_system.TaskDataManager;
import com.hhy.dreamingfishcore.gameplay.zhuiguang_system.ZhuiguangMembershipManager;
import com.hhy.dreamingfishcore.server.notice_system.BuiltInNoticeCatalog;
import com.hhy.dreamingfishcore.server.notice_system.NoticeData;
import com.hhy.dreamingfishcore.server.notice_system.NotificationPushHelper;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 阿拜多斯安置、白芷会面与逐光会选择的个人任务执行器。
 *
 * <p>全服任务定义和个人执行状态彼此独立：玩家到达或作出选择只记录自己的进度，
 * “建设逐光会基地”仍由服主在建筑真正完工后统一结算。</p>
 */
public final class OpeningStoryProgressManager {
    public static final String ABYDOS_LOCATION_NAME = "阿拜多斯";
    public static final String ZHUIGUANG_LOCATION_NAME = "人类逐光联合会";

    public static final int BAIZHI_NPC_ID = 101;
    public static final int ZHOUCEN_NPC_ID = 105;

    public static final String BAIZHI_ARRIVAL_MESSAGE_ID =
            "dreamingfishcore:opening/baizhi/abydos_arrival";
    /** 旧版本白芷联络消息 ID，仅用于兼容已经收到旧消息的存档。 */
    @Deprecated
    public static final String BAIZHI_ZHUIGUANG_MESSAGE_ID =
            "dreamingfishcore:opening/baizhi/zhuiguang_contact";
    public static final String ZHOUCEN_CONTACT_MESSAGE_ID =
            "dreamingfishcore:opening/zhoucen/contact_channel";
    public static final String ZHOUCEN_INTRODUCTION_MESSAGE_ID =
            "dreamingfishcore:opening/zhoucen/introduction";
    public static final String ZHOUCEN_MEMBER_WELCOME_MESSAGE_ID =
            "dreamingfishcore:opening/zhoucen/member_welcome";
    public static final String ZHOUCEN_INDEPENDENT_MESSAGE_ID =
            "dreamingfishcore:opening/zhoucen/independent_ack";

    public static final String CONTACT_ZHOUCEN_REPLY_ID = "contact_zhoucen";
    public static final String ASK_ABOUT_ZHUIGUANG_REPLY_ID = "ask_about_zhuiguang";
    public static final String JOIN_ZHUIGUANG_REPLY_ID = "join_zhuiguang";
    public static final String REMAIN_INDEPENDENT_REPLY_ID = "remain_independent";

    public static final String TRAVEL_GUIDANCE_ID =
            "dreamingfishcore:guidance/opening/travel_to_abydos";
    public static final String TALK_TO_BAIZHI_GUIDANCE_ID =
            "dreamingfishcore:guidance/opening/talk_to_baizhi";
    public static final String CONTACT_ZHOUCEN_GUIDANCE_ID =
            "dreamingfishcore:guidance/opening/contact_zhoucen";
    public static final String CHOOSE_MEMBERSHIP_GUIDANCE_ID =
            "dreamingfishcore:guidance/opening/choose_membership";
    public static final String BUILD_BASE_GUIDANCE_ID =
            "dreamingfishcore:guidance/opening/build_zhuiguang_base";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();
    private static final Type DATA_TYPE =
            new TypeToken<Map<String, OpeningStoryProgress>>() { }.getType();
    private static final Map<String, OpeningStoryProgress> PLAYER_PROGRESS =
            new ConcurrentHashMap<>();

    private static boolean loaded;
    private static boolean dirty;
    private static boolean writesEnabled;

    private OpeningStoryProgressManager() {
    }

    public static synchronized void loadWorldData(MinecraftServer server) {
        PLAYER_PROGRESS.clear();
        dirty = false;
        writesEnabled = false;
        var path = WorldDataPaths.resolve(server, "story", "opening_player_progress.json");
        boolean fileExisted = Files.exists(path);
        try {
            Map<String, OpeningStoryProgress> stored = JsonDataStore.read(
                    path,
                    GSON,
                    DATA_TYPE,
                    ConcurrentHashMap::new);
            boolean repaired = false;
            for (Map.Entry<String, OpeningStoryProgress> entry : stored.entrySet()) {
                if (!isUuid(entry.getKey()) || entry.getValue() == null) {
                    repaired = true;
                    continue;
                }
                repaired |= entry.getValue().repair();
                PLAYER_PROGRESS.put(entry.getKey(), entry.getValue());
            }
            loaded = true;
            writesEnabled = true;
            dirty = !fileExisted || repaired;
            DreamingFishCore.LOGGER.info(
                    "开场个人任务进度加载完成，共 {} 名玩家", PLAYER_PROGRESS.size());
        } catch (Exception exception) {
            loaded = true;
            writesEnabled = false;
            DreamingFishCore.LOGGER.error(
                    "读取开场个人任务进度失败，本次会话不会覆盖损坏文件：{}", path, exception);
        }
    }

    /** 玩家真正打开阿拜多斯通知后才生成前往安置点的引导。 */
    public static synchronized boolean onNoticeOpened(ServerPlayer player, NoticeData notice) {
        if (!canWrite()
                || player == null
                || notice == null
                || !BuiltInNoticeCatalog.DESERT_TOWN_KEY.equals(notice.getNoticeKey())
                || !OpeningStoryDefinitionCatalog.STAGE_ID.equals(
                StoryManager.getCurrentStageIdOrDefault())) {
            return false;
        }

        OpeningStoryProgress progress = progressFor(player.getUUID());
        if (!progress.advanceTo(OpeningStoryStep.TRAVEL_TO_ABYDOS, System.currentTimeMillis())) {
            return false;
        }
        dirty = true;

        GuidanceManager.createFromStoryEvent(
                player.getUUID(),
                travelGuidance(),
                "dreamingfishcore:opening/event/read_abydos_notice",
                "梦屿广播",
                notice.getNoticeTitle());
        GuidanceManager.syncToClient(player);
        TaskDataManager.syncFullTaskData(player);
        NotificationPushHelper.sendTopLeftNotification(
                player,
                "§e新的引导：前往阿拜多斯§r\n§7目标已记录在任务引导中。",
                6500);
        return true;
    }

    /** 每秒取得一次玩家所在任务地点，同时维持成员基地的生命恢复效果。 */
    public static synchronized void onPlayerLocationTick(
            ServerPlayer player, TaskLocationDefinition currentLocation) {
        if (player == null) {
            return;
        }

        if (isNamed(currentLocation, ZHUIGUANG_LOCATION_NAME)
                && ZhuiguangMembershipManager.isMember(player)) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION,
                    60,
                    0,
                    true,
                    false,
                    true));
        }

        if (!canWrite() || !isNamed(currentLocation, ABYDOS_LOCATION_NAME)) {
            return;
        }
        OpeningStoryProgress progress = PLAYER_PROGRESS.get(player.getUUID().toString());
        if (progress == null || progress.getStep() != OpeningStoryStep.TRAVEL_TO_ABYDOS) {
            return;
        }

        boolean messageReady = NpcMessageManager.sendConfiguredMessage(
                player, BAIZHI_ARRIVAL_MESSAGE_ID)
                || NpcMessageManager.hasReceivedDefinition(
                player.getUUID(), BAIZHI_ARRIVAL_MESSAGE_ID);
        if (!messageReady) {
            DreamingFishCore.LOGGER.error(
                    "玩家 {} 已抵达阿拜多斯，但白芷到达消息无法投递",
                    player.getScoreboardName());
            return;
        }

        if (!progress.advanceTo(OpeningStoryStep.TALK_TO_BAIZHI, System.currentTimeMillis())) {
            return;
        }
        dirty = true;
        GuidanceManager.resolve(player.getUUID(), TRAVEL_GUIDANCE_ID);
        ensureTalkToBaizhiGuidance(player);
        recordPersonalTask(player, OpeningStoryDefinitionCatalog.SETTLE_IN_ABYDOS_TASK_ID);
        GuidanceManager.syncToClient(player);
    }

    /** 白芷的实体对话是任务完成条件；普通短信阅读不能跳过这一环。 */
    public static synchronized boolean onNpcInteraction(ServerPlayer player, int npcId) {
        if (!canWrite() || player == null || npcId != BAIZHI_NPC_ID) {
            return false;
        }
        OpeningStoryProgress progress = PLAYER_PROGRESS.get(player.getUUID().toString());
        if (progress == null || progress.getStep() != OpeningStoryStep.TALK_TO_BAIZHI) {
            return false;
        }

        // 初次打开界面显示第一句；第一次点击“交谈”切到第二句的同时，
        // 白芷便当面把玩家转给周岑，不再要求玩家回到终端回复白芷。
        boolean messageReady = NpcMessageManager.sendConfiguredMessage(
                player, ZHOUCEN_CONTACT_MESSAGE_ID)
                || NpcMessageManager.hasReceivedDefinition(
                player.getUUID(), ZHOUCEN_CONTACT_MESSAGE_ID);
        if (!messageReady) {
            DreamingFishCore.LOGGER.error(
                    "玩家 {} 已与白芷交谈，但周岑联络消息无法投递",
                    player.getScoreboardName());
            return false;
        }

        if (!progress.advanceTo(OpeningStoryStep.CONTACT_ZHOUCEN, System.currentTimeMillis())) {
            return false;
        }
        dirty = true;
        GuidanceManager.resolve(player.getUUID(), TALK_TO_BAIZHI_GUIDANCE_ID);
        ensureContactZhoucenGuidance(player);
        recordPersonalTask(player, OpeningStoryDefinitionCatalog.MEET_BAIZHI_TASK_ID);
        GuidanceManager.syncToClient(player);
        return true;
    }

    /** NPC 私信系统完成预设回复校验与后续消息投递后，在这里推进个人任务。 */
    public static synchronized void onNpcReply(
            ServerPlayer player, String sourceDefinitionId, String replyId) {
        if (!canWrite() || player == null || sourceDefinitionId == null || replyId == null) {
            return;
        }
        OpeningStoryProgress progress = PLAYER_PROGRESS.get(player.getUUID().toString());
        if (progress == null) {
            return;
        }

        // 兼容已经走过旧流程的存档；新玩家不会再收到这条白芷联络消息。
        if (BAIZHI_ZHUIGUANG_MESSAGE_ID.equals(sourceDefinitionId)
                && CONTACT_ZHOUCEN_REPLY_ID.equals(replyId)
                && progress.getStep() == OpeningStoryStep.CONTACT_ZHOUCEN) {
            ensureContactZhoucenGuidance(player);
            return;
        }

        if (ZHOUCEN_CONTACT_MESSAGE_ID.equals(sourceDefinitionId)
                && ASK_ABOUT_ZHUIGUANG_REPLY_ID.equals(replyId)
                && progress.advanceTo(
                OpeningStoryStep.CHOOSE_MEMBERSHIP, System.currentTimeMillis())) {
            dirty = true;
            GuidanceManager.resolve(player.getUUID(), CONTACT_ZHOUCEN_GUIDANCE_ID);
            ensureChooseMembershipGuidance(player);
            GuidanceManager.syncToClient(player);
            return;
        }

        if (!ZHOUCEN_INTRODUCTION_MESSAGE_ID.equals(sourceDefinitionId)
                || progress.getStep() != OpeningStoryStep.CHOOSE_MEMBERSHIP) {
            return;
        }

        if (JOIN_ZHUIGUANG_REPLY_ID.equals(replyId)
                && ZhuiguangMembershipManager.isMember(player)
                && progress.advanceTo(
                OpeningStoryStep.BUILD_ZHUIGUANG_BASE, System.currentTimeMillis())) {
            dirty = true;
            GuidanceManager.resolve(player.getUUID(), CHOOSE_MEMBERSHIP_GUIDANCE_ID);
            ensureBuildBaseGuidance(player);
            recordPersonalTask(player, OpeningStoryDefinitionCatalog.CHOOSE_ZHUIGUANG_PATH_TASK_ID);
            grantStarterSupply(player, progress);
            GuidanceManager.syncToClient(player);
            return;
        }

        if (REMAIN_INDEPENDENT_REPLY_ID.equals(replyId)
                && !ZhuiguangMembershipManager.isMember(player)
                && progress.advanceTo(
                OpeningStoryStep.DECLINED_ZHUIGUANG, System.currentTimeMillis())) {
            dirty = true;
            GuidanceManager.resolve(player.getUUID(), CHOOSE_MEMBERSHIP_GUIDANCE_ID);
            recordPersonalTask(player, OpeningStoryDefinitionCatalog.CHOOSE_ZHUIGUANG_PATH_TASK_ID);
            GuidanceManager.syncToClient(player);
        }
    }

    /** 登录时补齐加入成功但因异常未落下的补给或建设引导。 */
    public static synchronized void onPlayerLogin(ServerPlayer player) {
        if (!canWrite() || player == null) {
            return;
        }
        OpeningStoryProgress progress = PLAYER_PROGRESS.get(player.getUUID().toString());
        if (progress == null
                || progress.getStep() != OpeningStoryStep.BUILD_ZHUIGUANG_BASE
                || !ZhuiguangMembershipManager.isMember(player)) {
            return;
        }
        ensureBuildBaseGuidance(player);
        grantStarterSupply(player, progress);
        GuidanceManager.syncToClient(player);
    }

    /**
     * 白芷开场会面使用的专属台词，随打开的对话包一起发给客户端。
     *
     * <p>逐光会的基本情况必须在面对面交谈中说清楚，终端只负责把玩家
     * 接到周岑那里，让他继续说明登记、补给和建设安排。对话界面每次
     * 交互都会重新从服务端取一份视图，因此在会面完成后仍保留一段
     * 白芷的说明，避免玩家只能去终端才能理解这条故事线。</p>
     */
    public static synchronized Optional<List<String>> getDialogueOverride(
            ServerPlayer player, int npcId) {
        if (!loaded || player == null || npcId != BAIZHI_NPC_ID) {
            return Optional.empty();
        }
        OpeningStoryProgress progress = PLAYER_PROGRESS.get(player.getUUID().toString());
        if (progress == null) {
            return Optional.empty();
        }
        return switch (progress.getStep()) {
            case TALK_TO_BAIZHI -> Optional.of(List.of(
                    "你就是刚到阿拜多斯的那位吧？先坐下，我看看有没有明显外伤。镇上正在筹建的逐光会，我也想当面跟你说清楚。",
                    "现在情况实在不是不太乐观，梦屿正在考虑建设人类逐光联合会，医院、维修队和搜救的人想先把彼此连起来，这样可以更好的实施救援和发放补给。周岑是目前的代表，我现在就把你介绍给周岑，等会儿终端会收到他的消息。"));
            case CONTACT_ZHOUCEN -> Optional.of(List.of(
                    "逐光会还只是个开头：仓库、救援交接点和能让人过夜的地方，都要一点点搭起来。愿意加入就一起建设，不加入也能在阿拜多斯生活、看病和做交易。",
                    "周岑负责登记和具体安排。我把他的联络方式发到你的终端了，先听他把条件讲完，再按自己的想法决定。"));
            case CHOOSE_MEMBERSHIP -> Optional.of(List.of(
                    "周岑会把成员要承担的事情、能得到的补给和基地安排讲清楚。你听完再选，不用急着回答。"));
            default -> Optional.empty();
        };
    }

    public static synchronized OpeningStoryStep getStep(UUID playerId) {
        if (!loaded || playerId == null) {
            return OpeningStoryStep.NOT_STARTED;
        }
        OpeningStoryProgress progress = PLAYER_PROGRESS.get(playerId.toString());
        return progress == null ? OpeningStoryStep.NOT_STARTED : progress.getStep();
    }

    public static synchronized boolean saveIfDirty(MinecraftServer server) {
        if (!loaded || !dirty || !writesEnabled) {
            return true;
        }
        try {
            JsonDataStore.writeAtomic(
                    WorldDataPaths.resolve(server, "story", "opening_player_progress.json"),
                    GSON,
                    PLAYER_PROGRESS);
            dirty = false;
            return true;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error(
                    "写入开场个人任务进度失败，保留 dirty 状态等待下次保存", exception);
            return false;
        }
    }

    public static synchronized void clearWorldCache() {
        PLAYER_PROGRESS.clear();
        loaded = false;
        dirty = false;
        writesEnabled = false;
    }

    private static GuidanceSeed travelGuidance() {
        return new GuidanceSeed(
                TRAVEL_GUIDANCE_ID,
                "前往阿拜多斯",
                "前往沙漠中的临时安置点阿拜多斯，在镇内完成安置。")
                .withStoryStage(OpeningStoryDefinitionCatalog.STAGE_ID)
                .withLocation(
                        ABYDOS_LOCATION_NAME,
                        "minecraft:overworld",
                        9890,
                        151,
                        1771);
    }

    private static void ensureTalkToBaizhiGuidance(ServerPlayer player) {
        GuidanceManager.createFromStoryEvent(
                player.getUUID(),
                new GuidanceSeed(
                        TALK_TO_BAIZHI_GUIDANCE_ID,
                        "去学校找白芷",
                        "白芷在阿拜多斯的学校做医疗志愿。找到她并当面交谈。")
                        .withStoryStage(OpeningStoryDefinitionCatalog.STAGE_ID),
                "dreamingfishcore:opening/event/arrived_abydos",
                "白芷",
                "安顿好以后到学校来找我。等待实体对话");
    }

    private static void ensureContactZhoucenGuidance(ServerPlayer player) {
        GuidanceManager.createFromStoryEvent(
                player.getUUID(),
                new GuidanceSeed(
                        CONTACT_ZHOUCEN_GUIDANCE_ID,
                        "联系周岑",
                        "白芷已经当面说明了逐光会的大致情况。打开周岑发来的联络消息，听完具体安排后再决定是否加入。")
                        .withStoryStage(OpeningStoryDefinitionCatalog.STAGE_ID),
                "dreamingfishcore:opening/event/baizhi_conversation",
                "周岑",
                "白芷已经把你介绍给周岑。注意查看他的联络消息和预设回复");
    }

    private static void ensureChooseMembershipGuidance(ServerPlayer player) {
        GuidanceManager.createFromStoryEvent(
                player.getUUID(),
                new GuidanceSeed(
                        CHOOSE_MEMBERSHIP_GUIDANCE_ID,
                        "决定是否加入逐光会",
                        "阅读周岑发来的介绍，然后使用消息下方的预设选项作出决定。")
                        .withStoryStage(OpeningStoryDefinitionCatalog.STAGE_ID),
                "dreamingfishcore:opening/event/zhuiguang_introduction",
                "周岑",
                "加入与否由你自己决定");
    }

    private static void ensureBuildBaseGuidance(ServerPlayer player) {
        GuidanceSeed seed = new GuidanceSeed(
                BUILD_BASE_GUIDANCE_ID,
                "参与逐光会基地建设",
                "前往任务区域“人类逐光联合会”，与其他成员一起建设基地。成员在基地范围内会得到缓慢的生命恢复。")
                .withStoryStage(OpeningStoryDefinitionCatalog.STAGE_ID);

        TaskLocationManager.getLocationByName(ZHUIGUANG_LOCATION_NAME)
                .ifPresent(location -> {
                    BlockPos min = location.getMin();
                    BlockPos max = location.getMax();
                    seed.withLocation(
                            ZHUIGUANG_LOCATION_NAME,
                            location.getDimension(),
                            midpoint(min.getX(), max.getX()),
                            midpoint(min.getY(), max.getY()),
                            midpoint(min.getZ(), max.getZ()));
                });
        GuidanceManager.createFromStoryEvent(
                player.getUUID(),
                seed,
                "dreamingfishcore:opening/event/joined_zhuiguang",
                "周岑",
                "基地现在缺的不是一个人盖完整座楼，而是每个人把能做的那部分做完");
    }

    private static void grantStarterSupply(
            ServerPlayer player, OpeningStoryProgress progress) {
        if (!progress.markStarterSupplyGranted(System.currentTimeMillis())) {
            return;
        }
        dirty = true;
        List<ItemStack> supplies = new ArrayList<>();
        supplies.add(new ItemStack(Items.IRON_PICKAXE));
        supplies.add(new ItemStack(Items.IRON_AXE));
        supplies.add(new ItemStack(Items.BREAD, 16));
        supplies.add(new ItemStack(Items.STONE_BRICKS, 64));
        supplies.add(new ItemStack(Items.OAK_PLANKS, 64));
        supplies.add(new ItemStack(Items.TORCH, 32));
        for (ItemStack stack : supplies) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        NotificationPushHelper.sendTopLeftNotification(
                player,
                "§e逐光会补给已发放§r\n§7工具、口粮与首批建材已经交给你。",
                7000);
    }

    private static void recordPersonalTask(ServerPlayer player, String taskId) {
        try {
            if (StoryManager.recordPlayerTaskProgress(
                    taskId, player.getScoreboardName(), player.getUUID())) {
                TaskDataManager.syncFullTaskData(player);
            }
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.error(
                    "记录玩家 {} 的开场任务进度 {} 失败；个人任务链仍保留当前节点",
                    player.getScoreboardName(), taskId, exception);
        }
    }

    private static OpeningStoryProgress progressFor(UUID playerId) {
        return PLAYER_PROGRESS.computeIfAbsent(
                playerId.toString(), ignored -> new OpeningStoryProgress());
    }

    private static boolean isNamed(TaskLocationDefinition location, String expectedName) {
        return location != null
                && expectedName.equals(location.getName() == null ? "" : location.getName().trim());
    }

    private static int midpoint(int first, int second) {
        return (int) (((long) first + (long) second) / 2L);
    }

    private static boolean canWrite() {
        return loaded && writesEnabled;
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
