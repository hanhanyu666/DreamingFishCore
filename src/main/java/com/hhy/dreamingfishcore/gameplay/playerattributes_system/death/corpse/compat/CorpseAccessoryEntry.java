package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** 尸体中一件饰品及其原始模组槽位。 */
public final class CorpseAccessoryEntry {
    public static final String CURIOS = "curios";
    public static final String ACCESSORIES = "accessories";

    private final String provider;
    private final String slotName;
    private final int slotIndex;
    private final boolean cosmetic;
    private ItemStack stack;

    public CorpseAccessoryEntry(String provider,
                                String slotName,
                                int slotIndex,
                                boolean cosmetic,
                                ItemStack stack) {
        this.provider = provider;
        this.slotName = slotName;
        this.slotIndex = slotIndex;
        this.cosmetic = cosmetic;
        this.stack = normalize(stack).copy();
    }

    public String provider() {
        return provider;
    }

    public String slotName() {
        return slotName;
    }

    public int slotIndex() {
        return slotIndex;
    }

    public boolean cosmetic() {
        return cosmetic;
    }

    public ItemStack stack() {
        return stack;
    }

    public void setStack(ItemStack stack) {
        this.stack = normalize(stack);
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public CorpseAccessoryEntry copy() {
        return new CorpseAccessoryEntry(provider, slotName, slotIndex, cosmetic, stack);
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Provider", provider);
        tag.putString("SlotName", slotName);
        tag.putInt("SlotIndex", slotIndex);
        tag.putBoolean("Cosmetic", cosmetic);
        if (!stack.isEmpty()) {
            tag.put("Stack", stack.save(registries));
        }
        return tag;
    }

    public static CorpseAccessoryEntry load(HolderLookup.Provider registries, CompoundTag tag) {
        ItemStack stack = tag.contains("Stack")
                ? ItemStack.parseOptional(registries, tag.getCompound("Stack"))
                : ItemStack.EMPTY;
        return new CorpseAccessoryEntry(
                tag.getString("Provider"),
                tag.getString("SlotName"),
                tag.getInt("SlotIndex"),
                tag.getBoolean("Cosmetic"),
                stack);
    }

    private static ItemStack normalize(ItemStack stack) {
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack;
    }
}
