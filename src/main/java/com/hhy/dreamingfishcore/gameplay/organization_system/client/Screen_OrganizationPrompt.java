package com.hhy.dreamingfishcore.gameplay.organization_system.client;

import com.hhy.dreamingfishcore.client.ui.components.HighLevelTextField;
import com.hhy.dreamingfishcore.client.ui.components.UiPanelRenderer;
import com.hhy.dreamingfishcore.client.ui.util.VirtualCoordinateHelper;
import com.hhy.dreamingfishcore.server.server_ui_system.client.serverscreen.ServerScreenUI;
import com.hhy.dreamingfishcore.server.server_ui_system.client.serverscreen.ServerScreenUI_Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 组织系统的输入 / 确认弹窗。
 *
 * <p>终端本体是全部自绘的覆盖层，没有原版控件；而建会、改名、写公告、邀请都需要真实文本输入，
 * 所以这里按项目既有的「子屏幕」范式（见 {@code Screen_NoticeDetail}）单独开一个 {@link Screen}，
 * 内部用一个 {@link HighLevelTextField}（原版 {@code EditBox} 子类，带补全）承载键盘输入。
 * 关闭时重建终端并回到组织页，玩家的观感是「弹窗消失后我还站在原地」。</p>
 *
 * <p>两种形态：
 * <ul>
 *   <li>{@link Mode#TEXT} —— 输入一段文本后确认；</li>
 *   <li>{@link Mode#CONFIRM} —— 只有确认 / 取消，用于解散、踢人、转让这类不可逆操作。</li>
 * </ul>
 * 弹窗只负责收集玩家意图，**不判断对错**：真正的校验仍然全部在服务端。</p>
 */
public final class Screen_OrganizationPrompt extends Screen {

    /** 弹窗形态。 */
    public enum Mode {
        /** 需要输入文本。 */
        TEXT,
        /** 只需要一次确认。 */
        CONFIRM
    }

    private static final int BG = 0xF00B1118;
    private static final int PANEL_BORDER = 0xFF344555;
    private static final int PANEL_INNER = 0xF01B2530;
    private static final int TITLE_COLOR = 0xFFE8EDF2;
    private static final int HINT_COLOR = 0xFFA7B2BE;
    private static final int ACCENT = 0xFF8CCEFF;

    private static final int BTN_BG = 0xFF24313E;
    private static final int BTN_BG_HOVER = 0xFF2F4152;
    private static final int BTN_BORDER = 0xFF344555;
    private static final int BTN_PRIMARY_BG = 0xFF1F4A5C;
    private static final int BTN_PRIMARY_BORDER = 0xFF4FA8C7;
    private static final int BTN_DANGER_BG = 0xFF4A2A31;
    private static final int BTN_DANGER_BORDER = 0xFF9C4A57;

    private static final int PADDING = 12;
    private static final int BOX_WIDTH = 320;
    private static final int BTN_HEIGHT = 20;
    private static final int FIELD_HEIGHT = 18;

    private final Mode mode;
    private final String titleText;
    private final String hintText;
    private final String initialValue;
    private final int maxLength;
    private final boolean danger;
    private final List<String> suggestions;
    private final Consumer<String> onConfirm;

    private HighLevelTextField field;

    private int boxX;
    private int boxY;
    private int boxWidth;
    private int boxHeight;

    /** 弹窗内部各行的绝对 Y 坐标，全部由 {@link #updateLayout()} 一次算好。 */
    private int titleY;
    private int dividerY;
    private int hintY;
    private int fieldTop;
    private List<String> hintLines = List.of();

    private int confirmX1;
    private int confirmY1;
    private int confirmX2;
    private int confirmY2;
    private int cancelX1;
    private int cancelY1;
    private int cancelX2;
    private int cancelY2;

    private final VirtualCoordinateHelper.VirtualSizeResult virtualSize =
            new VirtualCoordinateHelper.VirtualSizeResult();

    private Screen_OrganizationPrompt(Mode mode, String titleText, String hintText, String initialValue,
                                      int maxLength, boolean danger, List<String> suggestions,
                                      Consumer<String> onConfirm) {
        super(Component.literal(titleText == null ? "组织" : titleText));
        this.mode = mode;
        this.titleText = titleText == null ? "" : titleText;
        this.hintText = hintText == null ? "" : hintText;
        this.initialValue = initialValue == null ? "" : initialValue;
        this.maxLength = Math.max(1, maxLength);
        this.danger = danger;
        this.suggestions = suggestions == null ? List.of() : suggestions;
        this.onConfirm = onConfirm;
    }

    /** 打开一个文本输入弹窗。确认时把输入内容交给 {@code onConfirm}。 */
    public static void openText(String title, String hint, int maxLength, String initialValue,
                                List<String> suggestions, Consumer<String> onConfirm) {
        ServerScreenUI.openSubScreen(new Screen_OrganizationPrompt(
                Mode.TEXT, title, hint, initialValue, maxLength, false, suggestions, onConfirm));
    }

    /** 打开一个确认弹窗；{@code danger} 为真时确认按钮用警示配色。 */
    public static void openConfirm(String title, String hint, boolean danger, Consumer<String> onConfirm) {
        ServerScreenUI.openSubScreen(new Screen_OrganizationPrompt(
                Mode.CONFIRM, title, hint, "", 1, danger, List.of(), onConfirm));
    }

    @Override
    protected void init() {
        super.init();
        if (mode == Mode.TEXT) {
            // 补全列表在创建时就要给定，否则首帧就会拿到空表。
            field = new HighLevelTextField(this.font, 0, 0, 100, FIELD_HEIGHT);
            field.setMaxLength(maxLength);
            field.setSuggestions(suggestions);
            field.setValue(initialValue);
            field.setHint(Component.literal("在此输入"));
            // 原版 EditBox 的背景是纯黑，深色文字会看不清。
            field.setTextColor(0xFFE8EDF2);
            field.setTextColorUneditable(0xFFA7B2BE);
            addRenderableWidget(field);
            setFocused(field);
            field.setFocused(true);
        }
    }

    private void updateLayout() {
        VirtualCoordinateHelper.calculateDownscaledVirtualSize(this, virtualSize);
        int availableWidth = Math.max(160, virtualSize.virtualWidth - 40);
        boxWidth = Math.min(BOX_WIDTH, availableWidth);

        // 先在"相对弹窗左上角"的坐标里把每行排好，算出总高，才能反推居中位置。
        int innerWidth = boxWidth - PADDING * 2;
        hintLines = wrap(hintText, innerWidth, mode == Mode.TEXT ? 2 : 3);

        int relativeTitleY = PADDING;
        int relativeDividerY = relativeTitleY + 13;
        int relativeHintY = relativeDividerY + 5;
        int hintBlockHeight = hintLines.size() * (this.font.lineHeight + 2);
        int relativeFieldTop = relativeHintY + hintBlockHeight + 4;
        int relativeButtonY = mode == Mode.TEXT
                ? relativeFieldTop + FIELD_HEIGHT + 14
                : relativeHintY + hintBlockHeight + 12;

        boxHeight = relativeButtonY + BTN_HEIGHT + PADDING;
        boxX = (virtualSize.virtualWidth - boxWidth) / 2;
        boxY = (virtualSize.virtualHeight - boxHeight) / 2;

        titleY = boxY + relativeTitleY;
        dividerY = boxY + relativeDividerY;
        hintY = boxY + relativeHintY;
        fieldTop = boxY + relativeFieldTop;

        int buttonGap = 8;
        int buttonWidth = (innerWidth - buttonGap) / 2;
        int buttonY = boxY + relativeButtonY;
        cancelX1 = boxX + PADDING;
        cancelY1 = buttonY;
        cancelX2 = cancelX1 + buttonWidth;
        cancelY2 = buttonY + BTN_HEIGHT;
        confirmX1 = cancelX2 + buttonGap;
        confirmY1 = buttonY;
        confirmX2 = confirmX1 + buttonWidth;
        confirmY2 = buttonY + BTN_HEIGHT;

        if (field != null) {
            field.setX((int) ((boxX + PADDING) * virtualSize.uiScale));
            field.setY((int) (fieldTop * virtualSize.uiScale));
            field.setWidth((int) (innerWidth * virtualSize.uiScale));
            field.setHeight((int) (FIELD_HEIGHT * virtualSize.uiScale));
        }
    }

    /** 提示文字按宽度折行，最多 {@code maxLines} 行；末行放不下时补省略号。 */
    private List<String> wrap(String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        if (maxWidth <= 0) {
            lines.add(text);
            return lines;
        }
        String remaining = text;
        while (!remaining.isEmpty() && lines.size() < maxLines) {
            String fitted = this.font.plainSubstrByWidth(remaining, maxWidth);
            if (fitted.isEmpty()) {
                fitted = remaining.substring(0, 1);
            }
            if (fitted.length() == remaining.length()) {
                lines.add(remaining);
                remaining = "";
                break;
            }
            lines.add(fitted);
            remaining = remaining.substring(fitted.length());
        }
        if (!remaining.isEmpty() && !lines.isEmpty()) {
            int lastIndex = lines.size() - 1;
            String last = lines.get(lastIndex);
            int ellipsis = this.font.width("...");
            lines.set(lastIndex, this.font.plainSubstrByWidth(last, Math.max(1, maxWidth - ellipsis)) + "...");
        }
        return lines;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateLayout();
        guiGraphics.fill(0, 0, this.width, this.height, BG);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(virtualSize.uiScale, virtualSize.uiScale, 1.0f);
        int virtualMouseX = (int) (mouseX / virtualSize.uiScale);
        int virtualMouseY = (int) (mouseY / virtualSize.uiScale);

        UiPanelRenderer.smoothRoundedRect(guiGraphics, boxX - 2, boxY - 2,
                boxWidth + 4, boxHeight + 4, 6, PANEL_BORDER, 0);
        UiPanelRenderer.smoothRoundedRect(guiGraphics, boxX, boxY,
                boxWidth, boxHeight, 4, PANEL_INNER, 0);
        guiGraphics.fill(boxX + PADDING, dividerY, boxX + boxWidth - PADDING, dividerY + 1, ACCENT);

        drawText(guiGraphics, titleText, boxX + PADDING, titleY, TITLE_COLOR);
        int hintLineY = hintY;
        for (String line : hintLines) {
            drawText(guiGraphics, line, boxX + PADDING, hintLineY, HINT_COLOR);
            hintLineY += this.font.lineHeight + 2;
        }

        if (mode == Mode.TEXT) {
            // 输入框本体由控件自己绘制（已按 uiScale 放到屏幕坐标），这里只画承载它的底板。
            UiPanelRenderer.smoothRoundedRect(guiGraphics, boxX + PADDING - 2, fieldTop - 2,
                    boxWidth - PADDING * 2 + 4, FIELD_HEIGHT + 4, 2, 0xFF101820, PANEL_BORDER);
        }

        drawButton(guiGraphics, virtualMouseX, virtualMouseY,
                cancelX1, cancelY1, cancelX2 - cancelX1, BTN_HEIGHT, "取消", false, false);
        drawButton(guiGraphics, virtualMouseX, virtualMouseY,
                confirmX1, confirmY1, confirmX2 - confirmX1, BTN_HEIGHT,
                mode == Mode.TEXT ? "确认" : "确认执行", true, danger);

        guiGraphics.pose().popPose();

        // 输入框要在自绘内容之上、且在屏幕坐标系里绘制。
        // 这里手动渲染而不是调用 super.render：super.render 会先铺一层原版压暗背景，
        // 把刚画好的面板再压暗一遍（项目既有的弹窗子界面同样不调它）。
        if (field != null) {
            field.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private void drawButton(GuiGraphics guiGraphics, int mouseX, int mouseY,
                            int x, int y, int width, int height, String label,
                            boolean primary, boolean dangerStyle) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        int fill = primary
                ? (dangerStyle ? BTN_DANGER_BG : BTN_PRIMARY_BG)
                : (hovered ? BTN_BG_HOVER : BTN_BG);
        int border = primary
                ? (dangerStyle ? BTN_DANGER_BORDER : BTN_PRIMARY_BORDER)
                : BTN_BORDER;
        if (hovered && !primary) {
            fill = BTN_BG_HOVER;
        }
        UiPanelRenderer.smoothRoundedRect(guiGraphics, x, y, width, height, 3, fill, border);
        int textWidth = this.font.width(label);
        int color = primary ? (dangerStyle ? 0xFFFFD5DB : 0xFFDDF3FF)
                : (hovered ? TITLE_COLOR : HINT_COLOR);
        guiGraphics.drawString(this.font, label, x + (width - textWidth) / 2,
                y + (height - this.font.lineHeight) / 2, color, false);
    }

    private void drawText(GuiGraphics guiGraphics, String text, int x, int y, int color) {
        guiGraphics.drawString(this.font, text, x, y, color, false);
    }

    private void confirm() {
        String value = mode == Mode.TEXT
                ? (field == null ? initialValue : field.getValue()).trim()
                : "";
        Consumer<String> callback = onConfirm;
        returnToTerminal();
        if (callback != null) {
            callback.accept(value);
        }
    }

    private void returnToTerminal() {
        ServerScreenUI.onSubScreenClosed();
        ServerScreenUI.setReturningFromSubScreen(true);
        ServerScreenUI_Screen terminal = new ServerScreenUI_Screen();
        terminal.setSelectedPageIndex(ServerScreenUI_Screen.ORGANIZATION_PAGE_INDEX);
        Minecraft.getInstance().setScreen(terminal);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            returnToTerminal();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        updateLayout();
        double virtualX = mouseX / virtualSize.uiScale;
        double virtualY = mouseY / virtualSize.uiScale;

        if (virtualX >= confirmX1 && virtualX <= confirmX2
                && virtualY >= confirmY1 && virtualY <= confirmY2) {
            confirm();
            return true;
        }
        if (virtualX >= cancelX1 && virtualX <= cancelX2
                && virtualY >= cancelY1 && virtualY <= cancelY2) {
            returnToTerminal();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        // 直接关闭（例如玩家按了菜单键）也要回到终端组织页，而不是掉回世界。
        returnToTerminal();
    }
}
