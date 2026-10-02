package com.hhy.dreamingfishcore.item.items;

import com.hhy.dreamingfishcore.common.util.ItemStackDataHelper;
import com.hhy.dreamingfishcore.gameplay.storybook_system.StoryBookDataManager;
import com.hhy.dreamingfishcore.item.DreamingFishCore_Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class Item_FragmentPage extends Item {
    private static final String FRAGMENT_PAGE_TAG = "FragmentPage";
    private static final String FRAGMENT_ID_KEY = "fragmentId";
    /** 里程碑 2：稳定线索 ID（新写入的残页用它；旧残页只有上面的整数编号）。 */
    private static final String CLUE_ID_KEY = "clueId";

    public Item_FragmentPage(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // 里程碑 2 收尾后统一按稳定线索 ID 翻页；旧残页（只有整数编号）由 getClueId 映射回来。
            boolean used = StoryBookDataManager.useCluePage(serverPlayer, getClueId(stack));
            if (used && !player.isCreative()) {
                stack.shrink(1);
            }
            return used ? InteractionResultHolder.sidedSuccess(stack, false) : InteractionResultHolder.fail(stack);
        }

        return InteractionResultHolder.fail(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.literal("§7一张残破的纸。需要拼成完整的才能真正读懂。"));
        tooltip.add(Component.literal("§8右键后获得§f随记本§8，并直接阅读这次整理出的内容"));

        String clueId = getClueId(stack);
        if (clueId != null && !clueId.isBlank()) {
            // 只显示短编号：客户端没有线索目录，标题要等服务端下发，这里不硬猜。
            tooltip.add(Component.literal("§7线索: §f" + shortLabel(clueId)));
        } else {
            tooltip.add(Component.literal("§c未绑定线索，无法整理出内容"));
        }
    }

    /** 短编号：稳定 ID 的最后一段；旧残页只有整数编号时由调用方映射。 */
    private static String shortLabel(String clueId) {
        String tail = clueId.substring(clueId.lastIndexOf('/') + 1);
        return tail.isBlank() ? clueId : tail;
    }


    /**
     * 按稳定 ID 造一张残页（里程碑 2）。
     *
     * <p>同时写入旧编号（如果有），这样在旧客户端或旧存档路径里仍然能读出内容。</p>
     */
    public static ItemStack createCluePage(String clueId, int legacyId) {
        ItemStack stack = new ItemStack(DreamingFishCore_Items.FRAGMENT_PAGE.get());
        setClueId(stack, clueId);
        if (legacyId > 0) {
            setFragmentId(stack, legacyId);
        }
        return stack;
    }

    public static void setClueId(ItemStack stack, String clueId) {
        if (clueId == null || clueId.isBlank()) {
            return;
        }
        CompoundTag rootTag = ItemStackDataHelper.getTag(stack);
        if (rootTag == null) {
            rootTag = new CompoundTag();
        }
        CompoundTag fragmentPageTag = rootTag.getCompound(FRAGMENT_PAGE_TAG);
        fragmentPageTag.putString(CLUE_ID_KEY, clueId);
        rootTag.put(FRAGMENT_PAGE_TAG, fragmentPageTag);
        ItemStackDataHelper.setTag(stack, rootTag);
    }

    /** 残页绑定的线索：优先读稳定 ID，旧残页回退到整数编号再映射。 */
    public static String getClueId(ItemStack stack) {
        CompoundTag fragmentPageTag = fragmentPageTag(stack);
        if (fragmentPageTag != null && fragmentPageTag.contains(CLUE_ID_KEY)) {
            String clueId = fragmentPageTag.getString(CLUE_ID_KEY);
            if (!clueId.isBlank()) {
                return clueId;
            }
        }
        Integer legacyId = getFragmentId(stack);
        return legacyId == null
                ? null
                : com.hhy.dreamingfishcore.gameplay.clue_system.ClueCatalog.idForLegacy(legacyId);
    }

    private static CompoundTag fragmentPageTag(ItemStack stack) {
        if (!ItemStackDataHelper.hasTag(stack)) {
            return null;
        }
        CompoundTag rootTag = ItemStackDataHelper.getTag(stack);
        if (rootTag == null || !rootTag.contains(FRAGMENT_PAGE_TAG)) {
            return null;
        }
        return rootTag.getCompound(FRAGMENT_PAGE_TAG);
    }

    public static void setFragmentId(ItemStack stack, int fragmentId) {
        CompoundTag rootTag = ItemStackDataHelper.getTag(stack);
        if (rootTag == null) {
            rootTag = new CompoundTag();
        }
        CompoundTag fragmentPageTag = rootTag.getCompound(FRAGMENT_PAGE_TAG);
        fragmentPageTag.putInt(FRAGMENT_ID_KEY, fragmentId);
        rootTag.put(FRAGMENT_PAGE_TAG, fragmentPageTag);
        ItemStackDataHelper.setTag(stack, rootTag);
    }

    public static Integer getFragmentId(ItemStack stack) {
        if (!ItemStackDataHelper.hasTag(stack)) {
            return null;
        }

        CompoundTag rootTag = ItemStackDataHelper.getTag(stack);
        if (rootTag == null || !rootTag.contains(FRAGMENT_PAGE_TAG)) {
            return null;
        }

        CompoundTag fragmentPageTag = rootTag.getCompound(FRAGMENT_PAGE_TAG);
        if (!fragmentPageTag.contains(FRAGMENT_ID_KEY)) {
            return null;
        }

        int fragmentId = fragmentPageTag.getInt(FRAGMENT_ID_KEY);
        return fragmentId > 0 ? fragmentId : null;
    }
}
