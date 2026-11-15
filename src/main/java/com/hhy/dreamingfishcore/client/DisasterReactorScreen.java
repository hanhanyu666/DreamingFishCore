package com.hhy.dreamingfishcore.client;

import com.hhy.dreamingfishcore.block.DisasterReactorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 天灾反应器的界面：**板条箱式的 3×9 格子**，没有任何贴图依赖（全用矩形画）。
 *
 * <p>不画贴图是有意的：这个面板只有"格子 + 两条文字"，用代码画反而便于跟着主题色改，
 * 也省掉一张 176×166 的 PNG 资源。</p>
 */
public class DisasterReactorScreen extends AbstractContainerScreen<DisasterReactorMenu> {

    /** 背景、边框、格子底色。 */
    private static final int COLOR_BACKGROUND = 0xE6121318;
    private static final int COLOR_BORDER = 0xFF35393F;
    private static final int COLOR_SLOT = 0xFF212429;
    private static final int COLOR_SLOT_INNER = 0xFF2B2F36;

    public DisasterReactorScreen(DisasterReactorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, COLOR_BACKGROUND);
        // 外框
        graphics.fill(x, y, x + this.imageWidth, y + 1, COLOR_BORDER);
        graphics.fill(x, y + this.imageHeight - 1, x + this.imageWidth, y + this.imageHeight, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + this.imageHeight, COLOR_BORDER);
        graphics.fill(x + this.imageWidth - 1, y, x + this.imageWidth, y + this.imageHeight, COLOR_BORDER);

        // 反应器 3×9 格子
        for (int row = 0; row < DisasterReactorMenu.ROWS; row++) {
            for (int column = 0; column < DisasterReactorMenu.COLUMNS; column++) {
                int slotX = x + 7 + column * 18;
                int slotY = y + 17 + row * 18;
                graphics.fill(slotX, slotY, slotX + 18, slotY + 18, COLOR_SLOT);
                graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, COLOR_SLOT_INNER);
            }
        }
        // 玩家背包格子
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int slotX = x + 7 + column * 18;
                int slotY = y + 83 + row * 18;
                graphics.fill(slotX, slotY, slotX + 18, slotY + 18, COLOR_SLOT);
                graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, COLOR_SLOT_INNER);
            }
        }
        for (int column = 0; column < 9; column++) {
            int slotX = x + 7 + column * 18;
            int slotY = y + 141;
            graphics.fill(slotX, slotY, slotX + 18, slotY + 18, COLOR_SLOT);
            graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, COLOR_SLOT_INNER);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        // 底部一行提示：只吃天灾分析数据
        graphics.drawString(this.font, "§8只接受天灾分析数据 · 天灾到来时自动拦截",
                8, this.imageHeight - 82, 0xFF808080, false);
    }
}
