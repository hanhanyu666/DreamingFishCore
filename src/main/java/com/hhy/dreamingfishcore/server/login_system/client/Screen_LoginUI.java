package com.hhy.dreamingfishcore.server.login_system.client;

import com.hhy.dreamingfishcore.client.ui.components.UiPanelRenderer;
import com.hhy.dreamingfishcore.client.ui.util.VirtualCoordinateHelper;
import com.hhy.dreamingfishcore.server.login_system.PlayerLoginData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class Screen_LoginUI extends Screen {
    private static final int TERMINAL_BACKGROUND = 0xD9050A10;
    private static final int TERMINAL_SHELL = 0xEE0B1118;
    private static final int TERMINAL_HEADER = 0xF0131B24;
    private static final int TERMINAL_CONTENT = 0xF01B2530;
    private static final int TERMINAL_CARD = 0xFF24313E;
    private static final int TERMINAL_CARD_HOVER = 0xFF2C3B4A;
    private static final int TERMINAL_BORDER = 0xFF7AA8C7;
    private static final int TERMINAL_BORDER_DARK = 0xFF344555;
    private static final int TERMINAL_TEXT = 0xFFE8EDF2;
    private static final int TERMINAL_MUTED_TEXT = 0xFFA7B2BE;
    private static final int TERMINAL_GREEN = 0xFF50D890;
    private static final int TERMINAL_RED = 0xFFFF6677;
    private static final int TERMINAL_GOLD = 0xFFFFC857;
    private static final int LOGIN_BOX_HEIGHT = 212;
    private static final int REGISTER_BOX_HEIGHT = 260;
    private static final int FIELD_TOP_OFFSET = 108;
    private static final int FIELD_PANEL_HEIGHT = 28;
    private static final int CONFIRM_FIELD_GAP = 42;

    private EditBox passwordField;
    private EditBox confirmPasswordField;
    private Component statusMessage = Component.literal("");
    private int messageColor = TERMINAL_RED;
    private boolean isSubmitting = false;  // 防止重复提交

    private final boolean requireRegistration;
    private final VirtualCoordinateHelper.VirtualSizeResult virtualSize = new VirtualCoordinateHelper.VirtualSizeResult();

    public Screen_LoginUI(boolean requireRegistration) {
        super(Component.literal("登录界面"));
        this.requireRegistration = requireRegistration;
    }

    @Override
    protected void init() {
        super.init();
        updateVirtualSize();

        int centerX = virtualSize.virtualWidth / 2;
        int centerY = virtualSize.virtualHeight / 2;

        int boxWidth = getBoxWidth();
        int boxHeight = getBoxHeight();
        int boxX = centerX - boxWidth / 2;
        int boxY = centerY - boxHeight / 2;

        int fieldWidth = Math.min(360, boxWidth - 64);
        int fieldHeight = 18;
        int fieldX = centerX - fieldWidth / 2;
        int startY = boxY + FIELD_TOP_OFFSET;

        // 密码输入框
        this.passwordField = new EditBox(this.font, fieldX + 7, startY + 5, fieldWidth - 14, fieldHeight, Component.literal("密码"));
        this.passwordField.setHint(Component.literal(requireRegistration ? "请设置您的密码" : "请输入您的密码"));
        this.passwordField.setMaxLength(PlayerLoginData.MAX_PASSWORD_LENGTH);
        this.passwordField.setBordered(false);
        this.passwordField.setTextColor(TERMINAL_TEXT);
        this.passwordField.setTextColorUneditable(TERMINAL_MUTED_TEXT);
        this.passwordField.setResponder(value -> {
            if (value.length() > 0 && value.endsWith("\n")) {
                // 回车键
                passwordField.setValue(value.substring(0, value.length() - 1));
                onSubmit();
            }
        });
        this.addRenderableWidget(this.passwordField);

        // 确认密码输入框（仅在注册阶段显示）
        this.confirmPasswordField = new EditBox(this.font, fieldX + 7, startY + CONFIRM_FIELD_GAP + 5,
                fieldWidth - 14, fieldHeight, Component.literal("确认密码"));
        this.confirmPasswordField.setHint(Component.literal("请再次确认您的密码"));
        this.confirmPasswordField.setMaxLength(PlayerLoginData.MAX_PASSWORD_LENGTH);
        this.confirmPasswordField.setBordered(false);
        this.confirmPasswordField.setTextColor(TERMINAL_TEXT);
        this.confirmPasswordField.setTextColorUneditable(TERMINAL_MUTED_TEXT);
        this.confirmPasswordField.setResponder(value -> {
            if (value.length() > 0 && value.endsWith("\n")) {
                // 回车键
                confirmPasswordField.setValue(value.substring(0, value.length() - 1));
                onSubmit();
            }
        });
        this.confirmPasswordField.setVisible(requireRegistration);
        this.addRenderableWidget(this.confirmPasswordField);
        this.setInitialFocus(this.passwordField);

        updatePromptMessage();
    }

    private void updateVirtualSize() {
        VirtualCoordinateHelper.calculateDownscaledVirtualSize(this, virtualSize);
    }

    private int getBoxWidth() {
        int preferred = Math.max(340, Math.round(virtualSize.virtualWidth * 0.64f));
        int available = Math.max(280, virtualSize.virtualWidth - 40);
        return Math.min(460, Math.min(available, preferred));
    }

    private int getBoxHeight() {
        return requireRegistration ? REGISTER_BOX_HEIGHT : LOGIN_BOX_HEIGHT;
    }

    private void updateFieldLayout() {
        int centerX = virtualSize.virtualWidth / 2;
        int centerY = virtualSize.virtualHeight / 2;
        int boxWidth = getBoxWidth();
        int boxHeight = getBoxHeight();
        int boxY = centerY - boxHeight / 2;
        int fieldWidth = Math.min(360, boxWidth - 64);
        int fieldX = centerX - fieldWidth / 2;
        int startY = boxY + FIELD_TOP_OFFSET;

        passwordField.setWidth(fieldWidth - 14);
        passwordField.setX(fieldX + 7);
        passwordField.setY(startY + 5);
        confirmPasswordField.setWidth(fieldWidth - 14);
        confirmPasswordField.setX(fieldX + 7);
        confirmPasswordField.setY(startY + CONFIRM_FIELD_GAP + 5);
    }

    private void updatePromptMessage() {
        if (requireRegistration) {
            statusMessage = Component.literal("首次接入梦屿网络，请设置终端访问密码");
            messageColor = TERMINAL_GOLD;
        } else {
            statusMessage = Component.literal("身份缓存已找到，请输入终端访问密码");
            messageColor = TERMINAL_GOLD;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateVirtualSize();
        updateFieldLayout();

        int centerX = virtualSize.virtualWidth / 2;
        int centerY = virtualSize.virtualHeight / 2;

        int boxWidth = getBoxWidth();
        int boxHeight = getBoxHeight();
        int boxX = centerX - boxWidth / 2;
        int boxY = centerY - boxHeight / 2;
        int headerHeight = 30;

        guiGraphics.fill(RenderType.gui(), 0, 0, this.width, this.height, TERMINAL_BACKGROUND);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(virtualSize.uiScale, virtualSize.uiScale, 1.0f);

        drawSoftRect(guiGraphics, boxX + 5, boxY + 6, boxWidth, boxHeight, 3, 0x66000000, 0x00000000);
        drawSoftRect(guiGraphics, boxX, boxY, boxWidth, boxHeight, 3, TERMINAL_SHELL, 0x66526372);
        drawSoftRect(guiGraphics, boxX + 6, boxY + 6, boxWidth - 12, headerHeight, 2, TERMINAL_HEADER, 0x224A5A68);
        drawSoftRect(guiGraphics, boxX + 6, boxY + headerHeight + 6,
                boxWidth - 12, boxHeight - headerHeight - 12, 2, TERMINAL_CONTENT, 0x22384755);

        drawBrandTitle(guiGraphics, boxX + 16, boxY + 15);

        String modeText = requireRegistration ? "REGISTER" : "LOGIN";
        int modeWidth = minecraft.font.width(modeText) + 18;
        drawSoftRect(guiGraphics, boxX + boxWidth - modeWidth - 16, boxY + 11, modeWidth, 16, 2,
                requireRegistration ? 0x3339A6FF : 0x3350D890, requireRegistration ? 0xFF4FC3F7 : TERMINAL_GREEN);
        guiGraphics.drawString(minecraft.font, modeText, boxX + boxWidth - modeWidth - 7, boxY + 15,
                requireRegistration ? 0xFF4FC3F7 : TERMINAL_GREEN, false);

        int playerCardX = boxX + 20;
        int playerCardY = boxY + 50;
        int playerCardHeight = 42;
        drawSoftRect(guiGraphics, playerCardX, playerCardY, boxWidth - 40, playerCardHeight,
                2, TERMINAL_CARD, TERMINAL_BORDER_DARK);
        UiPanelRenderer.smoothRoundedRect(guiGraphics, playerCardX + 2, playerCardY + 5,
                3, playerCardHeight - 10, 1, TERMINAL_GOLD, 0);
        guiGraphics.drawString(minecraft.font, "PLAYER", playerCardX + 16, playerCardY + 8,
                TERMINAL_MUTED_TEXT, false);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(1.20f, 1.20f, 1.0f);
        String playerName = minecraft.player != null ? minecraft.player.getName().getString() : "Player";
        guiGraphics.drawString(minecraft.font, playerName,
                (int) ((playerCardX + 16) / 1.20f), (int) ((playerCardY + 23) / 1.20f),
                TERMINAL_TEXT, false);
        guiGraphics.pose().popPose();

        int fieldWidth = Math.min(360, boxWidth - 64);
        int fieldX = centerX - fieldWidth / 2;
        int fieldY = boxY + FIELD_TOP_OFFSET;

        drawInputLabel(guiGraphics, fieldX, fieldY, "PASSWORD");
        if (!confirmPasswordField.isVisible()) {
            renderInputBackground(guiGraphics, fieldX, fieldY, fieldWidth, FIELD_PANEL_HEIGHT, passwordField.isFocused());
            guiGraphics.drawCenteredString(minecraft.font, statusMessage, centerX, fieldY + 38, messageColor);
            guiGraphics.drawCenteredString(minecraft.font, "按下 Enter 确认身份", centerX, fieldY + 55, TERMINAL_MUTED_TEXT);
        } else {
            renderInputBackground(guiGraphics, fieldX, fieldY, fieldWidth, FIELD_PANEL_HEIGHT, passwordField.isFocused());
            int confirmY = fieldY + CONFIRM_FIELD_GAP;
            drawInputLabel(guiGraphics, fieldX, confirmY, "CONFIRM");
            renderInputBackground(guiGraphics, fieldX, confirmY, fieldWidth, FIELD_PANEL_HEIGHT, confirmPasswordField.isFocused());
            guiGraphics.drawCenteredString(minecraft.font, statusMessage, centerX, confirmY + 38, messageColor);
            guiGraphics.drawCenteredString(minecraft.font, "设置完成后按下 Enter 写入身份凭据", centerX, confirmY + 55, TERMINAL_MUTED_TEXT);
        }

        drawTerminalFooter(guiGraphics, centerX, boxY + boxHeight - 18);

        super.render(guiGraphics, toVirtual(mouseX), toVirtual(mouseY), partialTick);
        guiGraphics.pose().popPose();
    }

    private int toVirtual(double coordinate) {
        return (int) (coordinate / virtualSize.uiScale);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        updateVirtualSize();
        updateFieldLayout();
        return super.mouseClicked(mouseX / virtualSize.uiScale, mouseY / virtualSize.uiScale, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        updateVirtualSize();
        updateFieldLayout();
        return super.mouseReleased(mouseX / virtualSize.uiScale, mouseY / virtualSize.uiScale, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        updateVirtualSize();
        updateFieldLayout();
        return super.mouseDragged(
                mouseX / virtualSize.uiScale,
                mouseY / virtualSize.uiScale,
                button,
                dragX / virtualSize.uiScale,
                dragY / virtualSize.uiScale
        );
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String password = passwordField == null ? "" : passwordField.getValue();
        String confirmation = confirmPasswordField == null ? "" : confirmPasswordField.getValue();
        boolean confirmationFocused = confirmPasswordField != null && confirmPasswordField.isFocused();
        Component previousStatus = statusMessage;
        int previousMessageColor = messageColor;

        super.resize(minecraft, width, height);

        passwordField.setValue(password);
        confirmPasswordField.setValue(confirmation);
        statusMessage = previousStatus;
        messageColor = previousMessageColor;
        if (confirmationFocused && confirmPasswordField.isVisible()) {
            this.setFocused(confirmPasswordField);
        }
    }

    private void renderInputBackground(GuiGraphics guiGraphics, int x, int y, int width, int height, boolean focused) {
        int borderColor = focused ? TERMINAL_BORDER : TERMINAL_BORDER_DARK;
        int bgColor = focused ? TERMINAL_CARD_HOVER : TERMINAL_CARD;
        drawSoftRect(guiGraphics, x, y, width, height, 2, bgColor, borderColor);
        UiPanelRenderer.smoothRoundedRect(guiGraphics, x + 2, y + 5,
                2, height - 10, 1, borderColor, 0);
    }

    private void drawInputLabel(GuiGraphics guiGraphics, int x, int y, String label) {
        guiGraphics.drawString(minecraft.font, label, x + 6, y - 12, TERMINAL_MUTED_TEXT, false);
    }

    private void drawBrandTitle(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.drawString(minecraft.font, "Dreaming", x, y, 0xFFB58BFF, false);
        int fishX = x + minecraft.font.width("Dreaming");
        guiGraphics.drawString(minecraft.font, "Fish", fishX, y, 0xFF4FC3F7, false);
        guiGraphics.drawString(minecraft.font, " Terminal", fishX + minecraft.font.width("Fish"), y, TERMINAL_GOLD, false);
    }

    private void drawTerminalFooter(GuiGraphics guiGraphics, int centerX, int y) {
        String left = "Dreaming";
        String mid = "Fish";
        String right = ".net";
        int totalWidth = minecraft.font.width(left + mid + right);
        int x = centerX - totalWidth / 2;
        guiGraphics.drawString(minecraft.font, left, x, y, 0xFFB58BFF, false);
        x += minecraft.font.width(left);
        guiGraphics.drawString(minecraft.font, mid, x, y, 0xFF4FC3F7, false);
        x += minecraft.font.width(mid);
        guiGraphics.drawString(minecraft.font, right, x, y, TERMINAL_GOLD, false);
    }

    private void drawSoftRect(GuiGraphics guiGraphics, int x, int y, int width, int height, int radius, int fillColor, int borderColor) {
        UiPanelRenderer.smoothRoundedRect(guiGraphics, x, y, width, height,
                Math.max(0, radius * 2), fillColor, borderColor);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC键
            // 不允许关闭，提示玩家必须登录
            return true;
        }
        if (keyCode == 257 || keyCode == 335) { // Enter键
            onSubmit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (passwordField.isFocused()) {
            return passwordField.charTyped(codePoint, modifiers);
        }
        if (confirmPasswordField.isFocused() && confirmPasswordField.isVisible()) {
            return confirmPasswordField.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        // 不允许关闭登录界面，玩家必须登录
    }

    private void onSubmit() {
        // 防止重复提交
        if (isSubmitting) {
            return;
        }

        String password = passwordField.getValue().trim();

        if (password.isEmpty()) {
            statusMessage = Component.literal("§c请输入密码！");
            messageColor = TERMINAL_RED;
            return;
        }

        if (!PlayerLoginData.isPasswordLengthValid(password)) {
            statusMessage = Component.literal("§c密码长度必须在"
                    + PlayerLoginData.MIN_PASSWORD_LENGTH + "到"
                    + PlayerLoginData.MAX_PASSWORD_LENGTH + "个字符之间！");
            messageColor = TERMINAL_RED;
            return;
        }

        if (requireRegistration) {
            // 注册模式：检查两个密码框
            String confirmPassword = confirmPasswordField.getValue().trim();

            if (confirmPassword.isEmpty()) {
                statusMessage = Component.literal("§c请确认密码！");
                messageColor = TERMINAL_RED;
                return;
            }

            if (!password.equals(confirmPassword)) {
                statusMessage = Component.literal("§c两次输入的密码不一致！");
                messageColor = TERMINAL_RED;
                return;
            }

            // 密码一致，执行注册
            isSubmitting = true;
            statusMessage = Component.literal("§a正在注册...");
            messageColor = TERMINAL_GREEN;
            ClientLoginHandler.sendRegisterRequest(password);
        } else {
            // 已注册，直接登录
            isSubmitting = true;
            statusMessage = Component.literal("§a正在登录...");
            messageColor = TERMINAL_GREEN;
            ClientLoginHandler.sendLoginRequest(password);
        }
    }

    public void setStatusMessage(String message, boolean isError) {
        statusMessage = Component.literal(message);
        messageColor = isError ? TERMINAL_RED : TERMINAL_GREEN;
        if (isError) {
            isSubmitting = false;  // 登录/注册失败，允许重新提交
        }
    }

    public void switchToLoginMode() {
        // 不再需要，因为注册后会自动登录
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
