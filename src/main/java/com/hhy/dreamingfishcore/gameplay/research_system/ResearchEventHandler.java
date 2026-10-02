package com.hhy.dreamingfishcore.gameplay.research_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 研究桌的玩家生命周期挂钩。
 *
 * <p>只做一件事：玩家登出时清掉为他缓存的那批课题。不清也不会出错（下次打开会因为失效而重掷），
 * 但缓存里留着离线玩家的数据不干净。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class ResearchEventHandler {

    private ResearchEventHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ResearchService.clearPending(event.getEntity().getUUID());
    }
}
