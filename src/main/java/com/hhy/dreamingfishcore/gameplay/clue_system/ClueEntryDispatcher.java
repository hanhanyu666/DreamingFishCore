package com.hhy.dreamingfishcore.gameplay.clue_system;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 发放入口的统一分发：把"刚刚发生了什么"翻译成"该发哪几条线索"。
 *
 * <p>六种入口（方块、容器、NPC、区域、广播、事件）都只做一件事——报出自己是哪种入口、
 * 键是什么，然后交给这里。这里向 {@link ClueCatalog} 查声明索引，再逐条走
 * {@link ClueGrantService}。**触发点不认识任何一条线索的名字**，所以服主改
 * {@code clue_secrets.json} 里的 {@code grantSources} 就能换入口，不用改代码。</p>
 *
 * <p>幂等由 {@link ClueGrantService} 保证：已经发现过的线索不会被重复发放，
 * 入口被反复触发也不会刷出多张残页。</p>
 */
public final class ClueEntryDispatcher {

    /**
     * 剧情事件键：玩家完成医院第一次正式复查。
     *
     * <p>事件键是"这里发生了一件有名字的事"，由剧情代码在对应位置触发；
     * 服主在 {@code clue_secrets.json} 里写 {@code event=<键>} 决定哪些线索挂在这个事件上。</p>
     */
    public static final String EVENT_HOSPITAL_REVIEW = "hospital_review";

    private ClueEntryDispatcher() {
    }

    /**
     * 触发一次入口。
     *
     * @return 本次真正新发放的线索条数（用于调用方判断要不要做后续动作，不需要就忽略）
     */
    public static int fire(ServerPlayer player, ClueSourceType type, String key) {
        if (player == null || type == null || key == null || key.isBlank()) {
            return 0;
        }
        List<String> clueIds = ClueCatalog.cluesForGrantSource(type, key);
        if (clueIds.isEmpty()) {
            return 0;
        }
        int granted = 0;
        for (String clueId : clueIds) {
            if (ClueGrantService.grant(player, clueId) == ClueGrantService.Outcome.GRANTED) {
                granted++;
            }
        }
        return granted;
    }
}
