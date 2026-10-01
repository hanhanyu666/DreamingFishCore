package com.hhy.dreamingfishcore.gameplay.organization_system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 组织权限矩阵的行为锁定。
 *
 * <p>终端「组织」页按这里的判定决定画不画「踢 / 升 / 降 / 转让」按钮，服务端也按同一套逻辑
 * 决定批不批准，所以这组断言同时守着「界面画得出来」和「服务端真的允许」两边。</p>
 */
class OrganizationPermissionsTest {

    @Test
    void officersAndAboveMayReviewInviteAndEditAnnouncement() {
        for (OrganizationRank rank : new OrganizationRank[]{
                OrganizationRank.OFFICER, OrganizationRank.VICE_LEADER, OrganizationRank.LEADER}) {
            assertTrue(OrganizationPermissions.canReviewApplications(rank), rank + " 应能审批申请");
            assertTrue(OrganizationPermissions.canInvite(rank), rank + " 应能邀请");
            assertTrue(OrganizationPermissions.canEditAnnouncement(rank), rank + " 应能编辑公告");
        }
        assertFalse(OrganizationPermissions.canReviewApplications(OrganizationRank.MEMBER));
        assertFalse(OrganizationPermissions.canInvite(OrganizationRank.MEMBER));
        assertFalse(OrganizationPermissions.canEditAnnouncement(OrganizationRank.MEMBER));
        assertFalse(OrganizationPermissions.canInvite(null), "无职位不应有任何权限");
    }

    @Test
    void territoryManagementIsLimitedToLeaderAndViceLeader() {
        assertTrue(OrganizationPermissions.canManageTerritories(OrganizationRank.LEADER));
        assertTrue(OrganizationPermissions.canManageTerritories(OrganizationRank.VICE_LEADER));

        assertFalse(OrganizationPermissions.canManageTerritories(OrganizationRank.OFFICER),
                "干部不能登记/移除组织领地");
        assertFalse(OrganizationPermissions.canManageTerritories(OrganizationRank.MEMBER));
        assertFalse(OrganizationPermissions.canManageTerritories(null));
    }

    @Test
    void everyMemberMayDepositIntoTheFundPool() {
        // 捐款只进不出，所以不需要额外权限；没有职位（非成员）才挡住。
        for (OrganizationRank rank : OrganizationRank.values()) {
            assertTrue(OrganizationPermissions.canDepositFunds(rank), rank + " 应能捐款");
        }
        assertFalse(OrganizationPermissions.canDepositFunds(null));
    }

    @Test
    void kickRequiresStrictlyHigherRankAndNeverTargetsLeader() {
        assertTrue(OrganizationPermissions.canKick(OrganizationRank.LEADER, OrganizationRank.MEMBER));
        assertTrue(OrganizationPermissions.canKick(OrganizationRank.LEADER, OrganizationRank.OFFICER));
        assertTrue(OrganizationPermissions.canKick(OrganizationRank.LEADER, OrganizationRank.VICE_LEADER));
        assertTrue(OrganizationPermissions.canKick(OrganizationRank.OFFICER, OrganizationRank.MEMBER));

        assertFalse(OrganizationPermissions.canKick(OrganizationRank.OFFICER, OrganizationRank.OFFICER),
                "同级不能踢");
        assertFalse(OrganizationPermissions.canKick(OrganizationRank.VICE_LEADER, OrganizationRank.LEADER),
                "会长不能被任何人踢");
        assertFalse(OrganizationPermissions.canKick(OrganizationRank.MEMBER, OrganizationRank.MEMBER),
                "不能踢自己（同级判定即自我）");
        assertFalse(OrganizationPermissions.canKick(OrganizationRank.LEADER, null));
    }

    @Test
    void rankChangeNeverEscalatesToLeaderNorTouchesSelf() {
        assertTrue(OrganizationPermissions.canChangeRank(OrganizationRank.LEADER,
                OrganizationRank.MEMBER, OrganizationRank.OFFICER));
        assertTrue(OrganizationPermissions.canChangeRank(OrganizationRank.LEADER,
                OrganizationRank.OFFICER, OrganizationRank.VICE_LEADER));

        assertFalse(OrganizationPermissions.canChangeRank(OrganizationRank.LEADER,
                OrganizationRank.VICE_LEADER, OrganizationRank.LEADER),
                "会长也不能把别人提成会长，转让走独立入口");
        assertFalse(OrganizationPermissions.canChangeRank(OrganizationRank.LEADER,
                OrganizationRank.LEADER, OrganizationRank.MEMBER),
                "会长不能被降职");
        assertFalse(OrganizationPermissions.canChangeRank(OrganizationRank.LEADER,
                OrganizationRank.MEMBER, OrganizationRank.LEADER),
                "自己改自己的职位一律不允许（避免自我提权）");
    }

    @Test
    void viceLeaderOnlyMovesRanksBelowItself() {
        assertTrue(OrganizationPermissions.canChangeRank(OrganizationRank.VICE_LEADER,
                OrganizationRank.MEMBER, OrganizationRank.OFFICER));
        assertTrue(OrganizationPermissions.canChangeRank(OrganizationRank.VICE_LEADER,
                OrganizationRank.OFFICER, OrganizationRank.MEMBER));

        assertFalse(OrganizationPermissions.canChangeRank(OrganizationRank.VICE_LEADER,
                OrganizationRank.MEMBER, OrganizationRank.VICE_LEADER),
                "副会长不能造出同级");
        assertFalse(OrganizationPermissions.canChangeRank(OrganizationRank.VICE_LEADER,
                OrganizationRank.VICE_LEADER, OrganizationRank.MEMBER),
                "副会长之间互不管理");
    }

    @Test
    void officerCannotAdjustRanksAtAll() {
        assertFalse(OrganizationPermissions.canChangeRank(OrganizationRank.OFFICER,
                OrganizationRank.MEMBER, OrganizationRank.OFFICER),
                "干部不能造出与自己同级的职位");
        assertFalse(OrganizationPermissions.canChangeRank(OrganizationRank.OFFICER,
                OrganizationRank.MEMBER, OrganizationRank.MEMBER),
                "干部不能任免他人");
    }

    @Test
    void leaderOnlyActionsAreRestrictedToLeader() {
        assertTrue(OrganizationPermissions.canRename(OrganizationRank.LEADER));
        assertTrue(OrganizationPermissions.canTransferLeadership(OrganizationRank.LEADER));
        assertTrue(OrganizationPermissions.canDisband(OrganizationRank.LEADER));

        for (OrganizationRank rank : new OrganizationRank[]{
                OrganizationRank.MEMBER, OrganizationRank.OFFICER, OrganizationRank.VICE_LEADER}) {
            assertFalse(OrganizationPermissions.canRename(rank), rank + " 不能改名");
            assertFalse(OrganizationPermissions.canTransferLeadership(rank), rank + " 不能转让");
            assertFalse(OrganizationPermissions.canDisband(rank), rank + " 不能解散");
        }
        assertFalse(OrganizationPermissions.canRename(null));
        assertFalse(OrganizationPermissions.canDisband(null));
    }
}
