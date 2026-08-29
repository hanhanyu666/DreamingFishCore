package com.hhy.dreamingfishcore.gameplay.npc_message_system;

import java.util.ArrayList;
import java.util.List;

/** 单名玩家的 NPC 私信历史。 */
public class PlayerNpcMessageData {
    private List<NpcMessageRecord> messages = new ArrayList<>();

    public PlayerNpcMessageData() {
    }

    public List<NpcMessageRecord> getMessages() {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        return messages;
    }
}
