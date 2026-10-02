package com.hhy.dreamingfishcore.gameplay.blueprint_system;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 物品 ID 通配符匹配：默认放行、白名单、黑名单都靠它。 */
class ItemIdPatternTest {

    @Test
    void exactIdMatchesOnlyItself() {
        assertTrue(ItemIdPattern.matches("minecraft:oak_planks", "minecraft:oak_planks"));
        assertFalse(ItemIdPattern.matches("minecraft:oak_planks_slab", "minecraft:oak_planks"),
                "不带通配符的规则必须整串匹配，不能前缀命中");
        assertFalse(ItemIdPattern.matches("minecraft:oak_planks", "minecraft:oak_plank"));
    }

    @Test
    void suffixWildcardCrossesNamespaces() {
        assertTrue(ItemIdPattern.matches("minecraft:oak_planks", "*_planks"));
        assertTrue(ItemIdPattern.matches("othermod:weird_planks", "*_planks"),
                "不带命名空间的规则会跨模组命中，这是刻意的");
    }

    @Test
    void namespacedWildcardStaysInItsNamespace() {
        assertTrue(ItemIdPattern.matches("minecraft:oak_planks", "minecraft:*_planks"));
        assertFalse(ItemIdPattern.matches("othermod:oak_planks", "minecraft:*_planks"));
    }

    @Test
    void questionMarkMatchesExactlyOneCharacter() {
        assertTrue(ItemIdPattern.matches("minecraft:wooden_axe", "minecraft:wooden_?xe"));
        assertFalse(ItemIdPattern.matches("minecraft:wooden_axxe", "minecraft:wooden_?xe"),
                "? 只匹配一个字符");
    }

    @Test
    void matchingIsCaseInsensitiveAndTrims() {
        assertTrue(ItemIdPattern.matches("  MINECRAFT:Oak_Planks ", "minecraft:oak_planks"));
        assertTrue(ItemIdPattern.matches("minecraft:oak_planks", "  MINECRAFT:OAK_PLANKS  "));
    }

    @Test
    void blankOrNullNeverMatches() {
        assertFalse(ItemIdPattern.matches(null, "minecraft:oak_planks"));
        assertFalse(ItemIdPattern.matches("minecraft:oak_planks", null));
        assertFalse(ItemIdPattern.matches("minecraft:oak_planks", "   "));
        assertFalse(ItemIdPattern.matches("", "minecraft:oak_planks"));
    }

    @Test
    void regexSpecialCharactersAreLiteral() {
        // 模组 ID 里允许出现点号；规则里的点必须当普通字符，不能变成“任意字符”。
        assertTrue(ItemIdPattern.matches("somemod:foo.bar", "somemod:foo.bar"));
        assertFalse(ItemIdPattern.matches("somemod:fooXbar", "somemod:foo.bar"),
                "规则里的 . 不应被当成正则通配");
    }

    @Test
    void matchesAnyHandlesEmptyAndMixedLists() {
        assertFalse(ItemIdPattern.matchesAny("minecraft:oak_planks", List.of()));
        assertFalse(ItemIdPattern.matchesAny(null, List.of("minecraft:oak_planks")));
        assertTrue(ItemIdPattern.matchesAny("minecraft:oak_planks",
                List.of("minecraft:cobblestone", "minecraft:*_planks")));
    }
}
