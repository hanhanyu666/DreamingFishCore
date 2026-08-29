package com.hhy.dreamingfishcore.gameplay.guidance_system;

import java.util.UUID;

/** 玩家实际接触剧情内容后形成的、服务端权威的个人引导记录。 */
public class GuidanceEntry {
    public enum Status {
        ACTIVE,
        RESOLVED,
        ARCHIVED
    }

    private String recordId = "";
    private String definitionId = "";
    private String sourceMessageRecordId = "";
    private int sourceNpcId;
    private String sourceNpcName = "";
    private String title = "";
    private String content = "";
    private String sourceQuote = "";
    private String storyStageId = "";
    private String locationLabel = "";
    private String dimension = "";
    private boolean hasLocation;
    private int x;
    private int y;
    private int z;
    private Status status = Status.ACTIVE;
    private long createdAtEpochMillis;
    private long resolvedAtEpochMillis;

    public GuidanceEntry() {
    }

    public static GuidanceEntry fromMessage(
            GuidanceSeed seed,
            String sourceMessageRecordId,
            int sourceNpcId,
            String sourceNpcName,
            String sourceQuote,
            long now) {
        GuidanceEntry entry = new GuidanceEntry();
        entry.recordId = UUID.randomUUID().toString();
        entry.definitionId = seed.getId();
        entry.sourceMessageRecordId = sourceMessageRecordId;
        entry.sourceNpcId = sourceNpcId;
        entry.sourceNpcName = sourceNpcName;
        entry.title = seed.getTitle();
        entry.content = seed.getContent();
        entry.sourceQuote = sourceQuote;
        entry.storyStageId = seed.getStoryStageId();
        entry.locationLabel = seed.getLocationLabel();
        entry.dimension = seed.getDimension();
        entry.hasLocation = seed.hasLocation();
        entry.x = seed.getX();
        entry.y = seed.getY();
        entry.z = seed.getZ();
        entry.status = Status.ACTIVE;
        entry.createdAtEpochMillis = now;
        return entry;
    }

    public String getRecordId() {
        return recordId == null ? "" : recordId;
    }

    public String getDefinitionId() {
        return definitionId == null ? "" : definitionId;
    }

    public String getSourceMessageRecordId() {
        return sourceMessageRecordId == null ? "" : sourceMessageRecordId;
    }

    public int getSourceNpcId() {
        return sourceNpcId;
    }

    public String getSourceNpcName() {
        return sourceNpcName == null ? "" : sourceNpcName;
    }

    public String getTitle() {
        return title == null ? "" : title;
    }

    public String getContent() {
        return content == null ? "" : content;
    }

    public String getSourceQuote() {
        return sourceQuote == null ? "" : sourceQuote;
    }

    public String getStoryStageId() {
        return storyStageId == null ? "" : storyStageId;
    }

    public String getLocationLabel() {
        return locationLabel == null ? "" : locationLabel;
    }

    public String getDimension() {
        return dimension == null ? "" : dimension;
    }

    public boolean hasLocation() {
        return hasLocation;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public Status getStatus() {
        return status == null ? Status.ACTIVE : status;
    }

    public long getCreatedAtEpochMillis() {
        return createdAtEpochMillis;
    }

    public long getResolvedAtEpochMillis() {
        return resolvedAtEpochMillis;
    }

    public boolean resolve(long now) {
        if (getStatus() != Status.ACTIVE) {
            return false;
        }
        status = Status.RESOLVED;
        resolvedAtEpochMillis = now;
        return true;
    }
}
