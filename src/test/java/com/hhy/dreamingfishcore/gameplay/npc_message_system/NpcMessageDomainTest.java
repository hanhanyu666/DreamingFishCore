package com.hhy.dreamingfishcore.gameplay.npc_message_system;

import com.hhy.dreamingfishcore.gameplay.npc_system.NpcRelationData;
import com.hhy.dreamingfishcore.gameplay.zhuiguang_system.ZhuiguangMembershipAction;
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
        NpcMessageRecord record = NpcMessageRecord.incoming(definition, "NPC", 10L);

        assertTrue(record.markReplied("first"));
        assertFalse(record.markReplied("second"));
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
                .requiringMembership(ZhuiguangMembershipRequirement.NON_MEMBER)
                .withMembershipAction(ZhuiguangMembershipAction.JOIN);

        assertFalse(memberMessage.isAvailableFor(99, true));
        assertFalse(memberMessage.isAvailableFor(100, false));
        assertTrue(memberMessage.isAvailableFor(100, true));
        assertTrue(joinReply.isAvailableFor(0, false));
        assertFalse(joinReply.isAvailableFor(0, true));
        assertEquals(ZhuiguangMembershipAction.JOIN, joinReply.getMembershipAction());
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
