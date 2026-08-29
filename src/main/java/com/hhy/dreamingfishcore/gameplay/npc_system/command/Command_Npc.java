package com.hhy.dreamingfishcore.gameplay.npc_system.command;

import com.hhy.dreamingfishcore.gameplay.npc_system.NpcData;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcManager;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcMessageManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import com.hhy.dreamingfishcore.gameplay.npc_system.entity.StoryNpcEntities;
import com.hhy.dreamingfishcore.gameplay.npc_system.entity.StoryNpcEntity;

public class Command_Npc {
    private static final String COMMAND_NPC = "npc";
    private static final String COMMAND_OPEN = "open";
    private static final String COMMAND_BIND = "bind";
    private static final String COMMAND_UNBIND = "unbind";
    private static final String COMMAND_RELOAD = "reload";
    private static final String COMMAND_LIST = "list";
    private static final String COMMAND_SPAWN = "spawn";
    private static final String ARG_NPC_ID = "npcId";
    private static final String ARG_TARGET = "target";
    private static final String ARG_ENTITY = "entity";
    private static final String ARG_MESSAGE_ID = "messageId";
    private static final String MSG_NPC_NOT_FOUND = "NPC不存在: ";
    private static final String MSG_NPC_OPENED = "已打开NPC对话: ";
    private static final String MSG_NPC_BOUND = "已绑定NPC到实体: ";
    private static final String MSG_NPC_UNBOUND = "已移除实体NPC绑定";
    private static final String MSG_NPC_RELOADED = "NPC配置已重载";
    private static final String MSG_NPC_LIST_EMPTY = "当前没有NPC配置";
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(Commands.literal(COMMAND_NPC)
                .then(Commands.literal(COMMAND_OPEN)
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument(ARG_NPC_ID, IntegerArgumentType.integer(1))
                                .executes(context -> openSelf(context.getSource(), IntegerArgumentType.getInteger(context, ARG_NPC_ID)))
                                .then(Commands.argument(ARG_TARGET, EntityArgument.player())
                                        .requires(source -> source.hasPermission(2))
                                        .executes(context -> openTarget(
                                                EntityArgument.getPlayer(context, ARG_TARGET),
                                                IntegerArgumentType.getInteger(context, ARG_NPC_ID),
                                                context.getSource()
                                        )))))
                .then(Commands.literal(COMMAND_BIND)
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument(ARG_NPC_ID, IntegerArgumentType.integer(1))
                                .then(Commands.argument(ARG_ENTITY, EntityArgument.entity())
                                        .executes(context -> bindEntity(
                                                EntityArgument.getEntity(context, ARG_ENTITY),
                                                IntegerArgumentType.getInteger(context, ARG_NPC_ID),
                                                context.getSource()
                                        )))))
                .then(Commands.literal(COMMAND_SPAWN)
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument(ARG_NPC_ID, IntegerArgumentType.integer(1))
                                .executes(context -> spawn(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, ARG_NPC_ID)))))
                .then(Commands.literal("message")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument(ARG_TARGET, EntityArgument.player())
                                .then(Commands.argument(ARG_MESSAGE_ID, StringArgumentType.word())
                                        .executes(context -> sendMessage(
                                                EntityArgument.getPlayer(context, ARG_TARGET),
                                                StringArgumentType.getString(context, ARG_MESSAGE_ID),
                                                context.getSource())))))
                .then(Commands.literal("messages")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("list")
                                .executes(context -> listMessages(context.getSource())))
                        .then(Commands.literal("reload")
                                .executes(context -> reloadMessages(context.getSource()))))
                .then(Commands.literal(COMMAND_UNBIND)
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument(ARG_ENTITY, EntityArgument.entity())
                                .executes(context -> unbindEntity(EntityArgument.getEntity(context, ARG_ENTITY), context.getSource()))))
                .then(Commands.literal(COMMAND_RELOAD)
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> reload(context.getSource())))
                .then(Commands.literal(COMMAND_LIST)
                        .executes(context -> list(context.getSource()))));
    }

    private static int openSelf(CommandSourceStack source, int npcId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return openTarget(source.getPlayerOrException(), npcId, source);
    }

    private static int openTarget(ServerPlayer player, int npcId, CommandSourceStack source) {
        if (!NpcManager.openNpcDialogue(player, npcId)) {
            source.sendFailure(Component.literal(MSG_NPC_NOT_FOUND + npcId));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(MSG_NPC_OPENED + npcId), false);
        return 1;
    }

    private static int bindEntity(Entity entity, int npcId, CommandSourceStack source) {
        if (NpcManager.getNpc(npcId).isEmpty()) {
            source.sendFailure(Component.literal(MSG_NPC_NOT_FOUND + npcId));
            return 0;
        }
        entity.getPersistentData().putInt(NpcManager.ENTITY_NPC_ID_TAG, npcId);
        source.sendSuccess(() -> Component.literal(MSG_NPC_BOUND + npcId), false);
        return 1;
    }

    private static int unbindEntity(Entity entity, CommandSourceStack source) {
        entity.getPersistentData().remove(NpcManager.ENTITY_NPC_ID_TAG);
        source.sendSuccess(() -> Component.literal(MSG_NPC_UNBOUND), false);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        NpcManager.load();
        int messageCount = NpcMessageManager.reloadDefinitions();
        int refreshed = NpcManager.refreshLoadedStoryNpcs(source.getServer());
        source.sendSuccess(() -> Component.literal(MSG_NPC_RELOADED + "，已刷新 " + refreshed
                + " 个剧情NPC、" + messageCount + " 条私信定义"), true);
        return 1;
    }

    private static int sendMessage(ServerPlayer player, String messageId, CommandSourceStack source) {
        if (!NpcMessageManager.sendConfiguredMessage(player, messageId)) {
            source.sendFailure(Component.literal("私信未发送：消息不存在、好感度不满足，或一次性消息已经送达：" + messageId));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("已向 " + player.getScoreboardName() + " 发送 NPC 私信：" + messageId), true);
        return 1;
    }

    private static int reloadMessages(CommandSourceStack source) {
        int count = NpcMessageManager.reloadDefinitions();
        source.sendSuccess(() -> Component.literal("NPC 私信配置已重载，共 " + count + " 条定义"), true);
        return count;
    }

    private static int listMessages(CommandSourceStack source) {
        var ids = NpcMessageManager.getDefinitionIds();
        if (ids.isEmpty()) {
            source.sendSuccess(() -> Component.literal("当前没有可用的 NPC 私信定义"), false);
            return 1;
        }
        ids.forEach(id -> source.sendSuccess(() -> Component.literal(id), false));
        return ids.size();
    }

    private static int spawn(CommandSourceStack source, int npcId) {
        NpcData npc = NpcManager.getNpc(npcId).orElse(null);
        if (npc == null) {
            source.sendFailure(Component.literal(MSG_NPC_NOT_FOUND + npcId));
            return 0;
        }
        StoryNpcEntity entity = StoryNpcEntities.STORY_NPC.get().create(source.getLevel());
        if (entity == null) {
            source.sendFailure(Component.literal("剧情NPC实体创建失败"));
            return 0;
        }
        // EntityType#create 会给 Mob 一个随机的身体/头部朝向；moveTo 只会更新
        // Entity 的 yRot/xRot，不会同步 LivingEntity 的 yBodyRot/yHeadRot。
        // 如果不显式补齐这几个字段，客户端渲染时就会短暂甚至一直使用固定的
        // 默认朝向，看起来像所有 NPC 都面向同一个方向。
        float yaw = source.getRotation().y;
        entity.moveTo(source.getPosition().x, source.getPosition().y, source.getPosition().z,
                yaw, 0.0F);
        entity.setSpawnFacing(yaw, 0.0F);
        entity.applyNpcData(npc);
        if (!source.getLevel().addFreshEntity(entity)) {
            source.sendFailure(Component.literal("剧情NPC生成失败"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("已生成剧情NPC: [" + npcId + "] " + npc.getNpcName()), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        if (NpcManager.getAllNpcs().isEmpty()) {
            source.sendSuccess(() -> Component.literal(MSG_NPC_LIST_EMPTY), false);
            return 1;
        }
        for (NpcData npc : NpcManager.getAllNpcs()) {
            source.sendSuccess(() -> Component.literal("[" + npc.getNpcId() + "] " + npc.getNpcName() + " - " + npc.getNpcProfession()), false);
        }
        return 1;
    }
}
