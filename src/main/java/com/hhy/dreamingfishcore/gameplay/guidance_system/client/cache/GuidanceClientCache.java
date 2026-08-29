package com.hhy.dreamingfishcore.gameplay.guidance_system.client.cache;

import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceEntry;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceViewData;

import java.util.List;

/** 当前客户端玩家的个人引导只读快照。 */
public final class GuidanceClientCache {
    private static List<GuidanceViewData> entries = List.of();
    private static boolean loaded;

    private GuidanceClientCache() {
    }

    public static synchronized void set(List<GuidanceViewData> snapshot) {
        entries = snapshot == null ? List.of() : List.copyOf(snapshot);
        loaded = true;
    }

    public static synchronized List<GuidanceViewData> getEntries() {
        return entries;
    }

    public static synchronized int getActiveCount() {
        return (int) entries.stream()
                .filter(entry -> entry.status() == GuidanceEntry.Status.ACTIVE)
                .count();
    }

    public static synchronized boolean isLoaded() {
        return loaded;
    }

    public static synchronized void clear() {
        entries = List.of();
        loaded = false;
    }
}
