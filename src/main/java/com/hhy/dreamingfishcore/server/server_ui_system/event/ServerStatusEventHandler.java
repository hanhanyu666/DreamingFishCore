package com.hhy.dreamingfishcore.server.server_ui_system.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import com.hhy.dreamingfishcore.server.login_system.event.PlayerAuthenticatedEvent;
import com.hhy.dreamingfishcore.server.server_ui_system.network.Packet_OnlinePlayerCountRequest;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Sends the first status sample as soon as the custom login succeeds. */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class ServerStatusEventHandler {
    private ServerStatusEventHandler() {
    }

    @SubscribeEvent
    public static void onPlayerAuthenticated(PlayerAuthenticatedEvent event) {
        ServerPlayer player = event.getPlayer();
        if (AuthSessionGuard.isAuthenticated(player)) {
            Packet_OnlinePlayerCountRequest.sendCurrentStatus(player);
        }
    }
}
