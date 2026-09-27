package com.hhy.dreamingfishcore.gameplay.npc_message_system;

import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceSeed;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 某名玩家已经实际收发的一条终端私信。 */
public class NpcMessageRecord {
    public enum Direction {
        NPC_TO_PLAYER,
        PLAYER_TO_NPC
    }

    private String recordId = "";
    private String definitionId = "";
    private int npcId;
    private String npcName = "";
    private String subject = "";
    private Direction direction = Direction.NPC_TO_PLAYER;
    private String content = "";
    private long sentAtEpochMillis;
    private boolean read;
    private String replyToRecordId = "";
    private String selectedReplyId = "";
    private GuidanceSeed guidanceSnapshot;
    /** 投递时固定的回复快照；空列表表示当时没有可用回复。 */
    private List<NpcReplySnapshot> replySnapshots = new ArrayList<>();
    private String favorabilityEffectId = "";
    private int favorabilityDelta;

    public NpcMessageRecord() {
    }

    /**
     * 创建带回复快照的入站消息。
     *
     * <p>只有当前确实满足好感度/成员条件的回复会进入快照，且数量与客户端展示
     * 上限一致。</p>
     */
    public static NpcMessageRecord incoming(
            NpcMessageDefinition definition,
            String npcName,
            long now,
            int favorability,
            boolean zhuiguangMember) {
        NpcMessageRecord record = new NpcMessageRecord();
        record.recordId = UUID.randomUUID().toString();
        record.definitionId = definition.getId();
        record.npcId = definition.getNpcId();
        record.npcName = npcName;
        record.subject = definition.getSubject();
        record.direction = Direction.NPC_TO_PLAYER;
        record.content = definition.getContent();
        record.sentAtEpochMillis = now;
        record.read = false;
        record.guidanceSnapshot = GuidanceSeed.copyOf(definition.getGuidance());
        record.replySnapshots = definition.getReplies().stream()
                .filter(reply -> reply != null
                        && reply.isAvailableFor(favorability, zhuiguangMember))
                .limit(NpcMessageManager.MAX_REPLY_OPTIONS)
                .map(NpcReplySnapshot::new)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        return record;
    }

    public static NpcMessageRecord outgoing(
            NpcMessageRecord source,
            NpcMessageReplyDefinition reply,
            long now) {
        NpcMessageRecord record = new NpcMessageRecord();
        record.recordId = UUID.randomUUID().toString();
        record.definitionId = source.getDefinitionId() + "#reply/" + reply.getId();
        record.npcId = source.getNpcId();
        record.npcName = source.getNpcName();
        record.direction = Direction.PLAYER_TO_NPC;
        record.content = reply.getText();
        record.sentAtEpochMillis = now;
        record.read = true;
        record.replyToRecordId = source.getRecordId();
        record.favorabilityEffectId = source.getRecordId() + ":" + reply.getId();
        record.favorabilityDelta = reply.getFavorabilityDelta();
        return record;
    }

    public String getRecordId() {
        return recordId == null ? "" : recordId;
    }

    public String getDefinitionId() {
        return definitionId == null ? "" : definitionId;
    }

    public int getNpcId() {
        return npcId;
    }

    public String getNpcName() {
        return npcName == null ? "" : npcName;
    }

    public String getSubject() {
        return subject == null ? "" : subject;
    }

    public Direction getDirection() {
        return direction == null ? Direction.NPC_TO_PLAYER : direction;
    }

    public String getContent() {
        return content == null ? "" : content;
    }

    public long getSentAtEpochMillis() {
        return sentAtEpochMillis;
    }

    public boolean isRead() {
        return read;
    }

    public String getReplyToRecordId() {
        return replyToRecordId == null ? "" : replyToRecordId;
    }

    public String getSelectedReplyId() {
        return selectedReplyId == null ? "" : selectedReplyId;
    }

    public boolean isReplied() {
        return !getSelectedReplyId().isBlank();
    }

    public GuidanceSeed getGuidanceSnapshot() {
        return guidanceSnapshot;
    }

    /** 返回投递时保存的回复快照。 */
    public List<NpcReplySnapshot> getReplySnapshots() {
        return replySnapshots == null
                ? List.of()
                : java.util.Collections.unmodifiableList(new ArrayList<>(replySnapshots));
    }

    public boolean hasReplySnapshot() {
        return replySnapshots != null;
    }

    public NpcReplySnapshot getReplySnapshot(String replyId) {
        if (replyId == null || replyId.isBlank()) {
            return null;
        }
        return getReplySnapshots().stream()
                .filter(snapshot -> snapshot != null && replyId.equals(snapshot.getId()))
                .findFirst()
                .orElse(null);
    }

    public String getFavorabilityEffectId() {
        return favorabilityEffectId == null ? "" : favorabilityEffectId;
    }

    public int getFavorabilityDelta() {
        return favorabilityDelta;
    }

    public boolean markRead() {
        if (read) {
            return false;
        }
        read = true;
        return true;
    }

    public boolean markReplied(String replyId) {
        if (isReplied() || replyId == null || replyId.isBlank()) {
            return false;
        }
        selectedReplyId = replyId;
        return true;
    }

}
