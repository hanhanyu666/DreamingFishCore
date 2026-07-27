package com.hhy.dreamingfishcore.server.rank_system;

import com.google.gson.Gson;
import com.hhy.dreamingfishcore.server.playerdata_system.PlayerData;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDataRankTest {
    private static final Gson GSON = new Gson();

    @Test
    void newPlayerStartsWithGoldBuilderFishEquipped() {
        PlayerData newPlayer = new PlayerData(UUID.randomUUID(), "NewBuilder", null);

        assertEquals(RankRegistry.BUILDER_FISH.getRankName(), newPlayer.getRank().getRankName());
        assertEquals(0xFFAA00, newPlayer.getRank().getRankColor());
        assertEquals(Set.of(RankRegistry.BUILDER_FISH.getRankName()), newPlayer.getOwnedRankNames());
    }

    @Test
    void legacyPlayerOnlyKeepsPreviouslyEquippedRank() {
        String legacyJson = """
                {
                  "uuid": "%s",
                  "playerName": "ExistingFish",
                  "rank": {
                    "rankName": "FISH++",
                    "rankLevel": 3,
                    "rankColor": 16755200
                  }
                }
                """.formatted(UUID.randomUUID());
        PlayerData existingPlayer = GSON.fromJson(legacyJson, PlayerData.class);

        assertTrue(existingPlayer.repairRankData());
        assertEquals(RankRegistry.FISH_PLUS_PLUS.getRankName(), existingPlayer.getRank().getRankName());
        assertEquals(Set.of(RankRegistry.FISH_PLUS_PLUS.getRankName()), existingPlayer.getOwnedRankNames());
        assertFalse(existingPlayer.ownsRank(RankRegistry.BUILDER_FISH));
    }

    @Test
    void unequippingDoesNotRemoveOwnedRank() {
        PlayerData player = new PlayerData(UUID.randomUUID(), "NewBuilder", null);

        player.setRank(RankRegistry.NO_RANK);

        assertEquals(RankRegistry.NO_RANK.getRankName(), player.getRank().getRankName());
        assertTrue(player.ownsRank(RankRegistry.BUILDER_FISH));
    }
}
