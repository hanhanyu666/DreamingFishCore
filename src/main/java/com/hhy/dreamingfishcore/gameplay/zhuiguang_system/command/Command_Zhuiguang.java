package com.hhy.dreamingfishcore.gameplay.zhuiguang_system.command;

import com.hhy.dreamingfishcore.gameplay.zhuiguang_system.ZhuiguangMembershipManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class Command_Zhuiguang {
    private Command_Zhuiguang() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("zhuiguang")
                .then(Commands.literal("status")
                        .executes(context -> showStatus(
                                context.getSource(),
                                context.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> showStatus(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "target")))))
                .then(Commands.literal("membership")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.literal("member")
                                        .executes(context -> setMembership(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "target"),
                                                true)))
                                .then(Commands.literal("independent")
                                        .executes(context -> setMembership(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "target"),
                                                false))))));
    }

    private static int showStatus(CommandSourceStack source, ServerPlayer player) {
        boolean member = ZhuiguangMembershipManager.isMember(player);
        source.sendSuccess(
                () -> Component.literal(player.getScoreboardName() + "："
                        + ZhuiguangMembershipManager.getDisplayName(member)),
                false);
        return 1;
    }

    private static int setMembership(CommandSourceStack source, ServerPlayer player, boolean member) {
        boolean changed = ZhuiguangMembershipManager.setMember(player, member);
        String displayName = ZhuiguangMembershipManager.getDisplayName(member);
        if (changed) {
            source.sendSuccess(
                    () -> Component.literal("已将 " + player.getScoreboardName() + " 的身份更新为：" + displayName),
                    true);
            player.sendSystemMessage(Component.literal("§6[逐光会] §f你的组织身份已更新为：§e" + displayName));
            return 1;
        }
        source.sendSuccess(
                () -> Component.literal(player.getScoreboardName() + " 当前已经是：" + displayName),
                false);
        return 0;
    }
}
