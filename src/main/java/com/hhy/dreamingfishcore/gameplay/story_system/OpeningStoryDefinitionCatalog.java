package com.hhy.dreamingfishcore.gameplay.story_system;

import com.hhy.dreamingfishcore.gameplay.npc_system.StoryNpcContentPolicy;

import java.util.List;

/**
 * “梦的开始”阶段中随模组发布的四项开场任务。
 *
 * <p>稳定字符串 ID 供故事流程和任务视图共同引用；数字编号只用于任务列表排序。</p>
 */
public final class OpeningStoryDefinitionCatalog {
    public static final String STAGE_ID = StoryWorldState.DEFAULT_STAGE_ID;

    /** 阿拜多斯任务地点的稳定 ID；剧情逻辑不再依赖可改的中文显示名。 */
    public static final String ABYDOS_LOCATION_ID =
            "dreamingfishcore:location_d105866ccdc84c4da7b017a7f13ec7d3";
    /** 逐光会基地预留的稳定地点 ID；地点尚未登记时引导仍可创建。 */
    public static final String ZHUIGUANG_LOCATION_ID =
            "dreamingfishcore:location_zhuiguang_base";
    public static final int BAIZHI_NPC_ID = StoryNpcContentPolicy.BAIZHI_ID;
    public static final int ZHOUCEN_NPC_ID = StoryNpcContentPolicy.ZHOUCEN_ID;

    public static final String BAIZHI_ARRIVAL_MESSAGE_ID =
            "dreamingfishcore:opening/baizhi/abydos_arrival";
    public static final String ZHOUCEN_CONTACT_MESSAGE_ID =
            "dreamingfishcore:opening/zhoucen/contact_channel";
    public static final String ZHOUCEN_INTRODUCTION_MESSAGE_ID =
            "dreamingfishcore:opening/zhoucen/introduction";
    public static final String ASK_ABOUT_ZHUIGUANG_REPLY_ID = "ask_about_zhuiguang";
    public static final String JOIN_ZHUIGUANG_REPLY_ID = "join_zhuiguang";
    public static final String REMAIN_INDEPENDENT_REPLY_ID = "remain_independent";

    public static final String SETTLE_IN_ABYDOS_TASK_ID =
            "dreamingfishcore:opening/settle_in_abydos";
    public static final String MEET_BAIZHI_TASK_ID =
            "dreamingfishcore:opening/meet_baizhi";
    public static final String CHOOSE_ZHUIGUANG_PATH_TASK_ID =
            "dreamingfishcore:opening/choose_zhuiguang_path";
    public static final String BUILD_ZHUIGUANG_BASE_TASK_ID =
            "dreamingfishcore:opening/build_zhuiguang_base";

    public static final int SETTLE_IN_ABYDOS_TASK_NUMBER = 1101;
    public static final int MEET_BAIZHI_TASK_NUMBER = 1102;
    public static final int CHOOSE_ZHUIGUANG_PATH_TASK_NUMBER = 1103;
    public static final int BUILD_ZHUIGUANG_BASE_TASK_NUMBER = 1104;

    public static final String STAGE_DESCRIPTION =
            "危机爆发后，幸存者在阿拜多斯安顿下来，并决定是否参与人类逐光联合会的筹建。";

    private OpeningStoryDefinitionCatalog() {
    }

    /** 返回新的定义对象，调用方可以安全地加入或写入配置。 */
    public static List<StoryTaskData> createTasks() {
        return List.of(
                task(
                        SETTLE_IN_ABYDOS_TASK_ID,
                        SETTLE_IN_ABYDOS_TASK_NUMBER,
                        "抵达阿拜多斯",
                        "阅读临时安置通知，前往任务地点“阿拜多斯”完成安置。"),
                task(
                        MEET_BAIZHI_TASK_ID,
                        MEET_BAIZHI_TASK_NUMBER,
                        "去学校见白芷",
                        "到达阿拜多斯后，根据白芷的消息前往学校与她当面交谈。"),
                task(
                        CHOOSE_ZHUIGUANG_PATH_TASK_ID,
                        CHOOSE_ZHUIGUANG_PATH_TASK_NUMBER,
                        "了解逐光会",
                        "白芷已经当面介绍了逐光会；联系筹备负责人听完具体安排，再决定是否加入。"),
                task(
                        BUILD_ZHUIGUANG_BASE_TASK_ID,
                        BUILD_ZHUIGUANG_BASE_TASK_NUMBER,
                        "建设逐光会基地",
                        "选择加入的成员前往“人类逐光联合会”任务地点，参与大型基地建设；独立协作者无需承担这项任务。"));
    }

    /** 建设任务只分配给已加入逐光会的玩家；全服定义本身仍然保留。 */
    public static boolean isMemberOnlyTask(String taskKey) {
        return BUILD_ZHUIGUANG_BASE_TASK_ID.equals(taskKey);
    }

    /**
     * 只补齐缺失的内置任务，不覆盖服主已经改写过的同 ID 任务。
     *
     * @return 是否修改了阶段定义
     */
    static boolean ensureTasks(StoryStageData stage) {
        if (stage == null || !STAGE_ID.equals(stage.getStageId())) {
            return false;
        }
        boolean changed = false;
        for (StoryTaskData builtIn : createTasks()) {
            boolean exists = stage.getTasks().stream()
                    .filter(task -> builtIn.getTaskKey().equals(task.getTaskKey()))
                    .findAny()
                    .isPresent();
            if (!exists) {
                stage.addTask(builtIn);
                changed = true;
            }
        }
        return changed;
    }

    private static StoryTaskData task(String id, int number, String name, String content) {
        StoryTaskData task = new StoryTaskData(id, number, name, content, 0L, 0L);
        task.setPublishedByDefault(true);
        // 任务地点使用稳定 ID；显示名称只属于 UI 文案，允许服主日后改名。
        task.setLocationId(id.equals(SETTLE_IN_ABYDOS_TASK_ID) ? ABYDOS_LOCATION_ID : "");
        return task;
    }

}
