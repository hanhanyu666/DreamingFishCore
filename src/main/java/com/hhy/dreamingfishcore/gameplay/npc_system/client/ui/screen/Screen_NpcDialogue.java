package com.hhy.dreamingfishcore.gameplay.npc_system.client.ui.screen;

import com.hhy.dreamingfishcore.client.ui.framework.anim.AnimatedFloat;
import com.hhy.dreamingfishcore.client.ui.framework.anim.Easing;
import com.hhy.dreamingfishcore.client.ui.framework.core.UiSounds;
import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.Cursor;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.Justify;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.screen.UiScreen;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Badge;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Dynamic;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.InteractiveNode;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Typewriter;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcDialogueViewData;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcInteractionType;
import com.hhy.dreamingfishcore.gameplay.npc_system.StoryNpcContentPolicy;
import com.hhy.dreamingfishcore.gameplay.npc_system.client.StoryNpcRenderer;
import com.hhy.dreamingfishcore.gameplay.npc_system.network.Packet_NpcInteractionRequest;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * NPC 对话：画面底部的对话框，左侧是 NPC 立像，中间是逐字显示的对白（长对白分页），
 * 右侧是交谈、关于、跟随、住处等行动。暖色调与终端的冷色调区分"现场对话"与"设备界面"。
 */
public class Screen_NpcDialogue extends UiScreen {
    private static final int PANEL_TOP = 0xF0120F0B;
    private static final int PANEL_BOTTOM = 0xF0090807;
    private static final int PANEL_BORDER = 0x889B7B43;
    private static final int GOLD = 0xFFFFD88A;
    private static final int TEXT = 0xFFEFE6D0;
    private static final int MUTED = 0xFFB8AA91;
    private static final int GOOD = 0xFF9DE08F;
    private static final int ACCENT = 0xFFCDAA64;
    private static final int OPTION = 0xFFEAD9B4;
    private static final int OPTION_HOVER = 0xFFFFD878;
    private static final int LOCKED = 0xFF777064;
    private static final String EMPTY_DIALOGUE = "对方暂时没有继续开口。";
    private static final String DEFAULT_INTRO = "随着你们逐渐的认识，你对这个人的了解会变多。";

    private final NpcDialogueViewData data;
    private final AnimatedFloat reveal = AnimatedFloat.tween(0.0F, 380.0F, Easing.EMPHASIZED);
    private final Typewriter typewriter = Typewriter.of("").style(TextStyle.BODY.withLineGap(4.0F)).color(TEXT)
            .linesPerPage(6).speed(55.0F);
    private int dialogueIndex;
    private boolean showingAbout;

    public Screen_NpcDialogue(NpcDialogueViewData data) {
        super(Component.literal("NPC对话"));
        this.data = data;
        setBackground(Background.NONE);
    }

    private enum Action {
        DIALOGUE(NpcInteractionType.DIALOGUE),
        ABOUT(null),
        FOLLOW(NpcInteractionType.FOLLOW),
        SET_HOME(NpcInteractionType.SET_HOME),
        HOSPITAL_REVIEW(NpcInteractionType.HOSPITAL_REVIEW),
        DAILY_TEMPLATE_SUPPORT(NpcInteractionType.DAILY_TEMPLATE_SUPPORT);

        final NpcInteractionType interaction;

        Action(NpcInteractionType interaction) {
            this.interaction = interaction;
        }
    }

    private record ActionEntry(String label, Icons icon, Action type, boolean enabled) {
    }

    @Override
    protected UiNode<?> build() {
        reveal.set(1.0F);
        typewriter.text(currentText());

        Badge relation = Badge.of((data.getRelationName().isEmpty() ? "尚未熟悉" : data.getRelationName())
                + " · 好感 " + data.getFavorability()).color(GOOD);
        Box nameRow = Ui.row(
                Text.of(data.getNpcName()).style(TextStyle.TITLE).color(GOLD).singleLine(),
                Text.of(data.getNpcProfession()).style(TextStyle.LABEL).color(MUTED).singleLine().shrink(1.0F),
                Ui.spacer(),
                relation
        ).gap(Theme.Space.MD).alignItems(Align.CENTER);

        Text modeLabel = Text.of(() -> Component.literal(showingAbout ? "关于 · " + data.getNpcName() : "对话"))
                .style(TextStyle.CAPTION_STRONG).color(ACCENT).singleLine();
        Box speech = Ui.row(
                Ui.stack().width(2.0F).radius(1.0F).background(UiColor.withAlpha(ACCENT, 0.8F)),
                Ui.column(modeLabel, typewriter).gap(Theme.Space.XS).grow(1.0F).shrink(1.0F)
        ).gap(Theme.Space.MD).alignItems(Align.STRETCH);

        Text pageHint = Text.of(() -> {
            int pages = typewriter.pageCount();
            if (pages > 1) {
                return Component.literal("第 " + (typewriter.page() + 1) + "/" + pages + " 页 · 点击交谈继续");
            }
            return Component.literal(typewriter.isPageFinished() ? "点击交谈继续" : "");
        }).style(TextStyle.CAPTION).color(MUTED).singleLine();

        Box dialogueColumn = Ui.column(nameRow, divider(), speech.grow(1.0F), Ui.row(pageHint, Ui.spacer(),
                Text.of("ESC 离开").style(TextStyle.CAPTION).color(MUTED).singleLine())).gap(Theme.Space.SM)
                .grow(1.0F).basis(0.0F);

        UiNode<?> actions = Dynamic.of(() -> showingAbout, about -> actionGrid()).width(176.0F);
        Box actionColumn = Ui.column(Text.of("你要怎么做？").style(TextStyle.LABEL_STRONG).color(ACCENT).singleLine(),
                divider(), actions).gap(Theme.Space.SM);

        Box panel = new DialoguePanel().row().alignItems(Align.STRETCH).gap(Theme.Space.LG)
                .padding(Theme.Space.LG, Theme.Space.MD, Theme.Space.LG, Theme.Space.MD);
        panel.add(new NpcPortrait().width(72.0F), dialogueColumn, actionColumn);
        panel.minHeight(112.0F).maxHeight(190.0F).enter(new EnterEffect(0, 18, 1, 0, 380, 0, Easing.EMPHASIZED));

        return Ui.stack(new BottomFade(), Ui.column(Ui.spacer(), panel).padding(10.0F, 10.0F))
                .alignItems(Align.STRETCH);
    }

    private static Box divider() {
        return Ui.stack().height(1.0F).background(0x55D0B16F);
    }

    private Box actionGrid() {
        List<ActionEntry> entries = new ArrayList<>(List.of(
                new ActionEntry("交谈", Icons.CHAT, Action.DIALOGUE, true),
                new ActionEntry(showingAbout ? "返回交谈" : "关于", showingAbout ? Icons.ARROW_LEFT : Icons.INFO, Action.ABOUT, true),
                new ActionEntry("邀请跟随", Icons.USERS, Action.FOLLOW, available(NpcInteractionType.FOLLOW)),
                new ActionEntry("设为住处", Icons.HOME, Action.SET_HOME, available(NpcInteractionType.SET_HOME))
        ));
        if (available(NpcInteractionType.HOSPITAL_REVIEW)) {
            entries.set(2, new ActionEntry("正式复查", Icons.PULSE, Action.HOSPITAL_REVIEW, true));
        }
        if (data.getNpcId() == StoryNpcContentPolicy.MEDICAL_STAFF_ID) {
            entries.set(2, new ActionEntry("提交并维护", Icons.PLUS, Action.DAILY_TEMPLATE_SUPPORT,
                    available(NpcInteractionType.DAILY_TEMPLATE_SUPPORT)));
            entries.remove(3);
        }
        Box grid = Ui.column().gap(6.0F);
        for (int i = 0; i < entries.size(); i += 2) {
            Box row = Ui.row().gap(6.0F).alignItems(Align.STRETCH);
            for (int j = i; j < i + 2; j++) {
                row.add(j < entries.size() ? new ActionButton(entries.get(j)).grow(1.0F).basis(0.0F)
                        : Ui.spacer().basis(0.0F));
            }
            grid.add(row);
        }
        return grid;
    }

    private boolean available(NpcInteractionType type) {
        return data.getAvailableActions().contains(type.name());
    }

    private String currentText() {
        if (showingAbout) {
            return data.getNpcIntroduction().isBlank() ? DEFAULT_INTRO : data.getNpcIntroduction();
        }
        List<String> dialogues = data.getDialogues();
        return dialogues.isEmpty() ? EMPTY_DIALOGUE : dialogues.get(Math.min(dialogueIndex, dialogues.size() - 1));
    }

    private void perform(Action action) {
        if (action == Action.ABOUT) {
            showingAbout = !showingAbout;
            typewriter.text(currentText());
            typewriter.restart();
            return;
        }
        if (action == Action.DIALOGUE) {
            if (showingAbout) {
                showingAbout = false;
                typewriter.text(currentText());
            }
            if (typewriter.advance()) {
                return;
            }
            dialogueIndex++;
            typewriter.text(currentText());
            typewriter.restart();
        }
        DreamingFishCore_NetworkManager.sendToServer(
                new Packet_NpcInteractionRequest(data.getNpcId(), data.getEntityId(), action.interaction));
    }

    private LivingEntity dialogueEntity() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || data.getEntityId() < 0) {
            return null;
        }
        Entity entity = minecraft.level.getEntity(data.getEntityId());
        return entity instanceof LivingEntity living ? living : null;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
            UiSounds.soft();
            perform(Action.DIALOGUE);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ==================== 部件 ====================

    /** 对话框底板：暖色玻璃，顶部一道金色高光。 */
    private final class DialoguePanel extends Box {
        @Override
        protected void paintBackground(UiCanvas canvas) {
            float w = width();
            float h = height();
            canvas.shape(0.0F, 0.0F, w, h).radius(Theme.Radius.XL).verticalGradient(PANEL_TOP, PANEL_BOTTOM)
                    .border(1.0F, PANEL_BORDER).shadow(new Theme.Shadow(0.0F, 8.0F, 28.0F, 0.0F, 0xAA000000)).draw();
            canvas.shape(20.0F, 0.0F, w - 40.0F, 1.0F)
                    .horizontalGradient(UiColor.withAlpha(GOLD, 0.0F), UiColor.withAlpha(GOLD, 0.45F)).draw();
        }
    }

    /** 画面下方的渐暗，让对话框与世界自然衔接。 */
    private final class BottomFade extends UiNode<BottomFade> {
        BottomFade() {
            pointerEvents(false);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            float p = reveal.get();
            float h = height();
            float top = h * 0.45F;
            canvas.shape(0.0F, top, width(), h - top)
                    .verticalGradient(0x00000000, UiColor.withAlpha(0xFF000000, 0.62F * p)).draw();
        }
    }

    /** NPC 立像：复用世界中的实体渲染，临时隐藏名称牌；视线跟随鼠标但限制扭头幅度。 */
    private final class NpcPortrait extends UiNode<NpcPortrait> {
        NpcPortrait() {
            pointerEvents(false);
        }

        @Override
        protected void paintBackground(UiCanvas canvas) {
            canvas.shape(0.0F, 0.0F, width(), height()).radius(Theme.Radius.LG)
                    .radial(UiColor.withAlpha(ACCENT, 0.18F), 0x00000000, width() * 0.5F, height() * 0.65F, height() * 0.7F).draw();
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            LivingEntity entity = dialogueEntity();
            if (entity == null) {
                return;
            }
            float x0 = guiLeft();
            float y0 = guiTop();
            float x1 = guiRight();
            float y1 = guiBottom();
            int size = Math.max(20, Math.round(Math.min((y1 - y0) * 0.42F, (x1 - x0) * 0.6F)));
            double mouseX = ui().mouseX();
            double mouseY = ui().mouseY();
            float trackedX = (float) Mth.clamp(mouseX, x0, x1);
            float trackedY = (float) Mth.clamp(mouseY, y0, y1);
            canvas.custom(0.0F, 0.0F, width(), height(), g -> {
                boolean nameVisible = entity.isCustomNameVisible();
                entity.setCustomNameVisible(false);
                PoseStack pose = g.pose();
                pose.pushPose();
                pose.last().pose().identity();
                try {
                    StoryNpcRenderer.renderWithoutNameplate(() -> InventoryScreen.renderEntityInInventoryFollowsMouse(g,
                            Math.round(x0), Math.round(y0) - 8, Math.round(x1), Math.round(y1) - 2, size, 0.0625F,
                            trackedX, trackedY, entity));
                } finally {
                    pose.popPose();
                    entity.setCustomNameVisible(nameVisible);
                }
            });
        }
    }

    /** 行动按钮：暖色描边，锁定时显示"未解锁"。 */
    private final class ActionButton extends InteractiveNode<ActionButton> {
        private final ActionEntry entry;
        private final Text label;
        private final Icon icon;

        ActionButton(ActionEntry entry) {
            this.entry = entry;
            row().alignItems(Align.CENTER).justify(Justify.CENTER).gap(5.0F).height(22.0F).padding(6.0F, 0.0F);
            icon = Icon.of(entry.icon(), 10.0F);
            label = Text.of(entry.enabled() ? entry.label() : entry.label() + " · 未解锁").style(TextStyle.LABEL).singleLine()
                    .shrink(1.0F);
            add(icon, label);
            if (entry.enabled()) {
                cursor(Cursor.POINTER);
                focusable(true);
                onClick(() -> perform(entry.type()));
            }
        }

        @Override
        protected void update() {
            int color = entry.enabled() ? UiColor.lerp(OPTION, OPTION_HOVER, hover()) : LOCKED;
            label.color(color);
            icon.color(color);
        }

        @Override
        protected void onStateChanged() {
            super.onStateChanged();
            if (entry.enabled()) {
                animateScale(isPressed() ? 0.97F : 1.0F);
            }
        }

        @Override
        protected void paintBackground(UiCanvas canvas) {
            float h = hover();
            int fill = entry.enabled() ? UiColor.lerp(0x8A1A1815, 0xC4372E20, h) : 0x69201E1A;
            int border = entry.enabled() ? UiColor.lerp(0x55D0B16F, OPTION_HOVER, h) : 0x33D0B16F;
            canvas.shape(0.0F, 0.0F, width(), height()).radius(Theme.Radius.MD).fill(fill).border(1.0F, border).draw();
        }
    }
}
