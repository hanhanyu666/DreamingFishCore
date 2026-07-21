package com.hhy.dreamingfishcore.server.check_system.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class Command_Info {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("info")
                        .then(Commands.literal("item")
                                .executes(context -> showItemInfo(context.getSource().getPlayerOrException())))
        );
    }

    private static int showItemInfo(ServerPlayer requester) {
        ItemStack itemStack = requester.getItemInHand(InteractionHand.MAIN_HAND);
        CompoundTag nbt = com.hhy.dreamingfishcore.common.util.ItemStackDataHelper.getTag(itemStack);

        if (nbt == null || nbt.isEmpty()) {
            requester.sendSystemMessage(Component.literal("该物品没有 NBT 数据"));
            return 0;
        }

        for (String key : nbt.getAllKeys()) {
            requester.sendSystemMessage(Component.literal(key + ": " + nbt.get(key)));
        }
        return 1;
    }
}
