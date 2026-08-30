package com.hhy.dreamingfishcore.gameplay.story_system.runtime;

/** 故事流程节点影响的状态范围。阶段切换仍然只能由服主手动完成。 */
public enum StoryFlowScope {
    PLAYER,
    WORLD,
    COOP
}
