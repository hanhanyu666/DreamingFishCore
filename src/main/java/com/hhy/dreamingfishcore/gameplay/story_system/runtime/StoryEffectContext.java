package com.hhy.dreamingfishcore.gameplay.story_system.runtime;

/** 一个流程效果执行时可读取的上下文。 */
public record StoryEffectContext(
        StoryEvent event,
        StoryFlowDefinition flow,
        StoryFlowNode node,
        StoryFlowEffect effect,
        int effectIndex,
        String effectId) {
}
