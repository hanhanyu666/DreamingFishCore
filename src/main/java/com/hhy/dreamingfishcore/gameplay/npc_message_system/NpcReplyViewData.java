package com.hhy.dreamingfishcore.gameplay.npc_message_system;

/** 客户端当前可以选择的一项预设回复。 */
public record NpcReplyViewData(String id, String text) {
    public NpcReplyViewData {
        id = id == null ? "" : id;
        text = text == null ? "" : text;
    }
}
