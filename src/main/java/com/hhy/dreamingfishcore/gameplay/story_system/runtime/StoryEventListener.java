package com.hhy.dreamingfishcore.gameplay.story_system.runtime;

/** 供故事流程引擎及未来扩展模块订阅统一事实事件。 */
@FunctionalInterface
public interface StoryEventListener {
    void onStoryEvent(StoryEvent event);
}
