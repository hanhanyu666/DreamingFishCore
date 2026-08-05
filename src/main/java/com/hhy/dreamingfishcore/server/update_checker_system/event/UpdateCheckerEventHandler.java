package com.hhy.dreamingfishcore.server.update_checker_system.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.update_checker_system.UpdateChecker;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class UpdateCheckerEventHandler {
    private UpdateCheckerEventHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // [已禁用] 进服模组更新检查提示：建筑服不需要，不再检查更新、不再发提示。
        // 如需恢复，去掉下面这段块注释即可。
        /*
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            UpdateChecker.checkForUpdates(serverPlayer);
        }
        */
    }
}
