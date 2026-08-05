package com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.item.items.Item_AidKit;
import com.hhy.dreamingfishcore.item.items.Potion_RestoreUnInfected;
import com.hhy.dreamingfishcore.item.items.medicine.Easy_Aid_Kit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public class CustomHotbarGUI {
    private static final int SLOT_COUNT = 9;
    private static final int SLOT_STEP = 20;
    private static final int HOTBAR_PADDING = 3;
    private static final int HOTBAR_WIDTH = HOTBAR_PADDING * 2 + SLOT_COUNT * SLOT_STEP;
    private static final int HOTBAR_HEIGHT = 24;
    private static final int HOTBAR_BOTTOM_MARGIN = 4;
    private static final int ITEM_SIZE = 16;
    private static final int OFFHAND_SLOT_WIDTH = HOTBAR_PADDING * 2 + SLOT_STEP;
    private static final int OFFHAND_SLOT_GAP = 4;
    private static final int HOTBAR_BG = 0x48060708;
    private static final int HOTBAR_BG_INNER = 0x22101012;
    private static final int HOTBAR_DIVIDER = 0x185E5E62;
    private static final int SELECTED_UNDERLINE = 0x90C8C8C2;
    private static final float HOTBAR_IDLE_SCALE = 0.7f;
    private static final float HOTBAR_ACTIVE_SCALE = 0.9f;
    private static final long HOTBAR_SCALE_UP_MS = 150L;
    private static final long HOTBAR_ACTIVE_HOLD_MS = 850L;
    private static final long HOTBAR_SCALE_DOWN_MS = 320L;
    private static final long HOTBAR_ANIMATION_TOTAL_MS =
            HOTBAR_SCALE_UP_MS + HOTBAR_ACTIVE_HOLD_MS + HOTBAR_SCALE_DOWN_MS;
    private static final int MEDICINE_HUD_WIDTH = 76;
    private static final int MEDICINE_HUD_HEIGHT = 26;
    private static final int MEDICINE_HUD_GAP = 5;
    private static final int BOTTOM_STATUS_BAR_HEIGHT = 5;
    private static final int BOTTOM_STATUS_HOTBAR_GAP = 4;
    private static final int MEDICINE_RING_RADIUS = 9;
    private static final int MEDICINE_RING_THICKNESS = 3;
    private static final int MEDICINE_PANEL_BG = 0x64060809;
    private static final int MEDICINE_PANEL_INNER = 0x2AFFFFFF;
    private static final int MEDICINE_RING_TRACK = 0x6850524D;
    private static final int MEDICINE_RING_FILL = 0xFFE1E9DD;
    private static final int MEDICINE_RING_HEAD = 0xFFFFFFFF;
    private static final int MEDICINE_TEXT_COLOR = 0xFFECE8DD;
    private static final int MEDICINE_DIM_TEXT_COLOR = 0xFF929891;

    private static int lastSelectedSlot = -1;
    private static long lastHotbarInteractionTime = 0L;
    private static Item localMedicineUseItem = null;
    private static InteractionHand localMedicineUseHand = InteractionHand.MAIN_HAND;
    private static long localMedicineUseStartTime = 0L;
    private static long localMedicineUseEndTime = 0L;
    private static int localMedicineUseMaxActiveTicks = 0;
    private static boolean localMedicineUsePersistent = false;
    private static long lastMedicineInputTime = 0L;

    @SubscribeEvent
    public static void replaceVanillaHotbar(RenderGuiOverlayEvent.Pre event) {
        if (!VanillaGuiOverlay.HOTBAR.id().equals(event.getOverlay().id())) {
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
        MedicineUseInfo medicineUseInfo = getMedicineUseInfo(player);
        if (medicineUseInfo != null) {
            registerHotbarInteraction();
        }

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
        drawOffhandSlot(guiGraphics, mc, player, x, y);

        guiGraphics.pose().popPose();

        drawMedicineUseHud(guiGraphics, mc, screenWidth, screenHeight, medicineUseInfo);
    }

    @SubscribeEvent
    public static void trackMedicineUseInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (!shouldRenderCustomHotbar(mc)) {
            return;
        }

        Player player = mc.player;
        if (player == null) {
            return;
        }

        startLocalMedicineUse(player, event.getHand());
    }

    @SubscribeEvent
    public static void hideVanillaSurvivalBars(RenderGuiOverlayEvent.Pre event) {
        // These overlays are redrawn by the custom status HUD.
        if (!VanillaGuiOverlay.EXPERIENCE_BAR.id().equals(event.getOverlay().id())
                && !VanillaGuiOverlay.FOOD_LEVEL.id().equals(event.getOverlay().id())
                && !VanillaGuiOverlay.ARMOR_LEVEL.id().equals(event.getOverlay().id())
                && !VanillaGuiOverlay.AIR_LEVEL.id().equals(event.getOverlay().id())) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !isHudVisibleScreen(mc) || mc.options.hideGui || mc.options.renderDebug
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

    public static int getMedicineUseVerticalOffset() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui || mc.options.renderDebug) {
            return 0;
        }

        return 0;
    }

    private static int getHotbarBaseTopY(int screenHeight) {
        return screenHeight - HOTBAR_BOTTOM_MARGIN - HOTBAR_HEIGHT;
    }

    private static boolean shouldRenderCustomHotbar(Minecraft mc) {
        return mc.player != null
                && isHudVisibleScreen(mc)
                && !mc.player.isDeadOrDying()
                && !mc.options.hideGui
                && !mc.options.renderDebug
                && (mc.gameMode == null || mc.gameMode.getPlayerMode() != GameType.SPECTATOR);
    }

    static boolean isHudVisibleScreen(Minecraft mc) {
        return mc.screen == null
                || mc.screen instanceof PauseScreen
                || mc.screen instanceof ChatScreen;
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

    private static void drawOffhandSlot(GuiGraphics guiGraphics, Minecraft mc, Player player, int hotbarX, int y) {
        ItemStack stack = player.getOffhandItem();
        if (stack.isEmpty()) {
            return;
        }

        boolean renderOnLeft = player.getMainArm().getOpposite() == HumanoidArm.LEFT;
        int x = renderOnLeft
                ? hotbarX - OFFHAND_SLOT_GAP - OFFHAND_SLOT_WIDTH
                : hotbarX + HOTBAR_WIDTH + OFFHAND_SLOT_GAP;

        drawSoftRoundedRect(guiGraphics, x, y, OFFHAND_SLOT_WIDTH, HOTBAR_HEIGHT, HOTBAR_BG);
        guiGraphics.fill(x + 3, y + 2, x + OFFHAND_SLOT_WIDTH - 3, y + HOTBAR_HEIGHT - 2, HOTBAR_BG_INNER);

        int itemX = x + (OFFHAND_SLOT_WIDTH - ITEM_SIZE) / 2;
        int itemY = y + (HOTBAR_HEIGHT - ITEM_SIZE) / 2;
        guiGraphics.renderItem(stack, itemX, itemY);
        guiGraphics.renderItemDecorations(mc.font, stack, itemX, itemY);
    }

    private static MedicineUseInfo getMedicineUseInfo(Player player) {
        if (player.isUsingItem()) {
            ItemStack useStack = player.getUseItem();
            if (isMedicineStack(useStack)) {
                int totalTicks = useStack.getUseDuration();
                int remainingTicks = player.getUseItemRemainingTicks();
                if (totalTicks > 0 && remainingTicks >= 0) {
                    float progress = (totalTicks - remainingTicks) / (float) totalTicks;
                    progress = Math.max(0.0f, Math.min(1.0f, progress));
                    String label = isAidKitStack(useStack) ? "准备中" : "使用中";
                    return new MedicineUseInfo(progress, Math.max(0, remainingTicks), label);
                }
            }
        }

        return getLocalMedicineUseInfo(player);
    }

    private static void startLocalMedicineUse(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isMedicineStack(stack)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastMedicineInputTime < 120L) {
            return;
        }
        lastMedicineInputTime = now;

        int durationTicks = stack.getUseDuration();
        if (durationTicks <= 0) {
            durationTicks = 40;
        }

        if ((localMedicineUseItem == stack.getItem()
                && localMedicineUseHand == hand
                && now < localMedicineUseEndTime) || localMedicineUsePersistent) {
            clearLocalMedicineUse();
            return;
        }

        localMedicineUseItem = stack.getItem();
        localMedicineUseHand = hand;
        localMedicineUseStartTime = now;
        localMedicineUseEndTime = now + durationTicks * 50L;
        localMedicineUseMaxActiveTicks = getMedicineActiveBudgetTicks(player, stack);
        localMedicineUsePersistent = isAidKitStack(stack)
                && stack.getDamageValue() < stack.getMaxDamage()
                && player.getHealth() < player.getMaxHealth();
        registerHotbarInteraction();
    }

    private static MedicineUseInfo getLocalMedicineUseInfo(Player player) {
        long now = System.currentTimeMillis();
        if (localMedicineUseItem == null || !isHoldingLocalMedicine(player)) {
            clearLocalMedicineUse();
            return null;
        }

        if (!localMedicineUsePersistent && now >= localMedicineUseEndTime) {
            clearLocalMedicineUse();
            return null;
        }

        if (localMedicineUsePersistent && shouldStopPersistentMedicine(player)) {
            clearLocalMedicineUse();
            return null;
        }

        long durationMs = Math.max(1L, localMedicineUseEndTime - localMedicineUseStartTime);
        if (now < localMedicineUseEndTime) {
            float progress = (now - localMedicineUseStartTime) / (float) durationMs;
            progress = Math.max(0.0f, Math.min(1.0f, progress));
            int remainingTicks = (int) Math.max(0L, (localMedicineUseEndTime - now + 49L) / 50L);
            return new MedicineUseInfo(progress, remainingTicks, "准备中");
        }

        ItemStack stack = findHeldLocalMedicine(player);
        int remainingTicks = getEstimatedRemainingMedicineTicks(stack,
                (int) Math.max(0L, (now - localMedicineUseEndTime) / 50L));
        int maxTicks = Math.max(1, localMedicineUseMaxActiveTicks);
        float progress = Math.max(0.0f, Math.min(1.0f, remainingTicks / (float) maxTicks));
        return new MedicineUseInfo(progress, remainingTicks, "治疗中");
    }

    private static boolean shouldStopPersistentMedicine(Player player) {
        ItemStack stack = player.getItemInHand(localMedicineUseHand);
        if (stack.getItem() != localMedicineUseItem) {
            stack = findHeldLocalMedicine(player);
        }

        if (stack.isEmpty()) {
            return true;
        }

        return stack.getDamageValue() >= stack.getMaxDamage()
                || player.getHealth() >= player.getMaxHealth();
    }

    private static ItemStack findHeldLocalMedicine(Player player) {
        if (player.getMainHandItem().getItem() == localMedicineUseItem) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().getItem() == localMedicineUseItem) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    private static boolean isHoldingLocalMedicine(Player player) {
        return !findHeldLocalMedicine(player).isEmpty();
    }

    private static boolean isAidKitStack(ItemStack stack) {
        return stack.getItem() instanceof Item_AidKit
                || stack.getItem() instanceof Easy_Aid_Kit;
    }

    private static void clearLocalMedicineUse() {
        localMedicineUseItem = null;
        localMedicineUseHand = InteractionHand.MAIN_HAND;
        localMedicineUseStartTime = 0L;
        localMedicineUseEndTime = 0L;
        localMedicineUseMaxActiveTicks = 0;
        localMedicineUsePersistent = false;
    }

    private static boolean isMedicineStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        return isAidKitStack(stack)
                || stack.getItem() instanceof Potion_RestoreUnInfected;
    }

    private static void drawMedicineUseHud(GuiGraphics guiGraphics, Minecraft mc, int screenWidth, int screenHeight,
                                           MedicineUseInfo medicineUseInfo) {
        if (medicineUseInfo == null) {
            return;
        }

        int x = (screenWidth - MEDICINE_HUD_WIDTH) / 2;
        int statusBarY = CustomHotbarGUI.getAnimatedHotbarTopY(screenHeight)
                - BOTTOM_STATUS_HOTBAR_GAP - BOTTOM_STATUS_BAR_HEIGHT;
        int y = Math.max(2, statusBarY - MEDICINE_HUD_GAP - MEDICINE_HUD_HEIGHT);
        int centerX = x + 14;
        int centerY = y + MEDICINE_HUD_HEIGHT / 2;

        drawSoftRoundedRect(guiGraphics, x, y, MEDICINE_HUD_WIDTH, MEDICINE_HUD_HEIGHT, MEDICINE_PANEL_BG);
        guiGraphics.fill(x + 3, y + 2, x + MEDICINE_HUD_WIDTH - 3, y + MEDICINE_HUD_HEIGHT - 2,
                0x18000000);
        guiGraphics.fill(x + 5, y + 3, x + MEDICINE_HUD_WIDTH - 5, y + 4, MEDICINE_PANEL_INNER);

        drawMedicineProgressRing(guiGraphics, centerX, centerY, medicineUseInfo.progress());
        drawMedicineGlyph(guiGraphics, centerX, centerY);

        String timeText = formatUseTime(medicineUseInfo.timeTicks());
        int timeX = x + 30;
        int timeY = y + 5;
        guiGraphics.drawString(mc.font, timeText, timeX + 1, timeY + 1, 0x80000000, false);
        guiGraphics.drawString(mc.font, timeText, timeX, timeY, MEDICINE_TEXT_COLOR, false);
        guiGraphics.drawString(mc.font, medicineUseInfo.label(), timeX, timeY + 10, MEDICINE_DIM_TEXT_COLOR, false);
    }

    private static int getEstimatedRemainingMedicineTicks(ItemStack stack, int activeElapsedTicks) {
        if (stack.isEmpty()) {
            return 0;
        }

        int intervalTicks = getMedicineIntervalTicks(stack);
        int currentDurabilityBudget = Math.max(0, stack.getMaxDamage() - stack.getDamageValue()) * intervalTicks;
        int plannedRemainingTicks = localMedicineUseMaxActiveTicks - activeElapsedTicks;
        int remainingTicks = Math.min(plannedRemainingTicks, currentDurabilityBudget);
        if (remainingTicks <= 0) {
            return 0;
        }

        return remainingTicks;
    }

    private static int getMedicineActiveBudgetTicks(Player player, ItemStack stack) {
        if (!isAidKitStack(stack)) {
            return Math.max(1, stack.getUseDuration());
        }

        int maxUses = Math.max(1, getRemainingMedicineUses(player, stack));
        return maxUses * getMedicineIntervalTicks(stack);
    }

    private static int getMedicineIntervalTicks(ItemStack stack) {
        if (stack.getItem() instanceof Item_AidKit aidKit) {
            return Math.max(1, aidKit.getHealInterval());
        }

        return 20;
    }

    private static int getRemainingMedicineUses(Player player, ItemStack stack) {
        int remainingDurability = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        if (remainingDurability <= 0) {
            return 0;
        }

        double healAmount = getMedicineHealAmount(stack);
        if (healAmount <= 0.0D) {
            return remainingDurability;
        }

        double missingHealth = Math.max(0.0D, player.getMaxHealth() - player.getHealth());
        int neededHeals = (int) Math.ceil(missingHealth / healAmount);
        return Math.max(0, Math.min(remainingDurability, neededHeals));
    }

    private static double getMedicineHealAmount(ItemStack stack) {
        if (stack.getItem() instanceof Item_AidKit aidKit) {
            return aidKit.getPerHealAmount();
        }

        return 1.0D;
    }

    private static String formatUseTime(int ticks) {
        float seconds = Math.max(0.0f, ticks / 20.0f);
        if (seconds >= 60.0f) {
            int totalSeconds = Math.round(seconds);
            return (totalSeconds / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", totalSeconds % 60);
        }

        return seconds >= 10.0f
                ? Math.round(seconds) + "s"
                : String.format(java.util.Locale.ROOT, "%.1fs", seconds);
    }

    private static void drawMedicineProgressRing(GuiGraphics guiGraphics, int centerX, int centerY, float progress) {
        drawDisk(guiGraphics, centerX, centerY, MEDICINE_RING_RADIUS + 2, 0x26000000);
        drawRing(guiGraphics, centerX, centerY, MEDICINE_RING_RADIUS, MEDICINE_RING_THICKNESS,
                MEDICINE_RING_TRACK, 1.0f);
        drawRing(guiGraphics, centerX, centerY, MEDICINE_RING_RADIUS, MEDICINE_RING_THICKNESS,
                MEDICINE_RING_FILL, progress);

        if (progress > 0.0f) {
            double angle = -Math.PI / 2.0 + Math.PI * 2.0 * progress;
            int headX = centerX + (int) Math.round(Math.cos(angle) * MEDICINE_RING_RADIUS);
            int headY = centerY + (int) Math.round(Math.sin(angle) * MEDICINE_RING_RADIUS);
            guiGraphics.fill(headX - 1, headY - 1, headX + 2, headY + 2, MEDICINE_RING_HEAD);
        }
    }

    private static void drawMedicineGlyph(GuiGraphics guiGraphics, int centerX, int centerY) {
        int color = 0xCFE9ECE4;
        guiGraphics.fill(centerX - 1, centerY - 4, centerX + 2, centerY + 5, color);
        guiGraphics.fill(centerX - 4, centerY - 1, centerX + 5, centerY + 2, color);
        guiGraphics.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, 0xFFFFFFFF);
    }

    private static void drawRing(GuiGraphics guiGraphics, int centerX, int centerY, int radius, int thickness,
                                 int color, float progress) {
        float clampedProgress = Math.max(0.0f, Math.min(1.0f, progress));
        int innerRadius = Math.max(0, radius - thickness);
        int innerSq = innerRadius * innerRadius;
        int outerSq = radius * radius;

        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int distanceSq = dx * dx + dy * dy;
                if (distanceSq > outerSq || distanceSq <= innerSq) {
                    continue;
                }

                double angle = Math.atan2(dy, dx) + Math.PI / 2.0;
                if (angle < 0.0) {
                    angle += Math.PI * 2.0;
                }

                float pixelProgress = (float) (angle / (Math.PI * 2.0));
                if (pixelProgress <= clampedProgress) {
                    guiGraphics.fill(centerX + dx, centerY + dy,
                            centerX + dx + 1, centerY + dy + 1, color);
                }
            }
        }
    }

    private static void drawDisk(GuiGraphics guiGraphics, int centerX, int centerY, int radius, int color) {
        int radiusSq = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy <= radiusSq) {
                    guiGraphics.fill(centerX + dx, centerY + dy,
                            centerX + dx + 1, centerY + dy + 1, color);
                }
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

    private record MedicineUseInfo(float progress, int timeTicks, String label) {
    }
}
