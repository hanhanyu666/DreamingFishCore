package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.Justify;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Button;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Dynamic;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Modal;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.TextField;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 终端内的输入 / 确认弹窗，直接浮在终端上，不切换界面，关闭后玩家仍停留在原页面。
 *
 * <p>弹窗只收集玩家意图，不判断对错：真正的校验全部在服务端。</p>
 */
final class TerminalPrompt {
    private static final int MAX_SUGGESTIONS = 6;

    private TerminalPrompt() {
    }

    /**
     * 文本输入弹窗。回车或点击确认时把去掉首尾空白的输入交给 {@code onConfirm}。
     *
     * @param suggestions 补全候选（例如在线玩家名），输入时按前缀筛选显示，可为空
     */
    static void text(TerminalScreen terminal, Icons icon, String title, String hint, int maxLength, String initial,
                     List<String> suggestions, Consumer<String> onConfirm) {
        TextField field = TextField.of("在此输入").maxLength(Math.max(1, maxLength)).accentColor(TerminalUi.CYAN);
        field.value(initial == null ? "" : initial);
        field.height(24.0F);
        Button confirm = Button.of("确认").filled().accentColor(TerminalUi.CYAN);
        Box body = Ui.column(field).gap(Theme.Space.SM);
        if (suggestions != null && !suggestions.isEmpty()) {
            body.add(Dynamic.of(field::value, typed -> suggestionRow(field, suggestions, typed)));
        }
        Modal modal = show(terminal, icon, TerminalUi.CYAN, title, hint, body, confirm);
        Runnable submit = () -> {
            if (modal.isClosing()) {
                return;
            }
            String value = field.value().trim();
            modal.dismiss();
            onConfirm.accept(value);
        };
        confirm.onClick(submit);
        field.onSubmit(submit);
        terminal.ui().focus(field);
    }

    /** 确认弹窗；{@code danger} 为真时确认按钮用警示配色，用于解散、踢人、转让这类不可逆操作。 */
    static void confirm(TerminalScreen terminal, String title, String hint, boolean danger, Runnable onConfirm) {
        int accent = danger ? TerminalUi.ROSE : TerminalUi.CYAN;
        Button confirm = Button.of("确认执行").filled().accentColor(accent);
        Modal modal = show(terminal, danger ? Icons.WARNING : Icons.INFO, accent, title, hint, null, confirm);
        confirm.onClick(() -> {
            if (modal.isClosing()) {
                return;
            }
            modal.dismiss();
            onConfirm.run();
        });
    }

    private static Modal show(TerminalScreen terminal, Icons icon, int accent, String title, String hint,
                              UiNode<?> body, Button confirm) {
        Button cancel = Button.of("取消").ghost();
        Box panel = new DialogPanel(accent).column().alignItems(Align.STRETCH).gap(Theme.Space.MD)
                .padding(Theme.Space.XL, Theme.Space.LG).width(300.0F);
        panel.add(
                Ui.row(TerminalUi.iconBadge(icon, accent, 20.0F),
                        Text.of(title).style(TextStyle.SUBTITLE).singleLine().grow(1.0F).shrink(1.0F))
                        .gap(Theme.Space.MD).alignItems(Align.CENTER));
        if (hint != null && !hint.isBlank()) {
            panel.add(Text.of(hint).style(TextStyle.LABEL.withLineGap(2.0F)).color(ColorRole.TEXT_SECONDARY).maxLines(4));
        }
        if (body != null) {
            panel.add(body);
        }
        panel.add(Ui.row(cancel, confirm).gap(Theme.Space.SM).justify(Justify.END).margin(0.0F, 2.0F, 0.0F, 0.0F));
        Modal modal = Modal.show(terminal.ui(), panel);
        cancel.onClick(modal::dismiss);
        return modal;
    }

    private static UiNode<?> suggestionRow(TextField field, List<String> suggestions, String typed) {
        String needle = typed == null ? "" : typed.trim().toLowerCase(Locale.ROOT);
        Box row = Ui.row().gap(Theme.Space.XS).wrap(true);
        int shown = 0;
        for (String candidate : suggestions) {
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (lower.equals(needle) || !lower.startsWith(needle)) {
                continue;
            }
            Button chip = Button.of(candidate).tonal().small().accentColor(TerminalUi.CYAN);
            chip.onClick(() -> field.value(candidate));
            row.add(chip);
            if (++shown >= MAX_SUGGESTIONS) {
                break;
            }
        }
        return shown == 0 ? Ui.space(0.0F) : row;
    }

    /** 弹窗底板：沿用终端设备外壳，四角括号取强调色。 */
    private static final class DialogPanel extends Box {
        private final int accent;

        DialogPanel(int accent) {
            this.accent = accent;
            radius(Theme.Radius.LG);
        }

        @Override
        protected void paintBackground(UiCanvas canvas) {
            TerminalChrome.paintDevice(canvas, width(), height(), Theme.Radius.LG, 0.35F);
        }

        @Override
        protected void paintOverlay(UiCanvas canvas) {
            TerminalChrome.paintCornerTicks(canvas, width(), height(), 7.0F, 4.0F, UiColor.withAlpha(accent, 0.7F));
        }
    }
}
