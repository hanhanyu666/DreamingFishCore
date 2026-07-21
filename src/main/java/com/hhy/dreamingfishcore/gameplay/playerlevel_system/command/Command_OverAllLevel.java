package com.hhy.dreamingfishcore.gameplay.playerlevel_system.command;

import com.hhy.dreamingfishcore.gameplay.playerlevel_system.overalllevel.PlayerLevelManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class Command_OverAllLevel {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // 注册总等级指令树：/overalllevel
        dispatcher.register(Commands.literal("overalllevel")
                // 设置总等级：/overalllevel set <玩家> <等级>
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(2)) // 需要OP权限
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("level", IntegerArgumentType.integer(0)) // 等级不能为负数
                                        .executes(Command_OverAllLevel::executeSetLevel)
                                )
                        )
                )
                // 查询总等级：/overalllevel get <玩家>
                .then(Commands.literal("get")
                        .requires(source -> source.hasPermission(0)) // 所有人可查询
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(Command_OverAllLevel::executeGetLevel)
                        )
                )
        );
    }

    /**
     * 执行设置玩家总等级的指令逻辑
     */
    private static int executeSetLevel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "target");
        int level = IntegerArgumentType.getInteger(context, "level");

        // 调用能力提供者的工具方法设置等级
        PlayerLevelManager.setPlayerLevelServer(targetPlayer, level);

        // 发送成功消息
        context.getSource().sendSuccess(
                () -> Component.literal("已将玩家 " + targetPlayer.getName().getString() + " 的总等级设置为：" + level),
                true // 广播给所有玩家
        );
        return 1;
    }

    /**
     * 执行查询玩家总等级的指令逻辑
     */
    private static int executeGetLevel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "target");
        int currentLevel = PlayerLevelManager.getPlayerLevelServer(targetPlayer);

        // 发送查询结果
        context.getSource().sendSuccess(
                () -> Component.literal("玩家 " + targetPlayer.getName().getString() + " 的当前总等级：" + currentLevel),
                false // 仅执行者可见
        );
        return 1;
    }
}