package com.hhy.dreamingfishcore.gameplay.story_system;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 同一批不同玩家各自做完全部前置任务；不把不同人的半条任务线拼成完成。 */
public record StoryPlayerGate(List<String> personalTaskIds, int requiredPlayers) {
    public StoryPlayerGate {
        if (personalTaskIds == null || personalTaskIds.isEmpty() || personalTaskIds.size() > 64
                || requiredPlayers < 1 || requiredPlayers > 16384) {
            throw new IllegalArgumentException("世界任务门槛必须包含前置个人任务和 1–16384 名完成人数");
        }
        personalTaskIds = List.copyOf(personalTaskIds);
        if (Set.copyOf(personalTaskIds).size() != personalTaskIds.size()) {
            throw new IllegalArgumentException("前置个人任务不能重复");
        }
        personalTaskIds.forEach(id -> StoryWorldState.requireValidId(id, "前置个人任务"));
    }

    public int completedPlayers(Map<String, Map<String, String>> completions) {
        Set<String> players = new LinkedHashSet<>(completions.getOrDefault(personalTaskIds.getFirst(), Map.of()).keySet());
        for (String task : personalTaskIds) {
            players.retainAll(completions.getOrDefault(task, Map.of()).keySet());
        }
        return players.size();
    }

    public boolean isSatisfied(Map<String, Map<String, String>> completions) {
        return completedPlayers(completions) >= requiredPlayers;
    }
}
