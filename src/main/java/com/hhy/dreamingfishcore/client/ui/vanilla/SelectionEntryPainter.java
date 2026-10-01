package com.hhy.dreamingfishcore.client.ui.vanilla;

import com.hhy.dreamingfishcore.client.ui.framework.anim.AnimatedFloat;
import com.hhy.dreamingfishcore.client.ui.framework.anim.Easing;
import com.hhy.dreamingfishcore.client.ui.framework.anim.Spring;
import com.hhy.dreamingfishcore.client.ui.framework.core.UiClock;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.LanServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.storage.LevelSummary;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 世界/服务器列表条目的绘制。条目仍由原版列表管理（滚动、点击、键盘、异步加载），
 * 这里只负责外观：每次绘制开一段框架画布，按条目对象记住悬停与选中的动画状态。
 */
public final class SelectionEntryPainter {
    static final int WORLD_ACCENT = 0xFF83C8D8;
    static final int SERVER_ACCENT = 0xFF7EC28F;
    static final int OK = 0xFF7EC28F;
    static final int WARN = 0xFFD0A45F;
    static final int BAD = 0xFFD16862;
    static final int IDLE = 0xFF9BA5A8;
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm").withZone(ZoneId.systemDefault());

    private static final UiCanvas CANVAS = new UiCanvas();
    private static final Map<Object, EntryState> STATES = new WeakHashMap<>();
    private static boolean nextSelected;

    private SelectionEntryPainter() {
    }

    /** 原版在绘制选中条目前调用 renderSelection；记下来交给紧接着的条目绘制。 */
    public static void markNextSelected() {
        nextSelected = true;
    }

    private static boolean consumeSelected() {
        boolean selected = nextSelected;
        nextSelected = false;
        return selected;
    }

    private static final class EntryState {
        final AnimatedFloat hover = AnimatedFloat.spring(0.0F, Spring.SNAPPY);
        final AnimatedFloat select = AnimatedFloat.spring(0.0F, Spring.GENTLE);
        final AnimatedFloat appear = AnimatedFloat.tween(0.0F, 420.0F, Easing.EMPHASIZED);
        boolean started;
    }

    private static EntryState state(Object entry, int index, boolean hovering, boolean selected) {
        EntryState state = STATES.computeIfAbsent(entry, key -> new EntryState());
        if (!state.started) {
            state.started = true;
            state.appear.delay(Math.min(index, 10) * 40.0F);
            state.appear.set(1.0F);
        }
        state.hover.set(hovering ? 1.0F : 0.0F);
        state.select.set(selected ? 1.0F : 0.0F);
        return state;
    }

    // ==================== 条目 ====================

    public static void paintWorld(GuiGraphics graphics, Object entry, LevelSummary summary, ResourceLocation icon, int index,
                                  int top, int left, int width, int height, boolean hovering) {
        boolean selected = consumeSelected();
        EntryState state = state(entry, index, hovering, selected);
        boolean playable = summary.primaryActionActive();
        int accent = playable ? WORLD_ACCENT : 0xFF757B80;
        String status = worldStatus(summary);
        int statusColor = worldStatusColor(summary);

        CANVAS.begin(graphics);
        float x = left - 2.0F;
        float w = width + 2.0F;
        float appear = state.appear.get();
        CANVAS.pushAlpha(appear);
        CANVAS.push();
        CANVAS.translate((1.0F - appear) * -12.0F, 0.0F);
        paintCard(x, top, w, height, accent, state.hover.get(), state.select.get());

        float iconSize = height - 12.0F;
        float iconX = x + 9.0F;
        float iconY = top + 6.0F;
        CANVAS.shape(iconX - 1.0F, iconY - 1.0F, iconSize + 2.0F, iconSize + 2.0F).radius(Theme.Radius.MD + 1.0F)
                .fill(0x90000000).draw();
        CANVAS.image(icon, iconX, iconY, iconSize, iconSize, 0.0F, 0.0F, 1.0F, 1.0F, Theme.Radius.MD,
                playable ? 0xFFFFFFFF : 0xFF707070);

        Font font = Minecraft.getInstance().font;
        float textX = iconX + iconSize + 10.0F;
        float statusW = font.width(status) * 0.75F + 14.0F;
        float maxText = Math.max(40.0F, x + w - textX - statusW - 18.0F);
        String name = summary.getLevelName() == null || summary.getLevelName().isBlank() ? "世界 " + (index + 1) : summary.getLevelName();
        String date = summary.getLastPlayed() <= 0L ? "未记录时间" : DATE.format(Instant.ofEpochMilli(summary.getLastPlayed()));
        CANVAS.text(trim(font, name, maxText), textX, top + 7.0F, playable ? 0xFFF0F5F6 : 0xFF8A8F92, 1.0F, false);
        CANVAS.text(trim(font, summary.getLevelId() + "  ·  " + date, maxText / 0.75F), textX, top + 20.0F, 0xFF9BA5A8, 0.75F, false);
        FormattedCharSequence info = firstLine(font, summary.getInfo(), maxText / 0.75F);
        if (info != null) {
            CANVAS.text(info, textX, top + 30.0F, 0xFF76838A, 0.75F, false);
        }
        paintPill(x + w - statusW - 10.0F, top + (height - 13.0F) * 0.5F, statusW, status, statusColor);
        paintPlayHint(x + w, top, height, accent, state.hover.get() * (playable ? 1.0F : 0.0F));
        CANVAS.pop();
        CANVAS.popAlpha();
        CANVAS.end();
    }

    public static void paintServer(GuiGraphics graphics, Object entry, ServerData data, ResourceLocation icon, int index,
                                   int top, int left, int width, int height, boolean hovering) {
        boolean selected = consumeSelected();
        EntryState state = state(entry, index, hovering, selected);
        int statusColor = serverStatusColor(data);

        CANVAS.begin(graphics);
        float x = left - 2.0F;
        float w = width + 2.0F;
        float appear = state.appear.get();
        CANVAS.pushAlpha(appear);
        CANVAS.push();
        CANVAS.translate((1.0F - appear) * -12.0F, 0.0F);
        paintCard(x, top, w, height, SERVER_ACCENT, state.hover.get(), state.select.get());

        float iconSize = height - 12.0F;
        float iconX = x + 9.0F;
        float iconY = top + 6.0F;
        CANVAS.shape(iconX - 1.0F, iconY - 1.0F, iconSize + 2.0F, iconSize + 2.0F).radius(Theme.Radius.MD + 1.0F)
                .fill(0x90000000).draw();
        CANVAS.image(icon, iconX, iconY, iconSize, iconSize, 0.0F, 0.0F, 1.0F, 1.0F, Theme.Radius.MD, 0xFFFFFFFF);

        Font font = Minecraft.getInstance().font;
        float textX = iconX + iconSize + 10.0F;
        float maxText = Math.max(40.0F, x + w - textX - 86.0F);
        String name = data.name == null || data.name.isBlank() ? data.ip : data.name;
        CANVAS.text(trim(font, name, maxText), textX, top + 7.0F, 0xFFF0F5F6, 1.0F, false);
        FormattedCharSequence motd = firstLine(font, data.motd, maxText / 0.875F);
        if (motd != null) {
            CANVAS.text(motd, textX, top + 20.0F, 0xFFB7C1C4, 0.875F, false);
        }
        boolean hideAddress = Minecraft.getInstance().options.hideServerAddress;
        CANVAS.text(trim(font, hideAddress ? "地址已隐藏" : data.ip, maxText / 0.75F), textX, top + 32.0F, 0xFF76838A, 0.75F, false);

        float right = x + w - 12.0F;
        paintSignal(right - 22.0F, top + 9.0F, data);
        String players = data.players == null ? "" : data.players.online() + " / " + data.players.max();
        String label = data.state() == ServerData.State.SUCCESSFUL ? players : serverStatus(data);
        float labelW = font.width(label) * 0.75F;
        CANVAS.text(label, right - labelW, top + height - 15.0F, data.state() == ServerData.State.SUCCESSFUL ? 0xFFC9D2D4 : statusColor,
                0.75F, false);
        CANVAS.pop();
        CANVAS.popAlpha();
        CANVAS.end();
    }

    public static void paintLan(GuiGraphics graphics, Object entry, LanServer server, int index, int top, int left, int width,
                                int height, boolean hovering) {
        boolean selected = consumeSelected();
        EntryState state = state(entry, index, hovering, selected);
        CANVAS.begin(graphics);
        float x = left - 2.0F;
        float w = width + 2.0F;
        CANVAS.pushAlpha(state.appear.get());
        paintCard(x, top, w, height, OK, state.hover.get(), state.select.get());
        float iconSize = height - 12.0F;
        float iconX = x + 9.0F;
        CANVAS.shape(iconX, top + 6.0F, iconSize, iconSize).radius(Theme.Radius.MD).fill(UiColor.withAlpha(OK, 0.12F))
                .border(1.0F, UiColor.withAlpha(OK, 0.35F)).draw();
        Icon.paint(CANVAS, Icons.SIGNAL, iconX + iconSize * 0.25F, top + 6.0F + iconSize * 0.25F, iconSize * 0.5F, OK);
        Font font = Minecraft.getInstance().font;
        float textX = iconX + iconSize + 10.0F;
        float maxText = Math.max(40.0F, x + w - textX - 60.0F);
        CANVAS.text("局域网世界", textX, top + 7.0F, 0xFFF0F5F6, 1.0F, false);
        CANVAS.text(trim(font, server.getMotd(), maxText / 0.875F), textX, top + 20.0F, 0xFFB7C1C4, 0.875F, false);
        boolean hideAddress = Minecraft.getInstance().options.hideServerAddress;
        CANVAS.text(hideAddress ? "地址已隐藏" : server.getAddress(), textX, top + 32.0F, 0xFF76838A, 0.75F, false);
        paintPill(x + w - 44.0F, top + (height - 13.0F) * 0.5F, 34.0F, "LAN", OK);
        CANVAS.popAlpha();
        CANVAS.end();
    }

    public static void paintLanHeader(GuiGraphics graphics, int top, int left, int width, int height) {
        CANVAS.begin(graphics);
        Font font = Minecraft.getInstance().font;
        String dots = ".".repeat((int) (UiClock.now() / 400.0 % 4.0));
        String label = "正在扫描局域网世界";
        float labelW = font.width(label) * 0.875F;
        float cy = top + height * 0.5F;
        float cx = left + width * 0.5F;
        CANVAS.shape(left + 8.0F, cy, cx - labelW * 0.5F - 18.0F - left, 1.0F)
                .horizontalGradient(0x007EC28F, 0x507EC28F).draw();
        CANVAS.shape(cx + labelW * 0.5F + 18.0F, cy, left + width - 8.0F - (cx + labelW * 0.5F + 18.0F), 1.0F)
                .horizontalGradient(0x507EC28F, 0x007EC28F).draw();
        CANVAS.text(label + dots, cx - labelW * 0.5F, cy - 4.0F, 0xFF9FAAAC, 0.875F, false);
        CANVAS.end();
    }

    /** 细滚动条：轨道是一条细线，滑块为圆角胶囊。 */
    public static void paintScrollbar(GuiGraphics graphics, boolean thumb, int x, int y, int width, int height) {
        CANVAS.begin(graphics);
        if (thumb) {
            CANVAS.shape(x + 1.0F, y + 2.0F, width - 2.0F, height - 4.0F).radius((width - 2.0F) * 0.5F).fill(0x70FFFFFF).draw();
        } else {
            CANVAS.shape(x + width * 0.5F - 0.5F, y + 2.0F, 1.0F, height - 4.0F).fill(0x1CFFFFFF).draw();
        }
        CANVAS.end();
    }

    // ==================== 公共片段 ====================

    private static void paintCard(float x, float y, float w, float h, int accent, float hover, float select) {
        float emphasis = Math.max(hover * 0.6F, select);
        CANVAS.shape(x, y, w, h).radius(Theme.Radius.LG)
                .fill(UiColor.lerp(0x8C0A0F14, 0xC4121A22, Math.max(hover, select)))
                .border(1.0F, UiColor.lerp(0x16FFFFFF, UiColor.withAlpha(accent, 0.6F), emphasis))
                .shadow(new Theme.Shadow(0.0F, 2.0F, 8.0F, 0.0F, UiColor.withAlpha(accent, 0.22F * select))).draw();
        if (select > 0.01F) {
            CANVAS.shape(x, y, w * 0.6F, h).radius(Theme.Radius.LG, 0.0F, 0.0F, Theme.Radius.LG)
                    .horizontalGradient(UiColor.withAlpha(accent, 0.16F * select), UiColor.withAlpha(accent, 0.0F)).draw();
        }
        float barH = (h - 16.0F) * (0.3F + 0.7F * Math.max(select, hover * 0.5F));
        CANVAS.shape(x + 2.0F, y + (h - barH) * 0.5F, 2.5F, barH).radius(1.25F)
                .fill(UiColor.withAlpha(accent, 0.35F + 0.65F * Math.max(select, hover))).draw();
    }

    private static void paintPill(float x, float y, float w, String text, int color) {
        CANVAS.shape(x, y, w, 13.0F).radius(6.5F).fill(UiColor.withAlpha(color, 0.14F))
                .border(1.0F, UiColor.withAlpha(color, 0.35F)).draw();
        float textW = Minecraft.getInstance().font.width(text) * 0.75F;
        CANVAS.text(text, x + (w - textW) * 0.5F, y + 3.5F, color, 0.75F, false);
    }

    private static void paintPlayHint(float right, float top, float height, int accent, float amount) {
        if (amount <= 0.01F) {
            return;
        }
        float size = 10.0F;
        Icon.paint(CANVAS, Icons.CHEVRON_RIGHT, right - 4.0F - size + amount * 2.0F - 2.0F, top + height - size - 4.0F, size,
                UiColor.withAlpha(accent, amount));
    }

    /** 五格信号：延迟越低格数越多，未连通时显示红色空格。 */
    private static void paintSignal(float x, float y, ServerData data) {
        int bars;
        int color;
        if (data.state() == ServerData.State.SUCCESSFUL) {
            long ping = data.ping;
            bars = ping < 0 ? 0 : ping < 150 ? 5 : ping < 300 ? 4 : ping < 600 ? 3 : ping < 1000 ? 2 : 1;
            color = bars >= 4 ? OK : bars >= 2 ? WARN : BAD;
        } else if (data.state() == ServerData.State.PINGING || data.state() == ServerData.State.INITIAL) {
            bars = (int) (UiClock.now() / 160.0 % 6.0);
            color = IDLE;
        } else {
            bars = 0;
            color = BAD;
        }
        for (int i = 0; i < 5; i++) {
            float bh = 3.0F + i * 2.0F;
            CANVAS.shape(x + i * 4.5F, y + 11.0F - bh, 3.0F, bh).radius(1.0F)
                    .fill(i < bars ? color : UiColor.withAlpha(color, 0.22F)).draw();
        }
        if (data.state() == ServerData.State.SUCCESSFUL && data.ping >= 0) {
            String ms = data.ping + "ms";
            float msW = Minecraft.getInstance().font.width(ms) * 0.75F;
            CANVAS.text(ms, x - msW - 5.0F, y + 3.5F, 0xFF8E99A0, 0.75F, false);
        }
    }

    private static String trim(Font font, String text, float maxWidth) {
        if (text == null) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, Math.round(maxWidth) - font.width("…"))) + "…";
    }

    private static FormattedCharSequence firstLine(Font font, Component text, float maxWidth) {
        if (text == null) {
            return null;
        }
        List<FormattedCharSequence> lines = font.split(text, Math.max(10, Math.round(maxWidth)));
        return lines.isEmpty() ? null : lines.get(0);
    }

    // ==================== 状态文案 ====================

    static String worldStatus(LevelSummary summary) {
        if (summary.isLocked()) {
            return "使用中";
        }
        if (!summary.isCompatible()) {
            return "版本不符";
        }
        if (summary.requiresManualConversion()) {
            return "需转换";
        }
        if (summary.shouldBackup()) {
            return "建议备份";
        }
        if (summary.isHardcore()) {
            return "极限";
        }
        return "可进入";
    }

    static int worldStatusColor(LevelSummary summary) {
        if (summary.isLocked() || !summary.isCompatible() || summary.requiresManualConversion()) {
            return BAD;
        }
        if (summary.shouldBackup() || summary.isHardcore()) {
            return WARN;
        }
        return WORLD_ACCENT;
    }

    static String serverStatus(ServerData data) {
        return switch (data.state()) {
            case INITIAL, PINGING -> "连接中";
            case UNREACHABLE -> "离线";
            case INCOMPATIBLE -> "版本不符";
            case SUCCESSFUL -> "在线";
        };
    }

    static int serverStatusColor(ServerData data) {
        return switch (data.state()) {
            case INITIAL, PINGING -> IDLE;
            case UNREACHABLE, INCOMPATIBLE -> BAD;
            case SUCCESSFUL -> OK;
        };
    }
}
