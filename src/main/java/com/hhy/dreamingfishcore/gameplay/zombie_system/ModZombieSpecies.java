package com.hhy.dreamingfishcore.gameplay.zombie_system;

/**
 * 标记接口：本模组自定义的丧尸变体（围攻僵尸 {@link SiegeZombieEntity}、射手僵尸等）。
 *
 * <p>存在的意义是让「模组丧尸家族」成为一个可判定的集合，而不是在各处逐个 {@code instanceof}
 * 具体类。例如蓝图掉落、后续的击杀统计或剧情判定，只需要检查这个接口；将来再新增第三种变体
 * 时不必回头改这些调用点。</p>
 */
public interface ModZombieSpecies {
}
