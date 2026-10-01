package com.hhy.dreamingfishcore.gameplay.story_system.command;

import com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamPlayerProgress;
import com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamStory;
import com.hhy.dreamingfishcore.gameplay.task_location_system.StoryLocationResolver;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationDefinition;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationManager;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationMode;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

/**
 * 剧情流程的测试命令（仅 3 级权限可用）。
 *
 * <pre>
 * /dreamingfish debug story preset reception
 * /dreamingfish debug story preset finalcheck
 * /dreamingfish debug story preset followup
 * /dreamingfish debug story preset location medical [半径] [protected|buildable]
 * /dreamingfish debug story preset location abydos [半径] [protected|buildable]
 * /dreamingfish debug story course complete
 * /dreamingfish debug story course followup due &lt;3|7&gt;
 * /dreamingfish debug story course followup done &lt;3|7&gt;
 * </pre>
 *
 * <p>为什么需要疗程／随访命令：疗程间隔与随访都按“剧情活动时间”计算——一次治疗要等一个
 * 活动日（24,000 tick ≈ 20 分钟真实时间），第 7 天随访要等到近 140 分钟。这些命令跳过等待，
 * 但**结算逻辑与正式流程完全相同**，因此可以用来验证进度、清零、引导与文案。</p>
 *
 * <p>{@code preset location} 以执行者当前站的位置为中心，用剧情角色的固定 ID 直接建点。
 * 自从 {@link StoryLocationResolver} 支持按**名称关键词**匹配后，这条命令不再必需——服主用
 * {@code task_location select 逐光会医疗接待点} 建出来的地点同样会被剧情认到；它的价值是“一步建好、
 * 给出确定的 ID、并且能顺手替换同名旧地点”。同名条目重复会让整份地点配置加载失败，故一并清理。</p>
 */
public final class Command_StoryDebug {

    /** 可一键创建的剧情固定地点。 */
    private static final List<String> LOCATION_TARGETS = List.of("medical", "abydos");
    private static final List<String> LOCATION_MODES = List.of("protected", "buildable");

    private static final String MEDICAL_LOCATION_NAME = "逐光会医疗接待点";
    private static final String ABYDOS_LOCATION_NAME = "阿拜多斯区域";
    /** 医疗接待点默认 33×25×33（水平半径 16，向下 8、向上 16）。 */
    private static final int DEFAULT_MEDICAL_RADIUS = 16;
    private static final int DEFAULT_MEDICAL_DOWN = 8;
    private static final int DEFAULT_MEDICAL_UP = 16;
    /** 阿拜多斯区域默认 97×73×97（水平半径 48，向下 24、向上 48）。 */
    private static final int DEFAULT_ABYDOS_RADIUS = 48;
    private static final int DEFAULT_ABYDOS_DOWN = 24;
    private static final int DEFAULT_ABYDOS_UP = 48;

    private Command_StoryDebug() {
    }

    /** 供 {@code /dreamingfish debug} 根节点复用；根节点与权限由命令管理器统一提供。 */
    public static LiteralArgumentBuilder<CommandSourceStack> storyBranch() {
        return Commands.literal("story")
                .then(Commands.literal("preset")
                        .then(preset("reception"))
                        .then(preset("finalcheck"))
                        .then(preset("followup"))
                        .then(Commands.literal("location")
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests(suggest(LOCATION_TARGETS))
                                        .executes(context -> createStoryLocation(
                                                context, -1, TaskLocationMode.PROTECTED))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(4, 256))
                                                .executes(context -> createStoryLocation(
                                                        context,
                                                        IntegerArgumentType.getInteger(context, "radius"),
                                                        TaskLocationMode.PROTECTED))
                                                .then(Commands.argument("mode", StringArgumentType.word())
                                                        .suggests(suggest(LOCATION_MODES))
                                                        .executes(context -> createStoryLocation(
                                                                context,
                                                                IntegerArgumentType.getInteger(context, "radius"),
                                                                parseMode(StringArgumentType.getString(
                                                                        context, "mode")))))))))
                .then(Commands.literal("course")
                        .then(Commands.literal("complete")
                                .executes(Command_StoryDebug::completeCourse))
                        .then(Commands.literal("followup")
                                .then(Commands.literal("due")
                                        .then(Commands.argument("day", IntegerArgumentType.integer(3, 7))
                                                .executes(Command_StoryDebug::markFollowUpDue)))
                                .then(Commands.literal("done")
                                        .then(Commands.argument("day", IntegerArgumentType.integer(3, 7))
                                                .executes(Command_StoryDebug::completeFollowUp)))));
    }

    /** 预设写成显式字面量：与 {@code location} 子命令同级时不会产生解析歧义。 */
    private static LiteralArgumentBuilder<CommandSourceStack> preset(String name) {
        return Commands.literal(name).executes(context -> applyPreset(context, name));
    }

    private static com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> suggest(
            List<String> candidates) {
        return (context, builder) -> {
            for (String candidate : candidates) {
                builder.suggest(candidate);
            }
            return builder.buildFuture();
        };
    }

    private static TaskLocationMode parseMode(String raw) {
        return "buildable".equalsIgnoreCase(raw)
                ? TaskLocationMode.BUILDABLE : TaskLocationMode.PROTECTED;
    }

    private static int applyPreset(CommandContext<CommandSourceStack> context, String preset) {
        return withPlayer(context, player -> AfterdreamStory.debugApplyPreset(player, preset),
                "已快进到预设进度：" + preset + "（详细提示见聊天栏与引导）");
    }

    /**
     * 用剧情固定 ID 创建／覆盖一个地点，范围以执行者当前站的位置为中心。
     *
     * <p>建好后不需要走出去再走回来：地点观察每 20 tick 执行一次，位置从“无地点”变成
     * 新地点即视为进入，1 秒内就会触发现有的进入流程。</p>
     */
    private static int createStoryLocation(
            CommandContext<CommandSourceStack> context, int requestedRadius, TaskLocationMode mode) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException exception) {
            source.sendFailure(Component.literal("该命令只能由玩家执行（地点以你当前站的位置为中心）"));
            return 0;
        }

        String target = StringArgumentType.getString(context, "target").toLowerCase(Locale.ROOT);
        boolean medical = "medical".equals(target);
        boolean abydos = "abydos".equals(target);
        if (!medical && !abydos) {
            source.sendFailure(Component.literal("未知地点：" + target
                    + "（可选 " + String.join(" / ", LOCATION_TARGETS) + "）"));
            return 0;
        }

        BlockPos center = player.blockPosition();
        ResourceKey<Level> dimension = player.level().dimension();
        String locationId = medical
                ? StoryLocationResolver.Role.ZHUIGUANG.fixedId()
                : StoryLocationResolver.Role.ABYDOS.fixedId();
        String name = medical ? MEDICAL_LOCATION_NAME : ABYDOS_LOCATION_NAME;
        int radius = requestedRadius < 0
                ? (medical ? DEFAULT_MEDICAL_RADIUS : DEFAULT_ABYDOS_RADIUS) : requestedRadius;
        int down = medical ? DEFAULT_MEDICAL_DOWN : DEFAULT_ABYDOS_DOWN;
        int up = medical ? DEFAULT_MEDICAL_UP : DEFAULT_ABYDOS_UP;

        TaskLocationDefinition created;
        String replacedNote;
        try {
            TaskLocationDefinition replaced = TaskLocationManager.getLocationByName(name).orElse(null);
            replacedNote = replaced == null || replaced.getId().equals(locationId)
                    ? "" : "\n  已替换同名旧地点：" + replaced.getId();
            created = install(locationId, name, dimension, center, radius, down, up, mode);
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal("创建剧情地点失败：" + exception.getMessage()));
            return 0;
        }

        String summary = describe(created) + replacedNote;
        source.sendSuccess(() -> Component.literal(summary), true);
        return 1;
    }

    private static TaskLocationDefinition install(
            String locationId, String name, ResourceKey<Level> dimension, BlockPos center,
            int radius, int down, int up, TaskLocationMode mode) {
        BlockPos min = new BlockPos(
                center.getX() - radius, center.getY() - down, center.getZ() - radius);
        BlockPos max = new BlockPos(
                center.getX() + radius, center.getY() + up, center.getZ() + radius);
        return TaskLocationManager.installLocationWithFixedId(locationId, name, dimension, min, max, mode);
    }

    private static String describe(TaskLocationDefinition location) {
        return "已创建剧情地点：" + location.getName()
                + "\n  ID：" + location.getId()
                + "\n  " + location.getDimension() + "  " + format(location.getMin())
                + " -> " + format(location.getMax())
                + "  / " + (location.isBuildable() ? "可建造" : "强制保护");
    }

    private static String format(BlockPos position) {
        return position.getX() + "," + position.getY() + "," + position.getZ();
    }

    private static int completeCourse(CommandContext<CommandSourceStack> context) {
        return withPlayer(context, player -> {
            if (!AfterdreamStory.debugCompleteCourse(player)) {
                return "无法完成疗程：玩家不在余梦期、还没有开始疗程，或身份不是稳定感染者。";
            }
            return null;
        }, "疗程已完成（终检结算与正式流程一致）");
    }

    private static int markFollowUpDue(CommandContext<CommandSourceStack> context) {
        int day = normalizedDay(context);
        return withPlayer(context, player -> {
            if (!AfterdreamStory.debugMarkFollowUpDue(player, day)) {
                return "无法标记随访：需要先完成疗程终检，且该次随访尚未完成。";
            }
            return null;
        }, "第 " + day + " 天随访已置为待复核（可去找白芷交谈验证对话）");
    }

    private static int completeFollowUp(CommandContext<CommandSourceStack> context) {
        int day = normalizedDay(context);
        return withPlayer(context, player -> {
            if (!AfterdreamStory.debugCompleteFollowUp(player, day)) {
                return "无法完成随访：需要先完成疗程终检，且该次随访尚未复核。";
            }
            return null;
        }, "第 " + day + " 天随访已记为完成");
    }

    /** 命令接受 3..7 的任意值，这里只区分“第 3 天”和“第 7 天”两种随访。 */
    private static int normalizedDay(CommandContext<CommandSourceStack> context) {
        int raw = IntegerArgumentType.getInteger(context, "day");
        return raw <= 4 ? AfterdreamPlayerProgress.FOLLOW_UP_THIRD_DAY
                : AfterdreamPlayerProgress.FOLLOW_UP_SEVENTH_DAY;
    }

    private interface PlayerAction {
        /** 返回非 null 表示需要反馈给执行者的失败原因。 */
        String run(ServerPlayer player);
    }

    private static int withPlayer(
            CommandContext<CommandSourceStack> context, PlayerAction action, String successMessage) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException exception) {
            source.sendFailure(Component.literal("该命令只能由玩家执行（剧情进度保存在具体玩家身上）"));
            return 0;
        }
        String failure = action.run(player);
        if (failure != null) {
            source.sendFailure(Component.literal(failure));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(successMessage), true);
        return 1;
    }
}
