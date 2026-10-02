package com.hhy.dreamingfishcore.item.items;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.PlayerInfectionManager;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 感染抑制剂：按剂量降低感染进度。
 *
 * <p>与基因复苏药剂的区别很关键：这里**只压进度，不改身份**。稳定感染者（二级）
 * 必须走重构疗程，抑制剂对他们同样只是把读数往下压——这正是"读数不下降 ≠ 痊愈"
 * 那条主张在玩法上的对应物：药能压数字，压不掉身份。</p>
 *
 * <p>剂量由构造参数决定，两种规格共用这一个类；感染值本来为 0 时拒绝使用、也不消耗物品，
 * 避免玩家白扔药。</p>
 */
public class Item_InfectionSuppressant extends Item {

    /** 低剂量：一次降低 5 点感染进度。 */
    public static final float LOW_DOSE = 5.0F;
    /** 高剂量：一次降低 15 点感染进度。 */
    public static final float HIGH_DOSE = 15.0F;

    /** 长按右键的时长：比剧情药剂（60）短一些，属于常规消耗品手感。 */
    private static final int USE_DURATION_TICKS = 32;

    private final float dose;

    public Item_InfectionSuppressant(Properties properties, float dose) {
        super(properties);
        this.dose = Math.max(0.0F, dose);
    }

    public float dose() {
        return dose;
    }

    /**
     * 是否值得服用：感染进度为 0 时服用没有意义。
     *
     * <p>抽成纯函数便于单测，也让"服务端最终判定"两处（开始使用与结算）共用同一判据。</p>
     */
    public static boolean canTreat(float currentInfection) {
        return currentInfection > 0.0F;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.literal("§e长按右键使用"));
        tooltip.add(Component.literal("§7降低 §f" + formatDose() + "§7 点感染进度"));
        tooltip.add(Component.literal("§8只压制进度，不解除感染者身份"));
    }

    /** 剂量显示：整数不带小数点，避免出现"5.0 点"。 */
    private String formatDose() {
        return dose == Math.floor(dose) ? String.valueOf((int) dose) : String.valueOf(dose);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            return ItemUtils.startUsingInstantly(level, player, hand);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!AuthSessionGuard.isAuthenticated(serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }

        PlayerAttributesData attributesData =
                PlayerAttributesDataManager.getPlayerAttributesData(serverPlayer.getUUID());
        if (attributesData == null) {
            serverPlayer.sendSystemMessage(Component.literal("§c无法获取玩家数据！"));
            return InteractionResultHolder.fail(stack);
        }
        if (!canTreat(attributesData.getCurrentInfection())) {
            serverPlayer.sendSystemMessage(Component.literal("§7你的感染进度本来就是 0，不需要用药。"));
            return InteractionResultHolder.fail(stack);
        }

        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        if (level.isClientSide) {
            return stack;
        }
        if (!(livingEntity instanceof ServerPlayer serverPlayer)) {
            return stack;
        }
        if (!AuthSessionGuard.isAuthenticated(serverPlayer)) {
            return stack;
        }

        PlayerAttributesData attributesData =
                PlayerAttributesDataManager.getPlayerAttributesData(serverPlayer.getUUID());
        if (attributesData == null) {
            serverPlayer.sendSystemMessage(Component.literal("§c无法获取玩家数据！"));
            return stack;
        }

        // 使用动作与结算之间存在时间差，以服务端此刻的数值为准重新判定。
        float before = attributesData.getCurrentInfection();
        if (!canTreat(before)) {
            serverPlayer.sendSystemMessage(Component.literal("§7你的感染进度已经是 0，药品没有消耗。"));
            return stack;
        }

        PlayerInfectionManager.reduceInfection(serverPlayer, dose);

        PlayerAttributesData after =
                PlayerAttributesDataManager.getPlayerAttributesData(serverPlayer.getUUID());
        float afterValue = after == null ? 0.0F : after.getCurrentInfection();
        float reduced = Math.max(0.0F, before - afterValue);

        if (reduced <= 0.0F) {
            // 一点都没降（例如刚被别的机制清零）：不消耗物品，别让玩家白吃。
            serverPlayer.sendSystemMessage(Component.literal("§7感染进度没有变化，药品没有消耗。"));
            return stack;
        }

        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }
        serverPlayer.awardStat(Stats.ITEM_USED.get(this));
        serverPlayer.sendSystemMessage(Component.literal(
                "§a感染进度 §f-" + trim(reduced) + "§a，当前 §f" + trim(afterValue)));
        DreamingFishCore.LOGGER.info("玩家 {} 使用感染抑制剂（-{}），感染进度 {} → {}",
                serverPlayer.getScoreboardName(), trim(dose), trim(before), trim(afterValue));

        return stack;
    }

    private static String trim(float value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.format("%.1f", value);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }
}
