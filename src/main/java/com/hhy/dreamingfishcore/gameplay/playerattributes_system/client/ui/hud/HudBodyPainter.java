package com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud;

/**
 * 按体征状态把 {@link HudBodyGeometry} 分层绘制成矩形，不依赖 Minecraft。
 *
 * <p>这是着色器线稿不可用时的备用人形。人形本身就是生命条：内部从脚底向上按生命比例柔和填充，
 * 生命越低填充越浓。感染从轮廓向躯干蔓延；穿戴护甲的部位描边加粗，耐久过低时内圈闪烁；
 * 受击部位的描边短暂泛红。</p>
 */
final class HudBodyPainter {
    private static final int HALO_COLOR = 0x70000000;
    private static final int EMPTY_COLOR = 0x3C0C0E11;
    private static final int CALM_FILL_ALPHA = 0x30;
    private static final int DANGER_FILL_ALPHA = 0x78;
    private static final int LAG_COLOR = 0x6CE8E2D2;
    private static final int SEAM_COLOR = 0x40000000;
    private static final int PLATED_OUTLINE_ALPHA = 0xF4;
    private static final int BARE_OUTLINE_ALPHA = 0xB0;
    private static final float CRITICAL_RATIO = 0.15F;
    private static final float WEAR_WARN_RATIO = 0.25F;
    private static final float WEAR_DANGER_RATIO = 0.10F;

    private HudBodyPainter() {
    }

    @FunctionalInterface
    interface QuadSink {
        void fill(int left, int top, int right, int bottom, int argb);
    }

    /**
     * 单帧的人形状态。
     *
     * @param healthRatio      当前生命比例，决定填充高度
     * @param lagRatio         不低于 {@code healthRatio}；两者之间是刚失去的生命，以淡色残影显示
     * @param infectionRatio   感染覆盖比例
     * @param flash            受伤白闪强度 0—1
     * @param regionHit        各部位受击高亮强度 0—1
     * @param regionPlated     各部位是否穿戴护甲
     * @param regionDurability 各部位装备耐久比例，无耐久装备为 1
     * @param scanProgress     扫描线位置 0—1，小于 0 表示不显示
     * @param timeMillis       用于危险脉冲的时钟
     */
    record Visual(float healthRatio, float lagRatio, float infectionRatio, float flash,
                  float[] regionHit, boolean[] regionPlated, float[] regionDurability,
                  float scanProgress, long timeMillis) {
    }

    static void paint(HudBodyGeometry body, Visual visual, QuadSink sink) {
        float health = HudPalette.clamp01(visual.healthRatio());
        float lag = Math.max(health, HudPalette.clamp01(visual.lagRatio()));
        int span = body.bottomY - body.topY;
        int fillY = body.bottomY - Math.round(health * span);
        int lagY = body.bottomY - Math.round(lag * span);
        boolean critical = health < CRITICAL_RATIO;
        float criticalPulse = critical ? HudPalette.pulse(visual.timeMillis(), 900L) : 0.0F;
        int healthColor = HudPalette.bodyColor(health);

        emit(sink, body.halo, HALO_COLOR);

        // 生命容器：上方是已流失的空腔，下方是剩余生命。
        emitRows(sink, body.interior, Integer.MIN_VALUE, fillY, EMPTY_COLOR);
        if (lagY < fillY) {
            emitRows(sink, body.interior, lagY, fillY, LAG_COLOR);
        }
        float danger = HudPalette.clamp01((0.5F - health) / (0.5F - CRITICAL_RATIO));
        int fillAlpha = Math.round(CALM_FILL_ALPHA + (DANGER_FILL_ALPHA - CALM_FILL_ALPHA) * danger
                + criticalPulse * 0x30);
        emitRows(sink, body.interior, fillY, Integer.MAX_VALUE, HudPalette.withAlpha(healthColor, fillAlpha));

        // 感染是暗沉的腐化色，不与随生命变化的人形颜色撞色；空腔里更淡，完全感染时仍能读出生命高度。
        HudBodyGeometry.Span[] infection = body.infection(visual.infectionRatio());
        if (infection.length > 0) {
            emitRows(sink, infection, Integer.MIN_VALUE, fillY,
                    HudPalette.withAlpha(HudPalette.INFECTION_ROT, 0x5A));
            emitRows(sink, infection, fillY, Integer.MAX_VALUE, HudPalette.withAlpha(HudPalette.INFECTION_ROT, 0x96));
        }

        emit(sink, body.seams, SEAM_COLOR);

        for (int region = 0; region < HudBodyGeometry.REGION_COUNT; region++) {
            float hit = HudPalette.clamp01(visual.regionHit()[region]);
            if (hit > 0.0F) {
                emit(sink, body.regionInterior[region],
                        HudPalette.withAlpha(HudPalette.RED, Math.round(0x48 * hit)));
                emit(sink, body.innerRing[region],
                        HudPalette.withAlpha(HudPalette.RED, Math.round(0xC8 * hit)));
                continue;
            }
            int ringColor = innerRingColor(visual, region, healthColor);
            if (ringColor != 0) {
                emit(sink, body.innerRing[region], ringColor);
            }
        }

        // 液面线：让生命高度在任何背景下都能一眼看清。
        if (health > 0.0F && health < 1.0F) {
            emitRows(sink, body.interior, fillY, fillY + 1,
                    HudPalette.withAlpha(HudPalette.blend(healthColor, 0xFFFFFFFF, 0.35F), 0xE0));
        }

        for (int region = 0; region < HudBodyGeometry.REGION_COUNT; region++) {
            int color = healthColor;
            float hit = HudPalette.clamp01(visual.regionHit()[region]);
            if (hit > 0.0F) {
                color = HudPalette.blend(color, HudPalette.RED, hit);
            }
            if (visual.flash() > 0.0F) {
                color = HudPalette.blend(color, 0xFFFFFFFF, visual.flash() * 0.6F);
            }
            int alpha = visual.regionPlated()[region] ? PLATED_OUTLINE_ALPHA : BARE_OUTLINE_ALPHA;
            if (critical) {
                alpha = Math.round(0x96 + criticalPulse * 0x69);
            } else if (hit > 0.0F) {
                alpha = Math.max(alpha, Math.round(0xF0 * hit));
            }
            emit(sink, body.outline[region], HudPalette.withAlpha(color, alpha));
        }

        if (visual.scanProgress() >= 0.0F && visual.scanProgress() <= 1.0F) {
            int scanY = body.topY + Math.round(visual.scanProgress() * span);
            float fade = 1.0F - visual.scanProgress() * 0.5F;
            emitRows(sink, body.filled, scanY, scanY + 1,
                    HudPalette.withAlpha(0xFFFFFFFF, Math.round(0x8C * fade)));
            emitRows(sink, body.filled, scanY - 2, scanY,
                    HudPalette.withAlpha(HudPalette.BONE, Math.round(0x30 * fade)));
        }
    }

    /** 护甲内圈：正常时是提亮的描边色，耐久过低时转为闪烁的琥珀或红色。 */
    private static int innerRingColor(Visual visual, int region, int healthColor) {
        if (!visual.regionPlated()[region]) {
            return 0;
        }
        float durability = visual.regionDurability()[region];
        if (durability <= WEAR_DANGER_RATIO) {
            float pulse = HudPalette.pulse(visual.timeMillis(), 520L);
            return HudPalette.withAlpha(HudPalette.RED, Math.round(0x70 + pulse * 0x80));
        }
        if (durability <= WEAR_WARN_RATIO) {
            float pulse = HudPalette.pulse(visual.timeMillis(), 1100L);
            return HudPalette.withAlpha(HudPalette.AMBER, Math.round(0x70 + pulse * 0x70));
        }
        return HudPalette.withAlpha(HudPalette.blend(healthColor, 0xFFFFFFFF, 0.4F), 0x7A);
    }

    private static void emit(QuadSink sink, HudBodyGeometry.Span[] spans, int color) {
        if ((color >>> 24) == 0) {
            return;
        }
        for (HudBodyGeometry.Span span : spans) {
            sink.fill(span.left(), span.top(), span.right(), span.bottom(), color);
        }
    }

    /** 只绘制 {@code [minY, maxY)} 行范围内的部分。 */
    private static void emitRows(QuadSink sink, HudBodyGeometry.Span[] spans, int minY, int maxY, int color) {
        if ((color >>> 24) == 0 || minY >= maxY) {
            return;
        }
        for (HudBodyGeometry.Span span : spans) {
            int top = Math.max(span.top(), minY);
            int bottom = Math.min(span.bottom(), maxY);
            if (top < bottom) {
                sink.fill(span.left(), top, span.right(), bottom, color);
            }
        }
    }
}
