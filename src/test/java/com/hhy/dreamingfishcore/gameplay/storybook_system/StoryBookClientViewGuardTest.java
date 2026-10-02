package com.hhy.dreamingfishcore.gameplay.storybook_system;

import com.hhy.dreamingfishcore.gameplay.storybook_system.network.Packet_OpenStoryBookGUI;
import com.hhy.dreamingfishcore.gameplay.storybook_system.network.Packet_OpenStoryFragmentGUI;
import com.hhy.dreamingfishcore.gameplay.storybook_system.network.Packet_UpdateStoryBookOrder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验收标准的护栏：**玩家不能从客户端视图或网络数据读到隐藏真相**。
 *
 * <p>里程碑 2 把"作者立场 / 证据包分组 / 隐藏证据关系 / 真伪说明"放进了服主私密的
 * {@code ClueSecrets}，客户端只允许看到正文与四项论证元数据。这条界限靠人工 review 守不住
 * （以后有人顺手把 secrets 的字段加到视图上，编译还照样过），所以用反射把它钉死：</p>
 *
 * <ul>
 *   <li>客户端视图的字段必须落在白名单内；</li>
 *   <li>三个随记本网络包的任何字段/方法名都不得出现隐藏真相关键词。</li>
 * </ul>
 */
class StoryBookClientViewGuardTest {

    /** 客户端视图允许出现的字段：全部是玩家能直接看到的东西。 */
    private static final Set<String> ALLOWED_VIEW_FIELDS = Set.of(
            "clueId", "legacyId", "fragmentId", "stageId", "chapterId", "title", "content",
            "time", "authorName", "source", "observationSpan", "sample", "conditions", "read");

    /** 一旦出现在客户端可见类型里就说明泄漏的关键词。 */
    private static final List<String> FORBIDDEN_KEYWORDS = List.of(
            "stance", "evidencepack", "claim", "relation", "truth", "relatedclue",
            "alternativeclue", "grantsource", "secret");

    private static final List<Class<?>> CLIENT_FACING_TYPES = List.of(
            StoryBookEntryViewData.class,
            Packet_OpenStoryBookGUI.class,
            Packet_OpenStoryFragmentGUI.class,
            Packet_UpdateStoryBookOrder.class);

    @Test
    void clientViewOnlyExposesWhitelistedFields() {
        for (Field field : StoryBookEntryViewData.class.getDeclaredFields()) {
            assertTrue(ALLOWED_VIEW_FIELDS.contains(field.getName()),
                    "客户端视图出现了白名单之外的字段：" + field.getName()
                            + "（隐藏真相字段一律不得进客户端视图）");
        }
    }

    @Test
    void clientFacingTypesNeverMentionHiddenTruth() {
        for (Class<?> type : CLIENT_FACING_TYPES) {
            for (Field field : type.getDeclaredFields()) {
                assertFalse(mentionsHiddenTruth(field.getName()),
                        type.getSimpleName() + "." + field.getName() + " 看起来是隐藏真相字段");
            }
            for (Method method : type.getDeclaredMethods()) {
                assertFalse(mentionsHiddenTruth(method.getName()),
                        type.getSimpleName() + "#" + method.getName() + " 看起来会暴露隐藏真相");
            }
        }
    }

    @Test
    void secretsTypeKeepsTheHiddenFieldsOnTheServerSide() {
        Set<String> secretFields = Set.of("id", "authorStance", "evidencePackId", "claimId",
                "relation", "truthNote", "relatedClueIds", "alternativeClueIds", "grantSources");
        for (Field field : com.hhy.dreamingfishcore.gameplay.clue_system.ClueSecrets.class
                .getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;   // 静态常量（长度上限之类）不是"会下发的字段"
            }
            assertTrue(secretFields.contains(field.getName()),
                    "私密定义里出现了未登记的字段：" + field.getName()
                            + "（新增字段时请同步确认它不会被下发到客户端）");
        }
    }

    @Test
    void shortLabelFallsBackToTheStableIdTailForNewClues() {
        StoryBookEntryViewData legacy = new StoryBookEntryViewData(
                "dreamingfishcore:clue/observation_ward_log", 1, 1, 1, "标题", "正文", "时间",
                "署名", "来源", "1 天", "1 例", "条件", false);
        assertEquals("1", legacy.getShortLabel(), "有历史编号时优先显示历史编号");
        assertEquals(1, legacy.getFragmentId());

        StoryBookEntryViewData fresh = new StoryBookEntryViewData(
                "dreamingfishcore:clue/reading_gap_observation", 0, 1, 1, "标题", "正文", "时间",
                "署名", "来源", "1 夜", "1 例", "条件", false);
        assertEquals("reading_gap_observation", fresh.getShortLabel(),
                "新线索用稳定 ID 的最后一段作为短编号");
        assertEquals(0, fresh.getFragmentId());
    }

    private static boolean mentionsHiddenTruth(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
