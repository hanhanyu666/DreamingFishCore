package com.hhy.dreamingfishcore.server.login_system.client;

import com.hhy.dreamingfishcore.client.ui.framework.anim.AnimatedFloat;
import com.hhy.dreamingfishcore.client.ui.framework.anim.Easing;
import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.Justify;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.screen.UiScreen;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Button;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.PlayerHead;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ScrollView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.TextField;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.server.login_system.PlayerLoginData;
import com.hhy.dreamingfishcore.server.server_ui_system.client.terminal.TerminalChrome;
import com.hhy.dreamingfishcore.server.server_ui_system.client.terminal.TerminalUi;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/**
 * 服务器身份验证界面：首次接入时设置密码（注册），之后输入密码登录。
 * 必须完成验证才能关闭；密码以圆点显示。
 */
@OnlyIn(Dist.CLIENT)
public class Screen_LoginUI extends UiScreen {
    private final boolean requireRegistration;
    private final AnimatedFloat reveal = AnimatedFloat.tween(0.0F, 420.0F, Easing.EMPHASIZED);
    private final Text status = Text.of("").style(TextStyle.LABEL).centered();
    private TextField passwordField;
    private TextField confirmField;
    private boolean submitting;

    public Screen_LoginUI(boolean requireRegistration) {
        super(Component.literal("登录界面"));
        this.requireRegistration = requireRegistration;
        setBackground(Background.NONE);
    }

    @Override
    protected UiNode<?> build() {
        reveal.set(1.0F);
        String mode = requireRegistration ? "REGISTER" : "LOGIN";
        int modeColor = requireRegistration ? TerminalUi.SKY : TerminalUi.GREEN;

        Box header = Ui.row(TerminalChrome.brand(), Ui.spacer(), TerminalUi.chip(mode, modeColor)).alignItems(Align.CENTER);

        Box player = Ui.row(
                PlayerHead.local().headSize(28.0F).cornerRadius(6.0F).ring(1.5F, 0x66FFC857),
                Ui.column(TerminalUi.sectionLabel("PLAYER · 身份"),
                        Text.of(Minecraft.getInstance().player != null
                                ? Minecraft.getInstance().player.getName().getString() : "Player").style(TextStyle.TITLE).singleLine())
                        .gap(2.0F).grow(1.0F)
        ).gap(Theme.Space.MD).alignItems(Align.CENTER).padding(Theme.Space.LG, Theme.Space.MD)
                .radius(Theme.Radius.LG).background(0xFF141D26).border(1.0F, 0xFF23303C);

        passwordField = TextField.of(requireRegistration ? "请设置您的密码" : "请输入您的密码")
                .icon(Icons.LOCK).password(true).maxLength(PlayerLoginData.MAX_PASSWORD_LENGTH).onSubmit(this::submit);
        passwordField.height(24.0F);
        Box fields = Ui.column(fieldLabel("PASSWORD · 访问密码"), passwordField).gap(Theme.Space.XS);
        if (requireRegistration) {
            confirmField = TextField.of("请再次确认您的密码")
                    .icon(Icons.CHECK).password(true).maxLength(PlayerLoginData.MAX_PASSWORD_LENGTH).onSubmit(this::submit);
            confirmField.height(24.0F);
            fields.add(Ui.space(Theme.Space.XS), fieldLabel("CONFIRM · 确认密码"), confirmField);
        }

        setStatus(requireRegistration ? "首次接入梦屿网络，请设置终端访问密码" : "身份缓存已找到，请输入终端访问密码",
                TerminalUi.GOLD);

        Button submit = Button.of(requireRegistration ? "写入身份凭据" : "确认身份").leadingIcon(Icons.ARROW_RIGHT)
                .large().onClick(this::submit);

        Box panel = TerminalChrome.panel().padding(Theme.Space.XL).gap(Theme.Space.LG);
        panel.add(header, player, fields, status, submit,
                Text.of("按下 Enter 确认 · 完成验证后才能进入世界").style(TextStyle.CAPTION).centered(),
                footer());
        panel.width(360.0F).maxWidth(420.0F);
        int index = 0;
        for (UiNode<?> child : panel.children()) {
            child.enter(EnterEffect.FADE_UP.delayed(120.0F + index++ * 40.0F));
        }
        panel.enter(EnterEffect.ZOOM);

        Box center = Ui.column(panel).alignItems(Align.CENTER).justify(Justify.CENTER).padding(Theme.Space.LG);
        return Ui.stack(TerminalChrome.backdrop(reveal::get), ScrollView.of(center).alignItems(Align.CENTER)
                .justify(Justify.CENTER)).alignItems(Align.STRETCH);
    }

    private static Text fieldLabel(String text) {
        return Text.of(text).style(TextStyle.CAPTION_STRONG).color(ColorRole.TEXT_MUTED).singleLine();
    }

    private static UiNode<?> footer() {
        return Ui.row(Icon.of(Icons.SHIELD, 9.0F).color(TerminalUi.STEEL),
                Text.of("DreamingFish.net · 梦屿身份服务").style(TextStyle.CAPTION).singleLine()).gap(5.0F)
                .justify(Justify.CENTER);
    }

    @Override
    protected void onOpened() {
        ui().focus(passwordField);
    }

    private void setStatus(String message, int color) {
        status.text(Component.literal(message)).color(color);
    }

    private void submit() {
        if (submitting) {
            return;
        }
        String password = passwordField.value().trim();
        if (password.isEmpty()) {
            setStatus("请输入密码！", TerminalUi.RED);
            ui().focus(passwordField);
            return;
        }
        if (!PlayerLoginData.isPasswordLengthValid(password)) {
            setStatus("密码长度必须在" + PlayerLoginData.MIN_PASSWORD_LENGTH + "到"
                    + PlayerLoginData.MAX_PASSWORD_LENGTH + "个字符之间！", TerminalUi.RED);
            return;
        }
        if (requireRegistration) {
            String confirm = confirmField.value().trim();
            if (confirm.isEmpty()) {
                setStatus("请确认密码！", TerminalUi.RED);
                ui().focus(confirmField);
                return;
            }
            if (!password.equals(confirm)) {
                setStatus("两次输入的密码不一致！", TerminalUi.RED);
                ui().focus(confirmField);
                return;
            }
            submitting = true;
            setStatus("正在注册…", TerminalUi.GREEN);
            ClientLoginHandler.sendRegisterRequest(password);
        } else {
            submitting = true;
            setStatus("正在登录…", TerminalUi.GREEN);
            ClientLoginHandler.sendLoginRequest(password);
        }
    }

    /** 服务端返回的结果；失败时允许重新提交。 */
    public void setStatusMessage(String message, boolean isError) {
        setStatus(message == null ? "" : message.replaceAll("§.", ""), isError ? TerminalUi.RED : TerminalUi.GREEN);
        if (isError) {
            submitting = false;
        }
    }

    public void switchToLoginMode() {
        // 注册成功后服务端会自动登录，不再需要切换模式
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // 不允许关闭，必须完成验证
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (ui().focusedNode() == passwordField && confirmField != null && confirmField.value().isEmpty()) {
                ui().focus(confirmField);
            } else {
                submit();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        // 不允许关闭登录界面，玩家必须登录
    }
}
