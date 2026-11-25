package com.hhy.dreamingfishcore.integration.xaero;

/**
 * 捕获 Xaero 的 {@code HighlighterRegistry} 实例。
 *
 * <p>**这个类刻意不出现任何 Xaero 类型**（只用 {@link Object}）：
 * 没装 Xaero 时它也能安全加载，从而让"是否存在 Xaero"的判断永远走在接触 Xaero 类之前。</p>
 *
 * <p>实例由客户端 mixin（{@code HighlighterRegistryMixin}）在构造器末尾塞进来。</p>
 */
public final class XaeroRegistryCapture {

    private static volatile Object registry;
    /** 已经挂上我们高亮器的那个 registry 实例（Xaero 可能每次进世界新建一个）。 */
    private static volatile Object installedRegistry;
    private static volatile boolean failed;

    private XaeroRegistryCapture() {
    }

    /** 由 mixin 调用。 */
    public static void capture(Object instance) {
        if (instance != null) {
            registry = instance;    // 新实例就换掉旧的（会重新注册）
        }
    }

    public static Object registry() {
        return registry;
    }

    /** 已成功注册（注册只能在 registry 被冻结之前做，所以由 mixin 在构造时立刻完成）。 */
    public static boolean isInstalled() {
        return installedRegistry != null;
    }

    /** 这个实例是否已经挂过（重复进世界时用来跳过）。 */
    public static boolean isInstalledFor(Object instance) {
        return instance != null && installedRegistry == instance;
    }

    public static void markInstalled(Object instance) {
        installedRegistry = instance;
    }

    public static boolean hasRegistry() {
        return registry != null;
    }

    /** 注册失败过一次就不要再试（Xaero 版本不符时避免每帧刷屏）。 */
    public static boolean hasFailed() {
        return failed;
    }

    public static void markFailed() {
        failed = true;
    }
}
