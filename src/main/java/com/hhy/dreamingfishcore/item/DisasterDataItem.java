package com.hhy.dreamingfishcore.item;

import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterDataComponents;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule.EventType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.component.DataComponents;
import java.util.List;

/**
 * 「天灾分析数据」：可以插进天灾反应器，清掉**今天**对应的那一种天灾。
 *
 * <p>来源有三条（见 {@link DisasterDataDrops}）：</p>
 * <ol>
 *   <li>击杀生物时**小概率**掉落（任何天灾类型随机）；</li>
 *   <li>某场天灾结束后，经历过的玩家**大概率**获得该天灾的数据；</li>
 *   <li>创造模式物品栏里能直接拿到（无类型的空白数据会提示怎么用）。</li>
 * </ol>
 *
 * <p>插入反应器时有 {@link DisasterReactorRules#CORRUPTION_PERCENT}% 概率**数据损坏**：
 * 数据被消耗，但今天的这场天灾**不会**被清掉。</p>
 */
public class DisasterDataItem extends Item {

    public DisasterDataItem(Properties properties) {
        super(properties);
    }

    /** 造一份指定天灾的数据（带名字与类型组件）。 */
    public static ItemStack create(EventType type) {
        ItemStack stack = new ItemStack(DreamingFishCore_Items.DISASTER_DATA.get());
        stack.set(DisasterDataComponents.DISASTER_TYPE.get(), type.name());
        stack.set(DataComponents.CUSTOM_NAME, nameOf(type));
        return stack;
    }

    private static Component nameOf(EventType type) {
        return Component.literal("天灾分析数据 · " + type.displayName())
                .withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    /** 从物品里读出天灾类型；不是天灾数据或类型非法时返回 null。 */
    public static EventType typeOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (!(stack.getItem() instanceof DisasterDataItem)) {
            return null;
        }
        String raw = stack.get(DisasterDataComponents.DISASTER_TYPE.get());
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (EventType type : EventType.values()) {
            if (type.name().equals(raw)) {
                return type;
            }
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        EventType type = typeOf(stack);
        if (type == null) {
            tooltip.add(Component.literal("§7未标注类型的数据").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("§7（击杀生物或经历天灾后才会带上类型）")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.literal("§7类型：" + type.displayName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§8放进天灾反应器，可清除今天这一场")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("§8稳定性 75%：有概率无法解析（数据损坏）")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
