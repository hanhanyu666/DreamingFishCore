package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.compat.CorpseAccessoryCompat;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.compat.CorpseAccessoryEntry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 尸体中保存的死亡物品。
 *
 * <p>原版物品栏、盔甲和副手分开保存，以便取回时优先放回原槽位；
 * 无法匹配到原槽位的模组掉落则保存在 additionalItems 中。</p>
 */
public final class DeathCorpseInventory {
    public static final int MAIN_SIZE = 36;
    public static final int ARMOR_SIZE = 4;
    public static final int OFFHAND_SIZE = 1;

    private NonNullList<ItemStack> mainInventory = NonNullList.withSize(MAIN_SIZE, ItemStack.EMPTY);
    private NonNullList<ItemStack> armorInventory = NonNullList.withSize(ARMOR_SIZE, ItemStack.EMPTY);
    private NonNullList<ItemStack> offhandInventory = NonNullList.withSize(OFFHAND_SIZE, ItemStack.EMPTY);
    private NonNullList<ItemStack> additionalItems = NonNullList.create();
    private NonNullList<ItemStack> equipment = NonNullList.withSize(EquipmentSlot.values().length, ItemStack.EMPTY);
    private List<CorpseAccessoryEntry> accessoryItems = new ArrayList<>();

    public static DeathCorpseInventory snapshot(Player player) {
        DeathCorpseInventory inventory = new DeathCorpseInventory();
        copySlots(player.getInventory().items, inventory.mainInventory);
        copySlots(player.getInventory().armor, inventory.armorInventory);
        copySlots(player.getInventory().offhand, inventory.offhandInventory);

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            inventory.equipment.set(slot.ordinal(), player.getItemBySlot(slot).copy());
        }
        return inventory;
    }

    private static void copySlots(List<ItemStack> source, NonNullList<ItemStack> destination) {
        int size = Math.min(source.size(), destination.size());
        for (int i = 0; i < size; i++) {
            destination.set(i, source.get(i).copy());
        }
    }

    /**
     * 只保留真正出现在 LivingDropsEvent 中的物品。
     * 消失诅咒、灵魂绑定等没有进入掉落集合的物品不会被错误复制进尸体。
     */
    public void processDrops(Collection<ItemEntity> entities) {
        List<ItemStack> unmatchedDrops = new ArrayList<>();
        for (ItemEntity entity : entities) {
            if (entity != null && !entity.getItem().isEmpty()) {
                unmatchedDrops.add(entity.getItem().copy());
            }
        }

        matchOriginalSlots(mainInventory, unmatchedDrops);
        matchOriginalSlots(armorInventory, unmatchedDrops);
        matchOriginalSlots(offhandInventory, unmatchedDrops);

        additionalItems.clear();
        for (ItemStack stack : unmatchedDrops) {
            if (!stack.isEmpty()) {
                additionalItems.add(stack.copy());
            }
        }
    }

    private static void matchOriginalSlots(NonNullList<ItemStack> originalSlots, List<ItemStack> drops) {
        for (int slot = 0; slot < originalSlots.size(); slot++) {
            ItemStack original = originalSlots.get(slot);
            if (original.isEmpty()) {
                continue;
            }

            int match = findExactMatch(drops, original);
            if (match < 0) {
                originalSlots.set(slot, ItemStack.EMPTY);
            } else {
                originalSlots.set(slot, drops.remove(match));
            }
        }
    }

    private static int findExactMatch(List<ItemStack> drops, ItemStack expected) {
        for (int i = 0; i < drops.size(); i++) {
            if (ItemStack.matches(drops.get(i), expected)) {
                return i;
            }
        }
        return -1;
    }

    public ItemStack getMain(int slot) {
        return mainInventory.get(slot);
    }

    public void setMain(int slot, ItemStack stack) {
        mainInventory.set(slot, normalize(stack));
    }

    public ItemStack getArmor(int slot) {
        return armorInventory.get(slot);
    }

    public void setArmor(int slot, ItemStack stack) {
        armorInventory.set(slot, normalize(stack));
    }

    public ItemStack getOffhand(int slot) {
        return offhandInventory.get(slot);
    }

    public void setOffhand(int slot, ItemStack stack) {
        offhandInventory.set(slot, normalize(stack));
    }

    public ItemStack getAdditional(int slot) {
        if (slot < 0) {
            return ItemStack.EMPTY;
        }
        if (slot < additionalItems.size()) {
            return additionalItems.get(slot);
        }
        int accessorySlot = slot - additionalItems.size();
        return accessorySlot < accessoryItems.size()
                ? accessoryItems.get(accessorySlot).stack()
                : ItemStack.EMPTY;
    }

    public void setAdditional(int slot, ItemStack stack) {
        if (slot < 0) {
            return;
        }
        if (slot < additionalItems.size()) {
            additionalItems.set(slot, normalize(stack));
            return;
        }
        int accessorySlot = slot - additionalItems.size();
        if (accessorySlot < accessoryItems.size()) {
            accessoryItems.get(accessorySlot).setStack(stack);
            return;
        }
        // 尸体菜单禁止放入物品；这里只为没有饰品尾部时保留原有容器兼容行为。
        if (accessoryItems.isEmpty()) {
            while (additionalItems.size() <= slot) {
                additionalItems.add(ItemStack.EMPTY);
            }
            additionalItems.set(slot, normalize(stack));
        }
    }

    public int getAdditionalSize() {
        return additionalItems.size() + accessoryItems.size();
    }

    public void setAccessoryItems(List<CorpseAccessoryEntry> entries) {
        accessoryItems = new ArrayList<>(entries.size());
        for (CorpseAccessoryEntry entry : entries) {
            if (!entry.isEmpty()) {
                accessoryItems.add(entry.copy());
            }
        }
    }

    public ItemStack getEquipment(EquipmentSlot slot) {
        int index = slot.ordinal();
        return index >= 0 && index < equipment.size() ? equipment.get(index) : ItemStack.EMPTY;
    }

    public boolean isEmpty() {
        return allEmpty(mainInventory)
                && allEmpty(armorInventory)
                && allEmpty(offhandInventory)
                && allEmpty(additionalItems)
                && accessoryItems.stream().allMatch(CorpseAccessoryEntry::isEmpty);
    }

    private static boolean allEmpty(List<ItemStack> stacks) {
        return stacks.stream().allMatch(ItemStack::isEmpty);
    }

    /**
     * 将尸体物品交还给玩家。原槽位空闲时优先原位恢复，其余物品进入主物品栏。
     *
     * @return 所有物品是否均已成功转移
     */
    public TransferResult transferAllTo(Player player, boolean restoreAccessorySlots) {
        transferPreferred(mainInventory, player.getInventory().items, player);
        transferPreferred(armorInventory, player.getInventory().armor, player);
        transferPreferred(offhandInventory, player.getInventory().offhand, player);

        for (int i = 0; i < additionalItems.size(); i++) {
            additionalItems.set(i, insertIntoPlayer(player, additionalItems.get(i)));
        }

        List<CorpseAccessoryEntry> restoredSlots = new ArrayList<>();
        if (restoreAccessorySlots) {
            boolean restoredInPass;
            do {
                restoredInPass = false;
                for (CorpseAccessoryEntry entry : accessoryItems) {
                    if (entry.isEmpty()) {
                        continue;
                    }
                    CorpseAccessoryEntry original = entry.copy();
                    if (CorpseAccessoryCompat.restore(player, entry)) {
                        restoredSlots.add(original);
                        restoredInPass = true;
                    }
                }
            } while (restoredInPass && accessoryItems.stream().anyMatch(entry -> !entry.isEmpty()));
        }

        for (CorpseAccessoryEntry entry : accessoryItems) {
            if (!entry.isEmpty()) {
                entry.setStack(insertIntoPlayer(player, entry.stack()));
            }
        }

        compactAdditionalItems();
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        return new TransferResult(isEmpty(), List.copyOf(restoredSlots));
    }

    private static void transferPreferred(NonNullList<ItemStack> corpseSlots,
                                          NonNullList<ItemStack> playerSlots,
                                          Player player) {
        int size = Math.min(corpseSlots.size(), playerSlots.size());
        for (int i = 0; i < size; i++) {
            ItemStack corpseStack = corpseSlots.get(i);
            if (corpseStack.isEmpty()) {
                continue;
            }

            if (playerSlots.get(i).isEmpty()) {
                playerSlots.set(i, corpseStack.copy());
                corpseSlots.set(i, ItemStack.EMPTY);
            } else {
                corpseSlots.set(i, insertIntoPlayer(player, corpseStack));
            }
        }
    }

    private static ItemStack insertIntoPlayer(Player player, ItemStack source) {
        if (source.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = source.copy();
        if (player.getAbilities().instabuild) {
            insertWithoutCreativeVoid(player, remainder);
        } else {
            player.getInventory().add(remainder);
        }
        return remainder.isEmpty() ? ItemStack.EMPTY : remainder;
    }

    /**
     * Inventory#add deliberately deletes an uninsertable remainder for players with infinite materials.
     * That vanilla creative-mode convenience is unsafe for a corpse: it would report success and destroy the
     * only persisted copy. Insert slot by slot instead, leaving every item that does not fit in the corpse.
     */
    private static void insertWithoutCreativeVoid(Player player, ItemStack remainder) {
        while (!remainder.isEmpty()) {
            int slot = player.getInventory().getSlotWithRemainingSpace(remainder);
            if (slot < 0) {
                slot = player.getInventory().getFreeSlot();
            }
            if (slot < 0) {
                return;
            }

            ItemStack destination = player.getInventory().getItem(slot);
            if (destination.isEmpty()) {
                int moved = Math.min(remainder.getCount(), remainder.getMaxStackSize());
                ItemStack inserted = remainder.split(moved);
                inserted.setPopTime(5);
                player.getInventory().setItem(slot, inserted);
                continue;
            }

            int available = destination.getMaxStackSize() - destination.getCount();
            if (available <= 0) {
                return;
            }
            int moved = Math.min(available, remainder.getCount());
            destination.grow(moved);
            destination.setPopTime(5);
            remainder.shrink(moved);
        }
    }

    public void compactAdditionalItems() {
        NonNullList<ItemStack> compacted = NonNullList.create();
        for (ItemStack stack : additionalItems) {
            if (!stack.isEmpty()) {
                compacted.add(stack);
            }
        }
        additionalItems = compacted;
        accessoryItems.removeIf(CorpseAccessoryEntry::isEmpty);
    }

    public DeathCorpseInventory copy() {
        DeathCorpseInventory copy = new DeathCorpseInventory();
        copySlots(mainInventory, copy.mainInventory);
        copySlots(armorInventory, copy.armorInventory);
        copySlots(offhandInventory, copy.offhandInventory);
        copy.additionalItems = copyVariableList(additionalItems);
        copy.accessoryItems = copyAccessoryItems(accessoryItems);
        copySlots(equipment, copy.equipment);
        return copy;
    }

    private static List<CorpseAccessoryEntry> copyAccessoryItems(List<CorpseAccessoryEntry> source) {
        List<CorpseAccessoryEntry> copy = new ArrayList<>(source.size());
        for (CorpseAccessoryEntry entry : source) {
            copy.add(entry.copy());
        }
        return copy;
    }

    private static NonNullList<ItemStack> copyVariableList(List<ItemStack> source) {
        NonNullList<ItemStack> copy = NonNullList.createWithCapacity(source.size());
        for (ItemStack stack : source) {
            copy.add(stack.copy());
        }
        return copy;
    }

    public void clear() {
        // 这些列表对应固定的容器槽位，不能调用 clear() 使长度变成 0；否则
        // 玩家在容器仍打开时再次读取槽位会触发越界，并破坏后续快照结构。
        clearFixedSlots(mainInventory);
        clearFixedSlots(armorInventory);
        clearFixedSlots(offhandInventory);
        additionalItems.clear();
        accessoryItems.clear();
    }

    private static void clearFixedSlots(List<ItemStack> slots) {
        for (int index = 0; index < slots.size(); index++) {
            slots.set(index, ItemStack.EMPTY);
        }
    }

    public List<ItemStack> getAllItems() {
        List<ItemStack> items = new ArrayList<>();
        appendNonEmpty(items, mainInventory);
        appendNonEmpty(items, armorInventory);
        appendNonEmpty(items, offhandInventory);
        appendNonEmpty(items, additionalItems);
        for (CorpseAccessoryEntry entry : accessoryItems) {
            if (!entry.isEmpty()) {
                items.add(entry.stack());
            }
        }
        return items;
    }

    private static void appendNonEmpty(List<ItemStack> destination, List<ItemStack> source) {
        for (ItemStack stack : source) {
            if (!stack.isEmpty()) {
                destination.add(stack);
            }
        }
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Main", saveIndexedList(registries, mainInventory));
        tag.put("Armor", saveIndexedList(registries, armorInventory));
        tag.put("Offhand", saveIndexedList(registries, offhandInventory));
        tag.put("Additional", saveIndexedList(registries, additionalItems));
        tag.put("Equipment", saveIndexedList(registries, equipment));
        ListTag accessories = new ListTag();
        for (CorpseAccessoryEntry entry : accessoryItems) {
            if (!entry.isEmpty()) {
                accessories.add(entry.save(registries));
            }
        }
        tag.put("Accessories", accessories);
        return tag;
    }

    public static DeathCorpseInventory load(HolderLookup.Provider registries, CompoundTag tag) {
        DeathCorpseInventory inventory = new DeathCorpseInventory();
        loadIndexedList(registries, tag.getList("Main", CompoundTag.TAG_COMPOUND), inventory.mainInventory, false);
        loadIndexedList(registries, tag.getList("Armor", CompoundTag.TAG_COMPOUND), inventory.armorInventory, false);
        loadIndexedList(registries, tag.getList("Offhand", CompoundTag.TAG_COMPOUND), inventory.offhandInventory, false);
        loadIndexedList(registries, tag.getList("Additional", CompoundTag.TAG_COMPOUND), inventory.additionalItems, true);
        loadIndexedList(registries, tag.getList("Equipment", CompoundTag.TAG_COMPOUND), inventory.equipment, false);
        ListTag accessories = tag.getList("Accessories", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < accessories.size(); i++) {
            CorpseAccessoryEntry entry = CorpseAccessoryEntry.load(registries, accessories.getCompound(i));
            if (!entry.isEmpty()) {
                inventory.accessoryItems.add(entry);
            }
        }
        inventory.compactAdditionalItems();
        return inventory;
    }

    private static ListTag saveIndexedList(HolderLookup.Provider registries, List<ItemStack> stacks) {
        ListTag list = new ListTag();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", i);
            entry.put("Stack", stack.save(registries));
            list.add(entry);
        }
        return list;
    }

    private static void loadIndexedList(HolderLookup.Provider registries,
                                        ListTag source,
                                        NonNullList<ItemStack> destination,
                                        boolean grow) {
        for (int i = 0; i < source.size(); i++) {
            CompoundTag entry = source.getCompound(i);
            int slot = entry.getInt("Slot");
            if (slot < 0 || (!grow && slot >= destination.size())) {
                continue;
            }
            while (grow && destination.size() <= slot) {
                destination.add(ItemStack.EMPTY);
            }
            ItemStack stack = ItemStack.parseOptional(registries, entry.getCompound("Stack"));
            destination.set(slot, stack);
        }
    }

    private static ItemStack normalize(ItemStack stack) {
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    public record TransferResult(boolean complete, List<CorpseAccessoryEntry> restoredAccessorySlots) {
    }
}
