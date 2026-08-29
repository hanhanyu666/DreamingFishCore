package com.hhy.dreamingfishcore.gameplay.npc_message_system.client.cache;

import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcConversationViewData;

import java.util.List;

/** 当前客户端玩家的 NPC 私信只读快照。 */
public final class NpcMessageClientCache {
    private static List<NpcConversationViewData> conversations = List.of();
    private static boolean loaded;

    private NpcMessageClientCache() {
    }

    public static synchronized void set(List<NpcConversationViewData> snapshot) {
        conversations = snapshot == null ? List.of() : List.copyOf(snapshot);
        loaded = true;
    }

    public static synchronized List<NpcConversationViewData> getConversations() {
        return conversations;
    }

    public static synchronized NpcConversationViewData getConversation(int npcId) {
        return conversations.stream()
                .filter(conversation -> conversation.npcId() == npcId)
                .findFirst()
                .orElse(null);
    }

    public static synchronized int getUnreadCount() {
        return conversations.stream().mapToInt(NpcConversationViewData::unreadCount).sum();
    }

    public static synchronized boolean isLoaded() {
        return loaded;
    }

    public static synchronized void clear() {
        conversations = List.of();
        loaded = false;
    }
}
