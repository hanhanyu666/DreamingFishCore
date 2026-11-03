package com.hhy.dreamingfishcore.gameplay.disaster_system;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /dreamingfish disaster ...}：查看与重排天灾日程。
 *
 * <pre>
 * /dreamingfish disaster info          今天 + 后 3 日（与"天灾预警系统"方块同一口径）
 * /dreamingfish disaster next &lt;天数&gt;   后 N 日（1~14）
 * /dreamingfish disaster reroll        重新排一份（仅 3 级权限，供服务器自测）
 * </pre>
 */
public final class Command_Disaster {

    private Command_Disaster() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("disaster")
                .then(Commands.literal("info")
                        .executes(context -> show(context, DisasterService.PREVIEW_DAYS)))
                .then(Commands.literal("next")
                        .then(Commands.argument("days", IntegerArgumentType.integer(1, 14))
                                .executes(context -> show(context,
                                        IntegerArgumentType.getInteger(context, "days")))))
                .then(Commands.literal("reroll")
                        .requires(source -> source.hasPermission(3))
                        .executes(Command_Disaster::reroll)));
    }

    private static int show(CommandContext<CommandSourceStack> context, int days) {
        CommandSourceStack source = context.getSource();
        reply(source, DisasterService.describe(source.getServer(), days));
        return 1;
    }

    private static int reroll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<String> messages = new ArrayList<>(DisasterService.reroll(source.getServer()));
        messages.addAll(DisasterService.describe(source.getServer(), DisasterService.PREVIEW_DAYS));
        reply(source, messages);
        return 1;
    }

    private static void reply(CommandSourceStack source, List<String> lines) {
        lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
    }
}
