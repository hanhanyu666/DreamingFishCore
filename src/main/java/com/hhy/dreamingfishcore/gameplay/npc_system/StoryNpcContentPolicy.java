package com.hhy.dreamingfishcore.gameplay.npc_system;

import java.util.Set;

/**
 * 当前上线批次的 NPC 白名单。
 *
 * <p>这是内容收口策略，不是永久的 NPC 类型限制。下一轮增加角色时只需更新内容包和
 * 这份白名单，并通过迁移明确放行，避免旧配置/默认资源把已删除角色重新带回来。</p>
 */
public final class StoryNpcContentPolicy {
    public static final int BAIZHI_ID = 101;
    public static final int ZHOUCEN_ID = 105;
    private static final Set<Integer> RETAINED_IDS = Set.of(BAIZHI_ID, ZHOUCEN_ID);

    private StoryNpcContentPolicy() {
    }

    public static boolean isRetained(int npcId) {
        return RETAINED_IDS.contains(npcId);
    }

    public static Set<Integer> retainedIds() {
        return RETAINED_IDS;
    }
}
