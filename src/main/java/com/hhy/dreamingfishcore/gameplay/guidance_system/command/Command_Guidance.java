package com.hhy.dreamingfishcore.gameplay.guidance_system.command;

import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** 个人引导的服务端核验/运营命令；客户端没有手动完成入口。 */
public final class Command_Guidance {
    private Command_Guidance() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("guidance")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> list(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "target")))))
                .then(Commands.literal("resolve")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("guidanceId", StringArgumentType.word())
                                        .executes(context -> resolve(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "target"),
                                                StringArgumentType.getString(context, "guidanceId")))))));
    }

    private static int list(CommandSourceStack source, ServerPlayer player) {
        var entries = GuidanceManager.getView(player.getUUID());
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal(player.getScoreboardName() + " 暂无个人引导"), false);
            return 1;
        }
        entries.forEach(entry -> source.sendSuccess(() -> Component.literal(
                "[" + entry.status() + "] " + entry.definitionId() + " · " + entry.title()), false));
        return entries.size();
    }

    private static int resolve(CommandSourceStack source, ServerPlayer player, String guidanceId) {
        if (!GuidanceManager.resolve(player.getUUID(), guidanceId)) {
            source.sendFailure(Component.literal("未找到待处理引导，或该记录已经完成：" + guidanceId));
            return 0;
        }
        GuidanceManager.syncToClient(player);
        source.sendSuccess(() -> Component.literal("已由服务端确认完成 " + player.getScoreboardName()
                + " 的引导：" + guidanceId), true);
        return 1;
    }
}
