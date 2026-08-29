package com.hhy.dreamingfishcore.gameplay.guidance_system;

/** 发送给客户端的个人引导只读视图。 */
public record GuidanceViewData(
        String recordId,
        String definitionId,
        int sourceNpcId,
        String sourceNpcName,
        String title,
        String content,
        String sourceQuote,
        String storyStageId,
        String locationLabel,
        String dimension,
        boolean hasLocation,
        int x,
        int y,
        int z,
        GuidanceEntry.Status status,
        long createdAtEpochMillis,
        long resolvedAtEpochMillis) {
    public GuidanceViewData {
        recordId = safe(recordId);
        definitionId = safe(definitionId);
        sourceNpcName = safe(sourceNpcName);
        title = safe(title);
        content = safe(content);
        sourceQuote = safe(sourceQuote);
        storyStageId = safe(storyStageId);
        locationLabel = safe(locationLabel);
        dimension = safe(dimension);
        status = status == null ? GuidanceEntry.Status.ACTIVE : status;
    }

    public static GuidanceViewData fromEntry(GuidanceEntry entry) {
        return new GuidanceViewData(
                entry.getRecordId(),
                entry.getDefinitionId(),
                entry.getSourceNpcId(),
                entry.getSourceNpcName(),
                entry.getTitle(),
                entry.getContent(),
                entry.getSourceQuote(),
                entry.getStoryStageId(),
                entry.getLocationLabel(),
                entry.getDimension(),
                entry.hasLocation(),
                entry.getX(),
                entry.getY(),
                entry.getZ(),
                entry.getStatus(),
                entry.getCreatedAtEpochMillis(),
                entry.getResolvedAtEpochMillis());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
