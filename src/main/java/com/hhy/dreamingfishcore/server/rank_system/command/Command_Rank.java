package com.hhy.dreamingfishcore.server.rank_system.command;

import com.hhy.dreamingfishcore.server.rank_system.PlayerRankManager;
import com.hhy.dreamingfishcore.server.rank_system.Rank;
import com.hhy.dreamingfishcore.server.rank_system.RankRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

public class Command_Rank {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("rank");
        // /rank set <玩家> <等级名>（仅管理员）
        root.then(Commands.literal("set")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("rankName", StringArgumentType.string())
                                .executes(Command_Rank::executeSetRank))));
        // /rank get <玩家>（普通玩家也可查询）
        root.then(Commands.literal("get")
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(Command_Rank::executeGetRank)));
        dispatcher.register(root);
    }

    private static int executeSetRank(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "target");
        String rankName = StringArgumentType.getString(context, "rankName");
        Rank targetRank = RankRegistry.isRegistered(rankName) ? RankRegistry.getRankByName(rankName) : null;

        if (targetRank == null) {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("无效的等级名！"));
            return 0;
        }

        // 使用PlayerRankManager设置等级
        PlayerRankManager.setPlayerRankServer(targetPlayer, targetRank);
        context.getSource().sendSuccess(
                () -> net.minecraft.network.chat.Component.literal("已授予并装配玩家 " + targetPlayer.getName().getString()
                        + " 的 Rank：" + targetRank.getRankName()),
                true
        );
        return 1;
    }

    private static int executeGetRank(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "target");
        // 使用PlayerRankManager获取等级
        Rank currentRank = PlayerRankManager.getPlayerRankServer(targetPlayer);
        context.getSource().sendSuccess(
                () -> net.minecraft.network.chat.Component.literal("玩家 " + targetPlayer.getName().getString() + " 的当前等级：" + currentRank.getRankName()),
                false
        );
        return 1;
    }
}
