package com.hhy.dreamingfishcore.gameplay.raid_system.anchor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 锚点标签的规矩（撤离点类型、条件、距离、可用次数都靠标签表达，所以标签处理必须稳）。
 */
class RaidAnchorTagsTest {

    private static RaidAnchor anchor(List<String> tags) {
        return new RaidAnchor("a1", RaidAnchorType.EXTRACTION, "factory", 1.0D, 2.0D, 3.0D,
                RaidAnchor.Rotation.NONE, "", tags, 100, true, 1.0D, RaidAnchor.Source.DEFINITION);
    }

    @Test
    void tagsAreLowercasedDeduplicatedAndSorted() {
        RaidAnchor tagged = anchor(List.of()).withTags(List.of(" extract:Fixed ", "EXTRACT:fixed",
                "Uses:4", " ", "container:Safe"));
        assertEquals(List.of("container:safe", "extract:fixed", "uses:4"), tagged.tags(),
                "应统一小写、去重、按字典序排列");
    }

    @Test
    void addingAndRemovingSingleTags() {
        RaidAnchor base = anchor(List.of("extract:random"));
        assertEquals(List.of("extract:random", "uses:4"),
                base.withTags(base.tagsWith("uses:4", true)).tags());

        RaidAnchor withUse = base.withTags(List.of("extract:random", "uses:4"));
        assertEquals(List.of("extract:random"), withUse.withTags(withUse.tagsWith("USES:4", false)).tags(),
                "移除标签应大小写不敏感");
    }

    @Test
    void removingSomethingThatIsNotThereIsHarmless() {
        RaidAnchor base = anchor(List.of("extract:fixed"));
        RaidAnchor after = base.withTags(base.tagsWith("container:safe", false));
        assertEquals(List.of("extract:fixed"), after.tags(), "删不存在的标签不该影响别的标签");
    }

    @Test
    void nullAndBlankTagsAreDropped() {
        assertTrue(anchor(List.of()).withTags(null).tags().isEmpty());
        assertTrue(anchor(List.of("a")).withTags(java.util.Arrays.asList(null, "", "  ")).tags().isEmpty(),
                "空白与 null 标签都应被丢掉");
    }

    @Test
    void otherFieldsSurviveTagging() {
        RaidAnchor base = anchor(List.of());
        RaidAnchor tagged = base.withTags(List.of("uses:4"));
        assertEquals(base.id(), tagged.id());
        assertEquals(base.type(), tagged.type());
        assertEquals(base.zone(), tagged.zone());
        assertEquals(base.x(), tagged.x());
        assertEquals(base.weight(), tagged.weight());
        assertEquals(base.enabled(), tagged.enabled());
    }
}
