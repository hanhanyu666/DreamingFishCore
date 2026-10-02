package com.hhy.dreamingfishcore.gameplay.research_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.block.DreamingFishCore_Blocks;
import com.hhy.dreamingfishcore.gameplay.blueprint_system.BlueprintConfig;
import com.hhy.dreamingfishcore.gameplay.blueprint_system.PlayerBlueprintData;
import com.hhy.dreamingfishcore.gameplay.research_system.network.Packet_ResearchTableOpen;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 研究桌的服务端逻辑：掷课题、收经验、解锁配方。
 *
 * <p><b>服务端权威</b>：客户端只负责"我点了确认"，能不能研究、研究出什么、扣多少经验都由这里决定。</p>
 *
 * <p><b>课题是固定的</b>：打开界面时掷一次并缓存，玩家关掉再打开会拿到同一批。
 * 不这么做的话，玩家可以反复开关界面来刷随机结果，等于把"花经验换随机"变成"花经验挑想要的"。
 * 缓存会在下面几种情况下自动失效并重掷：这批课题已经全部被学会、或不再出现在候选池里。</p>
 *
 * <p>候选来自蓝图抽取池（已经过免蓝图 / 白名单 / 黑名单过滤），再按配置的命名空间与
 * "是否跳过已学"筛一遍——研究桌就是蓝图的另一条获取途径，不该绕过蓝图那套规则。</p>
 */
public final class ResearchService {

    /** 玩家 UUID → 本次固定的课题。确认或失效后清除。 */
    private static final Map<UUID, List<String>> PENDING_OFFERS = new ConcurrentHashMap<>();

    /** 交互距离上限（方块中心到玩家），比原版的 4.5 格放宽一点，和刷怪箱的手感一致。 */
    private static final double MAX_INTERACTION_DISTANCE_SQR = 8.0D * 8.0D;

    private ResearchService() {
    }

    /** 玩家右键研究桌 / 客户端请求刷新：校验方块仍在那儿，然后把当前状态推给客户端。 */
    public static void onInteract(ServerPlayer player, BlockPos pos) {
        if (!isStillResearchTable(player, pos)) {
            return;
        }
        List<String> offer = offerFor(player);
        sendState(player, pos, offer, List.of(), statusMessage(player, offer));
    }

    /** 客户端点了"开始研究"。 */
    public static void handleConfirm(ServerPlayer player, BlockPos pos) {
        if (!isStillResearchTable(player, pos)) {
            return;
        }
        ResearchTableConfig config = ResearchTableConfig.current();
        if (!config.isEnabled()) {
            sendState(player, pos, List.of(), List.of(), "§c研究桌未启用");
            return;
        }
        if (!BlueprintConfig.current().isEnabled()) {
            sendState(player, pos, List.of(), List.of(),
                    "§c蓝图系统未启用，研究桌暂时用不上（所有配方本来就能合成）");
            return;
        }

        UUID uuid = player.getUUID();
        List<String> offer = PENDING_OFFERS.get(uuid);
        if (offer == null || offer.isEmpty()) {
            sendState(player, pos, offerFor(player), List.of(),
                    "§e没有正在进行的课题，请重新打开研究桌");
            return;
        }

        List<String> candidates = candidatesOf(player);
        List<String> usable = new ArrayList<>();
        for (String itemId : offer) {
            if (candidates.contains(itemId) && !usable.contains(itemId)) {
                usable.add(itemId);
            }
        }
        if (usable.isEmpty()) {
            // 这批课题在玩家走开期间被学掉了（或者内容改动后失效了）：换一批，不收经验。
            PENDING_OFFERS.remove(uuid);
            sendState(player, pos, offerFor(player), List.of(),
                    "§e这批课题已经全部学过了，已重新抽一批");
            return;
        }

        int cost = config.getCostExperiencePoints();
        int have = experiencePoints(player);
        if (have < cost) {
            sendState(player, pos, offer, List.of(),
                    "§c经验不足：需要 " + cost + " 点，你当前有 " + have + " 点");
            return;
        }

        player.giveExperiencePoints(-cost);
        for (String itemId : usable) {
            PlayerBlueprintData.unlockItem(player, itemId);
        }
        PENDING_OFFERS.remove(uuid);

        DreamingFishCore.LOGGER.info("玩家 {} 在研究桌学会 {} 个配方（消耗 {} 点经验）：{}",
                player.getScoreboardName(), usable.size(), cost, usable);

        sendState(player, pos, offerFor(player), usable,
                "§a研究完成：消耗 " + cost + " 点经验，学会 " + usable.size() + " 个配方");
    }

    /** 取（或首次掷出）该玩家本次的课题。池子里没有可研究的物品时返回空列表。 */
    public static List<String> offerFor(ServerPlayer player) {
        UUID uuid = player.getUUID();
        List<String> candidates = candidatesOf(player);
        if (candidates.isEmpty()) {
            PENDING_OFFERS.remove(uuid);
            return List.of();
        }

        List<String> cached = PENDING_OFFERS.get(uuid);
        if (cached != null && !isStillUseful(cached, candidates)) {
            cached = null;
        }
        if (cached != null) {
            return cached;
        }

        ResearchTableConfig config = ResearchTableConfig.current();
        int size = ResearchMath.rollSize(
                config.getMinRecipes(), config.getMaxRecipes(), candidates.size(), player.getRandom());
        List<String> rolled = ResearchMath.pickDistinct(candidates, size, player.getRandom());
        if (rolled.isEmpty()) {
            PENDING_OFFERS.remove(uuid);
            return List.of();
        }
        PENDING_OFFERS.put(uuid, rolled);
        return rolled;
    }

    /** 玩家登出时清掉缓存，避免缓存里留着已经离线玩家的数据。 */
    public static void clearPending(UUID playerUuid) {
        if (playerUuid != null) {
            PENDING_OFFERS.remove(playerUuid);
        }
    }

    /** 当前能研究出来的物品（受配置的命名空间与"跳过已学"约束）。 */
    static List<String> candidatesOf(ServerPlayer player) {
        if (!ResearchTableConfig.current().isEnabled() || !BlueprintConfig.current().isEnabled()) {
            return List.of();
        }
        ResearchTableConfig config = ResearchTableConfig.current();
        return ResearchMath.candidates(
                PlayerBlueprintData.getBlueprintPool(),
                PlayerBlueprintData.getLearnedBlueprintItems(player),
                config::allowsNamespace,
                config.isSkipLearned());
    }

    /** 玩家当前持有的经验点数。 */
    public static int experiencePoints(ServerPlayer player) {
        return ResearchMath.experiencePointsOf(
                player.experienceLevel, player.experienceProgress, player.getXpNeededForNextLevel());
    }

    private static boolean isStillUseful(List<String> offer, List<String> candidates) {
        for (String itemId : offer) {
            if (candidates.contains(itemId)) {
                return true;
            }
        }
        return false;
    }

    /** 打开界面时给玩家的第一句话：说清"为什么现在点不了"。 */
    private static String statusMessage(ServerPlayer player, List<String> offer) {
        ResearchTableConfig config = ResearchTableConfig.current();
        if (!config.isEnabled()) {
            return "§c研究桌未启用";
        }
        if (!BlueprintConfig.current().isEnabled()) {
            return "§c蓝图系统未启用，研究桌暂时用不上（所有配方本来就能合成）";
        }
        if (offer.isEmpty()) {
            return "§e没有可以研究的配方了（都学会了，或被白名单 / 黑名单挡住了）";
        }
        int cost = config.getCostExperiencePoints();
        int have = experiencePoints(player);
        if (have < cost) {
            return "§c经验不足：需要 " + cost + " 点，你当前有 " + have + " 点";
        }
        return "";
    }

    private static void sendState(ServerPlayer player, BlockPos pos, List<String> offer,
                                  List<String> learned, String message) {
        ResearchTableConfig config = ResearchTableConfig.current();
        int cost = config.getCostExperiencePoints();
        int have = experiencePoints(player);
        boolean available = config.isEnabled()
                && BlueprintConfig.current().isEnabled()
                && !offer.isEmpty()
                && have >= cost;
        DreamingFishCore_NetworkManager.sendToClient(
                new Packet_ResearchTableOpen(pos, offer, cost, have, learned, message, available),
                player);
    }

    private static boolean isStillResearchTable(ServerPlayer player, BlockPos pos) {
        if (pos == null || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!level.getBlockState(pos).is(DreamingFishCore_Blocks.RESEARCH_TABLE.get())) {
            return false;
        }
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_INTERACTION_DISTANCE_SQR;
    }
}
