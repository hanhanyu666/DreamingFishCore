package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.effect.DreamingFishCore_Effects;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.cache.PlayerAttributesClientCache;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = DreamingFishCore.MODID)
public class PlayerInfectionManager {
    /** 面具事件前，感染者仍按原有 0—100 规则计算。 */
    public static final float PRE_MASK_INFECTION_MAX = 100.0F;
    /** 面具事件后，新感染者按 0—200 规则计算。 */
    public static final float POST_MASK_INFECTION_MAX = 200.0F;
    /** 首次实际发放面具后写入世界存档的全局事实。 */
    public static final String PROTECTIVE_MASK_DISTRIBUTED_WORLD_FLAG =
            "dreamingfishcore:afterdream/protective_mask_distributed";

    private static final int INFECTION_CHECK_INTERVAL = 40;
    private static final float INFECTION_EPSILON = 0.01F;
    /** 「感染」效果的持续时间；检查间隔是 40 tick，取同样长度即可无缝隙覆盖。 */
    private static final int INFECTION_EFFECT_DURATION_TICKS = 40;
    /** 面具阶段新晋一级感染者可以接受治疗的完整游戏日。 */
    public static final long NEW_LEVEL_ONE_TREATMENT_WINDOW_TICKS = 24_000L;

    // 记录玩家已显示过的消息级别：0=无, 1=50%警告, 2=80%警告, 3=100%警告
    private static final Map<UUID, Integer> INFECTION_MSG_SHOWN = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide() || !event.getEntity().isAlive()
                || !(event.getEntity() instanceof ServerPlayer serverPlayer)
                || !AuthSessionGuard.isAuthenticated(serverPlayer)) {
            return;
        }
        if (serverPlayer.gameMode.getGameModeForPlayer() == GameType.CREATIVE) {
            return;
        }

        UUID playerUUID = serverPlayer.getUUID();
        PlayerAttributesData attributesData = PlayerAttributesDataManager.getPlayerAttributesData(playerUUID);
        if (attributesData == null) {
            return;
        }

        // 处理服务器重启或旧脚本留下的“感染值已到阈值但状态尚未写入”情况。
        if (markInfectedAtThreshold(serverPlayer, attributesData)) {
            PlayerAttributesDataManager.updatePlayerAttributesData(serverPlayer, attributesData);
            sendInfectionStateMessage(serverPlayer, attributesData);
            syncInfectionData(serverPlayer, attributesData);
        }

        if (serverPlayer.tickCount % INFECTION_CHECK_INTERVAL != 0) {
            return;
        }

        int infectionMaximum = getInfectionMaximum(serverPlayer, attributesData);
        float currentInfection = attributesData.getCurrentInfection();
        float infectionRatio = infectionRatio(currentInfection, infectionMaximum);
        int msgShownLevel = INFECTION_MSG_SHOWN.getOrDefault(playerUUID, 0);

        if (infectionRatio >= 1.0F) {
            applyInfectionDebuff(serverPlayer, attributesData);

            if (msgShownLevel < 3) {
                sendInfectionStateMessage(serverPlayer, attributesData);
                INFECTION_MSG_SHOWN.put(playerUUID, 3);
            }
        } else if (infectionRatio >= 0.8F) {
            applyInfectionDebuff(serverPlayer, attributesData);

            if (msgShownLevel < 2) {
                serverPlayer.displayClientMessage(
                        Component.literal("§c感染值过高，您的身体正在恶化..."), true);
                INFECTION_MSG_SHOWN.put(playerUUID, 2);
            }
        } else if (infectionRatio >= 0.5F) {
            if (msgShownLevel < 1) {
                serverPlayer.displayClientMessage(
                        Component.literal("§e您感到身体有些不适..."), true);
                INFECTION_MSG_SHOWN.put(playerUUID, 1);
            }
        }
    }

    /**
     * 每服务器 tick 检查面具阶段新一级感染者的个人治疗窗口。
     * 只处理截止时间已到的那一名玩家，不会批量改写其他一级感染者。
     *
     * <p>窗口到期表示突变没能被及时逆转，玩家由此成为<b>稳定感染者</b>；
     * 身份写入统一交给 {@link InfectionTreatmentService}。</p>
     */
    public static void tickTreatmentWindows(net.minecraft.server.MinecraftServer server) {
        if (server == null || !isPostMaskEraEnabled()
                || !PlayerAttributesDataManager.isLoaded()) {
            return;
        }
        long activeTick;
        try {
            activeTick = StoryManager.getSnapshot().activeTicks();
        } catch (RuntimeException exception) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!AuthSessionGuard.isAuthenticated(player)) {
                continue;
            }
            PlayerAttributesData data = PlayerAttributesDataManager
                    .findStoredPlayerAttributesData(player.getUUID());
            if (data == null || !data.hasPendingInfectionTreatmentWindow()
                    || data.getInfectionTreatmentDeadlineActiveTick() > activeTick) {
                continue;
            }
            InfectionTreatmentService.TreatmentOutcome outcome =
                    InfectionTreatmentService.applyStabilization(player,
                            "§4治疗窗口已经关闭，突变就此稳定：你成为稳定感染者。");
            if (outcome == InfectionTreatmentService.TreatmentOutcome.APPLIED) {
                StoryManager.onVirusEvolution();
            }
        }
    }

    /**
     * 增加感染值并在达到当前阶段阈值时写入对应感染等级。
     */
    public static void addInfection(ServerPlayer player, float amount) {
        if (player == null || amount <= 0 || !AuthSessionGuard.isAuthenticated(player)) {
            return;
        }

        UUID playerUUID = player.getUUID();
        PlayerAttributesData attributesData = PlayerAttributesDataManager.getPlayerAttributesData(playerUUID);
        if (attributesData == null) {
            return;
        }
        if (attributesData.isInfected()) {
            return;
        }

        float currentInfection = attributesData.getCurrentInfection();
        float infectionMaximum = getInfectionMaximum(player, attributesData);
        float newInfection = Math.min(currentInfection + amount, infectionMaximum);
        boolean valueChanged = Math.abs(newInfection - currentInfection) >= INFECTION_EPSILON;
        if (valueChanged) {
            attributesData.setCurrentInfection(newInfection);
        }

        boolean becameInfected = markInfectedAtThreshold(player, attributesData);
        if (!valueChanged && !becameInfected) {
            return;
        }

        PlayerAttributesDataManager.updatePlayerAttributesData(player, attributesData);

        if (becameInfected) {
            sendInfectionStateMessage(player, attributesData);
        }
        syncInfectionData(player, attributesData);
    }

    public static void reduceInfection(ServerPlayer player, float amount) {
        if (player == null || amount <= 0 || !AuthSessionGuard.isAuthenticated(player)) {
            return;
        }

        UUID playerUUID = player.getUUID();
        PlayerAttributesData attributesData = PlayerAttributesDataManager.getPlayerAttributesData(playerUUID);
        if (attributesData == null) {
            return;
        }

        float currentInfection = attributesData.getCurrentInfection();
        float newInfection = Math.max(currentInfection - amount, 0.0F);

        if (Math.abs(newInfection - currentInfection) < INFECTION_EPSILON) {
            return;
        }

        attributesData.setCurrentInfection(newInfection);
        PlayerAttributesDataManager.updatePlayerAttributesData(player, attributesData);

        // 感染值降低时重置消息级别，允许重新触发警告；阈值按当前阶段比例计算。
        float newInfectionRatio = infectionRatio(
                newInfection, getInfectionMaximum(player, attributesData));
        int currentMsgLevel = INFECTION_MSG_SHOWN.getOrDefault(playerUUID, 0);
        int newMsgLevel = currentMsgLevel;

        if (newInfectionRatio < 0.5F) {
            newMsgLevel = 0;
        } else if (newInfectionRatio < 0.8F && currentMsgLevel >= 2) {
            newMsgLevel = 1;
        } else if (newInfectionRatio < 1.0F && currentMsgLevel >= 3) {
            newMsgLevel = 2;
        }

        if (newMsgLevel != currentMsgLevel) {
            if (newMsgLevel == 0) {
                INFECTION_MSG_SHOWN.remove(playerUUID);
            } else {
                INFECTION_MSG_SHOWN.put(playerUUID, newMsgLevel);
            }
        }

        syncInfectionData(player, attributesData);
    }

    /**
     * 是否已经发生“首个玩家实际领取面具”的全局事件。
     * 故事系统尚未加载时按 false 处理，避免启动阶段提前切换规则。
     */
    public static boolean isPostMaskEraEnabled() {
        try {
            return StoryManager.hasWorldFlag(PROTECTIVE_MASK_DISTRIBUTED_WORLD_FLAG);
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    /**
     * 判断某个玩家当前是否使用面具发放后的 0—200 感染进度尺度。
     * 只有首个面具实际领取写入的全局旗标才能切换尺度；二级感染者始终使用 0—200。
     */
    public static boolean usesPostMaskRules(ServerPlayer player, PlayerAttributesData data) {
        if (data == null) {
            return isPostMaskEraEnabled();
        }
        return data.isLevelTwoInfected() || isPostMaskEraEnabled();
    }

    public static int getInfectionMaximum(ServerPlayer player, PlayerAttributesData data) {
        return usesPostMaskRules(player, data)
                ? (int) POST_MASK_INFECTION_MAX
                : (int) PRE_MASK_INFECTION_MAX;
    }

    /** 供同步调用方使用的便捷入口。 */
    public static int getInfectionMaximum(ServerPlayer player) {
        if (player == null || !PlayerAttributesDataManager.isLoaded()) {
            return isPostMaskEraEnabled()
                    ? (int) POST_MASK_INFECTION_MAX
                    : (int) PRE_MASK_INFECTION_MAX;
        }
        PlayerAttributesData data = PlayerAttributesDataManager.findStoredPlayerAttributesData(player.getUUID());
        return getInfectionMaximum(player, data);
    }

    /**
     * 在一次感染值写入后检查当前阈值，并写入"不稳定感染者"身份。
     * 面具阶段的新晋不稳定感染者会额外保存一个个人治疗截止 tick；截止后转为稳定感染者。
     */
    public static boolean markInfectedAtThreshold(ServerPlayer player, PlayerAttributesData data) {
        if (player == null || data == null || data.isInfected()) {
            return false;
        }
        int infectionMaximum = getInfectionMaximum(player, data);
        if (data.getCurrentInfection() < infectionMaximum) {
            return false;
        }

        boolean postMask = usesPostMaskRules(player, data);
        // 活动时钟不可用时（故事运行时尚未加载）不排期治疗窗口，避免写入一个立刻到期的期限。
        long activeTick = InfectionTreatmentService.currentActiveTick();
        return InfectionTreatmentService.becomeUnstable(player, data, postMask, activeTick);
    }

    /**
     * 将感染状态交给复活系统时使用的唯一规则入口。
     *
     * <p>复活目标不是一次普通的“写入 100 点”操作：面具阶段的感染上限是 200，
     * 新产生的不稳定感染者还必须拥有自己的治疗截止时间；稳定感染者则始终落在
     * 200 点。把这些规则集中在这里，避免死亡/复活网络包复制一份已经过时的数值逻辑。</p>
     *
     * <p>传播复发与复发冷却<b>不随复活传递</b>：被复活的身体是按生命模板重建的，
     * 复发必须由这名玩家自己再次受到重伤或高污染刺激才会发生。否则一次救援就会
     * 把施救者剩下几分钟的复发窗口连同一段冷却一起过继给另一个人。</p>
     */
    public static void applyRevivalInfectionState(
            PlayerAttributesData source, PlayerAttributesData target) {
        if (source == null || target == null) {
            return;
        }
        long activeTick;
        try {
            activeTick = StoryManager.getSnapshot().activeTicks();
        } catch (RuntimeException exception) {
            activeTick = 0L;
        }
        applyRevivalInfectionState(source, target, isPostMaskEraEnabled(), activeTick);
    }

    /** 纯规则重载，供单元测试验证复活状态映射，不读取服务器或玩家实体。 */
    static void applyRevivalInfectionState(
            PlayerAttributesData source,
            PlayerAttributesData target,
            boolean postMaskEra,
            long activeTick) {
        // 复发是临时身体状态，不随复活过继；被复活者一律从"不在复发中"开始。
        target.clearRelapseState();

        if (!source.isInfected() || source.getInfectionLevel() == PlayerAttributesData.INFECTION_LEVEL_NONE) {
            target.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_NONE);
            target.setCurrentInfection(0.0F);
            target.clearInfectionTreatmentDeadline();
            return;
        }

        if (source.getInfectionLevel() == PlayerAttributesData.INFECTION_LEVEL_TWO) {
            target.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_TWO);
            target.setCurrentInfection(POST_MASK_INFECTION_MAX);
            target.clearInfectionTreatmentDeadline();
            return;
        }

        target.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_ONE);
        if (postMaskEra) {
            target.setCurrentInfection(POST_MASK_INFECTION_MAX);
            // 只有面具时代新产生的不稳定感染者才有治疗期限。
            // 复活一个在面具发放前就已存在的不稳定感染者时，不能因为复活动作
            // 重新开始 24,000 tick 倒计时；已经存在的期限则原样保留。
            if (source.hasPendingInfectionTreatmentWindow()) {
                target.setInfectionTreatmentDeadlineActiveTick(
                        source.getInfectionTreatmentDeadlineActiveTick());
            } else {
                target.clearInfectionTreatmentDeadline();
            }
        } else {
            target.setCurrentInfection(PRE_MASK_INFECTION_MAX);
            target.clearInfectionTreatmentDeadline();
        }
    }

    /**
     * 江晚实际发放面具后的唯一状态入口。调用方必须在物品成功进入玩家库存（或掉落）后调用。
     * 全局旗标只在这里写入，倒计时、公告和普通首次对话都不会触发它。
     */
    public static boolean onProtectiveMaskGranted(ServerPlayer player) {
        if (player == null || !AuthSessionGuard.isAuthenticated(player)
                || !PlayerAttributesDataManager.isLoaded()) {
            return false;
        }

        PlayerAttributesData data = PlayerAttributesDataManager.findStoredPlayerAttributesData(
                player.getUUID());
        if (data == null) {
            return false;
        }

        boolean activatedNow = false;
        boolean receiptWasNew = data.recordProtectiveMaskReceipt();
        if (!isPostMaskEraEnabled()) {
            if (!StoryManager.areWritesEnabled()) {
                if (receiptWasNew) {
                    data.setProtectiveMaskReceived(false);
                }
                DreamingFishCore.LOGGER.error(
                        "玩家 {} 已领取防护面具，但故事世界状态尚未允许写入，无法开启感染阶段切换",
                        player.getScoreboardName());
                return false;
            }
            try {
                StoryManager.setWorldFlag(PROTECTIVE_MASK_DISTRIBUTED_WORLD_FLAG, true);
            } catch (RuntimeException exception) {
                if (receiptWasNew) {
                    data.setProtectiveMaskReceived(false);
                }
                DreamingFishCore.LOGGER.error("记录首个防护面具领取事件失败", exception);
                return false;
            }
            activatedNow = true;
        }

        boolean changed = receiptWasNew;
        if (changed) {
            PlayerAttributesDataManager.updatePlayerAttributesData(player, data);
        }
        if (activatedNow) {
            // 全局分母改变后，立即刷新在线玩家的 HUD；离线玩家会在登录时收到同样的上限。
            for (ServerPlayer onlinePlayer : player.server.getPlayerList().getPlayers()) {
                PlayerAttributesData onlineData =
                        PlayerAttributesDataManager.findStoredPlayerAttributesData(onlinePlayer.getUUID());
                if (onlineData != null && AuthSessionGuard.isAuthenticated(onlinePlayer)) {
                    syncInfectionData(onlinePlayer, onlineData);
                }
            }
        } else {
            syncInfectionData(player, data);
        }
        return true;
    }

    public static void setInfectionDataClient(Player player, float currentInfection, boolean infected) {
        setInfectionDataClient(
                player, currentInfection, infected, infected ? 1 : 0,
                (int) PRE_MASK_INFECTION_MAX, false);
    }

    public static void setInfectionDataClient(
            Player player, float currentInfection, boolean infected, int infectionLevel) {
        int fallbackMaximum = infectionLevel == PlayerAttributesData.INFECTION_LEVEL_TWO
                ? (int) POST_MASK_INFECTION_MAX
                : (int) PRE_MASK_INFECTION_MAX;
        setInfectionDataClient(player, currentInfection, infected, infectionLevel, fallbackMaximum, false);
    }

    public static void setInfectionDataClient(
            Player player, float currentInfection, boolean infected,
            int infectionLevel, int infectionMaximum) {
        setInfectionDataClient(player, currentInfection, infected, infectionLevel, infectionMaximum, false);
    }

    public static void setInfectionDataClient(
            Player player, float currentInfection, boolean infected,
            int infectionLevel, int infectionMaximum, boolean relapsing) {
        if (player == null || !player.level().isClientSide()) {
            return;
        }
        PlayerAttributesData data = PlayerAttributesClientCache.getOrCreate(player.getUUID());
        data.setCurrentInfection(currentInfection);
        data.setInfectionLevel(infectionLevel);
        if (infected && infectionLevel == PlayerAttributesData.INFECTION_LEVEL_NONE) {
            data.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_ONE);
        }
        PlayerAttributesClientCache.put(player.getUUID(), data);
        PlayerAttributesClientCache.setInfectionLevel(
                player.getUUID(), data.getInfectionLevel());
        PlayerAttributesClientCache.setInfectionMaximum(
                player.getUUID(), normalizeInfectionMaximum(infectionMaximum));
        // 传播复发是稳定感染者的临时状态，必须单独下发，客户端才能算出正确身份。
        PlayerAttributesClientCache.setRelapsing(player.getUUID(), relapsing);
    }

    public static float getCurrentInfectionClient(Player player) {
        if (player == null || !player.level().isClientSide()) {
            return 0.0F;
        }
        PlayerAttributesData data = PlayerAttributesClientCache.get(player.getUUID());
        return data != null ? data.getCurrentInfection() : 0.0F;
    }

    public static int getInfectionMaximumClient(Player player) {
        if (player == null || !player.level().isClientSide()) {
            return (int) PRE_MASK_INFECTION_MAX;
        }
        return normalizeInfectionMaximum(
                PlayerAttributesClientCache.getInfectionMaximum(player.getUUID()));
    }

    /** 身份同步的唯一出口：复发窗口必须一起下发，否则客户端算不出「传播复发」。 */
    private static void syncInfectionData(ServerPlayer player, PlayerAttributesData data) {
        InfectionTreatmentService.syncIdentity(player, data);
    }

    /**
     * 感染值过高时的负面状态：施加模组自有的「感染」。
     *
     * <p>它内部同时施加移速 -15% 与攻击力 -4（与原版缓慢 I + 虚弱 I 完全等价），
     * 但 HUD 上显示为「感染」，玩家能立刻知道惩罚来自感染而不是某个药水。</p>
     *
     * <p>稳定感染者不再承受这个惩罚：突变已经稳定意味着身体适应了异常因子
     * （ADR 0016「稳定感染者适应污染」）。这是里程碑 1 里"污染适应"的 v1 落点；
     * 等到污染区域实装后，环境伤害减免也应挂在同一处身份判定上。</p>
     */
    private static void applyInfectionDebuff(ServerPlayer player, PlayerAttributesData data) {
        if (data != null && data.getInfectionIdentity() == InfectionIdentity.STABLE) {
            return;
        }
        player.addEffect(new MobEffectInstance(
                DreamingFishCore_Effects.INFECTION,
                INFECTION_EFFECT_DURATION_TICKS, 0, false, true));
    }

    private static void sendInfectionStateMessage(ServerPlayer player, PlayerAttributesData data) {
        String text = switch (data.getInfectionIdentity()) {
            case UNSTABLE -> "§4§l你已经感染：不稳定感染者。尽快接受早期逆转治疗。";
            case STABLE -> "§4§l突变已经稳定：你现在是稳定感染者。";
            case RELAPSE -> "§4§l你的身体重新释放异常因子：传播复发。";
            case SURVIVOR -> "§a你已经恢复为幸存者。";
        };
        player.displayClientMessage(Component.literal(text), true);
    }

    private static float infectionRatio(float currentInfection, float infectionMaximum) {
        if (!(infectionMaximum > 0.0F)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, currentInfection / infectionMaximum));
    }

    private static int normalizeInfectionMaximum(int infectionMaximum) {
        return infectionMaximum >= POST_MASK_INFECTION_MAX
                ? (int) POST_MASK_INFECTION_MAX
                : (int) PRE_MASK_INFECTION_MAX;
    }
}
