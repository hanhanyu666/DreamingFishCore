package com.hhy.dreamingfishcore.server.title_system.command;

import com.hhy.dreamingfishcore.server.title_system.PlayerTitleManager;
import com.hhy.dreamingfishcore.server.title_system.Title;
import com.hhy.dreamingfishcore.server.title_system.TitleConfig;
import com.hhy.dreamingfishcore.server.title_system.TitleRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class Command_Title {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(Commands.literal("title")
                // 设置玩家称号：/title set <玩家> <称号ID>
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("titleId", IntegerArgumentType.integer())
                                        .executes(Command_Title::executeSetTitle)
                                )
                        )
                )
                // 删除称号：/title delete <称号ID>
                .then(Commands.literal("delete")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("titleId", IntegerArgumentType.integer())
                                .executes(Command_Title::executeDeleteTitle)
                        )
                )
                // 查询玩家称号：/title get <玩家>
                .then(Commands.literal("get")
                        .requires(source -> source.hasPermission(0))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(Command_Title::executeGetTitle)
                        )
                )
        );
    }

    private static int executeSetTitle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "target");
        int titleId = IntegerArgumentType.getInteger(context, "titleId");
        Title targetTitle = TitleRegistry.getTitleById(titleId);

        if (targetTitle == null) {
            context.getSource().sendFailure(Component.literal("错误：不存在ID为 " + titleId + " 的称号！"));
            return 0;
        }

        PlayerTitleManager.setPlayerTitleServer(targetPlayer, targetTitle);
        context.getSource().sendSuccess(
                () -> Component.literal("已将玩家 " + targetPlayer.getName().getString() + " 的称号设置为：" + targetTitle.getTitleName()),
                true
        );
        targetPlayer.sendSystemMessage(Component.literal("你的称号已更新为：" + targetTitle.getTitleName()));
        return 1;
    }

    private static int executeDeleteTitle(CommandContext<CommandSourceStack> context) {
        int titleId = IntegerArgumentType.getInteger(context, "titleId");

        if (titleId == 0) {
            context.getSource().sendFailure(Component.literal("错误：默认称号（ID=0）不允许删除！"));
            return 0;
        }

        if (!TitleConfig.containsTitleId(titleId)) {
            context.getSource().sendFailure(Component.literal("错误：不存在ID为 " + titleId + " 的称号！"));
            return 0;
        }
        Title titleToDelete = TitleRegistry.getTitleById(titleId);

        if (!TitleConfig.removeTitleById(titleId)) {
            context.getSource().sendFailure(Component.literal("错误：称号配置写入失败，删除操作已回滚！"));
            return 0;
        }

        context.getSource().sendSuccess(
                () -> Component.literal("成功删除称号：" + titleToDelete.getTitleName() + "（ID=" + titleId + "）"),
                true
        );
        return 1;
    }

    private static int executeGetTitle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "target");
        Title currentTitle = PlayerTitleManager.getPlayerTitleServer(targetPlayer);
        context.getSource().sendSuccess(
                () -> Component.literal("玩家 " + targetPlayer.getName().getString() + " 的当前称号：" + currentTitle.getTitleName()),
                false
        );
        return 1;
    }
}
