package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.command;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionTreatmentService;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 测试用的感染身份调试命令（仅 3 级权限可用）。
 *
 * <pre>
 * /dreamingfish debug infection set &lt;survivor|unstable|stable|relapse|0|1|2&gt; [感染值]
 * /dreamingfish debug infection stabilize   稳定治疗（不稳定 → 稳定；或结束传播复发）
 * /dreamingfish debug infection relapse     强制进入传播复发
 * /dreamingfish debug infection cure        高成本重构（稳定 → 幸存者）
 * </pre>
 *
 * <p>存在的意义：完整走一遍"幸存者 → 不稳定 → 稳定 → 重构回幸存者"需要真实的感染积累、
 * 一个剧情活动日的等待和一套医院流程，直接用命令可以把状态摆到指定位置。
 * 这里只写感染身份，不推进任何剧情进度，也不发物品。</p>
 *
 * <p>{@code set} 直接写等级与感染值（不走疗程），用于观察某个身份本身；
 * {@code stabilize} / {@code cure} / {@code relapse} 则复用服务端治疗入口，
 * 与真实流程走同一份代码。</p>
 */
public final class Command_InfectionDebug {

    private Command_InfectionDebug() {
    }

    /** 供 {@code /dreamingfish debug} 根节点复用；根节点与权限由命令管理器统一提供。 */
    public static LiteralArgumentBuilder<CommandSourceStack> infectionBranch() {
        return Commands.literal("infection")
                .then(Commands.literal("set")
                        .then(Commands.argument("identity", StringArgumentType.word())
                                .executes(context -> applyInfection(context, -1.0F))
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 1000.0F))
                                        .executes(context -> applyInfection(
                                                context, FloatArgumentType.getFloat(context, "value"))))))
                .then(Commands.literal("stabilize")
                        .executes(context -> runTreatment(context, "stabilize")))
                .then(Commands.literal("relapse")
                        .executes(context -> runTreatment(context, "relapse")))
                .then(Commands.literal("cure")
                        .executes(context -> runTreatment(context, "cure")));
    }

    private static int applyInfection(CommandContext<CommandSourceStack> context, float explicitValue) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException exception) {
            source.sendFailure(Component.literal("该命令只能由玩家执行（需要把感染状态写到某个具体玩家身上）"));
            return 0;
        }

        PlayerAttributesData data = PlayerAttributesDataManager.getPlayerAttributesData(player.getUUID());
        if (data == null) {
            source.sendFailure(Component.literal("无法读取玩家属性数据"));
            return 0;
        }

        String requested = StringArgumentType.getString(context, "identity").toLowerCase(java.util.Locale.ROOT);
        int level = resolveLevel(requested);
        if (level < 0) {
            source.sendFailure(Component.literal(
                    "未知身份：" + requested + "（可用 survivor / unstable / stable / relapse / 0 / 1 / 2）"));
            return 0;
        }

        float value = explicitValue >= 0.0F ? explicitValue : defaultInfectionFor(level);
        if (level == PlayerAttributesData.INFECTION_LEVEL_NONE) {
            value = 0.0F;
        }

        data.setInfectionLevel(level);
        data.setCurrentInfection(value);
        if (level != PlayerAttributesData.INFECTION_LEVEL_ONE) {
            data.clearInfectionTreatmentDeadline();
        }
        if (level != PlayerAttributesData.INFECTION_LEVEL_TWO) {
            data.clearRelapseState();
        }
        PlayerAttributesDataManager.updatePlayerAttributesData(player, data);
        InfectionTreatmentService.syncIdentity(player, data);

        boolean relapseRequested = "relapse".equals(requested);
        boolean relapseApplied = false;
        if (relapseRequested) {
            relapseApplied = InfectionTreatmentService.beginRelapse(player);
        }

        final boolean relapseResult = relapseApplied;
        source.sendSuccess(() -> Component.literal(
                "已将 " + player.getName().getString() + " 的感染身份设为："
                        + data.getInfectionIdentity().displayName()
                        + "，感染值 " + String.format("%.1f", data.getCurrentInfection())
                        + (relapseRequested
                        ? (relapseResult ? "（已进入传播复发）" : "（复发未能生效：活动时钟不可用或已在冷却中）")
                        : "")), true);
        return 1;
    }

    private static int runTreatment(CommandContext<CommandSourceStack> context, String action) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException exception) {
            source.sendFailure(Component.literal("该命令只能由玩家执行"));
            return 0;
        }

        if ("relapse".equals(action)) {
            boolean applied = InfectionTreatmentService.beginRelapse(player);
            if (!applied) {
                source.sendFailure(Component.literal(
                        "未能进入传播复发：只有稳定感染者可以复发，且不能在复发冷却期内重复触发。"));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("已强制进入传播复发。"), true);
            return 1;
        }

        InfectionTreatmentService.TreatmentOutcome outcome = "stabilize".equals(action)
                ? InfectionTreatmentService.applyStabilization(player)
                : InfectionTreatmentService.applyReconstruction(player);

        PlayerAttributesData data = PlayerAttributesDataManager
                .findStoredPlayerAttributesData(player.getUUID());
        String identityText = data == null ? "未知" : data.getInfectionIdentity().displayName();
        if (outcome != InfectionTreatmentService.TreatmentOutcome.APPLIED) {
            source.sendFailure(Component.literal(
                    "治疗未生效：" + describeOutcome(outcome) + "（当前身份：" + identityText + "）"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "治疗已生效，当前身份：" + identityText), true);
        return 1;
    }

    private static String describeOutcome(InfectionTreatmentService.TreatmentOutcome outcome) {
        return switch (outcome) {
            case APPLIED -> "已生效";
            case NOTHING_TO_DO -> "没有需要改变的内容";
            case WRONG_IDENTITY -> "当前身份不适用该疗程";
            case NOT_AUTHENTICATED -> "会话未通过认证";
            case NOT_LOADED -> "玩家档案尚未加载";
        };
    }

    /** 解析身份关键字或旧的 0/1/2 数字；无法识别时返回 -1。 */
    private static int resolveLevel(String requested) {
        return switch (requested) {
            case "survivor", "0" -> PlayerAttributesData.INFECTION_LEVEL_NONE;
            case "unstable", "1" -> PlayerAttributesData.INFECTION_LEVEL_ONE;
            case "stable", "2", "relapse" -> PlayerAttributesData.INFECTION_LEVEL_TWO;
            default -> -1;
        };
    }

    private static float defaultInfectionFor(int level) {
        return switch (level) {
            case PlayerAttributesData.INFECTION_LEVEL_TWO -> 150.0F;
            case PlayerAttributesData.INFECTION_LEVEL_ONE -> 50.0F;
            default -> 0.0F;
        };
    }
}
