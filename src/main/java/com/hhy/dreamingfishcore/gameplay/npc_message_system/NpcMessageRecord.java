package com.hhy.dreamingfishcore.gameplay.npc_message_system;

import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceSeed;

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
    private String favorabilityEffectId = "";
    private int favorabilityDelta;

    public NpcMessageRecord() {
    }

    public static NpcMessageRecord incoming(NpcMessageDefinition definition, String npcName, long now) {
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

    /** 仅供旧版内置私信快照迁移使用；不会改变已读、回复或发送时间。 */
    boolean replaceText(String subject, String content) {
        String safeSubject = subject == null ? "" : subject;
        String safeContent = content == null ? "" : content;
        if (getSubject().equals(safeSubject) && getContent().equals(safeContent)) {
            return false;
        }
        this.subject = safeSubject;
        this.content = safeContent;
        return true;
    }
}
