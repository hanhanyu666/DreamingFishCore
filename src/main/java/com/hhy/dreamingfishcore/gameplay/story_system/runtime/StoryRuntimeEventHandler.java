package com.hhy.dreamingfishcore.gameplay.story_system.runtime;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationDefinition;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationManager;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import com.hhy.dreamingfishcore.server.login_system.event.PlayerAuthenticatedEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** 把已验证的登录和游戏行为转换为故事事实。 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class StoryRuntimeEventHandler {
    private StoryRuntimeEventHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerAuthenticated(PlayerAuthenticatedEvent event) {
        ServerPlayer player = event.getPlayer();
        if (AuthSessionGuard.isAuthenticated(player)) {
            StoryFlowEngine.onPlayerAuthenticated(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StoryFlowEngine.onPlayerDisconnected(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.level().isClientSide()
                || !AuthSessionGuard.isAuthenticated(player)
                || player.tickCount % 20 != 0) {
            return;
        }
        TaskLocationDefinition location = TaskLocationManager.findLocationAt(
                player.serverLevel(), player.blockPosition()).orElse(null);
        StoryFlowEngine.onPlayerLocationTick(player, location);
    }
}
