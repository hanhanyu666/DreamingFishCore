package com.hhy.dreamingfishcore.utils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ItemStackDataHelper {
    private ItemStackDataHelper() {
    }

    public static boolean hasTag(ItemStack stack) {
        return stack.hasTag();
    }

    public static CompoundTag getTag(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? null : tag.copy();
    }

    public static void setTag(ItemStack stack, CompoundTag tag) {
        stack.setTag(tag == null || tag.isEmpty() ? null : tag.copy());
    }

    public static CompoundTag saveSimple(ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        tag.putString("id", itemId.toString());
        tag.putInt("count", stack.getCount());
        CompoundTag customData = getTag(stack);
        if (customData != null && !customData.isEmpty()) {
            tag.put("customData", customData);
        }
        return tag;
    }

    public static ItemStack loadSimple(CompoundTag tag) {
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(tag.getString("id")));
        ItemStack stack = new ItemStack(item, Math.max(1, tag.getInt("count")));
        if (tag.contains("customData")) {
            setTag(stack, tag.getCompound("customData"));
        }
        return stack;
    }
}
