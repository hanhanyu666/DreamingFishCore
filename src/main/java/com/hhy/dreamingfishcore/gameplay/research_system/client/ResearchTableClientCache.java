package com.hhy.dreamingfishcore.gameplay.research_system.client;

import com.hhy.dreamingfishcore.gameplay.research_system.network.Packet_ResearchTableOpen;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 研究桌界面的客户端缓存。
 *
 * <p>快照由服务端下发（{@link Packet_ResearchTableOpen}），界面每次渲染都从这里读，
 * 所以服务端在"研究完成"后再推一份，界面就自动刷新了——不需要界面自己去拉数据。</p>
 */
public final class ResearchTableClientCache {

    private static volatile Snapshot snapshot;

    private ResearchTableClientCache() {
    }

    /** 界面用的只读快照。 */
    public record Snapshot(BlockPos pos,
                           List<String> offer,
                           int cost,
                           int playerExperience,
                           List<String> learned,
                           String message,
                           boolean available,
                           int submitDivisor,
                           boolean canSubmit,
                           String submitStatus) {
    }

    /** 由网络包在收到服务端快照时调用（在客户端的渲染线程上）。 */
    public static void accept(Packet_ResearchTableOpen packet) {
        snapshot = new Snapshot(
                packet.pos(),
                List.copyOf(packet.offer()),
                packet.cost(),
                packet.playerExperience(),
                List.copyOf(packet.learned()),
                packet.message(),
                packet.available(),
                packet.submitDivisor(),
                packet.canSubmit(),
                packet.submitStatus());
    }

    /** 当前快照；服务端还没推过时为 {@code null}。 */
    public static Snapshot get() {
        return snapshot;
    }

    public static void clear() {
        snapshot = null;
    }
}
