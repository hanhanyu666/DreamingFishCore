package com.hhy.dreamingfishcore.gameplay.water_gun_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.item.DreamingFishCore_Items;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 呲水枪的端到端 gametest：灭火、扑灭火方块、喷自己、对水源装水。
 *
 * <p>水柱的发射者统一用盔甲架：它是 {@code LivingEntity}（满足发射者要求）、不会自燃、
 * 也没有 AI，比拿僵尸或玩家当发射者确定得多。</p>
 */
@GameTestHolder(DreamingFishCore.MODID)
@PrefixGameTestTemplate(false)
public class WaterGunGameTest {
    private static final int PLATFORM_Y = 11;
    /** 水柱推进上限。 */
    private static final int MAX_JET_TICKS = 40;

    /** 命中着火的生物：火灭，但不掉血（水枪不是武器）。 */
    @GameTest(template = "empty")
    public static void waterJetExtinguishesABurningTargetWithoutDamagingIt(GameTestHelper helper) {
        buildPlatform(helper);
        Cow cow = null;
        ArmorStand shooter = null;
        WaterJetEntity jet = null;
        try {
            cow = helper.spawn(EntityType.COW, new BlockPos(2, PLATFORM_Y + 1, 2));
            cow.setNoAi(true);
            cow.igniteForSeconds(60.0F);
            helper.assertTrue(cow.getRemainingFireTicks() > 0, "受试生物应当先被点着");

            shooter = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(2, PLATFORM_Y + 1, 4));
            jet = new WaterJetEntity(helper.getLevel(), shooter, WaterGunConfig.current().resolve());
            aimAt(jet, cow.getEyePosition());
            Vec3 start = jet.position();
            Vec3 aimTarget = cow.getEyePosition();
            helper.getLevel().addFreshEntity(jet);

            float healthBefore = cow.getHealth();
            for (int i = 0; i < MAX_JET_TICKS && !jet.isRemoved(); i++) {
                jet.tick();
            }

            helper.assertTrue(cow.getRemainingFireTicks() <= 0,
                    "水柱命中应当扑灭目标身上的火，实际剩余着火 tick " + cow.getRemainingFireTicks()
                            + "（消散 " + jet.isRemoved()
                            + "，起点 " + start
                            + "，瞄准 " + aimTarget
                            + "，终点 " + jet.position()
                            + "，牛 " + cow.position() + "）");
            helper.assertTrue(Math.abs(cow.getHealth() - healthBefore) < 1.0E-4F,
                    "水柱不该造成任何伤害，实际 " + cow.getHealth() + " / " + healthBefore);
        } finally {
            if (jet != null) {
                jet.discard();
            }
            if (shooter != null) {
                shooter.discard();
            }
            if (cow != null) {
                cow.discard();
            }
        }
        helper.succeed();
    }

    /**
     * 命中方块：扑灭附近的火方块。
     *
     * <p>火方块没有碰撞，水柱会直接穿过它打到下面的平台，所以真正的判定落在「命中点周围一格」
     * 的检查上——这条用例同时把那个范围也钉住了。</p>
     */
    @GameTest(template = "empty")
    public static void waterJetPutsOutNearbyFireBlocks(GameTestHelper helper) {
        buildPlatform(helper);
        BlockPos firePos = new BlockPos(3, PLATFORM_Y + 1, 3);
        // 再放一格「不该被误伤」的火，用来确认判定范围没有失控地拉大。
        BlockPos farFirePos = new BlockPos(7, PLATFORM_Y + 1, 7);
        ArmorStand shooter = null;
        WaterJetEntity jet = null;
        try {
            helper.setBlock(firePos, Blocks.FIRE);
            helper.setBlock(farFirePos, Blocks.FIRE);
            helper.assertTrue(helper.getBlockState(firePos).is(Blocks.FIRE), "火方块应当已被放上");

            shooter = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(3, PLATFORM_Y + 1, 6));
            jet = new WaterJetEntity(helper.getLevel(), shooter, WaterGunConfig.current().resolve());
            // 瞄平台的石面（火方块没有碰撞，水柱会穿过去打在石头上）。
            // 注意用绝对坐标：helper.setBlock 收的是相对坐标，但水柱在世界里飞。
            BlockPos absoluteFire = helper.absolutePos(firePos);
            aimAt(jet, new Vec3(
                    absoluteFire.getX() + 0.5D,
                    absoluteFire.getY() - 0.5D,
                    absoluteFire.getZ() + 0.5D));
            Vec3 start = jet.position();
            helper.getLevel().addFreshEntity(jet);

            for (int i = 0; i < MAX_JET_TICKS && !jet.isRemoved(); i++) {
                jet.tick();
            }

            helper.assertTrue(!helper.getBlockState(firePos).is(Blocks.FIRE),
                    "水柱命中点周围的火方块应当被扑灭，实际 " + helper.getBlockState(firePos)
                            + "（消散 " + jet.isRemoved()
                            + "，起点 " + start
                            + "，终点 " + jet.position()
                            + "，火位置 " + helper.absolutePos(firePos)
                            + "，开关 extinguishBlockFire="
                            + WaterGunConfig.current().resolve().extinguishBlockFire() + "）");
            helper.assertTrue(helper.getBlockState(farFirePos).is(Blocks.FIRE),
                    "离得远的火不该被误灭，实际 " + helper.getBlockState(farFirePos));
        } finally {
            if (jet != null) {
                jet.discard();
            }
            if (shooter != null) {
                shooter.discard();
            }
        }
        helper.succeed();
    }

    /** 右键：喷自己灭火，并且消耗一发水。 */
    @GameTest(template = "empty")
    public static void rightClickingSoaksThePlayerAndPutsOutTheirFire(GameTestHelper helper) {
        buildPlatform(helper);
        ServerPlayer player = spawnPlayer(helper, new BlockPos(2, PLATFORM_Y + 1, 2));
        try {
            ItemStack gun = new ItemStack(
                    com.hhy.dreamingfishcore.item.DreamingFishCore_Items.WATER_GUN.get());
            Item_WaterGun.setWater(gun, 5);
            player.setItemInHand(InteractionHand.MAIN_HAND, gun);
            player.igniteForSeconds(30.0F);
            helper.assertTrue(player.getRemainingFireTicks() > 0, "玩家应当先被点着");

            ItemStack used = player.getMainHandItem();
            used.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

            helper.assertTrue(player.getRemainingFireTicks() <= 0,
                    "喷自己应当熄灭身上的火，实际剩余 " + player.getRemainingFireTicks());
            helper.assertTrue(Item_WaterGun.getWater(used) == 4,
                    "喷自己应当消耗一发水，实际 " + Item_WaterGun.getWater(used));
        } finally {
            dispose(player);
        }
        helper.succeed();
    }

    /**
     * 事实守护：<b>原版的方块射线会穿水而过</b>。
     *
     * <p>原版选方块走 {@code ClipContext.Block.OUTLINE}，而 {@code LiquidBlock#getShape(...)} 返回
     * {@code Shapes.empty()}——水方块没有轮廓形状，准星选不中它。所以「对着水面」时命中点会落到
     * 水面后方的方块或水底地面上，永远不是水方块本身。</p>
     *
     * <p>这条是装水逻辑的依据，也是当初那个 bug 的起点：只按 {@code useOn} 的命中方块判水源，
     * 游戏里就是「右键水面什么也不发生」。哪天原版给水加上了可选中的形状，这条会变红，
     * 提醒我们可以把 {@link Item_WaterGun#findWaterSource} 那道独立射线简化掉。</p>
     */
    @GameTest(template = "empty")
    public static void vanillaBlockRaycastPassesThroughWater(GameTestHelper helper) {
        buildPlatform(helper);
        BlockPos waterPos = new BlockPos(4, PLATFORM_Y + 1, 4);
        helper.setBlock(waterPos, Blocks.WATER);
        BlockPos absoluteWater = helper.absolutePos(waterPos);

        // 站在 2 格外、眼睛高度朝水面中心看：这条视线会穿过水体。
        Vec3 eye = standingEye(helper, new BlockPos(2, PLATFORM_Y + 1, 4));
        Vec3 aiming = new Vec3(
                absoluteWater.getX() + 0.5D,
                absoluteWater.getY() + 0.4D,
                absoluteWater.getZ() + 0.5D);

        BlockHitResult blockOnly = helper.getLevel().clip(new ClipContext(
                eye, aiming, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        BlockHitResult withFluid = helper.getLevel().clip(new ClipContext(
                eye, aiming, ClipContext.Block.OUTLINE, ClipContext.Fluid.WATER,
                CollisionContext.empty()));

        helper.assertTrue(!blockRaycastCaughtWater(blockOnly, absoluteWater),
                "原版方块射线不该选中水方块（水的轮廓形状为空），实际命中 "
                        + blockOnly.getType() + "@" + blockOnly.getBlockPos());
        helper.assertTrue(withFluid.getType() == HitResult.Type.BLOCK
                        && withFluid.getBlockPos().equals(absoluteWater)
                        && helper.getLevel().getFluidState(withFluid.getBlockPos()).is(FluidTags.WATER),
                "带流体的射线应当命中水方块，实际 " + withFluid.getType()
                        + "@" + withFluid.getBlockPos() + "，水方块在 " + absoluteWater);
        helper.succeed();
    }

    /**
     * 射线有没有把水方块当成「打到的方块」。
     *
     * <p>注意不能只比较 {@code getBlockPos()}：射线 MISS 时 {@code BlockHitResult} 仍带着落点，
     * 而落点很可能就落在水方块内部——那样会把 MISS 误判成「命中了水」。</p>
     */
    private static boolean blockRaycastCaughtWater(BlockHitResult hit, BlockPos absoluteWater) {
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(absoluteWater);
    }

    /**
     * 对着水面右键装水——走 {@code useOn}，且命中点<b>不是</b>水方块。
     *
     * <p>这是当初那个 bug 的形状：玩家对着水面，原版射线穿过水打到别处，{@code useOn} 收到的
     * {@code clickedPos} 是普通方块，只看它就永远认不出水。所以这条特意用 {@code level.clip}
     * 算出来的<b>真实命中点</b>去构造上下文，并显式断言「那里不是水方块」。</p>
     */
    @GameTest(template = "empty")
    public static void refillingFromTheWaterSurfaceWorksEvenWhenTheRaycastHitIsNotWater(
            GameTestHelper helper) {
        buildPlatform(helper);
        BlockPos waterPos = new BlockPos(4, PLATFORM_Y + 1, 4);
        helper.setBlock(waterPos, Blocks.WATER);
        ServerPlayer player = spawnPlayer(helper, new BlockPos(2, PLATFORM_Y + 1, 4));
        try {
            ItemStack gun = new ItemStack(DreamingFishCore_Items.WATER_GUN.get());
            Item_WaterGun.setWater(gun, 1);
            player.setItemInHand(InteractionHand.MAIN_HAND, gun);

            BlockPos absoluteWater = helper.absolutePos(waterPos);
            lookAt(player, absoluteWater.getX() + 0.5D, absoluteWater.getY() + 0.4D,
                    absoluteWater.getZ() + 0.5D);

            // 用游戏里同一个方法算出真实命中点（而不是我手填一个水方块坐标）。
            BlockHitResult realHit = helper.getLevel().clip(new ClipContext(
                    player.getEyePosition(),
                    player.getEyePosition().add(player.getViewVector(1.0F).scale(24.0D)),
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    player));
            helper.assertTrue(!blockRaycastCaughtWater(realHit, absoluteWater),
                    "前提：原版射线不会把水方块当作命中目标，实际 "
                            + realHit.getType() + "@" + realHit.getBlockPos());

            int waterBefore = Item_WaterGun.getWater(gun);
            UseOnContext context = new UseOnContext(
                    helper.getLevel(),
                    player,
                    InteractionHand.MAIN_HAND,
                    player.getMainHandItem(),
                    realHit);
            int capacity = WaterGunConfig.current().resolve().capacity();
            var result = gun.getItem().useOn(context);

            helper.assertTrue(Item_WaterGun.getWater(player.getMainHandItem()) == capacity,
                    "对着水面右键应当装满水枪（" + capacity + " 发），实际 "
                            + Item_WaterGun.getWater(player.getMainHandItem())
                            + "（放水前 " + waterBefore
                            + "，返回 " + result
                            + "，命中点 " + realHit.getType() + "@" + realHit.getBlockPos()
                            + "，水方块 " + absoluteWater
                            + "，视线 " + player.getViewVector(1.0F)
                            + "，眼睛 " + player.getEyePosition()
                            + "，findWaterSource " + Item_WaterGun.findWaterSource(helper.getLevel(), player) + "）");
        } finally {
            dispose(player);
        }
        helper.succeed();
    }

    /**
     * 对着水面右键装水——走 {@code use}（原版射线 MISS 时的路径）。
     *
     * <p>水面上方通常是空气，视线再往下就是水，所以原版 {@code hitResult} 完全可能是 MISS，
     * 这时原版会跳过 {@code useItemOn} 直接调 {@code use}。装水必须在这条路上也成立，
     * 否则「站在开阔水面边上右键」又会装不上。</p>
     */
    @GameTest(template = "empty")
    public static void aimingAtOpenWaterRefillsTheGunThroughUse(GameTestHelper helper) {
        buildPlatform(helper);
        BlockPos waterPos = new BlockPos(4, PLATFORM_Y + 1, 4);
        helper.setBlock(waterPos, Blocks.WATER);
        ServerPlayer player = spawnPlayer(helper, new BlockPos(2, PLATFORM_Y + 1, 4));
        try {
            ItemStack gun = new ItemStack(DreamingFishCore_Items.WATER_GUN.get());
            Item_WaterGun.setWater(gun, 0);
            player.setItemInHand(InteractionHand.MAIN_HAND, gun);

            BlockPos absoluteWater = helper.absolutePos(waterPos);
            lookAt(player, absoluteWater.getX() + 0.5D, absoluteWater.getY() + 0.4D,
                    absoluteWater.getZ() + 0.5D);

            helper.assertTrue(Item_WaterGun.findWaterSource(helper.getLevel(), player) != null,
                    "视线应当能认出前方有水（眼睛 " + player.getEyePosition()
                            + "，视线 " + player.getViewVector(1.0F)
                            + "，水 " + absoluteWater + "）");

            int capacity = WaterGunConfig.current().resolve().capacity();
            var result = gun.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

            helper.assertTrue(Item_WaterGun.getWater(player.getMainHandItem()) == capacity,
                    "空枪对着水面右键应当装满（" + capacity + " 发），实际 "
                            + Item_WaterGun.getWater(player.getMainHandItem())
                            + "（返回 " + result.getResult() + "）");
        } finally {
            dispose(player);
        }
        helper.succeed();
    }

    /** 站在相对位置上时的眼睛位置（站姿眼高 1.62 格）。 */
    private static Vec3 standingEye(GameTestHelper helper, BlockPos relativePos) {
        BlockPos absolute = helper.absolutePos(relativePos);
        return new Vec3(absolute.getX() + 0.5D, absolute.getY() + 1.62D, absolute.getZ() + 0.5D);
    }

    /** 把玩家转向世界坐标里的一点（与原版「视线方向」同一套欧拉角约定）。 */
    private static void lookAt(ServerPlayer player, double targetX, double targetY, double targetZ) {
        Vec3 eye = player.getEyePosition();
        double dx = targetX - eye.x;
        double dy = targetY - eye.y;
        double dz = targetZ - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(-dx, dz) * 180.0D / Math.PI);
        float pitch = (float) (-Mth.atan2(dy, horizontal) * 180.0D / Math.PI);
        player.setYRot(yaw);
        player.setXRot(pitch);
        // 关键：玩家这类 LivingEntity 的「视线 yaw」取自头部朝向 ——
        // LivingEntity 覆写了 getViewYRot 用 yHeadRot，而 getViewVector 是 final 的、会走那个覆写。
        // 只设 setYRot（身体朝向）的话，视线方向纹丝不动（pitch 没这层覆写，所以照样生效，
        // 表现为「低头角度对了、水平方向完全没转」）。
        player.setYHeadRot(yaw);
        player.yRotO = yaw;
        player.xRotO = pitch;
    }

    private static void aimAt(WaterJetEntity jet, Vec3 target) {
        Vec3 from = jet.position();
        jet.shoot(
                target.x - from.x,
                target.y - from.y,
                target.z - from.z,
                (float) WaterGunConfig.current().resolve().jetSpeed(),
                0.0F);
    }

    private static ServerPlayer spawnPlayer(GameTestHelper helper, BlockPos relativePos) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        AuthSessionGuard.markAuthenticated(player);
        BlockPos absolute = helper.absolutePos(relativePos);
        player.moveTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        return player;
    }

    private static void dispose(ServerPlayer player) {
        if (player != null) {
            player.discard();
        }
    }

    private static void buildPlatform(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(new BlockPos(x, PLATFORM_Y, z), Blocks.STONE);
            }
        }
    }
}
