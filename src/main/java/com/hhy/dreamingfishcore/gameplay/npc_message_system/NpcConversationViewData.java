package com.hhy.dreamingfishcore.gameplay.npc_message_system;

import java.util.List;

/** 某名玩家与一个 NPC 的私信会话视图。 */
public record NpcConversationViewData(
        int npcId,
        String npcName,
        int favorability,
        String relationName,
        int unreadCount,
        long lastMessageAtEpochMillis,
        List<NpcMessageViewData> messages) {
    public NpcConversationViewData {
        npcName = npcName == null ? "" : npcName;
        relationName = relationName == null ? "" : relationName;
        unreadCount = Math.max(0, unreadCount);
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
