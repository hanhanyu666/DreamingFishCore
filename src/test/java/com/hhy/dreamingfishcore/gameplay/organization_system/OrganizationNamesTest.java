package com.hhy.dreamingfishcore.gameplay.organization_system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 组织名称 / 公告的校验与归一。 */
class OrganizationNamesTest {

    private static final int MIN = 2;
    private static final int MAX = 12;

    @Test
    void blankNamesAreRejected() {
        assertNotNull(OrganizationNames.validate(null, MIN, MAX));
        assertNotNull(OrganizationNames.validate("", MIN, MAX));
        assertNotNull(OrganizationNames.validate("     ", MIN, MAX));
    }

    @Test
    void lengthIsCountedByCodePoints() {
        assertNull(OrganizationNames.validate("逐光", MIN, MAX), "两个汉字应恰好满足下限");
        assertNotNull(OrganizationNames.validate("逐", MIN, MAX), "一个字不足下限");

        String twelve = "一二三四五六七八九十一二";
        assertEquals(12, twelve.codePointCount(0, twelve.length()));
        assertNull(OrganizationNames.validate(twelve, MIN, MAX), "恰好上限应通过");
        assertNotNull(OrganizationNames.validate(twelve + "三", MIN, MAX), "超过上限应被拒绝");
    }

    @Test
    void surroundingWhitespaceIsTrimmedBeforeValidation() {
        assertNull(OrganizationNames.validate("  逐光会  ", MIN, MAX),
                "首尾空白会被去掉，不应当成非法字符");
    }

    @Test
    void innerWhitespaceAndColorCodesAreRejected() {
        String innerSpace = OrganizationNames.validate("逐光 会", MIN, MAX);
        assertNotNull(innerSpace);
        assertTrue(innerSpace.contains("空格"), "提示应说清是空格问题：" + innerSpace);

        assertNotNull(OrganizationNames.validate("逐光§c会", MIN, MAX), "颜色代码必须拒绝");
        assertNotNull(OrganizationNames.validate("逐光\n会", MIN, MAX), "换行必须拒绝");
    }

    @Test
    void normalizeIgnoresCaseAndSurroundingSpace() {
        assertEquals(OrganizationNames.normalize("  Dreaming  "),
                OrganizationNames.normalize("dreaming"),
                "查重键要能吃掉大小写与首尾空白");
        assertEquals("", OrganizationNames.normalize(null));
    }

    @Test
    void announcementAllowsNewlinesButRejectsColorCodes() {
        assertNull(OrganizationNames.validateAnnouncement(null, 200), "空公告等价于没有公告");
        assertNull(OrganizationNames.validateAnnouncement("   ", 200));
        assertNull(OrganizationNames.validateAnnouncement("第一行\n第二行\t制表符", 200),
                "换行与制表符应当允许");
        assertNotNull(OrganizationNames.validateAnnouncement("公告§6颜色", 200));
        assertNotNull(OrganizationNames.validateAnnouncement("包含\u0000控制符", 200));
    }

    @Test
    void announcementLengthUsesCodePoints() {
        String text = "字".repeat(200);
        assertNull(OrganizationNames.validateAnnouncement(text, 200));
        assertNotNull(OrganizationNames.validateAnnouncement(text + "多", 200),
                "公告也按码点计数，中文不应被按字节误判");
    }

    @Test
    void rankParsingIsForgivingButRoundTrips() {
        for (OrganizationRank rank : OrganizationRank.values()) {
            assertEquals(rank, OrganizationRank.parse(rank.serializedName()),
                    "序列化名称必须能还原");
            assertFalse(rank.displayName().isBlank(), rank + " 需要中文显示名");
        }
        assertEquals(OrganizationRank.MEMBER, OrganizationRank.parse(null));
        assertEquals(OrganizationRank.MEMBER, OrganizationRank.parse("  "));
        assertEquals(OrganizationRank.MEMBER, OrganizationRank.parse("不存在的职位"));
        // 2026-10-02 起「副会长」更名为「管理员」：旧存档里存的还是 VICE_LEADER，
        // 必须映射到 ADMIN。只靠 valueOf 的容错会静默变成 MEMBER，等于把旧档降权。
        assertEquals(OrganizationRank.ADMIN, OrganizationRank.parse("vice_leader"),
                "旧职位名必须迁移为管理员（大小写不敏感）");
        assertEquals(OrganizationRank.ADMIN, OrganizationRank.parse("VICE_LEADER"));
        assertEquals(OrganizationRank.ADMIN, OrganizationRank.parse("  VICE_LEADER  "));
        assertEquals("ADMIN", OrganizationRank.ADMIN.serializedName(),
                "新存档写出的名字应为 ADMIN");
        assertEquals("管理员", OrganizationRank.ADMIN.displayName());
        assertEquals(2, OrganizationRank.ADMIN.weight(), "改名不改权重：与旧副会长同权限");
    }

    @Test
    void rankWeightDrivesComparison() {
        assertTrue(OrganizationRank.LEADER.isHigherThan(OrganizationRank.ADMIN));
        assertTrue(OrganizationRank.ADMIN.isHigherThan(OrganizationRank.OFFICER));
        assertTrue(OrganizationRank.OFFICER.isHigherThan(OrganizationRank.MEMBER));
        assertFalse(OrganizationRank.MEMBER.isHigherThan(OrganizationRank.MEMBER));
        assertTrue(OrganizationRank.MEMBER.atLeast(OrganizationRank.MEMBER));
        assertFalse(OrganizationRank.MEMBER.atLeast(OrganizationRank.OFFICER));
        assertFalse(OrganizationRank.LEADER.isHigherThan(null));
        assertFalse(OrganizationRank.LEADER.atLeast(null));
    }
}
