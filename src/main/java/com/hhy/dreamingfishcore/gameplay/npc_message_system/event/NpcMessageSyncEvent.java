package com.hhy.dreamingfishcore.gameplay.npc_message_system.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceManager;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcMessageManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** 玩家登录时分别同步私信与个人引导。 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class NpcMessageSyncEvent {
    private NpcMessageSyncEvent() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NpcMessageManager.syncToClient(player);
            GuidanceManager.syncToClient(player);
        }
    }
}
