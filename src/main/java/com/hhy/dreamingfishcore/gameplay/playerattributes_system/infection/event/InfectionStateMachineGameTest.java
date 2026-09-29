package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.ContactExposureTracker;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionIdentity;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionRules;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionTreatmentService;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 感染身份状态机的端到端 gametest（无头专用服务器即可运行，不需要客户端与人工操作）。
 *
 * <p>为什么需要它：路线图里程碑 1 的验收里有几条单测够不到——"稳定感染者与幸存者正常相处
 * 不会持续感染队友"（要两名真实服务端玩家同场）、"接触暴露攒满后转化为实际感染"
 * （要真实玩家写档案）、"身份变化重启后保留"（要走真实的落盘与重载）。这些都在这里用
 * {@link GameTestHelper#makeMockServerPlayerInLevel()} 造出的真实服务端玩家验证。</p>
 *
 * <p>本类放在 {@code infection.event} 包，是因为它要直接调用包内可见的
 * {@link InfectionEventHandler#hasSpreadingSourceNearby}，用真实的附近玩家扫描来验证共存规则，
 * 而不是复述一遍判定谓词。</p>
 */
@GameTestHolder(DreamingFishCore.MODID)
@PrefixGameTestTemplate(false)
public class InfectionStateMachineGameTest {

    /** 稳定感染者在正常状态下不产生接触暴露：幸存者贴着他站着也扫不到传播来源。 */
    @GameTest(template = "empty")
    public static void stableInfectedDoesNotExposeNearbySurvivor(GameTestHelper helper) {
        ServerPlayer survivor = authenticatedPlayer(helper);
        ServerPlayer stable = authenticatedPlayer(helper);

        PlayerAttributesData stableData = dataOf(stable);
        stableData.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_TWO);
        stableData.setCurrentInfection(200.0F);
        stableData.clearRelapseState();
        PlayerAttributesDataManager.updatePlayerAttributesData(stable, stableData);

        helper.assertTrue(stableData.getInfectionIdentity() == InfectionIdentity.STABLE,
                "前置条件：该玩家应是稳定感染者");
        helper.assertFalse(InfectionEventHandler.hasSpreadingSourceNearby(survivor),
                "稳定感染者在正常状态下不应产生接触暴露");
        helper.succeed();
    }

    /** 不稳定感染者会被扫描到：这是"只有不稳定与传播复发产生暴露"的另一半。 */
    @GameTest(template = "empty")
    public static void unstableInfectedExposesNearbySurvivor(GameTestHelper helper) {
        ServerPlayer survivor = authenticatedPlayer(helper);
        ServerPlayer unstable = authenticatedPlayer(helper);

        PlayerAttributesData unstableData = dataOf(unstable);
        unstableData.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_ONE);
        unstableData.setCurrentInfection(50.0F);
        PlayerAttributesDataManager.updatePlayerAttributesData(unstable, unstableData);

        helper.assertTrue(unstableData.getInfectionIdentity() == InfectionIdentity.UNSTABLE,
                "前置条件：该玩家应是不稳定感染者");
        helper.assertTrue(InfectionEventHandler.hasSpreadingSourceNearby(survivor),
                "不稳定感染者应被识别为传播来源");
        helper.succeed();
    }

    /** 暴露攒满后转化为实际感染增长，并且暴露量清零重新累积。 */
    @GameTest(template = "empty")
    public static void exposureConvertsIntoInfectionAndResets(GameTestHelper helper) {
        ServerPlayer survivor = authenticatedPlayer(helper);
        PlayerAttributesData data = dataOf(survivor);
        data.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_NONE);
        data.setCurrentInfection(0.0F);
        PlayerAttributesDataManager.updatePlayerAttributesData(survivor, data);

        float charge = InfectionEventHandler.debugAdvanceExposure(
                survivor, (int) InfectionRules.EXPOSURE_THRESHOLD);

        helper.assertTrue(charge == 0.0F, "转化之后暴露量应清零，实际为 " + charge);
        helper.assertTrue(
                data.getCurrentInfection() >= InfectionRules.EXPOSURE_CONVERSION_INFECTION - 0.01F,
                "一次完整的接触暴露应转化为 +" + InfectionRules.EXPOSURE_CONVERSION_INFECTION
                        + " 感染值，实际为 " + data.getCurrentInfection());
        helper.succeed();
    }

    /** 身份变化由服务端校验，并且写进存档后能按真实加载路径读回来（等价于重启后保留）。 */
    @GameTest(template = "empty")
    public static void treatmentIsServerVerifiedAndSurvivesReload(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerAttributesData data = dataOf(player);

        // 未认证的会话不允许改写身份。
        AuthSessionGuard.invalidate(player);
        helper.assertTrue(
                InfectionTreatmentService.applyStabilization(player)
                        == InfectionTreatmentService.TreatmentOutcome.NOT_AUTHENTICATED,
                "未认证会话必须被拒绝");

        AuthSessionGuard.markAuthenticated(player);
        data.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_ONE);
        data.setCurrentInfection(50.0F);
        PlayerAttributesDataManager.updatePlayerAttributesData(player, data);

        helper.assertTrue(
                InfectionTreatmentService.applyStabilization(player)
                        == InfectionTreatmentService.TreatmentOutcome.APPLIED,
                "稳定治疗应作用于不稳定感染者");

        java.util.UUID playerId = player.getUUID();
        helper.assertTrue(
                PlayerAttributesDataManager.findStoredPlayerAttributesData(playerId)
                        .getInfectionIdentity() == InfectionIdentity.STABLE,
                "稳定治疗之后内存里的身份应为稳定感染者");

        // 走真实的落盘 + 重新加载路径：这一对调用就是服务器重启时的加载过程。
        PlayerAttributesDataManager.saveIfDirty(helper.getLevel().getServer());
        PlayerAttributesDataManager.clearWorldCache();
        PlayerAttributesDataManager.loadWorldData(helper.getLevel().getServer());

        PlayerAttributesData reloaded = PlayerAttributesDataManager.findStoredPlayerAttributesData(playerId);
        helper.assertTrue(reloaded != null, "重载后仍应能读到该玩家的档案");
        helper.assertTrue(reloaded.getInfectionIdentity() == InfectionIdentity.STABLE,
                "重载后身份应保持稳定感染者，实际为 " + reloaded.getInfectionIdentity().displayName());
        helper.succeed();
    }

    /** 重伤触发可观察、可结束的传播复发；稳定治疗能把它结束掉。 */
    @GameTest(template = "empty")
    public static void heavyDamageTriggersRelapseAndStabilizationEndsIt(GameTestHelper helper) {
        ServerPlayer player = authenticatedPlayer(helper);
        PlayerAttributesData data = dataOf(player);
        data.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_TWO);
        data.setCurrentInfection(200.0F);
        data.clearRelapseState();
        PlayerAttributesDataManager.updatePlayerAttributesData(player, data);

        helper.assertTrue(data.getInfectionIdentity() == InfectionIdentity.STABLE,
                "前置条件：该玩家应是稳定感染者");

        // 先确认两件前置事实，避免"复发没触发"被归错因：活动时钟可用、且伤害真的落地了。
        long activeTick = InfectionTreatmentService.currentActiveTick();
        helper.assertTrue(activeTick >= 0L, "传播复发依赖活动时钟，当前不可用：" + activeTick);

        // 一次超过阈值的真实伤害：事件链会经过 InfectionEventHandler 的复发判定。
        // 用 genericKill 是因为模拟玩家刚加入时还带着服务端的出生无敌时间（该字段是 private，
        // 测试改不了），而真实玩家在出生 3 秒后就会正常受伤；绕过无敌不会绕过伤害事件本身，
        // 因此这里验证的仍然是"伤害 → 复发判定"这一段。
        float healthBefore = player.getHealth();
        player.invulnerableTime = 0;
        boolean hurt = player.hurt(helper.getLevel().damageSources().genericKill(),
                InfectionRules.RELAPSE_DAMAGE_THRESHOLD + 4.0F);
        helper.assertTrue(hurt && player.getHealth() < healthBefore,
                "前置条件：伤害应真实生效（hurt=" + hurt + "，生命 "
                        + healthBefore + " -> " + player.getHealth() + "）");

        helper.assertTrue(data.hasActiveRelapseWindow(),
                "单次实际损失超过阈值应触发传播复发");
        helper.assertTrue(data.getInfectionIdentity() == InfectionIdentity.RELAPSE,
                "身份应变为传播复发，实际为 " + data.getInfectionIdentity().displayName());

        helper.assertTrue(
                InfectionTreatmentService.applyStabilization(player)
                        == InfectionTreatmentService.TreatmentOutcome.APPLIED,
                "稳定治疗应能作用于传播复发");
        helper.assertFalse(data.hasActiveRelapseWindow(), "稳定治疗之后不应再处于复发状态");
        helper.assertTrue(data.getInfectionIdentity() == InfectionIdentity.STABLE,
                "复发结束后应回到稳定感染者");
        helper.succeed();
    }

    private static ServerPlayer authenticatedPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        AuthSessionGuard.markAuthenticated(player);
        // 模拟玩家默认不具备可受伤的生存状态；伤害相关的验证（重伤触发复发）需要它是生存模式，
        // 否则 hurt() 会因为无敌判定直接返回 false，让测试看起来像"规则没生效"。
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().invulnerable = false;
        player.setInvulnerable(false);
        return player;
    }

    private static PlayerAttributesData dataOf(ServerPlayer player) {
        PlayerAttributesData data = PlayerAttributesDataManager.getPlayerAttributesData(player.getUUID());
        if (data == null) {
            throw new IllegalStateException("gametest 无法取得玩家属性档案");
        }
        return data;
    }
}
