package com.hhy.dreamingfishcore.gameplay.hospital_system;

import com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamStory;
import com.hhy.dreamingfishcore.gameplay.clue_system.ClueGuaranteeService;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceManager;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceSeed;
import com.hhy.dreamingfishcore.gameplay.npc_system.StoryNpcContentPolicy;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.story_system.*;
import com.hhy.dreamingfishcore.gameplay.story_system.runtime.StoryTextCatalog;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationManager;
import com.hhy.dreamingfishcore.gameplay.task_system.TaskDataManager;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import com.hhy.dreamingfishcore.server.notice_system.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 余梦期医院小章：读公告、固定人数开建设、人工验收、一次正式复查。 */
public final class HospitalStory {
    public static final String READ_TASK = "dreamingfishcore:hospital/read_template";
    public static final String BUILD_TASK = "dreamingfishcore:hospital/build";
    public static final String REVIEW_TASK = "dreamingfishcore:hospital/review";
    public static final String INFO_NOTICE = "hospital.template_information";
    private static final String BUILD_NOTICE = "hospital.construction";
    private static final String OPEN_NOTICE = "hospital.opened";
    public static final String TEMPLATE_CONTENT = "各位居民：\n\n重生不是让时间倒流。死亡后，梦屿的重生节点会读取个人生命模板，重新构建身体。模板保存身体结构、神经连接、基础记忆、当前感染状态和重建定位信息。因此重生通常不会清除感染。\n\n模板重建余量代表系统仍能为你提供的稳定重建储备，上限为 100。它不是已经永久丢失的人格信息；医疗维护可以恢复部分余量。感染结构需要额外校正，因此感染者的消耗通常更高。\n\n标准重建恢复身体，遗落装备需要自行或由同伴取回。选择保留物品栏会额外消耗余量。余量大于零时，标准重建允许将余量扣至零并完成当前这一次；余量已耗尽后，下一次死亡将无法自行重建。保留物品需要满足对应的额外条件，请查看死亡界面的实际消耗。\n\n临时医疗接待点会提供每日模板维护。向医疗工作人员了解当日所需物资，余量已满时无需提交。\n\n逐光会计划在阿拜多斯旁建设医院，提供正式接待、观察与模板维护场所。请阅读这份说明，了解自己的模板，也了解这座医院将保护什么。\n\n逐光会医疗组 · 江晚";
    private record ServiceResponse(int npcId, List<String> dialogues) { }
    // 办理结果只属于当前玩家与当前 NPC 的会话，不写入剧情事实或其他 NPC 的对白。
    private static final java.util.Map<UUID, ServiceResponse> SERVICE_RESPONSES = new java.util.HashMap<>();
    public static java.util.Optional<List<String>> serviceResponse(ServerPlayer player, int npcId) {
        return java.util.Optional.ofNullable(SERVICE_RESPONSES.get(player.getUUID()))
                .filter(response -> response.npcId() == npcId).map(ServiceResponse::dialogues);
    }
    public static void setServiceResponse(ServerPlayer player, int npcId, String response) {
        SERVICE_RESPONSES.put(player.getUUID(), new ServiceResponse(npcId, List.of(response)));
    }
    public static boolean clearResponse(UUID playerId) { return SERVICE_RESPONSES.remove(playerId) != null; }
    public static void clearTransient() { SERVICE_RESPONSES.clear(); }
    private HospitalStory() { }

    private static String text(String key, String fallback) {
        return StoryTextCatalog.textOrDefault("hospital." + key, fallback);
    }

    public static List<StoryTaskData> createTasks() {
        return List.of(task(READ_TASK, 2201, "下一次醒来", "阅读终端剧情广播中的《生命模板与重建余量说明》。", StoryTaskData.Scope.PERSONAL),
                task(BUILD_TASK, 2202, "让医院有个地址", "在阿拜多斯旁共同建设医院，准备接待区、治疗观察区和模板维护房间。建成后等待现场验收。", StoryTaskData.Scope.WORLD),
                task(REVIEW_TASK, 2203, "第一次正式复查", "医院开诊后，前往医院与江晚进行正式复查。检查完成不等于感染已治愈。", StoryTaskData.Scope.PERSONAL));
    }

    private static StoryTaskData task(String id, int number, String name, String content, StoryTaskData.Scope scope) {
        StoryTaskData task = new StoryTaskData(id, number, text("task." + number + ".name", name),
                text("task." + number + ".content", content), 0, 0);
        task.setScope(scope);
        task.setPublishedByDefault(false);
        task.setGuidanceDefinitionIds(List.of(id));
        return task;
    }

    public static boolean isTask(String id) { return Set.of(READ_TASK, BUILD_TASK, REVIEW_TASK).contains(id); }
    public static boolean isCurrent() { return AfterdreamStory.STAGE_ID.equals(StoryManager.getCurrentStageIdOrDefault()); }
    public static boolean isStarted() { return StoryManager.isLoaded() && StoryManager.getHospitalProgress().isStarted(); }
    public static boolean isOpen() {
        if (!isStarted()) return false;
        StoryTaskData task = StoryManager.getTask(BUILD_TASK);
        return task != null && task.isCompleted() && !task.isFailed();
    }

    public static boolean isVisible(String taskId, UUID playerId) {
        if (!isStarted()) return false;
        return READ_TASK.equals(taskId) || REVIEW_TASK.equals(taskId) && isOpen();
    }

    public static String start() {
        if (!StoryManager.areWritesEnabled() || !isCurrent()) {
            throw new IllegalStateException("只能在可写的余梦期发布医院剧情");
        }
        reconcile();
        refreshOnline();
        return "医院剧情已发布：居民可阅读重生说明；每日模板维护可在临时接待点办理。";
    }

    public static String setSite(String locationReference) {
        if (!StoryManager.areWritesEnabled() || !isCurrent()) throw new IllegalStateException("当前不能设置医院地点");
        var location = TaskLocationManager.resolveLocationReference(locationReference)
                .orElseThrow(() -> new IllegalArgumentException("任务地点不存在，请填写已注册的地点名称或 ID：" + locationReference));
        if (!location.isEnabled()) throw new IllegalArgumentException("任务地点已停用：" + location.getName());
        if (StoryManager.getHospitalProgress().setLocationId(location.getId())) StoryManager.markDirty();
        saveStory();
        refreshOnline();
        return "医院地点已设置为 " + location.getName() + "（" + location.getId() + "）；请将江晚与医疗工作人员放置在该区域。";
    }

    public static String complete() {
        validateResolution(BUILD_TASK, StoryTaskOutcome.SUCCEEDED);
        boolean changed = StoryManager.resolveTask(BUILD_TASK, StoryTaskOutcome.SUCCEEDED, List.of());
        saveStory();
        reconcile();
        refreshOnline();
        return changed ? "医院验收完成，已开放正式复查；章节保持余梦期。" : "医院已经开诊，未重复结算。";
    }

    public static void validateResolution(String taskId, StoryTaskOutcome outcome) {
        if (!BUILD_TASK.equals(taskId) || outcome != StoryTaskOutcome.SUCCEEDED) return;
        if (!isStarted()) {
            throw new IllegalStateException("医院剧情尚未启动；请用 /dreamingfish story hospital status 检查状态。医院剧情在余梦期自动启动");
        }
        if (StoryManager.getHospitalProgress().getLocationId().isBlank()) {
            throw new IllegalStateException("尚未绑定医院地点；创建区域后，还需执行 /dreamingfish story hospital site <地点名称或ID>");
        }
        TaskLocationManager.getLocation(StoryManager.getHospitalProgress().getLocationId()).filter(value -> value.isEnabled())
                .orElseThrow(() -> new IllegalStateException("医院地点已失效，请恢复地点后验收"));
    }

    /** 余梦期服务就绪或进入本阶段时自动发布；重复核对不重置进度，也不推进世界章节。 */
    public static void reconcile() {
        if (!StoryManager.areWritesEnabled() || !isCurrent()) return;
        if (StoryManager.getHospitalProgress().start()) StoryManager.markDirty();
        // 旧存档同样从这里接入；先保存发布事实，保存失败时留待下次核对重试。
        saveStory();
        publish(INFO_NOTICE, text("notice.template.title", "【逐光会】生命模板与重建余量说明"),
                text("notice.template.content", TEMPLATE_CONTENT));
        if (StoryManager.getPersonalCompletionCount(READ_TASK) >= HospitalConfig.get().requiredPlayers()) {
            StoryManager.activateTask(BUILD_TASK);
            saveStory();
        }
        StoryTaskData build = StoryManager.getTask(BUILD_TASK);
        if (build != null && build.isTaskState()) {
            publish(BUILD_NOTICE, text("notice.build.title", "【逐光会】阿拜多斯医院建设计划"),
                    text("notice.build.content", "临时接待点只能承担基础检查与紧急处置。逐光会将在阿拜多斯旁建设医院，请愿意参与的居民共同完成接待区、治疗观察区和模板维护房间。\n\n建筑形式由参与者共同决定。建成后进行现场验收，再安排江晚与医疗工作人员入驻。医院向所有居民开放，加入逐光会不是就医条件。建设期间，每日模板维护继续在临时接待点办理。"));
        }
        if (isOpen()) {
            publish(OPEN_NOTICE, text("notice.open.title", "【逐光会】阿拜多斯医院正式开诊"),
                    text("notice.open.content", "阿拜多斯旁的医院已完成验收，正式接收居民复查。请先阅读生命模板说明，再到医院向江晚申请一次正式复查。\n\n医疗工作人员继续办理每日模板维护。二级感染者同样可以完成检查并使用公共维护服务；完成检查不代表感染已经治愈。感谢每一位参与建设与运输的居民。"));
        }
    }

    private static void publish(String key, String title, String content) {
        NoticeData notice = NoticeManager.getNoticeByKey(key);
        if (notice == null) {
            notice = new NoticeData(NoticeManager.getMaxNoticeId() + 1, title, content, System.currentTimeMillis(),
                    NoticeCategory.GAME, AfterdreamStory.STAGE_ID, "余梦期 · 医院建设", key);
            if (!NoticeManager.addNotice(notice)) throw new IllegalStateException("医院公告保存失败：" + key);
        }
        NoticeDeliveryService.publishToAllOnlinePlayers(notice);
    }

    public static void onNoticeRead(ServerPlayer player, String noticeKey) {
        if (!INFO_NOTICE.equals(noticeKey) || !canParticipate(player)) return;
        if (NoticeManager.getNoticeByKey(INFO_NOTICE) == null) return;
        StoryManager.recordPlayerTaskProgress(READ_TASK, player.getScoreboardName(), player.getUUID());
        saveStory();
        reconcile();
        refreshOnline();
    }

    public static void onAuthenticated(ServerPlayer player) {
        DailyTemplateSupportService.recoverPayment(player);
        if (canParticipate(player)) {
            NoticeData notice = NoticeManager.getNoticeByKey(INFO_NOTICE);
            if (notice != null && PlayerNoticeDataManager.hasReadNotice(player.getUUID(), notice.getNoticeId())
                    && !StoryManager.isPlayerFinishedTask(READ_TASK, player.getUUID())) {
                StoryManager.recordPlayerTaskProgress(READ_TASK, player.getScoreboardName(), player.getUUID());
                reconcile();
            }
            grantReviewClueIfFinished(player);
            syncPlayer(player);
        }
    }

    public static boolean canParticipate(ServerPlayer player) {
        return player != null && StoryManager.areWritesEnabled() && isStarted() && isCurrent()
                && AuthSessionGuard.isAuthenticated(player) && player.isAlive() && !player.isSpectator();
    }

    public static boolean canReview(ServerPlayer player, int npcId) {
        return npcId == StoryNpcContentPolicy.JIANGWAN_ID && canParticipate(player) && isOpen()
                && StoryManager.isPlayerFinishedTask(READ_TASK, player.getUUID());
    }

    public static void review(ServerPlayer player) {
        if (!canReview(player, StoryNpcContentPolicy.JIANGWAN_ID)) throw new IllegalStateException("请先阅读重生说明，并等待医院开诊");
        var location = TaskLocationManager.getLocation(StoryManager.getHospitalProgress().getLocationId());
        if (location.isEmpty() || !location.get().contains(player.level().dimension(), player.blockPosition())) {
            throw new IllegalStateException("请进入医院区域后进行正式复查");
        }
        var data = PlayerAttributesDataManager.findStoredPlayerAttributesData(player.getUUID());
        if (data == null) throw new IllegalStateException("个人属性档案尚未就绪");
        StoryManager.recordPlayerTaskProgress(REVIEW_TASK, player.getScoreboardName(), player.getUUID());
        saveStory();
        syncPlayer(player);
        // 复查话术按感染身份分支：只有已经跨过突变的身份才需要重构疗程，
        // 不稳定感染者仍走成本较低的早期逆转（基因复苏试剂）。
        String result = switch (data.getInfectionIdentity()) {
            case RELAPSE -> "当前处于传播复发，异常因子重新活跃。已记录本次复查；"
                    + "请先让感染系统重新受控，再决定是否进入重构疗程。这不表示已经治愈。";
            case STABLE -> "当前为稳定感染，基因复苏试剂不适用。已记录本次复查，"
                    + "后续可通过重构疗程恢复；这不表示已经治愈。";
            case UNSTABLE -> "当前为不稳定感染，请按原医疗流程完成早期逆转；本次检查已完成。";
            case SURVIVOR -> data.getCurrentInfection() > 0
                    ? "当前仍有感染指标，请按原医疗流程完成早期治疗；本次检查已完成。"
                    : "目前未发现感染指标，本次检查已完成。";
        };
        setServiceResponse(player, StoryNpcContentPolicy.JIANGWAN_ID,
                result + "\n\n模板重建余量：" + String.format(java.util.Locale.ROOT, "%.1f / 100", data.getRespawnPoint()));
        // 正式复查属于医院观察区的事实来源，由江晚交出一份观察记录（保底线索，幂等）。
        ClueGuaranteeService.grant(player, ClueGuaranteeService.CLUE_OBSERVATION_LOG);
    }

    /** 已经完成过正式复查的玩家在登录时补发观察区值班记录。 */
    private static void grantReviewClueIfFinished(ServerPlayer player) {
        if (!StoryManager.isPlayerFinishedTask(REVIEW_TASK, player.getUUID())) return;
        ClueGuaranteeService.grant(player, ClueGuaranteeService.CLUE_OBSERVATION_LOG);
    }

    public static void syncPlayer(ServerPlayer player) {
        if (!canParticipate(player)) return;
        boolean read = StoryManager.isPlayerFinishedTask(READ_TASK, player.getUUID());
        project(player, READ_TASK, !read, "下一次醒来", "打开终端剧情广播，阅读《生命模板与重建余量说明》。", "knowledge");
        StoryTaskData build = StoryManager.getTask(BUILD_TASK);
        project(player, BUILD_TASK, build != null && build.isTaskState() && !build.isCompleted(),
                "让医院有个地址", "在阿拜多斯旁建设医院：接待区、治疗观察区、模板维护房间。建成后进行现场验收。", "construction");
        project(player, REVIEW_TASK, read && isOpen() && !StoryManager.isPlayerFinishedTask(REVIEW_TASK, player.getUUID()),
                "第一次正式复查", "到医院与江晚交互，选择“正式复查”。医疗工作人员提供每日模板维护。", "knowledge");
        GuidanceManager.syncToClient(player);
        TaskDataManager.syncFullTaskData(player);
    }

    private static void project(ServerPlayer player, String id, boolean active, String title, String content, String line) {
        if (!active) { GuidanceManager.resolve(player.getUUID(), id); return; }
        GuidanceSeed seed = new GuidanceSeed(id, title, content).withStoryStage(AfterdreamStory.STAGE_ID)
                .withStoryLine("dreamingfishcore:hospital/" + line);
        if (!READ_TASK.equals(id)) TaskLocationManager.getLocation(StoryManager.getHospitalProgress().getLocationId())
                .ifPresent(location -> seed.withLocation(location.getName(), location.getDimension(),
                        location.getMin().getX(), location.getMin().getY(), location.getMin().getZ()));
        GuidanceManager.ensureActiveFromStoryEvent(player.getUUID(), seed, id, "逐光会医疗组", content);
    }

    public static void refreshOnline() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (canParticipate(player)) {
                if (isOpen()) AfterdreamStory.refreshGuidance(player);
                syncPlayer(player);
            }
        }
    }

    public static String status() {
        return "医院剧情：" + (isStarted() ? "已发布" : "未发布") + "；正式开诊：" + isOpen()
                + "\n重生说明实际阅读人数：" + StoryManager.getPersonalCompletionCount(READ_TASK) + "/" + HospitalConfig.get().requiredPlayers()
                + "\n医院地点：" + (StoryManager.getHospitalProgress().getLocationId().isBlank()
                        ? "未绑定（请执行 hospital site <地点名称或ID>）" : StoryManager.getHospitalProgress().getLocationId())
                + "\n每日模板维护：" + HospitalConfig.get().dailyItemCount() + " × " + HospitalConfig.get().dailyItemId()
                + "，恢复 " + HospitalConfig.get().dailyRestorePoints() + " 点；每个主世界游戏日一次。";
    }

    private static void saveStory() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && !StoryManager.saveIfDirty(server)) throw new IllegalStateException("剧情状态尚未保存成功，请重试同一操作");
    }
}
