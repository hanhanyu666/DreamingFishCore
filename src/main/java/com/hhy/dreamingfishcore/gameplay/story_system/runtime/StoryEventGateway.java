package com.hhy.dreamingfishcore.gameplay.story_system.runtime;

import com.hhy.dreamingfishcore.DreamingFishCore;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 同步的内部故事事件总线。
 *
 * <p>NeoForge 的这些入口都运行在服务端线程；这里刻意不另起线程，避免故事状态、
 * NPC 私信和引导存档之间出现并发写入。监听器异常会被隔离，不能阻断其他监听器。</p>
 */
public final class StoryEventGateway {
    private static final List<StoryEventListener> LISTENERS = new CopyOnWriteArrayList<>();

    private StoryEventGateway() {
    }

    public static void register(StoryEventListener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void unregister(StoryEventListener listener) {
        if (listener != null) {
            LISTENERS.remove(listener);
        }
    }

    public static void emit(StoryEvent event) {
        if (event == null) {
            return;
        }
        for (StoryEventListener listener : LISTENERS) {
            try {
                listener.onStoryEvent(event);
            } catch (RuntimeException exception) {
                // 一个扩展流程失败不能让公告/NPC模块的服务端请求整体失败。
                DreamingFishCore.LOGGER.error("故事事件监听器执行失败", exception);
            }
        }
    }

    public static void clear() {
        LISTENERS.clear();
    }

    static int listenerCount() {
        return LISTENERS.size();
    }
}
