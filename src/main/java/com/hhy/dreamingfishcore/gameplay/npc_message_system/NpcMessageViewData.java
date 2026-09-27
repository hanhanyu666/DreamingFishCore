package com.hhy.dreamingfishcore.gameplay.npc_message_system;

import java.util.List;

/**
 * 客户端只读消息视图。回复按钮始终来自投递时保存的快照；没有快照的历史记录
 * 仍可阅读，但不会重新获得交互按钮。
 */
public record NpcMessageViewData(
        String recordId,
        String definitionId,
        String subject,
        NpcMessageRecord.Direction direction,
        String content,
        long sentAtEpochMillis,
        boolean read,
        boolean replied,
        List<NpcReplyViewData> availableReplies) {
    public NpcMessageViewData {
        recordId = recordId == null ? "" : recordId;
        definitionId = definitionId == null ? "" : definitionId;
        subject = subject == null ? "" : subject;
        direction = direction == null ? NpcMessageRecord.Direction.NPC_TO_PLAYER : direction;
        content = content == null ? "" : content;
        availableReplies = availableReplies == null ? List.of() : List.copyOf(availableReplies);
    }
}
