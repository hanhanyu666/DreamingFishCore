package com.hhy.dreamingfishcore.gameplay.story_system.runtime;

/**
 * 故事运行时只接收已经在服务端校验过的事实事件。
 *
 * <p>模块（公告、地点、NPC、登录）只负责发出事实，不直接推进某一条剧情。</p>
 */
public enum StoryEventType {
    PLAYER_AUTHENTICATED,
    NOTICE_READ,
    LOCATION_ENTERED,
    NPC_INTERACTION,
    NPC_REPLY
}
