package com.hhy.dreamingfishcore.gameplay.blueprint_system;

import java.util.Collection;
import java.util.Locale;

/**
 * 物品 ID 的通配符匹配。
 *
 * <p>蓝图的默认放行列表、白名单与黑名单都用这套规则，服主可以直接在
 * {@code config/dreamingfishcore/blueprint.json} 里写成：</p>
 *
 * <ul>
 *   <li>{@code minecraft:oak_planks} —— 精确匹配（命名空间可以省略，见下）</li>
 *   <li>{@code minecraft:*_planks} —— 匹配该命名空间下所有以 {@code _planks} 结尾的物品</li>
 *   <li>{@code *_planks} —— 匹配**所有命名空间**下的木板（跨模组，慎用）</li>
 *   <li>{@code minecraft:wooden_?axe} —— {@code ?} 匹配任意单个字符</li>
 * </ul>
 *
 * <p>匹配对象始终是带命名空间的完整 ID（如 {@code minecraft:oak_planks}），
 * 也因此 {@code *_planks} 这种不带命名空间的写法会跨模组命中。大小写不敏感。</p>
 *
 * <p>纯字符串工具，不依赖 {@code ResourceLocation} / 注册表，因此可以在单测里直接跑。</p>
 */
public final class ItemIdPattern {

    private static final String REGEX_SPECIAL = ".+^$()[]{}|\\";

    private ItemIdPattern() {
    }

    /**
     * 单条规则是否命中该物品 ID。
     *
     * @param itemId  完整物品 ID，如 {@code minecraft:oak_planks}
     * @param pattern 规则，支持 {@code *} 与 {@code ?}
     * @return 空值或空规则一律返回 {@code false}（不命中），避免空规则意外放行一切
     */
    public static boolean matches(String itemId, String pattern) {
        if (itemId == null || pattern == null) {
            return false;
        }
        String id = itemId.trim().toLowerCase(Locale.ROOT);
        String pat = pattern.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty() || pat.isEmpty()) {
            return false;
        }
        return id.matches(toRegex(pat));
    }

    /** 规则列表里是否有任意一条命中。空列表返回 {@code false}。 */
    public static boolean matchesAny(String itemId, Collection<String> patterns) {
        if (itemId == null || patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (matches(itemId, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把通配符规则编译成正则。
     *
     * <p>不以 {@code *} 开头时锚定开头、不以 {@code *} 结尾时锚定结尾，
     * 所以 {@code minecraft:oak_planks} 不会误命中 {@code minecraft:oak_planks_slab}。</p>
     */
    static String toRegex(String pattern) {
        StringBuilder regex = new StringBuilder(pattern.length() + 8);
        if (!pattern.startsWith("*")) {
            regex.append('^');
        }
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') {
                regex.append(".*");
                continue;
            }
            if (c == '?') {
                regex.append('.');
                continue;
            }
            if (REGEX_SPECIAL.indexOf(c) >= 0) {
                regex.append('\\');
            }
            regex.append(c);
        }
        if (!pattern.endsWith("*")) {
            regex.append('$');
        }
        return regex.toString();
    }
}
