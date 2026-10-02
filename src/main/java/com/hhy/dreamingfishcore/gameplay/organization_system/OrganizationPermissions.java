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

    /**
     * 登记或移除组织领地：管理员及以上（2026-10-02 起「副会长」更名为「管理员」，权限不变）。
     *
     * <p>领地登记涉及"把某人的私人财产挂到组织名下"，权限刻意比发公告更紧；
     * 往资金池捐钱则对所有成员开放，因为那只进不出。</p>
     */
    public static boolean canManageTerritories(OrganizationRank actor) {
        return actor != null && actor.atLeast(OrganizationRank.ADMIN);
    }

    /**
     * 登记/取消「核心领地」标记：会长与管理员（与领地登记同档）。
     *
     * <p>用户 2026-10-02 定的分级：核心领地的登记与标记只有会长/管理员能做，普通成员与干部不行。
     * 单独留一个谓词而不是复用 {@link #canManageTerritories}，是为了以后单独收紧/放宽标记权时
     * 只改这一处。</p>
     */
    public static boolean canManageCoreTerritories(OrganizationRank actor) {
        return actor != null && actor.atLeast(OrganizationRank.ADMIN);
    }

    /**
     * 能否把某块组织领地当作自己的场地/据点使用。
     *
     * <p>核心领地只有会长/管理员能用，普通成员与干部不行；非核心领地任意成员都能用。
     * 这条判定是给玩家场地系统（路线图里程碑 6）预留的准入：那套系统落地时直接调这里，
     * 不要另写第二份规则。</p>
     */
    public static boolean canUseTerritoryAsVenue(OrganizationRank actor, boolean core) {
        if (actor == null) {
            return false;
        }
        return !core || actor.atLeast(OrganizationRank.ADMIN);
    }

    /** 向组织资金池捐款：任何成员都可以（只进不出，不需要额外权限）。 */
    public static boolean canDepositFunds(OrganizationRank actor) {
        return actor != null;
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
     * <p>会长可以任免任何人；管理员只能在 {@code 干部 / 成员} 之间调动；干部与成员无权调整职位
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
        if (actor == OrganizationRank.ADMIN) {
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
