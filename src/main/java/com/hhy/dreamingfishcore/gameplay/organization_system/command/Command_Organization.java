package com.hhy.dreamingfishcore.gameplay.organization_system.command;

import com.hhy.dreamingfishcore.gameplay.organization_system.Organization;
import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationConfig;
import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationManager;
import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationRank;
import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationResult;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Map;

/**
 * 组织命令：{@code /organization}（别名 {@code /org}）。
 *
 * <p>终端界面的「组织」页面是主要入口，本命令用于不可用界面时的操作与自测。两者调用的是
 * 同一套 {@link OrganizationManager} 校验，不存在"命令能做、界面不能做"的差异。</p>
 *
 * <p>涉及其他玩家的操作（审批、邀请、踢人、任免、转让）**要求对方在线**——离线玩家拿不到
 * 权威的显示名，宁可明确报错也不写一个可能错的名字进存档。</p>
 */
public final class Command_Organization {

    private Command_Organization() {
    }

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("organization");
        register(root);
        dispatcher.register(root);
        dispatcher.register(Commands.literal("org").redirect(root.build()));
    }

    /** 供统一的根节点复用。 */
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> run(context,
                                        player -> OrganizationManager.create(player,
                                                StringArgumentType.getString(context, "name"))))))
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> run(context,
                                        player -> OrganizationManager.rename(player,
                                                StringArgumentType.getString(context, "name"))))))
                .then(Commands.literal("announce")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(context -> run(context,
                                        player -> OrganizationManager.setAnnouncement(player,
                                                StringArgumentType.getString(context, "text"))))))
                .then(Commands.literal("apply")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> run(context, player -> OrganizationManager.apply(
                                        player, organizationIdFor(context, "name"))))))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> run(context,
                                        player -> OrganizationManager.cancelApplication(
                                                player, organizationIdFor(context, "name"))))))
                .then(Commands.literal("accept")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> run(context,
                                        player -> OrganizationManager.respondInvite(player,
                                                organizationIdFor(context, "name"), true)))))
                .then(Commands.literal("decline")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> run(context,
                                        player -> OrganizationManager.respondInvite(player,
                                                organizationIdFor(context, "name"), false)))))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> run(context,
                                        player -> OrganizationManager.invite(player,
                                                EntityArgument.getPlayer(context, "player"))))))
                .then(Commands.literal("review")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> run(context,
                                        player -> OrganizationManager.reviewApplication(player,
                                                EntityArgument.getPlayer(context, "player")
                                                        .getUUID().toString(), true)))))
                .then(Commands.literal("reject")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> run(context,
                                        player -> OrganizationManager.reviewApplication(player,
                                                EntityArgument.getPlayer(context, "player")
                                                        .getUUID().toString(), false)))))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> run(context,
                                        player -> OrganizationManager.kick(player,
                                                EntityArgument.getPlayer(context, "player")
                                                        .getUUID())))))
                .then(Commands.literal("transfer")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> run(context,
                                        player -> OrganizationManager.transferLeadership(player,
                                                EntityArgument.getPlayer(context, "player")
                                                        .getUUID())))))
                .then(Commands.literal("rank")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("rank", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (OrganizationRank rank : OrganizationRank.values()) {
                                                builder.suggest(rank.name().toLowerCase(Locale.ROOT));
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> run(context,
                                                player -> OrganizationManager.setRank(player,
                                                        EntityArgument.getPlayer(context, "player")
                                                                .getUUID(),
                                                        rankOf(StringArgumentType.getString(
                                                                context, "rank"))))))))
                .then(Commands.literal("leave").executes(context ->
                        run(context, OrganizationManager::leave)))
                .then(Commands.literal("disband").executes(context ->
                        run(context, OrganizationManager::disband)))
                .then(Commands.literal("list").executes(Command_Organization::list))
                .then(Commands.literal("info").executes(context -> info(context, null))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> info(context,
                                        StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(3))
                        .executes(context -> {
                            OrganizationConfig config = OrganizationConfig.reload();
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "组织配置已重载：上限 " + config.getMaxOrganizations() + " 个组织 / "
                                            + config.getMaxMembers() + " 人，名称 "
                                            + config.getNameMinLength() + "-"
                                            + config.getNameMaxLength() + " 字"), true);
                            return 1;
                        }))
                .then(Commands.literal("force-disband")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> {
                                    OrganizationResult result = OrganizationManager.forceDisband(
                                            organizationIdFor(context, "name"));
                                    return report(context, result);
                                })));
    }

    private static OrganizationRank rankOf(String raw) {
        try {
            return OrganizationRank.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** 允许写组织名或组织 id，解析失败时返回空串，由 Manager 统一报"组织不存在"。 */
    private static String organizationIdFor(CommandContext<CommandSourceStack> context, String key) {
        String raw = StringArgumentType.getString(context, key);
        return OrganizationManager.findByName(raw)
                .map(Organization::id)
                .orElseGet(() -> raw == null ? "" : raw.trim());
    }

    private static int run(CommandContext<CommandSourceStack> context,
                           PlayerAction action) {
        ServerPlayer player;
        try {
            player = context.getSource().getPlayerOrException();
        } catch (CommandSyntaxException exception) {
            context.getSource().sendFailure(Component.literal("该命令只能由玩家执行"));
            return 0;
        }
        try {
            return report(context, action.run(player));
        } catch (CommandSyntaxException exception) {
            context.getSource().sendFailure(Component.literal("找不到目标玩家：" + exception.getMessage()));
            return 0;
        }
    }

    private static int report(CommandContext<CommandSourceStack> context, OrganizationResult result) {
        if (result.success()) {
            context.getSource().sendSuccess(
                    () -> Component.literal("§a[组织] §f" + result.message()), false);
            return 1;
        }
        context.getSource().sendFailure(Component.literal(result.message()));
        return 0;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        var organizations = OrganizationManager.all();
        if (organizations.isEmpty()) {
            context.getSource().sendSuccess(
                    () -> Component.literal("目前还没有任何组织。用 /organization create <名称> 创建一个。"),
                    false);
            return 1;
        }
        StringBuilder message = new StringBuilder("组织列表（").append(organizations.size()).append("）");
        for (Organization organization : organizations) {
            message.append("\n- ").append(organization.name())
                    .append("  ").append(organization.memberCount()).append(" 人");
        }
        context.getSource().sendSuccess(() -> Component.literal(message.toString()), false);
        return organizations.size();
    }

    private static int info(CommandContext<CommandSourceStack> context, String name) {
        Organization organization;
        if (name == null) {
            ServerPlayer player;
            try {
                player = context.getSource().getPlayerOrException();
            } catch (CommandSyntaxException exception) {
                context.getSource().sendFailure(Component.literal("请指定组织名称"));
                return 0;
            }
            organization = OrganizationManager.findByPlayer(player.getUUID()).orElse(null);
        } else {
            organization = OrganizationManager.findByName(name).orElse(null);
        }
        if (organization == null) {
            context.getSource().sendFailure(Component.literal("组织不存在"));
            return 0;
        }
        StringBuilder message = new StringBuilder("§6").append(organization.name())
                .append("§f（").append(organization.memberCount()).append(" 人）");
        if (!organization.announcement().isBlank()) {
            message.append("\n公告：").append(organization.announcement());
        }
        for (Map.Entry<String, Organization.Member> entry : organization.sortedMembers()) {
            message.append("\n  ").append(entry.getValue().rank().displayName())
                    .append("  ").append(entry.getValue().lastName());
        }
        Organization target = organization;
        context.getSource().sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }

    private interface PlayerAction {
        OrganizationResult run(ServerPlayer player) throws CommandSyntaxException;
    }
}
