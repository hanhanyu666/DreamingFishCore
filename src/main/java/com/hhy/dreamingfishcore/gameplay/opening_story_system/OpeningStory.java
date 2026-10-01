package com.hhy.dreamingfishcore.gameplay.opening_story_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceManager;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceSeed;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcMessageManager;
import com.hhy.dreamingfishcore.gameplay.npc_system.StoryNpcContentPolicy;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryTaskData;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.hhy.dreamingfishcore.gameplay.story_system.runtime.StoryTextCatalog;
import com.hhy.dreamingfishcore.gameplay.task_location_system.StoryLocationResolver;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationDefinition;
import com.hhy.dreamingfishcore.gameplay.task_system.TaskDataManager;
import com.hhy.dreamingfishcore.gameplay.zhuiguang_system.ZhuiguangMembershipManager;
import com.hhy.dreamingfishcore.server.notice_system.BuiltInNoticeCatalog;
import com.hhy.dreamingfishcore.server.notice_system.NotificationPushHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 第一阶段“梦的开始”的明确 Java 状态机。
 *
 * <p>这里不读取可执行剧情 JSON。NPC/公告模块只把已经验证过的事实交给本类，本类
 * 决定是否推进状态以及哪些投影需要更新。存档仍然是普通 JSON，方便运营者检查和
 * 在停服后手工处理玩家数据。</p>
 */
public final class OpeningStory {
    public static final String STAGE_ID = "dreamingfishcore:dream_beginning";
    public static final String STAGE_DESCRIPTION =
            "灾难来临后，阿拜多斯成了许多幸存者暂时的落脚处。你读到临时安置通知，穿过危险抵达小镇，在学校见到正在做医疗志愿的白芷。她告诉你，大家的伤势和感染仍在变化，逐光会也正准备把救援、医疗与补给的人连接起来。随后你认识了周岑，听他说明人类逐光联合会的计划，并在两条路之间做出自己的选择：加入逐光会，和大家一起建设基地；或保持独立，在阿拜多斯继续生活并以自己的方式提供帮助。你的选择已经被记下，故事将从这里继续。";
    /**
     * 阿拜多斯与逐光会区域的默认 ID。
     *
     * <p>判定不再依赖这两个值——{@link StoryLocationResolver} 允许服主用**名称**建点；它们只是
     * “找不到地点时写进任务/引导的兜底引用”。取值统一由角色枚举提供，避免两处真相。</p>
     */
    public static final String ABYDOS_LOCATION_ID =
            StoryLocationResolver.Role.ABYDOS.fixedId();
    public static final String ZHUIGUANG_LOCATION_ID =
            StoryLocationResolver.Role.ZHUIGUANG.fixedId();
    public static final int BAIZHI_NPC_ID = StoryNpcContentPolicy.BAIZHI_ID;
    public static final int ZHOUCEN_NPC_ID = StoryNpcContentPolicy.ZHOUCEN_ID;

    public static final String BAIZHI_ARRIVAL_MESSAGE_ID =
            "dreamingfishcore:opening/baizhi/abydos_arrival";
    public static final String ZHOUCEN_CONTACT_MESSAGE_ID =
            "dreamingfishcore:opening/zhoucen/contact_channel";
    public static final String ZHOUCEN_INTRODUCTION_MESSAGE_ID =
            "dreamingfishcore:opening/zhoucen/introduction";
    public static final String ZHOUCEN_MEMBER_WELCOME_MESSAGE_ID =
            "dreamingfishcore:opening/zhoucen/member_welcome";
    public static final String ZHOUCEN_INDEPENDENT_ACK_MESSAGE_ID =
            "dreamingfishcore:opening/zhoucen/independent_ack";
    public static final String ASK_ABOUT_ZHUIGUANG_REPLY_ID =
            "ask_about_zhuiguang";
    public static final String JOIN_ZHUIGUANG_REPLY_ID =
            "join_zhuiguang";
    public static final String REMAIN_INDEPENDENT_REPLY_ID =
            "remain_independent";

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

    public static final String SETTLE_IN_ABYDOS_TASK_ID =
            "dreamingfishcore:opening/settle_in_abydos";
    public static final String MEET_BAIZHI_TASK_ID =
            "dreamingfishcore:opening/meet_baizhi";
    public static final String CHOOSE_ZHUIGUANG_PATH_TASK_ID =
            "dreamingfishcore:opening/choose_zhuiguang_path";
    public static final String BUILD_ZHUIGUANG_BASE_TASK_ID =
            "dreamingfishcore:opening/build_zhuiguang_base";
    public static final int SETTLE_IN_ABYDOS_TASK_NUMBER = 1101;
    public static final int MEET_BAIZHI_TASK_NUMBER = 1102;
    public static final int CHOOSE_ZHUIGUANG_PATH_TASK_NUMBER = 1103;
    public static final int BUILD_ZHUIGUANG_BASE_TASK_NUMBER = 1104;

    /** Java 文件就是阶段定义；任务文案仍可在客户端内容包中覆盖。 */
    public static List<StoryTaskData> createTasks() {
        return List.of(
                task(SETTLE_IN_ABYDOS_TASK_ID, SETTLE_IN_ABYDOS_TASK_NUMBER,
                        StoryTextCatalog.textOrDefault(StoryTextCatalog.OPENING_TASK_SETTLE_NAME, "抵达阿拜多斯"),
                        StoryTextCatalog.textOrDefault(StoryTextCatalog.OPENING_TASK_SETTLE_CONTENT,
                                "阅读临时安置通知，前往任务地点“阿拜多斯”完成安置。"),
                        StoryLocationResolver.referenceId(StoryLocationResolver.Role.ABYDOS),
                        TRAVEL_GUIDANCE_ID),
                task(MEET_BAIZHI_TASK_ID, MEET_BAIZHI_TASK_NUMBER,
                        StoryTextCatalog.textOrDefault(StoryTextCatalog.OPENING_TASK_MEET_BAIZHI_NAME, "去学校见白芷"),
                        StoryTextCatalog.textOrDefault(StoryTextCatalog.OPENING_TASK_MEET_BAIZHI_CONTENT,
                                "到达阿拜多斯后，根据白芷的消息前往学校与她当面交谈。"), "",
                        TALK_TO_BAIZHI_GUIDANCE_ID),
                task(CHOOSE_ZHUIGUANG_PATH_TASK_ID, CHOOSE_ZHUIGUANG_PATH_TASK_NUMBER,
                        StoryTextCatalog.textOrDefault(StoryTextCatalog.OPENING_TASK_CHOOSE_NAME, "了解逐光会"),
                        StoryTextCatalog.textOrDefault(StoryTextCatalog.OPENING_TASK_CHOOSE_CONTENT,
                                "白芷已经当面介绍了逐光会；联系周岑听完具体安排，再决定是否加入。"), "",
                        CONTACT_ZHOUCEN_GUIDANCE_ID, CHOOSE_MEMBERSHIP_GUIDANCE_ID),
                task(BUILD_ZHUIGUANG_BASE_TASK_ID, BUILD_ZHUIGUANG_BASE_TASK_NUMBER,
                        StoryTextCatalog.textOrDefault(StoryTextCatalog.OPENING_TASK_BUILD_NAME, "建设逐光会基地"),
                        StoryTextCatalog.textOrDefault(StoryTextCatalog.OPENING_TASK_BUILD_CONTENT,
                                "选择加入的成员前往“人类逐光联合会”任务地点，参与大型基地建设；独立协作者无需承担这项任务。"), "",
                        BUILD_BASE_GUIDANCE_ID));
    }

    public static StoryStageData createStageDefinition() {
        StoryStageData stage = new StoryStageData(STAGE_ID, 1,
                StoryTextCatalog.textOrDefault(StoryTextCatalog.OPENING_STAGE_NAME, "梦的开始"),
                StoryTextCatalog.textOrDefault(StoryTextCatalog.OPENING_STAGE_DESCRIPTION,
                        STAGE_DESCRIPTION));
        createTasks().forEach(stage::addTask);
        return stage;
    }

    public static boolean isMemberOnlyTask(String taskKey) {
        return BUILD_ZHUIGUANG_BASE_TASK_ID.equals(taskKey);
    }

    public static boolean isJavaControlledMessage(String messageId) {
        return Set.of(BAIZHI_ARRIVAL_MESSAGE_ID, ZHOUCEN_CONTACT_MESSAGE_ID,
                ZHOUCEN_INTRODUCTION_MESSAGE_ID, ZHOUCEN_MEMBER_WELCOME_MESSAGE_ID,
                ZHOUCEN_INDEPENDENT_ACK_MESSAGE_ID).contains(messageId);
    }

    /** 故事页的任务可见性由阶段事实决定，不以引导存档反推剧情进度。 */
    public static boolean isTaskVisibleToPlayer(String taskKey, UUID playerId) {
        if (taskKey == null || playerId == null) {
            return false;
        }
        OpeningStoryProgress progress = StoryManager.findOpeningProgress(playerId);
        if (progress == null) {
            return false;
        }
        OpeningStoryStep step = progress.getStep();
        if (SETTLE_IN_ABYDOS_TASK_ID.equals(taskKey)) {
            return step != OpeningStoryStep.NOT_STARTED;
        }
        if (MEET_BAIZHI_TASK_ID.equals(taskKey)) {
            return step != OpeningStoryStep.NOT_STARTED
                    && step != OpeningStoryStep.TRAVEL_TO_ABYDOS;
        }
        if (CHOOSE_ZHUIGUANG_PATH_TASK_ID.equals(taskKey)) {
            return step == OpeningStoryStep.CONTACT_ZHOUCEN
                    || step == OpeningStoryStep.CHOOSE_MEMBERSHIP
                    || step == OpeningStoryStep.BUILD_ZHUIGUANG_BASE
                    || step == OpeningStoryStep.DECLINED_ZHUIGUANG;
        }
        if (BUILD_ZHUIGUANG_BASE_TASK_ID.equals(taskKey)) {
            return step == OpeningStoryStep.BUILD_ZHUIGUANG_BASE
                    && ZhuiguangMembershipManager.isMember(playerId);
        }
        return false;
    }

    private static StoryTaskData task(String id, int number, String name, String content,
                                      String locationId, String... guidanceIds) {
        StoryTaskData task = new StoryTaskData(id, number, name, content, 0L, 0L);
        task.setPublishedByDefault(true);
        task.setScope(StoryTaskData.Scope.PERSONAL);
        task.setLocationId(locationId);
        task.setGuidanceDefinitionIds(List.of(guidanceIds));
        return task;
    }

    private OpeningStory() {
    }

    public static synchronized void onPlayerAuthenticated(ServerPlayer player) {
        // 加入成功后的补给/引导在登录时重试；不会凭登录事件跳过前置步骤。
        onPlayerLogin(player);
    }

    /** 玩家读到阿拜多斯公告后才开始第一阶段个人链。 */
    public static synchronized void onNoticeRead(ServerPlayer player, String noticeKey) {
        if (!canWrite() || player == null
                || !BuiltInNoticeCatalog.DESERT_TOWN_KEY.equals(noticeKey)
                || !OpeningStory.STAGE_ID.equals(
                StoryManager.getCurrentStageIdOrDefault())) {
            return;
        }
        OpeningStoryProgress progress = progressFor(player.getUUID());
        if (!progress.advanceTo(OpeningStoryStep.TRAVEL_TO_ABYDOS, now())) {
            return;
        }
        StoryManager.markDirty();
        createTravelGuidance(player);
        syncPlayer(player);
    }

    /** 地点事件只使用稳定地点 ID，不依赖可改的中文地点名。 */
    public static synchronized void onLocationEntered(
            ServerPlayer player, TaskLocationDefinition location) {
        if (player == null || location == null) {
            return;
        }
        if (!canWrite()
                || !StoryLocationResolver.matches(StoryLocationResolver.Role.ABYDOS, location)
                || !OpeningStory.STAGE_ID.equals(
                StoryManager.getCurrentStageIdOrDefault())) {
            return;
        }
        OpeningStoryProgress progress = StoryManager.findOpeningProgress(player.getUUID());
        if (progress == null || progress.getStep() != OpeningStoryStep.TRAVEL_TO_ABYDOS) {
            return;
        }
        if (!sendOnce(player, BAIZHI_ARRIVAL_MESSAGE_ID)) {
            DreamingFishCore.LOGGER.warn("玩家 {} 已抵达阿拜多斯，但白芷消息暂不可投递",
                    player.getScoreboardName());
            return;
        }
        if (!progress.advanceTo(OpeningStoryStep.TALK_TO_BAIZHI, now())) {
            return;
        }
        StoryManager.markDirty();
        GuidanceManager.resolve(player.getUUID(), TRAVEL_GUIDANCE_ID);
        createTalkToBaizhiGuidance(player);
        recordTask(player, OpeningStory.SETTLE_IN_ABYDOS_TASK_ID);
        syncPlayer(player);
    }

    /** 白芷的实体对话是完成会面的唯一入口。 */
    public static synchronized void onNpcInteraction(
            ServerPlayer player, int npcId, String locationId) {
        if (!canWrite() || player == null || npcId != BAIZHI_NPC_ID
                || !StoryLocationResolver.matchesId(StoryLocationResolver.Role.ABYDOS, locationId)) {
            return;
        }
        if (!OpeningStory.STAGE_ID.equals(
                StoryManager.getCurrentStageIdOrDefault())) {
            return;
        }
        OpeningStoryProgress progress = StoryManager.findOpeningProgress(player.getUUID());
        if (progress == null || progress.getStep() != OpeningStoryStep.TALK_TO_BAIZHI) {
            return;
        }
        if (!sendOnce(player, ZHOUCEN_CONTACT_MESSAGE_ID)) {
            return;
        }
        if (!progress.advanceTo(OpeningStoryStep.CONTACT_ZHOUCEN, now())) {
            return;
        }
        StoryManager.markDirty();
        GuidanceManager.resolve(player.getUUID(), TALK_TO_BAIZHI_GUIDANCE_ID);
        createContactZhoucenGuidance(player);
        recordTask(player, OpeningStory.MEET_BAIZHI_TASK_ID);
        syncPlayer(player);
    }

    /** 周岑预设回复是服务端校验后的个人选择事实。 */
    public static synchronized void onNpcReply(
            ServerPlayer player, String sourceDefinitionId, String replyId) {
        if (!canWrite() || player == null || sourceDefinitionId == null || replyId == null) {
            return;
        }
        if (!OpeningStory.STAGE_ID.equals(
                StoryManager.getCurrentStageIdOrDefault())) {
            return;
        }
        OpeningStoryProgress progress = StoryManager.findOpeningProgress(player.getUUID());
        if (progress == null) {
            return;
        }
        if (ZHOUCEN_CONTACT_MESSAGE_ID.equals(sourceDefinitionId)
                && ASK_ABOUT_ZHUIGUANG_REPLY_ID.equals(replyId)
                && progress.advanceTo(OpeningStoryStep.CHOOSE_MEMBERSHIP, now())) {
            StoryManager.markDirty();
            GuidanceManager.resolve(player.getUUID(), CONTACT_ZHOUCEN_GUIDANCE_ID);
            createChooseMembershipGuidance(player);
            // 主线后续消息由 Java 状态机明确选择；消息 JSON 的
            // followUpMessageId 只服务于普通 NPC 通信，不能决定主线顺序。
            sendOnce(player, ZHOUCEN_INTRODUCTION_MESSAGE_ID);
            syncPlayer(player);
            return;
        }
        if (!ZHOUCEN_INTRODUCTION_MESSAGE_ID.equals(sourceDefinitionId)
                || progress.getStep() != OpeningStoryStep.CHOOSE_MEMBERSHIP) {
            return;
        }
        if (JOIN_ZHUIGUANG_REPLY_ID.equals(replyId)
                && ensureMembershipChoice(player, true)
                && progress.advanceTo(OpeningStoryStep.BUILD_ZHUIGUANG_BASE, now())) {
            StoryManager.markDirty();
            GuidanceManager.resolve(player.getUUID(), CHOOSE_MEMBERSHIP_GUIDANCE_ID);
            createBuildGuidance(player);
            recordTask(player, OpeningStory.CHOOSE_ZHUIGUANG_PATH_TASK_ID);
            grantStarterSupply(player, progress);
            sendOnce(player, ZHOUCEN_MEMBER_WELCOME_MESSAGE_ID);
            syncPlayer(player);
            return;
        }
        if (REMAIN_INDEPENDENT_REPLY_ID.equals(replyId)
                && ensureMembershipChoice(player, false)
                && progress.advanceTo(OpeningStoryStep.DECLINED_ZHUIGUANG, now())) {
            StoryManager.markDirty();
            GuidanceManager.resolve(player.getUUID(), CHOOSE_MEMBERSHIP_GUIDANCE_ID);
            recordTask(player, OpeningStory.CHOOSE_ZHUIGUANG_PATH_TASK_ID);
            sendOnce(player, ZHOUCEN_INDEPENDENT_ACK_MESSAGE_ID);
            syncPlayer(player);
        }
    }

    /** 登录重试只处理已经发生过的加入事实，不会把玩家自动变成成员。 */
    public static synchronized void onPlayerLogin(ServerPlayer player) {
        if (!canWrite() || player == null) {
            return;
        }
        if (!OpeningStory.STAGE_ID.equals(
                StoryManager.getCurrentStageIdOrDefault())) {
            archiveFinalTaskAfterStageAdvance(player);
            return;
        }
        OpeningStoryProgress progress = StoryManager.findOpeningProgress(player.getUUID());
        if (progress == null) {
            return;
        }

        // NPC 回复记录和开场进度分别保存。若服务器在“回信已写入”之后、
        // “剧情状态已写入”之前中断，按钮不会再次出现；用已经保存的选择事实
        // 幂等补齐成员身份、引导和补给，避免玩家永久卡在选择节点。
        if (progress.getStep() == OpeningStoryStep.CHOOSE_MEMBERSHIP
                && reconcileSavedMembershipChoice(player, progress)) {
            syncPlayer(player);
            return;
        }
        if (progress.getStep() == OpeningStoryStep.BUILD_ZHUIGUANG_BASE) {
            // 进度文件可能已经保存而成员档案尚未来得及写入；只在确实存在
            // “加入”回复事实时恢复身份，不会把普通管理员改动误判成剧情选择。
            try {
                if (NpcMessageManager.hasSelectedReply(
                        player.getUUID(), ZHOUCEN_INTRODUCTION_MESSAGE_ID,
                        JOIN_ZHUIGUANG_REPLY_ID)
                        && !ZhuiguangMembershipManager.isMember(player)) {
                    ZhuiguangMembershipManager.setMember(player, true);
                }
            } catch (RuntimeException exception) {
                DreamingFishCore.LOGGER.warn("无法恢复玩家 {} 的逐光会成员事实",
                        player.getScoreboardName(), exception);
            }
        }

        // 登录/重启只修复“已经发生的事实”对应的投影；它绝不通过登录事件推进
        // 状态，也不重新播放一次性通知。这样进程在奖励或引导写入中途退出后，
        // 下一次登录仍能把投影补齐，而不会凭空跳过剧情。
        switch (progress.getStep()) {
            case TRAVEL_TO_ABYDOS -> createTravelGuidance(player, false);
            case TALK_TO_BAIZHI -> {
                createTravelGuidance(player, false);
                createTalkToBaizhiGuidance(player);
                recordTask(player, OpeningStory.SETTLE_IN_ABYDOS_TASK_ID);
            }
            case CONTACT_ZHOUCEN -> {
                createContactZhoucenGuidance(player);
                recordTask(player, OpeningStory.SETTLE_IN_ABYDOS_TASK_ID);
                recordTask(player, OpeningStory.MEET_BAIZHI_TASK_ID);
            }
            case CHOOSE_MEMBERSHIP -> {
                createChooseMembershipGuidance(player);
                recordTask(player, OpeningStory.SETTLE_IN_ABYDOS_TASK_ID);
                recordTask(player, OpeningStory.MEET_BAIZHI_TASK_ID);
                sendOnce(player, ZHOUCEN_INTRODUCTION_MESSAGE_ID);
            }
            case BUILD_ZHUIGUANG_BASE -> {
                if (ZhuiguangMembershipManager.isMember(player)) {
                    createBuildGuidance(player);
                    recordTask(player, OpeningStory.SETTLE_IN_ABYDOS_TASK_ID);
                    recordTask(player, OpeningStory.MEET_BAIZHI_TASK_ID);
                    recordTask(player, OpeningStory.CHOOSE_ZHUIGUANG_PATH_TASK_ID);
                    grantStarterSupply(player, progress);
                    sendOnce(player, ZHOUCEN_MEMBER_WELCOME_MESSAGE_ID);
                }
            }
            case DECLINED_ZHUIGUANG -> {
                recordTask(player, OpeningStory.SETTLE_IN_ABYDOS_TASK_ID);
                recordTask(player, OpeningStory.MEET_BAIZHI_TASK_ID);
                recordTask(player, OpeningStory.CHOOSE_ZHUIGUANG_PATH_TASK_ID);
                sendOnce(player, ZHOUCEN_INDEPENDENT_ACK_MESSAGE_ID);
            }
            default -> {
                // 尚未形成可投影的个人事实。
            }
        }
        syncPlayer(player);
    }

    /**
     * 第一阶段切换到后续阶段时，归档建设引导，保留实际完成情况。
     *
     * <p>个人开场游标仍保留在 BUILD_ZHUIGUANG_BASE，作为历史事实；但它不应再
     * 继续生成进行中的引导。在线玩家在切阶段时、离线玩家在下次登录时都会经过
     * 这里，因此不会因为登录顺序把旧任务重新显示出来。</p>
     */
    private static void archiveFinalTaskAfterStageAdvance(ServerPlayer player) {
        OpeningStoryProgress progress = StoryManager.findOpeningProgress(player.getUUID());
        if (progress == null || progress.getStep() != OpeningStoryStep.BUILD_ZHUIGUANG_BASE) {
            return;
        }

        GuidanceManager.archiveDefinitions(player.getUUID(), List.of(BUILD_BASE_GUIDANCE_ID));
        syncPlayer(player);
    }

    /** 返回是否实际补齐了一个已保存的入会/独立选择。 */
    private static boolean reconcileSavedMembershipChoice(
            ServerPlayer player, OpeningStoryProgress progress) {
        boolean joined;
        boolean remainedIndependent;
        try {
            joined = NpcMessageManager.hasSelectedReply(
                    player.getUUID(), ZHOUCEN_INTRODUCTION_MESSAGE_ID, JOIN_ZHUIGUANG_REPLY_ID);
            remainedIndependent = NpcMessageManager.hasSelectedReply(
                    player.getUUID(), ZHOUCEN_INTRODUCTION_MESSAGE_ID,
                    REMAIN_INDEPENDENT_REPLY_ID);
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.warn("无法读取玩家 {} 的已保存逐光会选择",
                    player.getScoreboardName(), exception);
            return false;
        }

        if (joined) {
            // 入会回复本身就是玩家明确提交的选择；若成员存档尚未来得及写入，
            // 这里补齐它，再继续执行一次性剧情投影。
            if (!ZhuiguangMembershipManager.isMember(player)) {
                ZhuiguangMembershipManager.setMember(player, true);
            }
            if (!ZhuiguangMembershipManager.isMember(player)
                    || !progress.advanceTo(OpeningStoryStep.BUILD_ZHUIGUANG_BASE, now())) {
                return false;
            }
            StoryManager.markDirty();
            GuidanceManager.resolve(player.getUUID(), CHOOSE_MEMBERSHIP_GUIDANCE_ID);
            createBuildGuidance(player);
            recordTask(player, OpeningStory.CHOOSE_ZHUIGUANG_PATH_TASK_ID);
            grantStarterSupply(player, progress);
            return true;
        }

        if (remainedIndependent
                && !ZhuiguangMembershipManager.isMember(player)
                && progress.advanceTo(OpeningStoryStep.DECLINED_ZHUIGUANG, now())) {
            StoryManager.markDirty();
            GuidanceManager.resolve(player.getUUID(), CHOOSE_MEMBERSHIP_GUIDANCE_ID);
            recordTask(player, OpeningStory.CHOOSE_ZHUIGUANG_PATH_TASK_ID);
            return true;
        }
        return false;
    }

    public static synchronized Optional<List<String>> getDialogueOverride(
            ServerPlayer player, int npcId) {
        if (!StoryManager.areWritesEnabled() || player == null) {
            return Optional.empty();
        }
        if (!OpeningStory.STAGE_ID.equals(
                StoryManager.getCurrentStageIdOrDefault())) {
            return Optional.empty();
        }
        OpeningStoryProgress progress = StoryManager.findOpeningProgress(player.getUUID());
        if (progress == null || npcId != BAIZHI_NPC_ID) {
            return Optional.empty();
        }
        return switch (progress.getStep()) {
            case TALK_TO_BAIZHI -> Optional.of(StoryTextCatalog.dialogue(
                    StoryTextCatalog.OPENING_BAIZHI_TALK));
            case CONTACT_ZHOUCEN -> Optional.of(StoryTextCatalog.dialogue(
                    StoryTextCatalog.OPENING_BAIZHI_CONTACT));
            case CHOOSE_MEMBERSHIP -> Optional.of(StoryTextCatalog.dialogue(
                    StoryTextCatalog.OPENING_BAIZHI_CHOOSE));
            default -> Optional.empty();
        };
    }

    public static synchronized String getDialogueRevision(ServerPlayer player, int npcId) {
        if (player == null || npcId != BAIZHI_NPC_ID) {
            return "";
        }
        if (!OpeningStory.STAGE_ID.equals(
                StoryManager.getCurrentStageIdOrDefault())) {
            return "";
        }
        OpeningStoryProgress progress = StoryManager.findOpeningProgress(player.getUUID());
        return progress == null ? "" : "opening/" + progress.getStep().name();
    }

    public static synchronized OpeningStoryStep getStep(UUID playerId) {
        if (!StoryManager.areWritesEnabled() || playerId == null) {
            return OpeningStoryStep.NOT_STARTED;
        }
        OpeningStoryProgress progress = StoryManager.findOpeningProgress(playerId);
        return progress == null ? OpeningStoryStep.NOT_STARTED : progress.getStep();
    }

    public static synchronized boolean isLoaded() {
        return StoryManager.areWritesEnabled();
    }

    public static synchronized boolean areWritesEnabled() {
        return StoryManager.areWritesEnabled();
    }

    private static void createTravelGuidance(ServerPlayer player) {
        createTravelGuidance(player, true);
    }

    private static void createTravelGuidance(ServerPlayer player, boolean notify) {
        GuidanceSeed seed = new GuidanceSeed(
                TRAVEL_GUIDANCE_ID,
                StoryTextCatalog.text(StoryTextCatalog.OPENING_TRAVEL_GUIDANCE_TITLE),
                StoryTextCatalog.text(StoryTextCatalog.OPENING_TRAVEL_GUIDANCE_CONTENT))
                .withStoryStage(OpeningStory.STAGE_ID);
        StoryLocationResolver.find(StoryLocationResolver.Role.ABYDOS)
                .ifPresent(location -> {
                    BlockPos min = location.getMin();
                    BlockPos max = location.getMax();
                    seed.withLocation(location.getName(), location.getDimension(),
                            midpoint(min.getX(), max.getX()), midpoint(min.getY(), max.getY()),
                            midpoint(min.getZ(), max.getZ()));
                });
        GuidanceManager.ensureActiveFromStoryEvent(
                player.getUUID(), seed, "dreamingfishcore:opening/event/read_abydos_notice",
                "梦屿广播", StoryTextCatalog.text(StoryTextCatalog.OPENING_TRAVEL_GUIDANCE_QUOTE));
        if (notify) {
            NotificationPushHelper.sendTopLeftNotification(
                    player, StoryTextCatalog.text(StoryTextCatalog.OPENING_TRAVEL_NOTIFICATION), 6500);
        }
    }

    private static void createTalkToBaizhiGuidance(ServerPlayer player) {
        createGuidance(player, TALK_TO_BAIZHI_GUIDANCE_ID,
                StoryTextCatalog.text(StoryTextCatalog.OPENING_BAIZHI_GUIDANCE_TITLE),
                StoryTextCatalog.text(StoryTextCatalog.OPENING_BAIZHI_GUIDANCE_CONTENT),
                "dreamingfishcore:opening/event/arrived_abydos", BAIZHI_NPC_ID,
                "白芷", StoryTextCatalog.text(StoryTextCatalog.OPENING_BAIZHI_GUIDANCE_QUOTE));
    }

    private static void createContactZhoucenGuidance(ServerPlayer player) {
        createGuidance(player, CONTACT_ZHOUCEN_GUIDANCE_ID,
                StoryTextCatalog.text(StoryTextCatalog.OPENING_CONTACT_GUIDANCE_TITLE),
                StoryTextCatalog.text(StoryTextCatalog.OPENING_CONTACT_GUIDANCE_CONTENT),
                "dreamingfishcore:opening/event/baizhi_conversation", ZHOUCEN_NPC_ID,
                "周岑", StoryTextCatalog.text(StoryTextCatalog.OPENING_CONTACT_GUIDANCE_QUOTE));
    }

    private static void createChooseMembershipGuidance(ServerPlayer player) {
        createGuidance(player, CHOOSE_MEMBERSHIP_GUIDANCE_ID,
                StoryTextCatalog.text(StoryTextCatalog.OPENING_CHOOSE_GUIDANCE_TITLE),
                StoryTextCatalog.text(StoryTextCatalog.OPENING_CHOOSE_GUIDANCE_CONTENT),
                "dreamingfishcore:opening/event/zhuiguang_introduction", ZHOUCEN_NPC_ID,
                "周岑", StoryTextCatalog.text(StoryTextCatalog.OPENING_CHOOSE_GUIDANCE_QUOTE));
    }

    private static void createBuildGuidance(ServerPlayer player) {
        GuidanceSeed seed = new GuidanceSeed(
                BUILD_BASE_GUIDANCE_ID,
                StoryTextCatalog.text(StoryTextCatalog.OPENING_BUILD_GUIDANCE_TITLE),
                StoryTextCatalog.text(StoryTextCatalog.OPENING_BUILD_GUIDANCE_CONTENT))
                .withStoryStage(OpeningStory.STAGE_ID);
        StoryLocationResolver.find(StoryLocationResolver.Role.ZHUIGUANG)
                .ifPresent(location -> {
                    BlockPos min = location.getMin();
                    BlockPos max = location.getMax();
                    seed.withLocation(location.getName(), location.getDimension(),
                            midpoint(min.getX(), max.getX()), midpoint(min.getY(), max.getY()),
                            midpoint(min.getZ(), max.getZ()));
                });
        GuidanceManager.ensureActiveFromStoryEvent(
                player.getUUID(), seed, "dreamingfishcore:opening/event/joined_zhuiguang",
                ZHOUCEN_NPC_ID, "周岑",
                StoryTextCatalog.text(StoryTextCatalog.OPENING_BUILD_GUIDANCE_QUOTE));
    }

    private static void createGuidance(
            ServerPlayer player, String id, String title, String content, String sourceEvent,
            int sourceNpcId, String sourceName, String sourceQuote) {
        GuidanceSeed seed = new GuidanceSeed(id, title, content)
                .withStoryStage(OpeningStory.STAGE_ID);
        GuidanceManager.ensureActiveFromStoryEvent(
                player.getUUID(), seed, sourceEvent, sourceNpcId, sourceName, sourceQuote);
    }

    private static void grantStarterSupply(
            ServerPlayer player, OpeningStoryProgress progress) {
        if (progress.isStarterSupplyGranted()) {
            return;
        }
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
        if (progress.markStarterSupplyGranted(now())) {
            StoryManager.markDirty();
            NotificationPushHelper.sendTopLeftNotification(
                    player, StoryTextCatalog.text(StoryTextCatalog.OPENING_STARTER_NOTIFICATION), 7000);
        }
    }

    private static void recordTask(ServerPlayer player, String taskId) {
        try {
            if (StoryManager.recordPlayerTaskProgress(
                    taskId, player.getScoreboardName(), player.getUUID())) {
                TaskDataManager.syncFullTaskData(player);
            }
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.error("记录玩家 {} 的开场任务 {} 失败",
                    player.getScoreboardName(), taskId, exception);
        }
    }

    private static boolean sendOnce(ServerPlayer player, String messageId) {
        try {
            return NpcMessageManager.sendStoryMessage(player, messageId)
                    || NpcMessageManager.hasReceivedDefinition(player.getUUID(), messageId);
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.warn("无法投递开场私信 {} 给玩家 {}",
                    messageId, player.getScoreboardName(), exception);
            return false;
        }
    }

    /**
     * 逐光会身份是开场状态机的业务事实，不再由 NPC 消息 JSON 的字段驱动。
     * 这里同时允许“已经是目标身份”的重试，保证消息已回复但进度尚未落盘时可以恢复。
     */
    private static boolean ensureMembershipChoice(ServerPlayer player, boolean member) {
        try {
            boolean changed = ZhuiguangMembershipManager.setMember(player, member);
            if (ZhuiguangMembershipManager.isMember(player) != member) {
                return false;
            }
            if (changed) {
                NotificationPushHelper.sendTopLeftNotification(
                        player,
                        "§e组织身份已更新§r\n§7当前："
                                + ZhuiguangMembershipManager.getDisplayName(member),
                        6500);
            }
            return true;
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.warn("无法记录玩家 {} 的逐光会选择",
                    player.getScoreboardName(), exception);
            return false;
        }
    }

    private static void syncPlayer(ServerPlayer player) {
        try {
            GuidanceManager.syncToClient(player);
            TaskDataManager.syncFullTaskData(player);
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.warn("同步玩家 {} 的开场状态失败",
                    player.getScoreboardName(), exception);
        }
    }

    private static OpeningStoryProgress progressFor(UUID playerId) {
        return StoryManager.getOrCreateOpeningProgress(playerId);
    }

    private static long now() {
        return Math.max(0L, System.currentTimeMillis());
    }

    private static int midpoint(int first, int second) {
        return (int) (((long) first + second) / 2L);
    }

    private static boolean canWrite() {
        return StoryManager.areWritesEnabled();
    }
}
