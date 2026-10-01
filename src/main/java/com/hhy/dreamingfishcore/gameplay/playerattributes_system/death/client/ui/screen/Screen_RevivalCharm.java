package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.client.ui.screen;

import com.hhy.dreamingfishcore.client.ui.framework.anim.AnimatedFloat;
import com.hhy.dreamingfishcore.client.ui.framework.anim.Easing;
import com.hhy.dreamingfishcore.client.ui.framework.core.UiClock;
import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.Justify;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.screen.UiScreen;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Button;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ItemIcon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ScrollView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.TextField;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.network.Packet_RevivalRequest;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 重生锦鲤：输入被封禁玩家的名称，用自己的余量复苏对方。金色神圣风格。
 */
public class Screen_RevivalCharm extends UiScreen {
    private static final int GOLD = 0xFFFFCC33;
    private static final int GOLD_SOFT = 0xFFD4AF37;
    private static final int PANEL_TOP = 0xF41E1A0A;
    private static final int PANEL_BOTTOM = 0xF40E0C05;
    private static final int TEXT = 0xFFF4EBD2;
    private static final int MUTED = 0xFFB9AE8C;
    private static final int WARN = 0xFFFF7A6E;

    private final AnimatedFloat reveal = AnimatedFloat.tween(0.0F, 380.0F, Easing.EMPHASIZED);
    private final Text feedback = Text.of("").style(TextStyle.LABEL).color(WARN).centered().visible(false);
    private TextField nameField;

    public Screen_RevivalCharm() {
        super(Component.literal("重生锦鲤"));
        setBackground(Background.NONE);
    }

    @Override
    protected UiNode<?> build() {
        reveal.set(1.0F);
        Box header = Ui.row(
                Ui.stack(ItemIcon.of(new ItemStack(Items.TOTEM_OF_UNDYING)).iconSize(24.0F).noTooltip()).alignItems(Align.CENTER)
                        .size(38.0F, 38.0F).radius(Theme.Radius.LG).background(UiColor.withAlpha(GOLD, 0.12F))
                        .border(1.0F, UiColor.withAlpha(GOLD, 0.45F)),
                Ui.column(Text.of("重生锦鲤").style(TextStyle.HEADLINE).color(GOLD).singleLine(),
                        Text.of("SACRED REVIVAL · 以你的能量复苏同伴").style(TextStyle.CAPTION).color(MUTED).singleLine())
                        .gap(2.0F).grow(1.0F)
        ).gap(Theme.Space.LG).alignItems(Align.CENTER);

        nameField = TextField.of("输入被封禁玩家的名称…").icon(Icons.USER).maxLength(16).accentColor(GOLD)
                .onSubmit(this::confirm);
        nameField.height(26.0F);

        Box warning = Ui.row(Icon.of(Icons.WARNING, 11.0F).color(WARN),
                Text.of("成功复活后，你的模板重建余量会扣除一半；被复活者的感染情况与你相同。")
                        .style(TextStyle.LABEL).color(0xFFFFB3A8).grow(1.0F).shrink(1.0F))
                .gap(Theme.Space.SM).alignItems(Align.START).padding(Theme.Space.MD, Theme.Space.SM)
                .radius(Theme.Radius.MD).background(0x1FFF6A5F).border(1.0F, 0x40FF6A5F);

        Box actions = Ui.row(
                Button.of("复活").leadingIcon(Icons.SPARKLE).accentColor(GOLD_SOFT).large().grow(1.0F).basis(0.0F)
                        .onClick(this::confirm),
                Button.of("取消").ghost().large().grow(1.0F).basis(0.0F).onClick(this::onClose)
        ).gap(Theme.Space.MD);

        Box panel = new SacredPanel().column().alignItems(Align.STRETCH).gap(Theme.Space.LG).padding(Theme.Space.XL);
        panel.add(header, new GoldDivider().height(3.0F),
                Text.of("输入被封禁玩家的名称，用你的能量复苏他们。").style(TextStyle.BODY).color(TEXT),
                nameField, feedback, warning, actions);
        panel.width(380.0F).maxWidth(440.0F).enter(EnterEffect.POP);

        Box center = Ui.column(panel).alignItems(Align.CENTER).justify(Justify.CENTER).padding(Theme.Space.LG);
        return Ui.stack(new Glow(), ScrollView.of(center).justify(Justify.CENTER).alignItems(Align.CENTER))
                .alignItems(Align.STRETCH);
    }

    @Override
    protected void onOpened() {
        ui().focus(nameField);
    }

    private void confirm() {
        String name = nameField.value().trim();
        if (name.isEmpty()) {
            feedback.text("请输入玩家名称！").visible(true);
            ui().focus(nameField);
            return;
        }
        DreamingFishCore_NetworkManager.sendToServer(new Packet_RevivalRequest(name));
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    /** 背景：暗色遮罩与中心金色光晕，光晕缓慢呼吸。 */
    private final class Glow extends UiNode<Glow> {
        Glow() {
            pointerEvents(false);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            float p = reveal.get();
            float w = width();
            float h = height();
            canvas.fill(0.0F, 0.0F, w, h, UiColor.multiplyAlpha(0xDD0A0A00, p));
            float breath = (float) (0.85 + 0.15 * Math.sin(UiClock.now() / 900.0));
            canvas.shape(0.0F, 0.0F, w, h).radial(UiColor.withAlpha(GOLD, 0.16F * p * breath), 0x00000000,
                    w * 0.5F, h * 0.5F, Math.min(w, h) * 0.6F).draw();
        }
    }

    /** 金色描边的面板，外圈柔光。 */
    private static final class SacredPanel extends Box {
        @Override
        protected void paintBackground(UiCanvas canvas) {
            float w = width();
            float h = height();
            canvas.shape(0.0F, 0.0F, w, h).radius(Theme.Radius.XL).verticalGradient(PANEL_TOP, PANEL_BOTTOM)
                    .border(1.5F, UiColor.withAlpha(GOLD, 0.75F))
                    .shadow(new Theme.Shadow(0.0F, 0.0F, 26.0F, 0.0F, UiColor.withAlpha(GOLD, 0.28F))).draw();
            canvas.shape(4.0F, 4.0F, w - 8.0F, h - 8.0F).radius(Theme.Radius.LG).fill(0)
                    .border(1.0F, UiColor.withAlpha(GOLD, 0.18F)).draw();
        }
    }

    /** 双线金色分隔。 */
    private static final class GoldDivider extends UiNode<GoldDivider> {
        GoldDivider() {
            pointerEvents(false);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            float w = width();
            canvas.shape(0.0F, 0.0F, w, 1.5F).horizontalGradient(UiColor.withAlpha(GOLD_SOFT, 0.1F), GOLD_SOFT).draw();
            canvas.shape(0.0F, 2.5F, w, 0.5F).horizontalGradient(0x00666600, 0xAA666600).draw();
        }
    }
}
