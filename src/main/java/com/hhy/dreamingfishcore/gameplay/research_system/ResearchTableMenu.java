package com.hhy.dreamingfishcore.gameplay.research_system;

import com.hhy.dreamingfishcore.block.DreamingFishCore_Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * 研究桌的容器菜单：界面里有一个**真实的物品槽**，玩家把物品放进去，提交够数量就解锁对应配方。
 *
 * <p><b>为什么可以没有方块实体</b>：槽位内容用菜单自己的 {@link SimpleContainer} 装着，
 * 服务端与客户端各一份，靠原版的 {@link AbstractContainerMenu#broadcastChanges()} 同步
 * （{@code ServerPlayer} 每 tick 都会广播一次槽位，点击处理完也会立刻广播一次）。
 * 方块本身依然无状态——菜单关掉时槽里的东西全部还给玩家，见 {@link #removed(Player)}，
 * 所以没有"留在方块里"的数据，也就没有需要存档的东西。</p>
 *
 * <p><b>服务端权威</b>：这个类只负责"物品放在哪儿"。能不能提交、扣几个、解锁什么，
 * 全部在 {@link ResearchService#handleSubmit} 里重新判定，客户端说了不算。</p>
 */
public class ResearchTableMenu extends AbstractContainerMenu {

    /** 提交槽在菜单里的下标（{@link Slot#index}），客户端点击时就传这个值。 */
    public static final int SUBMIT_SLOT = 0;
    /** 玩家背包（主背包 3 行 + 快捷栏 1 行）在菜单里的起始下标。 */
    public static final int INVENTORY_SLOT_START = 1;
    public static final int INVENTORY_COLUMNS = 9;
    public static final int MAIN_INVENTORY_ROWS = 3;
    /** 主背包槽位数量（27），加上快捷栏 9 个就是玩家全部可操作的槽位。 */
    public static final int MAIN_INVENTORY_SIZE = INVENTORY_COLUMNS * MAIN_INVENTORY_ROWS;

    /**
     * 交互距离上限（方块中心到玩家），与研究桌的其它交互保持一致。
     * 超出这个距离菜单会被服务端关掉，槽里的物品照常退回背包。
     */
    private static final double MAX_INTERACTION_DISTANCE_SQR = 8.0D * 8.0D;

    /**
     * 槽位坐标只是给原版 {@code AbstractContainerScreen} 用的占位值。
     *
     * <p>研究桌的界面是自绘的（见 {@code Screen_ResearchTable}）：面板大小随窗口变化，
     * 抽屉式的槽位命中判定由界面自己的布局负责，这里不参与。</p>
     */
    private static final int PLACEHOLDER_X = 0;
    private static final int PLACEHOLDER_Y = 0;

    private final BlockPos pos;
    private final SimpleContainer submitContainer = new SimpleContainer(1);

    /** 服务端构造函数；客户端由 {@link #fromNetwork(int, Inventory, RegistryFriendlyByteBuf)} 走同一套。 */
    public ResearchTableMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(ResearchTableMenus.RESEARCH_TABLE.get(), containerId);
        this.pos = pos == null ? BlockPos.ZERO : pos;

        // 0：提交槽
        this.addSlot(new Slot(this.submitContainer, 0, PLACEHOLDER_X, PLACEHOLDER_Y));
        // 1..27：主背包（与原版箱子菜单一致的顺序）
        for (int row = 0; row < MAIN_INVENTORY_ROWS; row++) {
            for (int column = 0; column < INVENTORY_COLUMNS; column++) {
                this.addSlot(new Slot(playerInventory,
                        column + row * INVENTORY_COLUMNS + INVENTORY_COLUMNS,
                        PLACEHOLDER_X, PLACEHOLDER_Y));
            }
        }
        // 28..36：快捷栏
        for (int column = 0; column < INVENTORY_COLUMNS; column++) {
            this.addSlot(new Slot(playerInventory, column, PLACEHOLDER_X, PLACEHOLDER_Y));
        }
    }

    /** 客户端工厂：坐标是服务端通过 {@code writeClientSideData} 一起发过来的。 */
    public static ResearchTableMenu fromNetwork(int containerId, Inventory playerInventory,
                                                RegistryFriendlyByteBuf data) {
        // 只有"服务端没写额外数据"时才会是 null（本模组不会走到这条路），
        // 此时给一个安全坐标：真正能不能提交由服务端按自己的坐标重判，客户端改不了结果。
        return new ResearchTableMenu(containerId, playerInventory, data == null ? BlockPos.ZERO : data.readBlockPos());
    }

    /** 右键研究桌时用的菜单提供者：把方块坐标随开屏包一起发给客户端。 */
    public static MenuProvider provider(BlockPos pos) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("研究桌");
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
                return new ResearchTableMenu(containerId, playerInventory, pos);
            }

            @Override
            public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
                // 客户端要靠这个坐标发"提交"请求，也必须让客户端能核对自己在哪张桌子旁。
                buffer.writeBlockPos(pos);
            }
        };
    }

    @Override
    public MenuType<?> getType() {
        return ResearchTableMenus.RESEARCH_TABLE.get();
    }

    public BlockPos getPos() {
        return this.pos;
    }

    /** 这张菜单是不是指定的那张研究桌。 */
    public boolean matches(BlockPos other) {
        return other != null && this.pos.equals(other);
    }

    /** 提交槽里的物品（服务端读的就是它，客户端读的是同步过来的副本）。 */
    public ItemStack getSubmitStack() {
        return this.submitContainer.getItem(SUBMIT_SLOT);
    }

    /** 服务端结算用：改写槽位内容并立刻同步给客户端。 */
    public void setSubmitStack(ItemStack stack) {
        this.submitContainer.setItem(SUBMIT_SLOT, stack == null ? ItemStack.EMPTY : stack);
        this.broadcastChanges();
    }

    /** 供界面显示"背包里有多少"用：整份容器（提交槽 + 玩家背包）。 */
    public Container getSubmitContainer() {
        return this.submitContainer;
    }

    /**
     * 每次点击之后把最新状态推给客户端。
     *
     * <p>槽位内容可能刚被这次点击改掉，"能不能提交 / 为什么不能"必须重新算一遍再显示，
     * 否则界面上的原因会停在上一件物品上。客户端也会调用 {@code clicked}（转发鼠标点击），
     * 但那里不是 {@link ServerPlayer}，所以不会触发任何同步。</p>
     */
    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType, Player player) {
        super.clicked(slotId, button, clickType, player);
        if (player instanceof ServerPlayer serverPlayer) {
            ResearchService.pushState(serverPlayer, this.pos);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack untouched = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return untouched;
        }

        ItemStack stack = slot.getItem();
        untouched = stack.copy();
        if (index == SUBMIT_SLOT) {
            // 提交槽 → 背包
            if (!this.moveItemStackTo(stack, INVENTORY_SLOT_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, SUBMIT_SLOT, SUBMIT_SLOT + 1, false)) {
            // 背包 → 提交槽（放不进去就什么都不做，与原版箱子菜单的行为一致）
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == untouched.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return untouched;
    }

    /**
     * 菜单是否还应该开着：方块还在、玩家没走远。
     *
     * <p>返回 false 时服务端会关掉菜单并把槽里的物品退回背包——所以"走远了"不会吞物品。</p>
     */
    @Override
    public boolean stillValid(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            // 客户端不做这个判定（原版也不会在客户端调用它）。
            return true;
        }
        Block block = DreamingFishCore_Blocks.RESEARCH_TABLE.get();
        return level.getBlockState(this.pos).is(block)
                && player.distanceToSqr(Vec3.atCenterOf(this.pos)) <= MAX_INTERACTION_DISTANCE_SQR;
    }

    /**
     * 关闭菜单：槽里剩下的物品**必须**还给玩家。
     *
     * <p>不这么做的话，玩家放进去 20 个、提交 16 个，剩下 4 个就随着菜单消失被吞掉了。
     * {@code placeItemBackInInventory} 在背包放不下时会自己把物品丢在玩家脚下，
     * 所以这里不会因为背包满而丢东西。</p>
     */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ItemStack rest = this.submitContainer.removeItemNoUpdate(SUBMIT_SLOT);
        if (!rest.isEmpty()) {
            player.getInventory().placeItemBackInInventory(rest);
        }
    }
}
