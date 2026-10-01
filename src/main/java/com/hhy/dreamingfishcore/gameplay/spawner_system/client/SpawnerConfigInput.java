package com.hhy.dreamingfishcore.gameplay.spawner_system.client;

/**
 * 配置界面输入框的解析（纯函数，便于单测）。
 */
public final class SpawnerConfigInput {

    private SpawnerConfigInput() {
    }

    /**
     * 解析输入框里的整数。
     *
     * @return 解析结果；空或非法返回 {@code null}，表示"这次不提交"
     */
    public static Integer parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
