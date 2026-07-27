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
        // [已禁用] 进服模组更新检查提示：建筑服不需要，不再检查更新、不再发提示。
        // 如需恢复，去掉下面这段块注释即可。
        /*
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            UpdateChecker.checkForUpdates(serverPlayer);
        }
        */
    }
}
