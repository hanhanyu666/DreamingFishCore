package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.node.Size;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

/** 物品图标。默认悬停显示原版物品提示。 */
public class ItemIcon extends UiNode<ItemIcon> {
    private Supplier<ItemStack> stack = () -> ItemStack.EMPTY;
    private float iconSize = 16.0F;
    private boolean decorations = true;

    public ItemIcon() {
        pointerEvents(true);
        tooltip(this::itemTooltip);
    }

    public static ItemIcon of(ItemStack stack) {
        ItemIcon icon = new ItemIcon();
        icon.stack = () -> stack;
        return icon;
    }

    public static ItemIcon of(Supplier<ItemStack> stack) {
        ItemIcon icon = new ItemIcon();
        icon.stack = stack;
        return icon;
    }

    public ItemIcon iconSize(float value) {
        iconSize = value;
        markDirty();
        return this;
    }

    public ItemIcon decorations(boolean value) {
        decorations = value;
        return this;
    }

    public ItemIcon noTooltip() {
        tooltip((Supplier<List<Component>>) null);
        return this;
    }

    private List<Component> itemTooltip() {
        ItemStack current = stack.get();
        if (current == null || current.isEmpty()) {
            return List.of();
        }
        return Screen.getTooltipFromItem(Minecraft.getInstance(), current);
    }

    @Override
    protected void measureContent(float availableWidth, float availableHeight, Size out) {
        out.set(iconSize, iconSize);
    }

    @Override
    protected void paintContent(UiCanvas canvas) {
        ItemStack current = stack.get();
        if (current == null || current.isEmpty()) {
            return;
        }
        float x = padLeft() + (innerWidth() - iconSize) * 0.5F;
        float y = padTop() + (innerHeight() - iconSize) * 0.5F;
        canvas.item(current, x, y, iconSize, decorations);
    }
}
