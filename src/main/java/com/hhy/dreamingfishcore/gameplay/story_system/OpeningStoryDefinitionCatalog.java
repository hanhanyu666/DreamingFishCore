package com.hhy.dreamingfishcore.gameplay.story_system;

import java.util.List;

/**
 * “梦的开始”阶段中随模组发布的四项开场任务。
 *
 * <p>稳定字符串 ID 供剧情执行器引用；数字编号只用于旧界面排序与兼容。</p>
 */
public final class OpeningStoryDefinitionCatalog {
    public static final String STAGE_ID = StoryWorldState.DEFAULT_STAGE_ID;

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
                    .anyMatch(task -> builtIn.getTaskKey().equals(task.getTaskKey()));
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
        // 正式服按任务地点的中文名称判定，地点稳定 ID 无需预先写进任务定义。
        task.setLocationId("");
        return task;
    }
}
