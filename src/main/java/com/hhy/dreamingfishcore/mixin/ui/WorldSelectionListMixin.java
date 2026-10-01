package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.vanilla.SelectionScreenUi;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldSelectionList.class)
public abstract class WorldSelectionListMixin {

    @Inject(method = "getRowWidth", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$getModernRowWidth(CallbackInfoReturnable<Integer> cir) {
        if (SelectionScreenUi.isActive()) {
            // 两侧留出滚动条的位置，条目铺满列表区域
            AbstractSelectionList<?> list = (AbstractSelectionList<?>) (Object) this;
            cir.setReturnValue(Math.max(120, list.getWidth() - 32));
        }
    }
}
