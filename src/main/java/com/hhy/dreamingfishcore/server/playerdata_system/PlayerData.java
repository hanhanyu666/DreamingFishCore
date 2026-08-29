package com.hhy.dreamingfishcore.server.playerdata_system;

import com.hhy.dreamingfishcore.server.title_system.Title;
import com.hhy.dreamingfishcore.server.title_system.TitleRegistry;
import com.hhy.dreamingfishcore.server.rank_system.Rank;
import com.hhy.dreamingfishcore.server.rank_system.RankRegistry;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerData {
    private UUID uuid;
    private String playerName;
    private Rank rank;
    private Set<String> ownedRanks;
    private Title title;
    private int level;
    private long currentExperience;
    private long registrationTime;
    private long lastLoginTime;
    private long totalPlayTime;
    private boolean zhuiguangMember;

    public PlayerData() {
        long now = System.currentTimeMillis();
        this.rank = RankRegistry.NO_RANK;
        this.ownedRanks = new LinkedHashSet<>();
        // Gson uses this constructor before hydrating persisted fields.
        this.title = null;
        this.level = 1;
        this.currentExperience = 0;
        this.registrationTime = now;
        this.lastLoginTime = now;
        this.totalPlayTime = 0;
        this.zhuiguangMember = false;
    }

    public PlayerData(ServerPlayer player) {
        this(player.getUUID(), player.getScoreboardName(), TitleRegistry.getDefaultTitle());
    }

    public PlayerData(UUID uuid, String playerName) {
        this(uuid, playerName, TitleRegistry.getDefaultTitle());
    }

    public PlayerData(UUID uuid, String playerName, Title defaultTitle) {
        long now = System.currentTimeMillis();
        this.uuid = uuid;
        this.playerName = playerName;
        this.rank = RankRegistry.NO_RANK;
        this.ownedRanks = new LinkedHashSet<>();
        this.title = defaultTitle;
        this.level = 1;
        this.registrationTime = now;
        this.lastLoginTime = now;  //登录时记录当前时间
        this.totalPlayTime = 0;
        this.zhuiguangMember = false;
    }

    public PlayerData(UUID uuid, String playerName, Rank rank, Title title, int level) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.rank = rank;
        this.ownedRanks = new LinkedHashSet<>();
        grantRank(rank);
        this.title = title;
        this.level = level;
    }

    public void setRank(Rank rank) {
        this.rank = rank == null ? RankRegistry.NO_RANK : RankRegistry.getRankByName(rank.getRankName());
        grantRank(this.rank);
    }

    public boolean grantRank(Rank rank) {
        if (rank == null || !RankRegistry.isRegistered(rank.getRankName())) {
            return false;
        }
        Rank registeredRank = RankRegistry.getRankByName(rank.getRankName());
        if (registeredRank == RankRegistry.NO_RANK) return false;
        ensureOwnedRanks();
        return ownedRanks.add(registeredRank.getRankName());
    }

    public boolean ownsRank(Rank rank) {
        if (rank == null || !RankRegistry.isRegistered(rank.getRankName())) return false;
        Rank registeredRank = RankRegistry.getRankByName(rank.getRankName());
        if (registeredRank == RankRegistry.NO_RANK) return true;
        ensureOwnedRanks();
        return ownedRanks.contains(registeredRank.getRankName());
    }

    public Set<String> getOwnedRankNames() {
        ensureOwnedRanks();
        return Collections.unmodifiableSet(ownedRanks);
    }

    public void setOwnedRankNames(Collection<String> rankNames) {
        this.ownedRanks = new LinkedHashSet<>();
        if (rankNames == null) {
            return;
        }
        for (String rankName : rankNames) {
            if (RankRegistry.isRegistered(rankName)) {
                Rank registeredRank = RankRegistry.getRankByName(rankName);
                if (registeredRank != RankRegistry.NO_RANK) {
                    this.ownedRanks.add(registeredRank.getRankName());
                }
            }
        }
    }

    /**
     * Migrates legacy saves that only stored the equipped Rank.
     */
    public boolean repairRankData() {
        boolean repaired = ownedRanks == null;
        ensureOwnedRanks();

        Rank canonicalRank = rank == null
                ? RankRegistry.NO_RANK
                : RankRegistry.getRankByName(rank.getRankName());
        if (rank == null || !canonicalRank.getRankName().equals(rank.getRankName())
                || canonicalRank.getRankLevel() != rank.getRankLevel()
                || canonicalRank.getRankColor() != rank.getRankColor()) {
            rank = canonicalRank;
            repaired = true;
        }
        if (canonicalRank != RankRegistry.NO_RANK && ownedRanks.add(canonicalRank.getRankName())) {
            repaired = true;
        }

        Set<String> normalizedRanks = new LinkedHashSet<>();
        for (String rankName : ownedRanks) {
            if (RankRegistry.isRegistered(rankName)) {
                Rank registeredRank = RankRegistry.getRankByName(rankName);
                if (registeredRank != RankRegistry.NO_RANK) {
                    normalizedRanks.add(registeredRank.getRankName());
                }
            }
        }
        if (!normalizedRanks.equals(ownedRanks)) {
            ownedRanks = normalizedRanks;
            repaired = true;
        }
        return repaired;
    }

    private void ensureOwnedRanks() {
        if (ownedRanks == null) {
            ownedRanks = new LinkedHashSet<>();
        }
    }
    public void setTitle(Title title) {
        this.title = title;
    }
    public void setLevel(int level) {
        this.level = level;
    }
    public void setCurrentExperience(long currentExperience) {
        this.currentExperience = currentExperience;
    }

    public String getPlayerName() {
        return this.playerName;
    }
    public UUID getUUID() {
        return uuid;
    }
    public Rank getRank() {
        return this.rank;
    }
    public Title getTitle() {
        return this.title;
    }
    public int getLevel() {
        return this.level;
    }
    public long getCurrentExperience() {
        return currentExperience;
    }



    public long getRegistrationTime() { return registrationTime; }
    public void setRegistrationTime(long registrationTime) { this.registrationTime = registrationTime; }
    public long getLastLoginTime() { return lastLoginTime; }
    public void setLastLoginTime(long time) { this.lastLoginTime = time; }
    public long getTotalPlayTime() { return totalPlayTime; }
    public void setTotalPlayTime(long totalPlayTime) { this.totalPlayTime = totalPlayTime; }
    public void addPlayTime(long time) { this.totalPlayTime += time; }
    public boolean isZhuiguangMember() { return zhuiguangMember; }
    public void setZhuiguangMember(boolean zhuiguangMember) { this.zhuiguangMember = zhuiguangMember; }
}
