package com.hhy.dreamingfishcore.client.debug;

import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.Justify;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.screen.UiScreen;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Badge;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Button;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Card;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Divider;
import com.hhy.dreamingfishcore.client.ui.framework.widget.EntityView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ForEach;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ItemIcon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Modal;
import com.hhy.dreamingfishcore.client.ui.framework.widget.PlayerHead;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ProgressBar;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ProgressRing;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ScrollView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Segmented;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Slider;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.TextField;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Toggle;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** 组件展示：验证布局引擎与全部基础组件。 */
final class UiGalleryScreen extends UiScreen {
    private static final List<String> ROWS = List.of(
            "故事进入「余梦期」", "逐光会发布医疗接待公告", "Dev 完成了感染复核",
            "第 2 阶段任务「前往医疗接待点」开放", "世界历史新增 1 条记录", "白芷发来一条私信",
            "尸潮将在 3 个游戏日后抵达", "领地「晨光站」建成", "市场新增 4 条挂单");

    UiGalleryScreen() {
        super(Component.literal("UI Gallery"));
    }

    @Override
    protected UiNode<?> build() {
        Card left = Card.of(
                Ui.row(PlayerHead.local().headSize(28).cornerRadius(6),
                        Ui.column(Text.of("Dev").style(TextStyle.TITLE),
                                Ui.row(Badge.of("OPERATOR", ColorRole.DANGER), Badge.of("感染者", ColorRole.INFECTION),
                                        Badge.dot(ColorRole.SUCCESS).pulse()).gap(4)).gap(3)).gap(10),
                Divider.horizontal(),
                labeled("等级 9", ProgressBar.of(0.5F).gradientTo(0xFF7AB6FF)),
                labeled("体力 935/1180", ProgressBar.of(0.79F).role(ColorRole.SUCCESS)),
                labeled("模板重建余量", ProgressBar.of(0.25F).role(ColorRole.DANGER).segments(4)),
                Ui.row(ProgressRing.of(0.72F).diameter(34).gradientTo(0xFF5BD69A)
                                .add(Text.of("72%").style(TextStyle.CAPTION_STRONG)),
                        ProgressRing.spinner().diameter(20),
                        ItemIcon.of(new ItemStack(Items.DIAMOND_SWORD)).iconSize(20),
                        ItemIcon.of(new ItemStack(Items.GOLDEN_APPLE, 8))).gap(10),
                EntityView.of(() -> Minecraft.getInstance().player).height(90)
        ).width(170).gap(8);

        Segmented tabs = new Segmented().option("全部", Icons.GRID, 0).option("未读", Icons.MAIL, 3)
                .option("广播", Icons.MEGAPHONE, 0).equalWidth();

        ScrollView list = ScrollView.of(
                ForEach.of(() -> ROWS, s -> s, s -> Card.of(
                                Ui.row(Icon.of(Icons.HISTORY, 12).color(ColorRole.ACCENT),
                                        Text.of(s).singleLine().grow(1),
                                        Icon.of(Icons.CHEVRON_RIGHT, 10)).gap(8))
                        .padding(8, 7).onClick(() -> {
                        }))
                        .enter(EnterEffect.FADE_UP, 35).gap(6)
        ).grow(1).edgeFade(ColorRole.SURFACE);

        Card middle = Card.of(Ui.row(Text.of("组件与列表").style(TextStyle.SUBTITLE), Ui.spacer(),
                                Badge.of("9 条", ColorRole.ACCENT)),
                        tabs, list)
                .grow(1).basis(0).surface(ColorRole.SURFACE).flat().gap(8);

        Card right = Card.of(
                Text.of("控件").style(TextStyle.SUBTITLE),
                TextField.of("搜索广播、任务、玩家…").icon(Icons.SEARCH),
                Ui.row(Text.of("自动追踪任务").grow(1), Toggle.of(true, v -> {
                })).gap(6),
                Ui.row(Text.of("通知").grow(1), Toggle.of(false, v -> {
                })).gap(6),
                Slider.of(0, 100, 40, v -> {
                }),
                Ui.row(Button.of("确认").leadingIcon(Icons.CHECK).grow(1), Button.of("取消").ghost()).gap(6),
                Button.of("色调按钮").tonal(),
                Button.of("描边按钮").outlined().trailingIcon(Icons.ARROW_RIGHT),
                Button.of("打开对话框").danger().onClick(this::openDialog),
                Ui.row(Button.icon(Icons.SETTINGS).ghost(), Button.icon(Icons.BELL).ghost(),
                        Button.icon(Icons.CLOSE).outlined()).gap(4)
        ).width(150).gap(8);

        return Ui.row(left, middle, right).alignItems(Align.STRETCH).gap(Theme.Space.LG)
                .padding(Theme.Space.XL).justify(Justify.CENTER);
    }

    private static UiNode<?> labeled(String label, UiNode<?> bar) {
        return Ui.column(Text.of(label).style(TextStyle.CAPTION), bar).gap(3);
    }

    void openDialogForTest() {
        openDialog();
    }

    private void openDialog() {
        Modal[] holder = new Modal[1];
        Card dialog = Card.of(
                Ui.row(Icon.of(Icons.WARNING, 14).color(ColorRole.WARNING), Text.of("确认离开据点？").style(TextStyle.TITLE)).gap(8),
                Text.of("离开后尸潮防守进度将暂停，其他玩家会收到你离线的通知。").style(TextStyle.BODY_SECONDARY),
                Ui.row(Ui.spacer(), Button.of("再想想").ghost().onClick(() -> holder[0].dismiss()),
                        Button.of("离开").danger().onClick(() -> holder[0].dismiss())).gap(6)
        ).width(230).surface(ColorRole.SURFACE_OVERLAY).elevation(Theme.Elevation.LEVEL4, Theme.Elevation.LEVEL4).gap(10);
        holder[0] = Modal.show(ui, dialog);
    }
}
