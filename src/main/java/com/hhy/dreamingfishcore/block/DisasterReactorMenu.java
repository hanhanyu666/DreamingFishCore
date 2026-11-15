package com.hhy.dreamingfishcore.block;

import com.hhy.dreamingfishcore.item.DisasterDataItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 天灾反应器的容器菜单：**3×9 格，只收天灾分析数据**，布局与板条箱/箱子一致。
 *
 * <p>用 {@code IMenuTypeExtension} 注册：开屏时要把方块坐标发过去，
 * 客户端据此找到（或缓存到）对应的方块实体。</p>
 */
public class DisasterReactorMenu extends AbstractContainerMenu {

    /** 反应器自身的格数（3 行 × 9 列）。 */
    public static final int ROWS = 3;
    public static final int COLUMNS = 9;
    /** 反应器界面左上角。 */
    private static final int GRID_X = 8;
    private static final int GRID_Y = 18;

    private final Container container;

    public DisasterReactorMenu(int id, Inventory playerInventory, Container container) {
        super(DreamingFishCore_BlockEntities.DISASTER_REACTOR_MENU.get(), id);
        this.container = container;
        this.container.startOpen(playerInventory.player);

        // 反应器：3×9
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                this.addSlot(new Slot(container, column + row * COLUMNS,
                        GRID_X + column * 18, GRID_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return stack.getItem() instanceof DisasterDataItem;
                    }
                });
            }
        }

        // 玩家背包 3 行
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        GRID_X + column * 18, 84 + row * 18));
            }
        }
        // 玩家快捷栏
        for (int column = 0; column < COLUMNS; column++) {
            this.addSlot(new Slot(playerInventory, column, GRID_X + column * 18, 142));
        }
    }

    /** 网络工厂：从缓冲区里读回方块坐标，再找回方块实体。 */
    public static DisasterReactorMenu fromNetwork(int id, Inventory playerInventory, FriendlyByteBuf buffer) {
        var pos = buffer.readBlockPos();
        if (playerInventory.player.level().getBlockEntity(pos) instanceof DisasterReactorBlockEntity reactor) {
            return new DisasterReactorMenu(id, playerInventory, reactor);
        }
        // 找不到（区块没加载/被拆了）：给一个空容器，别让客户端开屏时崩
        return new DisasterReactorMenu(id, playerInventory, new SimpleContainer(DisasterReactorBlockEntity.SLOTS));
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return copy;
        }
        ItemStack stack = slot.getItem();
        copy = stack.copy();
        int reactorSlots = ROWS * COLUMNS;
        if (index < reactorSlots) {
            // 从反应器取回到背包
            if (!this.moveItemStackTo(stack, reactorSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 从背包塞进反应器（只有天灾数据能进去）
            if (!this.moveItemStackTo(stack, 0, reactorSlots, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }
}
