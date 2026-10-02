package com.hhy.dreamingfishcore.gameplay.clue_system.entry;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.clue_system.ClueEntryDispatcher;
import com.hhy.dreamingfishcore.gameplay.clue_system.ClueSourceType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * 方块类发放入口：挖掉方块、打开容器（里程碑 2 六种入口里的两种）。
 *
 * <p>这里只判断"发生了什么事"并把方块 ID 报给 {@link ClueEntryDispatcher}，
 * 具体哪条线索挂在这个方块上由 {@code clue_secrets.json} 的
 * {@code block= / container=} 声明决定。</p>
 *
 * <p>不用 {@code PlayerContainerEvent.Open} 是因为那边拿到的是
 * {@code AbstractContainerMenu}，反查方块坐标比直接看方块实体更绕；
 * 这里用"右键的方块本身是容器"作为判据，语义够用。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class ClueEntryEvents {

    private ClueEntryEvents() {
    }

    /** 挖掉方块：声明了 {@code block=<方块 ID>} 的线索在这里发放。 */
    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ClueEntryDispatcher.fire(player, ClueSourceType.BLOCK, blockIdOf(event.getState()));
    }

    /** 打开容器：声明了 {@code container=<方块 ID>} 的线索在这里发放。 */
    @SubscribeEvent
    public static void onContainerOpened(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        if (!(blockEntity instanceof Container)) {
            return;
        }
        ClueEntryDispatcher.fire(player, ClueSourceType.CONTAINER,
                blockIdOf(player.level().getBlockState(pos)));
    }

    private static String blockIdOf(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }
}
