package com.hhy.dreamingfishcore.block;

import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule.EventType;
import com.hhy.dreamingfishcore.item.DisasterDataItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 天灾反应器：**板条箱式的容器**，只收「天灾分析数据」。
 *
 * <p>交互（照服主要求的"put in"逻辑）：</p>
 * <ul>
 *   <li><b>手持数据右键</b> → 塞进反应器，同种类会堆到已有的那一堆上；</li>
 *   <li><b>空手右键</b> → 打开界面（3×9 格子，像板条箱）；</li>
 *   <li>跨天时由 {@link DisasterReactorBlockEntity#resolveForDay} 自动拦截今天的天灾。</li>
 * </ul>
 */
public class DisasterReactorBlock extends Block implements EntityBlock {

    public DisasterReactorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DisasterReactorBlockEntity(pos, state);
    }

    /** 空手右键：开界面。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof DisasterReactorBlockEntity reactor
                && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.literal("天灾反应器");
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player menuPlayer) {
                    return new DisasterReactorMenu(containerId, inventory, reactor);
                }
            }, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    /** 手持数据右键：塞进去；手上不是数据就走"开界面"。 */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        EventType type = DisasterDataItem.typeOf(stack);
        if (type == null) {
            this.useWithoutItem(state, level, pos, player, hit);
            return ItemInteractionResult.SUCCESS;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof DisasterReactorBlockEntity reactor
                && player instanceof ServerPlayer serverPlayer) {
            int before = stack.getCount();
            boolean accepted = insert(reactor, stack);
            if (accepted && stack.getCount() < before) {
                serverPlayer.displayClientMessage(Component.literal(
                        "§7已放入「" + type.displayName() + "」的数据：" + (before - stack.getCount()) + " 份。"), true);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_FRAME_ADD_ITEM,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.2F);
            } else {
                serverPlayer.displayClientMessage(Component.literal(
                        "§c反应器满了 —— 空手右键打开它清点一下。"), true);
            }
        }
        return ItemInteractionResult.SUCCESS;
    }

    /** 先堆到同种类上，再找空格。 */
    private static boolean insert(DisasterReactorBlockEntity reactor, ItemStack stack) {
        EventType type = DisasterDataItem.typeOf(stack);
        if (type == null) {
            return false;
        }
        boolean changed = false;
        for (int index = 0; index < reactor.getContainerSize() && !stack.isEmpty(); index++) {
            ItemStack existing = reactor.getItem(index);
            if (!existing.isEmpty() && DisasterDataItem.typeOf(existing) == type
                    && existing.getCount() < existing.getMaxStackSize()) {
                int move = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(move);
                stack.shrink(move);
                changed = true;
            }
        }
        for (int index = 0; index < reactor.getContainerSize() && !stack.isEmpty(); index++) {
            if (reactor.getItem(index).isEmpty()) {
                ItemStack split = stack.split(Math.min(stack.getCount(), stack.getMaxStackSize()));
                reactor.setItem(index, split);
                changed = true;
            }
        }
        if (changed) {
            reactor.setChanged();
        }
        return changed;
    }
}
