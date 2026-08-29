package com.hhy.dreamingfishcore.server.notice_system;

import com.google.gson.annotations.SerializedName;

/**
 * 公告数据类
 */
public class NoticeData {
    @SerializedName("noticeId")
    private int noticeId;

    @SerializedName("noticeTitle")
    private String noticeTitle;

    @SerializedName("noticeContent")
    private String noticeContent;

    @SerializedName("publishTime")
    private long publishTime;

    @SerializedName("category")
    private NoticeCategory category = NoticeCategory.MAINTENANCE;

    @SerializedName("storyStageId")
    private String storyStageId = "";

    @SerializedName("storyDate")
    private String storyDate = "";

    @SerializedName("noticeKey")
    private String noticeKey = "";

    public NoticeData() {
    }

    public NoticeData(int noticeId, String noticeTitle, String noticeContent, long publishTime) {
        this(noticeId, noticeTitle, noticeContent, publishTime,
                NoticeCategory.MAINTENANCE, "", "", "");
    }

    public NoticeData(int noticeId, String noticeTitle, String noticeContent, long publishTime,
                      NoticeCategory category, String storyStageId, String storyDate, String noticeKey) {
        this.noticeId = noticeId;
        this.noticeTitle = noticeTitle;
        this.noticeContent = noticeContent;
        this.publishTime = publishTime;
        setCategory(category);
        setStoryStageId(storyStageId);
        setStoryDate(storyDate);
        setNoticeKey(noticeKey);
    }

    public int getNoticeId() {
        return noticeId;
    }

    public void setNoticeId(int noticeId) {
        this.noticeId = noticeId;
    }

    public String getNoticeTitle() {
        return noticeTitle;
    }

    public void setNoticeTitle(String noticeTitle) {
        this.noticeTitle = noticeTitle;
    }

    public String getNoticeContent() {
        return noticeContent;
    }

    public void setNoticeContent(String noticeContent) {
        this.noticeContent = noticeContent;
    }

    public long getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(long publishTime) {
        this.publishTime = publishTime;
    }

    public NoticeCategory getCategory() {
        return category == null ? NoticeCategory.MAINTENANCE : category;
    }

    public void setCategory(NoticeCategory category) {
        this.category = category == null ? NoticeCategory.MAINTENANCE : category;
    }

    public String getStoryStageId() {
        return normalize(storyStageId);
    }

    public void setStoryStageId(String storyStageId) {
        this.storyStageId = normalize(storyStageId);
    }

    public String getStoryDate() {
        return normalize(storyDate);
    }

    public void setStoryDate(String storyDate) {
        this.storyDate = normalize(storyDate);
    }

    public String getNoticeKey() {
        return normalize(noticeKey);
    }

    public void setNoticeKey(String noticeKey) {
        this.noticeKey = normalize(noticeKey);
    }

    public boolean isGameNotice() {
        return getCategory() == NoticeCategory.GAME;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        NoticeData that = (NoticeData) obj;
        // 只比较 noticeId，因为 ID 相同就是同一公告
        return noticeId == that.noticeId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(noticeId);
    }
}
