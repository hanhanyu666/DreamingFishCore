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
    void newPlayerStartsWithoutRank() {
        PlayerData newPlayer = new PlayerData(UUID.randomUUID(), "NewPlayer", null);

        assertEquals(RankRegistry.NO_RANK.getRankName(), newPlayer.getRank().getRankName());
        assertEquals(Set.of(), newPlayer.getOwnedRankNames());
    }

    @Test
    void existingPlayerCanReceiveBuilderWithoutChangingEquippedRank() {
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

        assertTrue(existingPlayer.grantRank(RankRegistry.BUILDER_FISH));
        assertEquals(RankRegistry.FISH_PLUS_PLUS.getRankName(), existingPlayer.getRank().getRankName());
        assertEquals(Set.of(RankRegistry.FISH_PLUS_PLUS.getRankName(), RankRegistry.BUILDER_FISH.getRankName()),
                existingPlayer.getOwnedRankNames());
    }

    @Test
    void unequippingDoesNotRemoveOwnedRank() {
        PlayerData player = new PlayerData(UUID.randomUUID(), "NewBuilder", null);

        player.setRank(RankRegistry.BUILDER_FISH);
        player.setRank(RankRegistry.NO_RANK);

        assertEquals(RankRegistry.NO_RANK.getRankName(), player.getRank().getRankName());
        assertTrue(player.ownsRank(RankRegistry.BUILDER_FISH));
    }

    @Test
    void grantingBuilderDoesNotEquipItForPlayerWhoChoseNoRank() {
        PlayerData player = GSON.fromJson("{\"rank\":{\"rankName\":\"NO_RANK\",\"rankLevel\":0,\"rankColor\":11184810}}",
                PlayerData.class);
        player.repairRankData();

        assertTrue(player.grantRank(RankRegistry.BUILDER_FISH));

        assertEquals(RankRegistry.NO_RANK.getRankName(), player.getRank().getRankName());
        assertTrue(player.ownsRank(RankRegistry.BUILDER_FISH));
    }

    @Test
    void originalFishRanksKeepLevelsOneTwoAndThree() {
        assertEquals(1, RankRegistry.FISH.getRankLevel());
        assertEquals(2, RankRegistry.FISH_PLUS.getRankLevel());
        assertEquals(3, RankRegistry.FISH_PLUS_PLUS.getRankLevel());
    }

    @Test
    void builderRanksShareTheFishAndFishPlusTiers() {
        assertEquals(1, RankRegistry.BUILDER_FISH.getRankLevel());
        assertEquals(RankRegistry.SUPER_BUILDER_FISH,
                RankRegistry.getRankByName("SUPER_BUILDER_FISH"));
        assertEquals(RankRegistry.SUPER_BUILDER_FISH, RankRegistry.getRankByName("super"));
        assertEquals(2, RankRegistry.SUPER_BUILDER_FISH.getRankLevel());
        assertEquals(RankRegistry.FISH_PLUS, RankRegistry.getRankByLevel(2));
        assertEquals(Set.of(RankRegistry.FISH_PLUS, RankRegistry.SUPER_BUILDER_FISH),
                Set.copyOf(RankRegistry.getRanksByLevel(2)));
        assertEquals(0x55FFFF, RankRegistry.SUPER_BUILDER_FISH.getRankColor());
        assertTrue(RankRegistry.isRegistered("SUPER BUILDER FISH"));
    }

    @Test
    void worldShaperFishSharesTheFishPlusPlusTier() {
        assertEquals(RankRegistry.WORLD_SHAPER_FISH,
                RankRegistry.getRankByName("WORLD_SHAPER_FISH"));
        assertEquals(3, RankRegistry.WORLD_SHAPER_FISH.getRankLevel());
        assertEquals(RankRegistry.FISH_PLUS_PLUS, RankRegistry.getRankByLevel(3));
        assertEquals(0xFFAA00, RankRegistry.WORLD_SHAPER_FISH.getRankColor());
        assertTrue(RankRegistry.isRegistered("WORLD SHAPER FISH"));
    }

    @Test
    void mythAndOperatorShareTheHighestTier() {
        assertEquals(RankRegistry.MYTH_SHAPER_FISH,
                RankRegistry.getRankByName("MYTH_SHAPER_FISH"));
        assertEquals(RankRegistry.MYTH_SHAPER_FISH, RankRegistry.getRankByName("myth"));
        assertEquals(4, RankRegistry.MYTH_SHAPER_FISH.getRankLevel());
        assertEquals(4, RankRegistry.OPERATOR.getRankLevel());
        assertEquals(RankRegistry.OPERATOR, RankRegistry.getRankByLevel(4));
        assertEquals(0xFF69B4, RankRegistry.MYTH_SHAPER_FISH.getRankColor());
        assertTrue(RankRegistry.isRegistered("MYTH SHAPER FISH"));
    }

    @Test
    void legacyStoredAliasLevelIsRepairedFromItsRankName() {
        PlayerData player = GSON.fromJson("""
                {
                  "rank": {
                    "rankName": "BUILDER FISH",
                    "rankLevel": 5,
                    "rankColor": 5635925
                  }
                }
                """, PlayerData.class);

        assertTrue(player.repairRankData());
        assertEquals(RankRegistry.BUILDER_FISH.getRankName(), player.getRank().getRankName());
        assertEquals(1, player.getRank().getRankLevel());
    }
}
