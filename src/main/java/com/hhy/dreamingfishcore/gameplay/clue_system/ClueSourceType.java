package com.hhy.dreamingfishcore.gameplay.clue_system;

import java.util.Locale;

/**
 * 线索发放入口的种类（里程碑 2）。
 *
 * <p>每种入口对应一个玩家可观察的动作：挖掉方块、打开容器、和 NPC 说话、进入某片区域、
 * 读完一条公告，以及由剧情代码主动触发的事件。声明写在私密定义
 * （{@code config/dreamingfishcore/clue_secrets.json}）的 {@code grantSources} 里，
 * 形如 {@code container=minecraft:chest}，由 {@link ClueCatalog} 建成索引，
 * 触发点只负责"报出自己是谁"，不关心会发出哪条线索。</p>
 */
public enum ClueSourceType {

    /** 挖掉指定方块。 */
    BLOCK("block"),
    /** 打开指定容器（箱子、木桶之类）。 */
    CONTAINER("container"),
    /** 与指定编号的 NPC 交互（对话界面打开后）。 */
    NPC("npc"),
    /** 进入指定任务地点。 */
    AREA("area"),
    /** 读完指定公告。 */
    BROADCAST("broadcast"),
    /** 剧情事件：由 Java 在对应位置主动触发，键名写在代码常量里。 */
    EVENT("event");

    private final String token;

    ClueSourceType(String token) {
        this.token = token;
    }

    /** 声明里用的前缀，例如 {@code container}。 */
    public String token() {
        return token;
    }

    /** 从声明前缀解析；未知类型返回 null（调用方负责记警告并跳过这一条）。 */
    public static ClueSourceType fromToken(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        for (ClueSourceType type : values()) {
            if (type.token.equals(normalized)) {
                return type;
            }
        }
        return null;
    }

    /** 供日志与文档使用：列出全部合法前缀。 */
    public static String allTokens() {
        StringBuilder builder = new StringBuilder();
        for (ClueSourceType type : values()) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(type.token);
        }
        return builder.toString();
    }
}
