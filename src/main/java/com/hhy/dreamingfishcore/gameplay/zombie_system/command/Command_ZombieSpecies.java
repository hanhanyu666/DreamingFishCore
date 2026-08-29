package com.hhy.dreamingfishcore.gameplay.zombie_system.command;

import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieSpeciesConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Management commands for inspecting and hot-reloading the story-gated zombie AI. */
public final class Command_ZombieSpecies {
    private Command_ZombieSpecies() {
    }

    /**
     * Registers:
     * <pre>
     * /dreamingfish zombie status
     * /dreamingfish zombie reload
     * /dreamingfish zombie set &lt;ability&gt; &lt;true|false&gt;
     * </pre>
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("dreamingfish")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("zombie")
                                .then(Commands.literal("status")
                                        .executes(context -> showStatus(context.getSource())))
                                .then(Commands.literal("reload")
                                        .requires(source -> source.hasPermission(3))
                                        .executes(context -> reload(context.getSource())))
                                .then(Commands.literal("set")
                                        .requires(source -> source.hasPermission(3))
                                        .then(abilityToggle("digging", ZombieSpeciesConfig.Ability.DIGGING))
                                        .then(abilityToggle("open_doors", ZombieSpeciesConfig.Ability.OPEN_DOORS))
                                        .then(abilityToggle("breaking_doors", ZombieSpeciesConfig.Ability.BREAKING_DOORS))
                                        .then(abilityToggle("placing_blocks", ZombieSpeciesConfig.Ability.PLACING_BLOCKS))
                                        .then(abilityToggle("stacking", ZombieSpeciesConfig.Ability.STACKING))
                                        .then(abilityToggle("hearing", ZombieSpeciesConfig.Ability.HEARING))
                                        .then(abilityToggle("broadcasting", ZombieSpeciesConfig.Ability.BROADCASTING))
                                        .then(abilityToggle("surrounding", ZombieSpeciesConfig.Ability.SURROUNDING)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> abilityToggle(
            String commandName,
            ZombieSpeciesConfig.Ability ability) {
        return Commands.literal(commandName)
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> setAbility(
                                context.getSource(),
                                ability,
                                BoolArgumentType.getBool(context, "enabled"))));
    }

    private static int showStatus(CommandSourceStack source) {
        ZombieSpeciesConfig config = ZombieSpeciesConfig.current();
        String stage = StoryManager.getCurrentStageIdOrDefault();
        ZombieSpeciesConfig.ResolvedSettings settings = config.resolveForStage(stage);
        String message = "丧尸状态"
                + "\n- 当前故事阶段: " + stage
                + "\n- 总开关: " + settings.enabled()
                + "\n- 速度倍率: " + settings.speedMultiplier()
                + "\n- 挖掘: " + settings.digging()
                + "\n- 开门: " + settings.openDoors()
                + "\n- 破门: " + settings.breakingDoors()
                + "\n- 放置方块: " + settings.placingBlocks()
                + "\n- 堆人墙: " + settings.stacking()
                + "（最低目标高度差: " + settings.stackMinimumTargetHeight() + "）"
                + "\n- 移动声追踪: " + settings.hearing()
                + "（隐藏目标重定位距离: " + settings.alertRetargetDistance() + "）"
                + "\n- 突破口承诺: " + settings.breachCommitmentTicks() + " tick"
                + "\n- 目标广播: " + settings.broadcasting()
                + "\n- 分流包围: " + settings.surrounding()
                + "（半径/触发距离/引导强度: " + settings.surroundRadius()
                + "/" + settings.surroundActivationRange()
                + "/" + settings.surroundSteeringStrength() + "）"
                + "\n- 索敌距离: " + settings.trackingRange()
                + "\n- 听觉距离: " + settings.hearingRange()
                + "\n- 广播半径/跳数/上限: " + settings.broadcastRange()
                + "/" + settings.broadcastMaxHops()
                + "/" + settings.broadcastMaxRecipients()
                + "\n- 自然生成: " + settings.naturalSpawn()
                + "（原版僵尸/丧尸/其他怪物: "
                + settings.vanillaZombieSpawnPercent() + "%/"
                + settings.customZombieSpawnPercent() + "%/"
                + settings.otherMonsterSpawnPercent() + "%）"
                + "\n- 配置文件: " + ZombieSpeciesConfig.getConfigPath().toAbsolutePath();
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        try {
            ZombieSpeciesConfig.reload();
            source.sendSuccess(
                    () -> Component.literal("丧尸配置已重载；在线实体将在下一次 AI 刷新时应用当前故事阶段设置"),
                    true);
            return 1;
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal("丧尸配置重载失败：" + exception.getMessage()));
            return 0;
        }
    }

    private static int setAbility(
            CommandSourceStack source,
            ZombieSpeciesConfig.Ability ability,
            boolean enabled) {
        String stage = StoryManager.getCurrentStageIdOrDefault();
        try {
            ZombieSpeciesConfig.setAbilityForStage(stage, ability, enabled);
            source.sendSuccess(
                    () -> Component.literal("已将当前阶段 " + stage + " 的 "
                            + abilityName(ability) + " 设置为 " + enabled
                            + "；在线实体将在下一次 AI 刷新时应用"),
                    true);
            return 1;
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal("保存丧尸能力失败：" + exception.getMessage()));
            return 0;
        }
    }

    private static String abilityName(ZombieSpeciesConfig.Ability ability) {
        return switch (ability) {
            case DIGGING -> "挖掘";
            case OPEN_DOORS -> "开门";
            case BREAKING_DOORS -> "破门";
            case PLACING_BLOCKS -> "放置方块";
            case STACKING -> "堆人墙";
            case HEARING -> "移动声追踪";
            case BROADCASTING -> "目标广播";
            case SURROUNDING -> "分流包围";
        };
    }
}
