package com.hhy.dreamingfishcore.mixin.ui;

import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取按钮的点击回调，用来判断其他模组加到标题界面上的按钮属于哪个模组。 */
@Mixin(Button.class)
public interface ButtonAccessor {
    @Accessor("onPress")
    Button.OnPress dreamingFishCore$getOnPress();
}
