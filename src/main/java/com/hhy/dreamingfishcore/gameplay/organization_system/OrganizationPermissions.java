package com.hhy.dreamingfishcore.gameplay.organization_system;

/**
 * 组织内的权限矩阵（纯逻辑，无副作用）。
 *
 * <p>命令与终端界面共用这里的判定，避免出现"命令能踢、界面不能踢"这类两处真相。
 * 所有方法都要求调用方把**行为发起者**与**被操作对象**的职位都传进来。</p>
 */
public final class OrganizationPermissions {

    private OrganizationPermissions() {
    }

    /** 审批入会申请：干部及以上。 */
    public static boolean canReviewApplications(OrganizationRank actor) {
        return actor != null && actor.atLeast(OrganizationRank.OFFICER);
    }

    /** 邀请他人加入：干部及以上。 */
    public static boolean canInvite(OrganizationRank actor) {
        return actor != null && actor.atLeast(OrganizationRank.OFFICER);
    }

    /** 编辑公告：干部及以上。 */
    public static boolean canEditAnnouncement(OrganizationRank actor) {
        return actor != null && actor.atLeast(OrganizationRank.OFFICER);
    }

    /** 踢人：必须高于对方；会长不能被任何人踢（也不能踢自己，由调用方挡住）。 */
    public static boolean canKick(OrganizationRank actor, OrganizationRank target) {
        return actor != null && target != null
                && actor.isHigherThan(target)
                && !target.atLeast(OrganizationRank.LEADER);
    }

    /**
     * 调整他人职位。
     *
     * <p>会长可以任免任何人；副会长只能在 {@code 干部 / 成员} 之间调动；干部与成员无权调整职位
     * （干部对成员唯一的"合法"操作是维持成员不变，那等于没有操作，所以直接禁止，避免出现
     * "命令能发、界面不给按钮"的两处真相）。任何人都不能把别人提升到自己同级或更高，
     * 也不能改自己的职位（避免自我提权），更不涉及会长 — 转让走独立入口。</p>
     */
    public static boolean canChangeRank(OrganizationRank actor, OrganizationRank target,
                                       OrganizationRank newRank) {
        if (actor == null || target == null || newRank == null) {
            return false;
        }
        if (target.atLeast(OrganizationRank.LEADER) || newRank.atLeast(OrganizationRank.LEADER)) {
            return false;
        }
        if (!actor.isHigherThan(target)) {
            return false;
        }
        if (!actor.isHigherThan(newRank)) {
            return false;
        }
        if (actor == OrganizationRank.LEADER) {
            return true;
        }
        if (actor == OrganizationRank.VICE_LEADER) {
            return newRank.atLeast(OrganizationRank.OFFICER) || newRank == OrganizationRank.MEMBER;
        }
        return false;
    }

    /** 改名：会长。 */
    public static boolean canRename(OrganizationRank actor) {
        return actor == OrganizationRank.LEADER;
    }

    /** 转让会长：会长。 */
    public static boolean canTransferLeadership(OrganizationRank actor) {
        return actor == OrganizationRank.LEADER;
    }

    /** 解散组织：会长（服主另可强制解散，走命令层）。 */
    public static boolean canDisband(OrganizationRank actor) {
        return actor == OrganizationRank.LEADER;
    }
}
