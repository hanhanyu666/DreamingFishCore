package com.hhy.dreamingfishcore.client.ui.notification;

import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.server.server_ui_system.network.SystemMessageKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 右上角的系统消息（进服、离开、进度、死亡），接在终端眼镜读数下方，每条一张事件卡。
 *
 * <p>卡片向左渐隐，右侧是当事玩家的头像或进度图标，上面一行小字写“发生了什么 · 谁”，
 * 下面一行是主要内容；挑战会有一道金色扫光。最新的在最上面，越旧越淡。
 * 文字画在卡片的暗底上，不加阴影。</p>
 */
final class SystemEventCards {
    private static final int MAIN = 0xFFEEE8D8;
    private static final int DIM = 0xFFC4BAA4;
    private static final int CARD = 0xCC0C0E11;
    private static final float RIGHT_MARGIN = 9.0F;
    private static final int MAX_CARDS = 4;
    private static final float CARD_HEIGHT = 19.0F;
    private static final float CARD_GAP = 2.0F;
    private static final float FADE_WIDTH = 14.0F;
    private static final float MIN_WIDTH = 96.0F;
    private static final float MAX_WIDTH = 176.0F;
    /** 右侧图标区的宽度。 */
    private static final float ICON_ZONE = 15.0F;
    private static final float CAPTION_SCALE = 0.45F;
    private static final float CAPTION_TRACKING = 0.6F;
    private static final float MAIN_SCALE = 0.66F;

    private static final Map<Notification, Parsed> PARSED = new WeakHashMap<>();

    private SystemEventCards() {
    }

    /** {@code anchorBottom} 为读数区下沿。 */
    static void render(UiCanvas canvas, Font font, int screenWidth, int anchorBottom,
                       List<NotificationManager.ActiveNotification> entries) {
        float pixel = (float) (1.0 / Minecraft.getInstance().getWindow().getGuiScale());
        float rail = screenWidth - RIGHT_MARGIN + 3.0F + pixel * 2.0F;
        float y = anchorBottom + 3.0F;
        long now = System.currentTimeMillis();
        int shown = 0;
        for (int index = entries.size() - 1; index >= 0 && shown < MAX_CARDS; index--) {
            NotificationManager.ActiveNotification entry = entries.get(index);
            Notification notification = entry.notification();
            long age = entry.ageMs(now);
            float in = easeOutCubic(range(age, 0L, 320L));
            float out = outro(notification, age);
            float alpha = in * (1.0F - out) * Math.max(0.55F, 1.0F - shown * 0.13F);
            if (alpha <= 0.01F) {
                continue;
            }
            paintCard(canvas, font, notification, parse(notification), rail, y, age, alpha,
                    (1.0F - in) * 12.0F + out * 8.0F, pixel);
            y += CARD_HEIGHT + CARD_GAP;
            shown++;
        }
    }

    private static void paintCard(UiCanvas canvas, Font font, Notification notification, Parsed parsed, float rail,
                                  float y, long age, float alpha, float slide, float pixel) {
        SystemEvent event = notification.event();
        SystemMessageKind kind = event != null ? event.kind() : null;
        int color = kind != null ? kindColor(kind) : notification.accentColor() >= 0
                ? toneColor(notification.effectiveAccentColor()) : DIM;

        // 进度卡片的主要内容是进度名称，其余是消息正文
        Component main = kind != null && kind.advancement() && !event.headline().getString().isEmpty()
                ? event.headline().copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color & 0xFFFFFF)))
                : parsed.body();
        String label = kind != null ? kindLabel(kind) : "系统";
        String who = kind != null && kind.advancement() ? event.player() : parsed.rank();
        int whoColor = kind != null && kind.advancement() ? DIM : toneColor(parsed.rankColor());
        float labelWidth = trackedWidth(font, label, CAPTION_SCALE, CAPTION_TRACKING);
        float whoWidth = who.isEmpty() ? 0.0F : trackedWidth(font, who, CAPTION_SCALE, 0.4F) + 5.0F;
        float maxMain = MAX_WIDTH - ICON_ZONE - 22.0F;
        FormattedCharSequence mainText = fit(font, main, maxMain / MAIN_SCALE);
        float mainWidth = font.width(mainText) * MAIN_SCALE;
        float width = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, Math.max(labelWidth + whoWidth, mainWidth) + ICON_ZONE + 22.0F));
        float x = rail - width;

        canvas.push();
        canvas.translate(slide, 0.0F);
        canvas.pushAlpha(alpha);
        // 底：左端一小段从透明渐变出来，其余是实色暗底，文字始终落在暗底上
        canvas.shape(x, y, FADE_WIDTH, CARD_HEIGHT).radius(4.0F, 0.0F, 0.0F, 4.0F)
                .horizontalGradient(UiColor.withAlpha(CARD, 0), CARD).draw();
        canvas.fill(x + FADE_WIDTH, y, width - FADE_WIDTH, CARD_HEIGHT, CARD);
        canvas.shape(x + width * 0.35F, y, width * 0.65F, pixel * 2.0F)
                .horizontalGradient(UiColor.withAlpha(color, 0), UiColor.withAlpha(color, 0.7F)).draw();
        canvas.fill(rail - 1.5F, y, 1.5F, CARD_HEIGHT, UiColor.withAlpha(color, 0.95F));

        if (kind == SystemMessageKind.CHALLENGE) {
            // 挑战：一道金色扫光掠过卡片
            float sweep = range(age, 280L, 1300L);
            if (sweep > 0.0F && sweep < 1.0F) {
                canvas.pushClip(x, y, width, CARD_HEIGHT, 0.0F);
                float sx = x + (width + 26.0F) * smooth(sweep) - 26.0F;
                canvas.shape(sx, y, 13.0F, CARD_HEIGHT).horizontalGradient(0x00FFE6A6, 0x3CFFE6A6).draw();
                canvas.shape(sx + 13.0F, y, 13.0F, CARD_HEIGHT).horizontalGradient(0x3CFFE6A6, 0x00FFE6A6).draw();
                canvas.popClip();
            }
        }

        float iconX = rail - 4.0F - ICON_ZONE / 2.0F;
        float iconY = y + CARD_HEIGHT / 2.0F;
        paintIcon(canvas, event, kind, color, iconX, iconY, age, pixel);

        float textRight = iconX - ICON_ZONE / 2.0F - 3.0F;
        float captionY = y + 3.0F;
        float cursor = textRight;
        if (!who.isEmpty()) {
            cursor -= trackedWidth(font, who, CAPTION_SCALE, 0.4F);
            drawTracked(canvas, font, who, cursor, captionY, CAPTION_SCALE, 0.4F, whoColor);
            cursor -= 2.5F;
            canvas.circle(cursor, captionY + 2.0F, 0.55F, UiColor.withAlpha(DIM, 0.8F));
            cursor -= 2.5F;
        }
        drawTracked(canvas, font, label, cursor - labelWidth, captionY, CAPTION_SCALE, CAPTION_TRACKING, color);
        canvas.text(mainText, textRight - mainWidth, y + 9.5F, MAIN, MAIN_SCALE, false);
        canvas.popAlpha();
        canvas.pop();
    }

    /** 卡片右侧的图标：进度画物品（外框按进度/目标/挑战区分），其余画当事玩家的头像。 */
    private static void paintIcon(UiCanvas canvas, SystemEvent event, SystemMessageKind kind, int color,
                                  float cx, float cy, long age, float pixel) {
        float pop = easeOutBack(range(age, 100L, 480L));
        if (pop <= 0.05F) {
            return;
        }
        float line = pixel * 2.0F;
        if (event == null) {
            canvas.arc(cx, cy, 3.6F * pop, line, 0.0F, (float) (Math.PI * 2.0), UiColor.withAlpha(color, 0.8F),
                    UiColor.withAlpha(color, 0.8F));
            canvas.circle(cx, cy, 1.2F * pop, color);
            return;
        }
        if (kind.advancement() && !event.icon().isEmpty()) {
            float half = 6.0F * pop;
            int frame = UiColor.withAlpha(color, 0.85F);
            switch (kind) {
                case CHALLENGE -> {
                    canvas.shape(cx - 9.0F, cy - 9.0F, 18.0F, 18.0F)
                            .radial(UiColor.withAlpha(color, 0.4F), UiColor.withAlpha(color, 0), 9.0F, 9.0F, 9.0F).draw();
                    float d = half + 1.2F;
                    canvas.line(cx, cy - d, cx + d, cy, line, frame, true);
                    canvas.line(cx + d, cy, cx, cy + d, line, frame, true);
                    canvas.line(cx, cy + d, cx - d, cy, line, frame, true);
                    canvas.line(cx - d, cy, cx, cy - d, line, frame, true);
                    for (int i = 0; i < 3; i++) {
                        double a = age / 1000.0 * 1.4 + i * Math.PI * 2.0 / 3.0;
                        float twinkle = 0.5F + 0.5F * (float) Math.sin(age / 260.0 + i * 2.0);
                        canvas.circle(cx + (float) Math.cos(a) * 8.0F, cy + (float) Math.sin(a) * 8.0F, 0.5F,
                                UiColor.withAlpha(0xFFFFF1C8, 0.8F * twinkle));
                    }
                }
                case GOAL -> canvas.shape(cx - half - 0.5F, cy - half - 0.5F, half * 2.0F + 1.0F, half * 2.0F + 1.0F)
                        .radius(half + 0.5F).fill(0x700C0E11).border(line, frame).draw();
                default -> canvas.shape(cx - half - 0.5F, cy - half - 0.5F, half * 2.0F + 1.0F, half * 2.0F + 1.0F)
                        .radius(2.0F).fill(0x700C0E11).border(line, frame).draw();
            }
            float size = 9.0F * pop;
            canvas.item(event.icon(), cx - size / 2.0F, cy - size / 2.0F, size, false);
            return;
        }
        int tint = kind == SystemMessageKind.LEAVE ? 0x99B4B4B4 : kind == SystemMessageKind.DEATH ? 0xFFC77A6E : 0xFFFFFFFF;
        float size = 10.0F * pop;
        canvas.shape(cx - size / 2.0F - 1.0F, cy - size / 2.0F - 1.0F, size + 2.0F, size + 2.0F).radius(2.5F)
                .fill(0x900C0E11).border(line, UiColor.withAlpha(color, 0.7F)).draw();
        canvas.playerFace(skin(event.playerId(), event.player()), cx - size / 2.0F, cy - size / 2.0F, size, 1.5F, tint);
        if (kind == SystemMessageKind.DEATH) {
            float k = size * 0.32F;
            canvas.line(cx - k, cy - k, cx + k, cy + k, 0.9F, UiColor.withAlpha(color, 0.85F), true);
            canvas.line(cx + k, cy - k, cx - k, cy + k, 0.9F, UiColor.withAlpha(color, 0.85F), true);
        }
        if (kind == SystemMessageKind.JOIN) {
            canvas.circle(cx + size / 2.0F, cy + size / 2.0F, 1.6F, 0xFF0C0E11);
            canvas.circle(cx + size / 2.0F, cy + size / 2.0F, 1.0F, color);
        }
    }

    private static ResourceLocation skin(UUID playerId, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PlayerInfo info = playerId != null ? mc.getConnection().getPlayerInfo(playerId) : null;
            if (info == null && !name.isEmpty()) {
                info = mc.getConnection().getPlayerInfo(name);
            }
            if (info != null) {
                return info.getSkin().texture();
            }
        }
        return DefaultPlayerSkin.get(playerId != null ? playerId
                : UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8))).texture();
    }

    private static String kindLabel(SystemMessageKind kind) {
        return switch (kind) {
            case JOIN -> "进服";
            case LEAVE -> "离开";
            case TASK -> "进度";
            case GOAL -> "目标";
            case CHALLENGE -> "挑战";
            case DEATH -> "阵亡";
        };
    }

    private static int kindColor(SystemMessageKind kind) {
        return switch (kind) {
            case JOIN -> 0xFF78E6AA;
            case LEAVE -> 0xFFB5AD9C;
            case TASK -> 0xFF8FD6A0;
            case GOAL -> 0xFF7FC8E8;
            case CHALLENGE -> 0xFFF2CF7A;
            case DEATH -> 0xFFE07060;
        };
    }

    /** 主要内容超出卡片宽度时截断并补省略号。 */
    private static FormattedCharSequence fit(Font font, Component text, float maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text.getVisualOrderText();
        }
        FormattedText cut = font.substrByWidth(text, Math.max(0, (int) maxWidth - font.width("…")));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of("…")));
    }

    // ==================== 解析 ====================

    /** 一条消息拆成：行首的 Rank 标签与其颜色、去掉标签和“[+]/[-]”之后按镜片配色调和过的正文。 */
    record Parsed(String rank, int rankColor, Component body) {
    }

    private record Run(String text, Style style) {
    }

    private static Parsed parse(Notification notification) {
        synchronized (PARSED) {
            return PARSED.computeIfAbsent(notification, ignored -> parse(notification.message()));
        }
    }

    static Parsed parse(Component message) {
        List<Run> runs = runs(message);
        StringBuilder plainBuilder = new StringBuilder();
        for (Run run : runs) {
            plainBuilder.append(run.text());
        }
        String plain = plainBuilder.toString();
        int skip = plain.startsWith("[+]") || plain.startsWith("[-]") ? 3 : 0;
        String rank = "";
        int rankColor = DIM;
        String rest = plain.substring(skip);
        if (rest.startsWith("[")) {
            int close = rest.indexOf(']');
            if (close > 1 && close < 26) {
                rank = rest.substring(1, close).trim();
                rankColor = colorAt(runs, skip + 1);
                skip += close + 1;
                while (skip < plain.length() && plain.charAt(skip) == ' ') {
                    skip++;
                }
            }
        }
        MutableComponent body = Component.empty();
        int position = 0;
        for (Run run : runs) {
            String text = run.text();
            int start = position;
            position += text.length();
            if (position <= skip) {
                continue;
            }
            String kept = start >= skip ? text : text.substring(skip - start);
            body.append(Component.literal(kept).withStyle(tone(run.style())));
        }
        return new Parsed(rank, rankColor, body);
    }

    /** 按样式切成若干段；文字里的 § 格式码在这里展开成样式。 */
    private static List<Run> runs(Component component) {
        List<Run> result = new ArrayList<>();
        component.visit((style, text) -> {
            StringBuilder run = new StringBuilder();
            Style[] current = {null};
            StringDecomposer.iterateFormatted(text, style, (index, charStyle, codePoint) -> {
                if (current[0] != null && !charStyle.equals(current[0])) {
                    result.add(new Run(run.toString(), current[0]));
                    run.setLength(0);
                }
                current[0] = charStyle;
                run.appendCodePoint(codePoint);
                return true;
            });
            if (!run.isEmpty()) {
                result.add(new Run(run.toString(), current[0]));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private static int colorAt(List<Run> runs, int charIndex) {
        int position = 0;
        for (Run run : runs) {
            if (charIndex < position + run.text().length()) {
                TextColor color = run.style().getColor();
                return color == null ? DIM : 0xFF000000 | color.getValue();
            }
            position += run.text().length();
        }
        return DIM;
    }

    /** 服务端配置的 § 颜色往骨白色靠一些，过暗的颜色（深紫、深蓝）提亮到能看清。 */
    private static Style tone(Style style) {
        TextColor color = style.getColor();
        return color == null ? style : style.withColor(TextColor.fromRgb(toneColor(0xFF000000 | color.getValue()) & 0xFFFFFF));
    }

    static int toneColor(int color) {
        int mixed = UiColor.lerp(color | 0xFF000000, MAIN, 0.26F);
        float luminance = (0.299F * UiColor.red(mixed) + 0.587F * UiColor.green(mixed) + 0.114F * UiColor.blue(mixed)) / 255.0F;
        if (luminance < 0.62F) {
            mixed = UiColor.lerp(mixed, 0xFFFFFFFF, Math.min(0.7F, (0.62F - luminance) * 1.4F));
        }
        return mixed;
    }

    // ==================== 工具 ====================

    private static float outro(Notification notification, long age) {
        long duration = notification.durationMs();
        if (duration >= Long.MAX_VALUE / 2L) {
            return 0.0F;
        }
        float t = range(age, duration - 360L, duration);
        return t * t * t;
    }

    private static float trackedWidth(Font font, String text, float scale, float tracking) {
        int count = text.codePointCount(0, text.length());
        return font.width(text) * scale + tracking * Math.max(0, count - 1);
    }

    /** 逐字绘制以拉开字距（不加阴影）。 */
    private static void drawTracked(UiCanvas canvas, Font font, String text, float x, float y, float scale,
                                    float tracking, int color) {
        float cursor = x;
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            String glyph = new String(Character.toChars(codePoint));
            canvas.text(glyph, cursor, y, color, scale, false);
            cursor += font.width(glyph) * scale + tracking;
            index += Character.charCount(codePoint);
        }
    }

    private static float range(long t, long start, long end) {
        return Math.max(0.0F, Math.min(1.0F, (t - start) / (float) Math.max(1L, end - start)));
    }

    private static float easeOutCubic(float v) {
        float inv = 1.0F - v;
        return 1.0F - inv * inv * inv;
    }

    private static float smooth(float v) {
        return v * v * (3.0F - 2.0F * v);
    }

    private static float easeOutBack(float v) {
        float c1 = 1.6F;
        float c3 = c1 + 1.0F;
        float x = v - 1.0F;
        return 1.0F + c3 * x * x * x + c1 * x * x;
    }
}
