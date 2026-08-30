package com.hhy.dreamingfishcore.gameplay.story_system;

import com.hhy.dreamingfishcore.gameplay.story_system.runtime.StoryFlowDefinitionStore;
import com.hhy.dreamingfishcore.gameplay.story_system.runtime.StoryFlowEngine;
import net.minecraft.server.MinecraftServer;

import java.time.Instant;

/**
 * 故事内容包的运行时门面。
 *
 * <p>内容包现在同时包含阶段/任务定义与 {@code story_flows.json} 流程节点。
 * 后续可以在不改变命令入口的情况下，把线索、广播和随机事件继续加入同一套校验流程。
 * 它与 {@link StoryManager} 的边界是：StoryManager 负责故事状态，本类负责“哪一版内容正在运行”。</p>
 *
 * <p>热重载会先校验阶段/任务与流程定义，再分别安装两套索引；任一校验失败时不会安装
 * 对应候选内容，且不会更新 {@code lastSuccessfulContentId}。成功完成两套安装后，才把
 * 本次内容 ID 记录为运行中的最后一次成功版本。</p>
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
        // StoryManager 已先完成阶段索引；流程定义因此可以校验其 stageId 引用。
        StoryFlowEngine.loadWorldData(server);
        loaded = true;
        lastReloadEpochMillis = Instant.now().toEpochMilli();
        lastError = "";
    }

    /** 只解析校验，不更新当前正在运行的内容。 */
    public static synchronized StoryManager.DefinitionSummary validate() {
        ensureLoaded();
        try {
            StoryManager.DefinitionSummary summary = StoryManager.validateDefinitions();
            StoryFlowDefinitionStore.Summary flowSummary = StoryFlowEngine.validateDefinitions();
            if (flowSummary.flowCount() == 0) {
                throw new IllegalStateException("故事流程为空");
            }
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
            // 先校验流程引用，再分别安装阶段索引和流程索引；失败时不更新版本记录。
            StoryFlowEngine.validateDefinitions();
            StoryManager.DefinitionSummary summary = StoryManager.reloadDefinitions();
            StoryFlowDefinitionStore.Summary flowSummary = StoryFlowEngine.reloadDefinitions();
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
                            "flowCount", Integer.toString(flowSummary.flowCount()),
                            "flowNodeCount", Integer.toString(flowSummary.nodeCount())));
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
        StoryFlowEngine.clearWorldCache();
        loaded = false;
        lastSuccessfulContentId = INITIAL_CONTENT_ID;
        lastReloadEpochMillis = 0L;
        lastError = "";
    }

    /** 保存内容包拥有的玩家流程游标；调用顺序应晚于其效果依赖的其他管理器。 */
    public static synchronized boolean saveIfDirty(MinecraftServer server) {
        return StoryFlowEngine.saveIfDirty(server);
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
