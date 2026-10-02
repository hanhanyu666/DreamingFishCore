package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.ContactExposureTracker;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionIdentity;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionRules;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionTreatmentService;
import com.hhy.dreamingfishcore.gameplay.organization_system.SettlementFilterRegistry;
import com.hhy.dreamingfishcore.gameplay.organization_system.SettlementFilterService;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.core.BlockPos;
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
        try {
            PlayerAttributesData stableData = dataOf(stable);
            stableData.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_TWO);
            stableData.setCurrentInfection(200.0F);
            stableData.clearRelapseState();
            PlayerAttributesDataManager.updatePlayerAttributesData(stable, stableData);

            helper.assertTrue(stableData.getInfectionIdentity() == InfectionIdentity.STABLE,
                    "前置条件：该玩家应是稳定感染者");
            helper.assertFalse(InfectionEventHandler.hasSpreadingSourceNearby(survivor),
                    "稳定感染者在正常状态下不应产生接触暴露");
        } finally {
            // 断言失败时也要摘掉这两名模拟玩家：gametest 共用同一个测试世界与玩家列表，
            // 而附近扫描是全服 32 格的（两个模拟玩家又都站在同一个出生点上），
            // 留下来的任何一名玩家都会成为后面用例的"同场玩家"。
            dispose(survivor, stable);
        }
        helper.succeed();
    }

    /** 不稳定感染者会被扫描到：这是"只有不稳定与传播复发产生暴露"的另一半。 */
    @GameTest(template = "empty")
    public static void unstableInfectedExposesNearbySurvivor(GameTestHelper helper) {
        ServerPlayer survivor = authenticatedPlayer(helper);
        ServerPlayer unstable = authenticatedPlayer(helper);
        try {
            PlayerAttributesData unstableData = dataOf(unstable);
            unstableData.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_ONE);
            unstableData.setCurrentInfection(50.0F);
            PlayerAttributesDataManager.updatePlayerAttributesData(unstable, unstableData);

            helper.assertTrue(unstableData.getInfectionIdentity() == InfectionIdentity.UNSTABLE,
                    "前置条件：该玩家应是不稳定感染者");
            helper.assertTrue(InfectionEventHandler.hasSpreadingSourceNearby(survivor),
                    "不稳定感染者应被识别为传播来源");
        } finally {
            // 本测试刻意造了一个"会传播"的感染者，而 gametest 共用同一个测试世界与玩家列表：
            // 不摘掉它的话，后面任何"附近不应该有传播来源"的断言都会扫到这个残留
            // （stableInfectedDoesNotExposeNearbySurvivor 就是这么被弄挂的）。
            // 放在 finally 里，断言失败时也不会把残留留给后面的测试。
            dispose(survivor, unstable);
        }
        helper.succeed();
    }

    /**
     * 把测试造出来的模拟玩家从服务器上摘掉，别让它影响同一批次里的其它 gametest。
     *
     * <p>为什么必须摘：gametest 共用同一个测试世界与 {@code PlayerList}，而
     * {@link InfectionEventHandler#hasSpreadingSourceNearby} 是全服扫描 32 格内的传播来源；
     * 模拟玩家又都由 {@code makeMockServerPlayerInLevel()} 放在同一个出生点上（彼此相距不到 32 格），
     * 所以只要有残留，别的用例就会被误判。</p>
     */
    private static void dispose(ServerPlayer... players) {
        for (ServerPlayer player : players) {
            if (player != null && player.getServer() != null) {
                player.getServer().getPlayerList().remove(player);
            }
        }
    }

    /** 暴露攒满后转化为实际感染增长，并且暴露量清零重新累积。 */
    @GameTest(template = "empty")
    public static void exposureConvertsIntoInfectionAndResets(GameTestHelper helper) {
        ServerPlayer survivor = authenticatedPlayer(helper);
        try {
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
        } finally {
            // 这个用例的结果就是把玩家变成不稳定感染者（会传播），而共用世界里的附近扫描是全服 32 格：
            // 不摘掉的话，后面"附近不应该有传播来源"的断言会扫到它。
            dispose(survivor);
        }
        helper.succeed();
    }

    /** 身份变化由服务端校验，并且写进存档后能按真实加载路径读回来（等价于重启后保留）。 */
    @GameTest(template = "empty")
    public static void treatmentIsServerVerifiedAndSurvivesReload(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
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
        } finally {
            // 这个用例中途把玩家设成过不稳定感染者（会传播），而共用世界里的附近扫描是全服 32 格：
            // 断言失败时最容易留下残留，因此无论如何都从玩家列表里摘掉。
            dispose(player);
        }
        helper.succeed();
    }

    /** 重伤触发可观察、可结束的传播复发；稳定治疗能把它结束掉。 */
    @GameTest(template = "empty")
    public static void heavyDamageTriggersRelapseAndStabilizationEndsIt(GameTestHelper helper) {
        ServerPlayer player = authenticatedPlayer(helper);
        try {
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
        } finally {
            // 这个用例中途是传播复发（会传播），而共用世界里的附近扫描是全服 32 格：
            // 断言在中间失败时它就会以"传播来源"的身份留下来，所以放在 finally 里摘掉。
            dispose(player);
        }
        helper.succeed();
    }

    /**
     * 工作中的聚居地过滤装置会阻断被动接触暴露（ADR 0017）。
     *
     * <p>只验证"设备工作 → 不产生暴露"这条链路：设备本身由 {@link SettlementFilterRegistry}
     * 直接登记为工作中（维护周期、扣费、领地校验由单测与 {@code SettlementFilterService} 覆盖），
     * 这样测试不会被资金/领地前置条件绑住，但仍然走真实的
     * {@link InfectionEventHandler#hasSpreadingSourceNearby} 扫描路径。</p>
     */
    @GameTest(template = "empty")
    public static void workingFilterSuppressesPassiveExposure(GameTestHelper helper) {
        ServerPlayer survivor = authenticatedPlayer(helper);
        ServerPlayer unstable = authenticatedPlayer(helper);
        BlockPos devicePos = helper.absolutePos(new BlockPos(1, 2, 1));
        String dimensionId = helper.getLevel().dimension().location().toString();
        try {
            PlayerAttributesData unstableData = dataOf(unstable);
            unstableData.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_ONE);
            unstableData.setCurrentInfection(50.0F);
            PlayerAttributesDataManager.updatePlayerAttributesData(unstable, unstableData);

            // 两名玩家都站到结构内，紧邻设备位置（半径 32 一定覆盖）。
            survivor.teleportTo(devicePos.getX() + 0.5D, devicePos.getY(), devicePos.getZ() + 0.5D);
            unstable.teleportTo(devicePos.getX() + 1.5D, devicePos.getY(), devicePos.getZ() + 0.5D);

            helper.setBlock(new BlockPos(1, 1, 1),
                    com.hhy.dreamingfishcore.block.DreamingFishCore_Blocks.SETTLEMENT_FILTER.get());
            SettlementFilterRegistry.register(dimensionId,
                    devicePos.getX(), devicePos.getY(), devicePos.getZ());
            SettlementFilterRegistry.bind(dimensionId,
                    devicePos.getX(), devicePos.getY(), devicePos.getZ(), "gametest-org");

            // 未标为工作时不抑制：不稳定感染者就在旁边，应当能被扫到。
            helper.assertFalse(SettlementFilterService.isSuppressed(survivor), "停机设备不应抑制");
            helper.assertTrue(InfectionEventHandler.hasSpreadingSourceNearby(survivor),
                    "停机时不稳定感染者应被扫到");

            SettlementFilterRegistry.updateState(dimensionId,
                    devicePos.getX(), devicePos.getY(), devicePos.getZ(), true, 0L);
            helper.assertTrue(SettlementFilterService.isSuppressed(survivor), "工作设备应覆盖附近的幸存者");
            helper.assertFalse(InfectionEventHandler.hasSpreadingSourceNearby(survivor),
                    "工作中的设备应阻断被动接触暴露");
        } finally {
            // 清理：设备表是全局状态，别影响同一服务器上的其它 gametest；
            // 放在 finally 里，断言失败时也不会留下一台"工作中"的设备去抑制别的用例。
            SettlementFilterRegistry.updateState(dimensionId,
                    devicePos.getX(), devicePos.getY(), devicePos.getZ(), false, 0L);
            SettlementFilterRegistry.unregister(dimensionId,
                    devicePos.getX(), devicePos.getY(), devicePos.getZ());
            // 这个用例造了一名不稳定感染者（会传播），而共用世界里的附近扫描是全服 32 格：
            // 它只有被主动挪进结构里才离得远，断言失败在中间时同样要摘掉。
            dispose(survivor, unstable);
        }
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
