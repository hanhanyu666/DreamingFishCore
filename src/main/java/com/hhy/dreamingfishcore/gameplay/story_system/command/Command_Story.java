package com.hhy.dreamingfishcore.gameplay.story_system.command;

import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.hhy.dreamingfishcore.gameplay.story_system.ContentPackManager;
import com.hhy.dreamingfishcore.gameplay.story_system.WorldHistoryLog;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryOperationsCatalog;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryTaskOutcome;
import com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamStory;
import com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamWorldProgress;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Comparator;

/**
 * 故事系统的服主管理命令。
 *
 * <p>Brigadier 使用树状方式注册命令：每个 {@code then} 都是在上一级后面增加一个分支。
 * 提供状态查看、手动切章、前情发布、世界任务验收、内容重载和历史查询。</p>
 */
public final class Command_Story {
    /** 命令类只提供静态方法，不需要实例。 */
    private Command_Story() {
    }

    /**
     * 注册：
     * <pre>
     * /dreamingfish story status
     * /dreamingfish story stage set &lt;stageId&gt;
     * /dreamingfish story content validate
     * /dreamingfish story content reload &lt;contentId&gt;
     * /dreamingfish story history [limit]
     * </pre>
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("dreamingfish")
                .requires(source -> source.hasPermission(2));
        register(root);
        dispatcher.register(root);
    }

    /** 将故事子树挂到统一的 /dreamingfish 根节点。 */
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("story")
                .then(com.hhy.dreamingfishcore.gameplay.hospital_system.Command_Hospital.branch())
                .then(Commands.literal("status")
                        .executes(Command_Story::showStatus))
                .then(Commands.literal("recap").requires(source -> source.hasPermission(3))
                        .then(Commands.literal("list").executes(context -> operation(context, StoryManager::describeRecaps)))
                        .then(Commands.literal("preview")
                                .then(Commands.argument("id", StringArgumentType.greedyString()).executes(context ->
                                        operation(context, () -> {
                                            String id = StringArgumentType.getString(context, "id");
                                            var draft = StoryOperationsCatalog.read().recaps().stream()
                                                    .filter(value -> value.id().equals(id)).findFirst()
                                                    .orElseThrow(() -> new IllegalArgumentException("前情草稿不存在：" + id));
                                            return draft.title() + "\n接入点：" + draft.checkpointId() + "\n" + draft.content();
                                        })) ))
                        .then(Commands.literal("publish")
                                .then(Commands.argument("id", StringArgumentType.greedyString()).executes(context ->
                                        operation(context, () -> StoryManager.publishRecap(
                                                StringArgumentType.getString(context, "id"), context.getSource().getTextName())
                                                ? "前情总结已发布；目标阶段开放后向落后玩家发放，离线及后续玩家上线时补发。"
                                                : "该总结已经发布；保留原发布正文并重试补发。")))))
                .then(Commands.literal("worldtask").requires(source -> source.hasPermission(3))
                        .then(Commands.literal("list").executes(context -> operation(context, StoryManager::describeWorldTaskGates)))
                        .then(Commands.literal("unlock")
                                .then(Commands.argument("id", StringArgumentType.greedyString()).executes(context -> operation(context,
                                        () -> StoryManager.activateTask(StringArgumentType.getString(context, "id"))
                                                ? "世界任务已手动解锁；章节保持不变。" : "世界任务已经解锁。"))))
                        .then(Commands.literal("succeed")
                                .then(Commands.argument("id", StringArgumentType.greedyString()).executes(context -> settleWorldTask(context, StoryTaskOutcome.SUCCEEDED))))
                        .then(Commands.literal("fail")
                                .then(Commands.argument("id", StringArgumentType.greedyString()).executes(context -> settleWorldTask(context, StoryTaskOutcome.FAILED)))))
                .then(Commands.literal("stage")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.literal("set")
                                .then(Commands.argument("stageId", StringArgumentType.greedyString())
                                        .suggests((context, builder) ->
                                                SharedSuggestionProvider.suggest(
                                                        StoryManager.getAllStagesById().keySet(), builder))
                                        .executes(Command_Story::setStage))))
                .then(Commands.literal("content")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.literal("validate")
                                .executes(Command_Story::validateContent))
                        .then(Commands.literal("reload")
                                .then(Commands.argument("contentId", StringArgumentType.greedyString())
                                        .executes(Command_Story::reloadContent))))
                .then(Commands.literal("history")
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, 50))
                                .executes(Command_Story::showHistory))
                        .executes(context -> showHistory(context, 10))));
    }

    private static int operation(CommandContext<CommandSourceStack> context, java.util.function.Supplier<String> action) {
        try {
            String message = action.get();
            context.getSource().sendSuccess(() -> Component.literal(message), false);
            return 1;
        } catch (RuntimeException exception) {
            context.getSource().sendFailure(Component.literal(String.valueOf(exception.getMessage())));
            return 0;
        }
    }

    private static int settleWorldTask(CommandContext<CommandSourceStack> context, StoryTaskOutcome outcome) {
        return operation(context, () -> {
            String taskId = StringArgumentType.getString(context, "id");
            var task = StoryManager.getTask(taskId);
            if (task == null) {
                throw new IllegalArgumentException("世界任务不存在：" + taskId);
            }
            boolean changed = task.getLocationId().isBlank()
                    ? StoryManager.resolveTask(taskId, outcome, java.util.List.of())
                    : StoryManager.resolveTaskAtConfiguredLocation(context.getSource().getServer(), taskId, outcome);
            if (!StoryManager.saveIfDirty(context.getSource().getServer())) {
                throw new IllegalStateException("任务结果在内存中，存盘失败；请检查日志并重试保存");
            }
            if (com.hhy.dreamingfishcore.gameplay.hospital_system.HospitalStory.BUILD_TASK.equals(taskId)) {
                com.hhy.dreamingfishcore.gameplay.hospital_system.HospitalStory.reconcile();
                com.hhy.dreamingfishcore.gameplay.hospital_system.HospitalStory.refreshOnline();
            }
            return changed ? "已记录世界任务结果 " + outcome + "；章节保持不变。" : "任务已结算，未重复发放结果。";
        });
    }

    /** 读取命令参数，调用 StoryManager 切换阶段，再向执行者反馈结果。 */
    private static int setStage(CommandContext<CommandSourceStack> context) {
        String stageId = StringArgumentType.getString(context, "stageId");
        try {
            if (!StoryManager.changeStage(stageId, context.getSource().getTextName())) {
                context.getSource().sendFailure(Component.literal("当前已经处于故事阶段：" + stageId));
                return 0;
            }
            StoryManager.Snapshot snapshot = StoryManager.getSnapshot();
            context.getSource().sendSuccess(
                    () -> Component.literal("已发布故事阶段 "
                            + snapshot.currentStageNumber() + " / "
                            + snapshot.currentStageId() + " / "
                            + snapshot.currentStageName()),
                    true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    /** 只检查候选故事定义，不改变当前运行内容。 */
    private static int validateContent(CommandContext<CommandSourceStack> context) {
        try {
            StoryManager.DefinitionSummary summary = ContentPackManager.validate();
            context.getSource().sendSuccess(
                    () -> Component.literal("故事内容校验通过：阶段 " + summary.stageCount()
                            + " 个，任务 " + summary.taskCount() + " 个"),
                    false);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            context.getSource().sendFailure(Component.literal("故事内容校验失败：" + exception.getMessage()));
            return 0;
        }
    }

    /** 校验通过后安装内容定义，并把本次发布写入世界历史。 */
    private static int reloadContent(CommandContext<CommandSourceStack> context) {
        String contentId = StringArgumentType.getString(context, "contentId");
        try {
            ContentPackManager.ReloadResult result = ContentPackManager.reload(
                    contentId,
                    context.getSource().getTextName());
            StoryManager.DefinitionSummary summary = result.definitionSummary();
            context.getSource().sendSuccess(
                    () -> Component.literal("故事内容已热重载：" + result.contentId()
                            + "，代数 " + summary.generation()
                            + "，阶段 " + summary.stageCount()
                            + " 个，任务 " + summary.taskCount() + " 个"),
                    true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            context.getSource().sendFailure(Component.literal("故事内容热重载失败：" + exception.getMessage()));
            return 0;
        }
    }

    /** 显示最近的只追加世界历史，默认显示 10 条。 */
    private static int showHistory(CommandContext<CommandSourceStack> context) {
        return showHistory(context, IntegerArgumentType.getInteger(context, "limit"));
    }

    private static int showHistory(CommandContext<CommandSourceStack> context, int limit) {
        WorldHistoryLog.Status status = WorldHistoryLog.getStatus();
        StringBuilder message = new StringBuilder("世界历史日志：总事件 ")
                .append(status.eventCount())
                .append("，写入状态=")
                .append(status.writesEnabled() ? "正常" : "只读保护");
        for (WorldHistoryLog.HistoryEvent event : WorldHistoryLog.getRecentEvents(limit)) {
            message.append("\n#").append(event.getSequence())
                    .append(" [").append(event.getType()).append("] ")
                    .append(event.getSubjectId())
                    .append(" / 执行者=").append(event.getActor())
                    .append(" / 活动时间=").append(event.getActiveTick());
        }
        context.getSource().sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }

    /** 把 Snapshot 中的服务器权威状态格式化成人类可读文本。 */
    private static int showStatus(CommandContext<CommandSourceStack> context) {
        StoryManager.Snapshot snapshot = StoryManager.getSnapshot();
        String flags = snapshot.worldFlags().stream()
                .sorted(Comparator.naturalOrder())
                .reduce((left, right) -> left + ", " + right)
                .orElse("无");
        String ending = snapshot.endingId().isEmpty() ? "未进入终章" : snapshot.endingId();
        String operationSource = snapshot.operationRoundSourceId().isEmpty()
                ? "无"
                : snapshot.operationRoundSourceId();
        String operationContent = snapshot.operationRoundContentId().isEmpty()
                ? "未发布"
                : snapshot.operationRoundContentId();
        ContentPackManager.Status contentStatus = ContentPackManager.getStatus();
        WorldHistoryLog.Status historyStatus = WorldHistoryLog.getStatus();

        String message = "世界故事状态"
                + "\n- 数据版本: " + snapshot.schemaVersion()
                + "\n- 当前阶段: " + snapshot.currentStageNumber()
                + " / " + snapshot.currentStageId()
                + " / " + snapshot.currentStageName()
                + "\n- 阶段推进方式: 服主手动"
                + "\n- 世界任务门槛: 使用 /dreamingfish story worldtask list 查看"
                + "\n- 在线活动时间: " + formatTicks(snapshot.activeTicks())
                + "\n- 当前阶段持续: "
                + formatTicks(snapshot.activeTicks() - snapshot.stageEnteredAtActiveTick())
                + "\n- 世界旗标: " + flags
                + "\n- 运营轮次: " + snapshot.operationRoundNumber()
                + " / " + snapshot.operationRoundStatus()
                + " / 来源=" + operationSource
                + " / 内容=" + operationContent
                + "\n- 终章状态: " + ending
                + "\n- 余梦期丧尸记忆事件: " + describeZombieMemoryEvent(context, snapshot)
                + "\n- 内容包: " + contentStatus.lastSuccessfulContentId()
                + "（定义代数 " + StoryManager.getDefinitionGeneration() + "）"
                + "\n- 历史日志: " + historyStatus.eventCount() + " 条，"
                + (historyStatus.writesEnabled() ? "可追加" : "只读保护")
                + "\n- 写入状态: " + (snapshot.writesEnabled() ? "正常" : "只读保护");

        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static String describeZombieMemoryEvent(
            CommandContext<CommandSourceStack> context, StoryManager.Snapshot snapshot) {
        if (!AfterdreamStory.STAGE_ID.equals(snapshot.currentStageId())) {
            return "当前阶段不适用";
        }
        AfterdreamWorldProgress progress = StoryManager.getAfterdreamWorldProgress();
        if (progress.isZombieDiggingAnnouncementSent()) {
            return "公告已发布，挖掘已开启";
        }
        long deadline = progress.getZombieDiggingAvailableAtGameTime();
        if (deadline < 0L) {
            return "等待服务器 tick 启动倒计时";
        }
        long now = context.getSource().getServer().overworld().getGameTime();
        return "倒计时中，剩余 " + formatTicks(Math.max(0L, deadline - now));
    }

    /** Minecraft 每秒通常运行 20 tick，这里把 tick 转换为便于服主阅读的时间。 */
    private static String formatTicks(long ticks) {
        long totalSeconds = Math.max(0L, ticks / 20L);
        long days = totalSeconds / 86_400L;
        long hours = totalSeconds % 86_400L / 3_600L;
        long minutes = totalSeconds % 3_600L / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%d天 %02d:%02d:%02d (%d ticks)", days, hours, minutes, seconds, ticks);
    }
}
