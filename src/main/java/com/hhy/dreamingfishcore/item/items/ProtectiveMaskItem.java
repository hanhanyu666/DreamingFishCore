package com.hhy.dreamingfishcore.item.items;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.ChatFormatting;

import java.util.List;

/**
 * 可佩戴的防护面具。
 *
 * <p>面具的实际防护判定由感染系统在服务端执行；本类只负责把道具定义为头部装备，
 * 并提供一个统一的佩戴查询入口，避免各个感染入口重复理解装备槽位。</p>
 */
public class ProtectiveMaskItem extends ArmorItem {

    public ProtectiveMaskItem(Holder<ArmorMaterial> material, Properties properties) {
        super(material, Type.HELMET, properties.stacksTo(1));
    }

    /**
     * 判断实体当前是否在头部装备了防护面具。
     * 使用实际物品类型判断，兼容重载/复制后的 ItemStack，并且不会把手持面具算作已装备。
     */
    public static boolean isEquipped(LivingEntity entity) {
        return entity != null
                && entity.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof ProtectiveMaskItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.dreamingfishcore.protective_mask.tooltip")
                .withStyle(ChatFormatting.GOLD));
    }
}
