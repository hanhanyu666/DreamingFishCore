package com.hhy.dreamingfishcore.init;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.clue_system.ClueDropConfig;
import com.hhy.dreamingfishcore.gameplay.kill_effect_system.KillEffectConfig;
import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationConfig;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcManager;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcMessageManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.limb_health_system.LimbDamageConfig;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieSpeciesConfig;
import com.hhy.dreamingfishcore.server.notice_system.NoticeManager;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Common-side startup wiring for systems that require eager initialization.
 * Registry-specific startup remains in the corresponding content registry class.
 */
public final class CommonInit {
    public static final Path CONFIG_DIRECTORY = FMLPaths.CONFIGDIR.get().resolve(DreamingFishCore.MODID);

    private CommonInit() {
    }

    public static void initialize() {
        try {
            Files.createDirectories(CONFIG_DIRECTORY);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create DreamingFishCore config directory", exception);
        }

        NoticeManager.loadFromConfig();
        NpcManager.init();
        NpcMessageManager.init();
        LimbDamageConfig.init();
        ClueDropConfig.init();
        // 线索目录要在随记本加载玩家数据之前就绪：旧整数编号迁移到稳定 ID 时要用它查映射。
        com.hhy.dreamingfishcore.gameplay.clue_system.ClueCatalog.load();
        OrganizationConfig.init();
        KillEffectConfig.init();
        ZombieSpeciesConfig.init();
    }
}
