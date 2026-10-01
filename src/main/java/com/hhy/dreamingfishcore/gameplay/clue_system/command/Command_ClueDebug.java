package com.hhy.dreamingfishcore.gameplay.clue_system.command;

import com.hhy.dreamingfishcore.gameplay.clue_system.ClueGuaranteeService;
import com.hhy.dreamingfishcore.gameplay.storybook_system.FragmentData;
import com.hhy.dreamingfishcore.gameplay.storybook_system.StoryBookDataManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 线索系统的测试命令（仅 3 级权限可用）。
 *
 * <pre>
 * /dreamingfish debug clue list
 * /dreamingfish debug clue grant &lt;编号&gt;            给自己
 * /dreamingfish debug clue grant &lt;编号&gt; &lt;玩家&gt;     给指定玩家
 * </pre>
 *
 * <p>保底发放本身是幂等的（已收录、或背包里已经有一张同编号残页时都不发），所以这条命令
 * 连敲两次只会得到一张残页——正好用来验证幂等，也可以给"在保底实装之前就完成了剧情"的玩家
 * 手动补页。</p>
 */
public final class Command_ClueDebug {

    private Command_ClueDebug() {
    }

    /** 供 {@code /dreamingfish debug} 根节点复用；根节点与权限由命令管理器统一提供。 */
    public static LiteralArgumentBuilder<CommandSourceStack> clueBranch() {
        return Commands.literal("clue")
                .then(Commands.literal("list").executes(Command_ClueDebug::list))
                .then(Commands.literal("grant")
                        .then(Commands.argument("fragmentId", IntegerArgumentType.integer(1))
                                .executes(context -> grant(
                                        context,
                                        context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "fragmentId")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> grant(
                                                context,
                                                EntityArgument.getPlayer(context, "player"),
                                                IntegerArgumentType.getInteger(context, "fragmentId"))))));
    }

    /** 列出当前加载的线索池，顺便让执行者看清编号。 */
    private static int list(CommandContext<CommandSourceStack> context) {
        Map<Integer, FragmentData> fragments = StoryBookDataManager.getAllFragments();
        if (fragments.isEmpty()) {
            context.getSource().sendFailure(Component.literal("当前没有加载任何线索，请先检查 fragment_data.json"));
            return 0;
        }

        List<Integer> ids = new ArrayList<>(fragments.keySet());
        Collections.sort(ids);
        StringBuilder builder = new StringBuilder("线索池（共 " + ids.size() + " 条）：");
        for (Integer id : ids) {
            FragmentData fragment = fragments.get(id);
            builder.append("\n  ").append(id).append("  ").append(fragment.getTitle());
        }
        String message = builder.toString();
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int grant(
            CommandContext<CommandSourceStack> context, ServerPlayer target, int fragmentId) {
        CommandSourceStack source = context.getSource();
        FragmentData fragment = StoryBookDataManager.getFragment(fragmentId);
        if (fragment == null) {
            source.sendFailure(Component.literal(
                    "编号 " + fragmentId + " 不在当前线索池里，用 /dreamingfish debug clue list 查看现有编号"));
            return 0;
        }

        if (!ClueGuaranteeService.grant(target, fragmentId)) {
            source.sendSuccess(() -> Component.literal("没有发放：" + target.getScoreboardName()
                    + " 已经收录这条线索、背包里已经有一张同编号残页，或随记本数据尚未加载"), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("已向 " + target.getScoreboardName()
                + " 发放线索 " + fragmentId + "（" + fragment.getTitle() + "），需要自己右键整理"), true);
        return 1;
    }
}
