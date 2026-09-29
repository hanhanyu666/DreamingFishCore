package com.hhy.dreamingfishcore.item.items;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionTreatmentService;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 基因复苏药剂
 * 可清零感染值；只可解除不稳定感染者身份，稳定感染者必须走重构疗程。
 */
public class Potion_RestoreUnInfected extends Item {
    private static final int USE_DURATION_TICKS = 60;

    public Potion_RestoreUnInfected(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§e长按右键使用"));
        tooltip.add(Component.literal("§6由逐光会牵头制作的解药，至少目前可以治愈感染者……"));
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

        // 获取玩家属性数据
        PlayerAttributesData attributesData = PlayerAttributesDataManager.getPlayerAttributesData(serverPlayer.getUUID());
        if (attributesData == null) {
            serverPlayer.sendSystemMessage(Component.literal("§c无法获取玩家数据！"));
            return InteractionResultHolder.fail(stack);
        }

        if (!canUseForInfectionLevel(attributesData.getInfectionLevel())) {
            serverPlayer.sendSystemMessage(Component.literal("§c基因复苏试剂无法治愈稳定感染者。"));
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

        PlayerAttributesData attributesData = PlayerAttributesDataManager.getPlayerAttributesData(serverPlayer.getUUID());
        if (attributesData == null) {
            serverPlayer.sendSystemMessage(Component.literal("§c无法获取玩家数据！"));
            return stack;
        }

        if (!canUseForInfectionLevel(attributesData.getInfectionLevel())) {
            serverPlayer.sendSystemMessage(Component.literal("§c基因复苏试剂无法治愈稳定感染者。"));
            return stack;
        }

        // 身份写入统一走感染系统的服务端入口：
        // 一级（不稳定感染者）是成本较低的早期逆转；未突变的幸存者属于抑制剂剂量。
        InfectionTreatmentService.TreatmentOutcome outcome =
                attributesData.getInfectionLevel() == PlayerAttributesData.INFECTION_LEVEL_ONE
                        ? InfectionTreatmentService.applyEarlyReversal(serverPlayer)
                        : InfectionTreatmentService.applyFullSuppressant(serverPlayer);

        // 使用动作与结算之间存在时间差，身份可能已经变化：以服务端最终判定为准，
        // 此时不消耗物品、不推进剧情。
        if (outcome != InfectionTreatmentService.TreatmentOutcome.APPLIED
                && outcome != InfectionTreatmentService.TreatmentOutcome.NOTHING_TO_DO) {
            serverPlayer.sendSystemMessage(Component.literal("§c基因复苏试剂无法作用于你当前的身份。"));
            return stack;
        }

        // 事件代表“玩家实际服用了阶段发放的药剂”，而不是服用前的感染等级。
        // 非感染者也可能需要用药清零感染值；StoryManager 会按当前阶段事实决定
        // 这条使用事件是否推进剧情，因而在余梦期之外使用药剂不会推进任何剧情。
        StoryManager.onGeneRevivalPotionUsed(serverPlayer);

        // 消耗物品
        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }
        serverPlayer.awardStat(Stats.ITEM_USED.get(this));

        serverPlayer.sendSystemMessage(Component.literal(
                outcome == InfectionTreatmentService.TreatmentOutcome.NOTHING_TO_DO
                        ? "§a基因复苏试剂已生效：感染值本来就是 0。"
                        : "§a基因复苏试剂已生效。"));

        DreamingFishCore.LOGGER.info("玩家 {} 使用基因复苏药剂，感染状态已处理", serverPlayer.getScoreboardName());

        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    static boolean canUseForInfectionLevel(int infectionLevel) {
        return infectionLevel == PlayerAttributesData.INFECTION_LEVEL_NONE
                || infectionLevel == PlayerAttributesData.INFECTION_LEVEL_ONE;
    }
}
