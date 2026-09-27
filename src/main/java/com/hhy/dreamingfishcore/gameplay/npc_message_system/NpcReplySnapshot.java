package com.hhy.dreamingfishcore.gameplay.npc_message_system;

/**
 * 投递私信时保存的一项回复快照。
 *
 * <p>回复按钮属于已经发生的通信事实。把显示文本、关系变化和后续消息一起保存，
 * 可以避免服主修改 {@code npc_messages.json} 后，玩家手里的旧消息突然出现
 * 不同按钮或不同好感度结果。组织身份等故事业务副作用不属于消息快照，
 * 必须由对应的 Java 状态机按稳定的消息/回复 ID 决定。资格条件在投递时已经
 * 判断过，因此快照不再保存当时的条件表达式。</p>
 */
public final class NpcReplySnapshot {
    private String id = "";
    private String text = "";
    private int favorabilityDelta;
    private String followUpMessageId = "";

    public NpcReplySnapshot() {
    }

    NpcReplySnapshot(NpcMessageReplyDefinition definition) {
        this.id = definition.getId();
        this.text = definition.getText();
        this.favorabilityDelta = definition.getFavorabilityDelta();
        this.followUpMessageId = definition.getFollowUpMessageId();
    }

    public String getId() {
        return id == null ? "" : id;
    }

    public String getText() {
        return text == null ? "" : text;
    }

    public int getFavorabilityDelta() {
        return favorabilityDelta;
    }

    public String getFollowUpMessageId() {
        return followUpMessageId == null ? "" : followUpMessageId;
    }

    NpcMessageReplyDefinition toDefinition() {
        return new NpcMessageReplyDefinition(
                getId(), getText(), favorabilityDelta, getFollowUpMessageId());
    }
}
