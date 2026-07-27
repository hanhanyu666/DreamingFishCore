package com.hhy.dreamingfishcore.gameplay.story_system;

import com.google.gson.annotations.SerializedName;

/**
 * 一个故事任务的“定义数据”和“客户端显示数据”。
 *
 * <p>前半部分字段来自 {@code story_stage_data.json}，描述任务叫什么、显示什么内容。
 * 后半部分的 {@code transient} 字段由服务端运行状态临时填充，只用于发给客户端显示。
 * {@code transient} 的含义是：Gson 保存配置时不会把这些运行状态写回任务定义文件。</p>
 *
 * <p>真正需要永久保存的任务结果不在这里，而在
 * {@link StoryWorldState.TaskProgress} 中。这样修改任务文案不会覆盖世界已经发生过的历史。</p>
 */
public class StoryTaskData {
    /** 稳定字符串 ID，例如 dreamingfishcore:medical_station_defense。 */
    @SerializedName("id")
    private String taskKey;
    /** 给旧界面、旧命令和排序使用的正整数编号。 */
    @SerializedName("number")
    private int taskId;
    /** 玩家在终端里看到的任务名称。 */
    @SerializedName("name")
    private String taskName;
    /** 玩家在终端里看到的引导内容。 */
    @SerializedName("content")
    private String taskContent;
    /** 可选的开始时间；目前保留旧系统字段，具体时间语义由未来任务执行器决定。 */
    private long startTime;
    /** 可选的结束时间；0 表示没有设置结束时间。 */
    private long endTime;
    /** 进入所属阶段时，是否自动将该任务发布到世界。 */
    private boolean publishedByDefault;
    /** 可选的任务地点 ID；空字符串表示该任务不依赖固定地点。 */
    private String locationId = "";

    // 以下字段只是某一次查询生成的“视图”，不属于配置文件，也不直接持久化。
    private transient boolean published;
    /** 成功和失败都会为 true，因为两者都表示任务已经结算。 */
    private transient boolean completed;
    /** 单独记录失败，供客户端把任务显示为红色。 */
    private transient boolean failed;
    /** 接收这份视图的玩家是否取得了该任务的个人记录。 */
    private transient boolean clientPlayerFinished;
    /** 任务结算时被记录在场的玩家人数，不向客户端发送姓名或 UUID。 */
    private transient int finishedPlayerCount;

    /** Gson 反序列化 JSON 时需要无参构造方法。 */
    public StoryTaskData() {
    }

    public StoryTaskData(String taskKey, int taskId, String taskName, String taskContent,
                         long startTime, long endTime) {
        this.taskKey = taskKey;
        this.taskId = taskId;
        this.taskName = taskName;
        this.taskContent = taskContent;
        this.startTime = startTime;
        this.endTime = endTime;
        this.publishedByDefault = true;
        this.published = true;
    }

    /**
     * 复制一份专门用于查询或网络同步的数据。
     *
     * <p>不能直接把缓存中的配置对象交给客户端逻辑修改，否则一次查询可能污染全局定义。</p>
     */
    StoryTaskData copyForView() {
        StoryTaskData copy = new StoryTaskData(taskKey, taskId, taskName, taskContent, startTime, endTime);
        copy.publishedByDefault = publishedByDefault;
        copy.locationId = locationId;
        copy.published = published;
        copy.completed = completed;
        copy.failed = failed;
        copy.clientPlayerFinished = clientPlayerFinished;
        copy.finishedPlayerCount = finishedPlayerCount;
        return copy;
    }

    /** 服务器加载配置时检查该任务定义，尽早拒绝错误 JSON。 */
    void validateDefinition() {
        StoryWorldState.requireValidId(taskKey, "故事任务");
        if (taskId <= 0) {
            throw new IllegalStateException("故事任务数字编号必须大于零：" + taskKey);
        }
        if (taskName == null || taskName.isBlank()) {
            throw new IllegalStateException("故事任务名称不能为空：" + taskKey);
        }
        if (taskContent == null) {
            taskContent = "";
        }
        if (startTime < 0L || endTime < 0L || endTime > 0L && endTime < startTime) {
            throw new IllegalStateException("故事任务时间范围非法：" + taskKey);
        }
        if (locationId == null) {
            locationId = "";
        } else if (!locationId.isBlank()) {
            StoryWorldState.requireValidId(locationId, "任务地点");
        }
    }

    /**
     * 将世界存档中的运行结果合并进这份客户端视图。
     *
     * @param progress 世界中的任务状态；null 表示任务还没有发布
     * @param playerFinished 当前接收客户端是否取得了个人记录
     */
    void applyRuntimeView(StoryWorldState.TaskProgress progress, boolean playerFinished) {
        published = progress != null;
        completed = progress != null && progress.getOutcome().isResolved();
        failed = progress != null && progress.getOutcome() == StoryTaskOutcome.FAILED;
        clientPlayerFinished = progress != null && playerFinished;
        finishedPlayerCount = progress == null ? 0 : progress.getParticipantCount();
    }

    public String getTaskKey() {
        return taskKey;
    }

    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    public int getTaskId() {
        return taskId;
    }

    public void setTaskId(int taskId) {
        this.taskId = taskId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getTaskContent() {
        return taskContent;
    }

    public void setTaskContent(String taskContent) {
        this.taskContent = taskContent;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public boolean isPublishedByDefault() {
        return publishedByDefault;
    }

    public void setPublishedByDefault(boolean publishedByDefault) {
        this.publishedByDefault = publishedByDefault;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId == null ? "" : locationId;
    }

    public boolean isTaskState() {
        return published;
    }

    public void setTaskState(boolean published) {
        this.published = published;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public boolean isFailed() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    public boolean isClientPlayerFinished() {
        return clientPlayerFinished;
    }

    public void setClientPlayerFinished(boolean clientPlayerFinished) {
        this.clientPlayerFinished = clientPlayerFinished;
    }

    public int getFinishedPlayerCount() {
        return finishedPlayerCount;
    }

    public void setFinishedPlayerCount(int finishedPlayerCount) {
        this.finishedPlayerCount = Math.max(0, finishedPlayerCount);
    }
}
