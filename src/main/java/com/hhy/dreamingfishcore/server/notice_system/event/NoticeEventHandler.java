package com.hhy.dreamingfishcore.server.notice_system.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.notice_system.NoticeDeliveryService;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import com.hhy.dreamingfishcore.server.login_system.event.PlayerAuthenticatedEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * 公告系统事件处理器
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public class NoticeEventHandler {

    /**
     * 玩家登录时发送教程常驻提示，并补投符合条件的新公告。
     */
    @SubscribeEvent
    public static void onPlayerAuthenticated(PlayerAuthenticatedEvent event) {
        ServerPlayer player = event.getPlayer();
        if (AuthSessionGuard.isAuthenticated(player)) {
            NewPlayerGuide.sendNewPlayerGuide(player);
            NoticeDeliveryService.deliverPendingOnLogin(player);
        }
    }
}
