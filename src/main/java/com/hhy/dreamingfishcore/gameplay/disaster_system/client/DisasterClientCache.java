package com.hhy.dreamingfishcore.gameplay.disaster_system.client;

import com.hhy.dreamingfishcore.gameplay.disaster_system.network.Packet_DisasterSnapshotResponse;

/**
 * 客户端手里的最新天灾日程快照（只在客户端用）。
 *
 * <p>服务端每次调整后都会重发全量快照，所以这里只做"最后一次为准"的替换——
 * 不需要增量合并，也不会出现"漏了一条增量导致界面永远显示错"的问题。</p>
 */
public final class DisasterClientCache {

    private static volatile Packet_DisasterSnapshotResponse snapshot;

    private DisasterClientCache() {
    }

    public static void accept(Packet_DisasterSnapshotResponse response) {
        snapshot = response;
    }

    public static Packet_DisasterSnapshotResponse snapshot() {
        return snapshot;
    }

    public static boolean loaded() {
        return snapshot != null;
    }

    public static void clear() {
        snapshot = null;
    }
}
