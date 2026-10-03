package com.hhy.dreamingfishcore.gameplay.raid_system.anchor;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * 锚点服务的生命周期：开局加载，关服清缓存。
 *
 * <p>清缓存是必须的：目录是静态字段，单人切存档时不清理就会拿着上一个世界的锚点干活。
 * 加载失败不影响启动——服务内部对每个文件/每条数据都做软失败处理。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class RaidAnchorEvents {

    private RaidAnchorEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        RaidAnchorService.reload(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RaidAnchorService.clear();
    }
}
