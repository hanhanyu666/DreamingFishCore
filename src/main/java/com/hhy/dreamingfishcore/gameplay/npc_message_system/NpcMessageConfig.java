package com.hhy.dreamingfishcore.gameplay.npc_message_system;

import java.util.ArrayList;
import java.util.List;

/** config/dreamingfishcore/npc_messages.json 的根对象。 */
public class NpcMessageConfig {
    private int schemaVersion = 1;
    private List<NpcMessageDefinition> messages = new ArrayList<>();

    public NpcMessageConfig() {
    }

    public NpcMessageConfig(List<NpcMessageDefinition> messages) {
        this.messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public List<NpcMessageDefinition> getMessages() {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        return messages;
    }
}
