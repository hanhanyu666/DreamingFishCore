package com.hhy.dreamingfishcore.gameplay.opening_story_system;

/**
 * 第一阶段“梦的开始”的单人状态。
 *
 * <p>这是业务状态而不是可自由编辑的游标。新增分支必须显式加入枚举和状态转换，
 * 编译器会帮助发现遗漏的处理。</p>
 */
public enum OpeningStoryStep {
    NOT_STARTED,
    TRAVEL_TO_ABYDOS,
    TALK_TO_BAIZHI,
    CONTACT_ZHOUCEN,
    CHOOSE_MEMBERSHIP,
    BUILD_ZHUIGUANG_BASE,
    DECLINED_ZHUIGUANG
}
