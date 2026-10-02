package com.hhy.dreamingfishcore.gameplay.blueprint_system.command;

import com.hhy.dreamingfishcore.gameplay.blueprint_system.BlueprintConfig;
import com.hhy.dreamingfishcore.gameplay.blueprint_system.PlayerBlueprintData;
import com.hhy.dreamingfishcore.item.items.Item_Blueprint;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 合成蓝图的运维命令（权限跟随 {@code /dreamingfish} 根节点的 2 级）。
 *
 * <pre>
 * /dreamingfish blueprint info                     总开关、抽取池与各名单的规模
 * /dreamingfish blueprint pool [页码]              列出抽取池（分页，每页 20）
 * /dreamingfish blueprint reload                   重载 blueprint.json 并重建抽取池
 * /dreamingfish blueprint give &lt;物品ID&gt; [玩家]     发一张解锁该物品的蓝图
 * /dreamingfish blueprint learn &lt;物品ID&gt; [玩家]    直接学会（调试用）
 * /dreamingfish blueprint list [玩家]              查看某人已学会的蓝图
 * /dreamingfish blueprint reset [玩家]             清空某人的蓝图进度
 * </pre>
 *
 * <p>没有 {@code reload} 的话，服主改完白/黑名单必须重启服务器；抽取池是懒重建的，
 * 重载后第一次访问就会用新配置重算。</p>
 */
public final class Command_Blueprint {

    private static final int PAGE_SIZE = 20;

    private Command_Blueprint() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("blueprint")
                .then(Commands.literal("info").executes(Command_Blueprint::info))
                .then(Commands.literal("pool")
                        .executes(context -> pool(context, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> pool(context, IntegerArgumentType.getInteger(context, "page")))))
                .then(Commands.literal("reload").executes(Command_Blueprint::reload))
                .then(itemBranch("give", Command_Blueprint::give))
                .then(itemBranch("learn", Command_Blueprint::learn))
                .then(targetBranch("list", Command_Blueprint::list))
                .then(targetBranch("reset", Command_Blueprint::reset)));
    }

    /** {@code <物品ID> [玩家]} 形状的分支。 */
    private static LiteralArgumentBuilder<CommandSourceStack> itemBranch(
            String name, ItemAction action) {
        return Commands.literal(name)
                .then(Commands.argument("item", ResourceLocationArgument.id())
                        .executes(context -> action.run(context, null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> action.run(context, EntityArgument.getPlayer(context, "player")))));
    }

    /** {@code [玩家]} 形状的分支。 */
    private static LiteralArgumentBuilder<CommandSourceStack> targetBranch(
            String name, TargetAction action) {
        return Commands.literal(name)
                .executes(context -> action.run(context, null))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> action.run(context, EntityArgument.getPlayer(context, "player"))));
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        BlueprintConfig config = BlueprintConfig.current();
        List<String> pool = PlayerBlueprintData.getBlueprintPool();
        List<String> lines = new ArrayList<>();
        lines.add("合成蓝图：" + (config.isEnabled() ? "§a已启用" : "§c未启用（所有配方照常可合成）"));
        lines.add("工作台配方 " + PlayerBlueprintData.getRecipeOutputs().size()
                + " 条 → 抽取池 " + pool.size() + " 个物品");
        lines.add("默认放行 " + config.getDefaultUnlockedItems().size() + " 条规则；"
                + "赦免命名空间 " + config.getExemptNamespaces().size() + " 个；"
                + "白名单 " + config.getBlueprintWhitelist().size() + " 条；"
                + "黑名单 " + config.getBlueprintBlacklist().size() + " 条");
        lines.add("丧尸掉落 " + formatPercent(config.getSiegeZombieDropPercent())
                + "；宝箱掉落 " + formatPercent(config.getChestDropPercent()));
        String message = String.join("\n", lines);
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int pool(CommandContext<CommandSourceStack> context, int page) {
        List<String> pool = PlayerBlueprintData.getBlueprintPool();
        if (pool.isEmpty()) {
            context.getSource().sendFailure(Component.literal(
                    PlayerBlueprintData.getRecipeOutputs().isEmpty()
                            ? "抽取池为空：还没有收集到工作台配方"
                            : "抽取池为空：配方都被默认放行 / 赦免命名空间 / 黑名单挡掉了"));
            return 0;
        }
        int totalPages = (pool.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int current = Math.min(page, totalPages);
        int from = (current - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, pool.size());

        List<String> lines = new ArrayList<>();
        lines.add("蓝图抽取池（" + pool.size() + " 个，第 " + current + "/" + totalPages + " 页）");
        for (int i = from; i < to; i++) {
            lines.add("  " + pool.get(i));
        }
        String message = String.join("\n", lines);
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        BlueprintConfig.reload();
        PlayerBlueprintData.markPoolDirty();
        PlayerBlueprintData.logPoolDiagnostics();
        List<String> pool = PlayerBlueprintData.getBlueprintPool();
        String message = "蓝图配置已重载："
                + (BlueprintConfig.current().isEnabled() ? "已启用" : "未启用")
                + "，抽取池 " + pool.size() + " 个物品（详情见服务器日志）";
        context.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> context, ServerPlayer target)
            throws CommandSyntaxException {
        ServerPlayer receiver = target != null ? target : context.getSource().getPlayerOrException();
        ResourceLocation itemId = ResourceLocationArgument.getId(context, "item");
        if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
            context.getSource().sendFailure(Component.literal("不存在的物品：" + itemId));
            return 0;
        }

        ItemStack blueprint = Item_Blueprint.createBlueprint(itemId.toString());
        if (!receiver.getInventory().add(blueprint)) {
            receiver.drop(blueprint, false);
        }
        String itemName = new ItemStack(BuiltInRegistries.ITEM.get(itemId)).getHoverName().getString();
        String message = "已给 " + receiver.getScoreboardName() + " 一张「" + itemName + "」的蓝图";
        context.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int learn(CommandContext<CommandSourceStack> context, ServerPlayer target)
            throws CommandSyntaxException {
        ServerPlayer receiver = target != null ? target : context.getSource().getPlayerOrException();
        ResourceLocation itemId = ResourceLocationArgument.getId(context, "item");
        if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
            context.getSource().sendFailure(Component.literal("不存在的物品：" + itemId));
            return 0;
        }

        String id = itemId.toString();
        if (PlayerBlueprintData.hasLearned(receiver, id)) {
            context.getSource().sendFailure(Component.literal(
                    receiver.getScoreboardName() + " 已经学会「" + id + "」了"));
            return 0;
        }
        PlayerBlueprintData.unlockItem(receiver, id);
        String message = receiver.getScoreboardName() + " 学会了「" + id + "」";
        context.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context, ServerPlayer target)
            throws CommandSyntaxException {
        ServerPlayer receiver = target != null ? target : context.getSource().getPlayerOrException();
        Set<String> learned = PlayerBlueprintData.getLearnedBlueprintItems(receiver);
        if (learned.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal(
                    receiver.getScoreboardName() + " 还没有学会任何蓝图"), false);
            return 1;
        }
        List<String> lines = new ArrayList<>();
        lines.add(receiver.getScoreboardName() + " 已学会 " + learned.size() + " 条蓝图：");
        for (String itemId : learned) {
            lines.add("  " + itemId);
        }
        String message = String.join("\n", lines);
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context, ServerPlayer target)
            throws CommandSyntaxException {
        ServerPlayer receiver = target != null ? target : context.getSource().getPlayerOrException();
        int before = PlayerBlueprintData.getLearnedBlueprintCount(receiver);
        PlayerBlueprintData.clearAllUnlocks(receiver);
        String message = "已清空 " + receiver.getScoreboardName() + " 的蓝图进度（原有 " + before + " 条）";
        context.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static String formatPercent(double percent) {
        return String.format(java.util.Locale.ROOT, "%.4f%%", percent);
    }

    @FunctionalInterface
    private interface ItemAction {
        int run(CommandContext<CommandSourceStack> context, ServerPlayer target) throws CommandSyntaxException;
    }

    @FunctionalInterface
    private interface TargetAction {
        int run(CommandContext<CommandSourceStack> context, ServerPlayer target) throws CommandSyntaxException;
    }
}
