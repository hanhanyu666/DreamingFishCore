package com.hhy.dreamingfishcore.gameplay.extraction_story_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * 把搜打撤的"成功撤离"接成剧情事实（第三阶段的任务完成入口）。
 *
 * <p>这套剧情系统的分工是：**服务端验证过的事实才能写个人进度**
 * （{@code StoryManager.recordPlayerTaskProgress} 的注释原话），阶段与公告仍由服主推进。
 * 所以这里只做一件事：撤离**由服务端确认成功**之后，把对应任务记成该玩家的个人完成。</p>
 *
 * <p>两条与现有剧情脚本一致的约定：</p>
 * <ul>
 *   <li><b>阶段要对得上</b>：只有当前阶段就是梦外行动时才计入（与开篇期
 *       {@code OpeningStory.onNoticeRead} 先比对阶段 ID 的写法一致）。否则玩家在别的章节
 *       撤离也会被记账，剧情顺序就乱了。</li>
 *   <li><b>幂等</b>：写之前先问 {@code isPlayerFinishedTask}，
 *       重复撤离同一局/多局不会重复记录（底层也会用 changed() 兜一层）。</li>
 * </ul>
 */
public final class ExtractionStoryHooks {

    private ExtractionStoryHooks() {
    }

    /**
     * 一次成功撤离。
     *
     * @param carriedItems 撤出时背包里是否至少带了一件东西（决定"带一件有用的东西回来"）
     */
    public static void onExtracted(ServerPlayer player, boolean carriedItems) {
        if (player == null) {
            return;
        }
        if (!ExtractionEraStory.STAGE_ID.equals(StoryManager.getCurrentStageIdOrDefault())) {
            // 不在本章节：不计入，也不报错（玩家可能在新章节里打对局）
            return;
        }
        complete(player, ExtractionEraStory.TASK_FIRST_EXTRACT_ID);
        if (carriedItems) {
            complete(player, ExtractionEraStory.TASK_BRING_BACK_ID);
        }
    }

    /** 撤出时背包里有没有东西——"带回来"这条任务的判定依据。 */
    public static boolean hasAnyCarriedItem(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void complete(ServerPlayer player, String taskKey) {
        UUID playerId = player.getUUID();
        if (StoryManager.isPlayerFinishedTask(taskKey, playerId)) {
            return;
        }
        boolean recorded = StoryManager.recordPlayerTaskProgress(
                taskKey, player.getGameProfile().getName(), playerId);
        if (recorded) {
            DreamingFishCore.LOGGER.info("[extraction_story] {} 完成剧情任务 {}（成功撤离）",
                    player.getGameProfile().getName(), taskKey);
        }
    }
}
