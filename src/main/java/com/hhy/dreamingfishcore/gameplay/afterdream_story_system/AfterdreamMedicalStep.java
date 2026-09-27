package com.hhy.dreamingfishcore.gameplay.afterdream_story_system;

/**
 * “余梦期”个人医疗接待的业务状态。
 *
 * <p>每个值都对应玩家已经发生的事实；它不是可以在 JSON 中任意拼接的游标。</p>
 */
public enum AfterdreamMedicalStep {
    NOT_STARTED,
    MESSAGE_RECEIVED,
    MESSAGE_READ,
    RECEPTION_READY,
    INTRODUCTION,
    RESULT_LEVEL_ONE,
    RESULT_NONINFECTED,
    RESULT_LEVEL_TWO,
    AWAITING_TREATMENT,
    /** 面具已经实际进入物品栏，下一次交互再回到治疗/完成视图。 */
    MASK_RECEIVED,
    COMPLETED
}
