package com.hhy.dreamingfishcore.gameplay.afterdream_story_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceManager;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceSeed;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcMessageManager;
import com.hhy.dreamingfishcore.gameplay.npc_system.StoryNpcContentPolicy;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.PlayerInfectionClientSync;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.PlayerInfectionManager;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryTaskData;
import com.hhy.dreamingfishcore.gameplay.story_system.runtime.StoryTextCatalog;
import com.hhy.dreamingfishcore.gameplay.task_location_system.StoryLocationResolver;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationDefinition;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationManager;
import com.hhy.dreamingfishcore.gameplay.task_system.TaskDataManager;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieSpeciesConfig;
import com.hhy.dreamingfishcore.item.DreamingFishCore_Items;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import com.hhy.dreamingfishcore.server.notice_system.NoticeCategory;
import com.hhy.dreamingfishcore.server.notice_system.NoticeData;
import com.hhy.dreamingfishcore.server.notice_system.NoticeDeliveryService;
import com.hhy.dreamingfishcore.server.notice_system.NoticeManager;
import com.hhy.dreamingfishcore.server.notice_system.NotificationPushHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 第二阶段“余梦期”的明确 Java 状态机。
 *
 * <p>这里保存的是已经发生的事实，顺序和奖励写在 Java 中；JSON 只保存状态，文案
 * 由 {@link StoryTextCatalog} 提供。这样修改一句台词不会改变流程，修改流程也不会
 * 要求维护一张容易失配的通用节点图。</p>
 */
public final class AfterdreamStory {
    public static final String STAGE_ID = "dreamingfishcore:afterdream";
    public static final String STAGE_DESCRIPTION =
            "随着危机的进一步发展，逐光会发现阿拜多斯发生了多起感染事件。";
    public static final int BAIZHI_NPC_ID = StoryNpcContentPolicy.BAIZHI_ID;
    public static final int JIANGWAN_NPC_ID = StoryNpcContentPolicy.JIANGWAN_ID;
    /**
     * 医疗接待点的兜底 ID。
     *
     * <p>判定与解析都走 {@link StoryLocationResolver}（允许服主按名称建点）；这个常量只在
     * “一个地点都没有”时作为写进任务/引导的兜底引用，取值由角色枚举统一提供。</p>
     */
    public static final String MEDICAL_LOCATION_ID =
            StoryLocationResolver.Role.ZHUIGUANG.fixedId();

    public static final String BAIZHI_PUBLIC_TREATMENT_MESSAGE_ID =
            "dreamingfishcore:afterdream/baizhi/public_treatment";
    public static final String PUBLIC_NOTICE_KEY = "afterdream.zhuiguang_public_treatment";
    public static final String MASK_NOTICE_KEY = "afterdream.jiangwan_mask";
    public static final String VIRUS_EVOLUTION_NOTICE_KEY = "afterdream.virus_evolution";
    public static final String ZOMBIE_MEMORY_NOTICE_KEY = "afterdream.zombie_memory";
    /** 第二阶段启用时立即发布的医疗与恢复规则公告。 */
    public static final String RECOVERY_RULES_NOTICE_KEY = "afterdream.recovery_rules";

    /** 世界旗标只表示公告已经成为故事档案的一部分。面具规则另有独立旗标。 */
    public static final String PUBLIC_NOTICE_PUBLISHED_FLAG =
            "dreamingfishcore:afterdream/public_treatment_notice";
    public static final String MASK_NOTICE_PUBLISHED_FLAG =
            "dreamingfishcore:afterdream/mask_notice";
    public static final String VIRUS_EVOLUTION_NOTICE_PUBLISHED_FLAG =
            "dreamingfishcore:afterdream/virus_evolution_notice";
    public static final String ZOMBIE_MEMORY_NOTICE_PUBLISHED_FLAG =
            "dreamingfishcore:afterdream/zombie_memory_notice";
    public static final String RECOVERY_RULES_NOTICE_PUBLISHED_FLAG =
            "dreamingfishcore:afterdream/recovery_rules_notice";

    public static final String BAIZHI_MESSAGE_TASK_ID =
            "dreamingfishcore:afterdream/receive_baizhi_message";
    public static final String ENTER_RECEPTION_TASK_ID =
            "dreamingfishcore:afterdream/enter_medical_reception";
    public static final String MEDICAL_REVIEW_TASK_ID =
            "dreamingfishcore:afterdream/medical_review";
    public static final String MASK_RECEIPT_TASK_ID =
            "dreamingfishcore:afterdream/receive_protective_mask";
    public static final int BAIZHI_MESSAGE_TASK_NUMBER = 2102;
    public static final int ENTER_RECEPTION_TASK_NUMBER = 2103;
    public static final int MEDICAL_REVIEW_TASK_NUMBER = 2104;
    public static final int MASK_RECEIPT_TASK_NUMBER = 2105;
    public static final String READ_BAIZHI_MESSAGE_GUIDANCE_ID =
            "dreamingfishcore:guidance/afterdream/read_baizhi_message";
    public static final String ENTER_RECEPTION_GUIDANCE_ID =
            "dreamingfishcore:guidance/afterdream/enter_medical_reception";
    public static final String MEET_JIANGWAN_GUIDANCE_ID =
            "dreamingfishcore:guidance/afterdream/meet_jiangwan";
    public static final String MASK_GUIDANCE_ID =
            "dreamingfishcore:guidance/afterdream/receive_protective_mask";
    public static final String COURSE_GUIDANCE_ID =
            "dreamingfishcore:guidance/afterdream/revival_course";
    public static final String FOLLOW_UP_GUIDANCE_ID =
            "dreamingfishcore:guidance/afterdream/follow_up";

    /**
     * 三次早期逆转疗程的用药间隔：一个剧情活动日。
     * 只有至少一名认证玩家在线时才会累计，因此离线等待不会消耗疗程间隔。
     */
    public static final long COURSE_DOSE_INTERVAL_TICKS = AfterdreamPlayerProgress.ACTIVE_TICKS_PER_DAY;

    /** 白芷在终检后第 3 / 7 天发出的随访私信。 */
    public static final String BAIZHI_FOLLOW_UP_THIRD_DAY_MESSAGE_ID =
            "dreamingfishcore:afterdream/baizhi/follow_up_third_day";
    public static final String BAIZHI_FOLLOW_UP_SEVENTH_DAY_MESSAGE_ID =
            "dreamingfishcore:afterdream/baizhi/follow_up_seventh_day";

    /**
     * 瞬时的界面会话门槛：只有完成首次接待后重新打开江晚对话，才可领取面具。
     * 这不是剧情事实，不会写入 story_state.json；服务端刷新台词不会重置它。
     */
    private static final Set<UUID> MASK_ELIGIBLE_DIALOGUE_SESSIONS =
            ConcurrentHashMap.newKeySet();

    public static List<StoryTaskData> createTasks() {
        return List.of(
                task(BAIZHI_MESSAGE_TASK_ID, BAIZHI_MESSAGE_TASK_NUMBER,
                        StoryTextCatalog.textOrDefault(StoryTextCatalog.AFTERDREAM_TASK_MESSAGE_NAME,
                                "阅读白芷的医疗说明"),
                        StoryTextCatalog.textOrDefault(StoryTextCatalog.AFTERDREAM_TASK_MESSAGE_CONTENT,
                                "查看白芷发来的私信，了解逐光会成立的原因、医疗接待安排和江晚的工作。"), "",
                        READ_BAIZHI_MESSAGE_GUIDANCE_ID),
                task(ENTER_RECEPTION_TASK_ID, ENTER_RECEPTION_TASK_NUMBER,
                        StoryTextCatalog.textOrDefault(StoryTextCatalog.AFTERDREAM_TASK_RECEPTION_NAME,
                                "前往逐光会医疗接待点"),
                        StoryTextCatalog.textOrDefault(StoryTextCatalog.AFTERDREAM_TASK_RECEPTION_CONTENT,
                                "进入保护区“人类逐光联合会”，前往逐光会大楼的医疗接待点。"),
                        StoryLocationResolver.referenceId(StoryLocationResolver.Role.ZHUIGUANG),
                        ENTER_RECEPTION_GUIDANCE_ID),
                task(MEDICAL_REVIEW_TASK_ID, MEDICAL_REVIEW_TASK_NUMBER,
                        StoryTextCatalog.textOrDefault(StoryTextCatalog.AFTERDREAM_TASK_REVIEW_NAME,
                                "完成江晚的感染复核"),
                        StoryTextCatalog.textOrDefault(StoryTextCatalog.AFTERDREAM_TASK_REVIEW_CONTENT,
                                "在医疗接待点与江晚当面交谈，完成感染复核并领取对应药剂。"), "",
                        MEET_JIANGWAN_GUIDANCE_ID),
                task(MASK_RECEIPT_TASK_ID, MASK_RECEIPT_TASK_NUMBER,
                        StoryTextCatalog.textOrDefault(StoryTextCatalog.AFTERDREAM_TASK_MASK_NAME,
                                "领取防护面具"),
                        StoryTextCatalog.textOrDefault(StoryTextCatalog.AFTERDREAM_TASK_MASK_CONTENT,
                                "在两天后的医疗接待点再次与江晚交谈，领取逐光会准备的防护面具。"), "",
                        MASK_GUIDANCE_ID));
    }

    public static StoryStageData createStageDefinition() {
        StoryStageData stage = new StoryStageData(STAGE_ID, 2,
                StoryTextCatalog.textOrDefault(StoryTextCatalog.AFTERDREAM_STAGE_NAME, "余梦期"),
                StoryTextCatalog.textOrDefault(StoryTextCatalog.AFTERDREAM_STAGE_DESCRIPTION,
                        STAGE_DESCRIPTION));
        createTasks().forEach(stage::addTask);
        com.hhy.dreamingfishcore.gameplay.hospital_system.HospitalStory.createTasks().forEach(stage::addTask);
        return stage;
    }

    /** 余梦期私信由阶段脚本决定后续，不走 NPC 配置的自动 follow-up。 */
    public static boolean isJavaControlledMessage(String definitionId) {
        return BAIZHI_PUBLIC_TREATMENT_MESSAGE_ID.equals(definitionId)
                || BAIZHI_FOLLOW_UP_THIRD_DAY_MESSAGE_ID.equals(definitionId)
                || BAIZHI_FOLLOW_UP_SEVENTH_DAY_MESSAGE_ID.equals(definitionId);
    }

    /** 故事页任务由本阶段个人事实逐步开放，而不是由引导是否成功写入决定。 */
    public static boolean isTaskVisibleToPlayer(String taskKey, UUID playerId) {
        if (taskKey == null || playerId == null) {
            return false;
        }
        AfterdreamPlayerProgress progress = StoryManager.findAfterdreamProgress(playerId);
        if (progress == null) {
            return false;
        }
        AfterdreamMedicalStep step = progress.getStep();
        if (BAIZHI_MESSAGE_TASK_ID.equals(taskKey)) {
            return step != AfterdreamMedicalStep.NOT_STARTED;
        }
        if (ENTER_RECEPTION_TASK_ID.equals(taskKey)) {
            return step != AfterdreamMedicalStep.NOT_STARTED
                    && step != AfterdreamMedicalStep.MESSAGE_RECEIVED;
        }
        if (MEDICAL_REVIEW_TASK_ID.equals(taskKey)) {
            return step == AfterdreamMedicalStep.RECEPTION_READY
                    || step == AfterdreamMedicalStep.INTRODUCTION
                    || step == AfterdreamMedicalStep.RESULT_LEVEL_ONE
                    || step == AfterdreamMedicalStep.RESULT_NONINFECTED
                    || step == AfterdreamMedicalStep.RESULT_LEVEL_TWO
                    || step == AfterdreamMedicalStep.AWAITING_TREATMENT
                    || step == AfterdreamMedicalStep.MASK_RECEIVED
                    || step == AfterdreamMedicalStep.COMPLETED;
        }
        if (MASK_RECEIPT_TASK_ID.equals(taskKey)) {
            return progress.isMaskReceived()
                    || (progress.isFirstReceptionCompleted()
                    && worldProgress().isMaskDistributionAvailable());
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

    private static final String STORY_DATE = "危机第2日";

    private AfterdreamStory() {
    }


    /** 阶段切换时发布一次公开救治公告。 */
    public static synchronized void onStageActivated(String stageId) {
        if (!canWrite() || !STAGE_ID.equals(stageId)) {
            return;
        }
        // 第二阶段一进入就固定丧尸挖掘的倒计时起点。这个事件使用世界
        // gameTime，而不是“在线活动时间”，所以空服和重启都不会把两天暂停。
        ensureZombieDiggingCountdown(ServerLifecycleHooks.getCurrentServer());
        ensurePublicNotice();
        ensureRecoveryRulesNotice();
        // 如果服务器恰好在“能力已写入、公告尚未写入”的瞬间重启，
        // 进度文件会保留 enabled=true。入口要把缺失的公告补发，不能让
        // 这个一次性事件停在半完成状态。
        if (worldProgress().isZombieDiggingEnabled()) {
            ensureZombieDiggingNotice();
        }
        if (worldProgress().isMaskDistributionAvailable()) {
            ensureMaskNotice();
        }
    }

    /** 新的 NPC 界面会话开始；打开其他 NPC 会结束江晚的领取会话。 */
    public static synchronized void onNpcDialogueOpened(ServerPlayer player, int npcId) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        MASK_ELIGIBLE_DIALOGUE_SESSIONS.remove(playerId);
        if (!canWrite() || !isCurrentStage()) {
            return;
        }
        if (npcId == BAIZHI_NPC_ID) {
            // 白芷负责终检后的长期随访：当面交谈即完成一次待办复核。
            AfterdreamPlayerProgress baizhiProgress = StoryManager.findAfterdreamProgress(playerId);
            if (baizhiProgress != null) {
                completeFollowUpReview(player, baizhiProgress, currentActiveTick());
            }
            return;
        }
        if (npcId != JIANGWAN_NPC_ID) {
            return;
        }
        AfterdreamPlayerProgress progress = StoryManager.findAfterdreamProgress(playerId);
        if (progress != null
                && progress.isFirstReceptionCompleted()
                && !progress.isMaskReceived()
                && worldProgress().isMaskDistributionAvailable()) {
            MASK_ELIGIBLE_DIALOGUE_SESSIONS.add(playerId);
        }
    }

    /** 玩家离线或切换世界时清理不持久化的界面会话。 */
    public static synchronized void onPlayerDisconnected(ServerPlayer player) {
        if (player != null) {
            MASK_ELIGIBLE_DIALOGUE_SESSIONS.remove(player.getUUID());
        }
    }

    public static synchronized void clearTransientSessions() {
        MASK_ELIGIBLE_DIALOGUE_SESSIONS.clear();
    }

    /** 登录入口：公告之后向所有已认证玩家发送白芷私信，并重试个人投影。 */
    public static synchronized void onPlayerAuthenticated(ServerPlayer player) {
        if (!canWrite() || player == null || !isCurrentStage()) {
            return;
        }
        ensurePublicNotice();
        ensureRecoveryRulesNotice();
        if (worldProgress().isMaskDistributionAvailable()) {
            ensureMaskNotice();
        }
        sendBaizhiMessage(player);
        AfterdreamPlayerProgress progress = progressFor(player.getUUID());
        rebuildPlayerProjections(player, progress);
        syncPlayer(player);
    }

    /** 登录/重启后的幂等投影修复；不会改变个人状态机游标。 */
    private static void rebuildPlayerProjections(
            ServerPlayer player, AfterdreamPlayerProgress progress) {
        var data = PlayerAttributesDataManager.findStoredPlayerAttributesData(player.getUUID());
        boolean medicationApplicable = data == null || data.getInfectionLevel() < 2;
        var targets = progress.pendingGuidance(worldProgress().isMaskDistributionAvailable(), medicationApplicable);
        if (progress.getStep() != AfterdreamMedicalStep.NOT_STARTED
                && progress.getStep() != AfterdreamMedicalStep.MESSAGE_RECEIVED) {
            recordTask(player, BAIZHI_MESSAGE_TASK_ID);
            if (progress.getStep() != AfterdreamMedicalStep.MESSAGE_READ) {
                recordTask(player, ENTER_RECEPTION_TASK_ID);
            }
        }
        if (progress.isMedicalTreatmentCompleted() || progress.isFirstReceptionCompleted() && !medicationApplicable) {
            recordTask(player, MEDICAL_REVIEW_TASK_ID);
        }
        if (progress.isMaskReceived()) {
            recordTask(player, MASK_RECEIPT_TASK_ID);
        }
        if (targets.contains(AfterdreamPlayerProgress.GuidanceTarget.READ_MESSAGE)) {
            createReadGuidance(player);
        } else {
            GuidanceManager.resolve(player.getUUID(), READ_BAIZHI_MESSAGE_GUIDANCE_ID);
        }
        if (targets.contains(AfterdreamPlayerProgress.GuidanceTarget.ENTER_RECEPTION)) {
            createEnterReceptionGuidance(player);
        } else {
            GuidanceManager.resolve(player.getUUID(), ENTER_RECEPTION_GUIDANCE_ID);
        }
        if (targets.contains(AfterdreamPlayerProgress.GuidanceTarget.MEDICAL_REVIEW)) {
            createMeetJiangwanGuidance(player);
        } else {
            GuidanceManager.resolve(player.getUUID(), MEET_JIANGWAN_GUIDANCE_ID);
        }
        if (targets.contains(AfterdreamPlayerProgress.GuidanceTarget.COURSE)) {
            createCourseGuidance(player);
        } else {
            GuidanceManager.resolve(player.getUUID(), COURSE_GUIDANCE_ID);
        }
        if (targets.contains(AfterdreamPlayerProgress.GuidanceTarget.FOLLOW_UP)) {
            createFollowUpGuidance(player);
        } else {
            GuidanceManager.resolve(player.getUUID(), FOLLOW_UP_GUIDANCE_ID);
        }
        if (targets.contains(AfterdreamPlayerProgress.GuidanceTarget.MASK)) {
            createMaskGuidance(player);
        } else {
            GuidanceManager.resolve(player.getUUID(), MASK_GUIDANCE_ID);
        }
    }

    /** 公告终端被读后，白芷私信才会再次重试。 */
    public static synchronized void onNoticeRead(
            ServerPlayer player, String noticeKey) {
        if (!canWrite() || player == null || !PUBLIC_NOTICE_KEY.equals(noticeKey)
                || !isCurrentStage()) {
            return;
        }
        sendBaizhiMessage(player);
    }

    /** 白芷私信已读后建立“前往接待点”个人引导。 */
    public static synchronized void onNpcMessageRead(
            ServerPlayer player, String definitionId, int npcId) {
        if (!canWrite() || player == null || npcId != BAIZHI_NPC_ID
                || !BAIZHI_PUBLIC_TREATMENT_MESSAGE_ID.equals(definitionId)
                || !isCurrentStage()) {
            return;
        }
        AfterdreamPlayerProgress progress = progressFor(player.getUUID());
        if (progress.recordMessageRead(currentActiveTick())) {
            StoryManager.markDirty();
        }
        // 重放依据当前事实修复投影；已完成的接待步骤不会重新成为目标。
        rebuildPlayerProjections(player, progress);
        syncPlayer(player);
    }

    /** 玩家进入稳定的逐光会地点后，建立江晚接待引导。 */
    public static synchronized void onLocationEntered(
            ServerPlayer player, TaskLocationDefinition location) {
        if (!canWrite() || player == null || location == null
                || !(StoryLocationResolver.matches(StoryLocationResolver.Role.ZHUIGUANG, location)
                        || isInsideActiveMedicalLocation(player))
                || !isCurrentStage()) {
            return;
        }
        AfterdreamPlayerProgress progress = progressFor(player.getUUID());
        if (progress.getStep() != AfterdreamMedicalStep.MESSAGE_READ) {
            return;
        }
        GuidanceManager.resolve(player.getUUID(),
                AfterdreamStory.READ_BAIZHI_MESSAGE_GUIDANCE_ID);
        GuidanceManager.resolve(player.getUUID(),
                AfterdreamStory.ENTER_RECEPTION_GUIDANCE_ID);
        createMeetJiangwanGuidance(player);
        recordTask(player, AfterdreamStory.ENTER_RECEPTION_TASK_ID);
        progress.setStep(AfterdreamMedicalStep.RECEPTION_READY, currentActiveTick());
        StoryManager.markDirty();
        syncPlayer(player);
    }

    /**
     * 江晚交互状态机。第一次打开界面展示共同开场，随后点击推进终端复核和结果；
     * 面具领取还要求玩家在完成首次接待后重新打开一次江晚对话。
     */
    public static synchronized void onNpcInteraction(
            ServerPlayer player, int npcId, String locationId) {
        if (!canWrite() || player == null || npcId != JIANGWAN_NPC_ID
                || !isMedicalLocation(locationId) || !isCurrentStage()) {
            return;
        }
        AfterdreamPlayerProgress progress = progressFor(player.getUUID());
        long activeTick = currentActiveTick();
        progress.incrementJiangwanInteraction(activeTick);
        StoryManager.markDirty();

        switch (progress.getStep()) {
            case RECEPTION_READY -> {
                progress.setStep(AfterdreamMedicalStep.INTRODUCTION, activeTick);
                StoryManager.markDirty();
                syncPlayer(player);
            }
            case INTRODUCTION -> completeFirstReception(player, progress, activeTick);
            case RESULT_LEVEL_TWO -> {
                // 二级感染的唯一正规出路是三次疗程：结果对话已经说明药剂无效，
                // 下一次交互直接开始第一次治疗（面具可领取时仍优先发放面具）。
                if (tryGrantMaskInSession(player, progress, activeTick)) {
                    return;
                }
                startCourse(player, progress, activeTick);
            }
            case RESULT_LEVEL_ONE, RESULT_NONINFECTED -> {
                // 结果对话已经在上一次交互中展示完毕。若全服面具已可领取，
                // 只有新的江晚对话会话才直接尝试发放；背包满时保留原状态以便重试。
                if (tryGrantMaskInSession(player, progress, activeTick)) {
                    return;
                }
                progress.setStep(AfterdreamMedicalStep.AWAITING_TREATMENT, activeTick);
                StoryManager.markDirty();
                syncPlayer(player);
            }
            case COURSE_IN_PROGRESS -> advanceCourse(player, progress, activeTick);
            case MASK_RECEIVED -> {
                AfterdreamMedicalStep next = progress.isCourseActive()
                        ? AfterdreamMedicalStep.COURSE_IN_PROGRESS
                        : (progress.isMedicalTreatmentCompleted()
                        ? AfterdreamMedicalStep.COMPLETED
                        : AfterdreamMedicalStep.AWAITING_TREATMENT);
                progress.setStep(next, activeTick);
                StoryManager.markDirty();
                syncPlayer(player);
            }
            case AWAITING_TREATMENT, COMPLETED -> {
                if (tryGrantMaskInSession(player, progress, activeTick)) {
                    return;
                }
                // 旧存档里的二级感染此前停在“无药可治”；现在可以直接转入疗程。
                if (isLevelTwoInfection(player)) {
                    startCourse(player, progress, activeTick);
                    return;
                }
                StoryManager.markDirty();
                syncPlayer(player);
            }
            default -> {
                // 尚未读完白芷说明，或已经处于纯展示状态；交互不能越过前置事实。
            }
        }
    }

    /** 只有实际服用药剂且状态已清零，才解锁后续医疗任务。 */
    public static synchronized void onGeneRevivalPotionUsed(ServerPlayer player) {
        if (!canWrite() || player == null || !isCurrentStage()) {
            return;
        }
        AfterdreamPlayerProgress progress = StoryManager.findAfterdreamProgress(player.getUUID());
        if (progress == null || progress.isMedicalTreatmentCompleted()
                || !progress.isPotionGranted()
                || (progress.getStep() != AfterdreamMedicalStep.RESULT_LEVEL_ONE
                && progress.getStep() != AfterdreamMedicalStep.RESULT_NONINFECTED
                && progress.getStep() != AfterdreamMedicalStep.AWAITING_TREATMENT
                && progress.getStep() != AfterdreamMedicalStep.MASK_RECEIVED)) {
            return;
        }
        PlayerAttributesData data = PlayerAttributesDataManager.findStoredPlayerAttributesData(
                player.getUUID());
        if (data == null || data.isInfected() || data.getCurrentInfection() > 0.001F) {
            return;
        }
        data.clearInfectionTreatmentDeadline();
        PlayerAttributesDataManager.updatePlayerAttributesData(player, data);
        progress.markMedicalTreatmentCompleted(currentActiveTick());
        GuidanceManager.resolve(player.getUUID(),
                AfterdreamStory.MEET_JIANGWAN_GUIDANCE_ID);
        recordTask(player, AfterdreamStory.MEDICAL_REVIEW_TASK_ID);
        if (progress.isFirstReceptionCompleted() && !progress.isMaskReceived()
                && worldProgress().isMaskDistributionAvailable()) {
            createMaskGuidance(player);
        }
        StoryManager.markDirty();
        syncPlayer(player);
    }

    /** 每服务器 tick 调用；只处理面具倒计时等“在线玩家”剧情事实。 */
    public static synchronized void tickActiveTime(MinecraftServer server) {
        if (!canWrite() || server == null || !isCurrentStage()) {
            return;
        }
        // 随访提醒按剧情活动时间推进，与面具倒计时共用同一时钟。
        tickFollowUps(server, currentActiveTick());
        boolean becameAvailable = worldProgress().advanceMaskCountdown(currentActiveTick());
        if (becameAvailable) {
            StoryManager.markDirty();
        }
        if (becameAvailable) {
            ensureMaskNotice();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (AuthSessionGuard.isAuthenticated(player)) {
                    AfterdreamPlayerProgress progress = progressFor(player.getUUID());
                    if (progress.isFirstReceptionCompleted() && !progress.isMaskReceived()) {
                        createMaskGuidance(player);
                        syncPlayer(player);
                    }
                }
            }
        }
    }

    /**
     * 每服务器 tick 调用一次的世界事件入口。与个人剧情的在线活动时间分开，
     * 只用主世界 gameTime 计算第二阶段开场后的两游戏日延迟。
     */
    public static synchronized void tickWorldTime(MinecraftServer server) {
        if (!canWrite() || server == null || !isCurrentStage()) {
            return;
        }
        ensureZombieDiggingCountdown(server);
        AfterdreamWorldProgress progress = worldProgress();
        // 能力事实已经落盘时，即使上一次发布公告被中断，也要继续补发公告。
        if (progress.isZombieDiggingEnabled()) {
            ensureZombieDiggingNotice();
            return;
        }
        if (!progress.isZombieDiggingDue(currentGameTime(server))) {
            return;
        }

        if (!progress.isZombieDiggingEnabled()) {
            try {
                // 只改当前阶段的 digging 覆盖项；远程服务器已有的听力、开门、
                // 破门、广播和保护区规则全部保持不变。
                ZombieSpeciesConfig.setAbilityForStage(
                        STAGE_ID, ZombieSpeciesConfig.Ability.DIGGING, true);
            } catch (RuntimeException exception) {
                DreamingFishCore.LOGGER.error(
                        "余梦期丧尸挖掘能力开启失败，将在下一次服务器 tick 重试", exception);
                return;
            }
            if (progress.markZombieDiggingEnabled()) {
                StoryManager.markDirty();
                DreamingFishCore.LOGGER.info("余梦期丧尸挖掘能力已开启");
            }
        }
        ensureZombieDiggingNotice();
    }

    /** 第一名新的一级感染者超时转化后，由感染系统调用。 */
    public static synchronized void onVirusEvolution() {
        if (!canWrite() || !isCurrentStage()
                || worldProgress().isVirusEvolutionAnnouncementSent()) {
            return;
        }
        NoticeData notice = findOrCreateNotice(
                VIRUS_EVOLUTION_NOTICE_KEY,
                StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_EVOLUTION_NOTICE_TITLE),
                StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_EVOLUTION_NOTICE_CONTENT));
        if (notice != null) {
            if (!worldProgress().markVirusEvolutionAnnouncementSent()) {
                return;
            }
            StoryManager.markDirty();
            setWorldFlagIfNeeded(VIRUS_EVOLUTION_NOTICE_PUBLISHED_FLAG);
            NoticeDeliveryService.publishToAllOnlinePlayers(notice);
        }
    }

    public static synchronized Optional<List<String>> getDialogueOverride(
            ServerPlayer player, int npcId) {
        if (!StoryManager.areWritesEnabled() || player == null || !isCurrentStage()) {
            return Optional.empty();
        }
        AfterdreamPlayerProgress progress = StoryManager.findAfterdreamProgress(player.getUUID());
        if (progress == null) {
            return Optional.empty();
        }
        if (npcId == BAIZHI_NPC_ID) {
            // 白芷只负责随访对白：有待办随访时播报对应那一档，其余情况交回默认台词。
            if (progress.isFollowUpThirdDayNotified() && !progress.isFollowUpThirdDayCompleted()) {
                return dialogue(StoryTextCatalog.AFTERDREAM_BAIZHI_FOLLOWUP_THIRD_DAY);
            }
            if (progress.isFollowUpSeventhDayNotified() && !progress.isFollowUpSeventhDayCompleted()) {
                return dialogue(StoryTextCatalog.AFTERDREAM_BAIZHI_FOLLOWUP_SEVENTH_DAY);
            }
            return Optional.empty();
        }
        if (npcId != JIANGWAN_NPC_ID) {
            return Optional.empty();
        }
        return switch (progress.getStep()) {
            case RECEPTION_READY -> dialogue(StoryTextCatalog.AFTERDREAM_JIANGWAN_COMMON);
            case INTRODUCTION -> dialogue(StoryTextCatalog.AFTERDREAM_JIANGWAN_CHECKING);
            case RESULT_LEVEL_ONE -> dialogue(StoryTextCatalog.AFTERDREAM_JIANGWAN_LEVEL_ONE);
            case RESULT_NONINFECTED -> dialogue(StoryTextCatalog.AFTERDREAM_JIANGWAN_NONINFECTED);
            case RESULT_LEVEL_TWO -> dialogue(StoryTextCatalog.AFTERDREAM_JIANGWAN_LEVEL_TWO);
            case COURSE_IN_PROGRESS -> courseDialogue(progress);
            case AWAITING_TREATMENT -> {
                var attributes = PlayerAttributesDataManager.findStoredPlayerAttributesData(player.getUUID());
                yield attributes != null && attributes.getInfectionLevel() >= 2
                        ? dialogue(StoryTextCatalog.AFTERDREAM_JIANGWAN_LEVEL_TWO_COURSE)
                        : dialogue(StoryTextCatalog.AFTERDREAM_JIANGWAN_AWAITING);
            }
            case MASK_RECEIVED -> dialogue(StoryTextCatalog.AFTERDREAM_JIANGWAN_MASK);
            case COMPLETED -> worldProgress().isMaskDistributionAvailable()
                    && !progress.isMaskReceived()
                    ? dialogue(StoryTextCatalog.AFTERDREAM_JIANGWAN_MASK)
                    : dialogue(StoryTextCatalog.AFTERDREAM_JIANGWAN_COMPLETED);
            default -> Optional.empty();
        };
    }

    /** 疗程中的江晚对白：终检 / 可以治疗 / 还没到间隔，三种情况各一套。 */
    private static Optional<List<String>> courseDialogue(AfterdreamPlayerProgress progress) {
        if (progress.isFinalCheckReady()) {
            return dialogue(StoryTextCatalog.AFTERDREAM_JIANGWAN_COURSE_FINAL);
        }
        if (!progress.isNextDoseAvailable(currentActiveTick())) {
            return dialogue(StoryTextCatalog.AFTERDREAM_JIANGWAN_COURSE_WAITING);
        }
        return dialogue(progress.getCourseDoses() == 0
                ? StoryTextCatalog.AFTERDREAM_JIANGWAN_COURSE_START
                : StoryTextCatalog.AFTERDREAM_JIANGWAN_COURSE_DOSE);
    }

    public static synchronized String getDialogueRevision(ServerPlayer player, int npcId) {
        if (player == null || npcId != JIANGWAN_NPC_ID || !isCurrentStage()) {
            return "";
        }
        AfterdreamPlayerProgress progress = StoryManager.findAfterdreamProgress(player.getUUID());
        if (progress == null) {
            return "";
        }
        return "afterdream/" + progress.getStep().name()
                + (worldProgress().isMaskDistributionAvailable() ? "/mask-ready" : "")
                + (progress.isMaskReceived() ? "/mask-received" : "");
    }

    public static synchronized AfterdreamMedicalStep getStep(UUID playerId) {
        if (!StoryManager.areWritesEnabled() || playerId == null) {
            return AfterdreamMedicalStep.NOT_STARTED;
        }
        AfterdreamPlayerProgress progress = StoryManager.findAfterdreamProgress(playerId);
        return progress == null ? AfterdreamMedicalStep.NOT_STARTED : progress.getStep();
    }

    public static synchronized AfterdreamPlayerProgress getPlayerProgress(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return StoryManager.findAfterdreamProgress(playerId);
    }

    public static synchronized AfterdreamWorldProgress getWorldProgress() {
        return worldProgress();
    }

    public static synchronized boolean isLoaded() {
        return StoryManager.areWritesEnabled();
    }

    public static synchronized boolean areWritesEnabled() {
        return StoryManager.areWritesEnabled();
    }

    private static void completeFirstReception(
            ServerPlayer player, AfterdreamPlayerProgress progress, long activeTick) {
        PlayerAttributesData data = PlayerAttributesDataManager.findStoredPlayerAttributesData(
                player.getUUID());
        if (data == null) {
            return;
        }

        // 策划案规定：江晚完成首次终端复核后，无论玩家当前是否感染，都实际
        // 发放一瓶基因复苏试剂。二级感染者不能被这瓶药治愈，但仍要把“已发放”
        // 这个事实记录下来；药剂的使用入口会继续拒绝二级感染者。
        if (!progress.isPotionGranted()) {
            ItemStack potion = new ItemStack(DreamingFishCore_Items.GENE_RESURGENCE_POTION.get());
            if (!player.getInventory().add(potion)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_INVENTORY_FULL)));
                return;
            }
            progress.markPotionGranted(activeTick);
        }

        int level = data.getInfectionLevel();
        if (level == PlayerAttributesData.INFECTION_LEVEL_TWO) {
            progress.setStep(AfterdreamMedicalStep.RESULT_LEVEL_TWO, activeTick);
        } else {
            progress.setStep(level == PlayerAttributesData.INFECTION_LEVEL_ONE
                    ? AfterdreamMedicalStep.RESULT_LEVEL_ONE
                    : AfterdreamMedicalStep.RESULT_NONINFECTED, activeTick);
        }
        if (progress.isFirstReceptionCompleted()) {
            if (worldProgress().startMaskCountdown(activeTick)) {
                StoryManager.markDirty();
                DreamingFishCore.LOGGER.info("余梦期面具倒计时启动：{} ticks", activeTick);
            }
        }
        StoryManager.markDirty();
        syncPlayer(player);
    }

    /** 面具领取的统一入口：只有新的江晚对话会话才允许发放，避免每次交互都塞一件。 */
    private static boolean tryGrantMaskInSession(
            ServerPlayer player, AfterdreamPlayerProgress progress, long activeTick) {
        if (!worldProgress().isMaskDistributionAvailable()
                || !progress.isFirstReceptionCompleted()
                || progress.isMaskReceived()
                || !isMaskEligibleDialogueSession(player)) {
            return false;
        }
        tryGrantMask(player, progress, activeTick);
        return true;
    }

    private static boolean isLevelTwoInfection(ServerPlayer player) {
        PlayerAttributesData data = PlayerAttributesDataManager.findStoredPlayerAttributesData(player.getUUID());
        return data != null && data.getInfectionLevel() >= PlayerAttributesData.INFECTION_LEVEL_TWO;
    }

    /**
     * 开始三次早期逆转疗程。进入当次交互即完成第一次治疗；后续治疗由
     * {@link #advanceCourse} 按“一个剧情活动日”的间隔推进。
     */
    private static void startCourse(
            ServerPlayer player, AfterdreamPlayerProgress progress, long activeTick) {
        if (!isLevelTwoInfection(player)) {
            // 感染等级已被其他途径改变（例如二级之前自行用药）：不进入疗程，
            // 只刷新投影，让玩家看到当前真实状态。
            StoryManager.markDirty();
            syncPlayer(player);
            return;
        }
        if (progress.getStep() != AfterdreamMedicalStep.COURSE_IN_PROGRESS) {
            progress.startCourse(activeTick);
        }
        StoryManager.markDirty();
        advanceCourse(player, progress, activeTick);
    }

    /** 疗程推进：按间隔完成一次院内治疗，或在三次完成后执行终检。 */
    private static void advanceCourse(
            ServerPlayer player, AfterdreamPlayerProgress progress, long activeTick) {
        if (progress.isFinalCheckReady()) {
            completeCourse(player, progress, activeTick);
            return;
        }
        if (!progress.isNextDoseAvailable(activeTick)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_COURSE_WAITING)));
            syncPlayer(player);
            return;
        }
        // 院内治疗：治疗在接待点由江晚执行，不消耗玩家物品，也不依赖药剂使用事件。
        if (!progress.recordCourseDose(activeTick, COURSE_DOSE_INTERVAL_TICKS)) {
            return;
        }
        StoryManager.markDirty();
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_COURSE_DOSE_DONE)));
        syncPlayer(player);
    }

    /** 终检：感染读数清零、解除感染状态，并进入第 3 / 7 天随访流程。 */
    private static void completeCourse(
            ServerPlayer player, AfterdreamPlayerProgress progress, long activeTick) {
        PlayerAttributesData data = PlayerAttributesDataManager.findStoredPlayerAttributesData(player.getUUID());
        if (data == null) {
            return;
        }
        // 服务端事实：三次疗程 + 终检之后才允许把二级感染清零。
        data.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_NONE);
        data.setCurrentInfection(0.0F);
        data.clearInfectionTreatmentDeadline();
        PlayerAttributesDataManager.updatePlayerAttributesData(player, data);
        PlayerInfectionClientSync.sendInfectionDataToClient(
                player, 0, false, PlayerAttributesData.INFECTION_LEVEL_NONE);
        if (!progress.completeCourse(activeTick)) {
            return;
        }
        GuidanceManager.resolve(player.getUUID(), COURSE_GUIDANCE_ID);
        recordTask(player, MEDICAL_REVIEW_TASK_ID);
        if (!progress.isMaskReceived()) {
            createMaskGuidance(player);
        }
        StoryManager.markDirty();
        NotificationPushHelper.sendTopLeftNotification(
                player, StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_COURSE_COMPLETED_NOTIFICATION), 7000);
        syncPlayer(player);
    }

    /** 终检后的第 3 / 7 天随访提醒；只在玩家在线时由活动时间推进。 */
    private static void tickFollowUps(MinecraftServer server, long activeTick) {
        if (!NpcMessageManager.isWorldDataLoaded()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!AuthSessionGuard.isAuthenticated(player)) {
                continue;
            }
            AfterdreamPlayerProgress progress = StoryManager.findAfterdreamProgress(player.getUUID());
            if (progress == null) {
                continue;
            }
            int day = progress.pendingFollowUpDay(activeTick);
            if (day <= 0) {
                continue;
            }
            String messageId = day == AfterdreamPlayerProgress.FOLLOW_UP_THIRD_DAY
                    ? BAIZHI_FOLLOW_UP_THIRD_DAY_MESSAGE_ID
                    : BAIZHI_FOLLOW_UP_SEVENTH_DAY_MESSAGE_ID;
            boolean sent = NpcMessageManager.sendStoryMessage(player, messageId)
                    || NpcMessageManager.hasReceivedDefinition(player.getUUID(), messageId);
            if (!sent) {
                // 私信系统尚未就绪或消息定义缺失：保留待提醒状态，下一次 tick 继续重试。
                continue;
            }
            progress.markFollowUpNotified(day, activeTick);
            StoryManager.markDirty();
            rebuildPlayerProjections(player, progress);
            syncPlayer(player);
        }
    }

    /** 与白芷交谈即视为完成一次待办随访复核；逾期或漏做只记录，不带惩罚。 */
    private static boolean completeFollowUpReview(
            ServerPlayer player, AfterdreamPlayerProgress progress, long activeTick) {
        return completeFollowUpReview(player, progress, activeTick, 0);
    }

    /**
     * @param explicitDay 0 表示自动取当前待办的那一次；非 0 时用于调试命令指定具体天数。
     */
    private static boolean completeFollowUpReview(
            ServerPlayer player, AfterdreamPlayerProgress progress, long activeTick, int explicitDay) {
        int day = explicitDay != 0 ? explicitDay : pendingFollowUpDay(progress);
        if (day == 0 || !progress.recordFollowUpCompletion(day, activeTick)) {
            return false;
        }
        GuidanceManager.resolve(player.getUUID(), FOLLOW_UP_GUIDANCE_ID);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_FOLLOWUP_REVIEW_DONE)));
        StoryManager.markDirty();
        rebuildPlayerProjections(player, progress);
        syncPlayer(player);
        return true;
    }

    /** 当前等待复核的那一次随访；没有待办时返回 0。 */
    private static int pendingFollowUpDay(AfterdreamPlayerProgress progress) {
        if (progress.isFollowUpThirdDayNotified() && !progress.isFollowUpThirdDayCompleted()) {
            return AfterdreamPlayerProgress.FOLLOW_UP_THIRD_DAY;
        }
        if (progress.isFollowUpSeventhDayNotified() && !progress.isFollowUpSeventhDayCompleted()) {
            return AfterdreamPlayerProgress.FOLLOW_UP_SEVENTH_DAY;
        }
        return 0;
    }

    // ==================== 测试用入口（只由 3 级权限的 /dreamingfish debug 调用） ====================

    /**
     * 测试用：把玩家一键快进到指定进度。返回 {@code null} 表示成功，否则返回失败原因。
     *
     * <p>预设：</p>
     * <ul>
     *   <li>{@code reception}：二级感染 + 首次接待完成（结果为二级）——下一次与江晚交互即开始疗程。</li>
     *   <li>{@code finalcheck}：疗程三次用药已完成——下一次与江晚交互即执行终检。</li>
     *   <li>{@code followup}：疗程已完成，且第 3 天随访已提醒——可直接去找白芷验证随访对话。</li>
     * </ul>
     */
    public static synchronized String debugApplyPreset(ServerPlayer player, String preset) {
        if (player == null) {
            return "需要由玩家执行该命令";
        }
        if (!StoryManager.areWritesEnabled()) {
            return "故事系统尚未随世界加载完成，请先进入世界";
        }
        if (!isCurrentStage()) {
            // 一键快进包含阶段切换，省掉手动切换与走开场流程。
            if (!StoryManager.changeStage(STAGE_ID, player.getName().getString())) {
                return "无法切换到余梦期：请确认当前世界的故事已加载";
            }
        }

        AfterdreamPlayerProgress progress = progressFor(player.getUUID());
        long activeTick = currentActiveTick();

        switch (preset) {
            case "reception" -> {
                applyLevelTwoInfection(player);
                if (!walkToLevelTwoResult(progress, activeTick)) {
                    return "当前进度已经超过“首次接待完成”，无需再快进到该预设";
                }
            }
            case "finalcheck" -> {
                applyLevelTwoInfection(player);
                if (!walkToLevelTwoResult(progress, activeTick)) {
                    return "当前进度已经超过“首次接待完成”，请改用 followup 预设或重置剧情";
                }
                if (progress.getStep() != AfterdreamMedicalStep.COURSE_IN_PROGRESS) {
                    progress.startCourse(activeTick);
                }
                while (progress.getCourseDoses() < AfterdreamPlayerProgress.COURSE_TOTAL_DOSES) {
                    if (!progress.recordCourseDoseForTesting(activeTick, COURSE_DOSE_INTERVAL_TICKS)) {
                        break;
                    }
                }
                if (!progress.isFinalCheckReady()) {
                    return "无法构造终检前的疗程进度（用药次数未能补齐）";
                }
            }
            case "followup" -> {
                applyLevelTwoInfection(player);
                if (!walkToLevelTwoResult(progress, activeTick)) {
                    return "当前进度已经超过“首次接待完成”，请改用 followup 预设或重置剧情";
                }
                if (!progress.isMedicalTreatmentCompleted()) {
                    if (progress.getStep() != AfterdreamMedicalStep.COURSE_IN_PROGRESS) {
                        progress.startCourse(activeTick);
                    }
                    while (progress.getCourseDoses() < AfterdreamPlayerProgress.COURSE_TOTAL_DOSES) {
                        if (!progress.recordCourseDoseForTesting(activeTick, COURSE_DOSE_INTERVAL_TICKS)) {
                            break;
                        }
                    }
                    if (!progress.completeCourse(activeTick)) {
                        return "无法完成疗程终检";
                    }
                }
                if (!progress.isFollowUpThirdDayNotified()) {
                    progress.markFollowUpNotified(AfterdreamPlayerProgress.FOLLOW_UP_THIRD_DAY, activeTick);
                }
            }
            default -> {
                return "未知的预设：" + preset + "（可用：reception / finalcheck / followup）";
            }
        }

        StoryManager.markDirty();
        rebuildPlayerProjections(player, progress);
        syncPlayer(player);
        return null;
    }

    /** 沿余梦期合法迁移把进度推到“首次接待结果为二级”。已经是后续状态时返回 false。 */
    private static boolean walkToLevelTwoResult(AfterdreamPlayerProgress progress, long activeTick) {
        AfterdreamMedicalStep step = progress.getStep();
        if (step == AfterdreamMedicalStep.COURSE_IN_PROGRESS
                || step == AfterdreamMedicalStep.MASK_RECEIVED
                || step == AfterdreamMedicalStep.COMPLETED) {
            return false;
        }
        if (step == AfterdreamMedicalStep.NOT_STARTED) {
            progress.setStep(AfterdreamMedicalStep.MESSAGE_RECEIVED, activeTick);
        }
        step = progress.getStep();
        if (step == AfterdreamMedicalStep.MESSAGE_RECEIVED) {
            progress.setStep(AfterdreamMedicalStep.MESSAGE_READ, activeTick);
        }
        step = progress.getStep();
        if (step == AfterdreamMedicalStep.MESSAGE_READ) {
            progress.setStep(AfterdreamMedicalStep.RECEPTION_READY, activeTick);
        }
        step = progress.getStep();
        if (step == AfterdreamMedicalStep.RECEPTION_READY) {
            progress.setStep(AfterdreamMedicalStep.INTRODUCTION, activeTick);
        }
        step = progress.getStep();
        if (step == AfterdreamMedicalStep.INTRODUCTION) {
            progress.setStep(AfterdreamMedicalStep.RESULT_LEVEL_TWO, activeTick);
        }
        if (!progress.isPotionGranted()) {
            progress.markPotionGranted(activeTick);
        }
        if (!progress.isFirstReceptionCompleted()) {
            progress.markFirstReceptionCompleted(activeTick);
        }
        return true;
    }

    /** 把玩家直接写成二级感染者，与调试命令走同一套服务端写入与客户端同步。 */
    private static void applyLevelTwoInfection(ServerPlayer player) {
        PlayerAttributesData data = PlayerAttributesDataManager.getPlayerAttributesData(player.getUUID());
        if (data == null) {
            return;
        }
        data.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_TWO);
        data.setCurrentInfection(150.0F);
        PlayerAttributesDataManager.updatePlayerAttributesData(player, data);
        PlayerInfectionClientSync.sendInfectionDataToClient(
                player, 150, true, PlayerAttributesData.INFECTION_LEVEL_TWO);
    }


    /**
     * 测试用：直接把玩家推进到疗程完成，终检结算与正式流程完全一致
     * （清零感染、记录进度、关闭引导、发布完成通知）。
     */
    public static synchronized boolean debugCompleteCourse(ServerPlayer player) {
        if (!canWrite() || player == null || !isCurrentStage()) {
            return false;
        }
        AfterdreamPlayerProgress progress = StoryManager.findAfterdreamProgress(player.getUUID());
        if (progress == null) {
            return false;
        }
        long activeTick = currentActiveTick();
        if (progress.getStep() != AfterdreamMedicalStep.COURSE_IN_PROGRESS) {
            if (!isLevelTwoInfection(player)) {
                return false;
            }
            progress.startCourse(activeTick);
        }
        // 按真实间隔补齐剩余用药次数，保证存档里的疗程数据与正常路径一致。
        while (progress.getCourseDoses() < AfterdreamPlayerProgress.COURSE_TOTAL_DOSES) {
            long doseTick = Math.max(activeTick, progress.getCourseNextDoseAtActiveTick());
            if (!progress.recordCourseDose(doseTick, COURSE_DOSE_INTERVAL_TICKS)) {
                break;
            }
        }
        if (!progress.isFinalCheckReady()) {
            return false;
        }
        completeCourse(player, progress, activeTick);
        return progress.isMedicalTreatmentCompleted();
    }

    /** 测试用：把某一次随访直接置为“已提醒、待复核”，用于验证白芷对话与复核记录。 */
    public static synchronized boolean debugMarkFollowUpDue(ServerPlayer player, int day) {
        if (!canWrite() || player == null || !isCurrentStage()) {
            return false;
        }
        AfterdreamPlayerProgress progress = StoryManager.findAfterdreamProgress(player.getUUID());
        if (progress == null || progress.getCourseCompletedAtActiveTick() < 0L) {
            return false;
        }
        long activeTick = currentActiveTick();
        if (!progress.isFollowUpThirdDayNotified()) {
            progress.markFollowUpNotified(AfterdreamPlayerProgress.FOLLOW_UP_THIRD_DAY, activeTick);
        }
        if (day == AfterdreamPlayerProgress.FOLLOW_UP_SEVENTH_DAY
                && !progress.isFollowUpSeventhDayNotified()) {
            progress.markFollowUpNotified(AfterdreamPlayerProgress.FOLLOW_UP_SEVENTH_DAY, activeTick);
        }
        if (!progress.isFollowUpAwaitingReview()) {
            return false;
        }
        StoryManager.markDirty();
        rebuildPlayerProjections(player, progress);
        syncPlayer(player);
        return true;
    }

    /** 测试用：直接把某一次随访记为已复核，跳过与白芷的对话。 */
    public static synchronized boolean debugCompleteFollowUp(ServerPlayer player, int day) {
        if (!canWrite() || player == null || !isCurrentStage()) {
            return false;
        }
        AfterdreamPlayerProgress progress = StoryManager.findAfterdreamProgress(player.getUUID());
        if (progress == null || progress.getCourseCompletedAtActiveTick() < 0L) {
            return false;
        }
        long activeTick = currentActiveTick();
        if (!progress.isFollowUpThirdDayNotified()) {
            progress.markFollowUpNotified(AfterdreamPlayerProgress.FOLLOW_UP_THIRD_DAY, activeTick);
        }
        if (day == AfterdreamPlayerProgress.FOLLOW_UP_SEVENTH_DAY
                && !progress.isFollowUpSeventhDayNotified()) {
            progress.markFollowUpNotified(AfterdreamPlayerProgress.FOLLOW_UP_SEVENTH_DAY, activeTick);
        }
        return completeFollowUpReview(player, progress, activeTick, day);
    }

    private static void createCourseGuidance(ServerPlayer player) {
        GuidanceSeed seed = new GuidanceSeed(
                COURSE_GUIDANCE_ID,
                StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_COURSE_GUIDANCE_TITLE),
                StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_COURSE_GUIDANCE_CONTENT))
                .withStoryStage(STAGE_ID);
        addMedicalLocation(seed);
        GuidanceManager.ensureActiveFromStoryEvent(player.getUUID(), seed,
                "dreamingfishcore:afterdream/event/revival_course_started", JIANGWAN_NPC_ID,
                "江晚", StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_COURSE_GUIDANCE_QUOTE));
    }

    private static void createFollowUpGuidance(ServerPlayer player) {
        GuidanceSeed seed = new GuidanceSeed(
                FOLLOW_UP_GUIDANCE_ID,
                StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_FOLLOWUP_GUIDANCE_TITLE),
                StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_FOLLOWUP_GUIDANCE_CONTENT))
                .withStoryStage(STAGE_ID);
        addMedicalLocation(seed);
        GuidanceManager.ensureActiveFromStoryEvent(player.getUUID(), seed,
                "dreamingfishcore:afterdream/event/follow_up_due", BAIZHI_NPC_ID,
                "白芷", StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_FOLLOWUP_GUIDANCE_QUOTE));
    }

    private static void tryGrantMask(
            ServerPlayer player, AfterdreamPlayerProgress progress, long activeTick) {
        // 玩家属性存档是面具领取事实的第二份幂等账本。服务器可能在上一轮
        // 已经把物品和全局旗标写好，却在章节进度落盘前中断；此时补齐进度，
        // 不要再往背包塞第二件面具。
        PlayerAttributesData existingData = PlayerAttributesDataManager
                .findStoredPlayerAttributesData(player.getUUID());
        if (existingData != null && existingData.hasReceivedProtectiveMask()) {
            if (!PlayerInfectionManager.onProtectiveMaskGranted(player)) {
                // The attribute receipt may have been written before the
                // world flag.  Keep the chapter state retryable until the
                // global infection rule can be committed as well.
                return;
            }
            progress.markMaskReceived(activeTick);
            progress.setStep(AfterdreamMedicalStep.MASK_RECEIVED, activeTick);
            MASK_ELIGIBLE_DIALOGUE_SESSIONS.remove(player.getUUID());
            GuidanceManager.resolve(player.getUUID(), AfterdreamStory.MASK_GUIDANCE_ID);
            recordTask(player, AfterdreamStory.MASK_RECEIPT_TASK_ID);
            if (worldProgress().markFirstMaskGranted(activeTick)) {
                StoryManager.markDirty();
            }
            if (!progress.isMedicalTreatmentCompleted()) {
                createMeetJiangwanGuidance(player);
            }
            StoryManager.markDirty();
            syncPlayer(player);
            return;
        }

        // 面具不可堆叠；记录 add() 实际使用的空槽位，失败时只回滚本次
        // 发放的那一件，不误删玩家原本已经拥有的面具。
        int grantSlot = player.getInventory().getFreeSlot();
        ItemStack mask = new ItemStack(DreamingFishCore_Items.PROTECTIVE_MASK.get());
        if (!player.getInventory().add(mask)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_INVENTORY_FULL)));
            return;
        }
        if (!PlayerInfectionManager.onProtectiveMaskGranted(player)) {
            rollbackGrantedMask(player, grantSlot);
            return;
        }
        progress.markMaskReceived(activeTick);
        progress.setStep(AfterdreamMedicalStep.MASK_RECEIVED, activeTick);
        MASK_ELIGIBLE_DIALOGUE_SESSIONS.remove(player.getUUID());
        GuidanceManager.resolve(player.getUUID(), AfterdreamStory.MASK_GUIDANCE_ID);
        recordTask(player, AfterdreamStory.MASK_RECEIPT_TASK_ID);
        worldProgress().markFirstMaskGranted(activeTick);
        if (!progress.isMedicalTreatmentCompleted()) {
            createMeetJiangwanGuidance(player);
        }
        StoryManager.markDirty();
        NotificationPushHelper.sendTopLeftNotification(
                player, StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_MASK_RECEIVED_NOTIFICATION), 7000);
        syncPlayer(player);
    }

    private static void rollbackGrantedMask(ServerPlayer player, int grantSlot) {
        if (grantSlot >= 0 && grantSlot < player.getInventory().getContainerSize()) {
            ItemStack stack = player.getInventory().getItem(grantSlot);
            if (stack.getItem() == DreamingFishCore_Items.PROTECTIVE_MASK.get()) {
                stack.shrink(1);
                player.getInventory().setChanged();
                return;
            }
        }
        // Inventory.add normally uses the first free slot.  Keep a defensive
        // fallback for platform changes where it chooses a different slot;
        // it still removes only one mask and never touches unrelated items.
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() == DreamingFishCore_Items.PROTECTIVE_MASK.get()) {
                stack.shrink(1);
                player.getInventory().setChanged();
                return;
            }
        }
    }

    private static void sendBaizhiMessage(ServerPlayer player) {
        if (StoryManager.isTaskWaived(BAIZHI_MESSAGE_TASK_ID, player.getUUID())) {
            return;
        }
        // 内容包加载顺序中 NPC 私信可能晚于剧情状态机；在它尚未就绪时不要把
        // “消息已收到”写进玩家状态，稍后登录/公告重试即可。
        if (!NpcMessageManager.isWorldDataLoaded()) {
            return;
        }
        boolean sent = NpcMessageManager.sendStoryMessage(
                player, BAIZHI_PUBLIC_TREATMENT_MESSAGE_ID)
                || NpcMessageManager.hasReceivedDefinition(
                player.getUUID(), BAIZHI_PUBLIC_TREATMENT_MESSAGE_ID);
        if (!sent) {
            return;
        }
        AfterdreamPlayerProgress progress = progressFor(player.getUUID());
        // 已经读过的当前阶段私信是玩家事实的一部分；重启或本地重置故事
        // 状态后，直接恢复到“已读”节点，不要求玩家再打开一次旧消息。
        if (NpcMessageManager.hasReadDefinition(
                player.getUUID(), BAIZHI_PUBLIC_TREATMENT_MESSAGE_ID)) {
            if (progress.getStep() == AfterdreamMedicalStep.NOT_STARTED
                    || progress.getStep() == AfterdreamMedicalStep.MESSAGE_RECEIVED) {
                progress.setStep(AfterdreamMedicalStep.MESSAGE_READ, currentActiveTick());
                StoryManager.markDirty();
            }
        } else if (progress.getStep() == AfterdreamMedicalStep.NOT_STARTED) {
            progress.setStep(AfterdreamMedicalStep.MESSAGE_RECEIVED, currentActiveTick());
            StoryManager.markDirty();
        }
    }

    private static void createReadGuidance(ServerPlayer player) {
        GuidanceSeed seed = new GuidanceSeed(
                AfterdreamStory.READ_BAIZHI_MESSAGE_GUIDANCE_ID,
                StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_READ_GUIDANCE_TITLE),
                StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_READ_GUIDANCE_CONTENT))
                .withStoryStage(STAGE_ID);
        GuidanceManager.ensureActiveFromStoryEvent(player.getUUID(), seed,
                "dreamingfishcore:afterdream/event/baizhi_message_received", BAIZHI_NPC_ID,
                "白芷", StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_READ_GUIDANCE_QUOTE));
    }

    private static void createEnterReceptionGuidance(ServerPlayer player) {
        GuidanceSeed seed = new GuidanceSeed(
                AfterdreamStory.ENTER_RECEPTION_GUIDANCE_ID,
                StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_ENTER_GUIDANCE_TITLE),
                com.hhy.dreamingfishcore.gameplay.hospital_system.HospitalStory.isOpen()
                        ? "医疗接待已迁入阿拜多斯医院，请按当前地点引导前往。"
                        : StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_ENTER_GUIDANCE_CONTENT))
                .withStoryStage(STAGE_ID);
        addMedicalLocation(seed);
        GuidanceManager.ensureActiveFromStoryEvent(player.getUUID(), seed,
                "dreamingfishcore:afterdream/event/baizhi_message_read", BAIZHI_NPC_ID,
                "白芷", StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_ENTER_GUIDANCE_QUOTE));
    }

    public static void refreshGuidance(ServerPlayer player) {
        if (player != null && canWrite() && isCurrentStage()) syncPlayer(player);
    }

    public static void updateTaskLocationForView(StoryTaskData task) {
        if (!java.util.Set.of(ENTER_RECEPTION_TASK_ID, MEDICAL_REVIEW_TASK_ID, MASK_RECEIPT_TASK_ID).contains(task.getTaskKey())
                || !com.hhy.dreamingfishcore.gameplay.hospital_system.HospitalStory.isOpen()) return;
        task.setLocationId(activeMedicalLocationId());
        if (ENTER_RECEPTION_TASK_ID.equals(task.getTaskKey())) task.setTaskContent("医疗接待已迁入阿拜多斯医院，请前往医院接待区。");
        if (MEDICAL_REVIEW_TASK_ID.equals(task.getTaskKey())) task.setTaskContent("到医院与江晚交谈，完成接待复核；适用早期治疗时还需实际服药。二级感染的复核与治愈分别记录。");
        if (MASK_RECEIPT_TASK_ID.equals(task.getTaskKey())) task.setTaskContent("面具开始发放后，到医院重新与江晚交谈领取。");
    }

    public static String activeMedicalLocationId() {
        return com.hhy.dreamingfishcore.gameplay.hospital_system.HospitalStory.isOpen()
                ? StoryManager.getHospitalProgress().getLocationId()
                : StoryLocationResolver.referenceId(StoryLocationResolver.Role.ZHUIGUANG);
    }

    private static boolean isInsideActiveMedicalLocation(ServerPlayer player) {
        return TaskLocationManager.getLocation(activeMedicalLocationId())
                .map(location -> location.contains(player.level().dimension(), player.blockPosition())).orElse(false);
    }

    /** 医疗接待点不再要求固定 ID：ID 或名称命中都算（见 {@link StoryLocationResolver}）。 */
    private static boolean isMedicalLocation(String id) {
        return StoryLocationResolver.matchesId(StoryLocationResolver.Role.ZHUIGUANG, id)
                || activeMedicalLocationId().equals(id);
    }

    private static void createMeetJiangwanGuidance(ServerPlayer player) {
        GuidanceSeed seed = new GuidanceSeed(
                AfterdreamStory.MEET_JIANGWAN_GUIDANCE_ID,
                StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_RECEPTION_GUIDANCE_TITLE),
                com.hhy.dreamingfishcore.gameplay.hospital_system.HospitalStory.isOpen()
                        ? "到医院与江晚交谈，完成接待与感染复核。"
                        : StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_RECEPTION_GUIDANCE_CONTENT))
                .withStoryStage(STAGE_ID);
        addMedicalLocation(seed);
        GuidanceManager.ensureActiveFromStoryEvent(player.getUUID(), seed,
                "dreamingfishcore:afterdream/event/enter_reception", JIANGWAN_NPC_ID,
                "江晚", StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_RECEPTION_GUIDANCE_QUOTE));
    }

    private static void addMedicalLocation(GuidanceSeed seed) {
        TaskLocationManager.getLocation(activeMedicalLocationId()).ifPresent(location -> {
            BlockPos min = location.getMin();
            BlockPos max = location.getMax();
            seed.withLocation(location.getName(), location.getDimension(),
                    midpoint(min.getX(), max.getX()), midpoint(min.getY(), max.getY()),
                    midpoint(min.getZ(), max.getZ()));
        });
    }

    private static void createMaskGuidance(ServerPlayer player) {
        GuidanceSeed seed = new GuidanceSeed(
                AfterdreamStory.MASK_GUIDANCE_ID,
                StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_MASK_GUIDANCE_TITLE),
                com.hhy.dreamingfishcore.gameplay.hospital_system.HospitalStory.isOpen()
                        ? "到医院重新与江晚交谈，领取防护面具。"
                        : StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_MASK_GUIDANCE_CONTENT))
                .withStoryStage(STAGE_ID).withStoryLine(STAGE_ID + "/mask");
        TaskLocationManager.getLocation(activeMedicalLocationId()).ifPresent(location -> {
            BlockPos min = location.getMin();
            BlockPos max = location.getMax();
            seed.withLocation(location.getName(), location.getDimension(),
                    midpoint(min.getX(), max.getX()), midpoint(min.getY(), max.getY()),
                    midpoint(min.getZ(), max.getZ()));
        });
        GuidanceManager.ensureActiveFromStoryEvent(player.getUUID(), seed,
                "dreamingfishcore:afterdream/event/mask_available", JIANGWAN_NPC_ID,
                "江晚", StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_MASK_GUIDANCE_QUOTE));
    }

    /**
     * 确保第二阶段开场时丧尸挖掘仍处于关闭状态，并只启动一次两游戏日倒计时。
     * 远程配置中已经有 afterdream.digging=false 时不会重复写文件；本地没有该
     * 覆盖项时则由阶段脚本补上，避免默认值让丧尸提前破坏建筑。
     */
    private static boolean ensureZombieDiggingCountdown(MinecraftServer server) {
        if (server == null) {
            return false;
        }
        AfterdreamWorldProgress progress = worldProgress();
        if (progress.isZombieDiggingEnabled()) {
            return true;
        }

        if (progress.getZombieDiggingCountdownStartedAtGameTime() < 0L) {
            try {
                ZombieSpeciesConfig.ResolvedSettings settings =
                        ZombieSpeciesConfig.current().resolveForStage(STAGE_ID);
                if (settings.digging()) {
                    ZombieSpeciesConfig.setAbilityForStage(
                            STAGE_ID, ZombieSpeciesConfig.Ability.DIGGING, false);
                }
            } catch (RuntimeException exception) {
                DreamingFishCore.LOGGER.error(
                        "余梦期无法关闭丧尸挖掘能力，倒计时暂不启动", exception);
                return false;
            }

            long start = currentGameTime(server);
            if (progress.startZombieDiggingCountdown(start)) {
                StoryManager.markDirty();
                DreamingFishCore.LOGGER.info(
                        "余梦期丧尸记忆公告倒计时启动：起点={}，发布时间={}（gameTime）",
                        start, progress.getZombieDiggingAvailableAtGameTime());
            }
        }
        return true;
    }

    /** 丧尸能力开启后发布一次全服公告；公告和能力事实都可跨重启恢复。 */
    private static void ensureZombieDiggingNotice() {
        AfterdreamWorldProgress progress = worldProgress();
        if (progress.isZombieDiggingAnnouncementSent()) {
            return;
        }
        NoticeData notice = findOrCreateNotice(
                ZOMBIE_MEMORY_NOTICE_KEY,
                StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_ZOMBIE_MEMORY_NOTICE_TITLE),
                StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_ZOMBIE_MEMORY_NOTICE_CONTENT));
        if (notice == null) {
            return;
        }
        try {
            NoticeDeliveryService.publishToAllOnlinePlayers(notice);
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.error("发布丧尸记忆公告失败，将在下一次服务器 tick 重试", exception);
            return;
        }
        if (progress.markZombieDiggingAnnouncementSent()) {
            setWorldFlagIfNeeded(ZOMBIE_MEMORY_NOTICE_PUBLISHED_FLAG);
            StoryManager.markDirty();
        }
    }

    private static long currentGameTime(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            return 0L;
        }
        return Math.max(0L, server.overworld().getGameTime());
    }

    private static void ensurePublicNotice() {
        NoticeData notice = findOrCreateNotice(
                PUBLIC_NOTICE_KEY,
                StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_PUBLIC_NOTICE_TITLE),
                StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_PUBLIC_NOTICE_CONTENT));
        if (notice != null) {
            setWorldFlagIfNeeded(PUBLIC_NOTICE_PUBLISHED_FLAG);
            NoticeDeliveryService.publishToAllOnlinePlayers(notice);
        }
    }

    /**
     * 第二阶段入口的恢复规则公告。它只由世界旗标去重，不绑定个人任务，
     * 因此更新模组后已经处于第二阶段的世界会在加载时立即收到一次。
     */
    private static void ensureRecoveryRulesNotice() {
        boolean alreadyPublished;
        try {
            alreadyPublished = StoryManager.hasWorldFlag(
                    RECOVERY_RULES_NOTICE_PUBLISHED_FLAG);
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.warn(
                    "无法读取恢复规则公告旗标，将在下一次剧情入口重试", exception);
            return;
        }

        NoticeData notice = findOrCreateNotice(
                RECOVERY_RULES_NOTICE_KEY,
                StoryTextCatalog.text(
                        StoryTextCatalog.AFTERDREAM_RECOVERY_RULES_NOTICE_TITLE),
                StoryTextCatalog.text(
                        StoryTextCatalog.AFTERDREAM_RECOVERY_RULES_NOTICE_CONTENT));
        if (notice == null || alreadyPublished) {
            return;
        }

        try {
            NoticeDeliveryService.publishToAllOnlinePlayers(notice);
            StoryManager.setWorldFlag(RECOVERY_RULES_NOTICE_PUBLISHED_FLAG, true);
            StoryManager.markDirty();
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.error(
                    "发布恢复规则公告失败，将在下一次剧情入口重试", exception);
        }
    }

    private static void ensureMaskNotice() {
        if (worldProgress().isMaskAnnouncementSent()) {
            return;
        }
        NoticeData notice = findOrCreateNotice(
                MASK_NOTICE_KEY,
                StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_MASK_NOTICE_TITLE),
                StoryTextCatalog.text(StoryTextCatalog.AFTERDREAM_MASK_NOTICE_CONTENT));
        if (notice != null) {
            worldProgress().markMaskAnnouncementSent();
            setWorldFlagIfNeeded(MASK_NOTICE_PUBLISHED_FLAG);
            StoryManager.markDirty();
            NoticeDeliveryService.publishToAllOnlinePlayers(notice);
        }
    }

    private static NoticeData findOrCreateNotice(String key, String title, String content) {
        NoticeData existing = NoticeManager.getNoticeByKey(key);
        if (existing != null) {
            NoticeManager.updateStoryNoticeText(existing, title, content);
            return existing;
        }
        int id = NoticeManager.getMaxNoticeId() + 1;
        NoticeData notice = new NoticeData(id, title, content, System.currentTimeMillis(),
                NoticeCategory.GAME, STAGE_ID, STORY_DATE, key);
        return NoticeManager.addNotice(notice) ? notice : null;
    }

    private static void setWorldFlagIfNeeded(String flag) {
        try {
            if (!StoryManager.hasWorldFlag(flag)) {
                StoryManager.setWorldFlag(flag, true);
            }
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.warn("无法记录余梦期世界旗标：{}", flag, exception);
        }
    }

    private static void recordTask(ServerPlayer player, String taskId) {
        try {
            if (StoryManager.recordPlayerTaskProgress(
                    taskId, player.getScoreboardName(), player.getUUID())) {
                TaskDataManager.syncFullTaskData(player);
            }
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.warn("记录玩家 {} 的余梦期任务 {} 失败",
                    player.getScoreboardName(), taskId, exception);
        }
    }

    private static void syncPlayer(ServerPlayer player) {
        try {
            var progress = StoryManager.findAfterdreamProgress(player.getUUID());
            if (progress != null && isCurrentStage()) rebuildPlayerProjections(player, progress);
            GuidanceManager.syncToClient(player);
            TaskDataManager.syncFullTaskData(player);
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.warn("同步玩家 {} 的余梦期状态失败",
                    player.getScoreboardName(), exception);
        }
    }

    private static Optional<List<String>> dialogue(String key) {
        List<String> lines = StoryTextCatalog.dialogue(key);
        return lines.isEmpty() ? Optional.empty() : Optional.of(lines);
    }

    private static AfterdreamPlayerProgress progressFor(UUID playerId) {
        return StoryManager.getOrCreateAfterdreamProgress(playerId);
    }

    private static boolean isCurrentStage() {
        return STAGE_ID.equals(StoryManager.getCurrentStageIdOrDefault());
    }

    private static long currentActiveTick() {
        try {
            return Math.max(0L, StoryManager.getSnapshot().activeTicks());
        } catch (RuntimeException exception) {
            return 0L;
        }
    }

    private static boolean canWrite() {
        return StoryManager.areWritesEnabled();
    }

    private static boolean isMaskEligibleDialogueSession(ServerPlayer player) {
        return player != null && MASK_ELIGIBLE_DIALOGUE_SESSIONS.contains(player.getUUID());
    }

    private static AfterdreamWorldProgress worldProgress() {
        return StoryManager.getAfterdreamWorldProgress();
    }

    private static int midpoint(int first, int second) {
        return (int) (((long) first + second) / 2L);
    }

}
