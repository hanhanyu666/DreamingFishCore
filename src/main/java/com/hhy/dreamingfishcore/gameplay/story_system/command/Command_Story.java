package com.hhy.dreamingfishcore.gameplay.story_system.command;

import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
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
 * 当前只开放状态查看和手动切换阶段，任务发布、结算等能力暂时保留为 Java API。</p>
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
     * </pre>
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("dreamingfish")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("story")
                                .then(Commands.literal("status")
                                        .executes(Command_Story::showStatus))
                                .then(Commands.literal("stage")
                                        .requires(source -> source.hasPermission(3))
                                        .then(Commands.literal("set")
                                                .then(Commands.argument("stageId", StringArgumentType.word())
                                                        .suggests((context, builder) ->
                                                                SharedSuggestionProvider.suggest(
                                                                        StoryManager.getAllStagesById().keySet(), builder))
                                                        .executes(Command_Story::setStage))))));
    }

    /** 读取命令参数，调用 StoryManager 切换阶段，再向执行者反馈结果。 */
    private static int setStage(CommandContext<CommandSourceStack> context) {
        String stageId = StringArgumentType.getString(context, "stageId");
        try {
            if (!StoryManager.changeStage(stageId)) {
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
        StoryManager.ProgressSnapshot progress = snapshot.currentStageProgress();

        String message = "世界故事状态"
                + "\n- 数据版本: " + snapshot.schemaVersion()
                + "\n- 当前阶段: " + snapshot.currentStageNumber()
                + " / " + snapshot.currentStageId()
                + " / " + snapshot.currentStageName()
                + "\n- 全服任务进度: " + progress.globalResolved()
                + "/" + progress.publishedTasks()
                + "（失败 " + progress.globalFailed() + "）"
                + "\n- 在线活动时间: " + formatTicks(snapshot.activeTicks())
                + "\n- 当前阶段持续: "
                + formatTicks(snapshot.activeTicks() - snapshot.stageEnteredAtActiveTick())
                + "\n- 世界旗标: " + flags
                + "\n- 运营轮次: " + snapshot.operationRoundNumber()
                + " / " + snapshot.operationRoundStatus()
                + " / 来源=" + operationSource
                + " / 内容=" + operationContent
                + "\n- 终章状态: " + ending
                + "\n- 写入状态: " + (snapshot.writesEnabled() ? "正常" : "只读保护");

        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
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
