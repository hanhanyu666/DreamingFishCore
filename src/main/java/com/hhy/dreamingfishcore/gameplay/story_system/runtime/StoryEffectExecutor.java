package com.hhy.dreamingfishcore.gameplay.story_system.runtime;

/** 未来内容包效果的服务端实现；实现必须自行保证幂等。 */
@FunctionalInterface
public interface StoryEffectExecutor {
    void execute(StoryEffectContext context);
}
