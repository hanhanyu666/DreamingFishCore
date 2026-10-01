package com.hhy.dreamingfishcore.client.ui.vanilla;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelSummary;

/** 由列表条目 Mixin 实现，让新界面读取原版条目的私有数据。 */
public final class SelectionEntryAccess {
    private SelectionEntryAccess() {
    }

    public interface World {
        LevelSummary dreamingFishCore$summary();

        ResourceLocation dreamingFishCore$icon();
    }

    public interface Server {
        ResourceLocation dreamingFishCore$icon();
    }
}
