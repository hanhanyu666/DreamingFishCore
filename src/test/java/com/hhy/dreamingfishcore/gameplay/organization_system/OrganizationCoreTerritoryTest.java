package com.hhy.dreamingfishcore.gameplay.organization_system;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 核心领地标记的存储规则（2026-10-02 新增的组织领地分级）。
 *
 * <p>这里只管存档层的不变量：标记只能落在已登记的领地上；取消登记要连标记一起摘；
 * 读档归一化要把指向不存在领地的孤儿标记清掉。权限判定在
 * {@code OrganizationPermissionsTest}，命令与终端入口在各自的测试里。</p>
 */
class OrganizationCoreTerritoryTest {

    private static Organization organizationWith(String... territoryIds) {
        Organization organization = new Organization("org-1", "测试组织", "leader-uuid", "会长", 1L);
        long now = 1_000L;
        for (String id : territoryIds) {
            organization.registerTerritory(id, now);
            now += 1_000L;
        }
        return organization;
    }

    @Test
    void coreMarkOnlyAppliesToRegisteredTerritories() {
        Organization organization = organizationWith("territory-a");

        assertFalse(organization.isCoreTerritory("territory-a"), "登记后默认不是核心领地");
        assertTrue(organization.setCoreTerritory("territory-a", true), "已登记的领地可以标记");
        assertTrue(organization.isCoreTerritory("territory-a"));

        assertFalse(organization.setCoreTerritory("territory-b", true),
                "没登记的领地不能被标记为核心领地");
        assertFalse(organization.isCoreTerritory("territory-b"));
        assertFalse(organization.isCoreTerritory(null));
        assertFalse(organization.isCoreTerritory("  "));

        assertFalse(organization.setCoreTerritory("territory-a", true),
                "重复标记应返回 false（没有变化）");
        assertTrue(organization.setCoreTerritory("territory-a", false), "取消标记应返回 true");
        assertFalse(organization.isCoreTerritory("territory-a"));
        assertFalse(organization.setCoreTerritory("territory-a", false),
                "重复取消应返回 false");
    }

    @Test
    void unregisteringTerritoryAlsoDropsItsCoreMark() {
        Organization organization = organizationWith("territory-a", "territory-b");
        organization.setCoreTerritory("territory-a", true);
        organization.setCoreTerritory("territory-b", true);

        assertTrue(organization.unregisterTerritory("territory-a"));

        assertFalse(organization.isCoreTerritory("territory-a"),
                "取消登记必须一起摘掉核心标记，否则会留下孤儿标记");
        assertTrue(organization.isCoreTerritory("territory-b"), "其它领地不受影响");
        assertEquals(List.of("territory-b"), List.copyOf(organization.coreTerritoryIds()));
    }

    @Test
    void normalizationDropsCoreMarksWithoutTerritory() {
        Organization organization = organizationWith("territory-a");
        // 模拟手改存档：标记里塞了一个未登记的 id
        organization.coreTerritoryIds().add("ghost-territory");

        assertTrue(organization.normalizeLinkageData(), "归一化应报告发生了变化");

        assertFalse(organization.coreTerritoryIds().contains("ghost-territory"),
                "指向未登记领地的核心标记应被清除");
        assertFalse(organization.isCoreTerritory("ghost-territory"));
    }

    @Test
    void normalizationIsIdempotentWhenEverythingIsConsistent() {
        Organization organization = organizationWith("territory-a");
        organization.setCoreTerritory("territory-a", true);

        assertFalse(organization.normalizeLinkageData(), "一致的数据不该被反复改写");
        assertTrue(organization.isCoreTerritory("territory-a"));
    }
}
