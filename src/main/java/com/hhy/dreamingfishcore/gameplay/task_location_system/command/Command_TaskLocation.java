package com.hhy.dreamingfishcore.gameplay.task_location_system.command;

import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationDefinition;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationManager;
import com.hhy.dreamingfishcore.server.notice_system.NotificationPushHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/** 任务地点的服主管理命令。 */
public final class Command_TaskLocation {
    private Command_TaskLocation() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("dreamingfish")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.literal("task_location")
                                .then(Commands.literal("list")
                                        .executes(Command_TaskLocation::list))
                                .then(Commands.literal("info")
                                        .then(locationNameArgument()
                                                .executes(Command_TaskLocation::info)))
                                .then(Commands.literal("reload")
                                        .executes(Command_TaskLocation::reload))
                                .then(Commands.literal("select")
                                        .then(locationNameArgument()
                                                .executes(Command_TaskLocation::beginSelection)))
                                .then(Commands.literal("confirm")
                                        .executes(Command_TaskLocation::confirm))
                                .then(Commands.literal("cancel")
                                        .executes(Command_TaskLocation::cancel))
                                .then(Commands.literal("remove")
                                        .then(locationNameArgument()
                                                .executes(Command_TaskLocation::remove)))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
    locationNameArgument() {
        return Commands.argument("name", StringArgumentType.greedyString())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        TaskLocationManager.getAllLocations().stream()
                                .map(TaskLocationDefinition::getName), builder));
    }

    private static int beginSelection(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String locationName = StringArgumentType.getString(context, "name");
            TaskLocationManager.beginSelection(player, locationName);
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
                () -> Component.literal(describe(location) + "\n当前合格在场玩家：" + eligiblePlayers), false);
        return 1;
    }

    private static String describe(TaskLocationDefinition location) {
        return location.getName() + " / " + location.getDimension()
                + " / " + format(location.getMin()) + " -> " + format(location.getMax())
                + (location.isEnabled() ? "" : " / 已停用");
    }

    private static String format(net.minecraft.core.BlockPos position) {
        return position.getX() + "," + position.getY() + "," + position.getZ();
    }
}
