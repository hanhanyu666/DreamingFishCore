package com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.ui.framework.hud.HudFrame;
import com.hhy.dreamingfishcore.client.ui.framework.hud.HudLayer;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.item.items.Item_AidKit;
import com.hhy.dreamingfishcore.item.items.Potion_RestoreUnInfected;
import com.hhy.dreamingfishcore.item.items.medicine.Easy_Aid_Kit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.ItemDecoratorHandler;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;


@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public class CustomHotbarGUI {
    private static final int SLOT_COUNT = 9;
    private static final int SLOT_STEP = 20;
    private static final int HOTBAR_PADDING = 2;
    private static final int HOTBAR_WIDTH = HOTBAR_PADDING * 2 + SLOT_COUNT * SLOT_STEP;
    private static final int HOTBAR_HEIGHT = 24;
    private static final int HOTBAR_BOTTOM_MARGIN = 4;
    private static final int ITEM_SIZE = 16;
    private static final int OFFHAND_SLOT_WIDTH = HOTBAR_PADDING * 2 + SLOT_STEP;
    private static final int OFFHAND_SLOT_GAP = 4;
    private static final int HOTBAR_RADIUS = 6;
    private static final int HOTBAR_BG = 0x90212629;
    private static final int HOTBAR_BORDER = 0x4A767D82;
    private static final int SELECTED_BG = 0x56636B70;
    private static final int SELECTED_UNDERLINE = 0xC6A9D1E3;
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
    private static final float MEDICINE_RING_RADIUS = 7.5F;
    private static final float MEDICINE_RING_THICKNESS = 3.0F;
    private static final int MEDICINE_PANEL_BG = 0x64060809;
    private static final int MEDICINE_PANEL_INNER = 0x2AFFFFFF;
    private static final int MEDICINE_RING_TRACK = 0x6850524D;
    private static final int MEDICINE_RING_FILL = 0xFFE1E9DD;
    private static final int MEDICINE_RING_HEAD = 0xFFFFFFFF;
    private static final int MEDICINE_TEXT_COLOR = 0xFFECE8DD;
    private static final int MEDICINE_DIM_TEXT_COLOR = 0xFF929891;

    /* Reusable collection storage feeding the retained hotbar item cache. */
    private static final int MAX_BATCHED_HOTBAR_ITEMS = SLOT_COUNT + 1;
    private static final ItemStack[] BATCHED_ITEM_STACKS = new ItemStack[MAX_BATCHED_HOTBAR_ITEMS];
    private static final int[] BATCHED_ITEM_X = new int[MAX_BATCHED_HOTBAR_ITEMS];
    private static final int[] BATCHED_ITEM_Y = new int[MAX_BATCHED_HOTBAR_ITEMS];
    private static final Item[] CACHED_DECORATOR_ITEMS = new Item[MAX_BATCHED_HOTBAR_ITEMS];
    private static final ItemDecoratorHandler[] CACHED_DECORATORS =
            new ItemDecoratorHandler[MAX_BATCHED_HOTBAR_ITEMS];
    private static final HotbarItemRenderCache HOTBAR_ITEM_RENDER_CACHE =
            new HotbarItemRenderCache(MAX_BATCHED_HOTBAR_ITEMS);
    private static int batchedItemCount;

    /** 快捷栏在统一 HUD 画布中的区域，最先绘制。 */
    public static final HudLayer LAYER = new HudLayer() {
        @Override
        public int order() {
            return 10;
        }

        @Override
        public boolean visible(Minecraft minecraft) {
            return shouldRenderCustomHotbar(minecraft);
        }

        @Override
        public void paint(HudFrame frame) {
            CustomHotbarGUI.paint(frame);
        }
    };

    private static int lastSelectedSlot = -1;
    /** 选中框的显示位置（以格为单位），跟随选中格平滑滑动。 */
    private static float selectionSlot = -1.0F;
    private static long lastHotbarInteractionTime = 0L;
    private static Item localMedicineUseItem = null;
    private static InteractionHand localMedicineUseHand = InteractionHand.MAIN_HAND;
    private static long localMedicineUseStartTime = 0L;
    private static long localMedicineUseEndTime = 0L;
    private static int localMedicineUseMaxActiveTicks = 0;
    private static boolean localMedicineUsePersistent = false;
    private static long lastMedicineInputTime = 0L;

    @SubscribeEvent
    public static void replaceVanillaHotbar(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.HOTBAR.equals(event.getName())) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (!shouldReplaceVanillaHud(mc)) {
            return;
        }

        event.setCanceled(true);
    }

    public static boolean shouldRenderHud(Minecraft mc) {
        return shouldRenderCustomHotbar(mc);
    }

    private static void paint(HudFrame frame) {
        Minecraft mc = frame.minecraft();
        UiCanvas canvas = frame.canvas();
        Player player = mc.player;
        updateHotbarInteraction(player);
        MedicineUseInfo medicineUseInfo = getMedicineUseInfo(player);
        if (medicineUseInfo != null) {
            registerHotbarInteraction();
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        float scale = getHotbarAnimationScale();
        int x = (screenWidth - HOTBAR_WIDTH) / 2;
        int y = getHotbarBaseTopY(screenHeight);
        int anchorY = screenHeight - HOTBAR_BOTTOM_MARGIN;

        canvas.push();
        canvas.scale(scale, screenWidth / 2.0f, anchorY);

        batchedItemCount = 0;
        drawHotbarFrame(canvas, x, y);
        collectHotbarItems(canvas, player, x, y, frame.delta());
        collectOffhandSlot(canvas, player, x, y);
        if (batchedItemCount > 0) {
            // 物品模型走保留缓冲的缓存渲染，作为原生命令画在框体之上
            canvas.custom(x - OFFHAND_SLOT_WIDTH - OFFHAND_SLOT_GAP, y,
                    HOTBAR_WIDTH + (OFFHAND_SLOT_WIDTH + OFFHAND_SLOT_GAP) * 2, HOTBAR_HEIGHT,
                    graphics -> renderBatchedHotbarItems(graphics, mc, player, scale));
        }

        canvas.pop();

        drawMedicineUseHud(canvas, mc, screenWidth, screenHeight, medicineUseInfo);
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
    public static void hideVanillaSurvivalBars(RenderGuiLayerEvent.Pre event) {
        // These overlays are redrawn by the custom status HUD.
        if (!VanillaGuiLayers.EXPERIENCE_BAR.equals(event.getName())
                && !VanillaGuiLayers.EXPERIENCE_LEVEL.equals(event.getName())
                && !VanillaGuiLayers.FOOD_LEVEL.equals(event.getName())
                && !VanillaGuiLayers.ARMOR_LEVEL.equals(event.getName())
                && !VanillaGuiLayers.AIR_LEVEL.equals(event.getName())) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (!shouldReplaceVanillaHud(mc)) {
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
        if (mc.player == null || mc.screen != null || mc.options.hideGui
                || mc.getDebugOverlay().showDebugScreen()) {
            return 0;
        }

        return 0;
    }

    private static int getHotbarBaseTopY(int screenHeight) {
        return screenHeight - HOTBAR_BOTTOM_MARGIN - HOTBAR_HEIGHT;
    }

    private static boolean shouldRenderCustomHotbar(Minecraft mc) {
        return shouldReplaceVanillaHud(mc) && isHudVisibleScreen(mc);
    }

    private static boolean shouldReplaceVanillaHud(Minecraft mc) {
        return mc.player != null
                && !mc.player.isDeadOrDying()
                && !mc.options.hideGui
                && !mc.getDebugOverlay().showDebugScreen()
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

    private static void drawHotbarFrame(UiCanvas canvas, int x, int y) {
        drawSlotFrame(canvas, x, y, HOTBAR_WIDTH);
    }

    private static void drawSlotFrame(UiCanvas canvas, int x, int y, int width) {
        canvas.shape(x, y, width, HOTBAR_HEIGHT).radius(HOTBAR_RADIUS)
                .verticalGradient(0x98262B2E, HOTBAR_BG).border(1.0F, HOTBAR_BORDER).draw();
        // 顶边细高光，中间最亮
        float inner = (width - HOTBAR_RADIUS * 2) * 0.5F;
        canvas.shape(x + HOTBAR_RADIUS, y + 1, inner, 1).horizontalGradient(0x00FFFFFF, 0x1CFFFFFF).draw();
        canvas.shape(x + HOTBAR_RADIUS + inner, y + 1, inner, 1).horizontalGradient(0x1CFFFFFF, 0x00FFFFFF).draw();
    }

    private static void collectHotbarItems(UiCanvas canvas, Player player, int x, int y, float deltaSeconds) {
        int selectedSlot = player.getInventory().selected;
        // 选中框平滑滑到新格；跨越很远（如 9→1 滚轮回绕）时直接跳过去
        if (selectionSlot < 0.0F || Math.abs(selectionSlot - selectedSlot) > 4.0F) {
            selectionSlot = selectedSlot;
        } else {
            selectionSlot += (selectedSlot - selectionSlot) * Math.min(1.0F, deltaSeconds * 22.0F);
        }
        float selectedX = x + HOTBAR_PADDING + selectionSlot * SLOT_STEP;
        canvas.shape(selectedX, y + 2, SLOT_STEP, HOTBAR_HEIGHT - 4).radius(4.0F)
                .verticalGradient(0x6A6F777C, SELECTED_BG).draw();
        canvas.shape(selectedX + 5, y + HOTBAR_HEIGHT - 3, SLOT_STEP - 10, 2).radius(1.0F)
                .fill(SELECTED_UNDERLINE).draw();

        for (int i = 0; i < SLOT_COUNT; i++) {
            int slotX = x + HOTBAR_PADDING + i * SLOT_STEP;
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                int itemX = slotX + (SLOT_STEP - ITEM_SIZE) / 2;
                int itemY = y + (HOTBAR_HEIGHT - ITEM_SIZE) / 2;
                queueHotbarItem(stack, itemX, itemY);
            }
        }
    }

    private static void collectOffhandSlot(UiCanvas canvas, Player player, int hotbarX, int y) {
        ItemStack stack = player.getOffhandItem();
        if (stack.isEmpty()) {
            return;
        }

        boolean renderOnLeft = player.getMainArm().getOpposite() == HumanoidArm.LEFT;
        int x = renderOnLeft
                ? hotbarX - OFFHAND_SLOT_GAP - OFFHAND_SLOT_WIDTH
                : hotbarX + HOTBAR_WIDTH + OFFHAND_SLOT_GAP;

        drawSlotFrame(canvas, x, y, OFFHAND_SLOT_WIDTH);

        int itemX = x + (OFFHAND_SLOT_WIDTH - ITEM_SIZE) / 2;
        int itemY = y + (HOTBAR_HEIGHT - ITEM_SIZE) / 2;
        queueHotbarItem(stack, itemX, itemY);
    }

    private static void queueHotbarItem(ItemStack stack, int x, int y) {
        if (batchedItemCount >= MAX_BATCHED_HOTBAR_ITEMS) {
            return;
        }
        BATCHED_ITEM_STACKS[batchedItemCount] = stack;
        BATCHED_ITEM_X[batchedItemCount] = x;
        BATCHED_ITEM_Y[batchedItemCount] = y;
        batchedItemCount++;
    }

    private static void renderBatchedHotbarItems(GuiGraphics guiGraphics, Minecraft mc,
                                                  Player player, float scale) {
        if (batchedItemCount == 0) {
            return;
        }

        try {
            boolean cacheableScale = Math.abs(scale - HOTBAR_IDLE_SCALE) < 0.0001F
                    || Math.abs(scale - HOTBAR_ACTIVE_SCALE) < 0.0001F;
            boolean usedRetainedCache = HOTBAR_ITEM_RENDER_CACHE.renderModels(guiGraphics, mc, player,
                    BATCHED_ITEM_STACKS, BATCHED_ITEM_X, BATCHED_ITEM_Y,
                    batchedItemCount, scale, cacheableScale);

            if (usedRetainedCache) {
                drawDynamicItemDecorations(guiGraphics, mc);
            } else {
                // During the short scale transitions the model geometry is
                // immediate, so its decorations must use the same live pose.
                for (int index = 0; index < batchedItemCount; index++) {
                    guiGraphics.renderItemDecorations(mc.font, BATCHED_ITEM_STACKS[index],
                            BATCHED_ITEM_X[index], BATCHED_ITEM_Y[index]);
                }
            }
        } finally {
            batchedItemCount = 0;
        }
    }

    private static void drawDynamicItemDecorations(GuiGraphics guiGraphics, Minecraft mc) {
        Player player = mc.player;
        float partialTick = player == null
                ? 0.0F
                : mc.getTimer().getGameTimeDeltaPartialTick(true);
        for (int index = 0; index < batchedItemCount; index++) {
            ItemStack stack = BATCHED_ITEM_STACKS[index];
            int x = BATCHED_ITEM_X[index];
            int y = BATCHED_ITEM_Y[index];
            float cooldown = player == null
                    ? 0.0F
                    : player.getCooldowns().getCooldownPercent(stack.getItem(), partialTick);
            if (cooldown > 0.0F) {
                int top = y + Mth.floor(16.0F * (1.0F - cooldown));
                int bottom = top + Mth.ceil(16.0F * cooldown);
                guiGraphics.pose().pushPose();
                if (stack.getCount() != 1) {
                    // Match GuiGraphics#renderItemDecorations: count text moves
                    // the following overlay to the same z plane.
                    guiGraphics.pose().translate(0.0F, 0.0F, 200.0F);
                }
                guiGraphics.fill(RenderType.guiOverlay(), x, top, x + 16, bottom,
                        Integer.MAX_VALUE);
                guiGraphics.pose().popPose();
            }

            // Third-party decorators may animate or change without mutating
            // the ItemStack, so they deliberately remain live.
            Item item = stack.getItem();
            if (CACHED_DECORATOR_ITEMS[index] != item) {
                CACHED_DECORATOR_ITEMS[index] = item;
                CACHED_DECORATORS[index] = ItemDecoratorHandler.of(stack);
            }
            CACHED_DECORATORS[index].render(guiGraphics, mc.font, stack, x, y);
        }
    }

    public static void invalidateItemRenderCache() {
        HOTBAR_ITEM_RENDER_CACHE.invalidate();
        java.util.Arrays.fill(CACHED_DECORATOR_ITEMS, null);
        java.util.Arrays.fill(CACHED_DECORATORS, null);
    }

    private static MedicineUseInfo getMedicineUseInfo(Player player) {
        if (player.isUsingItem()) {
            ItemStack useStack = player.getUseItem();
            if (isMedicineStack(useStack)) {
                int totalTicks = useStack.getUseDuration(player);
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

        int durationTicks = stack.getUseDuration(player);
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

    private static void drawMedicineUseHud(UiCanvas canvas, Minecraft mc, int screenWidth, int screenHeight,
                                           MedicineUseInfo medicineUseInfo) {
        if (medicineUseInfo == null) {
            return;
        }

        int x = (screenWidth - MEDICINE_HUD_WIDTH) / 2;
        int statusBarY = CustomHotbarGUI.getAnimatedHotbarTopY(screenHeight)
                - BOTTOM_STATUS_HOTBAR_GAP - BOTTOM_STATUS_BAR_HEIGHT;
        int y = Math.max(2, statusBarY - MEDICINE_HUD_GAP - MEDICINE_HUD_HEIGHT);
        float centerX = x + 14.0F;
        float centerY = y + MEDICINE_HUD_HEIGHT / 2.0F;

        canvas.shape(x, y, MEDICINE_HUD_WIDTH, MEDICINE_HUD_HEIGHT).radius(HOTBAR_RADIUS)
                .verticalGradient(0x70080A0B, MEDICINE_PANEL_BG).border(1.0F, 0x18FFFFFF).draw();
        canvas.shape(x + 5, y + 3, MEDICINE_HUD_WIDTH - 10, 1).horizontalGradient(0x00FFFFFF, MEDICINE_PANEL_INNER).draw();

        drawMedicineProgressRing(canvas, centerX, centerY, medicineUseInfo.progress());
        drawMedicineGlyph(canvas, centerX, centerY);

        String timeText = formatUseTime(medicineUseInfo.timeTicks());
        float timeX = x + 30.0F;
        float timeY = y + 5.0F;
        canvas.text(timeText, timeX + 1.0F, timeY + 1.0F, 0x80000000, 1.0F, false);
        canvas.text(timeText, timeX, timeY, MEDICINE_TEXT_COLOR, 1.0F, false);
        canvas.text(medicineUseInfo.label(), timeX, timeY + 10.0F, MEDICINE_DIM_TEXT_COLOR, 1.0F, false);
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
            return Math.max(1, stack.getUseDuration(player));
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

    private static void drawMedicineProgressRing(UiCanvas canvas, float centerX, float centerY, float progress) {
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        float full = (float) (Math.PI * 2.0);
        canvas.circle(centerX, centerY, MEDICINE_RING_RADIUS + MEDICINE_RING_THICKNESS * 0.5F + 2.0F, 0x26000000);
        canvas.arc(centerX, centerY, MEDICINE_RING_RADIUS, MEDICINE_RING_THICKNESS, 0.0F, full,
                MEDICINE_RING_TRACK, MEDICINE_RING_TRACK);
        if (clamped > 0.0f) {
            float start = (float) (-Math.PI / 2.0);
            canvas.arc(centerX, centerY, MEDICINE_RING_RADIUS, MEDICINE_RING_THICKNESS, start, full * clamped,
                    0xFFB9C4B5, MEDICINE_RING_FILL);
            double angle = start + full * clamped;
            canvas.circle(centerX + (float) Math.cos(angle) * MEDICINE_RING_RADIUS,
                    centerY + (float) Math.sin(angle) * MEDICINE_RING_RADIUS, 1.7F, MEDICINE_RING_HEAD);
        }
    }

    private static void drawMedicineGlyph(UiCanvas canvas, float centerX, float centerY) {
        int color = 0xCFE9ECE4;
        canvas.shape(centerX - 1.5F, centerY - 4.0F, 3.0F, 8.0F).radius(1.0F).fill(color).draw();
        canvas.shape(centerX - 4.0F, centerY - 1.5F, 8.0F, 3.0F).radius(1.0F).fill(color).draw();
        canvas.shape(centerX - 1.5F, centerY - 1.5F, 3.0F, 3.0F).fill(0xFFFFFFFF).draw();
    }

    private record MedicineUseInfo(float progress, int timeTicks, String label) {
    }

}
