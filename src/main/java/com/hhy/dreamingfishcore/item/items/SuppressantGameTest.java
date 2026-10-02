package com.hhy.dreamingfishcore.item.items;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.PlayerInfectionManager;
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

    /** 剂量作用于真实感染值：低剂量精确扣 5，高剂量在不足时清零而不会变成负数。 */
    @GameTest(template = "empty")
    public static void dosesReduceInfectionAndClampAtZero(GameTestHelper helper) {
        ServerPlayer player = authenticatedPlayer(helper);
        PlayerAttributesData data = dataOf(player);

        data.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_NONE);
        data.setCurrentInfection(20.0F);
        PlayerAttributesDataManager.updatePlayerAttributesData(player, data);
        PlayerInfectionManager.reduceInfection(player, Item_InfectionSuppressant.LOW_DOSE);
        helper.assertValueEqual(dataOf(player).getCurrentInfection(), 15.0F,
                "20 点感染用低剂量应剩 15 点");

        PlayerInfectionManager.reduceInfection(player, Item_InfectionSuppressant.HIGH_DOSE);
        helper.assertValueEqual(dataOf(player).getCurrentInfection(), 0.0F,
                "15 点感染用高剂量应清零");

        // 再吃一次：已经 0 了不能变成负数（这也是物品拒绝消耗的依据）。
        PlayerInfectionManager.reduceInfection(player, Item_InfectionSuppressant.HIGH_DOSE);
        helper.assertValueEqual(dataOf(player).getCurrentInfection(), 0.0F,
                "感染进度为 0 时继续用药不能下溢");
        helper.assertFalse(Item_InfectionSuppressant.canTreat(0.0F),
                "感染进度为 0 时不应允许服用");
        helper.succeed();
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
