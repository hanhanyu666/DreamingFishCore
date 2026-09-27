package com.hhy.dreamingfishcore.gameplay.hospital_system;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import java.util.function.Supplier;

public final class Command_Hospital {
    private Command_Hospital() { }
    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("hospital").requires(source -> source.hasPermission(3))
                .then(Commands.literal("status").executes(context -> run(context, HospitalStory::status)))
                .then(Commands.literal("start").executes(context -> run(context, HospitalStory::start)))
                .then(Commands.literal("site").then(Commands.argument("locationId", StringArgumentType.greedyString())
                        .executes(context -> run(context, () -> HospitalStory.setSite(StringArgumentType.getString(context, "locationId"))))))
                .then(Commands.literal("complete").executes(context -> run(context, HospitalStory::complete)))
                .then(Commands.literal("reload").executes(context -> run(context, () -> {
                    HospitalConfig.reload();
                    HospitalStory.reconcile();
                    HospitalStory.refreshOnline();
                    return "医院配置已重载。\n" + HospitalStory.status();
                })));
    }

    private static int run(CommandContext<CommandSourceStack> context, Supplier<String> action) {
        try {
            String result = action.get();
            context.getSource().sendSuccess(() -> Component.literal(result), false);
            return 1;
        } catch (RuntimeException exception) {
            context.getSource().sendFailure(Component.literal("医院操作未完成：" + exception.getMessage()));
            return 0;
        }
    }
}
