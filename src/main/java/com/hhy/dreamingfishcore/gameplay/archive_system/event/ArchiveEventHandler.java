package com.hhy.dreamingfishcore.gameplay.archive_system.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.block.DreamingFishCore_Blocks;
import com.hhy.dreamingfishcore.gameplay.archive_system.ArchiveService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 资料库的左键交互：学习库里所有自己还没学会的配方。
 *
 * <p><b>为什么用 {@link PlayerInteractEvent.LeftClickBlock} 而不是客户端拦截原版按键</b>
 * （水枪走的是后者）：这个事件服务端可取消，取消后原版就不会破坏方块，
 * 因此**不需要任何客户端代码**，也就没有"客户端与服务端谁说了算"的问题。</p>
 *
 * <p><b>两侧都要取消</b>：服务端取消是「别挖」，客户端取消是「别起挖掘动画」。
 * 只取消服务端的话，玩家会看到方块抖一下再恢复。</p>
 *
 * <p><b>潜行时不拦截</b>：给玩家留一条「我就是要把它挖掉」的路，规则也单一
 * ——潜行 = 原版行为，不潜行 = 学习。</p>
 *
 * <p>只在 {@code START} 那一下动作，避免 {@code CLIENT_HOLD}（客户端每 tick 都会发）把提示刷爆。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class ArchiveEventHandler {

    private ArchiveEventHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        Player player = event.getEntity();
        if (player == null || player.isShiftKeyDown()) {
            return;
        }
        Level level = event.getLevel();
        if (level == null || !level.getBlockState(event.getPos())
                .is(DreamingFishCore_Blocks.ARCHIVE.get())) {
            return;
        }
        // 先取消再判定：即使这次没东西可学（比如库是空的），也保持「不潜行 = 不会挖掉」这条规则单一的语义。
        event.setCanceled(true);
        if (player instanceof ServerPlayer serverPlayer) {
            ArchiveService.onLearn(serverPlayer, event.getPos());
        }
    }
}
