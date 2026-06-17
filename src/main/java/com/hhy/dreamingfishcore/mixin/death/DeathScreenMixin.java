package com.hhy.dreamingfishcore.mixin.death;

import com.mojang.blaze3d.vertex.PoseStack;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.util.VirtualCoordinateHelper;
import com.hhy.dreamingfishcore.core.playerattributes_system.death.DeathScreenDataStorage;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.network.packets.playerattribute_system.death_system.Packet_KeepInventoryRequest;
import com.hhy.dreamingfishcore.network.packets.playerattribute_system.death_system.Packet_NormalRespawnRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 死亡界面 Mixin
 * 保留游戏画面主体的沉浸式死亡界面
 */
@Mixin(DeathScreen.class)
public abstract class DeathScreenMixin extends Screen {

    @Unique
    private Button dreamingFishCore$normalRespawnButton;
    @Unique
    private Button dreamingFishCore$keepInventoryButton;
    @Unique
    private Button dreamingFishCore$titleScreenButton;

    @Unique
    private boolean dreamingFishCore$showDeathPos = false;  // 是否显示死亡位置
    @Unique
    private int dreamingFishCore$posButtonX;
    @Unique
    private int dreamingFishCore$posButtonY;
    @Unique
    private int dreamingFishCore$posButtonWidth = 140;
    @Unique
    private int dreamingFishCore$posButtonHeight = 16;
    @Unique
    private long dreamingFishCore$openedAtMs = 0L;
    @Unique
    private final VirtualCoordinateHelper.VirtualSizeResult dreamingFishCore$virtualSize = new VirtualCoordinateHelper.VirtualSizeResult();

    @Unique
    private static final long GRAYSCALE_FADE_MS = 1300L;
    @Unique
    private static final long TITLE_DELAY_MS = 360L;
    @Unique
    private static final long TITLE_TYPE_INTERVAL_MS = 92L;
    @Unique
    private static final long TITLE_SETTLE_DELAY_MS = 260L;
    @Unique
    private static final long LAYOUT_SLIDE_MS = 620L;
    @Unique
    private static final long CONTENT_FADE_DELAY_MS = 180L;
    @Unique
    private static final long CONTENT_FADE_MS = 520L;
    @Unique
    private static final float DEATH_TITLE_SCALE = 3.08f;

    protected DeathScreenMixin(Component title) {
        super(title);
    }

    /**
     * 注入到 init() 方法，添加自定义按钮
     * 始终使用自定义逻辑，不再检查数据是否为空
     */
    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$init(CallbackInfo ci) {
        // 获取数据（如果没有数据，返回默认值）
        DeathScreenDataStorage.DeathScreenData data = DeathScreenDataStorage.getData();

        // 始终使用自定义界面
        ci.cancel();
        dreamingFishCore$openedAtMs = System.currentTimeMillis();

        dreamingFishCore$createDeathButtons(data);

        DreamingFishCore.LOGGER.info("死亡界面自定义按钮已添加");
    }

    /**
     * 返回标题界面
     */
    @Unique
    private void dreamingFishCore$returnToTitleScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.level.disconnect();
        }
        mc.disconnect();
        mc.setScreen(null);
    }

    /**
     * 发送正常复活请求
     */
    @Unique
    private void dreamingFishCore$sendNormalRespawn() {
        DreamingFishCore_NetworkManager.sendToServer(new Packet_NormalRespawnRequest());
    }

    /**
     * 发送保留物品复活请求
     */
    @Unique
    private void dreamingFishCore$sendKeepInventory() {
        DreamingFishCore_NetworkManager.sendToServer(new Packet_KeepInventoryRequest());
    }

    /**
     * 重新初始化按钮（当数据包延迟到达时调用）
     */
    @Unique
    private void reinitButtons() {
        DeathScreenDataStorage.DeathScreenData data = DeathScreenDataStorage.getData();

        // 移除旧按钮
        if (dreamingFishCore$normalRespawnButton != null) {
            this.removeWidget(dreamingFishCore$normalRespawnButton);
        }
        if (dreamingFishCore$keepInventoryButton != null) {
            this.removeWidget(dreamingFishCore$keepInventoryButton);
        }
        if (dreamingFishCore$titleScreenButton != null) {
            this.removeWidget(dreamingFishCore$titleScreenButton);
        }

        dreamingFishCore$createDeathButtons(data);

        DreamingFishCore.LOGGER.info("死亡界面按钮已刷新");
    }

    @Unique
    private void dreamingFishCore$createDeathButtons(DeathScreenDataStorage.DeathScreenData data) {
        int centerX = this.width / 2;
        int buttonHeight = dreamingFishCore$vSize(24);
        int buttonWidth = Math.min(286, Math.max(156, this.width - 36));
        int startY = Math.max(132, this.height - 96);

        dreamingFishCore$normalRespawnButton = new CustomButton(
                centerX - buttonWidth / 2, startY,
                buttonWidth, buttonHeight,
                Component.literal("重生  -" + String.format("%.1f", data.normalCost())),
                false, data.normalCost(), data.respawnPoint(),
                btn -> dreamingFishCore$sendNormalRespawn()
        );
        dreamingFishCore$keepInventoryButton = new CustomButton(
                centerX - buttonWidth / 2, startY + 30,
                buttonWidth, buttonHeight,
                Component.literal("保留物品  -" + String.format("%.1f", data.keepInventoryCost())),
                true, data.keepInventoryCost(), data.respawnPoint(),
                btn -> dreamingFishCore$sendKeepInventory()
        );
        dreamingFishCore$titleScreenButton = new CustomButton(
                centerX - buttonWidth / 2, startY + 60,
                buttonWidth, dreamingFishCore$vSize(22),
                Component.literal("返回标题"),
                false, 0, data.respawnPoint(),
                btn -> dreamingFishCore$returnToTitleScreen()
        );

        this.addRenderableWidget(dreamingFishCore$normalRespawnButton);
        this.addRenderableWidget(dreamingFishCore$keepInventoryButton);
        this.addRenderableWidget(dreamingFishCore$titleScreenButton);

        dreamingFishCore$normalRespawnButton.active = data.respawnPoint() >= data.normalCost();
        dreamingFishCore$keepInventoryButton.active = data.respawnPoint() >= data.keepInventoryCost();
    }

    /**
     * 注入到 render() 方法，使用压迫感风格渲染
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = true)
    private void dreamingFishCore$renderForge(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        // 检查是否需要重新初始化按钮（数据包延迟到达的情况）
        if (DeathScreenDataStorage.needsReinit()) {
            DeathScreenDataStorage.setNeedsReinit(false);
            reinitButtons();
        }

        // 获取数据（如果没有数据，返回默认值）
        DeathScreenDataStorage.DeathScreenData data = DeathScreenDataStorage.getData();

        // 始终取消原版渲染，使用自定义渲染
        ci.cancel();

        long elapsed = System.currentTimeMillis() - dreamingFishCore$openedAtMs;
        float grayProgress = dreamingFishCore$easeOutCubic(elapsed / (float) GRAYSCALE_FADE_MS);
        VirtualCoordinateHelper.calculateVirtualSize(this, dreamingFishCore$virtualSize);
        float uiScale = dreamingFishCore$virtualSize.uiScale;
        int virtualW = dreamingFishCore$virtualSize.virtualWidth;
        int virtualH = dreamingFishCore$virtualSize.virtualHeight;
        int centerX = virtualW / 2;

        // 游戏画面保留为主体，用无生命感的灰黑遮罩压住色彩，避免明显分层。
        int grayAlpha = (int) (72 + grayProgress * 156);
        guiGraphics.fillGradient(0, 0, this.width, this.height,
                dreamingFishCore$withAlpha(0xFF101113, grayAlpha),
                dreamingFishCore$withAlpha(0xFF0B0C0E, grayAlpha + 10));
        dreamingFishCore$drawVignette(guiGraphics, grayProgress);

        String titleText = "布豪，您趋势了！";
        long titleDoneAt = TITLE_DELAY_MS + titleText.length() * TITLE_TYPE_INTERVAL_MS;
        long slideStartAt = titleDoneAt + TITLE_SETTLE_DELAY_MS;
        float slideProgress = dreamingFishCore$easeOutCubic((elapsed - slideStartAt) / (float) LAYOUT_SLIDE_MS);
        float contentFade = dreamingFishCore$easeOutCubic((elapsed - slideStartAt - CONTENT_FADE_DELAY_MS) / (float) CONTENT_FADE_MS);
        int targetTitleX = virtualW < 560 ? centerX : Math.round(virtualW * 0.29f);
        int titleCenterX = Math.round(dreamingFishCore$lerp(centerX, targetTitleX, slideProgress));
        int titleY = Math.max(36, Math.round(virtualH * 0.24f));
        int titleBlockWidth = dreamingFishCore$deathTitleBlockWidth(titleText, virtualW);

        int visibleChars = Math.max(0, Math.min(titleText.length(),
                (int) ((elapsed - TITLE_DELAY_MS) / TITLE_TYPE_INTERVAL_MS)));
        String visibleTitle = titleText.substring(0, visibleChars);
        boolean cursorVisible = visibleChars < titleText.length() && (elapsed / 260L) % 2L == 0L;
        if (cursorVisible) {
            visibleTitle += "_";
        }

        int contentAlpha = (int) (contentFade * 220);
        int buttonTopY = dreamingFishCore$deathButtonTopY(virtualH);
        dreamingFishCore$layoutDeathButtons(buttonTopY, contentAlpha, uiScale, virtualW);
        dreamingFishCore$posButtonWidth = 0;
        dreamingFishCore$posButtonHeight = 0;

        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.scale(uiScale, uiScale, 1.0F);
        if (!visibleTitle.isEmpty()) {
            int titleAlpha = Math.min(255, (int) (60 + grayProgress * 195));
            dreamingFishCore$drawCenteredScaledText(guiGraphics, "§l" + visibleTitle, titleCenterX, titleY,
                    dreamingFishCore$vScale(DEATH_TITLE_SCALE), dreamingFishCore$withAlpha(0xFFD23E3A, titleAlpha), 0xD0000000);
            int lineWidth = Math.min(titleBlockWidth, 34 + visibleChars * 22);
            int titleLineY = titleY + dreamingFishCore$vSize(48);
            guiGraphics.fill(titleCenterX - lineWidth / 2, titleLineY, titleCenterX + lineWidth / 2, titleLineY + 2,
                    dreamingFishCore$withAlpha(0xFF8F1917, (int) (titleAlpha * 0.72f)));
        }
        if (contentAlpha > 0) {
            dreamingFishCore$drawTitleDeathSummary(guiGraphics, data, titleCenterX, titleY + 78, titleBlockWidth, contentAlpha, virtualW);
            dreamingFishCore$drawDeathMinimap(guiGraphics, data, contentAlpha, virtualW, virtualH);
            dreamingFishCore$drawRespawnProgressPanel(guiGraphics, data, mouseX, mouseY, buttonTopY, contentAlpha, virtualW);
            dreamingFishCore$drawRespawnActionHint(guiGraphics, mouseX, mouseY, buttonTopY, contentAlpha, virtualW, virtualH);
        }
        poseStack.popPose();

        // ========== 渲染按钮 ==========
        if (dreamingFishCore$normalRespawnButton != null) {
            dreamingFishCore$normalRespawnButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (dreamingFishCore$keepInventoryButton != null) {
            dreamingFishCore$keepInventoryButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (dreamingFishCore$titleScreenButton != null) {
            dreamingFishCore$titleScreenButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    /**
     * 注入到 mouseClicked() 方法，处理死亡位置按钮点击
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void dreamingFishCore$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        // 检查是否点击了死亡位置按钮
        if (mouseX >= dreamingFishCore$posButtonX && mouseX < dreamingFishCore$posButtonX + dreamingFishCore$posButtonWidth &&
            mouseY >= dreamingFishCore$posButtonY && mouseY < dreamingFishCore$posButtonY + dreamingFishCore$posButtonHeight) {
            // 切换显示状态
            dreamingFishCore$showDeathPos = !dreamingFishCore$showDeathPos;
            cir.setReturnValue(true);
        }
    }

    @Unique
    private float dreamingFishCore$easeOutCubic(float value) {
        float t = Math.max(0.0f, Math.min(1.0f, value));
        float inverted = 1.0f - t;
        return 1.0f - inverted * inverted * inverted;
    }

    @Unique
    private float dreamingFishCore$lerp(float from, float to, float progress) {
        return from + (to - from) * Math.max(0.0f, Math.min(1.0f, progress));
    }

    @Unique
    private int dreamingFishCore$vSize(int virtualSize) {
        return Math.max(1, virtualSize);
    }

    @Unique
    private float dreamingFishCore$vScale(float virtualScale) {
        return virtualScale;
    }

    @Unique
    private int dreamingFishCore$deathButtonWidth(int virtualW) {
        return Math.min(286, Math.max(156, virtualW - 36));
    }

    @Unique
    private int dreamingFishCore$deathButtonX(int virtualW) {
        int buttonWidth = dreamingFishCore$deathButtonWidth(virtualW);
        return Math.max(12, virtualW - buttonWidth - 18);
    }

    @Unique
    private int dreamingFishCore$deathButtonTopY(int virtualH) {
        return Math.max(112, virtualH - 48);
    }

    @Unique
    private void dreamingFishCore$layoutDeathButtons(int topY, int contentAlpha, float uiScale, int virtualW) {
        int buttonX = dreamingFishCore$deathButtonX(virtualW);
        int totalWidth = dreamingFishCore$deathButtonWidth(virtualW);
        int segmentWidth = totalWidth / 3;
        boolean visible = contentAlpha > 12;

        dreamingFishCore$placeButton(dreamingFishCore$normalRespawnButton, buttonX, topY, segmentWidth, 24, visible, uiScale);
        dreamingFishCore$placeButton(dreamingFishCore$keepInventoryButton, buttonX + segmentWidth, topY, segmentWidth, 24, visible, uiScale);
        dreamingFishCore$placeButton(dreamingFishCore$titleScreenButton, buttonX + segmentWidth * 2, topY,
                totalWidth - segmentWidth * 2, 24, visible, uiScale);
    }

    @Unique
    private void dreamingFishCore$placeButton(Button button, int x, int y, int width, int height, boolean visible, float uiScale) {
        if (button == null) {
            return;
        }

        button.setX(Math.round(x * uiScale));
        button.setY(Math.round(y * uiScale));
        if (button instanceof CustomButton customButton) {
            customButton.dreamingFishCore$setBounds(Math.round(width * uiScale), Math.max(14, Math.round(height * uiScale)));
        }
        button.visible = visible;
    }

    @Unique
    private int dreamingFishCore$withAlpha(int color, int alpha) {
        int clampedAlpha = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (clampedAlpha << 24);
    }

    @Unique
    private void dreamingFishCore$drawVignette(GuiGraphics guiGraphics, float progress) {
        int topBottomAlpha = (int) (progress * 86.0f);

        guiGraphics.fillGradient(0, 0, this.width, dreamingFishCore$vSize(54),
                dreamingFishCore$withAlpha(0xFF000000, topBottomAlpha), 0x00000000);
        guiGraphics.fillGradient(0, this.height - dreamingFishCore$vSize(68), this.width, this.height,
                0x00000000, dreamingFishCore$withAlpha(0xFF000000, topBottomAlpha));
    }

    @Unique
    private void dreamingFishCore$drawTitleDeathSummary(GuiGraphics guiGraphics, DeathScreenDataStorage.DeathScreenData data,
                                                       int titleCenterX, int y, int titleBlockWidth, int contentAlpha,
                                                       int virtualW) {
        int textWidth = Math.min(titleBlockWidth, virtualW - 48);
        int x = titleCenterX - textWidth / 2;
        int cardHeight = 42;
        String deathMessage = dreamingFishCore$trimToWidth(data.deathMessage().getString(), textWidth);

        guiGraphics.fill(x - 10, y, x - 7, y + cardHeight,
                dreamingFishCore$withAlpha(0xFFB83B37, (int) (contentAlpha * 0.82f)));
        guiGraphics.fill(x, y, x + textWidth, y + cardHeight,
                dreamingFishCore$withAlpha(0xFF07080A, (int) (contentAlpha * 0.20f)));
        guiGraphics.fill(x, y + cardHeight - 1, x + textWidth, y + cardHeight,
                dreamingFishCore$withAlpha(0xFF6A6660, (int) (contentAlpha * 0.28f)));
        guiGraphics.drawString(this.font, "死亡原因", x + 10, y + 9,
                dreamingFishCore$withAlpha(0xFF9C9690, contentAlpha), false);

        guiGraphics.drawString(this.font, deathMessage, x + 10, y + 23,
                dreamingFishCore$withAlpha(0xFFDAD4CC, contentAlpha), false);
    }

    @Unique
    private void dreamingFishCore$drawRespawnProgressPanel(GuiGraphics guiGraphics, DeathScreenDataStorage.DeathScreenData data,
                                                          int mouseX, int mouseY, int buttonTopY, int contentAlpha,
                                                          int virtualW) {
        int panelWidth = dreamingFishCore$deathButtonWidth(virtualW);
        int panelHeight = 68;
        int buttonRight = dreamingFishCore$deathButtonX(virtualW) + dreamingFishCore$deathButtonWidth(virtualW);
        int panelX = Math.max(18, buttonRight - panelWidth);
        int panelY = Math.max(18, buttonTopY - panelHeight - 8);

        int x = panelX + 14;
        int y = panelY + 10;
        int barWidth = panelWidth - 28;
        int barHeight = 7;
        float currentPoints = data.respawnPoint();
        int hoveredAction = dreamingFishCore$getHoveredDeathAction(mouseX, mouseY);
        float previewCost = hoveredAction == 1 ? data.normalCost() : hoveredAction == 2 ? data.keepInventoryCost() : 0.0f;
        float previewPoints = Math.max(0.0f, currentPoints - previewCost);
        float currentProgress = Math.max(0.0f, Math.min(1.0f, currentPoints / 100.0f));
        float previewProgress = Math.max(0.0f, Math.min(1.0f, previewPoints / 100.0f));
        int currentWidth = Math.round(barWidth * currentProgress);
        int previewWidth = Math.round(barWidth * previewProgress);
        boolean previewingCost = hoveredAction == 1 || hoveredAction == 2;
        int accentColor = switch (hoveredAction) {
            case 1 -> 0xFF8FC274;
            case 2 -> 0xFFD2A157;
            case 3 -> 0xFFC46A63;
            default -> 0xFF5C9FC2;
        };
        int panelTint = switch (hoveredAction) {
            case 1 -> 0xFF102016;
            case 2 -> 0xFF241A0C;
            case 3 -> 0xFF1F1010;
            default -> 0xFF0B151B;
        };

        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight,
                dreamingFishCore$withAlpha(panelTint, (int) (contentAlpha * 0.50f)));
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1,
                dreamingFishCore$withAlpha(accentColor, (int) (contentAlpha * 0.70f)));
        guiGraphics.fill(panelX, buttonTopY - 4, panelX + panelWidth, buttonTopY - 3,
                dreamingFishCore$withAlpha(0xFF6E6A64, (int) (contentAlpha * 0.48f)));
        String leftText = dreamingFishCore$getRespawnPreviewLabel(hoveredAction, previewCost, currentPoints);
        guiGraphics.drawString(this.font, leftText, x, y,
                dreamingFishCore$withAlpha(accentColor, contentAlpha), false);
        String currentText = previewingCost
                ? "剩余 " + String.format("%.1f", previewPoints)
                : String.format("%.0f/100", currentPoints);
        guiGraphics.drawString(this.font, currentText, x + barWidth - this.font.width(currentText), y,
                dreamingFishCore$withAlpha(0xFFE9E2DA, contentAlpha), false);

        int barY = y + 16;
        guiGraphics.fill(x, barY, x + barWidth, barY + barHeight,
                dreamingFishCore$withAlpha(0xFF161A1D, (int) (contentAlpha * 0.92f)));
        guiGraphics.fill(x, barY, x + previewWidth, barY + barHeight,
                dreamingFishCore$withAlpha(accentColor, contentAlpha));
        if (previewingCost && currentWidth > previewWidth) {
            guiGraphics.fill(x + previewWidth, barY, x + currentWidth, barY + barHeight,
                    dreamingFishCore$withAlpha(0xFFD04D45, contentAlpha));
        }
        guiGraphics.fill(x, barY + barHeight, x + barWidth, barY + barHeight + 1,
                dreamingFishCore$withAlpha(0xFF000000, (int) (contentAlpha * 0.35f)));

        float timesSource = previewingCost ? previewPoints : currentPoints;
        int respawnTimes = data.normalCost() > 0.0f ? Math.max(0, (int) Math.floor(timesSource / data.normalCost())) : 0;
        String timesText = dreamingFishCore$getRespawnPreviewHint(hoveredAction, currentPoints, previewCost, respawnTimes, data.isInfected());
        guiGraphics.drawString(this.font, timesText, x, barY + 15,
                dreamingFishCore$withAlpha(0xFF9E9892, contentAlpha), false);
    }

    @Unique
    private int dreamingFishCore$deathTitleBlockWidth(String titleText, int virtualW) {
        int titleWidth = Math.round(this.font.width(titleText) * DEATH_TITLE_SCALE) + 18;
        return Math.min(virtualW - 48, Math.max(260, titleWidth));
    }

    @Unique
    private int dreamingFishCore$getHoveredDeathAction(int mouseX, int mouseY) {
        if (dreamingFishCore$isButtonHovered(dreamingFishCore$normalRespawnButton, mouseX, mouseY)) {
            return 1;
        }
        if (dreamingFishCore$isButtonHovered(dreamingFishCore$keepInventoryButton, mouseX, mouseY)) {
            return 2;
        }
        if (dreamingFishCore$isButtonHovered(dreamingFishCore$titleScreenButton, mouseX, mouseY)) {
            return 3;
        }
        return 0;
    }

    @Unique
    private boolean dreamingFishCore$isButtonHovered(Button button, int mouseX, int mouseY) {
        return button != null && button.visible && button.isMouseOver(mouseX, mouseY);
    }

    @Unique
    private String dreamingFishCore$getRespawnPreviewLabel(int hoveredAction, float previewCost, float currentPoints) {
        return switch (hoveredAction) {
            case 1 -> "重生扣除  " + String.format("%.1f", previewCost);
            case 2 -> "保留扣除  " + String.format("%.1f", previewCost);
            case 3 -> "返回标题";
            default -> "剩余死亡点数";
        };
    }

    @Unique
    private String dreamingFishCore$getRespawnPreviewHint(int hoveredAction, float currentPoints, float previewCost,
                                                         int respawnTimes, boolean infected) {
        if (hoveredAction == 1 || hoveredAction == 2) {
            if (currentPoints < previewCost) {
                return "点数不足  还差 " + String.format("%.1f", previewCost - currentPoints);
            }
            String typeText = infected ? "感染者" : "幸存者";
            return "确认后作为" + typeText + (hoveredAction == 2 ? "保留物品重生" : "重生")
                    + "  /  之后可复活 " + respawnTimes + " 次";
        }
        if (hoveredAction == 3) {
            return "返回标题不会消耗死亡点数";
        }
        return "预计剩余复活次数  " + respawnTimes;
    }

    @Unique
    private void dreamingFishCore$drawRespawnActionHint(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                                       int buttonTopY, int contentAlpha, int virtualW, int virtualH) {
        int hoveredAction = dreamingFishCore$getHoveredDeathAction(mouseX, mouseY);
        if (hoveredAction != 1 && hoveredAction != 2) {
            return;
        }

        String actionText = hoveredAction == 1
                ? "花费少量点数直接重生，您会丢失所有的物品"
                : "保留物品栏重生，您会保留身上所有的东西，但是会消耗更多重生点数";
        String warningText = "点数耗尽后，您将无法重生，需要等待其余玩家拯救您";

        int buttonX = dreamingFishCore$deathButtonX(virtualW);
        int maxWidth = Math.max(210, Math.min(390, buttonX - 36));
        int x = 18;
        int y = Math.max(24, Math.min(virtualH - 46, buttonTopY - 5));
        int width = Math.min(maxWidth, Math.max(this.font.width(actionText), this.font.width(warningText)) + 24);
        int height = 38;
        int accentColor = hoveredAction == 1 ? 0xFF8FC274 : 0xFFD2A157;

        guiGraphics.fill(x, y, x + width, y + height,
                dreamingFishCore$withAlpha(0xFF050608, (int) (contentAlpha * 0.42f)));
        guiGraphics.fill(x, y, x + 2, y + height,
                dreamingFishCore$withAlpha(accentColor, (int) (contentAlpha * 0.86f)));
        guiGraphics.fill(x + 10, y + height - 1, x + width - 8, y + height,
                dreamingFishCore$withAlpha(0xFF6E6A64, (int) (contentAlpha * 0.32f)));

        guiGraphics.drawString(this.font, dreamingFishCore$trimToWidth(actionText, width - 20), x + 10, y + 8,
                dreamingFishCore$withAlpha(0xFFDCD5CA, contentAlpha), false);
        guiGraphics.drawString(this.font, dreamingFishCore$trimToWidth(warningText, width - 20), x + 10, y + 22,
                dreamingFishCore$withAlpha(0xFFD23E3A, contentAlpha), false);
    }

    @Unique
    private void dreamingFishCore$drawDeathMinimap(GuiGraphics guiGraphics, DeathScreenDataStorage.DeathScreenData data,
                                                  int contentAlpha, int virtualW, int virtualH) {
        int mapSize = Math.min(138, Math.max(102, virtualW / 5));
        int panelW = mapSize + 18;
        int panelH = mapSize + 48;
        int x = Math.max(16, virtualW - panelW - 18);
        int y = Math.max(10, Math.min(28, virtualH / 12));
        int mapX = x + 9;
        int mapY = y + 24;
        int centerX = mapX + mapSize / 2;
        int centerY = mapY + mapSize / 2;

        guiGraphics.fill(x, y, x + panelW, y + panelH,
                dreamingFishCore$withAlpha(0xFF05070A, (int) (contentAlpha * 0.44f)));
        guiGraphics.fill(x, y, x + panelW, y + 1,
                dreamingFishCore$withAlpha(0xFFB83B37, (int) (contentAlpha * 0.62f)));
        guiGraphics.fill(x + 9, y + 12, x + panelW - 9, y + 13,
                dreamingFishCore$withAlpha(0xFF6A6660, (int) (contentAlpha * 0.30f)));
        guiGraphics.drawString(this.font, "死亡位置地图", x + 9, y + 5,
                dreamingFishCore$withAlpha(0xFFDAD4CC, contentAlpha), false);

        guiGraphics.fill(mapX - 1, mapY - 1, mapX + mapSize + 1, mapY + mapSize + 1,
                dreamingFishCore$withAlpha(0xFF07080A, (int) (contentAlpha * 0.74f)));
        dreamingFishCore$drawMapTiles(guiGraphics, data, mapX, mapY, mapSize, contentAlpha);

        int ringColor = dreamingFishCore$withAlpha(0xFFE6DDD1, (int) (contentAlpha * 0.70f));
        guiGraphics.fill(centerX - 10, centerY - 1, centerX - 4, centerY + 1, ringColor);
        guiGraphics.fill(centerX + 4, centerY - 1, centerX + 10, centerY + 1, ringColor);
        guiGraphics.fill(centerX - 1, centerY - 10, centerX + 1, centerY - 4, ringColor);
        guiGraphics.fill(centerX - 1, centerY + 4, centerX + 1, centerY + 10, ringColor);
        dreamingFishCore$drawDeathCross(guiGraphics, centerX, centerY, contentAlpha);

        String dimension = "维度  " + formatDimension(data.dimension());
        String coord = String.format("坐标  X:%d  Y:%d  Z:%d", (int) data.deathX(), (int) data.deathY(), (int) data.deathZ());
        guiGraphics.drawString(this.font, dreamingFishCore$trimToWidth(dimension, panelW - 18), x + 9, mapY + mapSize + 6,
                dreamingFishCore$withAlpha(0xFFAFA8A0, contentAlpha), false);
        guiGraphics.drawString(this.font, dreamingFishCore$trimToWidth(coord, panelW - 18), x + 9, mapY + mapSize + 19,
                dreamingFishCore$withAlpha(0xFF9C9690, contentAlpha), false);
    }

    @Unique
    private void dreamingFishCore$drawMapTiles(GuiGraphics guiGraphics, DeathScreenDataStorage.DeathScreenData data,
                                               int mapX, int mapY, int mapSize, int contentAlpha) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        int cells = 31;
        int cellSize = Math.max(2, mapSize / cells);
        int drawnSize = cells * cellSize;
        int offset = (mapSize - drawnSize) / 2;
        int startX = mapX + offset;
        int startY = mapY + offset;
        int deathBlockX = (int) Math.floor(data.deathX());
        int deathBlockZ = (int) Math.floor(data.deathZ());
        int sampleStep = 16;

        for (int row = 0; row < cells; row++) {
            for (int col = 0; col < cells; col++) {
                int worldX = deathBlockX + (col - cells / 2) * sampleStep;
                int worldZ = deathBlockZ + (row - cells / 2) * sampleStep;
                int color = dreamingFishCore$sampleMapColor(level, worldX, worldZ, data.deathY(), contentAlpha);
                int px = startX + col * cellSize;
                int py = startY + row * cellSize;
                guiGraphics.fill(px, py, px + cellSize, py + cellSize, color);
            }
        }

        guiGraphics.fill(mapX, mapY, mapX + mapSize, mapY + 1,
                dreamingFishCore$withAlpha(0xFFFFFFFF, (int) (contentAlpha * 0.16f)));
        guiGraphics.fill(mapX, mapY + mapSize - 1, mapX + mapSize, mapY + mapSize,
                dreamingFishCore$withAlpha(0xFF000000, (int) (contentAlpha * 0.42f)));
        guiGraphics.fill(mapX, mapY, mapX + 1, mapY + mapSize,
                dreamingFishCore$withAlpha(0xFFFFFFFF, (int) (contentAlpha * 0.12f)));
        guiGraphics.fill(mapX + mapSize - 1, mapY, mapX + mapSize, mapY + mapSize,
                dreamingFishCore$withAlpha(0xFF000000, (int) (contentAlpha * 0.38f)));
    }

    @Unique
    private int dreamingFishCore$sampleMapColor(ClientLevel level, int worldX, int worldZ, double fallbackY, int contentAlpha) {
        if (level == null) {
            return dreamingFishCore$withAlpha(0xFF16191B, (int) (contentAlpha * 0.74f));
        }

        try {
            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
            if (surfaceY <= level.getMinBuildHeight()) {
                surfaceY = (int) Math.floor(fallbackY);
            }
            BlockPos pos = new BlockPos(worldX, Math.max(level.getMinBuildHeight(), surfaceY - 1), worldZ);
            BlockState state = level.getBlockState(pos);
            MapColor mapColor = state.getMapColor(level, pos);
            int rgb = mapColor.col;
            int shade = Math.floorMod(worldX * 13 + worldZ * 7, 24) - 12;
            int r = Math.max(0, Math.min(255, ((rgb >> 16) & 255) + shade));
            int g = Math.max(0, Math.min(255, ((rgb >> 8) & 255) + shade));
            int b = Math.max(0, Math.min(255, (rgb & 255) + shade));
            return dreamingFishCore$withAlpha((r << 16) | (g << 8) | b, (int) (contentAlpha * 0.82f));
        } catch (Exception ignored) {
            return dreamingFishCore$withAlpha(0xFF11161A, (int) (contentAlpha * 0.68f));
        }
    }

    @Unique
    private void dreamingFishCore$drawDeathCross(GuiGraphics guiGraphics, int centerX, int centerY, int contentAlpha) {
        int shadow = dreamingFishCore$withAlpha(0xFF000000, (int) (contentAlpha * 0.82f));
        int red = dreamingFishCore$withAlpha(0xFFD23E3A, contentAlpha);
        for (int i = -5; i <= 5; i++) {
            guiGraphics.fill(centerX + i - 1, centerY + i, centerX + i + 2, centerY + i + 3, shadow);
            guiGraphics.fill(centerX + i, centerY + i, centerX + i + 2, centerY + i + 2, red);
            guiGraphics.fill(centerX + i - 1, centerY - i, centerX + i + 2, centerY - i + 3, shadow);
            guiGraphics.fill(centerX + i, centerY - i, centerX + i + 2, centerY - i + 2, red);
        }
    }

    @Unique
    private String dreamingFishCore$trimToWidth(String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(0, maxWidth - this.font.width("..."))) + "...";
    }

    @Unique
    private void dreamingFishCore$drawCenteredScaledText(GuiGraphics guiGraphics, String text, int centerX, int y,
                                                        float scale, int color, int shadowColor) {
        if (text == null || text.isEmpty()) {
            return;
        }

        PoseStack poseStack = guiGraphics.pose();
        int textWidth = this.font.width(text);
        int scaledX = Math.round((centerX - textWidth * scale / 2.0f) / scale);
        int scaledY = Math.round(y / scale);

        poseStack.pushPose();
        poseStack.scale(scale, scale, 1.0f);
        guiGraphics.drawString(this.font, text, scaledX + 1, scaledY + 1, shadowColor, false);
        guiGraphics.drawString(this.font, text, scaledX, scaledY, color, false);
        poseStack.popPose();
    }

    /**
     * 格式化维度名称
     */
    @Unique
    private String formatDimension(String dimension) {
        return switch (dimension) {
            case "minecraft:overworld" -> "主世界";
            case "minecraft:the_nether" -> "下界";
            case "minecraft:the_end" -> "末地";
            default -> dimension;
        };
    }

    /**
     * 压迫感风格的自定义按钮
     */
    @Unique
    private static class CustomButton extends Button {
        private final boolean isKeepInventory;
        private final float cost;
        private final float currentPoints;

        public CustomButton(int x, int y, int width, int height, Component message,
                           boolean isKeepInventory, float cost, float currentPoints, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.isKeepInventory = isKeepInventory;
            this.cost = cost;
            this.currentPoints = currentPoints;
        }

        @Unique
        public void dreamingFishCore$setBounds(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = isHovered();
            boolean canAfford = this.cost <= 0 || currentPoints >= cost;

            int x = getX();
            int y = getY();
            int w = width;
            int h = height;
            boolean isTitleAction = this.cost <= 0 && !isKeepInventory;
            int accentColor = isTitleAction ? 0xFFB85B56 : (isKeepInventory ? 0xFFD2A157 : 0xFF8FC274);

            if (!this.active || !canAfford) {
                accentColor = 0xFF5E5B57;
            }

            String displayText = dreamingFishCore$fitButtonText(getMessage().getString(), Math.max(0, w - 10));
            int textColor = this.active && canAfford ? (hovered ? 0xFFF4EEE5 : 0xFFD8D0C6) : 0xFF77716A;
            int textX = x + w / 2 - Minecraft.getInstance().font.width(displayText) / 2;
            int textY = y + Math.max(2, (h - 8) / 2);

            guiGraphics.drawString(Minecraft.getInstance().font, displayText, textX, textY, textColor, false);
            int underlineY = y + h - 3;
            guiGraphics.fill(x + 8, underlineY, x + w - 8, underlineY + 1, 0x553F3F3F);
            if (hovered && this.active && canAfford) {
                guiGraphics.fill(x + 14, underlineY, x + w - 14, underlineY + 2, accentColor);
            }
        }

        @Unique
        private String dreamingFishCore$fitButtonText(String text, int maxWidth) {
            if (Minecraft.getInstance().font.width(text) <= maxWidth) {
                return text;
            }

            String ellipsis = "...";
            return Minecraft.getInstance().font.plainSubstrByWidth(
                    text,
                    Math.max(0, maxWidth - Minecraft.getInstance().font.width(ellipsis))
            ) + ellipsis;
        }
    }
}
