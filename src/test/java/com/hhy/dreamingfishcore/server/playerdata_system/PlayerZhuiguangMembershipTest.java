package com.hhy.dreamingfishcore.server.playerdata_system;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerZhuiguangMembershipTest {
    @Test
    void newPlayersStartAsIndependentCollaborators() {
        PlayerData player = new PlayerData(UUID.randomUUID(), "Builder", null);

        assertFalse(player.isZhuiguangMember());
        player.setZhuiguangMember(true);
        assertTrue(player.isZhuiguangMember());
        player.setZhuiguangMember(false);
        assertFalse(player.isZhuiguangMember());
    }

    @Test
    void legacySavesWithoutMembershipRemainNonMembers() {
        PlayerData legacy = new Gson().fromJson(
                "{\"playerName\":\"Legacy\",\"level\":3}",
                PlayerData.class);

        assertFalse(legacy.isZhuiguangMember());
    }
}
