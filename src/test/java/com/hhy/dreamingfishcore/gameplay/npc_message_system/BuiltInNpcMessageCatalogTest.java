package com.hhy.dreamingfishcore.gameplay.npc_message_system;

import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceSeed;
import com.hhy.dreamingfishcore.gameplay.opening_story_system.OpeningStoryProgressManager;
import com.hhy.dreamingfishcore.gameplay.zhuiguang_system.ZhuiguangMembershipAction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInNpcMessageCatalogTest {
    @Test
    void openingCatalogAddsEveryNpcMessageOnlyOnce() {
        List<NpcMessageDefinition> existing = new ArrayList<>();
        List<NpcMessageDefinition> additions =
                BuiltInNpcMessageCatalog.createMissingMessages(existing);

        assertEquals(56, additions.size());
        assertEquals(4, countMessagesForNpc(additions, 1));
        assertEquals(13, countMessagesForNpc(additions, 100));
        assertEquals(11, countMessagesForNpc(additions, 101));
        assertEquals(6, countMessagesForNpc(additions, 102));
        assertEquals(12, countMessagesForNpc(additions, 103));
        assertEquals(6, countMessagesForNpc(additions, 104));
        assertEquals(4, countMessagesForNpc(additions, 105));

        NpcMessageDefinition baizhi = findById(
                additions, BuiltInNpcMessageCatalog.BAIZHI_FIRST_STAGE_PROTOCOL_ID);
        assertEquals(101, baizhi.getNpcId());
        assertEquals(BuiltInNpcMessageCatalog.BAIZHI_FIRST_STAGE_PROTOCOL_ID,
                baizhi.getId());
        assertEquals(BuiltInNpcMessageCatalog.BAIZHI_OBSERVATIONS_SUBJECT,
                baizhi.getSubject());
        assertFalse(baizhi.getContent().contains("第一阶段"));
        assertTrue(baizhi.getContent().contains("阿拜多斯的学校"));

        NpcMessageDefinition outerBand = findById(
                additions, BuiltInNpcMessageCatalog.OUTER_BAND_LOAD_SHED_ID);
        assertEquals(103, outerBand.getNpcId());
        assertFalse(outerBand.isOnce());

        NpcMessageDefinition introduction = findById(
                additions, OpeningStoryProgressManager.ZHOUCEN_INTRODUCTION_MESSAGE_ID);
        assertEquals("人类逐光联合会", introduction.getSubject());
        assertEquals(2, introduction.getReplies().size());
        assertEquals(ZhuiguangMembershipAction.JOIN,
                introduction.getReplies().stream()
                        .filter(reply -> OpeningStoryProgressManager.JOIN_ZHUIGUANG_REPLY_ID
                                .equals(reply.getId()))
                        .findFirst()
                        .orElseThrow()
                        .getMembershipAction());

        existing.addAll(additions);
        assertTrue(BuiltInNpcMessageCatalog.createMissingMessages(existing).isEmpty());
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

    @Test
    void legacyOpeningGuidanceMovesToTheCurrentFirstStage() {
        GuidanceSeed seed = new GuidanceSeed(
                "dreamingfishcore:guidance/test",
                "test",
                "test")
                .withStoryStage("dreamingfishcore:afterdream");
        NpcMessageDefinition definition = new NpcMessageDefinition(
                "dreamingfishcore:recorder/first_contact",
                1,
                "test",
                "test",
                NpcMessageDefinition.DeliveryTrigger.INTERACTION)
                .withGuidance(seed);

        assertTrue(BuiltInNpcMessageCatalog.migrateOpeningGuidanceStage(List.of(definition)));
        assertEquals(BuiltInNpcMessageCatalog.OPENING_STAGE_ID,
                definition.getGuidance().getStoryStageId());
        assertFalse(BuiltInNpcMessageCatalog.migrateOpeningGuidanceStage(List.of(definition)));
    }

    @Test
    void legacyBaizhiCopyMigratesWithoutOverwritingCustomizedText() {
        NpcMessageDefinition legacy = new NpcMessageDefinition(
                BuiltInNpcMessageCatalog.BAIZHI_FIRST_STAGE_PROTOCOL_ID,
                101,
                BuiltInNpcMessageCatalog.LEGACY_BAIZHI_PROTOCOL_SUBJECT,
                BuiltInNpcMessageCatalog.LEGACY_BAIZHI_PROTOCOL_CONTENT,
                NpcMessageDefinition.DeliveryTrigger.MANUAL);

        assertTrue(BuiltInNpcMessageCatalog.migrateBaizhiObservationsCopy(List.of(legacy)));
        assertEquals(BuiltInNpcMessageCatalog.BAIZHI_OBSERVATIONS_SUBJECT, legacy.getSubject());
        assertEquals(BuiltInNpcMessageCatalog.BAIZHI_OBSERVATIONS_CONTENT, legacy.getContent());
        assertFalse(BuiltInNpcMessageCatalog.migrateBaizhiObservationsCopy(List.of(legacy)));

        NpcMessageDefinition previousVersion = new NpcMessageDefinition(
                BuiltInNpcMessageCatalog.BAIZHI_FIRST_STAGE_PROTOCOL_ID,
                101,
                BuiltInNpcMessageCatalog.BAIZHI_OBSERVATIONS_SUBJECT,
                BuiltInNpcMessageCatalog.PREVIOUS_BAIZHI_OBSERVATIONS_CONTENT,
                NpcMessageDefinition.DeliveryTrigger.MANUAL);
        assertTrue(BuiltInNpcMessageCatalog.migrateBaizhiObservationsCopy(
                List.of(previousVersion)));
        assertEquals(BuiltInNpcMessageCatalog.BAIZHI_OBSERVATIONS_CONTENT,
                previousVersion.getContent());

        NpcMessageDefinition customized = new NpcMessageDefinition(
                BuiltInNpcMessageCatalog.BAIZHI_FIRST_STAGE_PROTOCOL_ID,
                101,
                "服主自定义标题",
                "服主自定义正文",
                NpcMessageDefinition.DeliveryTrigger.MANUAL);
        assertFalse(BuiltInNpcMessageCatalog.migrateBaizhiObservationsCopy(List.of(customized)));
        assertEquals("服主自定义标题", customized.getSubject());
        assertEquals("服主自定义正文", customized.getContent());
    }

    @Test
    void deliveredLegacyBaizhiCopyMigratesWithoutResettingReadState() {
        NpcMessageDefinition legacy = new NpcMessageDefinition(
                BuiltInNpcMessageCatalog.BAIZHI_FIRST_STAGE_PROTOCOL_ID,
                101,
                BuiltInNpcMessageCatalog.LEGACY_BAIZHI_PROTOCOL_SUBJECT,
                BuiltInNpcMessageCatalog.LEGACY_BAIZHI_PROTOCOL_CONTENT,
                NpcMessageDefinition.DeliveryTrigger.MANUAL);
        NpcMessageRecord record = NpcMessageRecord.incoming(legacy, "白芷", 42L);
        record.markRead();

        assertTrue(BuiltInNpcMessageCatalog.migrateDeliveredBaizhiObservations(List.of(record)));
        assertEquals(BuiltInNpcMessageCatalog.BAIZHI_OBSERVATIONS_SUBJECT, record.getSubject());
        assertEquals(BuiltInNpcMessageCatalog.BAIZHI_OBSERVATIONS_CONTENT, record.getContent());
        assertTrue(record.isRead());
        assertEquals(42L, record.getSentAtEpochMillis());
        assertFalse(BuiltInNpcMessageCatalog.migrateDeliveredBaizhiObservations(List.of(record)));
    }
}
