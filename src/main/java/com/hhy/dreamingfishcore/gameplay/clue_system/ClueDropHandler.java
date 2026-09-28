package com.hhy.dreamingfishcore.gameplay.clue_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.storybook_system.FragmentData;
import com.hhy.dreamingfishcore.gameplay.storybook_system.StoryBookData;
import com.hhy.dreamingfishcore.gameplay.storybook_system.StoryBookDataManager;
import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieEntity;
import com.hhy.dreamingfishcore.item.items.Item_FragmentPage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.List;

/**
 * 随机丧尸线索掉落。
 *
 * <p>规则与文案见剧情仓库 {@code docs/story/07-丧尸线索掉落文案.md}：普通丧尸按较低概率、
 * 攻城丧尸按较高概率掉落一张尚未收录的残页；玩家已收录的线索不再重复掉落，
 * 幸存者与感染者的掉落规则完全一致。</p>
 *
 * <p>掉落概率与开关由 {@code config/dreamingfishcore/clue_drop.json} 控制，默认值来自
 * 剧情文档的建议值（两种丧尸各 0.01%），服主可自行调整或整体关闭。</p>
 *
 * <p>掉落判定全部在服务端完成，客户端无法请求或指定线索编号。</p>
 *
 * <p>当前未实装的部分（保持与文案文档一致，等后续剧情事件接入）：
 * 关键线索 {@code 01 / 04 / 08 / 11} 的剧情保底发放。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class ClueDropHandler {

    private ClueDropHandler() {
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity killed = event.getEntity();
        if (killed.level().isClientSide()) {
            return;
        }
        if (!(killed.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        double dropPercent = dropPercentFor(killed);
        if (dropPercent <= 0.0D) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }

        // 线索池以配置文件为准，服主可以通过 config/dreamingfishcore/data/fragment_data.json 增删内容。
        List<FragmentData> pool = List.copyOf(StoryBookDataManager.getAllFragments().values());
        if (pool.isEmpty()) {
            return;
        }

        StoryBookData storyBook;
        try {
            storyBook = StoryBookDataManager.getPlayerStoryBook(killer);
        } catch (IllegalStateException notLoaded) {
            // 随记本数据尚未随世界加载时静默跳过，不能因为掉落把异常抛到击杀流程里。
            return;
        }

        RandomSource random = serverLevel.getRandom();
        if (!ClueDropSelector.rollDrop(dropPercent, random)) {
            return;
        }

        Integer fragmentId = ClueDropSelector.pick(
                ClueDropSelector.candidates(pool, storyBook.getUnlockedFragmentIds()),
                random);
        if (fragmentId == null) {
            // 该玩家已经把当前线索池收录完，这次击杀不再掉落残页。
            return;
        }

        ItemStack fragmentPage = Item_FragmentPage.createFragmentPage(fragmentId);
        event.getDrops().add(new ItemEntity(
                serverLevel,
                killed.getX(),
                killed.getY(),
                killed.getZ(),
                fragmentPage));
    }

    /** 只有丧尸系会掉落线索；其他生物返回 0，表示不参与本规则。 */
    private static double dropPercentFor(LivingEntity entity) {
        ClueDropConfig config = ClueDropConfig.current();
        if (entity instanceof SiegeZombieEntity) {
            return config.getSiegeZombieDropPercent();
        }
        EntityType<?> type = entity.getType();
        if (type == EntityType.ZOMBIE || type == EntityType.HUSK) {
            return config.getNormalZombieDropPercent();
        }
        return 0.0D;
    }
}
