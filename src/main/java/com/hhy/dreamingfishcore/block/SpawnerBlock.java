package com.hhy.dreamingfishcore.block;

import com.hhy.dreamingfishcore.gameplay.spawner_system.SpawnerRegistry;
import com.hhy.dreamingfishcore.gameplay.spawner_system.SpawnerService;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 刷怪箱：尸潮区域里的可配置刷怪设备。
 *
 * <p>方块本身只保存两件展示用的状态：{@link #SKIN 外观}与 {@link #ACTIVE 是否正在工作}，
 * 另外用 {@link BlockStateProperties#POWERED 红石信号}给服主一个"为什么它不动"的直观提示。
 * 全部配置与运行状态都在 {@link SpawnerRegistry} 里（理由同聚居地过滤装置：服务端要能一次遍历）。</p>
 *
 * <p>启用条件（由 {@link SpawnerService} 判定）：位于开启尸潮开关的任务地点内，
 * 且检测范围内有生存/冒险模式玩家；勾了红石控制时还必须持续有红石信号。</p>
 */
public class SpawnerBlock extends Block {

    /** 外观皮肤数量；换外观就在 0..{@code SKIN_COUNT - 1} 之间循环。 */
    public static final int SKIN_COUNT = 4;
    public static final IntegerProperty SKIN = IntegerProperty.create("skin", 0, SKIN_COUNT - 1);
    /** 是否正在工作（有合格玩家在场、区域与红石条件都满足）。 */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    /** 是否有红石信号；只在勾了红石控制时有意义，但一直同步给玩家看。 */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public SpawnerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(SKIN, 0)
                .setValue(ACTIVE, false)
                .setValue(POWERED, false));
    }

    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 9.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL)
                // 工作中发一点暗光：远远就能看出这台设备在不在干活。
                .lightLevel(state -> state.getValue(ACTIVE) ? 7 : 0);
    }

    @Override
    protected MapCodec<? extends SpawnerBlock> codec() {
        return simpleCodec(SpawnerBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SKIN, ACTIVE, POWERED);
    }

    /** 放置时登记进刷怪箱表（默认配置，未启用）。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            SpawnerRegistry.register(
                    level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
        }
    }

    /**
     * 右键交互（空手走这里）。
     *
     * <p>只用普通右键：原版在"潜行 + 手持物品"时会跳过方块交互去放手里的方块，
     * 所以这个入口在有物品时不会被调用 —— 见下面的 {@link #useItemOn}。</p>
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               net.minecraft.world.entity.player.Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            // 客户端返回成功，保证手臂挥动动画正常。
            return InteractionResult.SUCCESS;
        }
        return SpawnerService.onInteract(serverPlayer, pos, player.isShiftKeyDown())
                ? InteractionResult.SUCCESS
                : InteractionResult.FAIL;
    }

    /**
     * 右键交互（手里拿着东西时走这里）。
     *
     * <p>两个入口都接到同一个服务：这样"手里拿着方块右键刷怪箱"也会进配置界面，
     * 而不是被原版判成放方块 —— 那样玩家会以为界面打不开。</p>
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos,
                                              net.minecraft.world.entity.player.Player player,
                                              net.minecraft.world.InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return ItemInteractionResult.SUCCESS;
        }
        return SpawnerService.onInteract(serverPlayer, pos, player.isShiftKeyDown())
                ? ItemInteractionResult.SUCCESS
                : ItemInteractionResult.FAIL;
    }

    /** 红石：把"有没有信号"同步到方块状态，服主一眼能看出是不是被红石卡住了。 */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide()) {
            boolean powered = level.hasNeighborSignal(pos);
            if (state.getValue(POWERED) != powered) {
                level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
            }
        }
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    /** 被移除或替换时摘除登记，避免留下刷不到的幽灵配置。 */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            SpawnerRegistry.unregister(
                    level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** 供命令与提示使用的方块说明。 */
    public static String description() {
        return "刷怪箱：在开启尸潮开关的任务地点内，按批刷怪并在剿灭后结算奖励。";
    }
}
