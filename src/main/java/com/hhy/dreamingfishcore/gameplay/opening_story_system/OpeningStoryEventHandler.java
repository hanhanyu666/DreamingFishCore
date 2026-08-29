package com.hhy.dreamingfishcore.gameplay.opening_story_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationDefinition;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** 把玩家登录和所在任务地点转换为开场剧情执行器可验证的服务端事件。 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class OpeningStoryEventHandler {
    private OpeningStoryEventHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            OpeningStoryProgressManager.onPlayerLogin(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.level().isClientSide()
                || player.tickCount % 20 != 0) {
            return;
        }
        TaskLocationDefinition location = TaskLocationManager.findLocationAt(
                player.serverLevel(), player.blockPosition()).orElse(null);
        OpeningStoryProgressManager.onPlayerLocationTick(player, location);
    }
}
