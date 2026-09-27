package com.hhy.dreamingfishcore.gameplay.story_system;

import java.util.List;

/** 当前开服版本两阶段的稳定身份与显示顺序。阶段切换不会自动触发。 */
public final class StoryStageCatalog {
    public static final String DREAM_BEGINNING_ID = "dreamingfishcore:dream_beginning";
    public static final String AFTERDREAM_ID = "dreamingfishcore:afterdream";

    private StoryStageCatalog() {
    }

    public static List<StageSeed> seeds() {
        return List.of(
                new StageSeed(DREAM_BEGINNING_ID, 1, "梦的开始"),
                new StageSeed(AFTERDREAM_ID, 2, "余梦期"));
    }

    public record StageSeed(String id, int number, String name) {
    }
}
