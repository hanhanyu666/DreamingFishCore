package com.hhy.dreamingfishcore.gameplay.organization_system;

import com.hhy.dreamingfishcore.server.economy_bridge.EconomySystemBridge;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 组织领地的纯查询逻辑：可选列表差集、坐标覆盖、领地视图的原点归一与面积。 */
class OrganizationTerritoryServiceTest {

    private static EconomySystemBridge.TerritoryInfo territory(
            String id, String dimension, int x1, int z1, int x2, int z2) {
        return new EconomySystemBridge.TerritoryInfo(
                id, "owner-uuid", "会长", "领地-" + id, dimension, x1, z1, x2, z2, 3);
    }

    @Test
    void availableExcludesTerritoriesAlreadyClaimedByAnyOrganization() {
        List<EconomySystemBridge.TerritoryInfo> owned = List.of(
                territory("a", "minecraft:overworld", 0, 0, 10, 10),
                territory("b", "minecraft:overworld", 0, 0, 10, 10),
                territory("c", "minecraft:overworld", 0, 0, 10, 10));

        List<EconomySystemBridge.TerritoryInfo> available =
                OrganizationTerritoryService.selectAvailable(owned, Set.of("b"));

        assertEquals(2, available.size());
        assertEquals(List.of("a", "c"), available.stream()
                .map(EconomySystemBridge.TerritoryInfo::territoryId).toList());
        assertTrue(OrganizationTerritoryService.selectAvailable(List.of(), Set.of()).isEmpty());
        assertTrue(OrganizationTerritoryService.selectAvailable(null, Set.of()).isEmpty());
    }

    @Test
    void coversPositionRequiresTheSameDimensionAndBothAxes() {
        EconomySystemBridge.TerritoryInfo info =
                territory("a", "minecraft:overworld", -20, -30, -10, -25);

        assertTrue(OrganizationTerritoryService.coversPosition(
                info, "minecraft:overworld", -15, -28), "边界内应覆盖");
        assertTrue(OrganizationTerritoryService.coversPosition(
                info, "minecraft:overworld", -20, -30), "角点也算覆盖");
        assertFalse(OrganizationTerritoryService.coversPosition(
                info, "minecraft:the_nether", -15, -28), "不同维度不算覆盖");
        assertFalse(OrganizationTerritoryService.coversPosition(
                info, "minecraft:overworld", -9, -28), "X 越界");
        assertFalse(OrganizationTerritoryService.coversPosition(
                info, "minecraft:overworld", -15, -24), "Z 越界");
        assertFalse(OrganizationTerritoryService.coversPosition(null, "minecraft:overworld", 0, 0));
    }

    @Test
    void territoryInfoNormalizesCornersAndComputesArea() {
        // EconomySystem 的两个选点顺序不保证，读取侧必须自己归一。
        EconomySystemBridge.TerritoryInfo info =
                territory("a", "minecraft:overworld", 30, 40, 10, 20);

        assertEquals(10, info.minX());
        assertEquals(20, info.minZ());
        assertEquals(30, info.maxX());
        assertEquals(40, info.maxZ());
        assertEquals(21 * 21, info.area());
        assertTrue(info.covers(30, 40));
    }

    @Test
    void registeredIdsAreReadOnlyCopiesOfTheOrganizationLedger() {
        Organization organization = new Organization("id", "测试", "leader", "会长", 0L);
        organization.registerTerritory("t-1", 5L);

        Set<String> ids = OrganizationTerritoryService.registeredIds(organization);

        assertTrue(ids.contains("t-1"));
        organization.unregisterTerritory("t-1");
        assertTrue(ids.contains("t-1"), "返回的是副本，不受后续修改影响");
        assertTrue(OrganizationTerritoryService.registeredIds(null).isEmpty());
    }
}
