package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 丧尸仇恨软身份差异的端到端 gametest（ADR 0016）。
 *
 * <p>纯规则已经由 {@code AggroPreferenceRulesTest} 覆盖；这里验证的是<b>接线</b>：
 * 真实丧尸调用 {@code setTarget} 时，{@code LivingChangeTargetEvent} 是否真的把目标改到了
 * 更近的幸存者身上。这一层只靠单测证明不了，而真人多人实测的成本很高（要同时凑齐
 * 稳定感染者、幸存者和一只丧尸）。</p>
 *
 * <p>位置安排（都在结构内的石台上，距离可比）：丧尸在 (0,1,0)，幸存者在 (1,1,0) —— 距离 1；
 * 稳定感染者在 (1,1,1) —— 距离约 1.41。幸存者更近，因此仇恨应当转移过去。</p>
 */
@GameTestHolder(DreamingFishCore.MODID)
@PrefixGameTestTemplate(false)
public class InfectionAggroGameTest {

    /** 稳定感染者被选为目标时，丧尸应当转向更近的幸存者。 */
    @GameTest(template = "empty")
    public static void zombiePrefersNearbySurvivorOverStableInfected(GameTestHelper helper) {
        ServerPlayer survivor = authenticatedPlayer(helper);
        ServerPlayer stable = authenticatedPlayer(helper);
        Zombie zombie = null;
        try {
            setIdentity(stable, PlayerAttributesData.INFECTION_LEVEL_TWO);

            zombie = spawnZombieWithTwoPlayers(helper, survivor, stable);

            zombie.setTarget(stable);

            helper.assertTrue(zombie.getTarget() == survivor,
                    "稳定感染者被选为目标时应转给更近的幸存者，实际目标是 "
                            + describeTarget(zombie));
        } finally {
            // gametest 共用同一个测试世界与玩家列表：漏下来的玩家会被后面用例的
            // "附近有没有传播来源/玩家"扫描扫到，漏下来的丧尸则可能去追别的用例的玩家。
            if (zombie != null) {
                zombie.discard();
            }
            dispose(survivor, stable);
        }
        helper.succeed();
    }

    /** 不稳定感染者不享受减免：丧尸应当保持原本的目标。 */
    @GameTest(template = "empty")
    public static void zombieKeepsUnstableInfectedAsTarget(GameTestHelper helper) {
        ServerPlayer survivor = authenticatedPlayer(helper);
        ServerPlayer unstable = authenticatedPlayer(helper);
        Zombie zombie = null;
        try {
            setIdentity(unstable, PlayerAttributesData.INFECTION_LEVEL_ONE);

            zombie = spawnZombieWithTwoPlayers(helper, survivor, unstable);

            zombie.setTarget(unstable);

            helper.assertTrue(zombie.getTarget() == unstable,
                    "不稳定感染者不应被降仇恨，实际目标是 " + describeTarget(zombie));
        } finally {
            // 这里的不稳定感染者是真正的"传播来源"（会持续污染附近扫描），必须摘掉；
            // 丧尸与幸存者也一起清掉，理由同上。
            if (zombie != null) {
                zombie.discard();
            }
            dispose(survivor, unstable);
        }
        helper.succeed();
    }

    /**
     * 把测试造出来的模拟玩家从服务器上摘掉。
     *
     * <p>gametest 共用同一个测试世界与 {@code PlayerList}，而"附近有没有玩家 / 传播来源"是
     * 全服扫描；这些模拟玩家又都由 {@code makeMockServerPlayerInLevel()} 放在同一个出生点上，
     * 只要留下一个就会干扰后面用例的判定。</p>
     */
    private static void dispose(ServerPlayer... players) {
        for (ServerPlayer player : players) {
            if (player != null && player.getServer() != null) {
                player.getServer().getPlayerList().remove(player);
            }
        }
    }

    /** 同一套位置安排：丧尸在 (0,1,0)，幸存者在 (1,1,0)，目标玩家在 (1,1,1)。 */
    private static Zombie spawnZombieWithTwoPlayers(GameTestHelper helper,
                                                   ServerPlayer survivor, ServerPlayer target) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 1, 0));

        BlockPos survivorPos = helper.absolutePos(new BlockPos(1, 1, 0));
        survivor.teleportTo(survivorPos.getX() + 0.5D, survivorPos.getY(), survivorPos.getZ() + 0.5D);

        BlockPos targetPos = helper.absolutePos(new BlockPos(1, 1, 1));
        target.teleportTo(targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D);
        return zombie;
    }

    /** 把玩家写成指定感染等级；等级 2 且无复发窗口即为稳定感染者。 */
    private static void setIdentity(ServerPlayer player, int level) {
        PlayerAttributesData data = PlayerAttributesDataManager.getPlayerAttributesData(player.getUUID());
        if (data == null) {
            throw new IllegalStateException("gametest 无法取得玩家属性档案");
        }
        data.setInfectionLevel(level);
        data.setCurrentInfection(level == PlayerAttributesData.INFECTION_LEVEL_NONE ? 0.0F : 200.0F);
        data.clearRelapseState();
        PlayerAttributesDataManager.updatePlayerAttributesData(player, data);
    }

    private static ServerPlayer authenticatedPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        AuthSessionGuard.markAuthenticated(player);
        // 仇恨规则只看 gameMode（不看 isCreative 覆写），因此这里必须是可被丧尸选中的生存模式。
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static String describeTarget(Zombie zombie) {
        return zombie.getTarget() == null ? "null" : zombie.getTarget().getName().getString();
    }
}
