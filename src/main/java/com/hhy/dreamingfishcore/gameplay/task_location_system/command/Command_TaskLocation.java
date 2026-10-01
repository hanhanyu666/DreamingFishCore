package com.hhy.dreamingfishcore.gameplay.task_location_system.command;

import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationDefinition;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationManager;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationMode;
import com.hhy.dreamingfishcore.server.notice_system.NotificationPushHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/** 任务地点的服主管理命令。 */
public final class Command_TaskLocation {
    private Command_TaskLocation() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("dreamingfish")
                .requires(source -> source.hasPermission(2));
        register(root);
        dispatcher.register(root);
    }

    /** 将任务地点子树挂到统一的 /dreamingfish 根节点。 */
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("task_location")
                .requires(source -> source.hasPermission(3))
                .then(Commands.literal("list")
                        .executes(Command_TaskLocation::list))
                .then(Commands.literal("info")
                        .then(locationNameArgument()
                                .executes(Command_TaskLocation::info)))
                .then(Commands.literal("reload")
                        .executes(Command_TaskLocation::reload))
                .then(Commands.literal("select")
                        .then(Commands.literal("buildable")
                                .then(locationNameArgument()
                                        .executes(context -> beginSelection(
                                                context, TaskLocationMode.BUILDABLE))))
                        .then(Commands.literal("protected")
                                .then(locationNameArgument()
                                        .executes(context -> beginSelection(
                                                context, TaskLocationMode.PROTECTED))))
                        .then(locationNameArgument()
                                .executes(context -> beginSelection(
                                        context, null))))
                .then(pointCommand("pos1", true))
                .then(pointCommand("pos2", false))
                .then(Commands.literal("confirm")
                        .executes(Command_TaskLocation::confirm))
                .then(Commands.literal("cancel")
                        .executes(Command_TaskLocation::cancel))
                .then(Commands.literal("horde")
                        .then(Commands.literal("on")
                                .then(locationNameArgument()
                                        .executes(context -> setHorde(context, true))))
                        .then(Commands.literal("off")
                                .then(locationNameArgument()
                                        .executes(context -> setHorde(context, false)))))
                .then(Commands.literal("remove")
                        .then(locationNameArgument()
                                .executes(Command_TaskLocation::remove))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> pointCommand(
            String literal, boolean firstPoint) {
        return Commands.literal(literal)
                .executes(context -> selectPoint(context, firstPoint, false))
                .then(Commands.argument("position", BlockPosArgument.blockPos())
                        .executes(context -> selectPoint(context, firstPoint, true)));
    }

    private static int selectPoint(
            CommandContext<CommandSourceStack> context,
            boolean firstPoint,
            boolean useArgument) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            BlockPos position = useArgument
                    ? BlockPosArgument.getBlockPos(context, "position")
                    : player.blockPosition();
            boolean handled = firstPoint
                    ? TaskLocationManager.selectFirstPoint(player, position)
                    : TaskLocationManager.selectSecondPoint(player, position);
            if (!handled) {
                context.getSource().sendFailure(Component.literal(
                        "请先使用 task_location select 开始设置任务地点"));
                return 0;
            }
            return 1;
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
    locationNameArgument() {
        return Commands.argument("name", StringArgumentType.greedyString())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        TaskLocationManager.getAllLocations().stream()
                                .map(TaskLocationDefinition::getName), builder));
    }

    private static int beginSelection(CommandContext<CommandSourceStack> context) {
        return beginSelection(context, null);
    }

    private static int beginSelection(
            CommandContext<CommandSourceStack> context, TaskLocationMode requestedMode) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String locationName = StringArgumentType.getString(context, "name");
            if (requestedMode == null) {
                TaskLocationManager.beginSelection(player, locationName);
            } else {
                TaskLocationManager.beginSelection(player, locationName, requestedMode);
            }
            context.getSource().sendSuccess(
                    () -> Component.literal("已开始设置任务地点：" + locationName), false);
            return 1;
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int confirm(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            TaskLocationDefinition location = TaskLocationManager.confirmSelection(player);
            NotificationPushHelper.sendTopLeftNotification(player,
                    "§a任务地点已保存§r\n" + location.getName(), 8000);
            context.getSource().sendSuccess(
                    () -> Component.literal("任务地点已保存：" + describe(location)), true);
            return 1;
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int cancel(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            if (!TaskLocationManager.cancelSelection(player)) {
                context.getSource().sendFailure(Component.literal("当前没有正在设置的任务地点"));
                return 0;
            }
            NotificationPushHelper.sendTopLeftNotification(player, "§7已取消任务地点选区。", 4000);
            return 1;
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        String locationName = StringArgumentType.getString(context, "name");
        try {
            if (!TaskLocationManager.removeLocationByName(locationName)) {
                context.getSource().sendFailure(Component.literal("任务地点不存在：" + locationName));
                return 0;
            }
            context.getSource().sendSuccess(
                    () -> Component.literal("已删除任务地点：" + locationName), true);
            return 1;
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        try {
            int count = TaskLocationManager.reload();
            context.getSource().sendSuccess(
                    () -> Component.literal("任务地点配置已热重载，共 " + count + " 个地点"), true);
            return 1;
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    /**
     * 开关尸潮区域。
     *
     * <p>尸潮开关只影响"区域内的刷怪箱能不能工作"和"刷怪箱的生成是否被自动刷怪禁令拦下"，
     * 不改保护与圈地规则 —— 所以开在可建造地点上是常规用法，开在强制保护地点上也合法
     * （玩家不能建造，但会有尸潮），这里只做提示不做限制。</p>
     */
    private static int setHorde(CommandContext<CommandSourceStack> context, boolean horde) {
        String locationName = StringArgumentType.getString(context, "name");
        try {
            java.util.Optional<Boolean> result = TaskLocationManager.setHordeByName(locationName, horde);
            if (result.isEmpty()) {
                context.getSource().sendFailure(Component.literal("任务地点不存在：" + locationName));
                return 0;
            }
            TaskLocationDefinition location = TaskLocationManager
                    .getLocationByName(locationName).orElse(null);
            StringBuilder hint = new StringBuilder();
            if (location != null && !location.isBuildable()) {
                hint.append("（提示：该地点是强制保护模式，玩家在里面不能建造；尸潮开关本身照常生效）");
            }
            if (location != null && horde) {
                int span = location.getMax().getY() - location.getMin().getY() + 1;
                if (span < 5) {
                    // 任务地点是三维盒子：只有一格高的区域会让刷怪箱放高/放低一格就"不在区域内"。
                    hint.append("（注意：该区域只有 ").append(span).append(" 格高（Y ")
                            .append(location.getMin().getY()).append("..")
                            .append(location.getMax().getY())
                            .append("），刷怪箱必须放在这个高度范围内；建议把区域划高一些）");
                }
            }
            context.getSource().sendSuccess(() -> Component.literal(
                    "任务地点「" + locationName + "」的尸潮区域已"
                            + (horde ? "开启" : "关闭") + hint), true);
            return 1;
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        Collection<TaskLocationDefinition> locations = TaskLocationManager.getAllLocations();
        if (locations.isEmpty()) {
            context.getSource().sendSuccess(
                    () -> Component.literal("尚未定义任务地点。配置文件："
                            + TaskLocationManager.getConfigPath()), false);
            return 1;
        }
        StringBuilder message = new StringBuilder("任务地点（").append(locations.size()).append("）");
        for (TaskLocationDefinition location : locations) {
            message.append("\n- ").append(describe(location));
        }
        context.getSource().sendSuccess(() -> Component.literal(message.toString()), false);
        return locations.size();
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        String locationName = StringArgumentType.getString(context, "name");
        TaskLocationDefinition location = TaskLocationManager.getLocationByName(locationName).orElse(null);
        if (location == null) {
            context.getSource().sendFailure(Component.literal("任务地点不存在：" + locationName));
            return 0;
        }
        int eligiblePlayers = TaskLocationManager.getEligiblePlayers(
                context.getSource().getServer(), location.getId()).size();
        context.getSource().sendSuccess(
                () -> Component.literal(describe(location) + "\n地点 ID：" + location.getId()
                        + "\n当前合格在场玩家：" + eligiblePlayers), false);
        return 1;
    }

    private static String describe(TaskLocationDefinition location) {
        return location.getName() + " / " + location.getDimension()
                + " / " + format(location.getMin()) + " -> " + format(location.getMax())
                + " / " + (location.isBuildable() ? "可建造" : "强制保护")
                + (location.hasHordeFlag() ? " / 尸潮区域" : "")
                + (location.isEnabled() ? "" : " / 已停用");
    }

    private static String format(net.minecraft.core.BlockPos position) {
        return position.getX() + "," + position.getY() + "," + position.getZ();
    }
}
