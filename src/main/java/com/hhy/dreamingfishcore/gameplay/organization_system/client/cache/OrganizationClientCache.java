package com.hhy.dreamingfishcore.gameplay.organization_system.client.cache;

import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationViewData;

import java.util.List;

/**
 * 客户端持有的组织只读快照。
 *
 * <p>只存服务端下发的数据，不做任何权威判断；{@link #version()} 递增用于让界面知道需要重绘。</p>
 */
public final class OrganizationClientCache {

    private static volatile OrganizationViewData.Snapshot snapshot = empty();
    private static volatile boolean loaded;
    private static volatile long version;

    private OrganizationClientCache() {
    }

    private static OrganizationViewData.Snapshot empty() {
        return new OrganizationViewData.Snapshot(
                false, 32, 12, 200, 0, "", List.of(), null);
    }

    public static synchronized void set(OrganizationViewData.Snapshot value) {
        snapshot = value == null ? empty() : value;
        loaded = true;
        version++;
    }

    public static synchronized void clear() {
        snapshot = empty();
        loaded = false;
        version++;
    }

    public static OrganizationViewData.Snapshot get() {
        return snapshot;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static long version() {
        return version;
    }
}
