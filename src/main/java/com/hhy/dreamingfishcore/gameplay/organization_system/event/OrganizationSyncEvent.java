package com.hhy.dreamingfishcore.gameplay.organization_system.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationManager;
import com.hhy.dreamingfishcore.gameplay.organization_system.network.OrganizationSync;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import com.hhy.dreamingfishcore.server.login_system.event.PlayerAuthenticatedEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * 玩家完成登录认证后刷新组织显示名并下发快照。
 *
 * <p>监听认证事件而不是 {@code PlayerLoggedInEvent}：后者只表示连接建立，
 * 未通过密码验证的客户端不应该收到任何个人数据。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class OrganizationSyncEvent {

    private OrganizationSyncEvent() {
    }

    @SubscribeEvent
    public static void onPlayerAuthenticated(PlayerAuthenticatedEvent event) {
        ServerPlayer player = event.getPlayer();
        if (!AuthSessionGuard.isAuthenticated(player)) {
            return;
        }
        OrganizationManager.onPlayerLogin(player);
        OrganizationSync.sendSnapshot(player);
    }
}
