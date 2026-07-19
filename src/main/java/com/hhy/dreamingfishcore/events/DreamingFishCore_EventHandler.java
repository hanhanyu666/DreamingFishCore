package com.hhy.dreamingfishcore.events;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.commands.check_system.Command_Check;
import com.hhy.dreamingfishcore.commands.check_system.Command_Info;
import com.hhy.dreamingfishcore.core.update_checker_system.UpdateChecker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DreamingFishCore.MODID)
public class DreamingFishCore_EventHandler {
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        Command_Info.register(event.getServer().getCommands().getDispatcher());
        Command_Check.register(event.getServer().getCommands().getDispatcher());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            UpdateChecker.checkForUpdates(serverPlayer);
        }
    }
}
