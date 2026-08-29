package com.hhy.dreamingfishcore.gameplay.guidance_system;

/**
 * 作者随 NPC 消息显式配置的引导来源。
 *
 * <p>它不是从自然语言自动提取出的任务；只有消息配置明确携带该对象时，
 * 玩家实际收到消息后才会生成个人引导记录。</p>
 */
public class GuidanceSeed {
    private String id = "";
    private String title = "";
    private String content = "";
    private String storyStageId = "";
    private String locationLabel = "";
    private String dimension = "";
    private boolean hasLocation;
    private int x;
    private int y;
    private int z;

    public GuidanceSeed() {
    }

    public GuidanceSeed(String id, String title, String content) {
        this.id = id;
        this.title = title;
        this.content = content;
    }

    public static GuidanceSeed copyOf(GuidanceSeed source) {
        if (source == null) {
            return null;
        }
        GuidanceSeed copy = new GuidanceSeed(source.getId(), source.getTitle(), source.getContent())
                .withStoryStage(source.getStoryStageId());
        if (source.hasLocation()) {
            copy.withLocation(
                    source.getLocationLabel(),
                    source.getDimension(),
                    source.getX(),
                    source.getY(),
                    source.getZ());
        }
        return copy;
    }

    public String getId() {
        return id == null ? "" : id;
    }

    public String getTitle() {
        return title == null ? "" : title;
    }

    public String getContent() {
        return content == null ? "" : content;
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

    public GuidanceSeed withStoryStage(String storyStageId) {
        this.storyStageId = storyStageId;
        return this;
    }

    public GuidanceSeed withLocation(String locationLabel, String dimension, int x, int y, int z) {
        this.locationLabel = locationLabel;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.hasLocation = true;
        return this;
    }
}
