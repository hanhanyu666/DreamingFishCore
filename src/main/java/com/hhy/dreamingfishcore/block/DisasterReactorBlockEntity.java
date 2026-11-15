package com.hhy.dreamingfishcore.block;

import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterDataComponents;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule;
import com.hhy.dreamingfishcore.item.DisasterDataItem;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/**
 * 天灾反应器的方块实体：一个 **27 格（3×9）的板条箱式容器**，只收「天灾分析数据」。
 *
 * <p><b>为什么是容器而不是"右键即用"</b>：服主要的是"放进去、同种类堆叠、界面像板条箱"。
 * 这也让玩法从"临时点一下"变成"提前囤货"——你得在天灾被安排之前把数据塞进去。</p>
 *
 * <p><b>什么时候结算</b>：跨天时（{@link #resolveForDay}）扫描自己库存里今天每一场天灾的数据，
 * 有就消耗一份（25% 概率损坏），成功则把这场天灾从日程里摘掉。
 * 所以"堆叠"是有意义的：一次囤好几天的量，它每天替你挡一场。</p>
 */
public class DisasterReactorBlockEntity extends BlockEntity implements Container {

    /** 容器格数：3×9，和板条箱一样的形状。 */
    public static final int SLOTS = 27;

    /**
     * 当前加载着的反应器（静态登记表）。
     *
     * <p>跨天结算时只需要遍历这一小撮，而不是扫全世界的方块实体。
     * 方块实体的加载/卸载会自动进出这个表，所以它不会泄漏。</p>
     */
    private static final Set<DisasterReactorBlockEntity> LOADED = new LinkedHashSet<>();

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    public DisasterReactorBlockEntity(BlockPos pos, BlockState state) {
        super(DreamingFishCore_BlockEntities.DISASTER_REACTOR.get(), pos, state);
    }

    private IItemHandler itemHandler;

    /** 给漏斗/管道用的物品能力（只读外的常规行为由 Container 决定）。 */
    public IItemHandler itemHandler() {
        if (this.itemHandler == null) {
            this.itemHandler = new InvWrapper(this);
        }
        return this.itemHandler;
    }

    // ---------------------------------------------------------------- 生命周期

    @Override
    public void onLoad() {
        super.onLoad();
        LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        LOADED.remove(this);
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, this.items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items.clear();
        ContainerHelper.loadAllItems(tag, this.items, registries);
    }

    // ---------------------------------------------------------------- Container

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return this.items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(this.items, slot, amount);
        if (!result.isEmpty()) {
            this.setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.items.set(slot, stack);
        if (stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.items.clear();
        this.setChanged();
    }

    /** 只允许放天灾分析数据（板条箱式容器但没有杂物的意义）。 */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return DisasterDataItem.typeOf(stack) != null;
    }

    // ---------------------------------------------------------------- 跨天结算

    /**
     * 跨天时让所有加载着的反应器"吃掉"今天对应天灾的数据。
     *
     * <p>每一场天灾单独判定：先看今天是否真的安排了这一场，再从库存里找一份对应数据，
     * 掷 25% 损坏；成功就把这场天灾从日程里摘掉。数据无论成功失败都会被消耗（失败=损坏烧掉）。</p>
     */
    public static void resolveForDay(Level level, DisasterSchedule.DayPlan plan) {
        if (level == null || plan == null || plan.events().isEmpty()) {
            return;
        }
        List<DisasterReactorBlockEntity> reactors = new ArrayList<>(LOADED);
        for (DisasterReactorBlockEntity reactor : reactors) {
            if (reactor.isRemoved() || reactor.level != level) {
                continue;
            }
            reactor.resolveOneDay(plan);
        }
    }

    private void resolveOneDay(DisasterSchedule.DayPlan plan) {
        for (DisasterSchedule.EventType type : plan.events()) {
            int slot = findSlot(type);
            if (slot < 0) {
                continue;
            }
            ItemStack stack = this.items.get(slot);
            boolean corrupted = DisasterSchedule.EventType.values().length > 0
                    && (this.level != null
                    && com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterReactorRules
                    .isCorrupted(this.level.getRandom().nextInt(100)));
            stack.shrink(1);
            if (stack.isEmpty()) {
                this.items.set(slot, ItemStack.EMPTY);
            }
            this.setChanged();
            if (corrupted) {
                this.announce("§c反应器里的「" + type.displayName() + "」数据损坏了，没能拦住它。");
                continue;
            }
            com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterService
                    .setDayEvent(this.level == null ? null : this.level.getServer(),
                            plan.day(), type, false);
            this.announce("§a反应器解析了「" + type.displayName() + "」的数据：今天的这场天灾已被拦下。");
        }
    }

    private int findSlot(DisasterSchedule.EventType type) {
        for (int index = 0; index < this.items.size(); index++) {
            ItemStack stack = this.items.get(index);
            if (!stack.isEmpty() && DisasterDataItem.typeOf(stack) == type) {
                return index;
            }
        }
        return -1;
    }

    /** 在反应器上方飘一行字（附近玩家才看得到）。 */
    private void announce(String message) {
        if (!(this.level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        Component component = Component.literal(message);
        for (net.minecraft.server.level.ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(this.worldPosition.getX() + 0.5D, this.worldPosition.getY(),
                    this.worldPosition.getZ() + 0.5D) < 32.0D * 32.0D) {
                player.displayClientMessage(component, false);
            }
        }
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                this.worldPosition.getX() + 0.5D, this.worldPosition.getY() + 1.2D,
                this.worldPosition.getZ() + 0.5D, 20, 0.35D, 0.5D, 0.35D, 0.02D);
    }
}
