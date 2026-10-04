package com.hhy.dreamingfishcore.gameplay.raid_system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 参与者名单的纯逻辑测试（规则由服主定：只有开局在线的人算参与者、掉线给 5 分钟宽限）。
 *
 * <p>名单决定"本局还有没有人留在图里"，而这条会被自动结束条件使用，所以状态流转必须钉住。</p>
 */
class RaidRosterTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID CAROL = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    @Test
    void joiningMakesSomeoneAParticipantInRaid() {
        RaidRoster.Roster roster = new RaidRoster.Roster();
        roster.join(ALICE, "Alice");
        roster.join(BOB, "Bob");

        assertTrue(roster.isParticipant(ALICE));
        assertTrue(roster.isInRaid(ALICE));
        assertEquals(2, roster.count(RaidRoster.State.IN_RAID));
        assertEquals("Alice", roster.nameOf(ALICE));
    }

    @Test
    void someoneNeverRegisteredIsNotAParticipant() {
        RaidRoster.Roster roster = new RaidRoster.Roster();
        roster.join(ALICE, "Alice");

        assertFalse(roster.isParticipant(CAROL), "中途进来的人不在名单里，不算参与者");
        assertFalse(roster.isInRaid(CAROL));
        roster.set(CAROL, RaidRoster.State.DEAD);
        assertFalse(roster.isParticipant(CAROL), "没登记过的人不该被 set 塞进名单");
    }

    @Test
    void loggingOutStartsGraceAndComingBackRestores() {
        RaidRoster.Roster roster = new RaidRoster.Roster();
        long now = 1_700_000_000_000L;
        roster.join(ALICE, "Alice");

        roster.markLeft(ALICE, now);
        assertEquals(RaidRoster.State.LEFT, roster.stateOf(ALICE));
        assertTrue(roster.nobodyLeftInRaid(), "只剩掉线的人时也算没人留在图里");

        // 宽限内回来
        assertTrue(roster.handleLogin(ALICE, now + RaidRoster.GRACE_MILLIS - 1L), "宽限内回来应恢复");
        assertEquals(RaidRoster.State.IN_RAID, roster.stateOf(ALICE));
        assertFalse(roster.nobodyLeftInRaid(), "回来了就还在图里");
    }

    @Test
    void comingBackAfterGraceIsSettledAsNotExtracted() {
        RaidRoster.Roster roster = new RaidRoster.Roster();
        long now = 1_700_000_000_000L;
        roster.join(ALICE, "Alice");
        roster.markLeft(ALICE, now);

        assertFalse(roster.handleLogin(ALICE, now + RaidRoster.GRACE_MILLIS + 1L),
                "超过宽限回来不该恢复为在图里");
        assertEquals(RaidRoster.State.ABANDONED, roster.stateOf(ALICE), "按未撤离结算");
    }

    @Test
    void graceExpiryIsDrivenByTick() {
        RaidRoster.Roster roster = new RaidRoster.Roster();
        long now = 1_700_000_000_000L;
        roster.join(ALICE, "Alice");
        roster.join(BOB, "Bob");
        roster.markLeft(BOB, now);

        assertEquals(0, roster.expireGrace(now + 1000L), "还在宽限内不该超时");
        assertEquals(RaidRoster.State.LEFT, roster.stateOf(BOB));

        assertEquals(1, roster.expireGrace(now + RaidRoster.GRACE_MILLIS + 1L), "超时应被结算");
        assertEquals(RaidRoster.State.ABANDONED, roster.stateOf(BOB));
        assertEquals(1, roster.count(RaidRoster.State.IN_RAID), "Alice 不受影响");
    }

    @Test
    void extractedOrDeadPlayersStayOutEvenIfTheyLogIn() {
        RaidRoster.Roster roster = new RaidRoster.Roster();
        long now = 1_700_000_000_000L;
        roster.join(ALICE, "Alice");
        roster.join(BOB, "Bob");
        roster.set(ALICE, RaidRoster.State.EXTRACTED);
        roster.set(BOB, RaidRoster.State.DEAD);

        roster.markLeft(ALICE, now);         // 已撤离的人再掉线不该变成 LEFT
        assertEquals(RaidRoster.State.EXTRACTED, roster.stateOf(ALICE));
        roster.join(ALICE, "Alice");         // 重新登记
        assertEquals(RaidRoster.State.EXTRACTED, roster.stateOf(ALICE),
                "撤出去的人不该因为重新登录又变回在图里（否则自动结束永远不触发）");
        assertFalse(roster.handleLogin(BOB, now + 10L), "死过的人也不该恢复");
        assertEquals(RaidRoster.State.DEAD, roster.stateOf(BOB));
    }

    @Test
    void nobodyLeftOnlyWhenEveryParticipantIsOut() {
        RaidRoster.Roster roster = new RaidRoster.Roster();
        assertFalse(roster.nobodyLeftInRaid(), "空名单不算都出去了（刚开局没人时也不该结束）");

        roster.join(ALICE, "Alice");
        roster.join(BOB, "Bob");
        roster.set(ALICE, RaidRoster.State.EXTRACTED);
        assertFalse(roster.nobodyLeftInRaid(), "Bob 还在图里");

        roster.set(BOB, RaidRoster.State.ABANDONED);
        assertTrue(roster.nobodyLeftInRaid(), "一个撤了一个超时未撤离，本局已无人留在图里");
    }

    @Test
    void jsonRoundTripKeepsStatesNamesAndGraceTimer() {
        RaidRoster.Roster roster = new RaidRoster.Roster();
        long now = 1_700_000_000_000L;
        roster.join(ALICE, "Alice");
        roster.join(CAROL, "Carol");
        roster.set(ALICE, RaidRoster.State.EXTRACTED);
        roster.markLeft(CAROL, now);

        RaidRoster.Roster reparsed = RaidRoster.Roster.fromJson(roster.toJson());
        assertEquals(roster.size(), reparsed.size());
        assertEquals(RaidRoster.State.EXTRACTED, reparsed.stateOf(ALICE));
        assertEquals(RaidRoster.State.LEFT, reparsed.stateOf(CAROL));
        assertEquals("Carol", reparsed.nameOf(CAROL));

        // 宽限计时也要能读回，否则重启玩家会被当成刚掉线
        assertFalse(reparsed.handleLogin(CAROL, now + RaidRoster.GRACE_MILLIS + 1L),
                "读回后超时判定仍应生效");
        assertEquals(RaidRoster.State.ABANDONED, reparsed.stateOf(CAROL));
    }

    @Test
    void brokenEntriesAreSkipped() {
        JsonObject json = new JsonObject();
        JsonObject good = new JsonObject();
        good.addProperty("state", "IN_RAID");
        good.addProperty("name", "Alice");
        json.add(ALICE.toString(), good);
        json.addProperty("not-a-uuid", "garbage");
        JsonObject bad = new JsonObject();
        bad.addProperty("state", "NOT_A_STATE");
        json.add(BOB.toString(), bad);

        RaidRoster.Roster roster = RaidRoster.Roster.fromJson(json);
        assertEquals(1, roster.size());
        assertEquals("Alice", roster.nameOf(ALICE));
    }

    @Test
    void describeSummarizesEveryState() {
        RaidRoster.Roster roster = new RaidRoster.Roster();
        roster.join(ALICE, "Alice");
        roster.join(BOB, "Bob");
        roster.join(CAROL, "Carol");
        roster.set(BOB, RaidRoster.State.EXTRACTED);
        roster.markLeft(CAROL, 1L);

        String text = roster.describe();
        assertTrue(text.contains("在图中 1"), text);
        assertTrue(text.contains("已撤离 1"), text);
        assertTrue(text.contains("掉线中 1"), text);
    }
}
