package com.hhy.dreamingfishcore.commands;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.npc_system.command.Command_Npc;
import com.hhy.dreamingfishcore.gameplay.guidance_system.command.Command_Guidance;
import com.hhy.dreamingfishcore.gameplay.playerlevel_system.command.Command_Biomes;
import com.hhy.dreamingfishcore.gameplay.playerlevel_system.command.Command_OverAllLevel;
import com.hhy.dreamingfishcore.gameplay.story_system.command.Command_Story;
import com.hhy.dreamingfishcore.gameplay.task_location_system.command.Command_TaskLocation;
import com.hhy.dreamingfishcore.gameplay.task_system.command.Command_Task;
import com.hhy.dreamingfishcore.gameplay.zhuiguang_system.command.Command_Zhuiguang;
import com.hhy.dreamingfishcore.gameplay.zombie_system.command.Command_ZombieSpecies;
import com.hhy.dreamingfishcore.server.check_system.command.Command_Check;
import com.hhy.dreamingfishcore.server.check_system.command.Command_Info;
import com.hhy.dreamingfishcore.server.notice_system.command.Command_Notice;
import com.hhy.dreamingfishcore.server.rank_system.command.Command_Rank;
import com.hhy.dreamingfishcore.server.title_system.command.Command_Title;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class DreamingFishCore_CommandManager {
    private DreamingFishCore_CommandManager() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();

        Command_Info.register(dispatcher);
        Command_Check.register(dispatcher);
        Command_Notice.register(dispatcher);
        Command_Rank.register(dispatcher);
        Command_Title.register(dispatcher);

        Command_Npc.register(dispatcher);
        Command_Guidance.register(dispatcher);
        Command_Story.register(dispatcher);
        Command_Zhuiguang.register(dispatcher);
        Command_TaskLocation.register(dispatcher);
        Command_Biomes.register(dispatcher);
        Command_OverAllLevel.register(dispatcher);
        Command_Task.register(dispatcher);
        Command_ZombieSpecies.register(dispatcher);
    }
}
