package com.hhy.dreamingfishcore.gameplay.organization_system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 聚居地过滤装置的纯逻辑：设备状态语义、覆盖半径、维护到期判定。
 *
 * <p>涉及世界与方块的部分只能在 gametest / 实机验证；这里锁住的是那些最容易写错、
 * 又最容易被后续改动悄悄破坏的规则。</p>
 */
class SettlementFilterServiceTest {

    // ==================== 设备状态 ====================

    @Test
    void unboundDeviceNeverCountsAsActive() {
        SettlementFilterRegistry.Device device =
                new SettlementFilterRegistry.Device("minecraft:overworld", 10, 64, 20);

        assertFalse(device.bound(), "新设备默认未绑定");
        device.setActive(true);
        assertFalse(device.active(), "未绑定的设备即使被标了 active 也不算在工作");
        assertTrue(device.organizationId().isEmpty());

        device.setOrganizationId("org-1");
        assertTrue(device.bound());
        assertTrue(device.active(), "绑定之后 active 才生效");

        device.setOrganizationId("");
        assertFalse(device.active(), "解绑后立刻不算工作");
    }

    @Test
    void deviceKeyIsStableAndDimensionScoped() {
        SettlementFilterRegistry.Device device =
                new SettlementFilterRegistry.Device("minecraft:the_nether", 1, -2, 3);

        assertEquals("minecraft:the_nether|1|-2|3", device.key());
        assertEquals(device.key(),
                SettlementFilterRegistry.keyOf("minecraft:the_nether", 1, -2, 3));
        assertFalse(device.key().equals(SettlementFilterRegistry.keyOf("minecraft:overworld", 1, -2, 3)),
                "同坐标不同维度必须是两台设备");
    }

    @Test
    void deviceNormalizationRepairsBrokenSaveEntries() {
        SettlementFilterRegistry.Device device =
                new SettlementFilterRegistry.Device("minecraft:overworld", 0, 0, 0);
        device.setLastMaintenanceActiveTick(-99L);
        device.setOrganizationId(null);

        assertTrue(device.normalize(), "坏数据应报告发生了修改");
        assertEquals(-1L, device.lastMaintenanceActiveTick());
        assertEquals("", device.organizationId());
        assertFalse(device.normalize(), "归一之后应幂等");
    }

    // ==================== 抑制半径 ====================

    @Test
    void suppressionRangeIsAHorizontalRadius() {
        // 半径 32：正好在边界上算覆盖（<=），斜向按真实距离比较。
        assertTrue(SettlementFilterService.withinRange(0, 0, 32));
        assertTrue(SettlementFilterService.withinRange(32, 0, 32));
        assertTrue(SettlementFilterService.withinRange(-32, 0, 32));
        assertFalse(SettlementFilterService.withinRange(33, 0, 32));
        // 24/24 的斜向距离约 33.9，超出 32；23/23 约 32.5 也超出，
        // 因此用 22/22（约 31.1）验证"斜向确实按距离算"。
        assertTrue(SettlementFilterService.withinRange(22, 22, 32));
        assertFalse(SettlementFilterService.withinRange(23, 23, 32));
        assertFalse(SettlementFilterService.withinRange(0, 0, 0), "半径为 0 表示不覆盖");
        assertFalse(SettlementFilterService.withinRange(5, 5, -1));
    }

    // ==================== 维护到期 ====================

    @Test
    void maintenanceRunsImmediatelyForUnScheduledDevices() {
        // 刚绑定/刚解绑（-1）必须立刻维护一次，否则设备永远不会开始工作。
        assertTrue(SettlementFilterService.maintenanceDue(-1L, 100L, 24_000L));
        assertFalse(SettlementFilterService.maintenanceDue(-1L, -1L, 24_000L),
                "活动时钟不可用时不应维护");
    }

    @Test
    void maintenanceRespectsTheConfiguredInterval() {
        assertFalse(SettlementFilterService.maintenanceDue(1_000L, 24_999L, 24_000L),
                "不足一个周期不做维护");
        assertTrue(SettlementFilterService.maintenanceDue(1_000L, 25_000L, 24_000L));
        assertTrue(SettlementFilterService.maintenanceDue(1_000L, 90_000L, 24_000L),
                "离线很久后重新上线，一次维护即可补上");
        assertTrue(SettlementFilterService.maintenanceDue(5L, 6L, 0L),
                "周期写成 0 时按最小间隔处理，避免每 tick 都扣费以外的除零/负数问题");
    }
}
