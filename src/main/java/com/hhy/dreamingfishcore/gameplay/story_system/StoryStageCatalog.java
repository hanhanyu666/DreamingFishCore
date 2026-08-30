package com.hhy.dreamingfishcore.gameplay.story_system;

import java.util.List;

/** 当前服务器五阶段的稳定身份与显示顺序。阶段切换不会由本表自动触发。 */
public final class StoryStageCatalog {
    public static final String DREAM_BEGINNING_ID = "dreamingfishcore:dream_beginning";
    public static final String AFTERDREAM_ID = "dreamingfishcore:afterdream";
    public static final String CONTROL_PERIOD_ID = "dreamingfishcore:control_period";
    public static final String LIGHT_DOUBT_ID = "dreamingfishcore:light_doubt";
    public static final String DAWN_ID = "dreamingfishcore:dawn";

    private StoryStageCatalog() {
    }

    public static List<StageSeed> seeds() {
        return List.of(
                new StageSeed(DREAM_BEGINNING_ID, 1, "梦的开始"),
                new StageSeed(AFTERDREAM_ID, 2, "余梦期"),
                new StageSeed(CONTROL_PERIOD_ID, 3, "管制期"),
                new StageSeed(LIGHT_DOUBT_ID, 4, "疑光期"),
                new StageSeed(DAWN_ID, 5, "破晓期"));
    }

    public record StageSeed(String id, int number, String name) {
    }
}
