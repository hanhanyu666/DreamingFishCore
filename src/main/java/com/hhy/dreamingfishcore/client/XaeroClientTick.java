package com.hhy.dreamingfishcore.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.integration.xaero.XaeroIntegration;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 客户端每 tick 给 Xaero 联动一次"要不要挂上"的机会。
 *
 * <p>只是调用 {@link XaeroIntegration#tick()}——它内部第一行就判断好了，
 * 没装 Xaero 时不会有任何额外开销。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class XaeroClientTick {

    private XaeroClientTick() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        XaeroIntegration.tick();
    }
}
