package com.hhy.dreamingfishcore.gameplay.organization_system;

import java.util.Locale;

/**
 * 组织名称的校验与归一（纯逻辑，可单测）。
 *
 * <p>长度按**码点**计算，避免中文名被按字节误判。名称与比较键都做归一：去首尾空白、
 * 比较时忽略大小写，避免"逐光会"和"逐光会 "被当成两个组织。</p>
 */
public final class OrganizationNames {

    /** 归一后用于查重的键。 */
    public static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 校验组织名称。
     *
     * @return {@code null} 表示通过；否则返回可直接显示给玩家的失败原因
     */
    public static String validate(String rawName, int minLength, int maxLength) {
        if (rawName == null) {
            return "组织名称不能为空";
        }
        String name = rawName.trim();
        if (name.isEmpty()) {
            return "组织名称不能为空";
        }
        int length = name.codePointCount(0, name.length());
        if (length < minLength) {
            return "组织名称至少 " + minLength + " 个字";
        }
        if (length > maxLength) {
            return "组织名称最多 " + maxLength + " 个字";
        }
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (Character.isWhitespace(character)) {
                return "组织名称不能包含空格或换行";
            }
            if (character == '§') {
                return "组织名称不能包含颜色代码";
            }
            if (Character.isISOControl(character)) {
                return "组织名称包含无效字符";
            }
        }
        return null;
    }

    /** 公告允许换行，但限制总长度并禁止颜色代码。 */
    public static String validateAnnouncement(String rawText, int maxLength) {
        if (rawText == null) {
            return null;
        }
        String text = rawText.strip();
        if (text.isEmpty()) {
            return null;
        }
        if (text.codePointCount(0, text.length()) > maxLength) {
            return "公告最多 " + maxLength + " 个字";
        }
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '§') {
                return "公告不能包含颜色代码";
            }
            if (Character.isISOControl(character) && character != '\n' && character != '\t') {
                return "公告包含无效字符";
            }
        }
        return null;
    }
}
