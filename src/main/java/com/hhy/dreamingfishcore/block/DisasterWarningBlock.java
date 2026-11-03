package com.hhy.dreamingfishcore.block;

import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 天灾预警系统：右键查看"今天 + 后 3 日"的天灾日程。
 *
 * <p>这是天灾系统唯一的<b>玩家可见入口</b>：日程本身是服务器事实，玩家要通过这个方块
 * 才能提前三知道要准备什么。</p>
 *
 * <p>刻意先用聊天栏输出而不是自绘 UI：不新增网络包、不动协议版本，改起来也不牵一发动全身。
 * 真要有面板界面，等玩法稳定后再单开一刀（那时候再提协议）。</p>
 */
public class DisasterWarningBlock extends Block {

    public DisasterWarningBlock(Properties properties) {
        super(properties);
    }

    public static Properties defaultProperties() {
        return Properties.of()
                .strength(3.5F)
                .requiresCorrectToolForDrops()
                .sound(net.minecraft.world.level.block.SoundType.METAL);
    }

    /** 空手右键。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        return showSchedule(level, player);
    }

    /** 手持物品右键也照样弹预告（否则拿着东西点它就没反应，玩家会以为方块坏了）。 */
    @Override
    public net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
                                                               Level level, BlockPos pos,
                                                               Player player, InteractionHand hand,
                                                               BlockHitResult hit) {
        // 1.21 里 useItemOn 的返回类型是 ItemInteractionResult（没有 PASS 这个常量），
        // 我们只在自己方块上处理右键，一律回 SUCCESS 即可。
        showSchedule(level, player);
        return net.minecraft.world.ItemInteractionResult.SUCCESS;
    }

    private static InteractionResult showSchedule(Level level, Player player) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;   // 客户端只回一个成功，避免重复触发
        }
        if (!(player instanceof ServerPlayer serverPlayer) || level.getServer() == null) {
            return InteractionResult.PASS;
        }
        for (String line : DisasterService.describe(level.getServer(), DisasterService.PREVIEW_DAYS)) {
            serverPlayer.sendSystemMessage(Component.literal(line));
        }
        serverPlayer.sendSystemMessage(Component.literal("§8（天灾预警系统：只能看到后 "
                + DisasterService.PREVIEW_DAYS + " 日，更远的日程不会提前泄露）"));
        return InteractionResult.SUCCESS;
    }
}
