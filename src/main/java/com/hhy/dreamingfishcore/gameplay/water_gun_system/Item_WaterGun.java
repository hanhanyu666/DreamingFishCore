package com.hhy.dreamingfishcore.gameplay.water_gun_system;

import com.hhy.dreamingfishcore.common.util.ItemStackDataHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 呲水枪。
 *
 * <p>操作映射（这是设计里唯一"不直观"的部分，所以在这里写清楚）：</p>
 * <ul>
 *   <li><b>左键</b>（长按可连射）→ 射出水柱。左键的拦截在客户端做
 *       （{@code WaterGunClientInput} 取消 {@code InteractionKeyMappingTriggered} 的攻击分支），
 *       所以手持水枪时不会挖方块、也不会挥空打人。</li>
 *   <li><b>右键</b> → 喷自己：熄灭自己身上的火。</li>
 *   <li><b>对着水源右键</b> → 装水（水方块、含水方块、水瓶炼药锅都算）。</li>
 * </ul>
 *
 * <p><b>水源识别为什么不能只看 {@code useOn} 的命中方块</b>：原版方块射线是
 * {@code ClipContext.Block.OUTLINE}，而水方块的轮廓形状为 {@code Shapes.empty()}——水不可被准星
 * 选中，射线会穿水而过打到水底。所以「对着水面右键」拿到的 {@code clickedPos} 是水下的地面，
 * 光看命中方块永远认不出水。装水因此走两步：{@code useOn} 处理本身可被选中的水源（含水方块、
 * 炼药锅），{@link #findWaterSource} 再做一次带流体的射线兜住普通水方块；
 * 两条都没命中时右键才落到「喷自己」。</p>
 *
 * <p>水量存在物品的 {@code CustomData} 里（沿用项目既有的 {@link ItemStackDataHelper}），
 * 所以不需要额外注册数据组件；物品栏上的蓝色水量条与 tooltip 都读它。</p>
 */
public class Item_WaterGun extends Item {
    /** 水量在 CustomData 里的键名。 */
    public static final String TAG_WATER = "WaterGunWater";

    /** 语言键：由物品与网络包共用，避免两边写字面量写岔。 */
    public static final String KEY_WATER = "item.dreamingfishcore.water_gun.water";
    public static final String KEY_USAGE = "item.dreamingfishcore.water_gun.usage";
    public static final String KEY_EMPTY = "item.dreamingfishcore.water_gun.empty";
    public static final String KEY_REFILLED = "item.dreamingfishcore.water_gun.refilled";
    /** 水量条颜色：水的蓝。 */
    private static final int BAR_COLOR = 0x3F76E4;
    /** 水量条满刻度（原版物品栏那一格宽 13 像素）。 */
    private static final int BAR_WIDTH = 13;

    public Item_WaterGun(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------
    // 水量存取
    // ------------------------------------------------------------------

    public static int getWater(ItemStack stack) {
        CompoundTag tag = ItemStackDataHelper.getTag(stack);
        return tag == null ? 0 : Math.max(0, tag.getInt(TAG_WATER));
    }

    public static void setWater(ItemStack stack, int water) {
        int capacity = WaterGunConfig.current().resolve().capacity();
        CompoundTag tag = ItemStackDataHelper.getTag(stack);
        CompoundTag updated = tag == null ? new CompoundTag() : tag;
        updated.putInt(TAG_WATER, Mth.clamp(water, 0, capacity));
        ItemStackDataHelper.setTag(stack, updated);
    }

    /** 装满（调试/命令用）。 */
    public static void fillUp(ItemStack stack) {
        setWater(stack, WaterGunConfig.current().resolve().capacity());
    }

    /**
     * 把「手持物品的水量」同步到客户端。
     *
     * <p>原版只在 {@code ServerPlayerGameMode#useItem} 里顺手调了一次
     * {@code inventoryMenu.sendAllDataToRemote()}（就是「右键用物品」那条路）。而水枪的水量变化
     * 有两条路都不经过它：</p>
     * <ul>
     *   <li>{@code useOn}（对着水源装水）——{@code ServerPlayerGameMode#useItemOn} 全程没有物品同步；</li>
     *   <li>左键射水——走的是自定义网络包，原版根本不知道有这回事。</li>
     * </ul>
     * <p>不补这一下，服务端水量变了而客户端的 ItemStack 组件还是旧的，物品栏上那条水量条与
     * tooltip 会一直停在装水前的数字，玩家会当成「压根没装上水」。</p>
     */
    public static void syncWater(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.sendAllDataToRemote();
        }
    }

    // ------------------------------------------------------------------
    // 左键射出（由网络包驱动，见 Packet_WaterGunFire）
    // ------------------------------------------------------------------

    /** 服务端发射一发水柱；水量不足时只给提示，不消耗。 */
    public static boolean fireJet(ServerLevel level, Player shooter, ItemStack stack) {
        WaterGunConfig.Resolved settings = WaterGunConfig.current().resolve();
        if (!WaterGunRules.canFire(getWater(stack), settings.enabled())) {
            return false;
        }
        setWater(stack, WaterGunRules.afterFire(getWater(stack)));
        syncWater(shooter);
        WaterJetEntity jet = new WaterJetEntity(level, shooter, settings);
        level.addFreshEntity(jet);
        level.playSound(
                null,
                shooter.getX(), shooter.getY(), shooter.getZ(),
                SoundEvents.GENERIC_SPLASH,
                SoundSource.PLAYERS,
                0.5F,
                1.4F);
        return true;
    }

    // ------------------------------------------------------------------
    // 右键：喷自己 / 装水
    // ------------------------------------------------------------------

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        WaterGunConfig.Resolved settings = WaterGunConfig.current().resolve();
        if (!settings.enabled()) {
            return InteractionResultHolder.pass(stack);
        }

        // 先看视线是不是正对着水。这条判断必须在这里做，不能只靠 useOn：
        // 对着水面右键时原版方块射线会穿水而过（水方块没有轮廓形状），命中点落在水底的地面，
        // useOn 认不出水，返回 PASS 之后原版就会走到这个方法上。
        BlockPos waterSource = findWaterSource(level, player);
        if (waterSource != null) {
            InteractionResult refill = tryRefill(level, player, stack, waterSource, settings);
            if (refill != InteractionResult.PASS) {
                return new InteractionResultHolder<>(refill, stack);
            }
            // 返回 PASS 只有一种情况：水枪已经满了。那就继续往下走，改成「喷自己」。
        }

        if (!WaterGunRules.canFire(getWater(stack), true)) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable(KEY_EMPTY).withStyle(ChatFormatting.GRAY), true);
                level.playSound(
                        null,
                        player.getX(), player.getY(), player.getZ(),
                        SoundEvents.LEVER_CLICK,
                        SoundSource.PLAYERS,
                        0.4F,
                        1.2F);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide) {
            setWater(stack, WaterGunRules.afterFire(getWater(stack)));
            spraySelf(level, player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** 喷自己：灭火 + 一身水花。没着火也照喷照消耗——水枪就是水枪。 */
    private static void spraySelf(Level level, Player player) {
        boolean wasBurning = WaterGunRules.isBurning(player.getRemainingFireTicks());
        player.clearFire();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.SPLASH,
                    player.getX(),
                    player.getY() + 1.0D,
                    player.getZ(),
                    24,
                    0.35D, 0.6D, 0.35D,
                    0.02D);
            serverLevel.playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    wasBurning ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.GENERIC_SPLASH,
                    SoundSource.PLAYERS,
                    wasBurning ? 0.9F : 0.5F,
                    wasBurning ? 1.0F : 1.3F);
        }
    }

    /**
     * 对着水源右键装水。
     *
     * <p>判定只有一份：{@link #findWaterSource} 看视线前方有没有水。有就装、没有就返回
     * {@code PASS}——原版会接着走 {@link #use}，也就是「喷自己」。装水与喷自己因此共用右键
     * 而不会打架。</p>
     *
     * <p>唯一的例外是水瓶炼药锅：锅里的水不是流体方块（{@code getFluidState()} 是 {@code EMPTY}），
     * 流体射线看不到它，只能靠「命中的方块是不是炼药锅」来认。</p>
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        WaterGunConfig.Resolved settings = WaterGunConfig.current().resolve();
        if (!settings.enabled()) {
            return InteractionResult.PASS;
        }

        if (level.getBlockState(context.getClickedPos()).is(Blocks.WATER_CAULDRON)) {
            return tryRefill(level, context.getPlayer(), stack, context.getClickedPos(), settings);
        }
        BlockPos waterSource = findWaterSource(level, context.getPlayer());
        return waterSource == null
                ? InteractionResult.PASS
                : tryRefill(level, context.getPlayer(), stack, waterSource, settings);
    }

    /**
     * 找玩家视线正对着的水源。
     *
     * <p><b>为什么要单独做一次射线</b>：原版的方块射线走 {@code ClipContext.Block.OUTLINE}，
     * 而 {@code LiquidBlock#getShape(...)} 返回 {@code Shapes.empty()}——水方块<b>不可被准星选中</b>，
     * 射线直接穿水而过，命中水底的地面。于是「对着水面右键」拿到的 {@code clickedPos} 是沙子/泥土，
     * {@code state.getFluidState()} 恒为 {@code EMPTY}，只按命中方块判断就永远认不出水
     * （实测表现为：右键水面什么也没发生，水量不涨）。</p>
     *
     * <p>这里换成 {@code ClipContext.Fluid.WATER} 再裁一次：该模式下水流体是有形状的
     * （{@code FluidState#getShape}），水才会现身。射线长度用玩家的方块触及距离，
     * 与原版选方块的reach保持一致。</p>
     *
     * @return 视线上的水方块位置；没对着水时返回 {@code null}
     */
    @Nullable
    public static BlockPos findWaterSource(Level level, @Nullable Player player) {
        if (player == null) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
        BlockHitResult hit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.WATER, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        // 只认水：视线被玻璃/石头之类挡住时命中的是那个方块，不该算水源。
        return level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER) ? hit.getBlockPos() : null;
    }

    /**
     * 装水。调用方负责先确认 {@code pos} 处确实是水源。
     *
     * @return {@code PASS} 表示「水枪已经满了，这次不算装水」，调用方可以落到别的行为上；
     *         其余返回值表示这次交互已被装水消费掉
     */
    private static InteractionResult tryRefill(
            Level level,
            @Nullable Player player,
            ItemStack stack,
            BlockPos pos,
            WaterGunConfig.Resolved settings) {
        if (WaterGunRules.isFull(getWater(stack), settings.capacity())) {
            // 已经满了：让右键落到「喷自己」上，不浪费锅里的水。
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.WATER_CAULDRON) && settings.drainWaterCauldron()) {
            LayeredCauldronBlock.lowerFillLevel(state, level, pos);
        }
        setWater(stack, WaterGunRules.afterRefill(
                getWater(stack), settings.capacity(), settings.refillAmount(), settings.refillToFull()));
        // useOn 这条路径原版不做物品同步，不补的话客户端量条不会动。
        if (player != null) {
            syncWater(player);
        }
        level.playSound(
                null,
                pos,
                SoundEvents.BUCKET_FILL,
                SoundSource.BLOCKS,
                0.7F,
                1.2F);
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable(KEY_REFILLED, getWater(stack), settings.capacity()), true);
        }
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------
    // 显示
    // ------------------------------------------------------------------

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getWater(stack) < WaterGunConfig.current().resolve().capacity();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int capacity = WaterGunConfig.current().resolve().capacity();
        return Math.round(BAR_WIDTH * WaterGunRules.waterFraction(getWater(stack), capacity));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        int capacity = WaterGunConfig.current().resolve().capacity();
        tooltipComponents.add(Component
                .translatable(KEY_WATER, getWater(stack), capacity)
                .withStyle(ChatFormatting.AQUA));
        tooltipComponents.add(Component.translatable(KEY_USAGE).withStyle(ChatFormatting.DARK_GRAY));
    }
}
