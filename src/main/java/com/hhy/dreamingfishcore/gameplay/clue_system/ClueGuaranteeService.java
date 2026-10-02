package com.hhy.dreamingfishcore.gameplay.clue_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.item.items.Item_FragmentPage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
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

    private ClueGuaranteeService() {
    }

    /**
     * 保底通道覆盖的线索稳定 ID。
     *
     * <p>对应三个调用点：医院复查与登录补发（01）、疗程终检与登录补发（04）、
     * 感染者标准重建（11）。{@link #CLUE_OUTER_RELAY} 的编号虽然登记在类里，
     * 但外缘事件尚未接入，因此不算已覆盖；刷怪箱剿灭按实例配置发放，也不在这里。</p>
     *
     * <p>线索可达性审计（{@code /dreamingfish clue audit}）靠它区分"有确定入口"与"只能靠掉落"。</p>
     */
    public static List<String> guaranteedClueIds() {
        List<String> ids = new ArrayList<>();
        for (int legacyId : List.of(CLUE_OBSERVATION_LOG, CLUE_RECOVERED_VOICE, CLUE_RESPAWN_ANOMALY)) {
            String clueId = ClueCatalog.idForLegacy(legacyId);
            if (clueId != null) {
                ids.add(clueId);
            }
        }
        return ids;
    }

    /**
     * 向玩家发放一条保底线索。
     *
     * <p>里程碑 2 起改为委派 {@link ClueGrantService}：发放的动作统一在那边，
     * 这里保留旧整数编号签名给既有的剧情调用点用（编号会映射到稳定 ID）。
     * 存在性判定也交给线索目录，不再查旧的整数内容缓存——两份内容并存时
     * 那种"旧文件里没有就直接跳过"的静默失败就是收尾要解决的问题。</p>
     *
     * @return 真的发放了才返回 true；已发现、已持有、编号不存在或数据未加载时返回 false
     */
    public static boolean grant(ServerPlayer player, int legacyId) {
        if (player == null || player.level().isClientSide()) {
            return false;
        }

        String clueId = ClueCatalog.idForLegacy(legacyId);
        if (clueId == null) {
            // 服主可能把这条内容删了，或者旧编号没登记；不要因此打断剧情事件本身。
            DreamingFishCore.LOGGER.warn("保底线索 {} 没有对应的稳定 ID，跳过发放", legacyId);
            return false;
        }

        // 背包里已经有同一条的未使用残页时不再重复发（避免剧情重入刷物品）。
        if (isCarrying(player, clueId)) {
            return false;
        }

        ClueGrantService.Outcome outcome = ClueGrantService.grant(player, clueId);
        return outcome == ClueGrantService.Outcome.GRANTED;
    }

    /** 判断是否可以发放；抽成纯函数方便单测。 */
    static boolean shouldGrant(boolean fragmentExists, boolean alreadyOwned, boolean alreadyCarrying) {
        return fragmentExists && !alreadyOwned && !alreadyCarrying;
    }

    /** 背包（含副手）里是否已经有一张同一条的未使用残页。 */
    private static boolean isCarrying(ServerPlayer player, String clueId) {
        return carriesFragmentPage(player.getInventory().items, clueId)
                || carriesFragmentPage(player.getInventory().offhand, clueId);
    }

    private static boolean carriesFragmentPage(List<ItemStack> stacks, String clueId) {
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String bound = Item_FragmentPage.getClueId(stack);
            if (bound != null && bound.equals(clueId)) {
                return true;
            }
        }
        return false;
    }
}
