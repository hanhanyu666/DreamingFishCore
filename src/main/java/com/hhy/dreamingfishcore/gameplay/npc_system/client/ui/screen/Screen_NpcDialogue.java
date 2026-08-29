package com.hhy.dreamingfishcore.gameplay.npc_system.client.ui.screen;

import com.hhy.dreamingfishcore.client.ui.components.UiPanelRenderer;
import com.hhy.dreamingfishcore.client.ui.util.VirtualCoordinateHelper;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcDialogueViewData;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcInteractionType;
import com.hhy.dreamingfishcore.gameplay.npc_system.client.StoryNpcRenderer;
import com.hhy.dreamingfishcore.gameplay.npc_system.network.Packet_NpcInteractionRequest;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class Screen_NpcDialogue extends Screen {
    private static final Minecraft MC = Minecraft.getInstance();

    private static final String TITLE_FALLBACK = "NPC对话";
    private static final String LABEL_ACTION = "你要怎么做？";
    private static final String BUTTON_DIALOGUE = "交谈";
    private static final String BUTTON_ABOUT = "关于";
    private static final String BUTTON_BACK_TO_DIALOGUE = "返回交谈";
    private static final String BUTTON_FOLLOW = "邀请跟随";
    private static final String BUTTON_SET_HOME = "设为住处";
    private static final String TEXT_LOCKED = "未解锁";
    private static final String TEXT_EMPTY_DIALOGUE = "对方暂时没有继续开口。";
    private static final String TEXT_CLOSE_HINT = "ESC 离开";

    private static final int COLOR_PANEL = 0xDC090B0E;
    private static final int COLOR_PANEL_BORDER = 0x7A9B7B43;
    private static final int COLOR_BUTTON = 0x8A1A1815;
    private static final int COLOR_BUTTON_HOVER = 0xC4372E20;
    private static final int COLOR_BUTTON_LOCKED = 0x69201E1A;
    private static final int COLOR_TITLE = 0xFFFFD88A;
    private static final int COLOR_TEXT = 0xFFEFE6D0;
    private static final int COLOR_MUTED = 0xFFB8AA91;
    private static final int COLOR_GOOD = 0xFF9DE08F;
    private static final int COLOR_ACCENT = 0xFFCDAA64;
    private static final int COLOR_OPTION = 0xFFEAD9B4;
    private static final int COLOR_OPTION_HOVER = 0xFFFFD878;
    private static final int COLOR_OPTION_LOCKED = 0xFF777064;
    private static final int COLOR_DIVIDER = 0x55D0B16F;

    private final NpcDialogueViewData data;
    private final VirtualCoordinateHelper.VirtualSizeResult virtualSize =
            new VirtualCoordinateHelper.VirtualSizeResult();
    private final List<TextActionArea> textActionAreas = new ArrayList<>();

    private int virtualWidth;
    private int virtualHeight;
    private float uiScale;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int modelCenterX;
    private int modelFootY;
    private int dialogueX;
    private int dialogueWidth;
    private int actionX;
    private int actionWidth;
    private int dialogueIndex;
    private boolean showingAbout;
    private long openTime;

    public Screen_NpcDialogue(NpcDialogueViewData data) {
        super(Component.literal(TITLE_FALLBACK));
        this.data = data;
    }

    @Override
    protected void init() {
        openTime = System.currentTimeMillis();
        calculateVirtualLayout();
        rebuildTextActions();
    }

    private void calculateVirtualLayout() {
        VirtualCoordinateHelper.calculateVirtualSize(this, virtualSize);
        uiScale = virtualSize.uiScale;
        virtualWidth = virtualSize.virtualWidth;
        virtualHeight = virtualSize.virtualHeight;

        panelX = 12;
        panelHeight = Math.max(98, Math.min(110, virtualHeight / 3 - 8));
        panelY = virtualHeight - panelHeight - 10;
        panelWidth = virtualWidth - panelX * 2;

        modelCenterX = panelX + 42;
        modelFootY = panelY + panelHeight - 7;
        dialogueX = panelX + 84;
        actionWidth = 166;
        actionX = panelX + panelWidth - actionWidth - 10;
        dialogueWidth = Math.max(190, actionX - dialogueX - 14);
    }

    private void rebuildTextActions() {
        textActionAreas.clear();
        List<TextAction> actions = List.of(
                new TextAction(BUTTON_DIALOGUE, ScreenAction.DIALOGUE, true),
                new TextAction(showingAbout ? BUTTON_BACK_TO_DIALOGUE : BUTTON_ABOUT,
                        ScreenAction.ABOUT, true),
                new TextAction(BUTTON_FOLLOW, ScreenAction.FOLLOW,
                        isActionAvailable(NpcInteractionType.FOLLOW)),
                new TextAction(BUTTON_SET_HOME, ScreenAction.SET_HOME,
                        isActionAvailable(NpcInteractionType.SET_HOME))
        );

        int columns = 2;
        int gap = 6;
        int buttonWidth = (actionWidth - gap) / columns;
        int buttonHeight = 22;
        int startY = panelY + 29;
        for (int index = 0; index < actions.size(); index++) {
            TextAction action = actions.get(index);
            String label = action.enabled ? action.label : action.label + " · " + TEXT_LOCKED;
            int column = index % columns;
            int row = index / columns;
            int x = actionX + column * (buttonWidth + gap);
            int y = startY + row * (buttonHeight + 6);
            textActionAreas.add(new TextActionArea(x, y, buttonWidth, buttonHeight,
                    label, action.type, action.enabled));
        }
    }

    private boolean isActionAvailable(NpcInteractionType type) {
        return data.getAvailableActions().contains(type.name());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        calculateVirtualLayout();
        rebuildTextActions();

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(uiScale, uiScale, 1.0f);
        float virtualMouseX = mouseX / uiScale;
        float virtualMouseY = mouseY / uiScale;

        renderStage(guiGraphics);
        renderNpcModel(guiGraphics, virtualMouseX, virtualMouseY);
        renderInfoColumn(guiGraphics);
        renderDialogueColumn(guiGraphics);
        renderActionColumn(guiGraphics, virtualMouseX, virtualMouseY);
        renderFooter(guiGraphics);

        guiGraphics.pose().popPose();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderStage(GuiGraphics guiGraphics) {
        renderQuickFade(guiGraphics);
        UiPanelRenderer.smoothRoundedRect(guiGraphics, panelX, panelY, panelWidth, panelHeight,
                7, COLOR_PANEL, COLOR_PANEL_BORDER);
    }

    private void renderQuickFade(GuiGraphics guiGraphics) {
        int fadeTop = panelY - 18;
        for (int y = fadeTop; y < virtualHeight; y += 3) {
            float ratio = (float) (y - fadeTop) / Math.max(1, virtualHeight - fadeTop);
            int alpha = (int) (ratio * 88.0f);
            guiGraphics.fill(0, y, virtualWidth, Math.min(virtualHeight, y + 3), alpha << 24);
        }
    }

    private void renderNpcModel(GuiGraphics guiGraphics, float mouseX, float mouseY) {
        LivingEntity entity = getDialogueEntity();
        if (entity == null) {
            return;
        }

        int modelSize = Math.max(34, Math.min(42, panelHeight / 2 - 8));
        int modelLeft = modelCenterX - modelSize;
        int modelTop = modelFootY - modelSize * 2;
        int modelRight = modelCenterX + modelSize;
        int clipPadding = 8;
        int clipLeft = modelLeft - clipPadding;
        int clipTop = modelTop - clipPadding;
        int clipRight = modelRight + clipPadding;
        int clipBottom = modelFootY + clipPadding;

        // 原版接口需要模型边界坐标系中的绝对鼠标坐标；限幅可避免人物大幅扭头。
        float trackedMouseX = Mth.clamp(mouseX, (float) modelLeft, (float) modelRight);
        float trackedMouseY = Mth.clamp(mouseY, (float) modelTop, (float) modelFootY);

        // 对话预览复用世界实体，只在这一帧临时隐藏名称牌，渲染后立即恢复。
        boolean customNameVisible = entity.isCustomNameVisible();
        entity.setCustomNameVisible(false);
        try {
            StoryNpcRenderer.renderWithoutNameplate(() ->
                    InventoryScreen.renderEntityInInventoryFollowsMouse(
                            guiGraphics,
                            clipLeft,
                            clipTop,
                            clipRight,
                            clipBottom,
                            modelSize,
                            0.0625F,
                            trackedMouseX,
                            trackedMouseY,
                            entity
                    ));
        } finally {
            entity.setCustomNameVisible(customNameVisible);
        }
    }

    private void renderInfoColumn(GuiGraphics guiGraphics) {
        int headerY = panelY + 10;
        String relation = data.getRelationName().isEmpty() ? "尚未熟悉" : data.getRelationName();
        relation = fitText(relation + " · 好感 " + data.getFavorability(),
                Math.max(72, dialogueWidth / 2));
        int relationWidth = MC.font.width(relation);
        int relationX = dialogueX + dialogueWidth - relationWidth;

        String npcName = fitText(data.getNpcName(), Math.max(42, dialogueWidth / 3));
        guiGraphics.drawString(MC.font, npcName, dialogueX, headerY, COLOR_TITLE, false);

        int professionX = dialogueX + MC.font.width(npcName) + 8;
        int professionWidth = Math.max(0, relationX - professionX - 8);
        if (professionWidth > 18 && !data.getNpcProfession().isEmpty()) {
            guiGraphics.drawString(MC.font, fitText(data.getNpcProfession(), professionWidth),
                    professionX, headerY, COLOR_MUTED, false);
        }
        guiGraphics.drawString(MC.font, relation, relationX, headerY, COLOR_GOOD, false);
    }

    private void renderDialogueColumn(GuiGraphics guiGraphics) {
        if (showingAbout) {
            String introduction = data.getNpcIntroduction().isBlank()
                    ? "随着你们逐渐的认识，你对这个人的了解会变多。"
                    : data.getNpcIntroduction();
            UiPanelRenderer.roundedRect(guiGraphics, dialogueX - 8, panelY + 31, 2,
                    Math.min(48, panelHeight - 48), 1, COLOR_ACCENT);
            drawTypewriterWrapped(guiGraphics, introduction, dialogueX, panelY + 31,
                    dialogueWidth, COLOR_TEXT, 4);
            return;
        }

        List<String> dialogues = data.getDialogues();
        String dialogue = dialogues.isEmpty()
                ? TEXT_EMPTY_DIALOGUE
                : dialogues.get(Math.min(dialogueIndex, dialogues.size() - 1));
        UiPanelRenderer.roundedRect(guiGraphics, dialogueX - 8, panelY + 31, 2,
                Math.min(48, panelHeight - 48), 1, COLOR_ACCENT);
        drawTypewriterWrapped(guiGraphics, dialogue, dialogueX, panelY + 31,
                dialogueWidth, COLOR_TEXT, 4);
    }

    private void renderActionColumn(GuiGraphics guiGraphics, float mouseX, float mouseY) {
        drawColumnHeader(guiGraphics, LABEL_ACTION, actionX, panelY + 10, actionWidth);
        for (TextActionArea area : textActionAreas) {
            boolean hovered = area.contains((int) mouseX, (int) mouseY);
            int textColor = area.enabled
                    ? (hovered ? COLOR_OPTION_HOVER : COLOR_OPTION)
                    : COLOR_OPTION_LOCKED;
            int background = area.enabled
                    ? (hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON)
                    : COLOR_BUTTON_LOCKED;
            int border = hovered && area.enabled ? COLOR_OPTION_HOVER : COLOR_DIVIDER;
            UiPanelRenderer.roundedRect(guiGraphics, area.x, area.y, area.width, area.height,
                    4, background);
            UiPanelRenderer.roundedBorder(guiGraphics, area.x, area.y, area.width, area.height,
                    4, border);

            String label = fitText(area.label, area.width - 8);
            int textX = area.x + (area.width - MC.font.width(label)) / 2;
            int textY = area.y + (area.height - MC.font.lineHeight) / 2;
            guiGraphics.drawString(MC.font, label, textX, textY, textColor, false);
        }
    }

    private void drawColumnHeader(GuiGraphics guiGraphics, String title, int x, int y, int width) {
        guiGraphics.drawString(MC.font, title, x, y, COLOR_ACCENT, false);
        guiGraphics.fill(x, y + 12, x + width, y + 13, COLOR_DIVIDER);
    }

    private void renderFooter(GuiGraphics guiGraphics) {
        int hintWidth = MC.font.width(TEXT_CLOSE_HINT);
        guiGraphics.drawString(MC.font, TEXT_CLOSE_HINT,
                panelX + panelWidth - hintWidth - 12, panelY + panelHeight - 15,
                COLOR_MUTED, false);
    }

    private void drawWrapped(GuiGraphics guiGraphics, String text, int x, int y,
                             int width, int color, int maxLines) {
        var lines = MC.font.getSplitter().splitLines(text, width, Style.EMPTY);
        int count = Math.min(maxLines, lines.size());
        for (int index = 0; index < count; index++) {
            guiGraphics.drawString(MC.font, lines.get(index).getString(), x,
                    y + index * 12, color, false);
        }
    }

    private void drawTypewriterWrapped(GuiGraphics guiGraphics, String text, int x, int y,
                                       int width, int color, int maxLines) {
        int visibleCharacters = Math.min(text.length(),
                (int) ((System.currentTimeMillis() - openTime) / 18L));
        drawWrapped(guiGraphics, text.substring(0, visibleCharacters),
                x, y, width, color, maxLines);
    }

    private String fitText(String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (MC.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int contentWidth = Math.max(0, maxWidth - MC.font.width(ellipsis));
        return MC.font.plainSubstrByWidth(text, contentWidth) + ellipsis;
    }

    private LivingEntity getDialogueEntity() {
        if (MC.level == null || data.getEntityId() < 0) {
            return null;
        }
        Entity entity = MC.level.getEntity(data.getEntityId());
        if (entity instanceof LivingEntity livingEntity) {
            return livingEntity;
        }
        return null;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int virtualMouseX = (int) (mouseX / uiScale);
            int virtualMouseY = (int) (mouseY / uiScale);
            for (TextActionArea area : textActionAreas) {
                if (area.contains(virtualMouseX, virtualMouseY)) {
                    if (area.enabled) {
                        if (area.type == ScreenAction.ABOUT) {
                            showingAbout = !showingAbout;
                            openTime = System.currentTimeMillis();
                            return true;
                        }
                        if (area.type == ScreenAction.DIALOGUE) {
                            showingAbout = false;
                            dialogueIndex++;
                            openTime = System.currentTimeMillis();
                        }
                        DreamingFishCore_NetworkManager.sendToServer(
                                new Packet_NpcInteractionRequest(data.getNpcId(),
                                        data.getEntityId(), area.type.interactionType));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum ScreenAction {
        DIALOGUE(NpcInteractionType.DIALOGUE),
        ABOUT(null),
        FOLLOW(NpcInteractionType.FOLLOW),
        SET_HOME(NpcInteractionType.SET_HOME);

        private final NpcInteractionType interactionType;

        ScreenAction(NpcInteractionType interactionType) {
            this.interactionType = interactionType;
        }
    }

    private record TextAction(String label, ScreenAction type, boolean enabled) {
    }

    private record TextActionArea(int x, int y, int width, int height, String label,
                                  ScreenAction type, boolean enabled) {
        private boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width
                    && mouseY >= y && mouseY < y + height;
        }
    }
}
