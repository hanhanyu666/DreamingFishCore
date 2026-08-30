package com.hhy.dreamingfishcore.server.server_management_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryWorldState;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * 统一维护全服游戏规则。
 * 第一阶段允许原版自然回血（具体上限由 FirstStageSurvivalManager 控制），
 * 后续阶段恢复关闭自然回血；死亡掉落交由自定义尸体/复活流程处理，不修改全局规则。
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public class ServerGameRulesManager {

    /** 服务器启动时按当前故事阶段应用自然回血策略。 */
    @SubscribeEvent
    public static void disableAllDimensionsNaturalRegeneration(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        boolean firstStage = StoryWorldState.DEFAULT_STAGE_ID.equals(
                StoryManager.getCurrentStageIdOrDefault());
        applyNaturalRegenerationPolicy(server, firstStage);
    }

    /**
     * 按故事阶段切换所有维度的自然回血规则。
     * 该方法由阶段生存管理器在阶段切换时调用。
     */
    public static void applyNaturalRegenerationPolicy(
            MinecraftServer server,
            boolean naturalRegenerationEnabled) {
        if (server == null) {
            return;
        }

        GameRules.Key<GameRules.BooleanValue> regenRuleKey = GameRules.RULE_NATURAL_REGENERATION;
        for (ServerLevel level : server.getAllLevels()) {
            level.getGameRules().getRule(regenRuleKey).set(naturalRegenerationEnabled, server);
        }
        LogUtils.getLogger().info(
                "已{}自然回血，死亡掉落由自定义尸体流程处理",
                naturalRegenerationEnabled ? "开启" : "关闭");
    }

    /**
     * 关闭指定维度的自然回血
     * @param targetLevel 目标服务端维度
     */
    public static void disableSpecifiedDimensionNaturalRegeneration(ServerLevel targetLevel) {
        // 非空判断（服务端实例 + 目标维度）
        MinecraftServer server = GetServerInstance.SERVER_INSTANCE;
        if (server == null || targetLevel == null) {
            // LogUtils.getLogger().warn("服务端实例或目标维度为空，无法关闭自然回血！");
            return;
        }

        GameRules.Key<GameRules.BooleanValue> regenRuleKey = GameRules.RULE_NATURAL_REGENERATION;
        targetLevel.getGameRules().getRule(regenRuleKey).set(false, server);

        // 可选：日志输出
        // LogUtils.getLogger().info("维度 " + targetLevel.dimension().location() + " 已关闭自然回血功能");
    }
}
