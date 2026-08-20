package com.hhy.dreamingfishcore.mixin.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hhy.dreamingfishcore.client.ui.util.UiBackgroundRenderer;
import com.hhy.dreamingfishcore.client.ui.util.VirtualCoordinateHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * TitleScreen Mixin
 * 虚拟坐标系统 640x360 (2560x1440 ÷ 4)
 * 主面板: 85%宽 × 75%高 = 544x270
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    // ==================== 字符串常量 ====================

    @Unique
    private static volatile String dreamingFishCore$updateLogPreview = "§7暂无更新";
    @Unique
    private static volatile boolean dreamingFishCore$updateLogFetchStarted = false;

    private static final String UPDATE_LOG_URL = "https://github.com/QingMo-A/DreamingFishCore/releases";
    private static final String UPDATE_LOG_API_URL = "https://api.github.com/repos/QingMo-A/DreamingFishCore/releases/latest";

    private static final String DREAMINGFISH_COPYRIGHT_TEXT = "© 2026 DreamingFish - DreamingFishCore";

    // 资助面板文案
    private static final String DONATE_WELCOME = "§e§l欢§6§l迎§a§l来§b§l到 §d§l守§9§l望§c§l梦§6§l屿 §8— §7梦鱼服";
    private static final String DONATE_TITLE = "§7本服为§e非营利公益服§7，";
    private static final String DONATE_LINE_1 = "§e公益服维持不易，感谢所有资助者§7。";
    private static final String DONATE_LINE_2 = "§7无偿资助§c无法获得§7游戏内权益和物资，";
    private static final String DONATE_LINE_3 = "§7请您资助前三思。";
    private static final String DONATE_LINE_4 = "§7资助者可自定义设计武器/装备/物品等，";
    private static final String DONATE_LINE_5 = "§7且可以自定义属性、外观（数值保证合理）。";
    private static final String DONATE_LINE_6 = "§7开发完成后可以让所有人§a获取§7。";

    // 颜色定义
    private static final int ACCENT_BLUE = 0xFF0088FF;
    private static final int ACCENT_GREEN = 0xFF44FF88;
    private static final int ACCENT_GOLD = 0xFFFFAA44;
    private static final int ACCENT_RED = 0xFFE05F5F;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFFAAAAAA;

    @Shadow @Final
    private boolean fading;

    @Shadow
    private long fadeInStart;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Unique
    private VirtualCoordinateHelper.VirtualSizeResult dreamingFishCore$virtualSize = new VirtualCoordinateHelper.VirtualSizeResult();

    @Unique
    private long dreamingFishCore$hoverTime = 0;

    @Unique
    private int dreamingFishCore$hoveredButtonIndex = -1;
    @Unique
    private boolean dreamingFishCore$donateExpanded = false;
    @Unique
    private int dreamingFishCore$donateButtonX = 0;
    @Unique
    private int dreamingFishCore$donateButtonY = 0;
    @Unique
    private int dreamingFishCore$donateButtonW = 0;
    @Unique
    private int dreamingFishCore$donateButtonH = 0;
    @Unique
    private int dreamingFishCore$donateCloseX = 0;
    @Unique
    private int dreamingFishCore$donateCloseY = 0;
    @Unique
    private int dreamingFishCore$donateCloseSize = 0;
    @Unique
    private Button dreamingFishCore$updateLogButton;
    @Unique
    private final java.util.List<AbstractWidget> dreamingFishCore$relayedAuxButtons = new java.util.ArrayList<>();
    @Unique
    private final java.util.List<dreamingFishCore$AuxButtonLayout> dreamingFishCore$relayedAuxButtonLayouts = new java.util.ArrayList<>();

    @Unique
    private record dreamingFishCore$AuxButtonLayout(AbstractWidget button, int x, int y, int width, int height) {
    }

    @Unique
    private record dreamingFishCore$MainButtonBounds(int x, int y, int width, int height) {
    }

    @Unique
    private long dreamingFishCore$openTime = 0;

    @Unique
    private static final long ANIMATION_DURATION = 600; // 600ms 滑入动画
    @Unique
    private static final float EASE_POWER = 2.0F; // 缓动指数
    @Unique
    private static final int MAIN_BTN_W = 132;
    @Unique
    private static final int MAIN_BTN_H = 24;
    @Unique
    private static final int MAIN_BTN_GAP = 5;
    @Unique
    private static final int MAIN_PANEL_W = 182;
    @Unique
    private static final int MAIN_PANEL_H = 112;
    @Unique
    private static final int LAYOUT_MARGIN = 34;
    @Unique
    private static final int DONATE_PANEL_H = 92;
    @Unique
    private static final int FOOTER_RESERVE_H = 34;
    @Unique
    private static final int CINEMATIC_BLACK_ALPHA = 168;

    @Inject(method = "init", at = @At("RETURN"))
    private void dreamingFishCore$init(CallbackInfo ci) {
        dreamingFishCore$startUpdateLogFetch();
        dreamingFishCore$updateLogButton = Button.builder(Component.literal("更新日志"),
                button -> dreamingFishCore$openUpdateLog(Minecraft.getInstance()))
                .bounds(-1000, -1000, 72, 20)
                .build();
        this.addRenderableWidget(dreamingFishCore$updateLogButton);
        // openTime 会在渐显完成时设置
    }

    @Unique
    private String dreamingFishCore$getTranslationKey(Component component) {
        // 获取Component的Contents，如果是TranslatableContents则返回key
        if (component.getContents() instanceof TranslatableContents) {
            return ((TranslatableContents) component.getContents()).getKey();
        }
        return null;
    }

    @Unique
    private boolean dreamingFishCore$isPrimaryVanillaButtonKey(String key) {
        if (key == null) {
            return false;
        }

        return "menu.singleplayer".equals(key)
                || "menu.multiplayer".equals(key)
                || "menu.online".equals(key)          // Realms
                || "menu.options".equals(key)         // 设置/选项
                || "menu.quit".equals(key);           // 退出
    }

    @Unique
    private void dreamingFishCore$relayModButtons(java.util.List<AbstractWidget> modButtons) {
        if (modButtons.isEmpty()) {
            dreamingFishCore$relayedAuxButtons.clear();
            dreamingFishCore$relayedAuxButtonLayouts.clear();
            return;
        }
        dreamingFishCore$relayedAuxButtons.clear();
        dreamingFishCore$relayedAuxButtonLayouts.clear();

        // 右上角区域，所有辅助/模组兼容按钮统一排布
        int startX = this.width - 10;
        int startY = 8;
        int buttonGap = 4;
        int buttonH = 20;
        int rowStep = buttonH + buttonGap;
        int maxButtonsPerRow = 4;
        int maxRowWidth = Math.min(330, Math.max(190, this.width / 3));
        int row = 0;
        int rowWidth = 0;
        java.util.List<AbstractWidget> rowButtons = new java.util.ArrayList<>();
        java.util.List<Integer> rowWidths = new java.util.ArrayList<>();

        for (AbstractWidget btn : modButtons) {
            int buttonWidth = dreamingFishCore$getAuxButtonWidth(btn);
            int nextRowWidth = rowButtons.isEmpty() ? buttonWidth : rowWidth + buttonGap + buttonWidth;
            if (!rowButtons.isEmpty()
                    && (rowButtons.size() >= maxButtonsPerRow || nextRowWidth > maxRowWidth)) {
                row = dreamingFishCore$placeAuxButtonRow(rowButtons, rowWidths, row, startX, startY,
                        rowStep, buttonH, buttonGap);
                rowButtons.clear();
                rowWidths.clear();
                rowWidth = 0;
            }

            rowButtons.add(btn);
            rowWidths.add(buttonWidth);
            rowWidth = rowButtons.size() == 1 ? buttonWidth : rowWidth + buttonGap + buttonWidth;
        }

        if (!rowButtons.isEmpty()) {
            dreamingFishCore$placeAuxButtonRow(rowButtons, rowWidths, row, startX, startY, rowStep, buttonH,
                    buttonGap);
        }
    }

    @Unique
    private int dreamingFishCore$placeAuxButtonRow(java.util.List<AbstractWidget> rowButtons,
                                                   java.util.List<Integer> rowWidths,
                                                   int row,
                                                   int startX,
                                                   int startY,
                                                   int rowStep,
                                                   int buttonH,
                                                   int buttonGap) {
        int totalWidth = 0;
        for (int i = 0; i < rowWidths.size(); i++) {
            totalWidth += rowWidths.get(i);
            if (i > 0) {
                totalWidth += buttonGap;
            }
        }

        int cursorX = Math.max(8, startX - totalWidth);
        for (int i = 0; i < rowButtons.size(); i++) {
            AbstractWidget btn = rowButtons.get(i);
            int buttonWidth = rowWidths.get(i);
            btn.setWidth(buttonWidth);
            btn.setHeight(buttonH);
            int targetY = startY + row * rowStep;
            float progress = dreamingFishCore$getIntroProgress(0.24F + row * 0.06F);
            int btnY = targetY - (int) ((1.0F - progress) * 16);

            btn.setX(cursorX);
            btn.setY(btnY);
            btn.setAlpha(0.0F);
            dreamingFishCore$relayedAuxButtons.add(btn);
            dreamingFishCore$relayedAuxButtonLayouts.add(
                    new dreamingFishCore$AuxButtonLayout(btn, cursorX, btnY, buttonWidth, buttonH));
            cursorX += buttonWidth + buttonGap;
        }
        return row + 1;
    }

    @Unique
    private void dreamingFishCore$hideVanillaButtons() {
        TitleScreen self = (TitleScreen) (Object) this;

        // 遍历所有子元素，隐藏原版按钮
        for (var widget : self.children()) {
            if (widget instanceof AbstractWidget) {
                AbstractWidget aw = (AbstractWidget) widget;
                String translationKey = dreamingFishCore$getTranslationKey(aw.getMessage());

                if (dreamingFishCore$isPrimaryVanillaButtonKey(translationKey)) {
                    // 隐藏原版按钮（移到屏幕外）
                    aw.setX(-1000);
                }
            }
        }
    }

    @Unique
    private void dreamingFishCore$hideVanillaButtonsAndRelayModButtons() {
        TitleScreen self = (TitleScreen) (Object) this;

        // 收集模组按钮（非原版按钮）
        java.util.List<AbstractWidget> modButtons = new java.util.ArrayList<>();

        for (var widget : self.children()) {
            if (widget instanceof AbstractWidget) {
                AbstractWidget aw = (AbstractWidget) widget;
                String translationKey = dreamingFishCore$getTranslationKey(aw.getMessage());

                if (dreamingFishCore$isPrimaryVanillaButtonKey(translationKey)) {
                    // 隐藏原版按钮（移到屏幕外）
                    aw.setX(-1000);
                } else if (aw instanceof PlainTextButton) {
                    // 保留 Mojang 原版版权文字按钮，放回左下角
                    aw.setX(2);
                    aw.setY(this.height - 12);
                } else if (aw == dreamingFishCore$updateLogButton || aw.getX() >= 0) {
                    // 收集原版辅助按钮、Forge/其他模组按钮和我们新增的更新日志按钮
                    modButtons.add(aw);
                }
            }
        }

        // 将模组按钮重新排列到右下角
        dreamingFishCore$relayModButtons(modButtons);
    }


    @Unique
    private void dreamingFishCore$renderBackground(GuiGraphics guiGraphics, float fadeAlpha) {
        UiBackgroundRenderer.renderCyclingBackgroundCrossfade(guiGraphics, this.width, this.height, fadeAlpha);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        // 取消原版渲染，完全替换标题界面
        ci.cancel();

        // 处理淡入效果
        if (this.fadeInStart == 0L) {
            this.fadeInStart = System.currentTimeMillis();
        }

        // 计算淡入alpha值
        float fadeAlpha = this.fading ? java.lang.Math.min((System.currentTimeMillis() - this.fadeInStart) / 1000.0F, 1.0F) : 1.0F;

        // ========== 步骤1: 渲染背景图 ==========
        dreamingFishCore$renderBackground(guiGraphics, fadeAlpha);
        dreamingFishCore$renderCinematicBackgroundOverlay(guiGraphics, fadeAlpha);

        // 如果渐显未完成，提前返回（只渲染背景，等渐显完成后再渲染卡片）
        if (fadeAlpha < 1.0F) {
            return;
        }

        // 渐显完成时，初始化动画开始时间
        if (dreamingFishCore$openTime == 0) {
            dreamingFishCore$openTime = System.currentTimeMillis();
        }

        // ========== 步骤2: 手动调用Forge钩子，让其他模组可以添加按钮 ==========
        net.neoforged.neoforge.client.ClientHooks.renderMainMenu(
            (TitleScreen) (Object) this,
            guiGraphics,
            this.font,
            this.width,
            this.height,
            0xFFFFFFFF
        );

        // ========== 步骤3: 隐藏原版按钮并重新定位模组按钮 ==========
        dreamingFishCore$hideVanillaButtonsAndRelayModButtons();

        long time = System.currentTimeMillis();

        // 计算虚拟坐标系统
        VirtualCoordinateHelper.calculateVirtualSize(this, dreamingFishCore$virtualSize);

        float scale = dreamingFishCore$virtualSize.uiScale;
        int virtualW = dreamingFishCore$virtualSize.virtualWidth;
        int virtualH = dreamingFishCore$virtualSize.virtualHeight;

        // 屏幕空间虚拟坐标（用于右下角/右上角按钮定位，无居中偏移）
        int vmxScreen = (int) (mouseX / scale);
        int vmyScreen = (int) (mouseY / scale);

        // ===== 按钮悬停检测（使用屏幕空间坐标） =====
        int newHoveredIndex = dreamingFishCore$detectButtonHover(vmxScreen, vmyScreen, virtualW, virtualH);
        if (newHoveredIndex != this.dreamingFishCore$hoveredButtonIndex) {
            this.dreamingFishCore$hoveredButtonIndex = newHoveredIndex;
            this.dreamingFishCore$hoverTime = time;
        }

        // 计算动画进度
        long elapsed = time - dreamingFishCore$openTime;
        float animationProgress = Math.min((float) elapsed / ANIMATION_DURATION, 1.0F);
        float easedProgress = dreamingFishCore$easeOutCubic(animationProgress);

        // 渲染标题界面主体（屏幕空间，无居中偏移）
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.scale(scale, scale, 1.0f);
        dreamingFishCore$renderTitleLayout(guiGraphics, virtualW, virtualH, easedProgress, time);
        poseStack.popPose();

        // 渲染左下角版权（屏幕空间，无居中偏移）
        dreamingFishCore$renderFooter(guiGraphics, scale);

        // 右上角辅助按钮由梦鱼核心统一绘制，避免原版控件与自定义外观重复叠加。
        dreamingFishCore$renderRelayedAuxButtons(guiGraphics, mouseX, mouseY);

        dreamingFishCore$scale = scale;
    }

    // ==================== 电影感标题布局 ====================

    @Unique private static final String[] MAIN_BTN_TEXTS = {"§l多人游戏", "单人游戏", "设置", "退出游戏"};
    @Unique private static final String[] MAIN_BTN_DESCS = {
        "§a多人游戏 §7— 选择服务器，加入§e梦屿§7与其他玩家共同冒险",
        "§6单人游戏 §7— 选择或创建你的单人世界",
        "§9设置 §7— 调整游戏选项，自定义你的体验",
        "§c退出游戏 §7— 离开梦屿，返回现实世界"
    };
    @Unique private static final int[] MAIN_BTN_COLORS = {ACCENT_GREEN, ACCENT_GOLD, ACCENT_BLUE, 0xFFCC6666};
    @Unique private static final int MAIN_MULTIPLAYER = 0;
    @Unique private static final int MAIN_SINGLEPLAYER = 1;
    @Unique private static final int MAIN_SETTINGS = 2;
    @Unique private static final int MAIN_EXIT = 3;

    // hover 索引编码：主按钮索引
    @Unique
    private int dreamingFishCore$detectButtonHover(int vmx, int vmy, int virtualW, int virtualH) {
        int menuX = dreamingFishCore$getMenuX(virtualW);
        int menuY = dreamingFishCore$getMenuY(virtualH);

        for (int i = 0; i < MAIN_BTN_TEXTS.length; i++) {
            dreamingFishCore$MainButtonBounds bounds = dreamingFishCore$getMainButtonBounds(i, menuX, menuY, 0);
            if (vmx >= bounds.x() && vmx <= bounds.x() + bounds.width()
                    && vmy >= bounds.y() && vmy <= bounds.y() + bounds.height()) {
                return i;
            }
        }

        return -1;
    }

    @Unique
    private int dreamingFishCore$getMenuX(int virtualW) {
        int preferred = virtualW - MAIN_PANEL_W - 22;
        int leftBound = virtualW >= 430 ? 238 : 170;
        int rightBound = Math.max(8, virtualW - MAIN_PANEL_W - 8);
        return Math.min(Math.max(leftBound, preferred), rightBound);
    }

    @Unique
    private int dreamingFishCore$getMenuY(int virtualH) {
        int preferred = virtualH - MAIN_PANEL_H - FOOTER_RESERVE_H - 10;
        int maxY = Math.max(34, virtualH - MAIN_PANEL_H - FOOTER_RESERVE_H - 8);
        return Math.min(Math.max(44, preferred), maxY);
    }

    @Unique
    private void dreamingFishCore$renderCinematicBackgroundOverlay(GuiGraphics guiGraphics, float fadeAlpha) {
        int alpha = (int) (fadeAlpha * 255.0F);
        guiGraphics.fill(0, 0, this.width, this.height, dreamingFishCore$withAlpha(0xFF05070A, Math.min(64, alpha)));

        long now = System.currentTimeMillis();
        int sweepX = (int) ((now / 32L) % Math.max(1, this.width + 260)) - 260;
        for (int i = 0; i < 8; i++) {
            int x = sweepX + i * 12;
            guiGraphics.fill(x, 0, x + 1, this.height, dreamingFishCore$withAlpha(ACCENT_BLUE, 10));
        }

        for (int y = 0; y < this.height; y += 4) {
            guiGraphics.fill(0, y, this.width, y + 1, dreamingFishCore$withAlpha(0xFF000000, 18));
        }
    }

    @Unique
    private void dreamingFishCore$renderTitleLayout(GuiGraphics guiGraphics, int virtualW, int virtualH,
                                                   float animProgress, long time) {
        int hovered = dreamingFishCore$hoveredButtonIndex;
        int mainHover = hovered;

        float titleProgress = dreamingFishCore$getStaggeredProgress(animProgress, 0.0F);
        float menuProgress = dreamingFishCore$getStaggeredProgress(animProgress, 0.16F);
        int titleX = LAYOUT_MARGIN - (int) ((1.0F - titleProgress) * 18);
        int titleY = Math.max(44, Math.min(virtualH / 2 - 94, virtualH - 244));
        int stableMenuX = dreamingFishCore$getMenuX(virtualW);
        int menuX = stableMenuX + (int) ((1.0F - menuProgress) * 18);
        int menuY = dreamingFishCore$getMenuY(virtualH);
        int leftMaxWidth = Math.max(150, stableMenuX - titleX - 28);

        dreamingFishCore$renderLeftReadabilityColumn(guiGraphics, stableMenuX, virtualH, titleProgress);
        dreamingFishCore$renderHero(guiGraphics, titleX, titleY, leftMaxWidth, time);
        dreamingFishCore$renderMainMenuPanel(guiGraphics, menuX, menuY, mainHover, menuProgress, time);
        dreamingFishCore$renderDonateNotice(guiGraphics, virtualW, virtualH, stableMenuX);

    }

    @Unique
    private void dreamingFishCore$renderLeftReadabilityColumn(GuiGraphics guiGraphics, int stableMenuX, int virtualH,
                                                              float progress) {
        int columnW = Math.max(176, Math.min(stableMenuX - 12, 336));
        int columnAlpha = (int) (CINEMATIC_BLACK_ALPHA * progress);
        guiGraphics.fill(0, 0, columnW, virtualH, dreamingFishCore$withAlpha(0xFF000000, columnAlpha));
        guiGraphics.fill(columnW - 1, 0, columnW, virtualH, dreamingFishCore$withAlpha(0xFFFFFFFF, (int) (16 * progress)));
    }

    @Unique
    private void dreamingFishCore$renderHero(GuiGraphics guiGraphics, int x, int y, int maxWidth, long time) {
        int pulse = 72 + (int) (Math.sin(time / 760.0D) * 24.0D);
        int textMax = Math.max(112, maxWidth - 10);

        guiGraphics.fill(x + 10, y + 3, x + 44, y + 5, dreamingFishCore$withAlpha(ACCENT_BLUE, 190));
        guiGraphics.drawString(this.font,
                dreamingFishCore$fitText("灾变之后，仍有人在这里守望。", textMax),
                x + 50, y, 0xFFC9F7F2, true);

        dreamingFishCore$drawScaledText(guiGraphics, "欢迎来到", x + 8, y + 25, 2.32F, 0xFFF2E7C8, true);
        dreamingFishCore$drawScaledText(guiGraphics, "梦屿", x + 8, y + 52, 3.42F, 0xFFFFF5D1, true);
        guiGraphics.fill(x + 10, y + 95, x + Math.min(maxWidth, 118), y + 96,
                dreamingFishCore$withAlpha(ACCENT_GOLD, pulse));

        if (!dreamingFishCore$donateExpanded) {
            String line1 = dreamingFishCore$fitText("一片曾让人慢下来生活、重新开始做梦的大陆。", textMax);
            String line2 = dreamingFishCore$fitText("如今，它正在风暴与沉默之间，等待新的幸存者。", textMax);
            guiGraphics.drawString(this.font, line1, x + 10, y + 108, 0xFFE8DDBD, true);
            guiGraphics.drawString(this.font, line2, x + 10, y + 121, 0xFFE8DDBD, true);
        }
    }

    @Unique
    private void dreamingFishCore$drawStatusChip(GuiGraphics guiGraphics, int x, int y, String label, String value,
                                                 int accent) {
        int w = Math.max(58, this.font.width(value) + 18);
        dreamingFishCore$drawPixelCutRect(guiGraphics, x, y, w, 24, 0x74070B0F);
        guiGraphics.fill(x + 2, y + 2, x + 3, y + 22, dreamingFishCore$withAlpha(accent, 150));
        guiGraphics.drawString(this.font, "§8" + label, x + 8, y + 4, 0xFF89919A, false);
        guiGraphics.drawString(this.font, value, x + 8, y + 14, TEXT_WHITE, false);
    }

    @Unique
    private void dreamingFishCore$renderDonateNotice(GuiGraphics guiGraphics, int virtualW, int virtualH,
                                                     int stableMenuX) {
        int buttonW = 88;
        int buttonH = 16;
        int buttonX = LAYOUT_MARGIN;
        int buttonY = Math.max(112, virtualH - FOOTER_RESERVE_H - buttonH - 8);
        boolean buttonHovered = dreamingFishCore$isVirtualMouseInside(buttonX, buttonY, buttonW, buttonH);

        dreamingFishCore$donateButtonX = buttonX;
        dreamingFishCore$donateButtonY = buttonY;
        dreamingFishCore$donateButtonW = buttonW;
        dreamingFishCore$donateButtonH = buttonH;

        dreamingFishCore$drawPixelCutRect(guiGraphics, buttonX + 2, buttonY + 2, buttonW, buttonH, 0x42000000);
        dreamingFishCore$drawPixelCutRect(guiGraphics, buttonX, buttonY, buttonW, buttonH,
                buttonHovered || dreamingFishCore$donateExpanded ? 0x9A11161B : 0x70090C10);
        guiGraphics.fill(buttonX + 2, buttonY + buttonH - 2, buttonX + buttonW - 2, buttonY + buttonH - 1,
                dreamingFishCore$withAlpha(ACCENT_GOLD, buttonHovered || dreamingFishCore$donateExpanded ? 190 : 92));
        guiGraphics.drawString(this.font, "公益资助说明", buttonX + 8, buttonY + 4,
                buttonHovered || dreamingFishCore$donateExpanded ? TEXT_WHITE : 0xFFC5CBD0, false);

        if (!dreamingFishCore$donateExpanded) {
            dreamingFishCore$donateCloseSize = 0;
            return;
        }

        int panelAvailableW = Math.min(stableMenuX - LAYOUT_MARGIN - 18, virtualW - LAYOUT_MARGIN * 2);
        int panelW = Math.max(96, Math.min(268, panelAvailableW));
        int panelH = Math.min(DONATE_PANEL_H + 12, Math.max(68, buttonY - 50));
        int panelX = buttonX;
        int panelY = Math.max(42, buttonY - panelH - 8);
        if (panelY + panelH + 8 > buttonY) {
            panelH = Math.max(68, buttonY - panelY - 8);
        }

        dreamingFishCore$drawDonateDetailPanel(guiGraphics, panelX, panelY, panelW, panelH);
    }

    @Unique
    private void dreamingFishCore$drawDonateDetailPanel(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        dreamingFishCore$drawPixelCutRect(guiGraphics, x + 3, y + 4, w, h, 0x62000000);
        dreamingFishCore$drawPixelCutRect(guiGraphics, x, y, w, h, 0xFF090C10);
        guiGraphics.fill(x + 8, y, x + w - 8, y + 1, 0x42FFFFFF);
        guiGraphics.fill(x + 8, y + h - 2, x + w - 8, y + h - 1, dreamingFishCore$withAlpha(ACCENT_GOLD, 110));

        dreamingFishCore$donateCloseSize = 10;
        dreamingFishCore$donateCloseX = x + w - 16;
        dreamingFishCore$donateCloseY = y + 7;
        boolean closeHovered = dreamingFishCore$isVirtualMouseInside(dreamingFishCore$donateCloseX,
                dreamingFishCore$donateCloseY, dreamingFishCore$donateCloseSize, dreamingFishCore$donateCloseSize);
        dreamingFishCore$drawPixelCutRect(guiGraphics, dreamingFishCore$donateCloseX, dreamingFishCore$donateCloseY,
                dreamingFishCore$donateCloseSize, dreamingFishCore$donateCloseSize,
                closeHovered ? 0x88AA3333 : 0x50161A1E);
        guiGraphics.drawString(this.font, "x", dreamingFishCore$donateCloseX + 3,
                dreamingFishCore$donateCloseY + 1, closeHovered ? TEXT_WHITE : 0xFFB9C0C8, false);

        int ty = y + 8;
        int textMax = Math.max(80, w - 22);
        ty = dreamingFishCore$drawDonateLine(guiGraphics, DONATE_WELCOME, x + 10, ty, y + h - 8, textMax - 14, TEXT_WHITE, false, 12);
        ty = dreamingFishCore$drawDonateLine(guiGraphics, DONATE_TITLE, x + 10, ty, y + h - 8, textMax, TEXT_GRAY, true, 11);
        ty = dreamingFishCore$drawDonateLine(guiGraphics, DONATE_LINE_1, x + 10, ty, y + h - 8, textMax, TEXT_GRAY, true, 11);
        ty = dreamingFishCore$drawDonateLine(guiGraphics, DONATE_LINE_2, x + 10, ty, y + h - 8, textMax, TEXT_GRAY, true, 11);
        ty = dreamingFishCore$drawDonateLine(guiGraphics, DONATE_LINE_3, x + 10, ty, y + h - 8, textMax, TEXT_GRAY, true, 11);
        ty = dreamingFishCore$drawDonateLine(guiGraphics, DONATE_LINE_4, x + 10, ty, y + h - 8, textMax, TEXT_GRAY, true, 11);
        ty = dreamingFishCore$drawDonateLine(guiGraphics, DONATE_LINE_5, x + 10, ty, y + h - 8, textMax, TEXT_GRAY, true, 11);
        dreamingFishCore$drawDonateLine(guiGraphics, DONATE_LINE_6, x + 10, ty, y + h - 8, textMax, TEXT_GRAY, true, 11);
    }

    @Unique
    private int dreamingFishCore$drawDonateLine(GuiGraphics guiGraphics, String text, int x, int y, int maxY,
                                                int maxWidth, int color, boolean shadow, int step) {
        if (y > maxY) {
            return y;
        }
        guiGraphics.drawString(this.font, dreamingFishCore$fitText(text, maxWidth), x, y, color, shadow);
        return y + step;
    }

    @Unique
    private boolean dreamingFishCore$isVirtualMouseInside(int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        int vmx = (int) (mc.mouseHandler.xpos() / dreamingFishCore$scale);
        int vmy = (int) (mc.mouseHandler.ypos() / dreamingFishCore$scale);
        return vmx >= x && vmx <= x + w && vmy >= y && vmy <= y + h;
    }

    @Unique
    private void dreamingFishCore$renderMainMenuPanel(GuiGraphics guiGraphics, int menuX, int menuY, int mainHover,
                                                      float animProgress, long time) {
        int panelW = MAIN_PANEL_W;
        int panelH = MAIN_PANEL_H;
        int originX = menuX + 4;
        int originY = menuY + 3;

        guiGraphics.fill(originX + 16, originY + 8, originX + 54, originY + 10,
                dreamingFishCore$withAlpha(ACCENT_BLUE, 132));
        guiGraphics.drawString(this.font, "ROUTE SELECT", originX + 62, originY + 4, 0xFFE1E7EC, true);
        guiGraphics.drawString(this.font, "选择你的下一段航线", originX + 18, originY + 16, 0xFFB3BAC1, true);

        int railAlpha = 44 + (int) (Math.sin(time / 620.0D) * 14.0D);
        guiGraphics.fill(menuX + 28, menuY + 47, menuX + 145, menuY + 48,
                dreamingFishCore$withAlpha(0xFFFFFFFF, railAlpha));
        guiGraphics.fill(menuX + 58, menuY + 69, menuX + 143, menuY + 70,
                dreamingFishCore$withAlpha(ACCENT_BLUE, railAlpha));
        guiGraphics.fill(menuX + 92, menuY + 79, menuX + 93, menuY + 95,
                dreamingFishCore$withAlpha(ACCENT_GOLD, railAlpha));

        for (int i = 0; i < MAIN_BTN_TEXTS.length; i++) {
            float stagger = i * 0.06F;
            float progress = dreamingFishCore$getStaggeredProgress(animProgress, stagger);
            int slideOff = (int) ((1.0F - progress) * 16);
            boolean hov = mainHover == i;
            int accent = MAIN_BTN_COLORS[i];

            dreamingFishCore$MainButtonBounds bounds = dreamingFishCore$getMainButtonBounds(i, menuX, menuY, slideOff);
            dreamingFishCore$drawRouteButton(guiGraphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    MAIN_BTN_TEXTS[i], "0" + (i + 1), accent, hov, time);

            if (hov && progress >= 0.95F) {
                String desc = MAIN_BTN_DESCS[i];
                int descY = menuY + panelH + 5;
                int descW = Math.min(235, this.font.width(desc) + 12);
                int descX = menuX + panelW - descW;
                dreamingFishCore$drawPixelCutRect(guiGraphics, descX, descY, descW, 16, 0xB0090C10);
                guiGraphics.fill(descX + 4, descY + 1, descX + descW - 4, descY + 2,
                        dreamingFishCore$withAlpha(accent, 100));
                guiGraphics.drawString(this.font, desc, descX + 6, descY + 5, TEXT_WHITE, true);
            }
        }
    }

    @Unique
    private dreamingFishCore$MainButtonBounds dreamingFishCore$getMainButtonBounds(int index, int menuX, int menuY,
                                                                                   int slideOff) {
        return switch (index) {
            case MAIN_MULTIPLAYER -> new dreamingFishCore$MainButtonBounds(menuX + 34 + slideOff, menuY + 31, 136, 28);
            case MAIN_SINGLEPLAYER -> new dreamingFishCore$MainButtonBounds(menuX + 16 + slideOff, menuY + 63, 82, 24);
            case MAIN_SETTINGS -> new dreamingFishCore$MainButtonBounds(menuX + 104 + slideOff, menuY + 63, 66, 24);
            case MAIN_EXIT -> new dreamingFishCore$MainButtonBounds(menuX + 70 + slideOff, menuY + 91, 78, 22);
            default -> new dreamingFishCore$MainButtonBounds(menuX, menuY, 0, 0);
        };
    }

    @Unique
    private void dreamingFishCore$drawRouteButton(GuiGraphics guiGraphics, int x, int y, int w, int h,
                                                  String label, String index, int accent, boolean hovered,
                                                  long time) {
        int bg = hovered ? 0xB010151B : 0x82070A0F;
        dreamingFishCore$drawPixelCutRect(guiGraphics, x + 2, y + 3, w, h, 0x36000000);
        dreamingFishCore$drawPixelCutRect(guiGraphics, x, y, w, h, bg);
        guiGraphics.fill(x + 2, y + 1, x + w - 2, y + 2, hovered ? 0x34FFFFFF : 0x14FFFFFF);
        guiGraphics.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, 0x4A000000);
        guiGraphics.fill(x, y + 4, x + 2, y + h - 4,
                dreamingFishCore$withAlpha(accent, hovered ? 230 : 96));

        if (hovered) {
            int sweep = (int) ((time / 13L) % (w + 42)) - 42;
            guiGraphics.fill(x + Math.max(0, sweep), y + 1,
                    x + Math.min(w, sweep + 24), y + h - 1,
                    dreamingFishCore$withAlpha(accent, 34));
            guiGraphics.fill(x + 8, y + h - 4, x + w - 8, y + h - 2,
                    dreamingFishCore$withAlpha(accent, 192));
        } else {
            guiGraphics.fill(x + 7, y + h - 4, x + w - 7, y + h - 3,
                    dreamingFishCore$withAlpha(accent, 62));
        }

        int labelY = y + (h - 8) / 2;
        guiGraphics.drawString(this.font, label, x + 12, labelY, hovered ? TEXT_WHITE : 0xFFD9DEE2, true);
        guiGraphics.drawString(this.font, index, x + w - 18, labelY,
                hovered ? dreamingFishCore$withAlpha(accent, 235) : 0xFF7B858E, true);
    }

    @Unique
    private void dreamingFishCore$drawLiquidGlassFrame(GuiGraphics guiGraphics, int x, int y, int w, int h,
                                                       long time) {
        dreamingFishCore$drawPixelCutRect(guiGraphics, x + 4, y + 6, w, h, 0x18000000);
        dreamingFishCore$drawPixelCutRect(guiGraphics, x, y, w, h, 0x14EEF8FF);
        dreamingFishCore$drawPixelCutRect(guiGraphics, x + 1, y + 1, w - 2, h - 2, 0x0E080D14);

        guiGraphics.fill(x + 8, y + 1, x + w - 18, y + 2, 0x42FFFFFF);
        guiGraphics.fill(x + 2, y + 10, x + 3, y + h - 18, 0x26FFFFFF);
        guiGraphics.fill(x + w - 2, y + 14, x + w - 1, y + h - 12, 0x1600A7FF);
        guiGraphics.fill(x + 12, y + h - 2, x + w - 12, y + h - 1, 0x1600A7FF);

        int sweep = (int) ((time / 18L) % (w + 80)) - 80;
        for (int i = 0; i < 5; i++) {
            int sx = x + sweep + i * 10;
            int alpha = Math.max(0, 16 - i * 3);
            guiGraphics.fill(sx, y + 2, sx + 2, y + h - 2, dreamingFishCore$withAlpha(0xFFFFFFFF, alpha));
        }

        for (int row = 0; row < 5; row++) {
            int lineY = y + 18 + row * 25;
            int offset = (int) ((time / 70L + row * 11L) % 18L);
            guiGraphics.fill(x + 14 + offset, lineY, x + w - 18, lineY + 1,
                    dreamingFishCore$withAlpha(row % 2 == 0 ? ACCENT_BLUE : 0xFFFFFFFF, row % 2 == 0 ? 10 : 8));
        }
    }

    @Unique
    private void dreamingFishCore$renderRelayedAuxButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (dreamingFishCore$AuxButtonLayout layout : dreamingFishCore$relayedAuxButtonLayouts) {
            AbstractWidget btn = layout.button();
            if (!btn.visible) {
                continue;
            }

            int x = layout.x();
            int y = layout.y();
            int w = layout.width();
            int h = layout.height();
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
            int accent = hovered ? ACCENT_BLUE : 0xFF6E7E8A;

            dreamingFishCore$drawPixelCutRect(guiGraphics, x + 1, y + 1, w, h, 0x33000000);
            dreamingFishCore$drawPixelCutRect(guiGraphics, x, y, w, h, hovered ? 0xA0182028 : 0x78080C10);
            guiGraphics.fill(x + 2, y + 1, x + w - 2, y + 2, hovered ? 0x34FFFFFF : 0x18FFFFFF);
            guiGraphics.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, 0x46000000);
            guiGraphics.fill(x + 4, y + h - 3, x + w - 4, y + h - 2,
                    dreamingFishCore$withAlpha(accent, hovered ? 170 : 78));
            if (hovered) {
                guiGraphics.fill(x, y + 4, x + 2, y + h - 4, dreamingFishCore$withAlpha(accent, 200));
            }

            String label = dreamingFishCore$getAuxButtonDisplayText(btn);
            String fittedLabel = dreamingFishCore$fitText(label, Math.max(0, w - 12));
            guiGraphics.drawCenteredString(this.font, fittedLabel, x + w / 2, y + (h - 8) / 2,
                    hovered ? TEXT_WHITE : 0xFFD7DDE2);
        }
    }

    @Unique
    private int dreamingFishCore$getAuxButtonWidth(AbstractWidget btn) {
        return Math.max(42, Math.min(112, this.font.width(dreamingFishCore$getAuxButtonDisplayText(btn)) + 24));
    }

    @Unique
    private boolean dreamingFishCore$isIconAuxButton(AbstractWidget btn) {
        return false;
    }

    @Unique
    private boolean dreamingFishCore$isLanguageButton(AbstractWidget btn) {
        String key = dreamingFishCore$getTranslationKey(btn.getMessage());
        String plain = btn.getMessage().getString();
        return "narrator.button.language".equals(key)
                || "menu.language".equals(key)
                || plain.contains("语言")
                || plain.toLowerCase(java.util.Locale.ROOT).contains("language");
    }

    @Unique
    private boolean dreamingFishCore$isAccessibilityButton(AbstractWidget btn) {
        String key = dreamingFishCore$getTranslationKey(btn.getMessage());
        String plain = btn.getMessage().getString();
        String lower = plain.toLowerCase(java.util.Locale.ROOT);
        return "narrator.button.accessibility".equals(key)
                || "options.accessibility".equals(key)
                || plain.contains("辅助功能")
                || lower.contains("accessibility");
    }

    @Unique
    private String dreamingFishCore$getAuxButtonDisplayText(AbstractWidget btn) {
        if (dreamingFishCore$isAccessibilityButton(btn)) {
            return "辅助功能";
        }
        if (dreamingFishCore$isLanguageButton(btn)) {
            return "语言";
        }

        String text = btn.getMessage().getString();
        if (text == null || text.isBlank()) {
            return "...";
        }
        return text.replace("...", "").trim();
    }

    @Unique
    private void dreamingFishCore$drawAuxButtonIcon(GuiGraphics guiGraphics, AbstractWidget btn, int centerX,
                                                    int centerY, int accent, boolean hovered) {
        String key = dreamingFishCore$getTranslationKey(btn.getMessage());
        if ("narrator.button.language".equals(key)) {
            dreamingFishCore$drawLanguageIcon(guiGraphics, centerX, centerY, accent, hovered);
        } else if ("narrator.button.accessibility".equals(key)) {
            dreamingFishCore$drawAccessibilityIcon(guiGraphics, centerX, centerY, accent, hovered);
        } else {
            dreamingFishCore$drawInventorySlotMark(guiGraphics, centerX - 4, centerY - 4, accent, hovered);
        }
    }

    @Unique
    private void dreamingFishCore$drawLanguageIcon(GuiGraphics guiGraphics, int centerX, int centerY, int accent,
                                                   boolean hovered) {
        int color = dreamingFishCore$withAlpha(hovered ? accent : 0xFFFFFFFF, hovered ? 230 : 176);
        guiGraphics.fill(centerX - 5, centerY - 4, centerX + 5, centerY + 5, 0x66000000);
        guiGraphics.fill(centerX - 4, centerY - 3, centerX + 4, centerY - 2, color);
        guiGraphics.fill(centerX - 4, centerY + 3, centerX + 4, centerY + 4, color);
        guiGraphics.fill(centerX - 5, centerY, centerX + 5, centerY + 1, color);
        guiGraphics.fill(centerX - 1, centerY - 5, centerX, centerY + 5, color);
        guiGraphics.fill(centerX + 3, centerY - 2, centerX + 4, centerY + 4, color);
    }

    @Unique
    private void dreamingFishCore$drawAccessibilityIcon(GuiGraphics guiGraphics, int centerX, int centerY, int accent,
                                                        boolean hovered) {
        int color = dreamingFishCore$withAlpha(hovered ? accent : 0xFFFFFFFF, hovered ? 230 : 176);
        guiGraphics.fill(centerX - 1, centerY - 6, centerX + 2, centerY - 3, color);
        guiGraphics.fill(centerX - 6, centerY - 2, centerX + 7, centerY - 1, color);
        guiGraphics.fill(centerX, centerY - 2, centerX + 1, centerY + 5, color);
        guiGraphics.fill(centerX - 4, centerY + 5, centerX - 1, centerY + 6, color);
        guiGraphics.fill(centerX + 2, centerY + 5, centerX + 5, centerY + 6, color);
    }

    @Unique
    private void dreamingFishCore$drawInventorySlotMark(GuiGraphics guiGraphics, int x, int y, int accent,
                                                        boolean hovered) {
        guiGraphics.fill(x, y, x + 8, y + 8, 0xAA05070A);
        guiGraphics.fill(x, y, x + 8, y + 1, hovered ? 0x88FFFFFF : 0x44FFFFFF);
        guiGraphics.fill(x, y, x + 1, y + 8, hovered ? 0x66FFFFFF : 0x34FFFFFF);
        guiGraphics.fill(x, y + 7, x + 8, y + 8, 0x88000000);
        guiGraphics.fill(x + 7, y, x + 8, y + 8, 0x66000000);
        guiGraphics.fill(x + 3, y + 3, x + 5, y + 5,
                dreamingFishCore$withAlpha(accent, hovered ? 238 : 132));
        if (hovered) {
            guiGraphics.fill(x + 2, y + 2, x + 6, y + 3, dreamingFishCore$withAlpha(accent, 76));
            guiGraphics.fill(x + 2, y + 5, x + 6, y + 6, dreamingFishCore$withAlpha(accent, 46));
        }
    }

    @Unique
    private void dreamingFishCore$drawScaledText(GuiGraphics guiGraphics, String text, int x, int y, float scale,
                                                 int color, boolean shadow) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0F);
        int sx = Math.round(x / scale);
        int sy = Math.round(y / scale);
        if (shadow) {
            guiGraphics.drawString(this.font, text, sx + 1, sy + 1, 0xAA000000, false);
        }
        guiGraphics.drawString(this.font, text, sx, sy, color, false);
        guiGraphics.pose().popPose();
    }

    @Unique
    private String dreamingFishCore$fitText(String text, int maxWidth) {
        if (text == null || text.isEmpty() || this.font.width(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int allowedWidth = Math.max(0, maxWidth - this.font.width(ellipsis));
        StringBuilder builder = new StringBuilder();
        boolean formatting = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            builder.append(c);
            if (formatting) {
                formatting = false;
                continue;
            }
            if (c == '§') {
                formatting = true;
                continue;
            }
            if (this.font.width(builder.toString()) > allowedWidth) {
                builder.setLength(Math.max(0, builder.length() - 1));
                break;
            }
        }
        return builder + ellipsis;
    }

    // ===== 像素切角绘制辅助 =====
    @Unique
    private static void dreamingFishCore$drawPixelCutRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        if ((color >>> 24) == 0 || w <= 0 || h <= 0) {
            return;
        }

        if (w <= 2 || h <= 2) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }

        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + w, y + h - 1, color);
    }

    @Unique
    private static int dreamingFishCore$withAlpha(int color, int alpha) {
        int clampedAlpha = Math.max(0, Math.min(255, alpha));
        return (clampedAlpha << 24) | (color & 0x00FFFFFF);
    }

    // ===== 圆角绘制辅助（保留备用，主按钮不再使用） =====
    @Unique
    private static void dreamingFishCore$fillRounded(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if ((color >>> 24) == 0) return;
        int rr = Math.min(r, Math.min(w / 2, h / 2));
        int right = x + w;
        int bottom = y + h;
        g.fill(x + rr, y, right - rr, bottom, color);
        g.fill(x, y + rr, right, bottom - rr, color);
        if (rr >= 2) {
            g.fill(x + 1, y + 1, x + rr, y + rr, color);
            g.fill(right - rr, y + 1, right - 1, y + rr, color);
            g.fill(x + 1, bottom - rr, x + rr, bottom - 1, color);
            g.fill(right - rr, bottom - rr, right - 1, bottom - 1, color);
        }
        if (rr >= 3) {
            g.fill(x + 1, y + 2, x + 2, bottom - 2, color);
            g.fill(right - 2, y + 2, right - 1, bottom - 2, color);
            g.fill(x + 2, y + 1, right - 2, y + 2, color);
            g.fill(x + 2, bottom - 2, right - 2, bottom - 1, color);
        }
    }

    @Unique
    private static void dreamingFishCore$drawRoundedBorder(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if ((color >>> 24) == 0) return;
        int rr = Math.min(r, Math.min(w / 2, h / 2));
        int right = x + w;
        int bottom = y + h;
        g.fill(x + rr, y, right - rr, y + 1, color);
        g.fill(x + rr, bottom - 1, right - rr, bottom, color);
        g.fill(x, y + rr, x + 1, bottom - rr, color);
        g.fill(right - 1, y + rr, right, bottom - rr, color);
        if (rr >= 2) {
            g.fill(x + 1, y + 1, x + 2, y + 2, color);
            g.fill(right - 2, y + 1, right - 1, y + 2, color);
            g.fill(x + 1, bottom - 2, x + 2, bottom - 1, color);
            g.fill(right - 2, bottom - 2, right - 1, bottom - 1, color);
        }
        if (rr >= 3) {
            g.fill(x + 1, y + 2, x + 2, y + 3, color);
            g.fill(x + 2, y + 1, x + 3, y + 2, color);
            g.fill(right - 2, y + 2, right - 1, y + 3, color);
            g.fill(right - 3, y + 1, right - 2, y + 2, color);
            g.fill(x + 1, bottom - 3, x + 2, bottom - 2, color);
            g.fill(x + 2, bottom - 2, x + 3, bottom - 1, color);
            g.fill(right - 2, bottom - 3, right - 1, bottom - 2, color);
            g.fill(right - 3, bottom - 2, right - 2, bottom - 1, color);
        }
    }

    @Unique
    private float dreamingFishCore$scale = 1.0f;


    @Unique
    private float dreamingFishCore$getStaggeredProgress(float animProgress, float delay) {
        // 获取错开的动画进度（delay 0-1 之间的值）
        float adjusted = Math.max((animProgress - delay) / (1.0F - delay), 0);
        adjusted = Math.min(adjusted, 1.0F);
        // 使用 smooth step 让动画更柔和
        return dreamingFishCore$easeSmooth(adjusted);
    }

    @Unique
    private void dreamingFishCore$renderFooter(GuiGraphics guiGraphics, float scale) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.scale(scale, scale, 1.0f);

        int virtualW = dreamingFishCore$virtualSize.virtualWidth;
        int virtualH = dreamingFishCore$virtualSize.virtualHeight;

        int footerY = virtualH - 14;

        // ===== 右下角模组版权 =====
        float footerProgress = dreamingFishCore$getIntroProgress(0.36F);
        int modCopyrightW = this.font.width(DREAMINGFISH_COPYRIGHT_TEXT);
        int modCopyrightX = virtualW - modCopyrightW - 8;
        int modCopyrightY = footerY + (int) ((1.0F - footerProgress) * 6);
        guiGraphics.drawString(this.font, DREAMINGFISH_COPYRIGHT_TEXT,
                Math.max(8, virtualW - modCopyrightW - 8), modCopyrightY,
                dreamingFishCore$withAlpha(0xFF8E969E, (int) (255 * footerProgress)), false);

        poseStack.popPose();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;

        Minecraft mc = Minecraft.getInstance();

        // 右下角按钮坐标（与 renderBottomRightButtons 对应，使用 offset 坐标系）
        int vmx = (int) (mouseX / dreamingFishCore$scale);
        int vmy = (int) (mouseY / dreamingFishCore$scale);

        int virtualW = dreamingFishCore$virtualSize.virtualWidth;
        int virtualH = dreamingFishCore$virtualSize.virtualHeight;

        if (dreamingFishCore$donateExpanded
                && dreamingFishCore$donateCloseSize > 0
                && vmx >= dreamingFishCore$donateCloseX
                && vmx <= dreamingFishCore$donateCloseX + dreamingFishCore$donateCloseSize
                && vmy >= dreamingFishCore$donateCloseY
                && vmy <= dreamingFishCore$donateCloseY + dreamingFishCore$donateCloseSize) {
            dreamingFishCore$donateExpanded = false;
            cir.setReturnValue(true);
            return;
        }

        if (dreamingFishCore$donateButtonW > 0
                && vmx >= dreamingFishCore$donateButtonX
                && vmx <= dreamingFishCore$donateButtonX + dreamingFishCore$donateButtonW
                && vmy >= dreamingFishCore$donateButtonY
                && vmy <= dreamingFishCore$donateButtonY + dreamingFishCore$donateButtonH) {
            dreamingFishCore$donateExpanded = !dreamingFishCore$donateExpanded;
            cir.setReturnValue(true);
            return;
        }

        int hit = dreamingFishCore$detectButtonHover(vmx, vmy, virtualW, virtualH);
        if (hit >= 0 && hit < 0x80) {
            switch (hit) {
                case MAIN_MULTIPLAYER -> dreamingFishCore$openMultiplayer(mc);
                case MAIN_SINGLEPLAYER -> dreamingFishCore$openSingleplayer(mc);
                case MAIN_SETTINGS -> dreamingFishCore$openSettings(mc);
                case MAIN_EXIT -> mc.stop();
            }
            cir.setReturnValue(true);
            return;
        }

    }

    @Unique
    private void dreamingFishCore$openMultiplayer(Minecraft mc) {
        TitleScreen self = (TitleScreen) (Object) this;
        boolean skipWarning = mc.options.skipMultiplayerWarning;
        net.minecraft.client.gui.screens.Screen newScreen = skipWarning
            ? new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(self)
            : new net.minecraft.client.gui.screens.multiplayer.SafetyScreen(self);
        mc.setScreen(newScreen);
    }

    @Unique
    private void dreamingFishCore$openSettings(Minecraft mc) {
        TitleScreen self = (TitleScreen) (Object) this;
        mc.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(self, mc.options));
    }

    @Unique
    private void dreamingFishCore$openSingleplayer(Minecraft mc) {
        TitleScreen self = (TitleScreen) (Object) this;
        mc.setScreen(new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(self));
    }

    @Unique
    private void dreamingFishCore$startUpdateLogFetch() {
        if (dreamingFishCore$updateLogFetchStarted) {
            return;
        }
        dreamingFishCore$updateLogFetchStarted = true;
        new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(UPDATE_LOG_API_URL).openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Minecraft-Mod");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                if (connection.getResponseCode() != 200) {
                    return;
                }

                try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    String name = json.has("name") ? json.get("name").getAsString() : "";
                    String tag = json.has("tag_name") ? json.get("tag_name").getAsString() : "";
                    String latest = !name.isBlank() ? name : tag;
                    if (!latest.isBlank()) {
                        dreamingFishCore$updateLogPreview = "§a" + latest;
                    }
                }
            } catch (Exception ignored) {
                // Ignore network/parse errors to avoid blocking the title screen.
            }
        }, "dreamingFishCore-update-log-fetch").start();
    }

    @Unique
    private void dreamingFishCore$openUpdateLog(Minecraft mc) {
        try {
            Util.getPlatform().openUri(new URI(UPDATE_LOG_URL));
        } catch (URISyntaxException e) {
            // Ignore malformed URL to avoid crashing the title screen.
        }
    }

    @Unique
    private float dreamingFishCore$easeOutCubic(float t) {
        // Ease out cubic: 1 - (1-t)^3 - 更柔和的缓动
        return 1.0F - (float) Math.pow(1.0F - t, EASE_POWER);
    }

    @Unique
    private float dreamingFishCore$getIntroProgress(float delay) {
        if (dreamingFishCore$openTime == 0L) {
            return 0.0F;
        }

        long elapsed = System.currentTimeMillis() - dreamingFishCore$openTime;
        float base = Math.min(elapsed / (float) ANIMATION_DURATION, 1.0F);
        return dreamingFishCore$getStaggeredProgress(base, Math.min(0.82F, delay));
    }

    @Unique
    private float dreamingFishCore$easeSmooth(float t) {
        // Smooth step: 3t^2 - 2t^3 - 最柔和的缓动
        return t * t * (3.0F - 2.0F * t);
    }
}
