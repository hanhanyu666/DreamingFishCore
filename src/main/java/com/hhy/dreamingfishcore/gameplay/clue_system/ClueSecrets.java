package com.hhy.dreamingfishcore.gameplay.clue_system;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条线索的**服主私密**定义：作者立场、证据包分组、隐藏证据关系和真伪说明。
 *
 * <p>单独一份文件（{@code config/dreamingfishcore/clue_secrets.json}）而不是跟正文放一起，
 * 是因为正文那份会随模组分发到客户端机器上——把真相字段写进去等于直接给玩家答案。
 * 服务端只在加载与运营时读这份文件，任何客户端视图与网络包都不得引用它的字段
 * （ADR 0008：不向玩家显示系统判定的真假与证据包分类）。</p>
 */
public final class ClueSecrets {

    public static final int MAX_NOTE = 2048;
    public static final int MAX_LINKS = 64;

    private String id = "";
    /** 作者在这份材料里的立场（作者自己相信什么，不等于事实）。 */
    private String authorStance = "";
    /** 证据包分组：同一项主张的四条材料归为一包；不向玩家显示。 */
    private String evidencePackId = "";
    /** 这份材料针对的核心主张。 */
    private String claimId = "";
    /** 相对于主张的关系：support / refute / ambient / context。 */
    private String relation = "";
    /** 服主备注：这份材料的真伪与陷阱说明。 */
    private String truthNote = "";
    /** 隐藏证据关系：与哪些线索互相印证或互相矛盾。 */
    private List<String> relatedClueIds = new ArrayList<>();
    /** 同一关键事实的替代来源：就算这条拿不到，也能从这些线索了解同一件事。 */
    private List<String> alternativeClueIds = new ArrayList<>();
    /** 发放入口声明（形如 {@code block=dreamingfishcore:xxx} / {@code container=...} / {@code event=...}）。 */
    private List<String> grantSources = new ArrayList<>();

    public ClueSecrets() {
    }

    public String id() {
        return id == null ? "" : id;
    }

    public String authorStance() {
        return authorStance == null ? "" : authorStance;
    }

    public String evidencePackId() {
        return evidencePackId == null ? "" : evidencePackId;
    }

    public String claimId() {
        return claimId == null ? "" : claimId;
    }

    public String relation() {
        return relation == null ? "" : relation;
    }

    public String truthNote() {
        return truthNote == null ? "" : truthNote;
    }

    public List<String> relatedClueIds() {
        return relatedClueIds == null ? List.of() : List.copyOf(relatedClueIds);
    }

    public List<String> alternativeClueIds() {
        return alternativeClueIds == null ? List.of() : List.copyOf(alternativeClueIds);
    }

    public List<String> grantSources() {
        return grantSources == null ? List.of() : List.copyOf(grantSources);
    }

    void validate() {
        if (id == null || !ClueDefinition.ID_PATTERN.matcher(id).matches()) {
            throw new IllegalStateException("线索私密定义 ID 非法：" + id);
        }
        for (String field : new String[]{authorStance, evidencePackId, claimId, relation}) {
            if (field != null && field.length() > ClueDefinition.MAX_META) {
                throw new IllegalStateException("线索私密字段过长：" + id);
            }
        }
        if (truthNote != null && truthNote.length() > MAX_NOTE) {
            throw new IllegalStateException("线索私密备注过长：" + id);
        }
        for (List<String> links : List.of(relatedClueIds(), alternativeClueIds(), grantSources())) {
            if (links.size() > MAX_LINKS) {
                throw new IllegalStateException("线索私密引用过多（上限 " + MAX_LINKS + "）：" + id);
            }
        }
    }
}
