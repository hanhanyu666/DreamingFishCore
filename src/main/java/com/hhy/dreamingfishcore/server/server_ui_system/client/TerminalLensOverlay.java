package com.hhy.dreamingfishcore.server.server_ui_system.client;

import com.google.common.collect.Ordering;
import com.hhy.dreamingfishcore.client.ui.render.GuiQuadBatchRenderer;
import com.hhy.dreamingfishcore.client.ui.render.RetainedGuiBuffers;
import com.hhy.dreamingfishcore.client.ui.render.RetainedPlayerFace;
import com.hhy.dreamingfishcore.gameplay.playerlevel_system.overalllevel.PlayerLevelManager;
import com.hhy.dreamingfishcore.server.rank_system.Rank;
import com.hhy.dreamingfishcore.server.rank_system.RankRegistry;
import com.hhy.dreamingfishcore.server.title_system.PlayerTitleManager;
import com.hhy.dreamingfishcore.server.title_system.Title;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import org.joml.Matrix4f;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 右上角「终端眼镜」：玩家终端投射在镜片上的状态读数。
 *
 * <p>右上角有一层镜片暗角，信息收在细线取景括号里；大号时钟为视觉重心，服务器 TPS
 * 以信号格表示，等级为分段进度条，状态效果为带倒计时圆环的图标。文字是暖色骨白，
 * 带轻微辉光与色散，模拟投射在镜片上的质感。</p>
 *
 * <p>除状态效果外的全部几何与文字都录成常驻顶点缓冲，只在时间、在线人数、等级等内容变化时重建；
 * 状态效果的圆环与图标每帧绘制。</p>
 */
final class TerminalLensOverlay {
    private static final int MAIN = 0xEEE8D8;
    private static final int ACCENT = 0xE8C482;
    private static final int DIM = 0xC4BAA4;
    private static final int LENS_TINT = 0x120E08;
    private static final int ONLINE_DOT = 0x78E6AA;
    private static final int TITLE_FALLBACK = 0xECD6A0;
    private static final int WARN = 0xE6A74B;
    private static final int DANGER = 0xE2503E;
    private static final int ABERRATION_RED = 0xFF5A5A;
    private static final int ABERRATION_CYAN = 0x5AD2FF;

    private static final float RIGHT_MARGIN = 9.0F;
    /** 镜片暗角覆盖右上角的范围（GUI 像素）与最深处的透明度。 */
    private static final float LENS_WIDTH = 230.0F;
    private static final float LENS_HEIGHT = 120.0F;
    private static final int LENS_COLUMNS = 24;
    private static final int LENS_ROWS = 12;
    private static final float LENS_MAX_ALPHA = 180.0F;
    /** 原版字体基线距字形顶部的距离；排版以基线对齐。 */
    private static final float FONT_ASCENT = 7.0F;

    private static final float SCALE_MICRO = 0.47F;
    private static final float SCALE_ONLINE = 0.56F;
    private static final float SCALE_DATE = 0.53F;
    private static final float SCALE_CLOCK = 1.56F;
    private static final float SCALE_TITLE = 0.66F;
    private static final float SCALE_NAME = 0.72F;
    private static final float SCALE_LEVEL = 0.75F;
    private static final float SCALE_LEVEL_LABEL = 0.44F;

    private static final int LEVEL_SEGMENTS = 20;
    private static final float SEGMENT_WIDTH = 2.25F;
    private static final float SEGMENT_GAP = 0.75F;
    private static final int EFFECTS_PER_ROW = 8;
    private static final float EFFECT_STEP = 13.0F;
    private static final float EFFECT_RADIUS = 5.2F;
    private static final float EFFECT_ICON = 7.0F;
    private static final float EFFECT_TOP = 57.0F;
    private static final int RING_SEGMENTS = 36;
    private static final float LOW_TPS = 18.0F;
    private static final long EFFECT_ORDER_CACHE_INTERVAL = 250L;
    private static final String[] WEEKDAYS = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};

    private static Cache cache;
    private static volatile boolean dirty = true;
    private static long lastClockSecond = Long.MIN_VALUE;
    private static String clockDate = "";
    private static String clockWeekday = "";
    private static String clockTime = "";
    private static List<MobEffectInstance> orderedEffects = List.of();
    private static int lastActiveEffectCount;
    private static long lastEffectOrderUpdate = Long.MIN_VALUE;
    private static final Map<Holder<MobEffect>, Integer> LONGEST_DURATIONS = new HashMap<>();

    private TerminalLensOverlay() {
    }

    /** 系统消息从读数区下方开始排列。 */
    record Anchor(int y, int height) {
    }

    /** 资源重载、进出服务器后下一帧重建常驻缓冲。 */
    static void invalidate() {
        dirty = true;
    }

    static Anchor render(GuiGraphics graphics, Minecraft mc, Font font, int level, Rank rank) {
        List<MobEffectInstance> effects = visibleEffects(mc);
        int effectRows = (effects.size() + EFFECTS_PER_ROW - 1) / EFFECTS_PER_ROW;
        Key key = key(mc, font, level, rank, effectRows);
        Matrix4f pose = graphics.pose().last().pose();
        Cache current = cache;
        if (dirty || current == null || !current.key().equals(key) || !current.pose().equals(pose)) {
            current = rebuild(font, key, pose, current);
        }

        // 常驻部分不再产生普通 GUI 几何；先提交之前排队的内容，再绘制保留缓冲。
        graphics.flush();
        current.buffers().draw();
        current.avatar().draw();
        if (!effects.isEmpty()) {
            renderEffects(graphics, mc, key.screenWidth() - RIGHT_MARGIN, effects, (float) (1.0D / key.guiScale()));
        }
        return current.anchor();
    }

    private static Key key(Minecraft mc, Font font, int level, Rank rank, int effectRows) {
        updateClock();
        float tps = ServerInformationDisplay.getClientTps(mc);
        Title title = PlayerTitleManager.getPlayerTitleClient(mc.player);
        String rankName = rank == null ? null : rank.getRankName();
        if (rankName == null || rankName.isBlank()
                || RankRegistry.NO_RANK.getRankName().equalsIgnoreCase(rankName)) {
            rankName = null;
        }
        return new Key(font, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScale(),
                ServerInformationDisplay.ONLINE_PLAYERS, clockDate, clockWeekday, clockTime,
                signalLevel(tps), Float.isFinite(tps) && tps < LOW_TPS
                ? ServerInformationDisplay.getServerTpsText(mc) : null,
                mc.player.getGameProfile().getName(),
                title == null ? "" : title.getTitleName(),
                title == null ? TITLE_FALLBACK : title.getColor() & 0xFFFFFF,
                rankName, rank == null ? 0 : rank.getRankColor() & 0xFFFFFF,
                level, PlayerLevelManager.getPlayerExperienceClient(mc.player),
                PlayerLevelManager.getExperienceNeededForNextLevelClient(mc.player),
                mc.player.getSkin().texture(), effectRows);
    }

    /** 服务器 TPS 换算为四格信号：满 20 为四格，卡顿时逐格减少，未知时不亮。 */
    private static int signalLevel(float tps) {
        if (!Float.isFinite(tps)) {
            return 0;
        }
        if (tps >= LOW_TPS) {
            return 4;
        }
        if (tps >= 15.0F) {
            return 3;
        }
        return tps >= 10.0F ? 2 : 1;
    }

    private static void updateClock() {
        long second = System.currentTimeMillis() / 1000L;
        if (second == lastClockSecond) {
            return;
        }
        lastClockSecond = second;
        LocalDateTime now = LocalDateTime.now();
        String date = String.format(Locale.ROOT, "%04d.%02d.%02d",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        String time = String.format(Locale.ROOT, "%02d:%02d", now.getHour(), now.getMinute());
        String weekday = WEEKDAYS[now.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue()];
        // 只在内容真正变化时替换引用，常驻缓冲按分钟而不是按秒重建。
        if (!date.equals(clockDate)) {
            clockDate = date;
        }
        if (!time.equals(clockTime)) {
            clockTime = time;
        }
        if (!weekday.equals(clockWeekday)) {
            clockWeekday = weekday;
        }
    }

    private static Cache rebuild(Font font, Key key, Matrix4f livePose, Cache previous) {
        Matrix4f base = new Matrix4f(livePose);
        float pixel = (float) (1.0D / key.guiScale());
        float right = key.screenWidth() - RIGHT_MARGIN;
        float bracketBottom = key.effectRows() > 0 ? 60.0F + EFFECT_STEP * (key.effectRows() - 1) : 51.0F;

        RetainedGuiBuffers buffers;
        int avatarX;
        try (RetainedGuiBuffers.Capture capture = new RetainedGuiBuffers.Capture()) {
            // 先申请普通几何缓冲：常驻缓冲按渲染类型首次出现的顺序绘制，文字因此总在几何之上。
            VertexConsumer gui = capture.getBuffer(RenderType.gui());
            Painter p = new Painter(capture, gui, font, base, pixel);

            lens(gui, base, key.screenWidth());
            bracket(p, right, bracketBottom);
            header(p, key, right);

            float clockWidth = p.text(key.time(), right, 24.0F, SCALE_CLOCK, MAIN, 255, 0.0F, true);
            p.text(key.date(), right - clockWidth - 3.0F, 17.5F, SCALE_DATE, DIM, 255, 0.2F, false);
            p.text(key.weekday(), right - clockWidth - 3.0F, 23.5F, SCALE_DATE, DIM, 255, 0.0F, false);

            // 刻度分隔线：每三格一根长刻度。
            p.quad(right - 108.0F, 27.5F, right, 27.5F + pixel, ACCENT, 90);
            for (int tick = 0; tick <= 12; tick++) {
                float x = right - tick * 9.0F;
                p.quad(x - pixel, 27.5F, x, tick % 3 == 0 ? 29.5F : 28.5F, ACCENT, 150);
            }

            avatarX = identity(p, key, right);
            level(p, key, right);
            buffers = capture.upload();
        }

        RetainedPlayerFace avatar;
        try {
            avatar = RetainedPlayerFace.create(key.skin(), base, avatarX + 1, 32, 7);
        } catch (Throwable throwable) {
            buffers.close();
            throw throwable;
        }
        if (previous != null) {
            previous.buffers().close();
            previous.avatar().close();
        }
        Cache rebuilt = new Cache(key, base, buffers, avatar,
                new Anchor(4, Math.round(bracketBottom)));
        cache = rebuilt;
        dirty = false;
        return rebuilt;
    }

    /** 镜片边缘的暗角：从右上角向内渐隐，保证亮背景下读数清晰，也是“透过镜片看”的提示。 */
    private static void lens(VertexConsumer gui, Matrix4f pose, int screenWidth) {
        float left = screenWidth - LENS_WIDTH;
        float cellWidth = LENS_WIDTH / LENS_COLUMNS;
        float cellHeight = LENS_HEIGHT / LENS_ROWS;
        for (int row = 0; row < LENS_ROWS; row++) {
            for (int column = 0; column < LENS_COLUMNS; column++) {
                float x0 = left + column * cellWidth;
                float y0 = row * cellHeight;
                float x1 = x0 + cellWidth;
                float y1 = y0 + cellHeight;
                int a00 = lensAlpha(screenWidth, x0, y0);
                int a01 = lensAlpha(screenWidth, x0, y1);
                int a11 = lensAlpha(screenWidth, x1, y1);
                int a10 = lensAlpha(screenWidth, x1, y0);
                if ((a00 | a01 | a11 | a10) == 0) {
                    continue;
                }
                gui.addVertex(pose, x0, y0, 0.0F).setColor(a00 << 24 | LENS_TINT);
                gui.addVertex(pose, x0, y1, 0.0F).setColor(a01 << 24 | LENS_TINT);
                gui.addVertex(pose, x1, y1, 0.0F).setColor(a11 << 24 | LENS_TINT);
                gui.addVertex(pose, x1, y0, 0.0F).setColor(a10 << 24 | LENS_TINT);
            }
        }
    }

    private static int lensAlpha(int screenWidth, float x, float y) {
        float dx = (screenWidth - x) / LENS_WIDTH;
        float dy = y / LENS_HEIGHT;
        float distance = (float) Math.sqrt(dx * 0.85F * dx * 0.85F + dy * 1.15F * dy * 1.15F);
        float falloff = Math.max(0.0F, 1.0F - distance);
        return Math.round((float) Math.pow(falloff, 1.4D) * LENS_MAX_ALPHA);
    }

    /** 取景括号：右侧竖线、上下短横与中段刻口。 */
    private static void bracket(Painter p, float right, float bottom) {
        float line = p.pixel * 2.0F;
        p.quad(right + 3.0F, 4.0F, right + 3.0F + line, bottom, ACCENT, 120);
        p.quad(right - 3.0F, 4.0F, right + 3.0F + line, 4.0F + line, ACCENT, 170);
        p.quad(right - 3.0F, bottom - line, right + 3.0F + line, bottom, ACCENT, 170);
        float middle = (4.0F + bottom) / 2.0F;
        p.quad(right + 2.5F, middle - 1.5F, right + 4.0F, middle + 1.5F, ACCENT, 220);
    }

    /** 顶行：设备名、信号格（服务器 TPS）与在线人数；TPS 偏低时补充数值。 */
    private static void header(Painter p, Key key, float right) {
        float onlineWidth = p.text(Integer.toString(key.online()), right, 9.0F,
                SCALE_ONLINE, MAIN, 255, 0.3F, false);
        p.quad(right - onlineWidth - 3.2F, 6.6F, right - onlineWidth - 1.7F, 8.1F, ONLINE_DOT, 240);

        // 尚未收到 TPS 样本时信号格全暗且保持中性色，不误报卡顿。
        int signalColor = key.signal() == 0 ? DIM
                : key.signal() <= 2 ? DANGER : key.signal() == 3 ? WARN : ACCENT;
        float x = right - onlineWidth - 6.0F;
        for (int bar = 3; bar >= 0; bar--) {
            x -= 1.5F;
            float height = 1.5F + bar * 1.2F;
            p.quad(x, 9.0F - height, x + 1.0F, 9.0F, signalColor, bar < key.signal() ? 230 : 60);
            x -= 0.75F;
        }
        float cursor = x - 1.25F;
        if (key.lowTps() != null) {
            cursor -= p.text(key.lowTps(), cursor, 9.0F, SCALE_MICRO, signalColor, 255, 0.2F, false) + 3.0F;
        }
        p.text("DREAMINGFISH  LINK", cursor, 9.0F, SCALE_MICRO, DIM, 255, 0.9F, false);
    }

    /** 身份行：头像取景框、名字、称号与描边的 Rank 标签。返回头像取景框的左边缘。 */
    private static int identity(Painter p, Key key, float right) {
        float cursor = right;
        if (key.rank() != null) {
            float rankWidth = p.width(key.rank(), SCALE_MICRO, 0.6F);
            p.text(key.rank(), right - 2.0F, 38.0F, SCALE_MICRO, key.rankColor(), 255, 0.6F, false);
            p.outline(right - rankWidth - 4.0F, 32.25F, right + 0.5F, 39.5F, key.rankColor(), 160);
            cursor = right - rankWidth - 7.0F;
        }
        if (!key.title().isEmpty()) {
            cursor -= p.text(key.title(), cursor, 38.5F, SCALE_TITLE, key.titleColor(), 255, 0.0F, true) + 1.5F;
            cursor -= p.text("·", cursor, 38.5F, SCALE_TITLE, DIM, 255, 0.0F, false) + 1.5F;
        }
        cursor -= p.text(key.name(), cursor, 38.5F, SCALE_NAME, MAIN, 255, 0.0F, true);

        int frameLeft = Math.round(cursor - 11.0F);
        float line = p.pixel;
        float[][] corners = {{frameLeft, 31, 1, 1}, {frameLeft + 9, 31, -1, 1},
                {frameLeft, 40, 1, -1}, {frameLeft + 9, 40, -1, -1}};
        for (float[] corner : corners) {
            float x = corner[0];
            float y = corner[1];
            p.quad(Math.min(x, x + corner[2] * 2.0F), y, Math.max(x, x + corner[2] * 2.0F) + line, y + line, ACCENT, 200);
            p.quad(x, Math.min(y, y + corner[3] * 2.0F), x + line, Math.max(y, y + corner[3] * 2.0F) + line, ACCENT, 200);
        }
        return frameLeft;
    }

    /** 等级行：LV 数字、二十格分段进度条与经验数值。 */
    private static void level(Painter p, Key key, float right) {
        float experienceWidth = p.text(key.experience() + " / " + key.nextExperience(), right, 48.5F,
                SCALE_MICRO, DIM, 255, 0.3F, false);
        float barRight = right - experienceWidth - 3.0F;
        float progress = key.nextExperience() > 0L
                ? Mth.clamp(key.experience() / (float) key.nextExperience(), 0.0F, 1.0F)
                : 0.0F;
        float filled = LEVEL_SEGMENTS * progress;
        float step = SEGMENT_WIDTH + SEGMENT_GAP;
        for (int index = 0; index < LEVEL_SEGMENTS; index++) {
            float x = barRight - (LEVEL_SEGMENTS - index) * step + SEGMENT_GAP;
            boolean on = index < (int) filled;
            p.quad(x, 45.5F, x + SEGMENT_WIDTH, 47.5F, on ? ACCENT : DIM, on ? 230 : 55);
            if (index == (int) filled && filled < LEVEL_SEGMENTS) {
                p.quad(x, 45.5F, x + SEGMENT_WIDTH * (filled - (int) filled), 47.5F, ACCENT, 230);
            }
        }
        float levelX = barRight - LEVEL_SEGMENTS * step - 2.0F;
        float levelWidth = p.text(Integer.toString(key.level()), levelX, 49.0F, SCALE_LEVEL, MAIN, 255, 0.0F, true);
        p.text("LV", levelX - levelWidth - 1.0F, 48.5F, SCALE_LEVEL_LABEL, DIM, 255, 0.4F, false);
    }

    private static List<MobEffectInstance> visibleEffects(Minecraft mc) {
        Collection<MobEffectInstance> active = mc.player.getActiveEffects();
        if (active.isEmpty()) {
            if (!orderedEffects.isEmpty() || lastActiveEffectCount != 0) {
                orderedEffects = List.of();
                lastActiveEffectCount = 0;
                LONGEST_DURATIONS.clear();
            }
            return orderedEffects;
        }
        long now = System.currentTimeMillis();
        if (active.size() != lastActiveEffectCount || lastEffectOrderUpdate == Long.MIN_VALUE
                || now - lastEffectOrderUpdate >= EFFECT_ORDER_CACHE_INTERVAL) {
            orderedEffects = Ordering.<MobEffectInstance>natural().reverse().sortedCopy(active).stream()
                    .filter(effect -> effect.showIcon() && IClientMobEffectExtensions.of(effect).isVisibleInGui(effect))
                    .toList();
            lastActiveEffectCount = active.size();
            lastEffectOrderUpdate = now;
            if (LONGEST_DURATIONS.size() > active.size()) {
                Set<Holder<MobEffect>> present = new HashSet<>();
                for (MobEffectInstance effect : active) {
                    present.add(effect.getEffect());
                }
                LONGEST_DURATIONS.keySet().retainAll(present);
            }
        }
        return orderedEffects;
    }

    /** 状态效果：细圆环表示剩余时间（以本次观察到的最长时长为满），图标居中。 */
    private static void renderEffects(GuiGraphics graphics, Minecraft mc, float right,
                                      List<MobEffectInstance> effects, float pixel) {
        graphics.drawManaged(() -> {
            VertexConsumer gui = graphics.bufferSource().getBuffer(RenderType.gui());
            Matrix4f pose = graphics.pose().last().pose();
            for (int index = 0; index < effects.size(); index++) {
                MobEffectInstance effect = effects.get(index);
                float cx = effectX(right, index);
                float cy = effectY(index);
                float alpha = effectAlpha(effect);
                ring(gui, pose, cx, cy, 1.0F, pixel * 2.0F, Math.round(60 * alpha));
                ring(gui, pose, cx, cy, remaining(effect), pixel * 3.0F,
                        Math.round((effect.isAmbient() ? 140 : 230) * alpha));
            }
        });

        // 第三方效果可能自绘图标，保留它们的渲染器；其余图标合并为一次图集提交。
        float iconScale = EFFECT_ICON / 18.0F;
        boolean[] handled = new boolean[effects.size()];
        RenderSystem.enableBlend();
        try {
            for (int index = 0; index < effects.size(); index++) {
                MobEffectInstance effect = effects.get(index);
                IClientMobEffectExtensions renderer = IClientMobEffectExtensions.of(effect);
                if (renderer == IClientMobEffectExtensions.DEFAULT) {
                    continue;
                }
                graphics.pose().pushPose();
                graphics.pose().translate(effectX(right, index) - EFFECT_ICON / 2.0F - 3.0F * iconScale,
                        effectY(index) - EFFECT_ICON / 2.0F - 3.0F * iconScale, 0.0F);
                graphics.pose().scale(iconScale, iconScale, 1.0F);
                handled[index] = renderer.renderGuiIcon(effect, mc.gui, graphics, 0, 0, 0, effectAlpha(effect));
                graphics.pose().popPose();
            }
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }

        Matrix4f pose = graphics.pose().last().pose();
        BufferBuilder icons = GuiQuadBatchRenderer.begin();
        ResourceLocation atlas = null;
        for (int index = 0; index < effects.size(); index++) {
            if (handled[index]) {
                continue;
            }
            MobEffectInstance effect = effects.get(index);
            TextureAtlasSprite icon = mc.getMobEffectTextures().get(effect.getEffect());
            atlas = icon.atlasLocation();
            GuiQuadBatchRenderer.addSprite(icons, pose, icon,
                    effectX(right, index) - EFFECT_ICON / 2.0F, effectY(index) - EFFECT_ICON / 2.0F,
                    EFFECT_ICON, EFFECT_ICON, effectAlpha(effect));
        }
        if (atlas != null) {
            GuiQuadBatchRenderer.draw(icons, atlas);
        } else {
            icons.build();
        }
    }

    private static float effectX(float right, int index) {
        return right - 5.5F - (index % EFFECTS_PER_ROW) * EFFECT_STEP;
    }

    private static float effectY(int index) {
        return EFFECT_TOP + (index / EFFECTS_PER_ROW) * EFFECT_STEP;
    }

    private static float remaining(MobEffectInstance effect) {
        if (effect.isInfiniteDuration()) {
            return 1.0F;
        }
        int duration = effect.getDuration();
        Integer longest = LONGEST_DURATIONS.get(effect.getEffect());
        if (longest == null || duration > longest) {
            LONGEST_DURATIONS.put(effect.getEffect(), duration);
            return 1.0F;
        }
        return longest <= 0 ? 0.0F : Mth.clamp(duration / (float) longest, 0.0F, 1.0F);
    }

    /** 与原版一致：即将结束的效果闪烁。 */
    private static float effectAlpha(MobEffectInstance effect) {
        if (effect.isAmbient() || !effect.endsWithin(200)) {
            return 1.0F;
        }
        int duration = effect.getDuration();
        int pulseStep = 10 - duration / 20;
        return Mth.clamp(duration / 10.0F / 5.0F * 0.5F, 0.0F, 0.5F)
                + Mth.cos(duration * (float) Math.PI / 5.0F)
                * Mth.clamp(pulseStep / 10.0F * 0.25F, 0.0F, 0.25F);
    }

    /** 从正上方顺时针画出 {@code progress} 比例的圆环。 */
    private static void ring(VertexConsumer gui, Matrix4f pose, float cx, float cy, float progress,
                             float thickness, int alpha) {
        int segments = Math.round(RING_SEGMENTS * Mth.clamp(progress, 0.0F, 1.0F));
        if (segments <= 0 || alpha <= 0) {
            return;
        }
        float inner = EFFECT_RADIUS - thickness / 2.0F;
        float outer = EFFECT_RADIUS + thickness / 2.0F;
        int color = Math.min(255, alpha) << 24 | ACCENT;
        for (int segment = 0; segment < segments; segment++) {
            double a0 = Math.PI * 2.0D * segment / RING_SEGMENTS;
            double a1 = Math.PI * 2.0D * (segment + 1) / RING_SEGMENTS;
            float s0 = (float) Math.sin(a0);
            float c0 = (float) -Math.cos(a0);
            float s1 = (float) Math.sin(a1);
            float c1 = (float) -Math.cos(a1);
            gui.addVertex(pose, cx + s0 * outer, cy + c0 * outer, 0.0F).setColor(color);
            gui.addVertex(pose, cx + s0 * inner, cy + c0 * inner, 0.0F).setColor(color);
            gui.addVertex(pose, cx + s1 * inner, cy + c1 * inner, 0.0F).setColor(color);
            gui.addVertex(pose, cx + s1 * outer, cy + c1 * outer, 0.0F).setColor(color);
        }
    }

    /** 录制常驻几何与全息文字的绘制器。 */
    private static final class Painter {
        private final RetainedGuiBuffers.Capture capture;
        private final VertexConsumer gui;
        private final Font font;
        private final Matrix4f base;
        private final float pixel;

        private Painter(RetainedGuiBuffers.Capture capture, VertexConsumer gui, Font font, Matrix4f base, float pixel) {
            this.capture = capture;
            this.gui = gui;
            this.font = font;
            this.base = base;
            this.pixel = pixel;
        }

        private void quad(float x0, float y0, float x1, float y1, int rgb, int alpha) {
            if (x1 <= x0 || y1 <= y0) {
                return;
            }
            int color = Math.min(255, alpha) << 24 | rgb & 0xFFFFFF;
            gui.addVertex(base, x0, y0, 0.0F).setColor(color);
            gui.addVertex(base, x0, y1, 0.0F).setColor(color);
            gui.addVertex(base, x1, y1, 0.0F).setColor(color);
            gui.addVertex(base, x1, y0, 0.0F).setColor(color);
        }

        private void outline(float x0, float y0, float x1, float y1, int rgb, int alpha) {
            quad(x0, y0, x1, y0 + pixel, rgb, alpha);
            quad(x0, y1 - pixel, x1, y1, rgb, alpha);
            quad(x0, y0 + pixel, x0 + pixel, y1 - pixel, rgb, alpha);
            quad(x1 - pixel, y0 + pixel, x1, y1 - pixel, rgb, alpha);
        }

        private float width(String text, float scale, float spacing) {
            if (spacing == 0.0F) {
                return font.width(text) * scale;
            }
            float width = 0.0F;
            for (int index = 0; index < text.length(); index++) {
                width += font.width(String.valueOf(text.charAt(index))) * scale + spacing;
            }
            return Math.max(0.0F, width - spacing);
        }

        /**
         * 右对齐、以基线定位的全息文字：可选的辉光（四向偏移的强调色）、
         * 一像素红青色散，最后是正文。返回文字宽度。
         */
        private float text(String text, float right, float baseline, float scale, int rgb, int alpha,
                           float spacing, boolean glow) {
            float width = width(text, scale, spacing);
            float left = right - width;
            float top = baseline - FONT_ASCENT * scale;
            if (glow) {
                int glowAlpha = Math.round(alpha * 0.16F);
                for (int dx = -1; dx <= 1; dx += 2) {
                    for (int dy = -1; dy <= 1; dy += 2) {
                        run(text, left + dx * 0.5F, top + dy * 0.5F, scale, spacing, ACCENT, glowAlpha);
                    }
                }
            }
            int aberration = Math.round(alpha * 0.22F);
            run(text, left - pixel, top, scale, spacing, ABERRATION_RED, aberration);
            run(text, left + pixel, top, scale, spacing, ABERRATION_CYAN, aberration);
            run(text, left, top, scale, spacing, rgb, alpha);
            return width;
        }

        private void run(String text, float x, float y, float scale, float spacing, int rgb, int alpha) {
            // 透明度低于 4 时原版字体会当作不透明处理。
            if (alpha < 8) {
                return;
            }
            int color = Math.min(255, alpha) << 24 | rgb & 0xFFFFFF;
            if (spacing == 0.0F) {
                font.drawInBatch(text, 0.0F, 0.0F, color, false, glyphPose(x, y, scale), capture,
                        Font.DisplayMode.NORMAL, 0, 15728880);
                return;
            }
            float cursor = x;
            for (int index = 0; index < text.length(); index++) {
                String glyph = String.valueOf(text.charAt(index));
                font.drawInBatch(glyph, 0.0F, 0.0F, color, false, glyphPose(cursor, y, scale), capture,
                        Font.DisplayMode.NORMAL, 0, 15728880);
                cursor += font.width(glyph) * scale + spacing;
            }
        }

        private Matrix4f glyphPose(float x, float y, float scale) {
            return new Matrix4f(base).translate(x, y, 0.0F).scale(scale, scale, 1.0F);
        }
    }

    private record Key(Font font, int screenWidth, double guiScale, int online,
                       String date, String weekday, String time, int signal, String lowTps,
                       String name, String title, int titleColor, String rank, int rankColor,
                       int level, long experience, long nextExperience,
                       ResourceLocation skin, int effectRows) {
        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key key)) {
                return false;
            }
            return font == key.font && screenWidth == key.screenWidth
                    && Double.compare(guiScale, key.guiScale) == 0 && online == key.online
                    && signal == key.signal && titleColor == key.titleColor && rankColor == key.rankColor
                    && level == key.level && experience == key.experience
                    && nextExperience == key.nextExperience && effectRows == key.effectRows
                    && Objects.equals(date, key.date) && Objects.equals(weekday, key.weekday)
                    && Objects.equals(time, key.time) && Objects.equals(lowTps, key.lowTps)
                    && Objects.equals(name, key.name) && Objects.equals(title, key.title)
                    && Objects.equals(rank, key.rank) && Objects.equals(skin, key.skin);
        }

        @Override
        public int hashCode() {
            return Objects.hash(screenWidth, online, time, level, experience);
        }
    }

    private record Cache(Key key, Matrix4f pose, RetainedGuiBuffers buffers, RetainedPlayerFace avatar,
                         Anchor anchor) {
    }
}
