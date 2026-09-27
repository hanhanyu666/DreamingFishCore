package com.hhy.dreamingfishcore.gameplay.npc_message_system;

import com.hhy.dreamingfishcore.gameplay.npc_system.NpcRelationData;
import com.hhy.dreamingfishcore.gameplay.zhuiguang_system.ZhuiguangMembershipRequirement;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcMessageDomainTest {
    @Test
    void messageAndReplyUseIndependentFavorabilityRanges() {
        NpcMessageDefinition message = new NpcMessageDefinition(
                "dreamingfishcore:test/message",
                1,
                "test",
                "content",
                NpcMessageDefinition.DeliveryTrigger.INTERACTION)
                .requiringFavorability(100, 600);
        NpcMessageReplyDefinition reply = new NpcMessageReplyDefinition(
                "trusted_reply",
                "reply",
                1,
                "")
                .requiringFavorability(300, 1000);

        assertFalse(message.isAvailableAt(99));
        assertTrue(message.isAvailableAt(100));
        assertTrue(message.isAvailableAt(600));
        assertFalse(message.isAvailableAt(601));

        assertFalse(reply.isAvailableAt(299));
        assertTrue(reply.isAvailableAt(300));
    }

    @Test
    void anIncomingMessageCanOnlyBeRepliedToOnce() {
        NpcMessageDefinition definition = new NpcMessageDefinition(
                "dreamingfishcore:test/once",
                1,
                "test",
                "content",
                NpcMessageDefinition.DeliveryTrigger.MANUAL);
        NpcMessageRecord record = NpcMessageRecord.incoming(
                definition, "NPC", 10L, 0, false);

        assertTrue(record.markReplied("first"));
        assertFalse(record.markReplied("second"));
    }

    @Test
    void incomingMessageCapturesOnlyRepliesAvailableAtDeliveryTime() {
        NpcMessageReplyDefinition available = new NpcMessageReplyDefinition(
                "available", "当时可以选择", 5, "dreamingfishcore:follow_up")
                .requiringFavorability(10, 100);
        NpcMessageReplyDefinition hidden = new NpcMessageReplyDefinition(
                "hidden", "当时不可选择", 99, "")
                .requiringFavorability(200, 300);
        NpcMessageDefinition definition = new NpcMessageDefinition(
                "dreamingfishcore:test/snapshot",
                1,
                "test",
                "content",
                NpcMessageDefinition.DeliveryTrigger.MANUAL)
                .withReplies(java.util.List.of(available, hidden));

        NpcMessageRecord record = NpcMessageRecord.incoming(
                definition, "NPC", 10L, 50, false);

        assertTrue(record.hasReplySnapshot());
        assertEquals(1, record.getReplySnapshots().size());
        NpcReplySnapshot snapshot = record.getReplySnapshot("available");
        assertEquals("当时可以选择", snapshot.getText());
        assertEquals(5, snapshot.getFavorabilityDelta());
        assertEquals("dreamingfishcore:follow_up", snapshot.getFollowUpMessageId());
        org.junit.jupiter.api.Assertions.assertNull(record.getReplySnapshot("hidden"));
    }

    @Test
    void membershipRequirementsAreIndependentFromFavorability() {
        NpcMessageDefinition memberMessage = new NpcMessageDefinition(
                "dreamingfishcore:test/member_message",
                1,
                "member",
                "content",
                NpcMessageDefinition.DeliveryTrigger.MANUAL)
                .requiringFavorability(100, 600)
                .requiringMembership(ZhuiguangMembershipRequirement.MEMBER);
        NpcMessageReplyDefinition joinReply = new NpcMessageReplyDefinition(
                "join",
                "加入逐光会",
                0,
                "")
                .requiringMembership(ZhuiguangMembershipRequirement.NON_MEMBER);

        assertFalse(memberMessage.isAvailableFor(99, true));
        assertFalse(memberMessage.isAvailableFor(100, false));
        assertTrue(memberMessage.isAvailableFor(100, true));
        assertTrue(joinReply.isAvailableFor(0, false));
        assertFalse(joinReply.isAvailableFor(0, true));
    }

    @Test
    void aPersistedReplyEffectCannotIncreaseFavorabilityTwice() {
        NpcRelationData relation = new NpcRelationData(1, UUID.randomUUID());

        assertTrue(relation.applyFavorabilityEffect("message:reply", 5));
        assertFalse(relation.applyFavorabilityEffect("message:reply", 5));
        assertTrue(relation.applyFavorabilityEffect("another:reply", 2));
        org.junit.jupiter.api.Assertions.assertEquals(7, relation.getFavorability());
    }
}
