package com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud;

import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.TemplateReconstructionRules;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.limb_health_system.LimbType;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.limb_health_system.client.sync.LimbClientInjurySync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.world.entity.player.Player;

import java.lang.ref.WeakReference;

/**
 * 左下角「体征回传」：生命模板对身体的实时读数。
 *
 * <p>常态只保留人形；数值变化、偏低或出现异常时，对应读数在人形右侧浮现，恢复后淡出。
 * 按住查看键时展开全部读数与标签，并补充护甲值与模板重建余量。</p>
 */
final class HudVitalsPanel {
    private static final int LEFT_MARGIN = 6;
    private static final int BOTTOM_MARGIN = 5;
    private static final int FIGURE_CENTER_OFFSET = 17;
    /**
     * 线稿人形站立时的高度与离屏绘制区域（GUI 像素）。区域右侧留给手持物品，
     * 头顶留出约 1.6 格方块的高度给头部饰品、玩偶等；空白处完全透明，不影响画面。
     */
    private static final int FIGURE_HEIGHT = 62;
    private static final int FIGURE_REGION_LEFT = 2;
    private static final int FIGURE_REGION_WIDTH = 66;
    private static final int FIGURE_REGION_HEIGHT = 124;
    private static final int FIGURE_REGION_BOTTOM_PADDING = 3;
    private static final float COLUMN_OFFSET = 23.0F;
    private static final float HOLDING_COLUMN_OFFSET = 34.0F;
    private static final int ROW_PITCH = 11;
    private static final int ICON_SIZE = 8;
    private static final int ICON_GAP = 4;
    private static final int LABEL_GAP = 4;
    private static final int VALUE_GAP = 3;
    private static final int VALUE_WIDTH = 66;
    private static final int ROW_SLIDE = 5;
    private static final int PANEL_PADDING = 4;
    private static final float TEXT_SCALE = 0.75F;
    private static final String HEADER = "体征回传";

    private static final long HEALTH_REVEAL_MS = 3000L;
    private static final long FOOD_REVEAL_MS = 2500L;
    private static final long INFECTION_REVEAL_MS = 3000L;
    private static final long TEMPLATE_REVEAL_MS = 4000L;
    private static final float HEALTH_LOW_RATIO = 0.35F;
    private static final int FOOD_LOW_LEVEL = 14;
    private static final float COURAGE_LOW_RATIO = 0.4F;
    private static final float COURAGE_DANGER_RATIO = 0.25F;
    private static final float INFECTION_DANGER_RATIO = 0.45F;

    private static final long LAG_HOLD_MS = 450L;
    private static final long FLASH_MS = 280L;
    private static final long HIT_FADE_MS = 2400L;
    private static final long SCAN_MS = 520L;
    private static final long INJURY_CLEANUP_INTERVAL_MS = 250L;

    private enum Row {
        HEALTH("生命"),
        FOOD("饱食"),
        INFECTION("感染"),
        COURAGE("勇气"),
        ARMOR("护甲"),
        TEMPLATE("模板余量");

        private final String label;

        Row(String label) {
            this.label = label;
        }
    }

    private static final Row[] ROWS = Row.values();
    private static final float[] VISIBILITY = new float[ROWS.length];
    private static final long[] CHANGED_AT = new long[ROWS.length];
    private static final long[] TEXT_KEY = new long[ROWS.length];
    private static final float[] REGION_HIT = new float[HudBodyGeometry.REGION_COUNT];
    private static final String[] TEXT_CACHE = new String[ROWS.length];

    /** 弱引用：退出世界后不应因 HUD 状态而保留旧的玩家与客户端世界。 */
    private static WeakReference<Player> trackedPlayer = new WeakReference<>(null);
    private static float baseHealth;
    private static int baseFood;
    private static float baseInfection;
    private static int baseInfectionLevel;
    private static float baseTemplate;
    private static int baseEquipmentKey;

    private static float displayHealth;
    private static float lagHealth;
    private static long damagedAt;
    private static long scanStartedAt;
    private static long lastInjuryCleanup;
    private static float columnOffset;

    private static Font cachedFont;
    private static float labelWidth;

    private HudVitalsPanel() {
    }

    static void render(UiCanvas canvas, Minecraft minecraft, Player player, HudVitals vitals,
                       boolean detailHeld, float detail, long now, float deltaSeconds) {
        if (player != trackedPlayer.get()) {
            reset(player, vitals, now);
        }
        trackChanges(vitals, now);
        updateFigure(vitals, now, deltaSeconds);
        updateVisibility(vitals, detailHeld, now, deltaSeconds);

        Font font = minecraft.font;
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int centerX = LEFT_MARGIN + FIGURE_CENTER_OFFSET;
        int footY = screenHeight - BOTTOM_MARGIN;
        // 手持物品会从线稿右侧伸出，读数列随之让开。
        float columnTarget = player.getMainHandItem().isEmpty() ? COLUMN_OFFSET : HOLDING_COLUMN_OFFSET;
        columnOffset = columnOffset <= 0.0F ? columnTarget
                : HudPalette.approach(columnOffset, columnTarget, 90.0F, deltaSeconds);
        int columnX = Math.round(centerX + columnOffset);
        float labels = labelWidth(font) * detail;

        float stackHeight = 0.0F;
        for (float visibility : VISIBILITY) {
            stackHeight += ROW_PITCH * HudPalette.easeOutCubic(visibility);
        }
        int headerY = Math.round(footY - ICON_SIZE - stackHeight);
        if (detail > 0.01F) {
            int right = Math.round(columnX + ICON_SIZE + ICON_GAP + labels + VALUE_WIDTH + PANEL_PADDING);
            int top = Math.min(footY - FIGURE_HEIGHT - 4, headerY) - PANEL_PADDING;
            canvas.shape(2, top, right - 2, footY + 3 - top).radius(4.0F)
                    .verticalGradient(HudPalette.withAlpha(0x0A0C0E, Math.round(0x70 * detail)),
                            HudPalette.withAlpha(0x0A0C0E, Math.round(0x90 * detail)))
                    .border(1.0F, HudPalette.withAlpha(HudPalette.BONE, Math.round(0x24 * detail))).draw();
        }

        HudBodyPainter.Visual visual = figureVisual(player, vitals, now);
        HudModelOutline.Layout layout = new HudModelOutline.Layout(FIGURE_REGION_LEFT,
                footY + FIGURE_REGION_BOTTOM_PADDING - FIGURE_REGION_HEIGHT,
                FIGURE_REGION_WIDTH, FIGURE_REGION_HEIGHT, centerX, footY, FIGURE_HEIGHT);
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        // 线稿需要离屏渲染模型再合成，作为原生命令放进画布；画布会保证它压在查看底板之上。
        canvas.custom(layout.left(), layout.top(), layout.width(), layout.height(), graphics -> {
            if (!HudModelOutline.render(graphics, minecraft, player, layout, visual, partialTick)) {
                HudBodyRenderer.render(graphics, HudBodyRenderer.geometry(player, centerX, footY, now), visual);
            }
        });

        float rowY = footY - ICON_SIZE;
        for (int index = ROWS.length - 1; index >= 0; index--) {
            float visibility = HudPalette.easeOutCubic(VISIBILITY[index]);
            if (visibility <= 0.01F) {
                continue;
            }
            float rowX = columnX - (1.0F - visibility) * ROW_SLIDE;
            drawRow(canvas, font, ROWS[index], vitals, rowX, Math.round(rowY), visibility, detail, detailHeld,
                    labels, now);
            rowY -= ROW_PITCH * visibility;
        }

        if (detail > 0.01F) {
            drawHeader(canvas, font, columnX, headerY, labels, detail, now);
        }
    }

    private static void reset(Player player, HudVitals vitals, long now) {
        trackedPlayer = new WeakReference<>(player);
        baseHealth = vitals.health();
        baseFood = vitals.food();
        baseInfection = vitals.infection();
        baseInfectionLevel = vitals.infectionLevel();
        baseTemplate = vitals.templatePoints();
        baseEquipmentKey = vitals.equipmentKey();
        displayHealth = vitals.healthRatio();
        lagHealth = displayHealth;
        damagedAt = 0L;
        // 进服、重生和切换维度时，模板重新同步身体，播放一次扫描。
        scanStartedAt = now;
        java.util.Arrays.fill(VISIBILITY, 0.0F);
        java.util.Arrays.fill(CHANGED_AT, 0L);
    }

    private static void trackChanges(HudVitals vitals, long now) {
        float healthDelta = vitals.health() - baseHealth;
        if (Math.abs(healthDelta) >= 0.5F) {
            CHANGED_AT[Row.HEALTH.ordinal()] = now;
            if (healthDelta >= 2.0F) {
                scanStartedAt = now;
            }
            baseHealth = vitals.health();
        }
        // 饥饿缓慢下降不打扰玩家；进食时短暂显示补充结果，偏低时常驻。
        if (vitals.food() > baseFood) {
            CHANGED_AT[Row.FOOD.ordinal()] = now;
        }
        baseFood = vitals.food();
        if (Math.abs(vitals.infection() - baseInfection) >= 0.5F
                || vitals.infectionLevel() != baseInfectionLevel) {
            CHANGED_AT[Row.INFECTION.ordinal()] = now;
            scanStartedAt = now;
            baseInfection = vitals.infection();
            baseInfectionLevel = vitals.infectionLevel();
        }
        if (Math.abs(vitals.templatePoints() - baseTemplate) >= 0.05F) {
            CHANGED_AT[Row.TEMPLATE.ordinal()] = now;
            scanStartedAt = now;
            baseTemplate = vitals.templatePoints();
        }
        if (vitals.equipmentKey() != baseEquipmentKey) {
            scanStartedAt = now;
            baseEquipmentKey = vitals.equipmentKey();
        }
    }

    /** 受伤时填充立即下降，刚失去的部分停留片刻再流走；治疗时平滑上升。 */
    private static void updateFigure(HudVitals vitals, long now, float deltaSeconds) {
        float ratio = vitals.healthRatio();
        if (ratio < displayHealth - 0.0005F) {
            displayHealth = ratio;
            damagedAt = now;
        } else {
            displayHealth = HudPalette.approach(displayHealth, ratio, 1.2F, deltaSeconds);
        }
        if (lagHealth < displayHealth) {
            lagHealth = displayHealth;
        } else if (now - damagedAt > LAG_HOLD_MS) {
            lagHealth = HudPalette.approach(lagHealth, displayHealth, 0.9F, deltaSeconds);
        }
    }

    private static void updateVisibility(HudVitals vitals, boolean detailHeld, long now, float deltaSeconds) {
        for (Row row : ROWS) {
            boolean target = detailHeld || switch (row) {
                case HEALTH -> vitals.healthRatio() <= HEALTH_LOW_RATIO
                        || recentlyChanged(row, now, HEALTH_REVEAL_MS);
                case FOOD -> vitals.food() <= FOOD_LOW_LEVEL || recentlyChanged(row, now, FOOD_REVEAL_MS);
                case INFECTION -> vitals.infected() || vitals.infection() >= 0.5F
                        || recentlyChanged(row, now, INFECTION_REVEAL_MS);
                // 勇气常驻在快捷栏上方的进度条，这里只在查看模式中列出。
                case COURAGE, ARMOR -> false;
                case TEMPLATE -> recentlyChanged(row, now, TEMPLATE_REVEAL_MS);
            };
            int index = row.ordinal();
            VISIBILITY[index] = HudPalette.approach(VISIBILITY[index], target ? 1.0F : 0.0F,
                    target ? 7.0F : 3.2F, deltaSeconds);
        }
    }

    private static boolean recentlyChanged(Row row, long now, long durationMillis) {
        long changedAt = CHANGED_AT[row.ordinal()];
        return changedAt > 0L && now - changedAt < durationMillis;
    }

    private static HudBodyPainter.Visual figureVisual(Player player, HudVitals vitals, long now) {
        if (now - lastInjuryCleanup >= INJURY_CLEANUP_INTERVAL_MS) {
            LimbClientInjurySync.cleanupExpiredInjuries(player);
            lastInjuryCleanup = now;
        }
        float[] hit = REGION_HIT;
        for (LimbType limb : HudVitals.LIMBS) {
            long injuredAt = LimbClientInjurySync.getInjuryTime(player, limb);
            long age = now - injuredAt;
            hit[limb.ordinal()] = injuredAt > 0L && age >= 0L && age < HIT_FADE_MS
                    ? (float) Math.pow(1.0F - age / (float) HIT_FADE_MS, 1.4D)
                    : 0.0F;
        }
        long sinceDamage = now - damagedAt;
        float flash = damagedAt > 0L && sinceDamage < FLASH_MS ? 1.0F - sinceDamage / (float) FLASH_MS : 0.0F;
        long sinceScan = now - scanStartedAt;
        float scan = scanStartedAt > 0L && sinceScan >= 0L && sinceScan < SCAN_MS
                ? sinceScan / (float) SCAN_MS
                : -1.0F;
        return new HudBodyPainter.Visual(displayHealth, lagHealth, vitals.infectionRatio(), flash, hit,
                vitals.regionPlated(), vitals.regionDurability(), scan, now);
    }

    private static void drawRow(UiCanvas canvas, Font font, Row row, HudVitals vitals,
                                float x, int y, float visibility, float detail, boolean detailHeld,
                                float labels, long now) {
        int iconX = Math.round(x);
        float textY = y + 1.0F;
        float valueX = x + ICON_SIZE + ICON_GAP;
        if (labels > 0.5F) {
            HudDraw.text(canvas, font, row.label, valueX, textY, HudPalette.BONE_DIM,
                    visibility * detail, TEXT_SCALE);
            valueX += labels;
        }
        String text = rowText(row, vitals, detailHeld);

        switch (row) {
            case HEALTH -> {
                HudIconBatch.draw(canvas, HudIconBatch.Icon.HEALTH, iconX, y, ICON_SIZE, visibility);
                float alpha = visibility;
                if (vitals.healthRatio() < 0.15F) {
                    alpha *= 0.7F + 0.3F * HudPalette.pulse(now, 900L);
                }
                HudDraw.text(canvas, font, text, valueX, textY,
                        HudPalette.bodyColor(vitals.healthRatio()), alpha, TEXT_SCALE);
            }
            case FOOD -> {
                HudIconBatch.draw(canvas, HudIconBatch.Icon.FOOD, iconX, y, ICON_SIZE, visibility);
                float ratio = vitals.food() / 20.0F;
                int width = HudDraw.segments(canvas, Math.round(valueX), y + 2, ratio,
                        HudPalette.resourceColor(ratio, 0.35F, 0.15F), visibility);
                HudDraw.text(canvas, font, text, valueX + width + VALUE_GAP, textY,
                        HudPalette.BONE, visibility * detail, TEXT_SCALE);
            }
            case INFECTION -> {
                HudIconBatch.draw(canvas, HudIconBatch.Icon.INFECTION, iconX, y, ICON_SIZE, visibility);
                float alpha = visibility;
                if (!vitals.infected() && vitals.infectionRatio() >= INFECTION_DANGER_RATIO) {
                    alpha *= 0.72F + 0.28F * HudPalette.pulse(now, 1200L);
                }
                HudDraw.text(canvas, font, text, valueX, textY, HudPalette.INFECTION, alpha, TEXT_SCALE);
            }
            case COURAGE -> {
                HudIconBatch.draw(canvas, HudIconBatch.Icon.COURAGE, iconX, y, ICON_SIZE, visibility);
                float ratio = vitals.courageRatio();
                int width = HudDraw.segments(canvas, Math.round(valueX), y + 2, ratio,
                        HudPalette.resourceColor(ratio, COURAGE_LOW_RATIO, COURAGE_DANGER_RATIO), visibility);
                HudDraw.text(canvas, font, text, valueX + width + VALUE_GAP, textY,
                        HudPalette.BONE, visibility * detail, TEXT_SCALE);
            }
            case ARMOR -> {
                HudIconBatch.draw(canvas, HudIconBatch.Icon.ARMOR, iconX, y, ICON_SIZE, visibility);
                HudDraw.text(canvas, font, text, valueX, textY, HudPalette.BONE, visibility, TEXT_SCALE);
            }
            case TEMPLATE -> {
                int remaining = TemplateReconstructionRules.remainingReconstructions(
                        vitals.templatePoints(), vitals.templateCost());
                int color = remaining <= 0 ? HudPalette.RED : remaining < 2 ? HudPalette.AMBER : HudPalette.BONE;
                drawTemplateGlyph(canvas, iconX, y, HudPalette.withAlpha(color, Math.round(0xE0 * visibility)));
                HudDraw.text(canvas, font, text, valueX, textY, color, visibility, TEXT_SCALE);
            }
        }
    }

    private static String rowText(Row row, HudVitals vitals, boolean detailHeld) {
        int detailBit = detailHeld ? 1 : 0;
        long key = switch (row) {
            case HEALTH -> (long) Float.floatToIntBits(vitals.health()) << 32
                    | Float.floatToIntBits(vitals.maxHealth()) & 0xFFFFFFFFL;
            case FOOD -> vitals.food();
            case INFECTION -> (long) vitals.infectionLevel() << 32
                    | (long) Math.round(vitals.infectionRatio() * 100.0F) << 1 | detailBit;
            case COURAGE -> (long) Math.round(vitals.courage()) << 32 | Math.round(vitals.maxCourage());
            case ARMOR -> vitals.armor();
            case TEMPLATE -> Float.floatToIntBits(vitals.templatePoints());
        };
        int index = row.ordinal();
        if (TEXT_CACHE[index] != null && TEXT_KEY[index] == key) {
            return TEXT_CACHE[index];
        }
        String text = switch (row) {
            case HEALTH -> HudDraw.number(vitals.health()) + "/" + HudDraw.number(vitals.maxHealth());
            case FOOD -> vitals.food() + "/20";
            case INFECTION -> infectionText(vitals, detailHeld);
            case COURAGE -> Math.round(vitals.courage()) + "/" + Math.round(vitals.maxCourage());
            case ARMOR -> Integer.toString(vitals.armor());
            case TEMPLATE -> HudDraw.number(vitals.templatePoints()) + "/100";
        };
        TEXT_KEY[index] = key;
        TEXT_CACHE[index] = text;
        return text;
    }

    private static String infectionText(HudVitals vitals, boolean detailHeld) {
        if (vitals.infected()) {
            String level = vitals.infectionLevel() >= 2 ? "二级感染" : "一级感染";
            return detailHeld ? level + "者" : level;
        }
        String percent = Math.round(vitals.infectionRatio() * 100.0F) + "%";
        return detailHeld ? percent + " · 幸存者" : percent;
    }

    private static void drawHeader(UiCanvas canvas, Font font, int x, int y, float labels,
                                   float detail, long now) {
        // 缓慢闪烁的记录点，表示读数来自实时回传。
        int dotAlpha = Math.round((0x60 + 0x9F * HudPalette.pulse(now, 1600L)) * detail);
        canvas.circle(x + 4.0F, y + 3.0F, 1.2F, HudPalette.withAlpha(HudPalette.BONE, dotAlpha));
        HudDraw.text(canvas, font, HEADER, x + ICON_SIZE + ICON_GAP, y, HudPalette.BONE_DIM, detail, TEXT_SCALE);
        int ruleRight = Math.round(x + ICON_SIZE + ICON_GAP + labels + VALUE_WIDTH);
        canvas.shape(x, y + 8, ruleRight - x, 1).horizontalGradient(
                HudPalette.withAlpha(HudPalette.BONE, Math.round(0x40 * detail)),
                HudPalette.withAlpha(HudPalette.BONE, Math.round(0x08 * detail))).draw();
    }

    /** 模板余量没有专属图标：用一枚空心菱形表示“模板”，中心点表示当前身体。 */
    private static void drawTemplateGlyph(UiCanvas canvas, int x, int y, int color) {
        if ((color >>> 24) == 0) {
            return;
        }
        float cx = x + 3.5F;
        float cy = y + 3.5F;
        float r = 3.2F;
        canvas.line(cx, cy - r, cx + r, cy, 1.0F, color, true);
        canvas.line(cx + r, cy, cx, cy + r, 1.0F, color, true);
        canvas.line(cx, cy + r, cx - r, cy, 1.0F, color, true);
        canvas.line(cx - r, cy, cx, cy - r, 1.0F, color, true);
        canvas.circle(cx, cy, 0.8F, color);
    }

    private static float labelWidth(Font font) {
        if (font != cachedFont) {
            float widest = 0.0F;
            for (Row row : ROWS) {
                widest = Math.max(widest, font.width(row.label) * TEXT_SCALE);
            }
            labelWidth = widest + LABEL_GAP;
            cachedFont = font;
        }
        return labelWidth;
    }
}
