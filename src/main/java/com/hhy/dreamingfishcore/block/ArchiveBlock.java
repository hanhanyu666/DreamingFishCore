package com.hhy.dreamingfishcore.block;

import com.hhy.dreamingfishcore.gameplay.archive_system.ArchiveService;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 资料库：把玩家已解锁的配方存起来，其他人左键就能免费学会（库内容不消耗）。
 *
 * <p>右键 = 存入（把「自己已解锁且需要蓝图」的配方一次性并入）；左键 = 学习，走
 * {@code ArchiveEventHandler} 的 LeftClickBlock，不在这里。潜行 + 左键是原版挖掘。</p>
 *
 * <p>方块本身<b>没有状态、也不需要方块实体</b>——内容全在 {@code ArchiveRegistry}
 * （世界存档 JSON）。理由与刷怪箱一致，但方向相反：刷怪箱是「服务端要一次遍历」，
 * 这里是「一个方块只被主动访问，没必要为它引入方块实体与同步数据」。</p>
 */
public class ArchiveBlock extends Block {

    public ArchiveBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.5F, 3.0F)
                .sound(SoundType.WOOD);
    }

    @Override
    protected MapCodec<? extends ArchiveBlock> codec() {
        return simpleCodec(ArchiveBlock::new);
    }

    /** 空手右键 = 存入。客户端返回 SUCCESS 只为挥臂动画，判定全在服务端。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ArchiveService.onDeposit(serverPlayer, pos);
        return InteractionResult.SUCCESS;
    }

    /**
     * 手里拿着东西右键 = 也是存入。
     *
     * <p>和刷怪箱/研究桌同一个理由：原版在「潜行 + 手持方块」时会跳过方块交互去放方块，
     * 两个入口都接上才不会出现「拿着方块就存不进去」。</p>
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return ItemInteractionResult.SUCCESS;
        }
        ArchiveService.onDeposit(serverPlayer, pos);
        return ItemInteractionResult.SUCCESS;
    }

    /** 供命令与提示使用的方块说明。 */
    public static String description() {
        return "资料库：右键存入自己已解锁的配方，左键学习库内全部配方（不消耗）。";
    }
}
