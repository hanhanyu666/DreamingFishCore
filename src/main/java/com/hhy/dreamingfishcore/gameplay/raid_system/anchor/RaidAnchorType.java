package com.hhy.dreamingfishcore.gameplay.raid_system.anchor;

import java.util.Locale;
import java.util.Optional;

/**
 * 锚点类型（搜打撤设计稿 §3.1）。
 *
 * <p>锚点是"人工确认过的候选功能位置"，服务器不会在任意坐标生成内容，只从这些合法锚点里选。
 * 因此后面所有随机系统（撤离点、资源点、露天物品、刷怪点、地图变体、事件）都按类型从这里取候选。</p>
 */
public enum RaidAnchorType {
    /** 玩家出生点候选。 */
    PLAYER_SPAWN,
    /** 撤离点候选。 */
    EXTRACTION,
    /** 容器战利品点（箱子、保险柜、工具箱…）。 */
    CONTAINER_LOOT,
    /** 露天物品点（桌面、货架、地面上的静态战利品节点）。 */
    LOOSE_LOOT,
    /** 普通刷怪点。 */
    MOB_SPAWN,
    /** Boss 刷怪点。 */
    BOSS_SPAWN,
    /** 地图局部变体的控制点（对应一组可切换状态）。 */
    MAP_VARIANT,
    /** 随机事件点。 */
    EVENT,
    /** 门 / 路障等可切换方块。 */
    DOOR,
    /** 任务相关点位。 */
    QUEST;

    /**
     * 宽松解析：大小写与首尾空白都不敏感，认不出来就返回空（由调用方记为软失败，而不是抛异常）。
     *
     * <p>刻意不抛异常：单个锚点数据写错不该让整张图或服务器起不来。</p>
     */
    public static Optional<RaidAnchorType> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (RaidAnchorType type : values()) {
            if (type.name().equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** 供命令补全与报错提示使用：全部合法类型名。 */
    public static String[] names() {
        RaidAnchorType[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }
}
