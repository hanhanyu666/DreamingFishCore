package com.hhy.dreamingfishcore.gameplay.task_location_system;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 剧情地点按名称识别：固定 ID 与名称关键词都要算命中。
 *
 * <p>这里只测纯判定逻辑（{@link StoryLocationResolver#matches}）；涉及
 * {@code TaskLocationManager} 的解析路径依赖 {@code FMLPaths}，单测环境不可用。</p>
 */
class StoryLocationResolverTest {

    private static final ResourceKey<Level> OVERWORLD = Level.OVERWORLD;

    private static TaskLocationDefinition location(String id, String name) {
        return new TaskLocationDefinition(id, name, OVERWORLD,
                new BlockPos(0, 0, 0), new BlockPos(16, 16, 16), TaskLocationMode.PROTECTED);
    }

    @Test
    void historicFixedIdsAreUnchanged() {
        // 旧服务器/旧存档仍按这两个 ID 建点，改动枚举时不能让它们漂移。
        assertEquals("dreamingfishcore:location_d105866ccdc84c4da7b017a7f13ec7d3",
                StoryLocationResolver.Role.ABYDOS.fixedId());
        assertEquals("dreamingfishcore:location_d41fd2b0cc77479c9e2017ae727fd117",
                StoryLocationResolver.Role.ZHUIGUANG.fixedId());
    }

    @Test
    void matchesByFixedIdEvenWithAnotherName() {
        TaskLocationDefinition renamed = location(
                StoryLocationResolver.Role.ZHUIGUANG.fixedId(), "旧医院");

        assertTrue(StoryLocationResolver.matches(StoryLocationResolver.Role.ZHUIGUANG, renamed));
        assertFalse(StoryLocationResolver.matches(StoryLocationResolver.Role.ABYDOS, renamed));
    }

    @Test
    void medicalReceptionNamesMatchZhuiguangRole() {
        for (String name : new String[]{"逐光会医疗接待点", "医疗接待区域", "逐光会区域", "医疗接待点", "逐光会基地"}) {
            assertTrue(StoryLocationResolver.matches(
                            StoryLocationResolver.Role.ZHUIGUANG, location("dreamingfishcore:location_x", name)),
                    "应按名称命中医疗接待点：" + name);
        }
    }

    @Test
    void abydosNameMatchesAbydosRole() {
        assertTrue(StoryLocationResolver.matches(StoryLocationResolver.Role.ABYDOS,
                location("dreamingfishcore:location_y", "阿拜多斯区域")));
        assertTrue(StoryLocationResolver.matches(StoryLocationResolver.Role.ABYDOS,
                location("dreamingfishcore:location_y", "阿拜多斯")));
    }

    @Test
    void unrelatedNamesDoNotMatch() {
        // 「旧医疗中心」含“医疗”但不含“医疗接待”，不应被当成接待点。
        assertFalse(StoryLocationResolver.matches(StoryLocationResolver.Role.ZHUIGUANG,
                location("dreamingfishcore:location_z", "旧医疗中心")));
        // 「逐光小队」含“逐光”但不含“逐光会”，也不算。
        assertFalse(StoryLocationResolver.matches(StoryLocationResolver.Role.ZHUIGUANG,
                location("dreamingfishcore:location_z", "逐光小队")));
        assertFalse(StoryLocationResolver.matches(StoryLocationResolver.Role.ABYDOS,
                location("dreamingfishcore:location_z", "临时安置点")));
    }

    @Test
    void keywordContainmentIsIntentionallyLoose() {
        // 关键词是“包含”匹配：名字里只要出现“逐光会”就算，哪怕还带了别的前后缀。
        // 这是刻意的取舍——宁可放宽，也不要让服主因为多打了两个字就“建了不认”。
        assertTrue(StoryLocationResolver.matches(StoryLocationResolver.Role.ZHUIGUANG,
                location("dreamingfishcore:location_z", "逐光会友")));
        assertTrue(StoryLocationResolver.matches(StoryLocationResolver.Role.ZHUIGUANG,
                location("dreamingfishcore:location_z", "新逐光会医疗接待点（临时）")));
    }

    @Test
    void rolesDoNotBleedIntoEachOther() {
        TaskLocationDefinition abydos = location("dreamingfishcore:location_a", "阿拜多斯区域");

        assertTrue(StoryLocationResolver.matches(StoryLocationResolver.Role.ABYDOS, abydos));
        assertFalse(StoryLocationResolver.matches(StoryLocationResolver.Role.ZHUIGUANG, abydos));
    }

    @Test
    void nameMatchingIgnoresSurroundingWhitespace() {
        TaskLocationDefinition padded = location("dreamingfishcore:location_w", "  逐光会医疗接待点  ");

        assertTrue(StoryLocationResolver.matches(StoryLocationResolver.Role.ZHUIGUANG, padded));
    }

    @Test
    void keywordMatchingIgnoresCase() {
        assertTrue(StoryLocationResolver.matches(StoryLocationResolver.Role.ABYDOS,
                location("dreamingfishcore:location_c", "ABYDOS 阿拜多斯")));
    }

    @Test
    void nullAndBlankInputsAreSafe() {
        assertFalse(StoryLocationResolver.matches(null, location("dreamingfishcore:location_n", "逐光会")));
        assertFalse(StoryLocationResolver.matches(StoryLocationResolver.Role.ZHUIGUANG, null));
        assertFalse(StoryLocationResolver.matches(StoryLocationResolver.Role.ZHUIGUANG,
                location("dreamingfishcore:location_n", null)));
        assertFalse(StoryLocationResolver.matches(StoryLocationResolver.Role.ZHUIGUANG,
                location("dreamingfishcore:location_n", "   ")));
    }

    @Test
    void matchesIdRejectsBlankAndUnknownWithoutThrowing() {
        // 地点系统未加载时 getLocation 会抛异常；matchesId 必须吞掉它并返回 false。
        assertFalse(StoryLocationResolver.matchesId(StoryLocationResolver.Role.ZHUIGUANG, null));
        assertFalse(StoryLocationResolver.matchesId(StoryLocationResolver.Role.ZHUIGUANG, "  "));
        assertFalse(StoryLocationResolver.matchesId(StoryLocationResolver.Role.ZHUIGUANG,
                "dreamingfishcore:location_not_loaded"));
        // 固定 ID 直接命中，不需要查表。
        assertTrue(StoryLocationResolver.matchesId(StoryLocationResolver.Role.ABYDOS,
                StoryLocationResolver.Role.ABYDOS.fixedId()));
    }
}
