package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;

/**
 * 感染身份轴（见 CONTEXT.md「感染身份」）。
 *
 * <p>四种身份是：幸存者、不稳定感染者、稳定感染者、传播复发。它只表示玩家当前身体与感染状态，
 * 不表示玩家所属组织、NPC 关系或服务器权限，也不是阵营或职业。</p>
 *
 * <p>存档层仍然使用 {@link PlayerAttributesData} 的 0/1/2 感染等级：0=幸存者、1=不稳定感染者、
 * 2=稳定感染者。传播复发不是"更严重的等级"，而是稳定感染者的<b>临时</b>状态，
 * 因此它由独立的时间窗字段表达，不能编码成等级 3 —— 否则所有 {@code >= 2} 判定
 * （复核话术、重构疗程准入等）都会把复发误判成另一种身份。</p>
 */
public enum InfectionIdentity {
    /** 尚未突变的幸存者。 */
    SURVIVOR("幸存者"),
    /** 正在经历剧烈修复与突变、会被动影响附近幸存者的感染者。 */
    UNSTABLE("不稳定感染者"),
    /** 突变已经稳定、正常状态下能与幸存者安全共同生活的感染者。 */
    STABLE("稳定感染者"),
    /** 稳定感染者因重伤或高污染刺激暂时重新释放异常因子的状态。 */
    RELAPSE("传播复发");

    private final String displayName;

    InfectionIdentity(String displayName) {
        this.displayName = displayName;
    }

    /** 面向玩家的身份名称；文案必须与 CONTEXT.md 的术语一致。 */
    public String displayName() {
        return displayName;
    }

    /** 是否为感染者身份（幸存者以外都算）。 */
    public boolean isInfected() {
        return this != SURVIVOR;
    }

    /**
     * 是否会被动影响附近幸存者（ADR 0003）。
     *
     * <p>只有不稳定感染者和传播复发者产生接触暴露；稳定感染者在正常状态下不会持续感染队友。</p>
     */
    public boolean canSpread() {
        return this == UNSTABLE || this == RELAPSE;
    }

    /**
     * 是否已经跨过突变、进入可被"稳定"或"重构"处理的阶段。
     * 用于替换散落在剧情里的 {@code getInfectionLevel() >= 2} 一类比较。
     */
    public boolean isStabilized() {
        return this == STABLE || this == RELAPSE;
    }

    /** 从玩家存档数据解析身份；数据缺失时按幸存者处理。 */
    public static InfectionIdentity of(PlayerAttributesData data) {
        if (data == null || !data.isInfected()) {
            return SURVIVOR;
        }
        if (data.getInfectionLevel() == PlayerAttributesData.INFECTION_LEVEL_ONE) {
            return UNSTABLE;
        }
        return data.hasActiveRelapseWindow() ? RELAPSE : STABLE;
    }
}
