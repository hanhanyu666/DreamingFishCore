package com.hhy.dreamingfishcore.block;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 方块注册器。
 *
 * <p>本模组此前只有物品注册器，方块从这里开始。注册（含 BlockItem）由 {@code DreamingFishCore}
 * 的构造器统一挂到 mod 事件总线上，与物品/效果/实体的做法一致。</p>
 */
public final class DreamingFishCore_Blocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, DreamingFishCore.MODID);

    /** 聚居地过滤装置（ADR 0017）。 */
    public static final DeferredHolder<Block, SettlementFilterBlock> SETTLEMENT_FILTER =
            BLOCKS.register("settlement_filter",
                    () -> new SettlementFilterBlock(SettlementFilterBlock.defaultProperties()));

    /** 刷怪箱：尸潮区域里按批刷怪的可配置设备。 */
    public static final DeferredHolder<Block, SpawnerBlock> SPAWNER =
            BLOCKS.register("spawner",
                    () -> new SpawnerBlock(SpawnerBlock.defaultProperties()));

    private DreamingFishCore_Blocks() {
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
