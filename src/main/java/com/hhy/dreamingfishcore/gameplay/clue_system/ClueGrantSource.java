package com.hhy.dreamingfishcore.gameplay.clue_system;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 一条发放入口声明：{@code <类型>=<键>}，例如 {@code container=minecraft:chest}。
 *
 * <p>解析失败一律返回 {@code null}（而不是抛异常）：一条写错的声明只该让那条线索的
 * 那个入口失效，不该把整份私密文件拖垮，调用方负责记警告。</p>
 *
 * <p>校验只用 JDK 的 {@link Pattern}，不碰 Minecraft 的 {@code ResourceLocation}——
 * 这样单测不需要 bootstrap 注册表。</p>
 */
public record ClueGrantSource(ClueSourceType type, String key) {

    /** 键的长度上限，防止把莫名其妙的超长字符串塞进索引。 */
    public static final int MAX_KEY_LENGTH = 128;
    /** 命名空间 ID 形状（方块、容器）：{@code namespace:path}，全小写。 */
    private static final Pattern NAMESPACED_ID =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** 索引键：{@code <类型>:<键>}，例如 {@code container:minecraft:chest}。 */
    public String indexKey() {
        return indexKey(type, key);
    }

    /** 索引键的静态形式，供查询侧复用同一套拼法。 */
    public static String indexKey(ClueSourceType type, String key) {
        return type == null || key == null ? "" : type.token() + ":" + key;
    }

    /**
     * 一组声明里是否至少有一条能解析通过。
     *
     * <p>可达性审计和索引构建共用这一个判据，避免"审计说有入口、实际却没进索引"这种偏差。</p>
     */
    public static boolean hasValidDeclaration(List<String> rawSources) {
        if (rawSources == null) {
            return false;
        }
        for (String raw : rawSources) {
            if (parse(raw) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析一条声明。
     *
     * @return 解析并归一化后的声明；类型未知、缺 {@code =}、键为空/过长/形状不对时返回 null
     */
    public static ClueGrantSource parse(String raw) {
        if (raw == null) {
            return null;
        }
        int separator = raw.indexOf('=');
        if (separator <= 0) {
            return null;
        }
        ClueSourceType type = ClueSourceType.fromToken(raw.substring(0, separator));
        if (type == null) {
            return null;
        }
        String key = raw.substring(separator + 1).trim();
        if (key.isEmpty() || key.length() > MAX_KEY_LENGTH) {
            return null;
        }

        switch (type) {
            case BLOCK, CONTAINER -> {
                // 方块与容器必须写成命名空间 ID，写错基本等于这个入口永远不触发。
                key = key.toLowerCase(Locale.ROOT);
                if (!NAMESPACED_ID.matcher(key).matches()) {
                    return null;
                }
            }
            case NPC -> {
                // NPC 编号是整数；写成名字的话永远匹配不上，直接拒掉更容易发现问题。
                if (!key.chars().allMatch(Character::isDigit)) {
                    return null;
                }
            }
            case EVENT, BROADCAST -> key = key.toLowerCase(Locale.ROOT);
            case AREA -> {
                // 地点名可以是中文，保持原样（大小写敏感）。
            }
        }
        return new ClueGrantSource(type, key);
    }
}
