package com.hhy.dreamingfishcore.gameplay.playerattributes_system.first_stage;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.BuiltInNpcMessageCatalog;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcMessageManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.RespawnPointSyncManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.PlayerInfectionManager;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryWorldState;
import com.hhy.dreamingfishcore.server.server_management_system.ServerGameRulesManager;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import com.hhy.dreamingfishcore.server.login_system.event.PlayerAuthenticatedEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 第一阶段的临时生存宽松规则。
 *
 * <p>所有数值集中在这里，且通过故事阶段 ID 门控。阶段推进后，饱食度自然回血会
 * 自动恢复为关闭状态，白天感染回落和每日重生储备也会停止，避免第一阶段的
 * 保护性数值泄漏到后续剧情。70% 上限只由 FoodData mixin 应用于饱食度自然回血，
 * 药水、金苹果、状态效果和医疗物品等主动治疗不受影响。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class FirstStageSurvivalManager {
    /** 第一阶段由饱食度触发的自然回血最多到最大生命值的 70%。 */
    public static final float NATURAL_HEAL_CAP_RATIO = 0.70F;
    /** 一个完整 Minecraft 白天（12000 tick）减少 5 个感染百分点。 */
    public static final float DAYLIGHT_INFECTION_REDUCTION = 5.0F;
    public static final long DAYLIGHT_TICKS = 12000L;
    /** 每个游戏日恢复 5 点分裂/重生储备。 */
    public static final float DAILY_RESPAWN_RECHARGE = 5.0F;

    private static final int INFECTION_DECAY_INTERVAL_TICKS = 200;
    private static final float INFECTION_DECAY_PER_INTERVAL =
            DAYLIGHT_INFECTION_REDUCTION * INFECTION_DECAY_INTERVAL_TICKS / DAYLIGHT_TICKS;
    private static final long TICKS_PER_MINECRAFT_DAY = 24000L;

    private static final Map<MinecraftServer, RuntimeState> RUNTIME_STATES = new WeakHashMap<>();

    private FirstStageSurvivalManager() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        RuntimeState state = RUNTIME_STATES.computeIfAbsent(server, ignored -> new RuntimeState());
        state.lastObservedDay = currentDay(server);
        state.appliedStageId = "";
        applyStagePolicy(server, state);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        RuntimeState state = RUNTIME_STATES.computeIfAbsent(server, ignored -> new RuntimeState());
        applyStagePolicy(server, state);

        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        long day = currentDay(server);
        if (state.lastObservedDay == Long.MIN_VALUE) {
            state.lastObservedDay = day;
        } else if (day < state.lastObservedDay) {
            // /time set 或维度重置把时间拨回去了：重新对齐，不倒扣也不重复发放。
            state.lastObservedDay = day;
        } else if (day > state.lastObservedDay) {
            long elapsedDays = day - state.lastObservedDay;
            state.lastObservedDay = day;
            if (isFirstStage() && PlayerAttributesDataManager.isLoaded()) {
                rechargeRespawnPoints(server, elapsedDays);
            }
        }

        if (!isFirstStage()
                || !PlayerAttributesDataManager.isLoaded()) {
            return;
        }
        applyDaylightInfectionRecovery(server);
    }

    /** FoodData mixin 用它判断本 tick 是否还应继续累计饱食度自然回血。 */
    public static boolean canFoodNaturalHealingRun(Player player) {
        if (player == null || !player.isHurt()) {
            return false;
        }
        if (!shouldCapFoodNaturalHealing(player)) {
            return true;
        }
        return player.getHealth() < player.getMaxHealth() * NATURAL_HEAL_CAP_RATIO;
    }

    /** FoodData mixin 用它截断越过 70% 边界的最后一次饱食度回血。 */
    public static float limitFoodNaturalHealing(Player player, float requested) {
        if (!shouldCapFoodNaturalHealing(player)) {
            return requested;
        }
        return capFoodNaturalHealingAmount(player.getHealth(), player.getMaxHealth(), requested);
    }

    /** 纯数值边界，独立出来便于回归测试。 */
    public static float capFoodNaturalHealingAmount(
            float currentHealth,
            float maximumHealth,
            float requested) {
        if (!(requested > 0.0F)
                || Float.isNaN(requested)
                || Float.isInfinite(requested)
                || !(maximumHealth > 0.0F)
                || Float.isNaN(maximumHealth)
                || Float.isInfinite(maximumHealth)) {
            return 0.0F;
        }
        float cap = maximumHealth * NATURAL_HEAL_CAP_RATIO;
        return Math.max(0.0F, Math.min(requested, cap - currentHealth));
    }

    /** 登录后给玩家投递一次白芷的阶段规则私信。消息本身由 once=true 保证幂等。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerAuthenticated(PlayerAuthenticatedEvent event) {
        ServerPlayer player = event.getPlayer();
        if (!AuthSessionGuard.isAuthenticated(player)
                || !isFirstStage()
                || !NpcMessageManager.isWorldDataLoaded()) {
            return;
        }
        sendBaizhiProtocol(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStopped(ServerStoppedEvent event) {
        RUNTIME_STATES.remove(event.getServer());
    }

    /** 供测试和其他服务端规则使用的阶段判断。 */
    public static boolean isFirstStage() {
        return StoryWorldState.DEFAULT_STAGE_ID.equals(StoryManager.getCurrentStageIdOrDefault());
    }

    public static float getInfectionDecayPerInterval() {
        return INFECTION_DECAY_PER_INTERVAL;
    }

    private static boolean shouldCapFoodNaturalHealing(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || player.level().isClientSide()
                || !isFirstStage()) {
            return false;
        }
        GameType gameType = serverPlayer.gameMode.getGameModeForPlayer();
        return gameType != GameType.CREATIVE && gameType != GameType.SPECTATOR;
    }

    private static void applyStagePolicy(MinecraftServer server, RuntimeState state) {
        String stageId = StoryManager.getCurrentStageIdOrDefault();
        if (stageId.equals(state.appliedStageId)) {
            return;
        }
        boolean previouslyFirstStage = StoryWorldState.DEFAULT_STAGE_ID.equals(state.appliedStageId);
        boolean firstStage = StoryWorldState.DEFAULT_STAGE_ID.equals(stageId);
        ServerGameRulesManager.applyNaturalRegenerationPolicy(server, firstStage);
        state.appliedStageId = stageId;
        DreamingFishCore.LOGGER.info(
                "故事阶段 {} 的临时生存规则已{}：自然回血上限={}, 白天感染回落={}, 每日重生补充={}",
                stageId,
                firstStage ? "启用" : "关闭",
                firstStage ? "70%" : "关闭",
                firstStage ? "5点/白天" : "关闭",
                firstStage ? "5点" : "关闭");
        if (firstStage && !previouslyFirstStage && NpcMessageManager.isWorldDataLoaded()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (AuthSessionGuard.isAuthenticated(player)) {
                    sendBaizhiProtocol(player);
                }
            }
        }
    }

    private static void sendBaizhiProtocol(ServerPlayer player) {
        try {
            NpcMessageManager.sendConfiguredMessage(
                    player, BuiltInNpcMessageCatalog.BAIZHI_FIRST_STAGE_PROTOCOL_ID);
        } catch (RuntimeException exception) {
            // 登录或阶段切换不应因为可选的剧情私信失败而被中断。
            DreamingFishCore.LOGGER.warn(
                    "无法向玩家 {} 投递白芷第一阶段规则私信",
                    player.getScoreboardName(), exception);
        }
    }

    private static void applyDaylightInfectionRecovery(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isEligibleForSurvivalRules(player)
                    || player.tickCount % INFECTION_DECAY_INTERVAL_TICKS != 0
                    || !isDaylight(player.serverLevel())) {
                continue;
            }

            PlayerAttributesData data = PlayerAttributesDataManager.getPlayerAttributesData(player.getUUID());
            if (data.isInfected() || data.getCurrentInfection() <= 0.0F) {
                continue;
            }

            PlayerInfectionManager.reduceInfection(player, INFECTION_DECAY_PER_INTERVAL);
        }
    }

    private static void rechargeRespawnPoints(MinecraftServer server, long elapsedDays) {
        if (elapsedDays <= 0L) {
            return;
        }
        float recharge = (float) Math.min(
                (double) DAILY_RESPAWN_RECHARGE * elapsedDays,
                100.0D);
        Set<UUID> changedPlayers = PlayerAttributesDataManager.restoreRespawnPointsForAll(recharge);
        if (changedPlayers.isEmpty()) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!changedPlayers.contains(player.getUUID())
                    || !AuthSessionGuard.isAuthenticated(player)) {
                continue;
            }
            RespawnPointSyncManager.syncRespawnPointToClient(player);
        }
    }

    private static boolean isEligibleForSurvivalRules(ServerPlayer player) {
        GameType gameType = player.gameMode.getGameModeForPlayer();
        return player.isAlive() && gameType != GameType.CREATIVE && gameType != GameType.SPECTATOR;
    }

    private static boolean isDaylight(ServerLevel level) {
        if (level == null
                || !level.dimensionType().hasSkyLight()
                // 固定时间维度（例如末地）没有真实的昼夜循环，不应凭空恢复感染值。
                || level.dimensionType().fixedTime().isPresent()) {
            return false;
        }
        long timeOfDay = Math.floorMod(level.getDayTime(), TICKS_PER_MINECRAFT_DAY);
        return timeOfDay < DAYLIGHT_TICKS;
    }

    private static long currentDay(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return Long.MIN_VALUE;
        }
        return Math.floorDiv(overworld.getDayTime(), TICKS_PER_MINECRAFT_DAY);
    }

    private static final class RuntimeState {
        private String appliedStageId = "";
        private long lastObservedDay = Long.MIN_VALUE;
    }
}
