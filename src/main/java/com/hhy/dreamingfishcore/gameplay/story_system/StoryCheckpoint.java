package com.hhy.dreamingfishcore.gameplay.story_system;

import com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamStory;
import com.hhy.dreamingfishcore.gameplay.opening_story_system.OpeningStory;

import java.util.List;

/** 作者明确注册的安全接入点。正文可编辑，跳转范围由 Java 限定。 */
public record StoryCheckpoint(String id, String stageId, int order, List<String> skippedTasks) {
    public static final String OPENING_START = "dreamingfishcore:opening/start";
    public static final String OPENING_MEMBERSHIP = "dreamingfishcore:opening/membership";
    public static final String AFTERDREAM_START = "dreamingfishcore:afterdream/start";
    public static final String AFTERDREAM_RECEPTION = "dreamingfishcore:afterdream/reception";

    public static List<StoryCheckpoint> all() {
        return List.of(
                new StoryCheckpoint(OPENING_START, OpeningStory.STAGE_ID, 1000, List.of()),
                new StoryCheckpoint(OPENING_MEMBERSHIP, OpeningStory.STAGE_ID, 1040,
                        List.of(OpeningStory.SETTLE_IN_ABYDOS_TASK_ID, OpeningStory.MEET_BAIZHI_TASK_ID)),
                new StoryCheckpoint(AFTERDREAM_START, AfterdreamStory.STAGE_ID, 2000, List.of()),
                new StoryCheckpoint(AFTERDREAM_RECEPTION, AfterdreamStory.STAGE_ID, 2020,
                        List.of(AfterdreamStory.BAIZHI_MESSAGE_TASK_ID, AfterdreamStory.ENTER_RECEPTION_TASK_ID)));
    }

    public static StoryCheckpoint require(String id) {
        return all().stream().filter(value -> value.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未注册的剧情接入点：" + id));
    }
}
