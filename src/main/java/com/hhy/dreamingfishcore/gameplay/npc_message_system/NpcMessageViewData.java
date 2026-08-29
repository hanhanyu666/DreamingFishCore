package com.hhy.dreamingfishcore.gameplay.npc_message_system;

import java.util.List;

/** 客户端只读消息视图；可用回复由服务端按当前好感度筛选。 */
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
