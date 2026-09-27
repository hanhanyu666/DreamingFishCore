package com.hhy.dreamingfishcore.gameplay.story_system;

import com.hhy.dreamingfishcore.gameplay.story_system.runtime.StoryTextCatalog;
import net.minecraft.server.MinecraftServer;

import java.time.Instant;

/**
 * 故事内容包的运行时门面。
 *
 * <p>阶段/任务定义由 {@link StoryManager} 管理，剧情顺序由 Java 状态机管理，本类只
 * 负责加载可编辑文案并记录内容版本。旧的 {@code story_flows.json} 不再参与运行时。</p>
 *
 * <p>热重载会先校验阶段/任务与文案目录；任一校验失败时不会更新
 * {@code lastSuccessfulContentId}。安装仍按“定义→文案”两个受控步骤进行，
 * 因此新增内容应先通过 validate，再执行 reload；失败时保留版本记录并记录错误，
 * 下一次重载可继续修复。</p>
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
    public static synchronized void loadWorldData(MinecraftServer server) {
        clearWorldCache();
        // StoryManager 负责唯一的阶段运行时；本类只加载可编辑文案。
        StoryTextCatalog.loadWorldData();
        loaded = true;
        lastReloadEpochMillis = Instant.now().toEpochMilli();
        lastError = "";
    }

    /** 只解析校验，不更新当前正在运行的内容。 */
    public static synchronized StoryManager.DefinitionSummary validate() {
        ensureLoaded();
        try {
            StoryManager.DefinitionSummary summary = StoryManager.validateDefinitions();
            StoryTextCatalog.Summary textSummary = StoryTextCatalog.validateDefinitions();
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
            // 文案先安装，Java 阶段定义随后读取同一版文案；这样修改文本后一次
            // reload 就能立即更新任务标题/说明，不会出现“要重载两次才生效”。
            StoryTextCatalog.validateDefinitions();
            StoryManager.validateDefinitions();
            StoryTextCatalog.Summary textSummary = StoryTextCatalog.reloadDefinitions();
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
                            "taskCount", Integer.toString(summary.taskCount()),
                            "textCount", Integer.toString(textSummary.textCount()),
                            "dialogueCount", Integer.toString(textSummary.dialogueCount())));
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
        StoryTextCatalog.clear();
        loaded = false;
        lastSuccessfulContentId = INITIAL_CONTENT_ID;
        lastReloadEpochMillis = 0L;
        lastError = "";
    }

    /** 保存两个章节状态机的事实；调用顺序应晚于其投影依赖的其他管理器。 */
    public static synchronized boolean saveIfDirty(MinecraftServer server) {
        // 文案配置在加载/热重载时保存，世界运行时事实由 StoryManager 保存。
        return true;
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
