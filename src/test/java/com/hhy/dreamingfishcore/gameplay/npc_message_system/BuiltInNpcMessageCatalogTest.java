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

        // 当前开服切片只有五条开场主线私信、一条余梦期救治私信，以及终检后的
        // 两条白芷随访私信；旧协议、旧分支和身份壳 NPC 的历史内容不会重新进入内容包。
        assertEquals(8, additions.size());
        assertEquals(4, countMessagesForNpc(additions, 101));
        assertEquals(4, countMessagesForNpc(additions, 105));
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
