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
    VICE_LEADER(2, "副会长"),
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

    public static OrganizationRank parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEMBER;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return MEMBER;
        }
    }
}
