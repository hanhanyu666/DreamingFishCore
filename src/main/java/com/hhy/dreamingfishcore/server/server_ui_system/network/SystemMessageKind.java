package com.hhy.dreamingfishcore.server.server_ui_system.network;

/** 右上角系统消息的事件类型，随 {@link Packet_SystemMessage} 发给客户端，客户端据此选图标与配色。 */
public enum SystemMessageKind {
    JOIN,
    LEAVE,
    /** 普通进度 */
    TASK,
    GOAL,
    CHALLENGE,
    DEATH;

    public boolean advancement() {
        return this == TASK || this == GOAL || this == CHALLENGE;
    }

    /** 网络包里按序号传，越界时视为没有类型。 */
    public static SystemMessageKind byId(int id) {
        SystemMessageKind[] values = values();
        return id >= 0 && id < values.length ? values[id] : null;
    }
}
