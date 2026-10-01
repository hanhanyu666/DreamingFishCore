package com.hhy.dreamingfishcore.gameplay.clue_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.storybook_system.FragmentData;
import com.hhy.dreamingfishcore.gameplay.storybook_system.StoryBookData;
import com.hhy.dreamingfishcore.gameplay.storybook_system.StoryBookDataManager;
import com.hhy.dreamingfishcore.item.items.Item_FragmentPage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 关键线索的剧情保底发放。
 *
 * <p>随机掉落的概率很低（默认两种丧尸各 0.01%），关键线索如果只靠掉落，玩家很可能长期
 * 收不齐、证据包断线。所以关键线索改由确定的剧情事件发放，规则与文案见剧情仓库
 * {@code docs/story/07-丧尸线索掉落文案.md} 第二节的「保底」一行。</p>
 *
 * <p>发放形式与随机掉落保持一致：给玩家一张绑定编号的残页物品，由玩家自己右键整理。
 * 这样两条路径的手感相同，读取流程、残页使用次数和成就也都走同一套逻辑。</p>
 *
 * <p>发放是幂等的：线索已收录、或者背包里/地上已经有一张同编号残页时都不会再发，
 * 所以可以安全地在每次登录和每次事件里重复调用。</p>
 *
 * <p>全部判定在服务端完成，客户端无法请求或指定线索编号。</p>
 */
public final class ClueGuaranteeService {

    /** 观察区值班记录：医院第一次正式复查完成后发放。 */
    public static final int CLUE_OBSERVATION_LOG = 1;
    /** 康复者口述摘录：二级感染三次疗程终检完成后发放。 */
    public static final int CLUE_RECOVERED_VOICE = 4;
    /**
     * 外缘中继信号记录：梁朔线种子。
     *
     * <p>本轮没有可挂的剧情事件（梁朔在外缘线尚未展开），编号先登记在这里，
     * 等外缘/救援事件实装后直接接入，不要提前硬塞进开场。</p>
     */
    public static final int CLUE_OUTER_RELAY = 8;
    /** 重生节点异常记录：感染者完成一次标准重建后发放。 */
    public static final int CLUE_RESPAWN_ANOMALY = 11;

    private static final String GRANT_MESSAGE = "§7你获得了一张沾灰的残页，右键可以整理出上面的内容。";

    private ClueGuaranteeService() {
    }

    /**
     * 向玩家发放一条保底线索（一张绑定编号的残页）。
     *
     * @return 真的发放了才返回 true；已收录、已持有、编号不存在或数据未加载时返回 false
     */
    public static boolean grant(ServerPlayer player, int fragmentId) {
        if (player == null || player.level().isClientSide()) {
            return false;
        }

        FragmentData fragment = StoryBookDataManager.getFragment(fragmentId);
        if (fragment == null) {
            // 服主可能从 fragment_data.json 里删掉了这条；不要因此打断剧情事件本身。
            DreamingFishCore.LOGGER.warn("保底线索 {} 不在当前线索池中，跳过发放", fragmentId);
            return false;
        }

        StoryBookData storyBook;
        try {
            storyBook = StoryBookDataManager.getPlayerStoryBook(player);
        } catch (IllegalStateException notLoaded) {
            // 随记本数据尚未随世界加载时静默跳过，不能把异常抛进剧情事件里。
            return false;
        }

        if (!shouldGrant(true, storyBook.hasUnlockedFragment(fragmentId), isCarrying(player, fragmentId))) {
            return false;
        }

        ItemStack page = Item_FragmentPage.createFragmentPage(fragmentId);
        if (!player.addItem(page)) {
            player.drop(page, false);
        }
        player.sendSystemMessage(Component.literal(GRANT_MESSAGE));
        DreamingFishCore.LOGGER.info("向玩家 {} 发放保底线索 {}（{}）",
                player.getScoreboardName(), fragmentId, fragment.getTitle());
        return true;
    }

    /** 判断是否可以发放；抽成纯函数方便单测。 */
    static boolean shouldGrant(boolean fragmentExists, boolean alreadyOwned, boolean alreadyCarrying) {
        return fragmentExists && !alreadyOwned && !alreadyCarrying;
    }

    /** 背包（含副手）里是否已经有一张同编号的未使用残页。 */
    private static boolean isCarrying(ServerPlayer player, int fragmentId) {
        return carriesFragmentPage(player.getInventory().items, fragmentId)
                || carriesFragmentPage(player.getInventory().offhand, fragmentId);
    }

    private static boolean carriesFragmentPage(List<ItemStack> stacks, int fragmentId) {
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            Integer bound = Item_FragmentPage.getFragmentId(stack);
            if (bound != null && bound == fragmentId) {
                return true;
            }
        }
        return false;
    }
}
