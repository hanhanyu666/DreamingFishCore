package com.hhy.dreamingfishcore.client.ui.loading;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * 断线原因归类：生命信号耗尽（永久死亡等待救援）、封禁、普通连接失败，
 * 并从服务器文本中提取尸体位置、封禁原因与解封时间。
 */
public final class DisconnectReason {
    public static final int GENERIC_ACCENT = 0xFFD96A62;
    public static final int BAN_ACCENT = 0xFFE15C55;
    public static final int DEATH_ACCENT = 0xFFD54A45;

    private final String raw;

    public DisconnectReason(@Nullable Component reason) {
        raw = plain(reason == null ? "连接被远端服务器关闭" : reason.getString());
    }

    public boolean isPermaDeath() {
        return raw.contains("复活点数耗尽") || raw.contains("细胞分裂") || raw.contains("等待一名幸存者");
    }

    public boolean isBan() {
        String reason = (" " + raw + " ").toLowerCase(Locale.ROOT);
        return reason.contains("banned") || reason.contains(" ban ") || reason.contains("封禁") || reason.contains("禁止进入");
    }

    public int accent() {
        if (isPermaDeath()) {
            return DEATH_ACCENT;
        }
        return isBan() ? BAN_ACCENT : GENERIC_ACCENT;
    }

    public String title() {
        if (isPermaDeath()) {
            return "生命信号耗尽";
        }
        return isBan() ? "访问被服务器拒绝" : "连接失败";
    }

    public String state() {
        if (isPermaDeath()) {
            return "等待救援";
        }
        if (isBan()) {
            return expiration() == null ? "永久封禁" : "临时封禁";
        }
        return "信号中断";
    }

    public String detail() {
        String detail;
        if (isPermaDeath()) {
            detail = "等待其他幸存者使用复活护符救援";
            String corpse = corpseLocation();
            if (corpse != null) {
                detail += "\n尸体位置：" + corpse;
            }
        } else if (isBan()) {
            detail = restrictedReason();
            String expiration = expiration();
            if (expiration != null) {
                detail += "\n解封于 " + expiration;
            }
        } else {
            detail = raw;
        }
        return detail.isBlank() ? "服务器未提供详细原因" : detail;
    }

    @Nullable
    private String corpseLocation() {
        String lower = raw.toLowerCase(Locale.ROOT);
        for (String marker : new String[]{"尸体位置：", "尸体位置:", "corpse location:"}) {
            int index = lower.indexOf(marker.toLowerCase(Locale.ROOT));
            if (index < 0) {
                continue;
            }
            String value = beforeAny(raw.substring(index + marker.length()).trim(),
                    "\n", "your ban will be removed on", "解封时间", "解封于").trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String restrictedReason() {
        String lower = raw.toLowerCase(Locale.ROOT);
        for (String marker : new String[]{"reason:", "原因：", "原因:"}) {
            int index = lower.indexOf(marker.toLowerCase(Locale.ROOT));
            if (index >= 0) {
                String extracted = beforeAny(raw.substring(index + marker.length()).trim(),
                        "\n", "your ban will be removed on", "解封时间", "解封于").trim();
                if (!extracted.isBlank()) {
                    return extracted;
                }
            }
        }
        for (String line : raw.split("\\R")) {
            String clean = line.trim();
            String lineLower = clean.toLowerCase(Locale.ROOT);
            if (!clean.isBlank() && !lineLower.contains("banned") && !clean.contains("封禁")
                    && !lineLower.contains("ban will be removed") && !clean.contains("解封")) {
                return clean;
            }
        }
        return raw;
    }

    @Nullable
    private String expiration() {
        String lower = raw.toLowerCase(Locale.ROOT);
        for (String marker : new String[]{"your ban will be removed on", "解封时间：", "解封时间:", "解封于"}) {
            int index = lower.indexOf(marker.toLowerCase(Locale.ROOT));
            if (index >= 0) {
                String value = beforeAny(raw.substring(index + marker.length()).trim(), "\n").trim();
                return value.isBlank() ? null : value;
            }
        }
        return null;
    }

    private static String beforeAny(String source, String... delimiters) {
        int end = source.length();
        String lower = source.toLowerCase(Locale.ROOT);
        for (String delimiter : delimiters) {
            int index = lower.indexOf(delimiter.toLowerCase(Locale.ROOT));
            if (index >= 0) {
                end = Math.min(end, index);
            }
        }
        return source.substring(0, end);
    }

    private static String plain(String text) {
        String stripped = ChatFormatting.stripFormatting(text == null ? "" : text);
        return stripped == null ? "" : stripped.replace('\r', '\n').replaceAll("\\n{2,}", "\n").trim();
    }
}
