package com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;

/**
 * 快捷栏上方的三段进度条：左侧体力、中间原版经验、右侧勇气，三段常驻且左右对称。
 *
 * <p>两端各有一个图标；体力或勇气变化时，对应数值在条上方短暂显示，按住查看键时常显。
 * 氧气只在水下或未回满时出现在经验条正上方，同样居中。</p>
 */
final class HudActionBars {
    private static final int SIDE_WIDTH = 68;
    private static final int CENTER_WIDTH = 82;
    private static final int SEGMENT_GAP = 4;
    private static final int TOTAL_WIDTH = SIDE_WIDTH * 2 + CENTER_WIDTH + SEGMENT_GAP * 2;
    private static final int HOTBAR_GAP = 4;
    private static final int ICON_SIZE = 10;
    private static final int ICON_GAP = 4;
    /** 氧气条位于经验等级数字之上。 */
    private static final int OXYGEN_OFFSET = 15;
    private static final float TEXT_SCALE = 0.66F;
    private static final long VALUE_REVEAL_MS = 1800L;
    private static final float COURAGE_DANGER_RATIO = 0.25F;
    private static final float OXYGEN_WARN_RATIO = 0.25F;

    private static final int STAMINA_COLOR = 0xFFB99A57;
    private static final int COURAGE_COLOR = 0xFF8170A7;
    private static final int EXPERIENCE_COLOR = 0xFF6E9A70;
    private static final int OXYGEN_COLOR = 0xFF4D9BC9;
    private static final int LOW_COLOR = 0xFFA85048;

    private static boolean valuesInitialized;
    private static int lastStrength;
    private static int lastMaxStrength;
    private static float lastCourage;
    private static float lastMaxCourage;
    private static long valuesChangedAt;
    private static float oxygenVisibility;
    private static String cachedStaminaText = "";
    private static long cachedStaminaKey = Long.MIN_VALUE;
    private static String cachedCourageText = "";
    private static long cachedCourageKey = Long.MIN_VALUE;
    private static String cachedLevelText = "";
    private static long cachedLevelKey = Long.MIN_VALUE;

    private HudActionBars() {
    }

    static void render(UiCanvas canvas, Minecraft minecraft, HudVitals vitals,
                       boolean detailHeld, float detail, long now, float deltaSeconds) {
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int x = (screenWidth - TOTAL_WIDTH) / 2;
        int y = CustomHotbarGUI.getAnimatedHotbarTopY(screenHeight) - HOTBAR_GAP - HudDraw.BAR_HEIGHT;
        int experienceX = x + SIDE_WIDTH + SEGMENT_GAP;
        int courageX = experienceX + CENTER_WIDTH + SEGMENT_GAP;
        Font font = minecraft.font;

        trackValueChanges(vitals, now);
        float valueAlpha = Math.max(detail, now - valuesChangedAt <= VALUE_REVEAL_MS ? 1.0F : 0.0F);

        float courageRatio = vitals.courageRatio();
        boolean courageDanger = courageRatio <= COURAGE_DANGER_RATIO;
        HudDraw.bar(canvas, x, y, SIDE_WIDTH, vitals.strengthRatio(), STAMINA_COLOR, 1.0F, false);
        HudDraw.bar(canvas, experienceX, y, CENTER_WIDTH, vitals.experienceProgress(), EXPERIENCE_COLOR, 1.0F, false);
        HudDraw.bar(canvas, courageX, y, SIDE_WIDTH, courageRatio,
                courageDanger ? HudPalette.blend(COURAGE_COLOR, LOW_COLOR, 0.36F) : COURAGE_COLOR, 1.0F, courageDanger);

        int iconY = y - (ICON_SIZE - HudDraw.BAR_HEIGHT) / 2;
        HudIconBatch.draw(canvas, HudIconBatch.Icon.STAMINA, x - ICON_SIZE - ICON_GAP, iconY, ICON_SIZE, 0.82F);
        HudIconBatch.draw(canvas, HudIconBatch.Icon.COURAGE, courageX + SIDE_WIDTH + ICON_GAP, iconY, ICON_SIZE, 0.82F);

        if (valueAlpha > 0.01F) {
            drawValue(canvas, font, staminaText(vitals), x, SIDE_WIDTH, y, STAMINA_COLOR, valueAlpha);
            drawValue(canvas, font, courageText(vitals), courageX, SIDE_WIDTH, y, COURAGE_COLOR, valueAlpha);
        }
        if (vitals.experienceLevel() > 0 || detailHeld) {
            drawValue(canvas, font, levelText(vitals, detailHeld), experienceX, CENTER_WIDTH, y,
                    EXPERIENCE_COLOR, 1.0F);
        }

        oxygenVisibility = HudPalette.approach(oxygenVisibility, vitals.showAir() ? 1.0F : 0.0F,
                vitals.showAir() ? 6.0F : 2.5F, deltaSeconds);
        float oxygen = HudPalette.easeOutCubic(oxygenVisibility);
        if (oxygen > 0.01F) {
            float ratio = vitals.maxAir() > 0 ? Math.max(0, vitals.air()) / (float) vitals.maxAir() : 0.0F;
            boolean warning = ratio <= OXYGEN_WARN_RATIO;
            int color = warning ? HudPalette.blend(OXYGEN_COLOR, LOW_COLOR, 0.36F) : OXYGEN_COLOR;
            HudDraw.bar(canvas, experienceX, y - OXYGEN_OFFSET, CENTER_WIDTH, ratio, color, oxygen, warning);
        }
    }

    private static void trackValueChanges(HudVitals vitals, long now) {
        if (!valuesInitialized) {
            valuesInitialized = true;
            cacheValues(vitals);
            return;
        }
        if (vitals.strength() != lastStrength || vitals.maxStrength() != lastMaxStrength
                || Math.abs(vitals.courage() - lastCourage) > 0.5F
                || Math.abs(vitals.maxCourage() - lastMaxCourage) > 0.5F) {
            valuesChangedAt = now;
            cacheValues(vitals);
        }
    }

    private static void cacheValues(HudVitals vitals) {
        lastStrength = vitals.strength();
        lastMaxStrength = vitals.maxStrength();
        lastCourage = vitals.courage();
        lastMaxCourage = vitals.maxCourage();
    }

    private static void drawValue(UiCanvas canvas, Font font, String text, int x, int width, int barY,
                                  int color, float alpha) {
        float textWidth = font.width(text) * TEXT_SCALE;
        HudDraw.text(canvas, font, text, x + (width - textWidth) / 2.0F, barY - 8.0F,
                HudPalette.withAlpha(HudPalette.blend(color, 0xFFFFFFFF, 0.34F), 214), alpha, TEXT_SCALE);
    }

    private static String staminaText(HudVitals vitals) {
        long key = (long) vitals.strength() << 32 | vitals.maxStrength();
        if (key != cachedStaminaKey) {
            cachedStaminaKey = key;
            cachedStaminaText = vitals.strength() + "/" + vitals.maxStrength();
        }
        return cachedStaminaText;
    }

    private static String courageText(HudVitals vitals) {
        long key = (long) Math.round(vitals.courage()) << 32 | Math.round(vitals.maxCourage());
        if (key != cachedCourageKey) {
            cachedCourageKey = key;
            cachedCourageText = Math.round(vitals.courage()) + "/" + Math.round(vitals.maxCourage());
        }
        return cachedCourageText;
    }

    private static String levelText(HudVitals vitals, boolean detailHeld) {
        int percent = Math.round(HudPalette.clamp01(vitals.experienceProgress()) * 100.0F);
        long key = (long) vitals.experienceLevel() << 8 | (long) percent << 1 | (detailHeld ? 1 : 0);
        if (key != cachedLevelKey) {
            cachedLevelKey = key;
            cachedLevelText = detailHeld
                    ? vitals.experienceLevel() + " · " + percent + "%"
                    : Integer.toString(vitals.experienceLevel());
        }
        return cachedLevelText;
    }
}
