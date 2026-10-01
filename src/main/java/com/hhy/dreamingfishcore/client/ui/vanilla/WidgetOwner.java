package com.hhy.dreamingfishcore.client.ui.vanilla;

import com.hhy.dreamingfishcore.mixin.ui.ButtonAccessor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 找出往原版界面里加控件的模组。
 *
 * <p>模组可能自定义按钮类，也可能直接用原版 {@link Button} 加一个回调：先看控件类所在的模块，
 * 原版类再看按钮回调（通常是模组里的 lambda）所在的模块，最后按模块名对应到模组文件。</p>
 */
final class WidgetOwner {
    private static final Map<Class<?>, Optional<String>> CACHE = new ConcurrentHashMap<>();

    private WidgetOwner() {
    }

    /** 控件所属模组的显示名称；无法判断（原版控件、类路径上的类）时返回 null。 */
    static String modName(AbstractWidget widget) {
        String name = modOf(widget.getClass());
        if (name == null && widget instanceof Button button) {
            Button.OnPress press = ((ButtonAccessor) button).dreamingFishCore$getOnPress();
            if (press != null) {
                name = modOf(press.getClass());
            }
        }
        return name;
    }

    private static String modOf(Class<?> type) {
        return CACHE.computeIfAbsent(type, WidgetOwner::lookup).orElse(null);
    }

    private static Optional<String> lookup(Class<?> type) {
        Module module = type.getModule();
        if (!module.isNamed() || "minecraft".equals(module.getName())) {
            return Optional.empty();
        }
        String moduleName = module.getName();
        for (IModFileInfo file : ModList.get().getModFiles()) {
            if (moduleName.equals(file.moduleName()) && !file.getMods().isEmpty()) {
                return Optional.of(file.getMods().get(0).getDisplayName());
            }
        }
        return Optional.empty();
    }
}
