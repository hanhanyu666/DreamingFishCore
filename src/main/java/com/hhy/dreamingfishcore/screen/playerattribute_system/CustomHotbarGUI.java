package com.hhy.dreamingfishcore.screen.playerattribute_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public class CustomHotbarGUI {
    private static final int SLOT_COUNT = 9;
    private static final int SLOT_STEP = 20;
    private static final int HOTBAR_PADDING = 3;
    private static final int HOTBAR_WIDTH = HOTBAR_PADDING * 2 + SLOT_COUNT * SLOT_STEP;
    private static final int HOTBAR_HEIGHT = 24;
    private static final int HOTBAR_BOTTOM_MARGIN = 4;
    private static final int ITEM_SIZE = 16;
    private static final int HOTBAR_BG = 0x48060708;
    private static final int HOTBAR_BG_INNER = 0x22101012;
    private static final int HOTBAR_DIVIDER = 0x185E5E62;
    private static final int SELECTED_UNDERLINE = 0x90C8C8C2;
    private static final float HOTBAR_IDLE_SCALE = 0.75f;
    private static final float HOTBAR_ACTIVE_SCALE = 1.0f;
    private static final long HOTBAR_SCALE_UP_MS = 150L;
    private static final long HOTBAR_ACTIVE_HOLD_MS = 850L;
    private static final long HOTBAR_SCALE_DOWN_MS = 320L;
    private static final long HOTBAR_ANIMATION_TOTAL_MS =
            HOTBAR_SCALE_UP_MS + HOTBAR_ACTIVE_HOLD_MS + HOTBAR_SCALE_DOWN_MS;

    private static int lastSelectedSlot = -1;
    private static long lastHotbarInteractionTime = 0L;

    @SubscribeEvent
    public static void replaceVanillaHotbar(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.HOTBAR.equals(event.getName())) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (!shouldRenderCustomHotbar(mc)) {
            return;
        }

        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void renderCustomHotbar(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!shouldRenderCustomHotbar(mc)) {
            return;
        }

        Player player = mc.player;
        updateHotbarInteraction(player);

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        float scale = getHotbarAnimationScale();
        int x = (screenWidth - HOTBAR_WIDTH) / 2;
        int y = getHotbarBaseTopY(screenHeight);
        int anchorY = screenHeight - HOTBAR_BOTTOM_MARGIN;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(screenWidth / 2.0f, anchorY, 0.0f);
        guiGraphics.pose().scale(scale, scale, 1.0f);
        guiGraphics.pose().translate(-screenWidth / 2.0f, -anchorY, 0.0f);

        drawHotbarFrame(guiGraphics, x, y);
        drawHotbarItems(guiGraphics, mc, player, x, y);

        guiGraphics.pose().popPose();
    }

    @SubscribeEvent
    public static void hideVanillaSurvivalBars(RenderGuiLayerEvent.Pre event) {
        // EXPERIENCE_BAR is redrawn as a three-part bar in CustomStatueGUI.
        if (!VanillaGuiLayers.EXPERIENCE_BAR.equals(event.getName())
                && !VanillaGuiLayers.EXPERIENCE_LEVEL.equals(event.getName())
                && !VanillaGuiLayers.FOOD_LEVEL.equals(event.getName())
                && !VanillaGuiLayers.ARMOR_LEVEL.equals(event.getName())) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()
                || mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return;
        }

        event.setCanceled(true);
    }

    public static float getHotbarAnimationScale() {
        long elapsed = System.currentTimeMillis() - lastHotbarInteractionTime;

        if (elapsed <= HOTBAR_SCALE_UP_MS) {
            float t = Math.max(0.0f, elapsed / (float) HOTBAR_SCALE_UP_MS);
            float eased = 1.0f - (1.0f - t) * (1.0f - t);
            return HOTBAR_IDLE_SCALE + (HOTBAR_ACTIVE_SCALE - HOTBAR_IDLE_SCALE) * eased;
        }

        if (elapsed <= HOTBAR_SCALE_UP_MS + HOTBAR_ACTIVE_HOLD_MS) {
            return HOTBAR_ACTIVE_SCALE;
        }

        float t = Math.min(1.0f,
                (elapsed - HOTBAR_SCALE_UP_MS - HOTBAR_ACTIVE_HOLD_MS) / (float) HOTBAR_SCALE_DOWN_MS);
        float eased = t * t * (3.0f - 2.0f * t);
        return HOTBAR_ACTIVE_SCALE + (HOTBAR_IDLE_SCALE - HOTBAR_ACTIVE_SCALE) * eased;
    }

    public static int getAnimatedHotbarTopY(int screenHeight) {
        int scaledHeight = Math.round(HOTBAR_HEIGHT * getHotbarAnimationScale());
        return screenHeight - HOTBAR_BOTTOM_MARGIN - scaledHeight;
    }

    private static int getHotbarBaseTopY(int screenHeight) {
        return screenHeight - HOTBAR_BOTTOM_MARGIN - HOTBAR_HEIGHT;
    }

    private static boolean shouldRenderCustomHotbar(Minecraft mc) {
        return mc.player != null
                && mc.screen == null
                && !mc.player.isDeadOrDying()
                && !mc.options.hideGui
                && !mc.getDebugOverlay().showDebugScreen()
                && (mc.gameMode == null || mc.gameMode.getPlayerMode() != GameType.SPECTATOR);
    }

    private static void updateHotbarInteraction(Player player) {
        int selectedSlot = player.getInventory().selected;
        if (lastSelectedSlot == -1) {
            lastSelectedSlot = selectedSlot;
            return;
        }

        if (selectedSlot != lastSelectedSlot) {
            lastSelectedSlot = selectedSlot;
            registerHotbarInteraction();
        }
    }

    private static void registerHotbarInteraction() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastHotbarInteractionTime;

        if (lastHotbarInteractionTime == 0L || elapsed > HOTBAR_ANIMATION_TOTAL_MS) {
            lastHotbarInteractionTime = now;
            return;
        }

        if (elapsed >= HOTBAR_SCALE_UP_MS) {
            lastHotbarInteractionTime = now - HOTBAR_SCALE_UP_MS;
        }
    }

    private static void drawHotbarFrame(GuiGraphics guiGraphics, int x, int y) {
        drawSoftRoundedRect(guiGraphics, x, y, HOTBAR_WIDTH, HOTBAR_HEIGHT, HOTBAR_BG);
        guiGraphics.fill(x + 3, y + 2, x + HOTBAR_WIDTH - 3, y + HOTBAR_HEIGHT - 2, HOTBAR_BG_INNER);

        for (int i = 1; i < SLOT_COUNT; i++) {
            int dividerX = x + HOTBAR_PADDING + i * SLOT_STEP;
            guiGraphics.fill(dividerX, y + 5, dividerX + 1, y + HOTBAR_HEIGHT - 5, HOTBAR_DIVIDER);
        }
    }

    private static void drawHotbarItems(GuiGraphics guiGraphics, Minecraft mc, Player player, int x, int y) {
        int selectedSlot = player.getInventory().selected;
        for (int i = 0; i < SLOT_COUNT; i++) {
            int slotX = x + HOTBAR_PADDING + i * SLOT_STEP;
            int slotY = y + 2;

            if (i == selectedSlot) {
                guiGraphics.fill(slotX + 4, y + HOTBAR_HEIGHT - 3, slotX + SLOT_STEP - 4,
                        y + HOTBAR_HEIGHT - 1, SELECTED_UNDERLINE);
            }

            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                int itemX = slotX + (SLOT_STEP - ITEM_SIZE) / 2;
                int itemY = y + (HOTBAR_HEIGHT - ITEM_SIZE) / 2;
                guiGraphics.renderItem(stack, itemX, itemY);
                guiGraphics.renderItemDecorations(mc.font, stack, itemX, itemY);
            }
        }
    }

    private static void drawSoftRoundedRect(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x + 2, y, x + width - 2, y + height, color);
        guiGraphics.fill(x, y + 2, x + 2, y + height - 2, color);
        guiGraphics.fill(x + width - 2, y + 2, x + width, y + height - 2, color);
        guiGraphics.fill(x + 1, y + 1, x + 2, y + 2, color);
        guiGraphics.fill(x + width - 2, y + 1, x + width - 1, y + 2, color);
        guiGraphics.fill(x + 1, y + height - 2, x + 2, y + height - 1, color);
        guiGraphics.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, color);
    }
}
