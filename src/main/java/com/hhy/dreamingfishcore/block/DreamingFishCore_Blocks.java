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

    /** 研究桌：花经验一次性研究出一批原版配方（蓝图的另一条获取途径）。 */
    public static final DeferredHolder<Block, ResearchTableBlock> RESEARCH_TABLE =
            BLOCKS.register("research_table",
                    () -> new ResearchTableBlock(ResearchTableBlock.defaultProperties()));

    /**
     * 资料库：右键存入自己已解锁的配方，左键学习库内全部配方（不消耗）。
     *
     * <p>内容不在方块里，而在 {@code ArchiveRegistry}（世界存档 JSON）——本模组不给方块用
     * 方块实体，理由见那个类。</p>
     */
    public static final DeferredHolder<Block, ArchiveBlock> ARCHIVE =
            BLOCKS.register("archive",
                    () -> new ArchiveBlock(ArchiveBlock.defaultProperties()));

    /**
     * 药草作物：模组药物的种植来源。
     *
     * <p>属性照抄原版作物：无碰撞、随机刻生长、瞬间破坏、作物音效、被活塞推就掉。</p>
     */
    public static final DeferredHolder<Block, HerbCropBlock> HERB_CROP =
            BLOCKS.register("herb_crop",
                    () -> new HerbCropBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties
                            .of()
                            .noCollission()
                            .randomTicks()
                            .instabreak()
                            .sound(net.minecraft.world.level.block.SoundType.CROP)
                            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

    private DreamingFishCore_Blocks() {
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
