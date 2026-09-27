package com.hhy.dreamingfishcore.gameplay.npc_system;

import java.util.Set;

/**
 * 当前上线批次的 NPC 白名单。
 *
 * <p>这是当前发布批次的内容收口策略，不是永久的 NPC 类型限制。下一轮增加角色时
 * 需要同步更新内容包和这份显式策略，避免旧配置/默认资源把已下线角色重新带回来。</p>
 */
public final class StoryNpcContentPolicy {
    public static final int BAIZHI_ID = 101;
    public static final int JIANGWAN_ID = 102;
    /** 梁朔的外缘基础设施身份壳（本轮不恢复旧剧情文案）。 */
    public static final int LIANGSHUO_ID = 103;
    /** 尉迟南的通兰天文台身份壳（本轮不恢复旧剧情文案）。 */
    public static final int WEICHINAN_ID = 104;
    public static final int ZHOUCEN_ID = 105;
    public static final int MEDICAL_STAFF_ID = 106;
    private static final Set<Integer> RETAINED_IDS = Set.of(
            BAIZHI_ID, JIANGWAN_ID, LIANGSHUO_ID, WEICHINAN_ID, ZHOUCEN_ID, MEDICAL_STAFF_ID);

    /**
     * 当前开服切片真正使用的主线私信。主线私信的投递顺序由对应 Java 阶段文件
     * 决定；配置文件只提供这些 ID 的可编辑文案和回复。把 ID 集中列在这里，
     * 可以让旧的 opening/afterdream 消息即使还留在运营配置里，也不会重新加入
     * 运行时链路。
     */
    private static final Set<String> ACTIVE_STORY_MESSAGE_IDS = Set.of(
            "dreamingfishcore:opening/baizhi/abydos_arrival",
            "dreamingfishcore:opening/zhoucen/contact_channel",
            "dreamingfishcore:opening/zhoucen/introduction",
            "dreamingfishcore:opening/zhoucen/member_welcome",
            "dreamingfishcore:opening/zhoucen/independent_ack",
            "dreamingfishcore:afterdream/baizhi/public_treatment");

    /** 明确退役的旧主线 ID；它不属于新流程，也不能被普通内容入口投递。 */
    private static final Set<String> RETIRED_STORY_MESSAGE_IDS = Set.of(
            "dreamingfishcore:baizhi/first_stage_protocol");

    private StoryNpcContentPolicy() {
    }

    public static boolean isRetained(int npcId) {
        return RETAINED_IDS.contains(npcId);
    }

    public static Set<Integer> retainedIds() {
        return RETAINED_IDS;
    }

    /** NPCs that are present only as an identity/skin shell in this launch. */
    public static boolean isIdentityShell(int npcId) {
        return npcId == JIANGWAN_ID
                || npcId == LIANGSHUO_ID
                || npcId == WEICHINAN_ID;
    }

    /** 判断某个 NPC 私信定义/快照是否属于本轮明确保留的内容。 */
    public static boolean isRetainedMessage(int npcId, String definitionId) {
        if (!isRetained(npcId) || definitionId == null || definitionId.isBlank()) {
            return false;
        }
        if (RETIRED_STORY_MESSAGE_IDS.contains(definitionId)) {
            return false;
        }
        // 主线命名空间采用显式白名单；这样配置文件中遗留的旧节点不会被
        // deliverInteractionMessage、follow-up 或管理员命令意外重新激活。
        if (definitionId.startsWith("dreamingfishcore:opening/")
                || definitionId.startsWith("dreamingfishcore:afterdream/")) {
            return ACTIVE_STORY_MESSAGE_IDS.contains(definitionId);
        }
        // 江晚、梁朔和尉迟南目前没有可投递的主线私信；其他命名空间仍允许
        // 服主为白芷/周岑配置普通通信，不会改变 Java 主线状态。
        return npcId != JIANGWAN_ID && npcId != LIANGSHUO_ID && npcId != WEICHINAN_ID;
    }

    /**
     * 本轮三个身份壳 NPC 不创建新的私信派生引导。
     * 这个判断只能用于新内容投递，绝不能用于删除已持久化的历史引导。
     */
    public static boolean isRetainedGuidanceSource(int npcId) {
        // 江晚、梁朔、尉迟南本轮都没有可从 NPC 私信生成的个人引导。
        return npcId != JIANGWAN_ID
                && npcId != LIANGSHUO_ID
                && npcId != WEICHINAN_ID
                && isRetained(npcId);
    }

    /** 主线私信的后续和引导由 Java 阶段脚本处理，消息系统不自动串联它们。 */
    public static boolean isStoryControlledMessage(String definitionId) {
        return definitionId != null && ACTIVE_STORY_MESSAGE_IDS.contains(definitionId);
    }
}
