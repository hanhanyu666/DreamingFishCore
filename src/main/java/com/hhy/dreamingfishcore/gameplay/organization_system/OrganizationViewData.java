package com.hhy.dreamingfishcore.gameplay.organization_system;

import java.util.List;

/**
 * 组织数据传给客户端的只读视图。
 *
 * <p>权限相关的布尔量（能否审批 / 能否邀请 / 能否编辑公告 / 能否改职位）**由服务端算好**再下发，
 * 客户端只负责按开关画按钮。这样权限矩阵只存在于 {@link OrganizationPermissions} 一处，
 * 不会出现"界面允许、服务端拒绝"的错位。</p>
 */
public final class OrganizationViewData {

    private OrganizationViewData() {
    }

    /** 玩家与某个组织的关系。 */
    public enum Relation {
        /** 无关系。 */
        NONE,
        /** 已提交申请，等待审批。 */
        APPLIED,
        /** 已被邀请，等待本人接受。 */
        INVITED,
        /** 已是成员。 */
        MEMBER
    }

    /** 浏览列表里的一条组织。 */
    public record Summary(String id, String name, int memberCount, String leaderName,
                          Relation relation) {
    }

    /** 成员 / 申请者 / 被邀请者共用的一行。 */
    public record MemberLine(String playerId, String name, String rankId, String rankName,
                             boolean online) {
    }

    /** 玩家自己所属组织的详情；没有组织时 {@code myOrganization} 为 null。 */
    public record Detail(String id, String name, String announcement,
                         String myRankId, String myRankName,
                         boolean canReviewApplications, boolean canInvite,
                         boolean canEditAnnouncement, boolean canManageMembers,
                         List<MemberLine> members, List<MemberLine> applicants,
                         List<MemberLine> invited, long createdAtEpochMillis) {
    }

    /** 一次同步的全部内容。 */
    public record Snapshot(boolean enabled, int maxMembers, int nameMaxLength,
                           int announcementMaxLength, int creationCost, String myOrganizationId,
                           List<Summary> organizations, Detail myOrganization) {
    }
}
