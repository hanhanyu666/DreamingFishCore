package com.hhy.dreamingfishcore.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.map_region_system.network.Packet_MapRegionRequest;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * 客户端进服后主动要一次区域快照。
 *
 * <p>服务端登录时也会推一份，这里是**双保险**：重连、切维度或错过那次推送时都能补上，
 * 而且两次全量快照只是"后到的覆盖先到的"，不会出现叠加错误。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class MapRegionClientJoin {

    private MapRegionClientJoin() {
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        DreamingFishCore_NetworkManager.sendToServer(new Packet_MapRegionRequest());
    }
}
