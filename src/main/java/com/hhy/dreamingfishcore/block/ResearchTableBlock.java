package com.hhy.dreamingfishcore.block;

import com.hhy.dreamingfishcore.gameplay.research_system.ResearchService;
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
 * 研究桌：花经验一次性研究出一批配方。
 *
 * <p>方块本身**没有状态、也不需要方块实体**——所有进度都在玩家身上（学到的配方记在
 * {@code PlayerBlueprintData}），研究桌只是个交互入口。这与刷怪箱刻意不同：刷怪箱要保存
 * 配置与运行状态，所以那个必须落到存档里；研究桌除了"被点一下"之外没有可保存的东西。</p>
 */
public class ResearchTableBlock extends Block {

    public ResearchTableBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.5F, 3.0F)
                .sound(SoundType.WOOD);
    }

    @Override
    protected MapCodec<? extends ResearchTableBlock> codec() {
        return simpleCodec(ResearchTableBlock::new);
    }

    /**
     * 空手右键。
     *
     * <p>客户端返回 SUCCESS 只是为了让手臂挥动动画正常，真正的判定全在服务端。</p>
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ResearchService.onInteract(serverPlayer, pos);
        return InteractionResult.SUCCESS;
    }

    /**
     * 手里拿着东西右键。
     *
     * <p>和刷怪箱同一个理由：原版在"潜行 + 手持方块"时会跳过方块交互去放方块，
     * 两个入口都接上，才不会出现"拿着方块就点不开研究桌"。</p>
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return ItemInteractionResult.SUCCESS;
        }
        ResearchService.onInteract(serverPlayer, pos);
        return ItemInteractionResult.SUCCESS;
    }

    /** 供命令与提示使用的方块说明。 */
    public static String description() {
        return "研究桌：消耗经验，一次性研究出一批原版配方。";
    }
}
