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
    private String storyLineId = "";
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
        entry.storyLineId = seed.getStoryLineId();
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

    public String getStoryLineId() {
        return storyLineId == null || storyLineId.isBlank() ? getStoryStageId() : storyLineId;
    }

    public boolean updateProjection(GuidanceSeed seed) {
        if (getStoryLineId().equals(seed.getStoryLineId()) && getTitle().equals(seed.getTitle())
                && getContent().equals(seed.getContent()) && getLocationLabel().equals(seed.getLocationLabel())
                && getDimension().equals(seed.getDimension()) && hasLocation == seed.hasLocation()
                && x == seed.getX() && y == seed.getY() && z == seed.getZ()) {
            return false;
        }
        storyLineId = seed.getStoryLineId();
        title = seed.getTitle();
        content = seed.getContent();
        locationLabel = seed.getLocationLabel();
        dimension = seed.getDimension();
        hasLocation = seed.hasLocation();
        x = seed.getX();
        y = seed.getY();
        z = seed.getZ();
        return true;
    }

    public boolean isReplacedBy(GuidanceSeed seed) {
        return getStatus() == Status.ACTIVE && !getStoryStageId().isBlank()
                && getStoryStageId().equals(seed.getStoryStageId())
                && getStoryLineId().equals(seed.getStoryLineId())
                && !getDefinitionId().equals(seed.getId());
    }

    public boolean archive(long now) {
        if (getStatus() != Status.ACTIVE) {
            return false;
        }
        status = Status.ARCHIVED;
        resolvedAtEpochMillis = now;
        return true;
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

    /**
     * 重新建立当前剧情步骤的投影。
     *
     * <p>引导记录是历史日志，但同一个稳定定义在玩家重置本地开发存档、或服务器
     * 在“状态已写入、投影尚未写入”时重启后，仍需要再次成为当前待办。阶段脚本只会
     * 对尚未完成的步骤调用这个方法，因此不会把已经完成的剧情凭空倒退。</p>
     */
    public boolean reactivate() {
        if (getStatus() == Status.ACTIVE) {
            return false;
        }
        status = Status.ACTIVE;
        resolvedAtEpochMillis = 0L;
        return true;
    }
}
