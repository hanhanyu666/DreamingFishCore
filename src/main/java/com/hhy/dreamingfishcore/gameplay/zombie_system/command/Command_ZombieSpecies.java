package com.hhy.dreamingfishcore.gameplay.zombie_system.command;

import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieSpeciesConfig;
import com.hhy.dreamingfishcore.gameplay.zombie_system.archer.ArcherZombieConfig;
import com.hhy.dreamingfishcore.gameplay.zombie_system.charred.CharredZombieConfig;
import com.hhy.dreamingfishcore.gameplay.zombie_system.adamant.AdamantZombieConfig;
import com.hhy.dreamingfishcore.gameplay.water_gun_system.WaterGunConfig;
import net.minecraft.world.Difficulty;
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
     * /dreamingfish zombie enable_all
     * /dreamingfish zombie set &lt;ability&gt; &lt;true|false&gt;
     * </pre>
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("dreamingfish")
                .requires(source -> source.hasPermission(2));
        register(root);
        dispatcher.register(root);
    }

    /** 将丧尸子树挂到统一的 /dreamingfish 根节点。 */
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("zombie")
                .then(Commands.literal("status")
                        .executes(context -> showStatus(context.getSource())))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(3))
                        .executes(context -> reload(context.getSource())))
                .then(Commands.literal("enable_all")
                        .requires(source -> source.hasPermission(3))
                        .executes(context -> enableAll(context.getSource())))
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(3))
                        .then(abilityToggle("digging", ZombieSpeciesConfig.Ability.DIGGING))
                        .then(abilityToggle("open_doors", ZombieSpeciesConfig.Ability.OPEN_DOORS))
                        .then(abilityToggle("breaking_doors", ZombieSpeciesConfig.Ability.BREAKING_DOORS))
                        .then(abilityToggle("placing_blocks", ZombieSpeciesConfig.Ability.PLACING_BLOCKS))
                        .then(abilityToggle("stacking", ZombieSpeciesConfig.Ability.STACKING))
                        .then(abilityToggle("hearing", ZombieSpeciesConfig.Ability.HEARING))
                        .then(abilityToggle("broadcasting", ZombieSpeciesConfig.Ability.BROADCASTING))
                        .then(abilityToggle("surrounding", ZombieSpeciesConfig.Ability.SURROUNDING))
                        .then(abilityToggle(
                                "task_location_protection",
                                ZombieSpeciesConfig.Ability.TASK_LOCATION_PROTECTION))
                        .then(abilityToggle(
                                "task_location_regeneration",
                                ZombieSpeciesConfig.Ability.TASK_LOCATION_REGENERATION))
                        .then(abilityToggle(
                                "task_location_spawn_protection",
                                ZombieSpeciesConfig.Ability.TASK_LOCATION_SPAWN_PROTECTION))));
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
                + "\n- 保护区禁止破坏/放置/破门: " + settings.taskLocationProtection()
                + "\n- 保护区生命恢复 I: " + settings.taskLocationRegeneration()
                + "\n- 保护区禁止刷怪: " + settings.taskLocationSpawnProtection()
                + "\n- 索敌距离: " + settings.trackingRange()
                + "\n- 听觉距离: " + settings.hearingRange()
                + "\n- 广播半径/跳数/上限: " + settings.broadcastRange()
                + "/" + settings.broadcastMaxHops()
                + "/" + settings.broadcastMaxRecipients()
                + "\n- 自然生成: " + settings.naturalSpawn()
                + "（僵尸家族总权重 " + settings.zombieFamilySpawnPercent() + "%；"
                + "内部原版/逐光: "
                + settings.vanillaZombieSpawnPercent() + "%/"
                + settings.customZombieSpawnPercent() + "%；其他怪物 "
                + settings.otherMonsterSpawnPercent() + "%）"
                + "\n- 配置文件: " + ZombieSpeciesConfig.getConfigPath().toAbsolutePath();
        ArcherZombieConfig archerConfig = ArcherZombieConfig.current();
        ArcherZombieConfig.Resolved archer = archerConfig.resolve();
        String archerMessage = "\n射手僵尸"
                + "\n- 启用/自然生成: " + archer.enabled() + "/" + archer.naturalSpawn()
                + "（占自定义丧尸权重 " + archer.spawnPercentOfCustomZombie() + "%）"
                + "\n- 生命/移速/索敌: " + archer.maxHealth() + "/" + archer.movementSpeed()
                + "/" + archer.followRange()
                + "\n- 近战交接/期望最小/射程: " + archer.meleeHandOffDistance()
                + "/" + archer.preferredMinDistance() + "/" + archer.rangedAttackRange()
                + "\n- 蓄力/攻击间隔: " + archer.chargeTicks() + "/" + archer.attackIntervalTicks()
                + " tick"
                + "\n- 骨刺伤害/弹速/散布/重力/射程: " + archer.projectileDamage()
                + "/" + archer.projectileSpeed()
                + "/" + archer.projectileInaccuracy()
                + "/" + archer.projectileGravity()
                + "/" + archer.projectileMaxRange()
                + "\n- 难度伤害倍率(和平/简单/普通/困难): "
                + archerConfig.damageMultiplier(Difficulty.PEACEFUL)
                + "/" + archerConfig.damageMultiplier(Difficulty.EASY)
                + "/" + archerConfig.damageMultiplier(Difficulty.NORMAL)
                + "/" + archerConfig.damageMultiplier(Difficulty.HARD)
                + "（实际伤害 = 骨刺伤害 × 倍率）"
                + "\n- 减速: " + archer.slowEnabled()
                + "（" + archer.slowDurationTicks() + " tick, 等级 " + archer.slowAmplifier() + "）"
                + "\n- 流血: " + archer.bleedEnabled()
                + "（" + archer.bleedDurationTicks() + " tick, 等级 " + archer.bleedAmplifier()
                + "，玩家需在移动中才结算）"
                + "\n- 射手配置文件: " + ArcherZombieConfig.getConfigPath().toAbsolutePath();
        CharredZombieConfig charredConfig = CharredZombieConfig.current();
        CharredZombieConfig.Resolved charred = charredConfig.resolve();
        String charredMessage = "\n焦尸"
                + "\n- 启用: " + charred.enabled()
                + "（自然生成：未接入，当前仅刷怪蛋与 /summon）"
                + "\n- 生命/移速/索敌: " + charred.maxHealth() + "/" + charred.movementSpeed()
                + "/" + charred.followRange()
                + "\n- 只受火焰类伤害: 是"
                + "（额外算作火的伤害类型: " + charred.extraFireDamageTypes().size() + " 项；"
                + "始终有效的兜底类型: " + charred.alwaysEffectiveDamageTypes().size() + " 项）"
                + "\n- 易燃体质: 火伤 ×" + charred.fireDamageMultiplier()
                + "；着火增强 " + charred.burningBoostEnabled()
                + "（移速 +" + charred.burningSpeedBonus() * 100.0D + "%，攻击 +"
                + charred.burningAttackBonus() + "）"
                + "\n- 易损: " + charred.vulnerableEnabled()
                + "（" + charred.vulnerableDurationTicks() + " tick, 等级 " + charred.vulnerableAmplifier()
                + "，每级 +" + charred.vulnerableDamageBonusPerLevel() * 100.0D + "% 受伤"
                + "，发光 " + charred.vulnerableGlowing() + "）"
                + "\n- 白天自燃/下水腐化: " + charred.burnInDaylight() + "/" + charred.convertInWater()
                + "\n- 免疫反馈: " + charred.immuneFeedbackEnabled()
                + "\n- 焦尸配置文件: " + CharredZombieConfig.getConfigPath().toAbsolutePath();
        AdamantZombieConfig adamantConfig = AdamantZombieConfig.current();
        AdamantZombieConfig.Resolved adamant = adamantConfig.resolve();
        String adamantMessage = "\n金刚僵尸"
                + "\n- 启用: " + adamant.enabled()
                + "（自然生成：未接入，当前仅刷怪蛋与 /summon）"
                + "\n- 生命/移速/索敌/攻击: " + adamant.maxHealth() + "/" + adamant.movementSpeed()
                + "/" + adamant.followRange() + "/" + adamant.attackDamage()
                + "\n- 击退抗性: " + adamant.knockbackResistance()
                + "\n- 未生锈: 免疫伤害" + "（兜底类型 " + adamant.alwaysEffectiveDamageTypes().size() + " 项）"
                + "；攻击者硬直 " + adamant.staggerEnabled()
                + "（" + adamant.staggerDurationTicks() + " tick）"
                + "\n- 锈级上限: " + adamant.maxRustStages()
                + "（水柱每次 +" + adamant.rustPerWaterHit() + " 层；每层受伤 +"
                + adamant.damageBonusPerStage() * 100.0D + "%）"
                + "\n- 潮湿生锈: " + adamant.rustInWaterOrRain()
                + "（每 " + adamant.rustIntervalTicks() + " tick 一层）"
                + "\n- 白天自燃/下水腐化: " + adamant.burnInDaylight() + "/" + adamant.convertInWater()
                + "\n- 金刚僵尸配置文件: " + AdamantZombieConfig.getConfigPath().toAbsolutePath();
        WaterGunConfig.Resolved waterGun = WaterGunConfig.current().resolve();
        String waterGunMessage = "\n呲水枪"
                + "\n- 启用: " + waterGun.enabled()
                + "；容量 " + waterGun.capacity() + " 发；间隔 " + waterGun.fireIntervalTicks() + " tick"
                + "\n- 水柱弹速/下坠/射程: " + waterGun.jetSpeed()
                + "/" + waterGun.jetGravity()
                + "/" + waterGun.jetMaxRange()
                + "\n- 每次命中涨锈: " + waterGun.rustStagesPerHit() + " 层"
                + "\n- 灭火(生物/方块): " + waterGun.extinguishEntities() + "/" + waterGun.extinguishBlockFire()
                + "\n- 装水: " + (waterGun.refillToFull() ? "一次装满" : "每次 +" + waterGun.refillAmount())
                + "；消耗炼药锅 " + waterGun.drainWaterCauldron()
                + "\n- 呲水枪配置文件: " + WaterGunConfig.getConfigPath().toAbsolutePath();
        String statusMessage = message + archerMessage + charredMessage + adamantMessage + waterGunMessage;
        source.sendSuccess(() -> Component.literal(statusMessage), false);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        try {
            ZombieSpeciesConfig.reload();
            ArcherZombieConfig.reload();
            CharredZombieConfig.reload();
            AdamantZombieConfig.reload();
            WaterGunConfig.reload();
            source.sendSuccess(
                    () -> Component.literal("配置已重载（围攻僵尸 + 射手僵尸 + 焦尸 + 金刚僵尸 + 呲水枪）；"
                            + "在线实体将在下一次 AI 刷新时应用当前设置"),
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

    private static int enableAll(CommandSourceStack source) {
        String stage = StoryManager.getCurrentStageIdOrDefault();
        try {
            ZombieSpeciesConfig.setAllAbilitiesForStage(stage, true);
            source.sendSuccess(
                    () -> Component.literal("已将当前阶段 " + stage
                            + " 的全部丧尸能力开启；在线实体将在下一次 AI 刷新时应用"),
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
            case TASK_LOCATION_PROTECTION -> "保护区禁止破坏";
            case TASK_LOCATION_REGENERATION -> "保护区生命恢复";
            case TASK_LOCATION_SPAWN_PROTECTION -> "保护区禁止刷怪";
        };
    }
}
