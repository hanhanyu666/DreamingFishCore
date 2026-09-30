package com.hhy.dreamingfishcore.client.ui.framework.widget;

import java.util.function.Consumer;

/**
 * 矢量线条图标（24×24 网格，线宽 2）。风格统一、任意缩放清晰、可按主题着色。
 */
public enum Icons {
    CHEVRON_LEFT(p -> p.poly(15, 5.5F, 8.5F, 12, 15, 18.5F)),
    CHEVRON_RIGHT(p -> p.poly(9, 5.5F, 15.5F, 12, 9, 18.5F)),
    CHEVRON_DOWN(p -> p.poly(5.5F, 9, 12, 15.5F, 18.5F, 9)),
    CHEVRON_UP(p -> p.poly(5.5F, 15, 12, 8.5F, 18.5F, 15)),
    ARROW_LEFT(p -> {
        p.line(19.5F, 12, 4.5F, 12);
        p.poly(11, 5.5F, 4.5F, 12, 11, 18.5F);
    }),
    ARROW_RIGHT(p -> {
        p.line(4.5F, 12, 19.5F, 12);
        p.poly(13, 5.5F, 19.5F, 12, 13, 18.5F);
    }),
    ARROW_UP_RIGHT(p -> {
        p.line(6.5F, 17.5F, 17.5F, 6.5F);
        p.poly(8.5F, 6.5F, 17.5F, 6.5F, 17.5F, 15.5F);
    }),
    CLOSE(p -> {
        p.line(6, 6, 18, 18);
        p.line(18, 6, 6, 18);
    }),
    CHECK(p -> p.poly(4.5F, 12.5F, 9.5F, 17.5F, 19.5F, 6.5F)),
    PLUS(p -> {
        p.line(12, 5, 12, 19);
        p.line(5, 12, 19, 12);
    }),
    MINUS(p -> p.line(5, 12, 19, 12)),
    MENU(p -> {
        p.line(4, 6.5F, 20, 6.5F);
        p.line(4, 12, 20, 12);
        p.line(4, 17.5F, 20, 17.5F);
    }),
    HOME(p -> {
        p.poly(3, 11, 12, 3.5F, 21, 11);
        p.poly(5.5F, 9.5F, 5.5F, 20.5F, 18.5F, 20.5F, 18.5F, 9.5F);
        p.poly(10, 20.5F, 10, 14.5F, 14, 14.5F, 14, 20.5F);
    }),
    USER(p -> {
        p.ring(12, 8, 4);
        p.arc(12, 21.5F, 7.5F, 180, 180);
    }),
    USERS(p -> {
        p.ring(9, 8.5F, 3.5F);
        p.arc(9, 21, 6.5F, 180, 180);
        p.arc(16, 8.5F, 3.5F, -90, 180);
        p.arc(16, 21, 6.5F, 270, 90);
    }),
    MEGAPHONE(p -> {
        p.polygon(3.5F, 9.5F, 7.5F, 9.5F, 15.5F, 5, 15.5F, 19, 7.5F, 14.5F, 3.5F, 14.5F);
        p.poly(7.5F, 14.5F, 9, 20, 11.5F, 20, 10.5F, 15.8F);
        p.arc(16, 12, 4.5F, -40, 80);
    }),
    BELL(p -> {
        p.arc(12, 11, 6, 180, 180);
        p.poly(6, 11, 6, 15.5F, 4.5F, 17.5F, 19.5F, 17.5F, 18, 15.5F, 18, 11);
        p.line(12, 3.5F, 12, 5);
        p.arc(12, 18.5F, 2.2F, 0, 180);
    }),
    MAIL(p -> {
        p.roundRect(3, 5.5F, 21, 18.5F, 2.5F);
        p.poly(4, 7, 12, 13, 20, 7);
    }),
    CHAT(p -> {
        p.roundRect(3.5F, 4.5F, 20.5F, 16, 3);
        p.poly(8, 16, 7, 20.5F, 12.5F, 16);
    }),
    BOOK(p -> {
        p.poly(2.5F, 5, 8.5F, 5, 12, 7.5F, 12, 20, 8.5F, 17.5F, 2.5F, 17.5F, 2.5F, 5);
        p.poly(21.5F, 5, 15.5F, 5, 12, 7.5F);
        p.poly(12, 20, 15.5F, 17.5F, 21.5F, 17.5F, 21.5F, 5);
    }),
    CLOCK(p -> {
        p.ring(12, 12, 9);
        p.poly(12, 7, 12, 12, 15.5F, 14);
    }),
    HISTORY(p -> {
        p.arc(12, 12, 8.5F, 200, 300);
        p.poly(2.5F, 8, 4.2F, 11.6F, 7.8F, 10);
        p.poly(12, 7.5F, 12, 12, 15, 14);
    }),
    HELP(p -> {
        p.ring(12, 12, 9);
        p.arc(12, 9.8F, 2.8F, 190, 230);
        p.line(12, 12.8F, 12, 13.8F);
        p.dot(12, 17, 1.25F);
    }),
    INFO(p -> {
        p.ring(12, 12, 9);
        p.line(12, 11, 12, 16.5F);
        p.dot(12, 7.8F, 1.25F);
    }),
    WARNING(p -> {
        p.polygon(12, 3.5F, 21.5F, 19.5F, 2.5F, 19.5F);
        p.line(12, 9.5F, 12, 13.5F);
        p.dot(12, 16.5F, 1.2F);
    }),
    SETTINGS(p -> {
        p.ring(12, 12, 3.2F);
        p.ring(12, 12, 7);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0 * i / 8.0;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            p.line(12 + cos * 7.6F, 12 + sin * 7.6F, 12 + cos * 9.8F, 12 + sin * 9.8F);
        }
    }),
    STAR(p -> {
        float[] pts = new float[20];
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2.0 + Math.PI * i / 5.0;
            float r = i % 2 == 0 ? 9.5F : 4.2F;
            pts[i * 2] = 12 + (float) Math.cos(angle) * r;
            pts[i * 2 + 1] = 12.8F + (float) Math.sin(angle) * r;
        }
        p.polygon(pts);
    }),
    TROPHY(p -> {
        p.poly(7, 4, 17, 4, 17, 9);
        p.arc(12, 9, 5, 0, 180);
        p.line(7, 4, 7, 9);
        p.arc(7, 7.5F, 2.8F, 90, 180);
        p.arc(17, 7.5F, 2.8F, -90, 180);
        p.line(12, 14, 12, 18);
        p.line(8, 20, 16, 20);
    }),
    BAG(p -> {
        p.roundRect(4.5F, 8, 19.5F, 20.5F, 2.5F);
        p.arc(12, 8.5F, 3.5F, 180, 180);
    }),
    FLAG(p -> {
        p.line(5, 21, 5, 3.5F);
        p.poly(5, 4, 18.5F, 4, 15.5F, 8, 18.5F, 12, 5, 12);
    }),
    MAP(p -> {
        p.polygon(3, 6, 9, 3.5F, 15, 6, 21, 3.5F, 21, 18, 15, 20.5F, 9, 18, 3, 20.5F);
        p.line(9, 3.5F, 9, 18);
        p.line(15, 6, 15, 20.5F);
    }),
    PIN(p -> {
        p.ring(12, 10, 2.8F);
        p.arc(12, 10, 7, 150, 240);
        p.poly(5.9F, 13.5F, 12, 21.5F, 18.1F, 13.5F);
    }),
    TARGET(p -> {
        p.ring(12, 12, 8);
        p.dot(12, 12, 2);
        p.line(12, 1.5F, 12, 5);
        p.line(12, 19, 12, 22.5F);
        p.line(1.5F, 12, 5, 12);
        p.line(19, 12, 22.5F, 12);
    }),
    SEARCH(p -> {
        p.ring(10.5F, 10.5F, 6.5F);
        p.line(15.5F, 15.5F, 20.5F, 20.5F);
    }),
    HEART(p -> {
        int n = 28;
        float[] xs = new float[n + 1];
        float[] ys = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            double t = Math.PI * 2.0 * i / n;
            double sx = 16 * Math.pow(Math.sin(t), 3);
            double sy = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
            xs[i] = 12 + (float) sx * 0.55F;
            ys[i] = 11.5F - (float) sy * 0.55F;
        }
        p.curve(xs, ys);
    }),
    SHIELD(p -> {
        p.poly(12, 3, 19.5F, 5.8F, 19.5F, 11.5F);
        p.poly(12, 3, 4.5F, 5.8F, 4.5F, 11.5F);
        p.arc(12, 11.5F, 7.5F, 0, 90);
        p.arc(12, 11.5F, 7.5F, 90, 90);
        p.poly(19.5F, 11.5F, 18.5F, 15);
    }),
    PULSE(p -> p.poly(2, 12.5F, 6, 12.5F, 9, 5.5F, 14, 19, 17, 12.5F, 22, 12.5F)),
    SIGNAL(p -> {
        p.line(5, 19, 5, 16);
        p.line(10, 19, 10, 12.5F);
        p.line(15, 19, 15, 9);
        p.line(20, 19, 20, 5);
    }),
    COIN(p -> {
        p.ring(12, 12, 9);
        p.ring(12, 12, 5);
    }),
    SPARKLE(p -> {
        p.poly(12, 3, 13.8F, 10.2F, 21, 12, 13.8F, 13.8F, 12, 21, 10.2F, 13.8F, 3, 12, 10.2F, 10.2F, 12, 3);
    }),
    GRID(p -> {
        p.roundRect(4, 4, 10.5F, 10.5F, 1.5F);
        p.roundRect(13.5F, 4, 20, 10.5F, 1.5F);
        p.roundRect(4, 13.5F, 10.5F, 20, 1.5F);
        p.roundRect(13.5F, 13.5F, 20, 20, 1.5F);
    }),
    LIST(p -> {
        p.dot(5, 6.5F, 1.3F);
        p.dot(5, 12, 1.3F);
        p.dot(5, 17.5F, 1.3F);
        p.line(9, 6.5F, 20, 6.5F);
        p.line(9, 12, 20, 12);
        p.line(9, 17.5F, 20, 17.5F);
    }),
    EYE(p -> {
        p.arc(12, 20, 12, 222, 96);
        p.arc(12, 4, 12, 42, 96);
        p.ring(12, 12, 3);
    }),
    LOCK(p -> {
        p.roundRect(5, 11, 19, 20.5F, 2);
        p.arc(12, 11, 4.5F, 180, 180);
    }),
    REFRESH(p -> {
        p.arc(12, 12, 8, -60, 300);
        p.poly(16, 3.5F, 16, 7.5F, 20, 7.5F);
    }),
    POWER(p -> {
        p.arc(12, 13, 8, -50, 280);
        p.line(12, 3, 12, 11);
    }),
    CROSSHAIR(p -> {
        p.line(12, 3, 12, 9);
        p.line(12, 15, 12, 21);
        p.line(3, 12, 9, 12);
        p.line(15, 12, 21, 12);
    }),
    DROP(p -> {
        p.arc(12, 14.5F, 6, -15, 210);
        p.poly(6.2F, 13, 12, 3.5F, 17.8F, 13);
    }),
    BOLT(p -> p.polygon(13, 2.5F, 5, 13.5F, 11.5F, 13.5F, 10.5F, 21.5F, 19, 10, 12.5F, 10)),
    DOT(p -> p.dot(12, 12, 4.5F));

    private final Consumer<IconPen> painter;

    Icons(Consumer<IconPen> painter) {
        this.painter = painter;
    }

    void paint(IconPen pen) {
        painter.accept(pen);
    }
}
