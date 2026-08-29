package com.hhy.dreamingfishcore.server.notice_system.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.notice_system.NoticeDeliveryService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 登录系统在单人模式会主动跳过认证流程，因此不能只依赖
            // PlayerLoginEvent/登录响应包发送教程提示。这里统一补发一次，
            // 让所有模式的未完成教程玩家都能看到常驻引导；replaceKey 会
            // 自动合并同一登录流程中可能产生的重复提示。
            NewPlayerGuide.sendNewPlayerGuide(player);
            NoticeDeliveryService.deliverPendingOnLogin(player);
        }
    }
}
