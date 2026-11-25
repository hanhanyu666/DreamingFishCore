package com.hhy.dreamingfishcore.gameplay.map_region_system;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /dreamingfish map regions}：把"地图会显示哪些区域"打出来。
 *
 * <p>这一步**还没接 Xaero**——先用聊天栏验证"服务端能不能正确汇总出区域数据"，
 * 数据对了再画图。权限等级 2（管理员），因为它会暴露领地/剧情地点的完整坐标。</p>
 */
public final class Command_MapRegions {

    private static final int REQUIRED_PERMISSION = 2;

    private Command_MapRegions() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("map")
                .then(Commands.literal("regions")
                        .requires(source -> source.hasPermission(REQUIRED_PERMISSION))
                        .executes(context -> describe(context.getSource()))));
    }

    private static int describe(CommandSourceStack source) {
        String text = MapRegionCollector.describe(source.getServer());
        DreamingFishCore.LOGGER.info("[map] 区域汇总：\n{}", text);
        if (source.getEntity() instanceof ServerPlayer player) {
            for (String line : text.split("\n")) {
                player.displayClientMessage(Component.literal("§7" + line), false);
            }
        } else {
            source.sendSuccess(() -> Component.literal(text), false);
        }
        return Command.SINGLE_SUCCESS;
    }
}
