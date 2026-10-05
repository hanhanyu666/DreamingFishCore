package com.hhy.dreamingfishcore.block;

import com.hhy.dreamingfishcore.item.DreamingFishCore_Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;

/**
 * 药草作物：模组药物的种植来源。
 *
 * <p>直接继承原版 {@link CropBlock}，因此天然具备原版作物的全部行为：
 * 骨粉催熟、踩踏破坏、只有成熟（age=7）才掉落果实、种子由 {@link #getBaseSeedId()} 决定。</p>
 *
 * <p>贴图仍是原版那套 8 个成长阶段（age 0~7），但只用 4 张图：
 * 方块状态文件把 age 0/1、2/3、4/5、6/7 分别映射到 stage0~stage3，
 * 这样既没有缺失模型，也只要画 4 张图。</p>
 */
public class HerbCropBlock extends CropBlock {

    public HerbCropBlock(Properties properties) {
        super(properties);
    }

    /** 种下去的是药草种子，破坏未成熟作物也掉它。 */
    @Override
    protected ItemLike getBaseSeedId() {
        return DreamingFishCore_Items.HERB_SEEDS.get();
    }
}
