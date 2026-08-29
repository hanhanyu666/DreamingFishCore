package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.loading.LoadingScreenUi;
import com.hhy.dreamingfishcore.client.ui.util.LoadingTips;
import com.hhy.dreamingfishcore.client.ui.util.VirtualCoordinateHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 连接加载界面的简洁失败变体：保留背景与留白，只替换左下状态。 */
@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {
    @Unique private static final int dreamingFishCore$GENERIC_ACCENT = 0xFFD96A62;
    @Unique private static final int dreamingFishCore$BAN_ACCENT = 0xFFE15C55;
    @Unique private static final int dreamingFishCore$DEATH_ACCENT = 0xFFD54A45;
    @Unique private static final int dreamingFishCore$DETAIL_COLOR = 0xD0C5B0A9;
    @Unique private static final int dreamingFishCore$MUTED_COLOR = 0xA89D8883;
    @Unique private static final long dreamingFishCore$INTRO_MS = 360L;

    @Unique
    private final VirtualCoordinateHelper.VirtualSizeResult dreamingFishCore$virtualSize =
            new VirtualCoordinateHelper.VirtualSizeResult();
    @Unique private String dreamingFishCore$tip = "";
    @Unique private Button dreamingFishCore$returnButton;
    @Unique private long dreamingFishCore$openedAt = -1L;

    @Shadow @Final private DisconnectionDetails details;
    @Shadow @Final private Screen parent;

    protected DisconnectedScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$init(CallbackInfo ci) {
        ci.cancel();
        if (dreamingFishCore$tip.isEmpty()) {
            dreamingFishCore$tip = LoadingTips.getRandomTip();
        }
        if (dreamingFishCore$openedAt < 0L) {
            dreamingFishCore$openedAt = System.currentTimeMillis();
        }

        dreamingFishCore$returnButton = Button.builder(
                        Component.literal(dreamingFishCore$returnLabel()),
                        button -> dreamingFishCore$returnFromDisconnect())
                .bounds(0, 0, 1, 1)
                .build();
        addRenderableWidget(dreamingFishCore$returnButton);
        dreamingFishCore$updateReturnHitbox();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        VirtualCoordinateHelper.calculateVirtualSize(this, dreamingFishCore$virtualSize);
        dreamingFishCore$updateReturnHitbox();

        LoadingScreenUi.renderBackground(guiGraphics, width, height);
        float intro = dreamingFishCore$introProgress();
        guiGraphics.fillGradient(0, 0, width, height,
                dreamingFishCore$withAlpha(0xFF35070B, Math.round(68.0F * intro)),
                dreamingFishCore$withAlpha(0xFF180205, Math.round(126.0F * intro)));
        guiGraphics.fill(0, 0, width, height,
                dreamingFishCore$withAlpha(0xFF170306, Math.round(28.0F * intro)));
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(dreamingFishCore$virtualSize.uiScale,
                dreamingFishCore$virtualSize.uiScale, 1.0F);
        LoadingScreenUi.renderTip(guiGraphics, font, dreamingFishCore$tip,
                Math.min(250, dreamingFishCore$virtualSize.virtualWidth - 52));
        dreamingFishCore$renderFailureStatus(guiGraphics);
        LoadingScreenUi.renderActionHint(guiGraphics, font,
                dreamingFishCore$virtualSize.virtualWidth,
                dreamingFishCore$virtualSize.virtualHeight,
                " " + dreamingFishCore$returnLabel());
        guiGraphics.pose().popPose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            dreamingFishCore$returnFromDisconnect();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Unique
    private void dreamingFishCore$renderFailureStatus(GuiGraphics guiGraphics) {
        int virtualW = dreamingFishCore$virtualSize.virtualWidth;
        int virtualH = dreamingFishCore$virtualSize.virtualHeight;
        int x = 24;
        int titleY = virtualH - 94;
        int maxReasonWidth = Math.min(360, Math.max(140, virtualW - 76));
        float progress = dreamingFishCore$introProgress();
        int alpha = Math.round(255.0F * progress);
        int accent = dreamingFishCore$accentColor();

        guiGraphics.drawString(font, dreamingFishCore$failureTitle(), x, titleY,
                dreamingFishCore$multiplyAlpha(accent, alpha), true);

        List<String> lines = dreamingFishCore$detailLines(maxReasonWidth, 2);
        for (int i = 0; i < lines.size(); i++) {
            guiGraphics.drawString(font, lines.get(i), x, titleY + 17 + i * 12,
                    dreamingFishCore$multiplyAlpha(dreamingFishCore$DETAIL_COLOR, alpha), true);
        }

        int signalY = virtualH - 45;
        int signalWidth = Math.min(190, Math.max(104, virtualW - x - 180));
        dreamingFishCore$renderInterruptedSignal(guiGraphics, x, signalY, signalWidth, accent, alpha);
        String state = dreamingFishCore$stateLabel();
        guiGraphics.drawString(font, state, x + signalWidth + 10, signalY + 2,
                dreamingFishCore$multiplyAlpha(dreamingFishCore$MUTED_COLOR, alpha), true);
    }

    @Unique
    private void dreamingFishCore$renderInterruptedSignal(GuiGraphics guiGraphics, int x, int y,
                                                           int width, int accent, int alpha) {
        int centerY = y + 5;
        guiGraphics.fill(x, centerY, x + width, centerY + 1,
                dreamingFishCore$withAlpha(accent, Math.round(alpha * 0.20F)));

        int segmentCount = Math.max(24, width / 5);
        int visibleUntil = Math.max(5, segmentCount * 2 / 5);
        for (int i = 0; i < segmentCount; i++) {
            int segmentX = x + i * width / segmentCount;
            int nextX = x + (i + 1) * width / segmentCount;
            if (i > visibleUntil && (i % 5 != 0 || i > visibleUntil + 8)) {
                continue;
            }
            int amplitude = i <= visibleUntil
                    ? 1 + (int) Math.round(Math.abs(Math.sin(i * 0.83D)) * 5.0D)
                    : 1;
            int segmentAlpha = i <= visibleUntil ? alpha : Math.round(alpha * 0.25F);
            guiGraphics.fill(segmentX, centerY - amplitude,
                    Math.max(segmentX + 1, nextX - 1), centerY + amplitude + 1,
                    dreamingFishCore$withAlpha(accent, segmentAlpha));
        }

        int breakX = x + width * 47 / 100;
        guiGraphics.fill(breakX - 1, centerY - 7, breakX + 1, centerY + 8,
                dreamingFishCore$withAlpha(accent, Math.round(alpha * 0.68F)));
    }

    @Unique
    private void dreamingFishCore$updateReturnHitbox() {
        if (dreamingFishCore$returnButton == null) {
            return;
        }
        VirtualCoordinateHelper.calculateVirtualSize(this, dreamingFishCore$virtualSize);
        String suffix = " " + dreamingFishCore$returnLabel();
        int hintWidth = LoadingScreenUi.getActionHintWidth(font, suffix);
        int virtualX = dreamingFishCore$virtualSize.virtualWidth - 24 - hintWidth;
        int virtualY = dreamingFishCore$virtualSize.virtualHeight - 28;
        float scale = dreamingFishCore$virtualSize.uiScale;
        dreamingFishCore$returnButton.setX(Math.round(virtualX * scale));
        dreamingFishCore$returnButton.setY(Math.round(virtualY * scale));
        dreamingFishCore$returnButton.setWidth(Math.max(1, Math.round((hintWidth + 8) * scale)));
        dreamingFishCore$returnButton.setHeight(Math.max(12, Math.round(18 * scale)));
    }

    @Unique
    private void dreamingFishCore$returnFromDisconnect() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen target;
        if (parent == null || parent instanceof ConnectScreen) {
            target = new JoinMultiplayerScreen(new TitleScreen());
        } else {
            target = parent;
        }

        // A local death-ban can show this screen while the integrated server is still saving.
        // Wait for Minecraft's normal shutdown path before the player can open another save;
        // otherwise NeoForge server configs from both instances can overlap and crash PermissionAPI.
        if (minecraft.hasSingleplayerServer()) {
            minecraft.disconnect();
        }
        minecraft.setScreen(target);
    }

    @Unique
    private String dreamingFishCore$returnLabel() {
        return parent instanceof TitleScreen ? "返回标题界面" : "返回服务器列表";
    }

    @Unique
    private Component dreamingFishCore$disconnectReason() {
        return details == null ? null : details.reason();
    }

    @Unique
    private String dreamingFishCore$rawReason() {
        Component reason = dreamingFishCore$disconnectReason();
        String raw = reason == null ? "连接被远端服务器关闭" : reason.getString();
        return dreamingFishCore$plain(raw);
    }

    @Unique
    private boolean dreamingFishCore$isPermaDeathDisconnect() {
        String reason = dreamingFishCore$rawReason();
        return reason.contains("复活点数耗尽") || reason.contains("细胞分裂")
                || reason.contains("等待一名幸存者");
    }

    @Unique
    private boolean dreamingFishCore$isBanDisconnect() {
        String reason = (" " + dreamingFishCore$rawReason() + " ").toLowerCase(Locale.ROOT);
        return reason.contains("banned") || reason.contains(" ban ")
                || reason.contains("封禁") || reason.contains("禁止进入");
    }

    @Unique
    private int dreamingFishCore$accentColor() {
        if (dreamingFishCore$isPermaDeathDisconnect()) {
            return dreamingFishCore$DEATH_ACCENT;
        }
        return dreamingFishCore$isBanDisconnect()
                ? dreamingFishCore$BAN_ACCENT
                : dreamingFishCore$GENERIC_ACCENT;
    }

    @Unique
    private String dreamingFishCore$failureTitle() {
        if (dreamingFishCore$isPermaDeathDisconnect()) {
            return "生命信号耗尽";
        }
        return dreamingFishCore$isBanDisconnect() ? "访问被服务器拒绝" : "连接失败";
    }

    @Unique
    private String dreamingFishCore$stateLabel() {
        if (dreamingFishCore$isPermaDeathDisconnect()) {
            return "等待救援";
        }
        if (dreamingFishCore$isBanDisconnect()) {
            return dreamingFishCore$extractExpiration() == null ? "永久封禁" : "临时封禁";
        }
        return "信号中断";
    }

    @Unique
    private List<String> dreamingFishCore$detailLines(int maxWidth, int maxLines) {
        String detail;
        if (dreamingFishCore$isPermaDeathDisconnect()) {
            detail = "等待其他幸存者使用复活护符救援";
            String corpseLocation = dreamingFishCore$extractCorpseLocation();
            if (corpseLocation != null) {
                detail += "\n尸体位置：" + corpseLocation;
            }
        } else if (dreamingFishCore$isBanDisconnect()) {
            detail = dreamingFishCore$extractRestrictedReason();
            String expiration = dreamingFishCore$extractExpiration();
            if (expiration != null) {
                detail += "\n解封于 " + expiration;
            }
        } else {
            detail = dreamingFishCore$rawReason();
        }
        return dreamingFishCore$wrapText(detail, maxWidth, maxLines);
    }

    @Unique
    private String dreamingFishCore$extractCorpseLocation() {
        String raw = dreamingFishCore$rawReason();
        String lower = raw.toLowerCase(Locale.ROOT);
        String[] markers = {"尸体位置：", "尸体位置:", "corpse location:"};
        for (String marker : markers) {
            int index = lower.indexOf(marker.toLowerCase(Locale.ROOT));
            if (index < 0) {
                continue;
            }
            String value = dreamingFishCore$beforeAny(
                    raw.substring(index + marker.length()).trim(),
                    "\n", "your ban will be removed on", "解封时间", "解封于").trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @Unique
    private String dreamingFishCore$extractRestrictedReason() {
        String raw = dreamingFishCore$rawReason();
        String lower = raw.toLowerCase(Locale.ROOT);
        String[] markers = {"reason:", "原因：", "原因:"};
        for (String marker : markers) {
            int index = lower.indexOf(marker.toLowerCase(Locale.ROOT));
            if (index >= 0) {
                String extracted = raw.substring(index + marker.length()).trim();
                extracted = dreamingFishCore$beforeAny(extracted,
                        "\n", "your ban will be removed on", "解封时间", "解封于").trim();
                if (!extracted.isBlank()) {
                    return extracted;
                }
            }
        }

        for (String line : raw.split("\\R")) {
            String clean = line.trim();
            String lineLower = clean.toLowerCase(Locale.ROOT);
            if (!clean.isBlank() && !lineLower.contains("banned") && !clean.contains("封禁")
                    && !lineLower.contains("ban will be removed") && !clean.contains("解封")) {
                return clean;
            }
        }
        return raw;
    }

    @Unique
    private String dreamingFishCore$extractExpiration() {
        String raw = dreamingFishCore$rawReason();
        String lower = raw.toLowerCase(Locale.ROOT);
        String[] markers = {"your ban will be removed on", "解封时间：", "解封时间:", "解封于"};
        for (String marker : markers) {
            int index = lower.indexOf(marker.toLowerCase(Locale.ROOT));
            if (index >= 0) {
                String tail = raw.substring(index + marker.length()).trim();
                String value = dreamingFishCore$beforeAny(tail, "\n").trim();
                return value.isBlank() ? null : value;
            }
        }
        return null;
    }

    @Unique
    private String dreamingFishCore$beforeAny(String source, String... delimiters) {
        int end = source.length();
        String lower = source.toLowerCase(Locale.ROOT);
        for (String delimiter : delimiters) {
            int index = lower.indexOf(delimiter.toLowerCase(Locale.ROOT));
            if (index >= 0) {
                end = Math.min(end, index);
            }
        }
        return source.substring(0, end);
    }

    @Unique
    private List<String> dreamingFishCore$wrapText(String source, int maxWidth, int maxLines) {
        List<String> result = new ArrayList<>();
        boolean truncated = false;
        for (String paragraph : dreamingFishCore$plain(source).split("\\R", -1)) {
            String remaining = paragraph.trim();
            while (!remaining.isEmpty()) {
                if (result.size() >= maxLines) {
                    truncated = true;
                    break;
                }
                String fitted = font.plainSubstrByWidth(remaining, Math.max(24, maxWidth));
                int length = Math.max(1, fitted.length());
                if (length < remaining.length()) {
                    int space = fitted.lastIndexOf(' ');
                    if (space > fitted.length() / 2) {
                        length = space;
                    }
                }
                result.add(remaining.substring(0, Math.min(length, remaining.length())).trim());
                remaining = remaining.substring(Math.min(length, remaining.length())).trim();
            }
            if (result.size() >= maxLines) {
                break;
            }
        }

        if (result.isEmpty()) {
            result.add("服务器未提供详细原因");
        }
        if (truncated) {
            int last = result.size() - 1;
            String ellipsis = "...";
            result.set(last, font.plainSubstrByWidth(result.get(last),
                    Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis);
        }
        return result;
    }

    @Unique
    private String dreamingFishCore$plain(String text) {
        String stripped = ChatFormatting.stripFormatting(text == null ? "" : text);
        return stripped == null ? "" : stripped.replace('\r', '\n')
                .replaceAll("\\n{2,}", "\n").trim();
    }

    @Unique
    private float dreamingFishCore$easeOutCubic(float value) {
        float t = Math.max(0.0F, Math.min(1.0F, value));
        float inverse = 1.0F - t;
        return 1.0F - inverse * inverse * inverse;
    }

    @Unique
    private float dreamingFishCore$introProgress() {
        return dreamingFishCore$easeOutCubic(
                (System.currentTimeMillis() - dreamingFishCore$openedAt)
                        / (float) dreamingFishCore$INTRO_MS);
    }

    @Unique
    private int dreamingFishCore$withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    @Unique
    private int dreamingFishCore$multiplyAlpha(int color, int alpha) {
        return dreamingFishCore$withAlpha(color,
                (color >>> 24) * Math.max(0, Math.min(255, alpha)) / 255);
    }
}
