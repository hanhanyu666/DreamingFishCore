package com.hhy.dreamingfishcore.gameplay.organization_system;

import java.util.Locale;

/**
 * 玩家组织内的职位层级。
 *
 * <p>权限判定一律基于 {@link #weight()} 做比较（"{@code 管理别人}" = 权重更大），
 * 避免在每个入口重复写一串 if。</p>
 */
public enum OrganizationRank {

    MEMBER(0, "成员"),
    OFFICER(1, "干部"),
    ADMIN(2, "管理员"),
    LEADER(3, "会长");

    private final int weight;
    private final String displayName;

    OrganizationRank(int weight, String displayName) {
        this.weight = weight;
        this.displayName = displayName;
    }

    /** 数值越大权限越高。 */
    public int weight() {
        return weight;
    }

    /** 面向玩家的中文名称。 */
    public String displayName() {
        return displayName;
    }

    /** 该职位是否达到某职位（含相同）。 */
    public boolean atLeast(OrganizationRank other) {
        return other != null && weight >= other.weight;
    }

    /** 该职位是否高于某职位——用于“能否管理对方”。 */
    public boolean isHigherThan(OrganizationRank other) {
        return other != null && weight > other.weight;
    }

    /** 序列化用名称；读取时对未知值容错为 {@link #MEMBER}。 */
    public String serializedName() {
        return name();
    }

    /**
     * 旧职位名：2026-10-02 之前这个职位叫「副会长」。
     *
     * <p>旧存档里存的是这个枚举名，必须显式映射——否则 {@code valueOf} 抛异常后被下面的
     * 容错降级成 {@link #MEMBER}，等于把旧档里的副会长静默降权（人还在、权限没了）。</p>
     */
    private static final String LEGACY_ADMIN_NAME = "VICE_LEADER";

    public static OrganizationRank parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEMBER;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (LEGACY_ADMIN_NAME.equals(normalized)) {
            return ADMIN;
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return MEMBER;
        }
    }
}
