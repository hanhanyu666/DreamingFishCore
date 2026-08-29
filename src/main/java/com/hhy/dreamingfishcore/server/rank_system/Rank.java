package com.hhy.dreamingfishcore.server.rank_system;

public class Rank {
    private String rankName;
    private int rankLevel;
    private int rankColor; // RGB 颜色（如 0x00FF00 表示绿色）

    // 无参构造函数（Gson反序列化需要）
    public Rank() {
        this.rankColor = 0xAAAAAA; // 默认灰色
    }

    public Rank(String rankName, int rankLevel) {
        this.rankName = rankName;
        this.rankLevel = rankLevel;
        this.rankColor = 0xAAAAAA; // 默认灰色
    }

    public Rank(String rankName, int rankLevel, int rankColor) {
        this.rankName = rankName;
        this.rankLevel = rankLevel;
        this.rankColor = rankColor;
    }

    public Rank(String rankName, RankTier tier) {
        this(rankName, tier.level());
    }

    public Rank(String rankName, RankTier tier, int rankColor) {
        this(rankName, tier.level(), rankColor);
    }

    public int getRankLevel() {
        return rankLevel;
    }

    public RankTier getTier() {
        return RankTier.fromLevel(rankLevel);
    }

    public boolean isAtLeast(RankTier minimumTier) {
        return getTier().isAtLeast(minimumTier);
    }

    public String getRankName() {
        return rankName;
    }

    public int getRankColor() {
        return rankColor;
    }

    public void setRankColor(int color) {
        this.rankColor = color;
    }
}
