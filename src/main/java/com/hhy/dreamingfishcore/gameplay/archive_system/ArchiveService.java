package com.hhy.dreamingfishcore.gameplay.archive_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.block.DreamingFishCore_Blocks;
import com.hhy.dreamingfishcore.gameplay.blueprint_system.BlueprintConfig;
import com.hhy.dreamingfishcore.gameplay.blueprint_system.PlayerBlueprintData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/**
 * 资料库的服务端逻辑：存入与学习。
 *
 * <p><b>「配方」在这个模组里就是物品 ID</b>——{@code PlayerBlueprintData} 记的是物品 ID，
 * 合成门禁（{@code CraftingMenuMixin}）也按合成结果判定。所以：</p>
 * <ul>
 *   <li><b>存入</b> = 把玩家 {@link PlayerBlueprintData#getLearnedBlueprintItems} 的结果
 *       （已学会、<b>且确实需要蓝图</b>的那部分，免蓝图的物品本来就能合成、存了没意义）并入方块；</li>
 *   <li><b>学习</b> = 对库里玩家还没学会的 ID 调 {@link PlayerBlueprintData#unlockItem}，
 *       <b>集合不清空</b>——这正是需求里的「不消耗」，一座库可以被无数人反复学。</li>
 * </ul>
 *
 * <p>服务端权威：能不能存、学什么、学了几条全部在这里判定，客户端不做任何判断。</p>
 */
public final class ArchiveService {

    /** 交互距离上限（方块中心到玩家），与原版研究桌保持一致的手感。 */
    private static final double MAX_INTERACTION_DISTANCE_SQR = 8.0D * 8.0D;

    private ArchiveService() {
    }

    // ==================== 存入（右键） ====================

    /** 右键资料库：把自己已解锁、且需要蓝图的配方一次性并入这个库。 */
    public static void onDeposit(ServerPlayer player, BlockPos pos) {
        if (!isStillLibrary(player, pos)) {
            return;
        }
        Set<String> learned = PlayerBlueprintData.getLearnedBlueprintItems(player);
        if (learned.isEmpty()) {
            // 蓝图系统关掉时 isExemptFromBlueprint 一律放行，这里必定为空——顺带把原因说清楚。
            if (!BlueprintConfig.current().isEnabled()) {
                message(player, "§e蓝图系统未启用：所有配方本来就能直接合成，资料库暂时用不上");
            } else {
                message(player, "§e你还没有可存入的配方：先至少学会一个需要蓝图的配方");
            }
            return;
        }

        String dimension = dimensionOf(player);
        List<String> before = ArchiveRegistry.recipesAt(dimension, pos);
        if (ArchiveRules.exceedsCapacity(before, learned)) {
            message(player, "§c这个资料库已经存满了（上限 "
                    + ArchiveRules.MAX_RECIPES_PER_LIBRARY + " 条）");
            return;
        }

        int added = ArchiveRegistry.mergeRecipes(dimension, pos, learned);
        int total = ArchiveRegistry.countAt(dimension, pos);
        if (added == 0) {
            message(player, "§7这个资料库已经有你全部 " + learned.size() + " 条配方了（库中 "
                    + total + " 条）");
            return;
        }
        DreamingFishCore.LOGGER.info("玩家 {} 向资料库 {} 存入 {} 条配方（库中现有 {} 条）",
                player.getScoreboardName(), pos, added, total);
        message(player, "§a已存入 " + added + " 条新配方（你共持有 " + learned.size()
                + " 条），库中现有 " + total + " 条");
    }

    // ==================== 学习（左键） ====================

    /**
     * 左键资料库：学习库里自己还没学会的全部配方。库内容<b>不消耗</b>。
     *
     * @return 是否由本方法接管了这次左键（true = 调用方应取消原版挖掘）
     */
    public static boolean onLearn(ServerPlayer player, BlockPos pos) {
        if (!isStillLibrary(player, pos)) {
            return false;
        }
        String dimension = dimensionOf(player);
        List<String> stored = ArchiveRegistry.recipesAt(dimension, pos);

        if (stored.isEmpty()) {
            message(player, "§e这个资料库还是空的：先让持有配方的人来存一次"
                    + "（潜行 + 左键可以直接把它挖掉）");
            return true;
        }

        List<String> todo = ArchiveRules.unlearned(
                stored, PlayerBlueprintData.getLearnedBlueprintItems(player));
        if (todo.isEmpty()) {
            message(player, "§7这个资料库里的 " + stored.size() + " 条配方你都已经学会了");
            return true;
        }

        for (String itemId : todo) {
            PlayerBlueprintData.unlockItem(player, itemId);
        }
        DreamingFishCore.LOGGER.info("玩家 {} 从资料库 {} 学会了 {} 条配方：{}",
                player.getScoreboardName(), pos, todo.size(), todo);
        message(player, "§a从资料库学会了 " + todo.size() + " 条配方（库中另有 "
                + (stored.size() - todo.size()) + " 条你已经会了）；资料库内容不会消耗");
        return true;
    }

    // ==================== 共用 ====================

    /** 方块还在原位、还是资料库、且玩家在交互距离内。 */
    static boolean isStillLibrary(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!level.getBlockState(pos).is(DreamingFishCore_Blocks.ARCHIVE.get())) {
            return false;
        }
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_INTERACTION_DISTANCE_SQR;
    }

    private static String dimensionOf(ServerPlayer player) {
        return player.level().dimension().location().toString();
    }

    private static void message(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text));
    }
}
