package com.hhy.dreamingfishcore.gameplay.clue_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.storybook_system.StoryBookDataManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 线索发放的唯一入口（里程碑 2）。
 *
 * <p>规则（与用户确认的设计一致）：</p>
 * <ul>
 *   <li><b>发放即视为已发现</b>：调用成功的那一刻就把线索写进玩家永久发现记录（ADR 0009），
 *       残页物品只是查看与分享的载体——物品丢了知识还在。</li>
 *   <li><b>幂等</b>：同一条线索对同一玩家只登记一次；已经发现过就不再重复塞物品。</li>
 *   <li><b>兼容旧编号</b>：{@link #grantLegacy} 走 {@link ClueCatalog#idForLegacy}，
 *       旧调用点（保底/掉落/命令/刷怪箱）不用改签名就能接上永久发现记录。</li>
 * </ul>
 *
 * <p>各种"在哪里能拿到"的入口（方块、容器、NPC、区域、广播、事件结果）都调用这里，
 * 入口本身只负责判断触发条件，不自己写玩家数据。</p>
 */
public final class ClueGrantService {

    /** 发放结果。 */
    public enum Outcome {
        /** 本次新发现并发出了残页。 */
        GRANTED,
        /** 早就发现过，什么都没做。 */
        ALREADY_DISCOVERED,
        /** 目录里没有这条线索（内容被删或写错了）。 */
        UNKNOWN_CLUE,
        /** 玩家档案还没加载好（登录早期）。 */
        NOT_READY
    }

    private ClueGrantService() {
    }

    /** 按稳定 ID 发放。 */
    public static Outcome grant(ServerPlayer player, String clueId) {
        return grant(player, clueId, true);
    }

    /**
     * 按稳定 ID 发放。
     *
     * @param givePage 是否同时给一张残页物品（事件结算给物品；某些入口只登记发现）
     */
    public static Outcome grant(ServerPlayer player, String clueId, boolean givePage) {
        if (player == null) {
            return Outcome.NOT_READY;
        }
        ClueDefinition definition = ClueCatalog.byId(clueId);
        if (definition == null) {
            DreamingFishCore.LOGGER.warn("线索不存在，跳过发放：{}", clueId);
            return Outcome.UNKNOWN_CLUE;
        }
        if (StoryBookDataManager.hasDiscoveredClue(player.getUUID(), clueId)) {
            return Outcome.ALREADY_DISCOVERED;
        }
        boolean discovered;
        try {
            discovered = StoryBookDataManager.discoverClueForPlayer(player.getUUID(), clueId);
        } catch (IllegalStateException exception) {
            // 档案尚未加载（登录早期）：不要打断调用方的事件处理。
            DreamingFishCore.LOGGER.warn("玩家档案未就绪，暂不发放线索 {}：{}",
                    clueId, exception.getMessage());
            return Outcome.NOT_READY;
        }
        if (!discovered) {
            return Outcome.ALREADY_DISCOVERED;
        }

        if (givePage) {
            givePageItem(player, definition);
        }
        player.displayClientMessage(Component.literal(
                "§7你获得了一张沾灰的残页，右键可以整理出上面的内容。"), false);
        DreamingFishCore.LOGGER.info("玩家 {} 发现线索「{}」（{}）",
                player.getScoreboardName(), definition.title(), clueId);
        return Outcome.GRANTED;
    }

    /**
     * 按旧整数编号发放。
     *
     * <p>旧编号会先映射成稳定 ID；映射不到说明这条内容已被删除，直接跳过而不是报错。</p>
     */
    public static Outcome grantLegacy(ServerPlayer player, int legacyId) {
        return grantLegacy(player, legacyId, true);
    }

    public static Outcome grantLegacy(ServerPlayer player, int legacyId, boolean givePage) {
        String clueId = ClueCatalog.idForLegacy(legacyId);
        if (clueId == null) {
            DreamingFishCore.LOGGER.warn("旧线索编号没有对应的稳定 ID，跳过发放：{}", legacyId);
            return Outcome.UNKNOWN_CLUE;
        }
        return grant(player, clueId, givePage);
    }

    /** 残页物品：优先用稳定 ID 绑定；这条线索有旧编号时同时写入，兼容尚未升级的客户端/存档。 */
    private static void givePageItem(ServerPlayer player, ClueDefinition definition) {
        net.minecraft.world.item.ItemStack page =
                com.hhy.dreamingfishcore.item.items.Item_FragmentPage.createCluePage(
                        definition.id(), definition.legacyId());
        if (page.isEmpty()) {
            return;
        }
        if (!player.addItem(page)) {
            player.drop(page, false);
        }
    }
}
