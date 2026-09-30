package com.hhy.dreamingfishcore.gameplay.organization_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.organization_system.network.Packet_OrganizationSnapshotResponse;
import com.hhy.dreamingfishcore.server.economy_bridge.EconomySystemBridge;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 组织联动的端到端 gametest：真实扣款 → 资金池账本 → 快照 → 线格式往返。
 *
 * <p>为什么需要它：终端「领地与资金」分区的数据是<strong>手写编解码</strong>的，字段顺序或类型写错
 * 在编译期完全看不出来，只会在真人打开界面时变成错位或丢字段。这里用真实的
 * {@link Packet_OrganizationSnapshotResponse} 编解码器跑一次往返，把线格式钉住。</p>
 *
 * <p>关于经济服务：gametest 服务器会加载 {@code run/mods} 下的模组，所以经济模组通常<b>在</b>，
 * 但模拟玩家没有余额。测试先给它备一笔钱，于是"扣款 → 入账 → 写盘"这条真实路径也被覆盖；
 * 若经济模组确实不在，创建组织本来就会免费放行，断言会走另一条分支。</p>
 */
@GameTestHolder(DreamingFishCore.MODID)
@PrefixGameTestTemplate(false)
public class OrganizationLinkageGameTest {

    @GameTest(template = "empty")
    public static void snapshotRoundTripKeepsFundsTerritoriesAndDevices(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        AuthSessionGuard.markAuthenticated(player);

        // 经济模组在时给模拟玩家一笔测试资金；不在时创建组织会免费放行，两条路都能继续。
        EconomySystemBridge.MutationResult funded = EconomySystemBridge.credit(
                player, 2000, "gametest/setup", "gametest 测试资金");
        boolean economyUsable = funded == EconomySystemBridge.MutationResult.SUCCESS;
        helper.assertTrue(economyUsable
                        || funded == EconomySystemBridge.MutationResult.NOT_AVAILABLE,
                "备资金应当成功或明确报告经济服务不可用，实际 " + funded);

        // 名字带上玩家 UUID 片段：gametest 世界的数据会在多次运行之间保留，
        // 固定名字会在第二次运行时撞上"已经存在同名组织"。
        String orgName = "测" + player.getUUID().toString().substring(0, 8);
        OrganizationResult created = OrganizationManager.create(player, orgName);
        helper.assertTrue(created.success(), "应能创建组织：" + created.message());
        Organization organization = OrganizationManager.findByPlayer(player.getUUID()).orElse(null);
        helper.assertTrue(organization != null, "创建后应能按玩家查到组织");

        if (economyUsable) {
            // 真实捐款：从个人账户扣款并入组织账。
            int balanceBefore = EconomySystemBridge.balance(player);
            OrganizationResult deposited = OrganizationManager.deposit(player, 500);
            helper.assertTrue(deposited.success(), "捐款应当成功：" + deposited.message());
            helper.assertTrue(organization.funds() == 500,
                    "组织资金池应记上 500，实际 " + organization.funds());
            helper.assertTrue(EconomySystemBridge.balance(player) == balanceBefore - 500,
                    "个人余额应减少 500");

            // 上限：超过 maxDeposit 必须被拒，且不能改动任何一边的余额。
            OrganizationResult tooMuch = OrganizationManager.deposit(player, 100_000);
            helper.assertFalse(tooMuch.success(), "超过单次捐款上限应被拒绝");
            helper.assertTrue(organization.funds() == 500
                            && EconomySystemBridge.balance(player) == balanceBefore - 500,
                    "被拒的捐款不能改动任何余额");
        }

        // 直接写入一条领地引用：领地登记本身还要求经济服务能读到"自己名下的领地"，
        // 模拟玩家没有领地，所以这里构造数据而不是走登记流程（登记规则由单测覆盖）。
        String territoryId = "11111111-2222-3333-4444-555555555555";
        organization.registerTerritory(territoryId, 42L);

        String dimensionId = helper.getLevel().dimension().location().toString();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        SettlementFilterRegistry.register(dimensionId, pos.getX(), pos.getY(), pos.getZ());
        SettlementFilterRegistry.bind(dimensionId, pos.getX(), pos.getY(), pos.getZ(), organization.id());

        OrganizationViewData.Snapshot snapshot = OrganizationManager.buildSnapshot(
                player, helper.getLevel().getServer());
        OrganizationViewData.Detail before = snapshot.myOrganization();
        helper.assertTrue(before != null, "本组织应带有详情");
        helper.assertTrue(before.funds() == organization.funds(),
                "快照里的资金池应与组织账一致");
        helper.assertTrue(before.territories().size() == 1, "应带上那条领地引用");
        helper.assertTrue(before.devices().size() == 1, "应带上那台设备");
        helper.assertTrue(before.canManageTerritories(), "会长应当能管理组织领地");
        helper.assertTrue(before.canDepositFunds(), "成员应当能捐款");

        // 真实编解码往返：字段顺序/类型写错会在这里暴露。
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().registryAccess());
        Packet_OrganizationSnapshotResponse.STREAM_CODEC.encode(
                buffer, new Packet_OrganizationSnapshotResponse(snapshot));
        OrganizationViewData.Snapshot decoded = Packet_OrganizationSnapshotResponse.STREAM_CODEC
                .decode(buffer).snapshot();

        OrganizationViewData.Detail after = decoded.myOrganization();
        helper.assertTrue(after != null, "解码后仍应有组织详情");
        helper.assertTrue(after.funds() == before.funds(), "资金池应在往返后保持一致");
        helper.assertTrue(after.maxDeposit() == before.maxDeposit(), "捐款上限应一致");
        helper.assertTrue(after.maxTerritories() == before.maxTerritories(), "领地上限应一致");
        helper.assertTrue(after.maxFilterDevices() == before.maxFilterDevices(), "设备上限应一致");
        helper.assertTrue(after.territories().size() == before.territories().size(),
                "领地条数应一致");
        helper.assertTrue(after.territories().get(0).territoryId().equals(territoryId),
                "领地 id 应原样过网");
        helper.assertTrue(after.devices().size() == before.devices().size(), "设备条数应一致");
        helper.assertTrue(after.devices().get(0).x() == pos.getX()
                        && after.devices().get(0).y() == pos.getY()
                        && after.devices().get(0).z() == pos.getZ(),
                "设备坐标应原样过网");
        helper.assertTrue(after.canManageTerritories() == before.canManageTerritories(),
                "权限开关应一致");

        // 清理：组织与设备表都是全局状态，别影响同一服务器上的其它 gametest。
        SettlementFilterRegistry.unregister(dimensionId, pos.getX(), pos.getY(), pos.getZ());
        OrganizationManager.forceDisband(organization.id());
        helper.succeed();
    }
}
