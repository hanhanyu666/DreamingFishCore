package com.hhy.dreamingfishcore.server.login_system.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 玩家完成服务端身份验证后派发的事件。
 *
 * <p>普通 {@code PlayerLoggedInEvent} 只表示网络连接建立，并不表示密码已经验证；
 * 需要发送个人数据或推进个人剧情的系统应监听本事件。</p>
 */
public final class PlayerAuthenticatedEvent extends PlayerEvent {
    public PlayerAuthenticatedEvent(ServerPlayer player) {
        super(player);
    }

    public ServerPlayer getPlayer() {
        return (ServerPlayer) getEntity();
    }
}
