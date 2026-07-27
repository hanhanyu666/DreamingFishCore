package com.hhy.dreamingfishcore.gameplay.story_system;

import java.time.Instant;

/**
 * 故事内容包的运行时门面。
 *
 * <p>当前第一版内容包只包含 {@code config/dreamingfishcore/story_stage_data.json}，
 * 也就是阶段和任务定义。后续可以在不改变命令入口的情况下，把线索、NPC 对话、广播和尸潮事件
 * 的定义逐步加入同一套校验流程。它与 {@link StoryManager} 的边界是：StoryManager 负责故事状态，
 * 本类负责“哪一版内容正在运行”。</p>
 *
 * <p>热重载失败时，StoryManager 不会替换旧索引，因此内存中的上一版内容继续服务玩家。
 * 成功重载后才更新 lastSuccessfulContentId，这就是本次运行的最后可用版本。</p>
 */
public final class ContentPackManager {
    /** 当前只实现故事定义这一类内容；ID 仍采用稳定命名空间格式。 */
    private static final String INITIAL_CONTENT_ID = "dreamingfishcore:startup";

    private static boolean loaded;
    private static String lastSuccessfulContentId = INITIAL_CONTENT_ID;
    private static long lastReloadEpochMillis;
    private static String lastError = "";

    private ContentPackManager() {
    }

    /** 故事系统加载成功后调用，建立本次世界会话的内容包状态。 */
    public static synchronized void loadWorldData() {
        clearWorldCache();
        loaded = true;
        lastReloadEpochMillis = Instant.now().toEpochMilli();
        lastError = "";
    }

    /** 只解析校验，不更新当前正在运行的内容。 */
    public static synchronized StoryManager.DefinitionSummary validate() {
        ensureLoaded();
        try {
            StoryManager.DefinitionSummary summary = StoryManager.validateDefinitions();
            lastError = "";
            return summary;
        } catch (RuntimeException exception) {
            lastError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            throw exception;
        }
    }

    /**
     * 验证并安装一版新内容。
     *
     * @param contentId 运营轮次使用的内容版本 ID，不是文件路径
     * @param actor      执行热重载的服主名称
     */
    public static synchronized ReloadResult reload(String contentId, String actor) {
        ensureLoaded();
        StoryWorldState.requireValidId(contentId, "内容包");
        try {
            StoryManager.DefinitionSummary summary = StoryManager.reloadDefinitions();
            lastSuccessfulContentId = contentId;
            lastReloadEpochMillis = Instant.now().toEpochMilli();
            lastError = "";
            StoryManager.recordHistory(
                    WorldHistoryLog.EventType.CONTENT_RELOADED,
                    contentId,
                    actor,
                    java.util.Map.of(
                            "generation", Long.toString(summary.generation()),
                            "stageCount", Integer.toString(summary.stageCount()),
                            "taskCount", Integer.toString(summary.taskCount())));
            return new ReloadResult(contentId, summary);
        } catch (RuntimeException exception) {
            lastError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            throw exception;
        }
    }

    /** 返回内容包的只读状态，供管理员命令和未来管理界面使用。 */
    public static synchronized Status getStatus() {
        return new Status(
                loaded,
                lastSuccessfulContentId,
                lastReloadEpochMillis,
                StoryManager.areWritesEnabled(),
                lastError);
    }

    /** 停服清空会话级内容缓存；文件内容不会被删除。 */
    public static synchronized void clearWorldCache() {
        loaded = false;
        lastSuccessfulContentId = INITIAL_CONTENT_ID;
        lastReloadEpochMillis = 0L;
        lastError = "";
    }

    private static void ensureLoaded() {
        if (!loaded) {
            throw new IllegalStateException("内容包管理器尚未随服务器世界加载");
        }
    }

    /** 热重载结果，给命令层显示本次实际安装了多少定义。 */
    public record ReloadResult(
            String contentId,
            StoryManager.DefinitionSummary definitionSummary) {
    }

    /** 内容包管理器状态；lastError 为空代表最近一次操作没有失败。 */
    public record Status(
            boolean loaded,
            String lastSuccessfulContentId,
            long lastReloadEpochMillis,
            boolean storyWritesEnabled,
            String lastError) {
    }
}
