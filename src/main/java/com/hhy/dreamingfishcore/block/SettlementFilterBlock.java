package com.hhy.dreamingfishcore.block;

import com.hhy.dreamingfishcore.gameplay.organization_system.SettlementFilterRegistry;
import com.hhy.dreamingfishcore.gameplay.organization_system.SettlementFilterService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import com.mojang.serialization.MapCodec;

/**
 * 聚居地过滤装置（ADR 0017）。
 *
 * <p>作用：装置<b>工作</b>时，它覆盖范围内的幸存者不会被不稳定感染者或传播复发者持续感染
 * （只阻断被动接触暴露，不治疗既有感染，也不阻止受伤、污染物与特殊袭击造成的感染）。</p>
 *
 * <p>工作条件由维护周期判定：已绑定组织 + 位于该组织已登记的领地内 + 当日维护费已从组织资金池扣除。
 * 方块本身<b>不存状态</b>：登记表 {@link SettlementFilterRegistry} 是唯一真相，
 * 方块只通过 {@code active} 方块状态把"在不在干活"显示给玩家。</p>
 */
public class SettlementFilterBlock extends Block {

    /** 是否正在工作；只在服务端按维护结果改写，客户端由方块状态同步自动获得。 */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public SettlementFilterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL)
                // 工作时发一点光：玩家远远就能看出它在不在干活。
                .lightLevel(state -> state.getValue(ACTIVE) ? 7 : 0);
    }

    @Override
    protected MapCodec<? extends SettlementFilterBlock> codec() {
        return simpleCodec(SettlementFilterBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    /** 放置时登记进设备表（此时未绑定，不会工作）。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            SettlementFilterRegistry.register(
                    level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
        }
    }

    /** 右键：未绑定则尝试绑定到所在组织；已绑定到自己的组织则解除绑定。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               net.minecraft.world.entity.player.Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            // 客户端直接返回成功，保证手臂挥动动画正常。
            return InteractionResult.SUCCESS;
        }
        return SettlementFilterService.onInteract(serverPlayer, pos)
                ? InteractionResult.SUCCESS
                : InteractionResult.FAIL;
    }

    /**
     * 方块被移除或被替换时摘除登记。
     *
     * <p>登记表是设备状态的唯一真相，所以这条路径必须清干净；即便被 /setblock 之类的手段绕过，
     * 维护周期还会按坐标复核"方块还在不在"，不会留下一直工作的幽灵设备。</p>
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            SettlementFilterRegistry.unregister(
                    level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** 供命令与终端提示使用的说明。 */
    public static String description() {
        return "聚居地过滤装置：工作时阻断范围内的被动接触暴露。";
    }
}
