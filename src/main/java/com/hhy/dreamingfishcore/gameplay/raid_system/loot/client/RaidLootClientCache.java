package com.hhy.dreamingfishcore.gameplay.raid_system.loot.client;

import com.hhy.dreamingfishcore.gameplay.raid_system.loot.network.Packet_RaidLootSync;
import java.util.List;

/**
 * 客户端持有的露天物品节点（只在客户端用）。
 *
 * <p>服务端每次全量下发，这里只做"最后一次为准"的替换——不需要增量合并，
 * 也就不会有"漏了一条增量导致永远显示错"的问题。退出对局时服务端会发空列表清掉。</p>
 */
public final class RaidLootClientCache {

    private static volatile List<Packet_RaidLootSync.Entry> nodes = List.of();

    private RaidLootClientCache() {
    }

    public static void accept(List<Packet_RaidLootSync.Entry> received) {
        nodes = received == null ? List.of() : List.copyOf(received);
    }

    /** 未拾取的节点（渲染只关心这些）。 */
    public static List<Packet_RaidLootSync.Entry> visible() {
        return nodes.stream().filter(entry -> !entry.picked()).toList();
    }

    public static int size() {
        return nodes.size();
    }

    public static void clear() {
        nodes = List.of();
    }
}
