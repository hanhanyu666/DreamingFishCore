package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.command;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.PlayerInfectionClientSync;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 测试用的感染状态调试命令（仅 3 级权限可用）。
 *
 * <pre>
 * /dreamingfish debug infection set &lt;0|1|2&gt; [感染值]
 * </pre>
 *
 * <p>存在的意义：二次感染的三次疗程需要先成为二级感染者，而正常流程要先被感染、
 * 再等一个剧情活动日恶化，测试成本过高。这个命令只用于服务器自测，
 * 不参与任何剧情判定，也不会写入剧情进度。</p>
 *
 * <p>省略感染值时按等级填入默认值：0 → 0，1 → 50，2 → 150（二级在面具前上限为 100，
 * 因此默认值会再按当前上限收敛）。</p>
 */
public final class Command_InfectionDebug {

    private Command_InfectionDebug() {
    }

    /** 供 {@code /dreamingfish debug} 根节点复用；根节点与权限由命令管理器统一提供。 */
    public static LiteralArgumentBuilder<CommandSourceStack> infectionBranch() {
        return Commands.literal("infection")
                .then(Commands.literal("set")
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 2))
                                .executes(context -> applyInfection(context, -1.0F))
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 1000.0F))
                                        .executes(context -> applyInfection(
                                                context, FloatArgumentType.getFloat(context, "value"))))));
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

        int level = IntegerArgumentType.getInteger(context, "level");
        float resolvedValue = explicitValue >= 0.0F ? explicitValue : defaultInfectionFor(level);
        final float value = level == PlayerAttributesData.INFECTION_LEVEL_NONE ? 0.0F : resolvedValue;

        data.setInfectionLevel(level);
        data.setCurrentInfection(value);
        if (level == PlayerAttributesData.INFECTION_LEVEL_NONE) {
            data.clearInfectionTreatmentDeadline();
        }
        PlayerAttributesDataManager.updatePlayerAttributesData(player, data);
        PlayerInfectionClientSync.sendInfectionDataToClient(
                player, Math.round(value), level > PlayerAttributesData.INFECTION_LEVEL_NONE, level);

        source.sendSuccess(() -> Component.literal(
                "已将 " + player.getName().getString() + " 的感染状态设为：等级 " + level
                        + "，感染值 " + value), true);
        return 1;
    }

    private static float defaultInfectionFor(int level) {
        return switch (level) {
            case PlayerAttributesData.INFECTION_LEVEL_TWO -> 150.0F;
            case PlayerAttributesData.INFECTION_LEVEL_ONE -> 50.0F;
            default -> 0.0F;
        };
    }
}
