package com.hhy.dreamingfishcore.gameplay.organization_system;

/**
 * 组织操作的统一结果。
 *
 * <p>命令与终端界面都拿它来给玩家回话，避免同一件事在两处写出不同的措辞。</p>
 */
public record OrganizationResult(boolean success, String message) {

    public static OrganizationResult ok(String message) {
        return new OrganizationResult(true, message);
    }

    public static OrganizationResult fail(String message) {
        return new OrganizationResult(false, message);
    }
}
