package com.hhy.dreamingfishcore.gameplay.npc_message_system;

import com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamStory;
import com.hhy.dreamingfishcore.gameplay.opening_story_system.OpeningStory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInNpcMessageCatalogTest {
    @Test
    void returnsOnlyMessagesWhitelistedByTheCurrentStory() {
        List<NpcMessageDefinition> additions =
                BuiltInNpcMessageCatalog.createMissingMessages(List.of());

        // 当前开服切片：五条开场主线私信、一条余梦期救治私信、两条白芷随访私信，
        // 以及第三阶段（梦外行动）的联络人简报；旧协议、旧分支和身份壳 NPC 的历史内容
        // 不会重新进入内容包。
        assertEquals(9, additions.size());
        assertEquals(4, countMessagesForNpc(additions, 101));
        assertEquals(5, countMessagesForNpc(additions, 105));
        assertEquals(0, countMessagesForNpc(additions, 102));
        assertEquals(0, countMessagesForNpc(additions, 103));
        assertEquals(0, countMessagesForNpc(additions, 104));
        assertTrue(additions.stream().noneMatch(definition ->
                "dreamingfishcore:baizhi/first_stage_protocol".equals(definition.getId())));
        // 两条随访私信必须真的进入内容包，否则随访提醒会静默发不出去。
        assertTrue(findById(additions, AfterdreamStory.BAIZHI_FOLLOW_UP_THIRD_DAY_MESSAGE_ID) != null,
                "第 3 天随访私信应被保留");
        assertTrue(findById(additions, AfterdreamStory.BAIZHI_FOLLOW_UP_SEVENTH_DAY_MESSAGE_ID) != null,
                "第 7 天随访私信应被保留");
        // 梦外行动的简报也必须进包，否则第三阶段的任务 3001 永远无法完成
        // （它正是靠"读完这条私信"来当作服务端事实的）。
        NpcMessageDefinition briefing = findById(additions,
                com.hhy.dreamingfishcore.gameplay.extraction_story_system.ExtractionEraStory
                        .BRIEFING_MESSAGE_ID);
        assertEquals(105, briefing.getNpcId(), "简报由现有联络人（周岑）发出，避免为一条私信新增 NPC");

        NpcMessageDefinition introduction = findById(
                additions, OpeningStory.ZHOUCEN_INTRODUCTION_MESSAGE_ID);
        assertEquals(2, introduction.getReplies().size());
        assertTrue(introduction.getReplies().stream()
                .anyMatch(reply -> OpeningStory.JOIN_ZHUIGUANG_REPLY_ID
                        .equals(reply.getId())));
        assertTrue(introduction.getReplies().stream()
                .allMatch(reply -> reply.getFollowUpMessageId().isBlank()),
                "开场分支的后续消息必须由 Java 状态机选择");

        List<NpcMessageDefinition> secondPass =
                BuiltInNpcMessageCatalog.createMissingMessages(new ArrayList<>(additions));
        assertTrue(secondPass.isEmpty());
    }

    private static long countMessagesForNpc(
            List<NpcMessageDefinition> definitions, int npcId) {
        return definitions.stream()
                .filter(definition -> definition.getNpcId() == npcId)
                .count();
    }

    private static NpcMessageDefinition findById(
            List<NpcMessageDefinition> definitions, String id) {
        return definitions.stream()
                .filter(definition -> id.equals(definition.getId()))
                .findFirst()
                .orElseThrow();
    }
}
