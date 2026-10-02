package com.hhy.dreamingfishcore.item.items;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionIdentity;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionTreatmentService;
import com.hhy.dreamingfishcore.item.DreamingFishCore_Items;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 两种感染抑制剂的注册与数值 gametest。
 *
 * <p>为什么不在单测里做：剂量挂在注册表上（哪个物品对应 5、哪个对应 15），
 * 而 {@code Item.Properties} 在普通单测环境里根本构造不出来（缺游戏引导），
 * 所以"注册出来的东西剂量对不对"只能在这里验。</p>
 */
@GameTestHolder(DreamingFishCore.MODID)
@PrefixGameTestTemplate(false)
public class SuppressantGameTest {

    /** 两个物品都注册成功，ID 正确，且剂量分别是 5 与 15。 */
    @GameTest(template = "empty")
    public static void registeredSuppressantsCarryTheDesignedDoses(GameTestHelper helper) {
        Item low = DreamingFishCore_Items.INFECTION_SUPPRESSANT.get();
        Item high = DreamingFishCore_Items.STRONG_INFECTION_SUPPRESSANT.get();

        helper.assertTrue(low instanceof Item_InfectionSuppressant,
                "低剂量感染抑制剂必须是抑制剂类");
        helper.assertTrue(high instanceof Item_InfectionSuppressant,
                "高剂量感染抑制剂必须是抑制剂类");
        helper.assertValueEqual(((Item_InfectionSuppressant) low).dose(),
                Item_InfectionSuppressant.LOW_DOSE, "低剂量物品的剂量");
        helper.assertValueEqual(((Item_InfectionSuppressant) high).dose(),
                Item_InfectionSuppressant.HIGH_DOSE, "高剂量物品的剂量");
        helper.assertValueEqual(Item_InfectionSuppressant.LOW_DOSE, 5.0F, "低剂量数值");
        helper.assertValueEqual(Item_InfectionSuppressant.HIGH_DOSE, 15.0F, "高剂量数值");

        ResourceLocation lowId = BuiltInRegistries.ITEM.getKey(low);
        ResourceLocation highId = BuiltInRegistries.ITEM.getKey(high);
        helper.assertTrue(lowId.toString().equals("dreamingfishcore:infection_suppressant"),
                "低剂量物品注册 ID 应为 dreamingfishcore:infection_suppressant，实际 " + lowId);
        helper.assertTrue(highId.toString().equals("dreamingfishcore:strong_infection_suppressant"),
                "高剂量物品注册 ID 应为 dreamingfishcore:strong_infection_suppressant，实际 " + highId);
        helper.succeed();
    }

    /**
     * 剂量作用于真实感染值：读数按剂量下降，**归零时解除感染者身份**。
     *
     * <p>三种身份都过一遍：幸存者只降读数；不稳定感染者与稳定感染者读数归零后都变回幸存者
     * （后者是用户明确选择的取舍——稳定感染者反复服用可以绕开重构疗程）。</p>
     */
    @GameTest(template = "empty")
    public static void dosesReduceInfectionAndClearIdentityAtZero(GameTestHelper helper) {
        ServerPlayer survivor = null;
        ServerPlayer unstable = null;
        ServerPlayer stable = null;
        try {
            survivor = authenticatedPlayer(helper);
            PlayerAttributesData survivorData = dataOf(survivor);
            survivorData.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_NONE);
            survivorData.setCurrentInfection(20.0F);
            PlayerAttributesDataManager.updatePlayerAttributesData(survivor, survivorData);

            // 幸存者：20 - 5 = 15，身份不变
            helper.assertValueEqual(InfectionTreatmentService.applyDoseSuppressant(
                            survivor, Item_InfectionSuppressant.LOW_DOSE),
                    InfectionTreatmentService.TreatmentOutcome.APPLIED, "幸存者低剂量应当生效");
            helper.assertValueEqual(dataOf(survivor).getCurrentInfection(), 15.0F,
                    "20 点读数用低剂量应剩 15 点");
            helper.assertValueEqual(dataOf(survivor).getInfectionIdentity(), InfectionIdentity.SURVIVOR,
                    "幸存者吃药不应改变身份");

            // 再吃高剂量：15 - 15 = 0，仍是幸存者
            InfectionTreatmentService.applyDoseSuppressant(survivor,
                    Item_InfectionSuppressant.HIGH_DOSE);
            helper.assertValueEqual(dataOf(survivor).getCurrentInfection(), 0.0F,
                    "15 点读数用高剂量应清零");

            // 读数 0 且不是感染者：没有可做的事，物品据此不消耗
            helper.assertValueEqual(InfectionTreatmentService.applyDoseSuppressant(
                            survivor, Item_InfectionSuppressant.HIGH_DOSE),
                    InfectionTreatmentService.TreatmentOutcome.NOTHING_TO_DO,
                    "读数 0 的幸存者再吃药应判定为无事可做");
            helper.assertFalse(Item_InfectionSuppressant.canTreat(0.0F, false),
                    "读数 0 且非感染者不应允许服用");

            // 不稳定感染者：读数归零时必须解除身份
            unstable = authenticatedPlayer(helper);
            PlayerAttributesData unstableData = dataOf(unstable);
            unstableData.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_ONE);
            unstableData.setCurrentInfection(10.0F);
            PlayerAttributesDataManager.updatePlayerAttributesData(unstable, unstableData);
            InfectionTreatmentService.applyDoseSuppressant(unstable,
                    Item_InfectionSuppressant.HIGH_DOSE);
            helper.assertValueEqual(dataOf(unstable).getCurrentInfection(), 0.0F,
                    "不稳定感染者读数应被压到 0");
            helper.assertValueEqual(dataOf(unstable).getInfectionIdentity(), InfectionIdentity.SURVIVOR,
                    "读数归零后不稳定感染者应恢复为幸存者");

            // 稳定感染者：同样归零解身份（已知且明确选择的副作用）
            stable = authenticatedPlayer(helper);
            PlayerAttributesData stableData = dataOf(stable);
            stableData.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_TWO);
            stableData.setCurrentInfection(10.0F);
            PlayerAttributesDataManager.updatePlayerAttributesData(stable, stableData);
            InfectionTreatmentService.applyDoseSuppressant(stable,
                    Item_InfectionSuppressant.HIGH_DOSE);
            helper.assertValueEqual(dataOf(stable).getCurrentInfection(), 0.0F,
                    "稳定感染者读数应被压到 0");
            helper.assertValueEqual(dataOf(stable).getInfectionIdentity(), InfectionIdentity.SURVIVOR,
                    "读数归零后稳定感染者也会变回幸存者（既定取舍）");
        } finally {
            // 这个用例中途会造出会传播的不稳定感染者（服药前的窗口），而 gametest 共用同一个
            // 测试世界与玩家列表、附近扫描又是全服 32 格：断言失败在中间时残留会污染后面用例。
            dispose(survivor, unstable, stable);
        }
        helper.succeed();
    }

    /** 把测试造出来的模拟玩家从服务器上摘掉，别让它影响同一批次里的其它 gametest。 */
    private static void dispose(ServerPlayer... players) {
        for (ServerPlayer player : players) {
            if (player != null && player.getServer() != null) {
                player.getServer().getPlayerList().remove(player);
            }
        }
    }

    private static ServerPlayer authenticatedPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        AuthSessionGuard.markAuthenticated(player);
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
