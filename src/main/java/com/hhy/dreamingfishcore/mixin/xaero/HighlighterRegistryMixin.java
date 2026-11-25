package com.hhy.dreamingfishcore.mixin.xaero;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.integration.xaero.XaeroInstaller;
import com.hhy.dreamingfishcore.integration.xaero.XaeroRegistryCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 捕获 Xaero 的 {@code HighlighterRegistry} 实例。
 *
 * <p>用 {@code targets = "..."} 字符串而不是 import：这样**编译期不需要** Xaero 的类型，
 * 运行期也只在 mixin 真正应用时才解析目标类（没装 Xaero 时整个配置被跳过）。</p>
 *
 * <p>对应配置文件 {@code dreamingfishcore.xaero.mixins.json} 是 {@code "required": false}：
 * 万一某个 Xaero 版本改了类名，只会打一条警告，而不是启动崩溃。</p>
 */
@Mixin(targets = "xaero.map.highlight.HighlighterRegistry")
public abstract class HighlighterRegistryMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dreamingfishcore$captureRegistry(CallbackInfo callbackInfo) {
        XaeroRegistryCapture.capture(this);
        // **必须在这里注册**：Xaero 初始化完会调用 HighlighterRegistry.end() 把列表冻结，
        // 之后任何 register 都会抛 UnsupportedOperationException（服主实测报"啥都没有"就是这个）。
        try {
            XaeroInstaller.install(this);
            DreamingFishCore.LOGGER.info("[map] 已在 Xaero 初始化阶段挂上区域图层");
        } catch (Throwable throwable) {
            DreamingFishCore.LOGGER.warn("[map] 初始化阶段挂载区域图层失败：{}", throwable.toString());
        }
    }
}
