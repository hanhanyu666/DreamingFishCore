package com.hhy.dreamingfishcore.gameplay.disaster_system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule.EventType;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 天灾类型的定义（纯数据）。
 *
 * <p>新加的五种地形天灾靠"群系标签"限定生效范围，所以这里钉住两件事：</p>
 * <ul>
 *   <li>哪些是全世界生效、哪些绑定群系（绑错了会让天灾永远不触发，或到处乱触发）；</li>
 *   <li>标签用的是**原版真实存在**的群系标签（`is_ocean`/`is_beach`/`is_forest`/`is_taiga`/`is_badlands`）——
 *       原版**没有** `is_plains`，所以"天降火雨"刻意设为全世界，而不是绑一个不存在的标签。</li>
 * </ul>
 */
class DisasterTypeTest {

    @Test
    void thereAreEightDisasterTypes() {
        assertEquals(8, EventType.values().length, "三个通用天灾 + 五个地形天灾");
    }

    @Test
    void globalDisastersAffectEverywhere() {
        for (EventType type : new EventType[]{EventType.ZOMBIE_HORDE, EventType.FROST_NIGHT,
                EventType.STORM_DAY, EventType.METEOR_RAIN}) {
            assertFalse(type.isBiomeBound(), type + " 应该是全世界生效");
            assertTrue(type.biomeTags().isEmpty(), type + " 不该带群系标签");
        }
    }

    @Test
    void oceanBlightIsBoundToOceanAndBeach() {
        assertEquals(Set.of("minecraft:is_ocean", "minecraft:is_beach"),
                EventType.OCEAN_BLIGHT.biomeTags());
        assertTrue(EventType.OCEAN_BLIGHT.isBiomeBound());
    }

    @Test
    void sporePlagueIsBoundToForests() {
        assertEquals(Set.of("minecraft:is_forest", "minecraft:is_taiga"),
                EventType.SPORE_PLAGUE.biomeTags());
    }

    @Test
    void badlandsHasTwoDisasters() {
        assertEquals(Set.of("minecraft:is_badlands"), EventType.ACID_RAIN.biomeTags());
        assertEquals(Set.of("minecraft:is_badlands"), EventType.SANDSTORM.biomeTags());
        assertTrue(EventType.ACID_RAIN.isBiomeBound());
        assertTrue(EventType.SANDSTORM.isBiomeBound());
    }

    @Test
    void everyTypeHasAPositiveWeight() {
        for (EventType type : EventType.values()) {
            assertTrue(type.weight() > 0, type + " 的抽取权重必须为正，否则永远不会被抽到");
        }
    }

    @Test
    void terrainEventsAreTheOnesWithBiomes() {
        for (EventType type : EventType.values()) {
            assertEquals(type.isBiomeBound(), type.isTerrainEvent(),
                    type + " 的 isTerrainEvent 应与 isBiomeBound 一致");
        }
    }

    @Test
    void everyTypeActuallyShowsUpAcrossManySchedules() {
        Map<EventType, Integer> seen = new EnumMap<>(EventType.class);
        for (EventType type : EventType.values()) {
            seen.put(type, 0);
        }
        for (long seed = 0L; seed < 300L; seed++) {
            DisasterSchedule.generate(seed, 0L).days().forEach(plan ->
                    plan.events().forEach(type -> seen.merge(type, 1, Integer::sum)));
        }
        for (EventType type : EventType.values()) {
            assertTrue(seen.get(type) > 0, type + " 在 300 份日程里一次都没出现，权重或抽取逻辑有问题");
        }
    }

    @Test
    void displayNamesAreNonEmptyAndUnique() {
        Set<String> names = new java.util.HashSet<>();
        for (EventType type : EventType.values()) {
            assertFalse(type.displayName().isBlank(), type + " 缺少显示名");
            assertTrue(names.add(type.displayName()), "显示名重复：" + type.displayName());
        }
    }
}
