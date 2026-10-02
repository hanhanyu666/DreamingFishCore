package com.hhy.dreamingfishcore.gameplay.clue_system;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条线索的**玩家可见**定义（里程碑 2）。
 *
 * <p>与旧 {@code FragmentData} 的区别：主键从 int 变成稳定字符串 ID（{@code legacyId} 保留旧编号做兼容），
 * 并补上 ADR 0008 要求的论证元数据：来源、观察跨度、样本、观察条件。</p>
 *
 * <p><b>这里只放玩家能看到的东西。</b>作者立场、证据包分组、隐藏证据关系、真伪说明一律不放，
 * 它们住在 {@link ClueSecrets}（服主私密文件，服务端从不装进客户端视图）。</p>
 */
public final class ClueDefinition {

    /** 稳定 ID 的合法形状：与任务地点/组织同风格。 */
    public static final java.util.regex.Pattern ID_PATTERN =
            java.util.regex.Pattern.compile("[a-z0-9][a-z0-9._:/-]{0,127}");

    public static final int MAX_TITLE = 512;
    public static final int MAX_CONTENT = 32768;
    public static final int MAX_META = 256;
    public static final int MAX_NOTES = 1024;

    private String id = "";
    /** 旧整数编号；0 表示这条线索没有历史编号。旧存档/物品/命令靠它兼容。 */
    private int legacyId;
    private int stageId = 1;
    private int chapterId;
    private String title = "";
    private String authorName = "";
    private String time = "";
    private String content = "";
    /** 来源：出具这份材料的机构、人物或地点。 */
    private String source = "";
    /** 观察跨度：这份材料覆盖了多长时间。 */
    private String observationSpan = "";
    /** 样本：观察/记录覆盖了多少对象。 */
    private String sample = "";
    /** 观察条件：在什么条件下得到这些记录。 */
    private String conditions = "";

    public ClueDefinition() {
    }

    public String id() {
        return id == null ? "" : id;
    }

    public int legacyId() {
        return legacyId;
    }

    public int stageId() {
        return stageId;
    }

    public int chapterId() {
        return chapterId;
    }

    public String title() {
        return title == null ? "" : title;
    }

    public String authorName() {
        return authorName == null ? "" : authorName;
    }

    public String time() {
        return time == null ? "" : time;
    }

    public String content() {
        return content == null ? "" : content;
    }

    public String source() {
        return source == null ? "" : source;
    }

    public String observationSpan() {
        return observationSpan == null ? "" : observationSpan;
    }

    public String sample() {
        return sample == null ? "" : sample;
    }

    public String conditions() {
        return conditions == null ? "" : conditions;
    }

    /** 正文按行拆分，供客户端渲染。 */
    public List<String> contentLines() {
        List<String> lines = new ArrayList<>();
        for (String line : content().split("\n", -1)) {
            lines.add(line);
        }
        return List.copyOf(lines);
    }

    /**
     * 校验并归一。抛出的消息直接进日志，便于服主定位是哪一条内容写坏了。
     */
    void validate() {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw new IllegalStateException("线索 ID 非法：" + id);
        }
        if (legacyId < 0) {
            throw new IllegalStateException("线索旧编号不能为负：" + id);
        }
        if (stageId <= 0) {
            throw new IllegalStateException("线索阶段必须为正：" + id);
        }
        if (chapterId < 0) {
            throw new IllegalStateException("线索章节不能为负：" + id);
        }
        requireText(title, MAX_TITLE, "标题", true);
        requireText(content, MAX_CONTENT, "正文", true);
        requireText(authorName, MAX_META, "署名", false);
        requireText(time, MAX_META, "时间", false);
        requireText(source, MAX_META, "来源", false);
        requireText(observationSpan, MAX_META, "观察跨度", false);
        requireText(sample, MAX_META, "样本", false);
        requireText(conditions, MAX_NOTES, "观察条件", false);
    }

    private void requireText(String value, int limit, String label, boolean required) {
        if (value == null) {
            throw new IllegalStateException("线索缺少" + label + "：" + id);
        }
        if (value.length() > limit) {
            throw new IllegalStateException("线索" + label + "过长（上限 " + limit + "）：" + id);
        }
        if (required && value.isBlank()) {
            throw new IllegalStateException("线索" + label + "不能为空：" + id);
        }
    }
}
