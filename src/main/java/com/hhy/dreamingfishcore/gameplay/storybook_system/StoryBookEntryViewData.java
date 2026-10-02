package com.hhy.dreamingfishcore.gameplay.storybook_system;

/**
 * 随记本条目的**客户端视图**（里程碑 2）。
 *
 * <p>这里只允许出现玩家能看到的东西：正文、署名、时间、来源、观察跨度、样本、观察条件。
 * {@code ClueSecrets} 里的作者立场、证据包分组、隐藏证据关系、真伪说明**一律不得加进来** ——
 * 客户端视图就是网络包的内容，加了就等于把答案发给玩家（ADR 0008）。
 * {@code StoryBookClientViewGuardTest} 用反射盯着这条底线。</p>
 */
public class StoryBookEntryViewData {
    private final String clueId;
    /** 旧整数编号；0 表示这条线索没有历史编号。仅用于兼容显示，不参与逻辑。 */
    private final int legacyId;
    private final int stageId;
    private final int chapterId;
    private final String title;
    private final String content;
    private final String time;
    private final String authorName;
    private final String source;
    private final String observationSpan;
    private final String sample;
    private final String conditions;
    private final boolean read;

    public StoryBookEntryViewData(String clueId, int legacyId, int stageId, int chapterId,
                                  String title, String content, String time, String authorName,
                                  String source, String observationSpan, String sample,
                                  String conditions, boolean read) {
        this.clueId = clueId == null ? "" : clueId;
        this.legacyId = legacyId;
        this.stageId = stageId;
        this.chapterId = chapterId;
        this.title = title == null ? "" : title;
        this.content = content == null ? "" : content;
        this.time = time == null ? "" : time;
        this.authorName = authorName == null ? "" : authorName;
        this.source = source == null ? "" : source;
        this.observationSpan = observationSpan == null ? "" : observationSpan;
        this.sample = sample == null ? "" : sample;
        this.conditions = conditions == null ? "" : conditions;
        this.read = read;
    }

    public String getClueId() {
        return clueId;
    }

    /** 兼容旧客户端与旧显示：返回历史编号，没有则 0。 */
    public int getFragmentId() {
        return legacyId;
    }

    public int getLegacyId() {
        return legacyId;
    }

    public int getStageId() {
        return stageId;
    }

    public int getChapterId() {
        return chapterId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getTime() {
        return time;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getSource() {
        return source;
    }

    public String getObservationSpan() {
        return observationSpan;
    }

    public String getSample() {
        return sample;
    }

    public String getConditions() {
        return conditions;
    }

    public boolean isRead() {
        return read;
    }

    /** 卡片上的短编号：优先历史编号，新线索用稳定 ID 的最后一段。 */
    public String getShortLabel() {
        if (legacyId > 0) {
            return String.valueOf(legacyId);
        }
        int slash = clueId.lastIndexOf('/');
        return slash < 0 ? clueId : clueId.substring(slash + 1);
    }
}
