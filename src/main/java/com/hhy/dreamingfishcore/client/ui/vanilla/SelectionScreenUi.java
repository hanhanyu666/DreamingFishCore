package com.hhy.dreamingfishcore.client.ui.vanilla;

import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.Justify;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.screen.ScreenHost;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Button;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Dynamic;
import com.hhy.dreamingfishcore.client.ui.framework.widget.EmptyState;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Responsive;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.TextField;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.client.ui.util.UiBackgroundRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.LanServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelSummary;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 世界选择与服务器列表：原版列表控件保留全部行为（异步读取、延迟检测、双击进入、键盘操作），
 * 外框、标题、搜索、详情面板与操作栏由框架绘制；操作栏转调原版按钮，可用状态逐帧同步。
 */
public final class SelectionScreenUi {
    public enum Kind {
        WORLDS,
        SERVERS
    }

    private static final String SELECT_WORLD = "net.minecraft.client.gui.screens.worldselection.SelectWorldScreen";
    private static final String JOIN_MULTIPLAYER = "net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen";

    private final Screen screen;
    private final Kind kind;
    private final Supplier<AbstractSelectionList<?>> list;
    private final Supplier<EditBox> searchBox;
    private final ScreenHost host;
    private final ListSlot slot = new ListSlot();
    private TextField search;
    private boolean searchFocused;

    public SelectionScreenUi(Screen screen, Kind kind, Supplier<AbstractSelectionList<?>> list, Supplier<EditBox> searchBox) {
        this.screen = screen;
        this.kind = kind;
        this.list = list;
        this.searchBox = searchBox;
        this.host = new ScreenHost(this::build);
    }

    /** 当前界面是否由本类接管（只认原版类本身，其他模组的子类不受影响）。 */
    public static boolean isActive() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) {
            return false;
        }
        String name = screen.getClass().getName();
        return SELECT_WORLD.equals(name) || JOIN_MULTIPLAYER.equals(name);
    }

    public ScreenHost host() {
        return host;
    }

    private int accent() {
        return kind == Kind.WORLDS ? SelectionEntryPainter.WORLD_ACCENT : SelectionEntryPainter.SERVER_ACCENT;
    }

    /** 隐藏原版按钮与搜索框：它们仍在，只是由新界面代为显示和触发。 */
    public void hideVanillaWidgets() {
        for (Button_ button : vanillaButtons()) {
            button.widget().visible = false;
        }
        EditBox box = searchBox.get();
        if (box != null) {
            box.visible = false;
        }
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiBackgroundRenderer.renderCyclingBackgroundCrossfade(graphics, screen.width, screen.height, 1.0F);
        hideVanillaWidgets();
        host.render(screen, graphics);
        if (!searchFocused && search != null && search.root() != null) {
            // 与原版一致：打开界面即可直接输入搜索
            searchFocused = true;
            host.ui().focus(search);
        }
        AbstractSelectionList<?> current = list.get();
        if (current != null && slot.width() > 8.0F && slot.height() > 8.0F) {
            int x = Math.round(slot.guiLeft()) + 2;
            int y = Math.round(slot.guiTop()) + 4;
            int w = Math.round(slot.guiRight() - slot.guiLeft()) - 4;
            int h = Math.round(slot.guiBottom() - slot.guiTop()) - 8;
            if (current.getX() != x || current.getY() != y || current.getWidth() != w || current.getHeight() != h) {
                current.setRectangle(w, h, x, y);
                current.clampScrollAmount();
            }
            current.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    // ==================== 构建 ====================

    private UiNode<?> build() {
        return Ui.stack(new Shade(), Responsive.of(this::layout)).alignItems(Align.STRETCH);
    }

    private UiNode<?> layout(Responsive.Size size) {
        boolean compact = size == Responsive.Size.COMPACT;
        Box body = Ui.row(slot.grow(1.0F).basis(0.0F)).gap(Theme.Space.LG).alignItems(Align.STRETCH).grow(1.0F).basis(0.0F);
        if (!compact) {
            body.add(Dynamic.of(this::selectedEntry, this::detail).width(size == Responsive.Size.WIDE ? 250.0F : 214.0F)
                    .enter(EnterEffect.FADE_LEFT.delayed(120.0F)));
        }
        slot.enter(EnterEffect.FADE_UP.delayed(60.0F));
        return Ui.column(
                header(compact).enter(EnterEffect.FADE_DOWN),
                body,
                Dynamic.of(() -> new ActionKey(vanillaButtons(), compact), key -> actionBar(key.compact()))
                        .enter(EnterEffect.FADE_UP.delayed(160.0F))
        ).gap(Theme.Space.MD).alignItems(Align.STRETCH)
                .padding(compact ? 14.0F : 24.0F, 14.0F, compact ? 14.0F : 24.0F, 12.0F);
    }

    private UiNode<?> header(boolean compact) {
        boolean worlds = kind == Kind.WORLDS;
        Box titleBlock = Ui.column(
                Ui.row(Text.of(worlds ? "世界档案" : "服务器档案").style(TextStyle.HEADLINE).color(0xFFF2F5F5).singleLine(),
                                Text.of(worlds ? "LOCAL ARCHIVE" : "NETWORK ARCHIVE").style(TextStyle.CAPTION_STRONG)
                                        .color(UiColor.withAlpha(accent(), 0.8F)).singleLine())
                        .gap(Theme.Space.MD).alignItems(Align.END),
                Text.of(worlds ? "继续旅程、创建新世界、管理备份" : "选择服务器、直连地址、刷新局域网").style(TextStyle.LABEL)
                        .color(0xFF9EA8AA).singleLine()
        ).gap(3.0F).shrink(1.0F);
        Box header = Ui.row(titleBlock, Ui.spacer()).alignItems(Align.CENTER).gap(Theme.Space.MD);
        if (worlds && searchBox.get() != null) {
            search = TextField.of("搜索存档名称或文件夹").icon(Icons.SEARCH).accentColor(accent())
                    .value(searchBox.get().getValue())
                    .onSubmit(() -> {
                        Button_ select = findButton("selectWorld.select");
                        if (select != null && select.widget().active) {
                            select.widget().onPress();
                        }
                    })
                    .onChange(value -> {
                        EditBox box = searchBox.get();
                        if (box != null && !box.getValue().equals(value)) {
                            box.setValue(value);
                        }
                    });
            search.width(compact ? 140.0F : 200.0F).height(22.0F);
            header.add(search);
        }
        header.add(countChip());
        return header;
    }

    private UiNode<?> countChip() {
        Text count = Text.of(() -> Component.literal(entryCount() + (kind == Kind.WORLDS ? " 个存档" : " 个服务器")))
                .style(TextStyle.LABEL_STRONG).color(0xFFE2E7E8).singleLine();
        return Ui.row(Icon.of(kind == Kind.WORLDS ? Icons.MAP : Icons.SIGNAL, 10.0F).color(accent()), count)
                .gap(5.0F).alignItems(Align.CENTER).padding(8.0F, 5.0F).radius(Theme.Radius.MD)
                .background(0x8A080C11).border(1.0F, 0x1EFFFFFF);
    }

    private int entryCount() {
        AbstractSelectionList<?> current = list.get();
        if (current == null) {
            return 0;
        }
        int count = 0;
        for (Object child : current.children()) {
            if (child instanceof SelectionEntryAccess.World || child instanceof SelectionEntryAccess.Server
                    || child instanceof ServerSelectionList.NetworkServerEntry) {
                count++;
            }
        }
        return count;
    }

    private Object selectedEntry() {
        AbstractSelectionList<?> current = list.get();
        return current == null ? null : current.getSelected();
    }

    // ==================== 详情面板 ====================

    private UiNode<?> detail(Object entry) {
        Box panel = VanillaChrome.glassPanel().column().alignItems(Align.STRETCH).gap(Theme.Space.MD).padding(Theme.Space.LG);
        if (entry instanceof SelectionEntryAccess.World world) {
            worldDetail(panel, world.dreamingFishCore$summary(), world.dreamingFishCore$icon());
        } else if (entry instanceof ServerSelectionList.OnlineServerEntry online && entry instanceof SelectionEntryAccess.Server access) {
            serverDetail(panel, online.getServerData(), access.dreamingFishCore$icon());
        } else if (entry instanceof ServerSelectionList.NetworkServerEntry lan) {
            lanDetail(panel, lan.getServerData());
        } else {
            panel.justify(Justify.CENTER).add(kind == Kind.WORLDS
                    ? EmptyState.of(Icons.MAP, "选择一个存档", "单击查看详情，双击直接进入")
                    : EmptyState.of(Icons.SIGNAL, "选择一个服务器", "单击查看状态，双击直接加入"));
        }
        return panel;
    }

    private void worldDetail(Box panel, LevelSummary summary, ResourceLocation icon) {
        String name = summary.getLevelName() == null || summary.getLevelName().isBlank() ? summary.getLevelId() : summary.getLevelName();
        panel.add(identity(icon, name, summary.getLevelId(), summary.primaryActionActive()),
                Ui.stack().height(1.0F).background(0x18FFFFFF),
                infoRow(Icons.USER, "游戏模式", summary.getGameMode().getLongDisplayName().getString()),
                infoRow(Icons.CLOCK, "上次游玩", summary.getLastPlayed() <= 0L ? "未记录"
                        : SelectionEntryPainter.DATE.format(Instant.ofEpochMilli(summary.getLastPlayed()))),
                infoRow(Icons.INFO, "版本", summary.getWorldVersionName().getString()));
        Box flags = Ui.row().gap(Theme.Space.XS).wrap(true);
        flag(flags, summary.isHardcore(), "极限模式", SelectionEntryPainter.WARN);
        flag(flags, summary.hasCommands(), "允许作弊", 0xFF9FB4FF);
        flag(flags, summary.isExperimental(), "实验性", SelectionEntryPainter.WARN);
        String status = SelectionEntryPainter.worldStatus(summary);
        flag(flags, true, status, SelectionEntryPainter.worldStatusColor(summary));
        panel.add(flags, Ui.spacer());
        panel.add(primaryButton("进入世界", "selectWorld.select"));
    }

    private void serverDetail(Box panel, ServerData data, ResourceLocation icon) {
        boolean hideAddress = Minecraft.getInstance().options.hideServerAddress;
        String name = data.name == null || data.name.isBlank() ? data.ip : data.name;
        panel.add(identity(icon, name, hideAddress ? "地址已隐藏" : data.ip, true),
                Text.of(() -> data.motd == null ? Component.empty() : data.motd).style(TextStyle.LABEL).color(0xFFB7C1C4).maxLines(2),
                Ui.stack().height(1.0F).background(0x18FFFFFF),
                infoRow(Icons.PULSE, "状态", () -> SelectionEntryPainter.serverStatus(data)),
                infoRow(Icons.SIGNAL, "延迟", () -> data.state() == ServerData.State.SUCCESSFUL && data.ping >= 0 ? data.ping + " ms" : "—"),
                infoRow(Icons.USERS, "在线", () -> data.players == null ? "—" : data.players.online() + " / " + data.players.max()),
                infoRow(Icons.INFO, "版本", () -> data.version == null ? "—" : data.version.getString()),
                Ui.spacer(),
                primaryButton("加入服务器", "selectServer.select"));
    }

    private void lanDetail(Box panel, LanServer server) {
        boolean hideAddress = Minecraft.getInstance().options.hideServerAddress;
        Box badge = Ui.stack(Icon.of(Icons.SIGNAL, 18.0F).color(SelectionEntryPainter.OK)).alignItems(Align.CENTER)
                .size(44.0F, 44.0F).radius(Theme.Radius.MD).background(UiColor.withAlpha(SelectionEntryPainter.OK, 0.12F));
        panel.add(Ui.row(badge, Ui.column(Text.of("局域网世界").style(TextStyle.TITLE).color(0xFFF0F5F6).singleLine(),
                                Text.of(hideAddress ? "地址已隐藏" : server.getAddress()).style(TextStyle.CAPTION).color(0xFF8E99A0).singleLine())
                        .gap(2.0F).shrink(1.0F)).gap(Theme.Space.MD).alignItems(Align.CENTER),
                Text.of(server.getMotd()).style(TextStyle.LABEL).color(0xFFB7C1C4).maxLines(2),
                Ui.spacer(),
                primaryButton("加入世界", "selectServer.select"));
    }

    private UiNode<?> identity(ResourceLocation icon, String name, String secondary, boolean active) {
        return Ui.row(new TextureView(icon, active).size(44.0F, 44.0F),
                Ui.column(Text.of(name).style(TextStyle.TITLE).color(0xFFF0F5F6).maxLines(2),
                        Text.of(secondary).style(TextStyle.CAPTION).color(0xFF8E99A0).singleLine()).gap(2.0F).grow(1.0F).shrink(1.0F)
        ).gap(Theme.Space.MD).alignItems(Align.CENTER);
    }

    private static UiNode<?> infoRow(Icons icon, String label, String value) {
        return infoRow(icon, label, () -> value);
    }

    private static UiNode<?> infoRow(Icons icon, String label, Supplier<String> value) {
        return Ui.row(Icon.of(icon, 10.0F).color(0xFF7E8A90),
                Text.of(label).style(TextStyle.LABEL).color(0xFF8E99A0).singleLine(),
                Ui.spacer(),
                Text.of(() -> Component.literal(value.get())).style(TextStyle.LABEL_STRONG).color(0xFFE2E7E8).singleLine().shrink(1.0F)
        ).gap(Theme.Space.SM).alignItems(Align.CENTER);
    }

    private static void flag(Box row, boolean show, String text, int color) {
        if (show) {
            row.add(Ui.stack(Text.of(text).style(TextStyle.CAPTION_STRONG).color(color).singleLine()).alignItems(Align.CENTER)
                    .padding(6.0F, 2.0F).radius(Theme.Radius.SM).background(UiColor.withAlpha(color, 0.14F))
                    .border(1.0F, UiColor.withAlpha(color, 0.3F)));
        }
    }

    private UiNode<?> primaryButton(String label, String key) {
        Button_ vanilla = findButton(key);
        Button button = Button.of(label).leadingIcon(Icons.ARROW_RIGHT).accentColor(accent()).large();
        if (vanilla != null) {
            button.onClick(() -> vanilla.widget().onPress());
            button.onUpdate(() -> button.disabled(!vanilla.widget().active));
        } else {
            button.disabled(true);
        }
        return button;
    }

    // ==================== 操作栏 ====================

    private record Button_(net.minecraft.client.gui.components.Button widget, String key) {
    }

    private record ActionKey(List<Button_> buttons, boolean compact) {
    }

    private List<Button_> vanillaButtons() {
        List<Button_> buttons = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof net.minecraft.client.gui.components.Button button) {
                buttons.add(new Button_(button, translationKey(button.getMessage())));
            }
        }
        return buttons;
    }

    private Button_ findButton(String key) {
        for (Button_ button : vanillaButtons()) {
            if (key.equals(button.key())) {
                return button;
            }
        }
        return null;
    }

    private UiNode<?> actionBar(boolean compact) {
        Box bar = Ui.row().gap(Theme.Space.SM).alignItems(Align.CENTER).wrap(true).justify(Justify.END);
        bar.add(Text.of(kind == Kind.WORLDS ? "双击存档直接进入" : "F5 刷新 · 双击直接加入").style(TextStyle.CAPTION)
                .color(0xFF7E8A90).singleLine().grow(1.0F));
        for (Button_ vanilla : vanillaButtons()) {
            String key = Objects.requireNonNullElse(vanilla.key(), "");
            boolean primary = key.equals("selectWorld.select") || key.equals("selectServer.select");
            if (primary && !compact) {
                continue;
            }
            Button button = Button.of(vanilla.widget().getMessage()).small();
            switch (key) {
                case "selectWorld.select", "selectServer.select" -> button.leadingIcon(Icons.ARROW_RIGHT).accentColor(accent());
                case "selectWorld.delete", "selectServer.delete" -> button.danger().leadingIcon(Icons.CLOSE);
                case "gui.back", "gui.cancel" -> button.ghost().leadingIcon(Icons.ARROW_LEFT);
                case "selectWorld.create", "selectServer.add" -> button.tonal().leadingIcon(Icons.PLUS).accentColor(accent());
                case "selectWorld.edit", "selectServer.edit" -> button.tonal().leadingIcon(Icons.SETTINGS).accentColor(accent());
                case "selectWorld.recreate", "selectServer.refresh" -> button.tonal().leadingIcon(Icons.REFRESH).accentColor(accent());
                case "selectServer.direct" -> button.tonal().leadingIcon(Icons.BOLT).accentColor(accent());
                default -> button.tonal().accentColor(accent());
            }
            button.onClick(() -> vanilla.widget().onPress());
            button.onUpdate(() -> button.disabled(!vanilla.widget().active));
            bar.add(button);
        }
        return bar;
    }

    private static String translationKey(Component component) {
        return component.getContents() instanceof TranslatableContents contents ? contents.getKey() : null;
    }

    // ==================== 部件 ====================

    /** 背景压暗，保证列表与文字可读。 */
    private static final class Shade extends UiNode<Shade> {
        Shade() {
            pointerEvents(false);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            float w = width();
            float h = height();
            canvas.shape(0.0F, 0.0F, w, h).verticalGradient(0x9005080C, 0xC007090D).draw();
            canvas.shape(0.0F, 0.0F, w, h).radial(0x00000000, 0x60000000, w * 0.5F, h * 0.45F, (float) Math.hypot(w, h) * 0.6F).draw();
        }
    }

    /** 列表区域：画一块玻璃底，原版列表按它的位置摆放。 */
    private static final class ListSlot extends Box {
        @Override
        protected void paintBackground(UiCanvas canvas) {
            canvas.shape(0.0F, 0.0F, width(), height()).radius(Theme.Radius.LG).fill(0x5A070B10).border(1.0F, 0x16FFFFFF).draw();
        }
    }

    /** 圆角贴图（世界/服务器图标）。 */
    private static final class TextureView extends UiNode<TextureView> {
        private final ResourceLocation texture;
        private final boolean active;

        TextureView(ResourceLocation texture, boolean active) {
            this.texture = texture;
            this.active = active;
            pointerEvents(false);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            canvas.shape(-1.0F, -1.0F, width() + 2.0F, height() + 2.0F).radius(Theme.Radius.MD + 1.0F).fill(0x90000000).draw();
            canvas.image(texture, 0.0F, 0.0F, width(), height(), 0.0F, 0.0F, 1.0F, 1.0F, Theme.Radius.MD,
                    active ? 0xFFFFFFFF : 0xFF707070);
        }
    }
}
