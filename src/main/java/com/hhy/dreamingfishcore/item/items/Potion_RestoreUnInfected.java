package com.hhy.dreamingfishcore.item.items;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.PlayerInfectionClientSync;
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
 * 使用后解除感染者身份，并将感染值重置为0
 */
public class Potion_RestoreUnInfected extends Item {
    private static final int USE_DURATION_TICKS = 60;

    public Potion_RestoreUnInfected(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // 灰色：感染后的滋味不好受吧..
        tooltip.add(Component.literal("§7感染后的滋味不好受吧.."));
        // 黄色：右键使用
        tooltip.add(Component.literal("§e右键即可使用"));
        // 金色：使用后您将解除感染者状态，且感染值清0
        tooltip.add(Component.literal("§6使用后您将解除感染者状态，且感染值清0"));
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

        // 获取玩家属性数据
        PlayerAttributesData attributesData = PlayerAttributesDataManager.getPlayerAttributesData(serverPlayer.getUUID());
        if (attributesData == null) {
            serverPlayer.sendSystemMessage(Component.literal("§c无法获取玩家数据！"));
            return InteractionResultHolder.fail(stack);
        }

        // 检查是否为感染者
        if (!attributesData.isInfected()) {
            serverPlayer.sendSystemMessage(Component.literal("§c你并不是感染者，无需使用此药剂！"));
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

        PlayerAttributesData attributesData = PlayerAttributesDataManager.getPlayerAttributesData(serverPlayer.getUUID());
        if (attributesData == null) {
            serverPlayer.sendSystemMessage(Component.literal("§c无法获取玩家数据！"));
            return stack;
        }

        if (!attributesData.isInfected()) {
            serverPlayer.sendSystemMessage(Component.literal("§c你并不是感染者，无需使用此药剂！"));
            return stack;
        }

        // 解除感染状态
        attributesData.setInfected(false);
        attributesData.setCurrentInfection(0);

        // 保存数据
        PlayerAttributesDataManager.updatePlayerAttributesData(serverPlayer, attributesData);

        // 同步到客户端
        PlayerInfectionClientSync.sendInfectionDataToClient(serverPlayer, 0, false);

        // 消耗物品
        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }
        serverPlayer.awardStat(Stats.ITEM_USED.get(this));

        // 发送成功消息
        serverPlayer.sendSystemMessage(Component.literal("§a§l基因复苏成功！你已不再是感染者。"));

        DreamingFishCore.LOGGER.info("玩家 {} 使用基因复苏药剂，感染状态已解除", serverPlayer.getScoreboardName());

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
}
