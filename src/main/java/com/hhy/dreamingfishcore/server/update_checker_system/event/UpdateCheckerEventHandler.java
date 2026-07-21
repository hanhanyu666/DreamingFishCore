package com.hhy.dreamingfishcore.server.update_checker_system.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.update_checker_system.UpdateChecker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class UpdateCheckerEventHandler {
    private UpdateCheckerEventHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            UpdateChecker.checkForUpdates(serverPlayer);
        }
    }
}
