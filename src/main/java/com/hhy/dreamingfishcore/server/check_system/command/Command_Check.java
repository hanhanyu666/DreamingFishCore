package com.hhy.dreamingfishcore.server.check_system.command;

import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.check_system.network.FileInspectionSecurity;
import com.hhy.dreamingfishcore.server.check_system.network.Packet_Check;
import com.hhy.dreamingfishcore.server.check_system.network.Packet_Get;
import com.hhy.dreamingfishcore.server.check_system.FileInspectionSessionManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 *  这是一个 查询 / 获取指定玩家 Mods / Shaderpacks / Resourcepacks 文件夹下文件数据的指令
 */
public class Command_Check {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 父指令
        dispatcher.register(Commands.literal("check")
                .requires(source -> source.hasPermission(2)) // 需要管理员权限
                // 第一个参数 -- 玩家
                .then(Commands.argument("playerName", EntityArgument.player())
                        // 第二个参数 -- 要查询的类型 ( Mods / Shaderpacks / resourcepacks )
                        .then(Commands.literal("mods")
                                .executes(context -> {
                                    // 获取 玩家
                                    ServerPlayer player = EntityArgument.getPlayer(context, "playerName");
                                    return checkPlayer(context.getSource(), player, "mods");
                                }))
                        .then(Commands.literal("shaderpacks")
                                .executes(context -> {
                                    // 获取 玩家
                                    ServerPlayer player = EntityArgument.getPlayer(context, "playerName");
                                    return checkPlayer(context.getSource(), player, "shaderpacks");
                                }))
                        .then(Commands.literal("resourcepacks")
                                .executes(context -> {
                                    // 获取 玩家
                                    ServerPlayer player = EntityArgument.getPlayer(context, "playerName");
                                    return checkPlayer(context.getSource(), player, "resourcepacks");
                                }))
                )
        );
        // 父指令
        dispatcher.register(Commands.literal("get")
                .requires(source -> source.hasPermission(2))
                // 第一个参数 -- 玩家
                .then(Commands.argument("playerName", EntityArgument.player())
                        // 第二个参数 -- 要获取的文件名
                        .then(Commands.argument("fileName", StringArgumentType.string())
                                // 第三个参数 -- 要获取的类型 ( Mods / Shaderpacks / resourcepacks )
                                .then(Commands.literal("mods")
                                        .executes(context -> {
                                            ServerPlayer player = EntityArgument.getPlayer(context, "playerName");
                                            String fileName = StringArgumentType.getString(context, "fileName");
                                            return getPlayerFile(context.getSource(), player, "mods", fileName);
                                        }))
                                .then(Commands.literal("shaderpacks")
                                        .executes(context -> {
                                            ServerPlayer player = EntityArgument.getPlayer(context, "playerName");
                                            String fileName = StringArgumentType.getString(context, "fileName");
                                            return getPlayerFile(context.getSource(), player, "shaderpacks", fileName);
                                        }))
                                .then(Commands.literal("resourcepacks")
                                        .executes(context -> {
                                            ServerPlayer player = EntityArgument.getPlayer(context, "playerName");
                                            String fileName = StringArgumentType.getString(context, "fileName");
                                            return getPlayerFile(context.getSource(), player, "resourcepacks", fileName);
                                        }))
                        )
                )
        );
    }

    /**
     * 检查玩家指定路径的指令
     *
     * @param source 指令源
     * @param player ServerPlayer的变量
     * @param type 操作种类
     */
    private static int checkPlayer(CommandSourceStack source, ServerPlayer player, String type) {
        ServerPlayer sender = source.getPlayer();
        if (player == null || sender == null) {
            source.sendFailure(Component.literal("该命令只能由在线管理员对在线玩家执行"));
            return Command.SINGLE_SUCCESS;
        }

        FileInspectionSessionManager.Session session = FileInspectionSessionManager.createCheck(sender, player, type);
        if (session == null) {
            source.sendFailure(Component.literal("无法创建检查会话：请求过多或参数无效"));
            return Command.SINGLE_SUCCESS;
        }

        source.sendSuccess(() -> Component.literal("检查请求已发送至 " + session.targetName()), false);

        DreamingFishCore_NetworkManager.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new Packet_Check(session.requestId(), session.actionType()));

        return Command.SINGLE_SUCCESS;
    }

    /**
     * 获取玩家本地文件的指令
     *
     * @param source 指令源
     * @param player ServerPlayer的变量
     * @param type 操作种类
     * @param fileName 文件名
     */
    private static int getPlayerFile(CommandSourceStack source, ServerPlayer player, String type, String fileName) {
        ServerPlayer sender = source.getPlayer();
        if (player == null || sender == null) {
            source.sendFailure(Component.literal("该命令只能由在线管理员对在线玩家执行"));
            return Command.SINGLE_SUCCESS;
        }
        if (!FileInspectionSecurity.isSafeFileName(fileName)) {
            source.sendFailure(Component.literal("文件名必须是单个文件名，不能包含路径或控制字符"));
            return Command.SINGLE_SUCCESS;
        }

        FileInspectionSessionManager.Session session = FileInspectionSessionManager.createGet(sender, player, type, fileName);
        if (session == null) {
            source.sendFailure(Component.literal("无法创建文件获取会话：请求过多或参数无效"));
            return Command.SINGLE_SUCCESS;
        }

        source.sendSuccess(() -> Component.literal("获取请求已发送至 " + session.targetName()), false);

        DreamingFishCore_NetworkManager.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new Packet_Get(session.requestId(), session.actionType(), session.fileName()));

        return Command.SINGLE_SUCCESS;
    }
}

