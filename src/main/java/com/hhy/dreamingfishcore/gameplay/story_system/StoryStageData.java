package com.hhy.dreamingfishcore.gameplay.story_system;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个全服共享故事阶段的定义，以及发送给客户端时使用的阶段视图。
 *
 * <p>例如“余梦期”可以拥有：</p>
 * <ul>
 *     <li>稳定 ID：dreamingfishcore:afterdream</li>
 *     <li>数字编号：1</li>
 *     <li>显示名称：余梦期</li>
 *     <li>这个阶段定义的若干任务</li>
 * </ul>
 *
 * <p>配置里可以定义很多任务。客户端视图通常只包含已经发布到世界的任务，
 * 但带个人部分的任务会在世界层解锁前提前显示，方便玩家从世界故事中看到自己的待办。
 * 这个过滤和运行时合并过程由 {@link StoryManager} 完成。</p>
 */
public class StoryStageData {
    /** 稳定字符串 ID，适合保存到存档和被脚本引用。 */
    @SerializedName("id")
    private String stageId;
    /** 便于排序和兼容旧代码的正整数编号。 */
    @SerializedName("number")
    private int stageNumber;
    /** 玩家可见名称。 */
    @SerializedName("name")
    private String stageName;
    /** 玩家可见的阶段简介。 */
    @SerializedName("description")
    private String stageDescription;
    /** 仅运行时使用的当前阶段标记，不属于故事定义配置。 */
    private transient boolean currentStage;
    /** 服务端按全体符合条件玩家计算出的阶段完成比例；未填充时由本地任务视图回退计算。 */
    private transient float globalProgressPercentage = -1.0f;
    /** 该阶段定义的任务；客户端副本可能包含尚未发布但可见的个人任务。 */
    private List<StoryTaskData> tasks = new ArrayList<>();
    /** 可选的阶段怪物倍率。它只提供数据，实际应用仍需要怪物事件层调用。 */
    private MonsterModifier monsterModifier;

    /** Gson 反序列化 JSON 时需要无参构造方法。 */
    public StoryStageData() {
    }

    public StoryStageData(String stageId, int stageNumber, String stageName, String stageDescription) {
        this.stageId = stageId;
        this.stageNumber = stageNumber;
        this.stageName = stageName;
        this.stageDescription = stageDescription;
    }

    /**
     * 用指定的任务视图生成阶段副本，避免调用方修改全局配置缓存。
     */
    StoryStageData copyWithTasks(List<StoryTaskData> taskViews) {
        StoryStageData copy = new StoryStageData(stageId, stageNumber, stageName, stageDescription);
        copy.tasks = new ArrayList<>(taskViews);
        copy.monsterModifier = monsterModifier == null ? null : monsterModifier.copy();
        copy.currentStage = currentStage;
        copy.globalProgressPercentage = globalProgressPercentage;
        return copy;
    }

    /** 服务器加载配置时检查阶段和其所有任务定义。 */
    void validateDefinition() {
        StoryWorldState.requireValidId(stageId, "故事阶段");
        if (stageNumber <= 0) {
            throw new IllegalStateException("故事阶段编号必须大于零：" + stageNumber);
        }
        if (stageName == null || stageName.isBlank()) {
            throw new IllegalStateException("故事阶段名称不能为空：" + stageId);
        }
        if (stageDescription == null) {
            stageDescription = "";
        }
        if (tasks == null) {
            tasks = new ArrayList<>();
        }
        for (StoryTaskData task : tasks) {
            if (task == null) {
                throw new IllegalStateException("故事阶段包含空任务：" + stageId);
            }
            task.validateDefinition();
        }
        if (monsterModifier != null) {
            monsterModifier.validate(stageId);
        }
    }

    public String getStageId() {
        return stageId;
    }

    public void setStageId(String stageId) {
        this.stageId = stageId;
    }

    public int getStageNumber() {
        return stageNumber;
    }

    public void setStageNumber(int stageNumber) {
        this.stageNumber = stageNumber;
    }

    public String getStageName() {
        return stageName;
    }

    public void setStageName(String stageName) {
        this.stageName = stageName;
    }

    public String getStageDescription() {
        return stageDescription;
    }

    public void setStageDescription(String stageDescription) {
        this.stageDescription = stageDescription;
    }

    public boolean isCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(boolean currentStage) {
        this.currentStage = currentStage;
    }

    public void setGlobalProgressPercentage(float globalProgressPercentage) {
        this.globalProgressPercentage = Float.isFinite(globalProgressPercentage)
                ? Math.max(0.0f, Math.min(1.0f, globalProgressPercentage))
                : -1.0f;
    }

    public List<StoryTaskData> getTasks() {
        return tasks;
    }

    public void setTasks(List<StoryTaskData> tasks) {
        this.tasks = tasks;
    }

    public void addTask(StoryTaskData task) {
        if (tasks == null) {
            tasks = new ArrayList<>();
        }
        tasks.add(task);
    }

    public MonsterModifier getMonsterModifier() {
        return monsterModifier;
    }

    public void setMonsterModifier(MonsterModifier monsterModifier) {
        this.monsterModifier = monsterModifier;
    }

    public int getTotalTaskCount() {
        return tasks == null ? 0 : tasks.size();
    }

    public int getCompletedTaskCount() {
        return tasks == null ? 0 : (int) tasks.stream().filter(StoryTaskData::isCompleted).count();
    }

    public int getFailedTaskCount() {
        return tasks == null ? 0 : (int) tasks.stream().filter(StoryTaskData::isFailed).count();
    }

    public int getClientPlayerCompletedTaskCount() {
        return tasks == null ? 0 : (int) tasks.stream()
                .filter(StoryTaskData::isClientPlayerFinished)
                .count();
    }

    /**
     * 计算全服阶段进度。
     *
     * <p>个人故事任务按“完成玩家数 / 应参与玩家数”计入，而不是把一名玩家
     * 的完成当成整项世界任务完成。普通世界任务仍按成功或失败的结算状态计入。</p>
     */
    public float getGlobalProgressPercentage() {
        if (globalProgressPercentage >= 0.0f) {
            return globalProgressPercentage;
        }
        if (tasks == null || tasks.isEmpty()) {
            return 0.0f;
        }
        float progressSum = 0.0f;
        int trackedTaskCount = 0;
        for (StoryTaskData task : tasks) {
            if (task == null) {
                continue;
            }
            if (task.isPersonalTask()) {
                int expected = task.getPersonalExpectedPlayerCount();
                // 建设任务在还没有成员时不应把阶段进度凭空压低。
                if (expected <= 0) {
                    continue;
                }
                progressSum += Math.min(1.0f,
                        (float) task.getFinishedPlayerCount() / expected);
            } else {
                progressSum += task.isCompleted() ? 1.0f : 0.0f;
            }
            trackedTaskCount++;
        }
        return trackedTaskCount == 0 ? 0.0f : progressSum / trackedTaskCount;
    }

    /** 计算当前客户端玩家取得的个人任务记录比例。 */
    public float getClientPlayerProgressPercentage() {
        int total = getTotalTaskCount();
        return total == 0 ? 0.0f : (float) getClientPlayerCompletedTaskCount() / total;
    }

    /**
     * 旧界面的兼容方法。新代码应该明确选择全服进度或个人进度。
     */
    @Deprecated
    public float getProgressPercentage() {
        return getClientPlayerProgressPercentage();
    }

    /**
     * 阶段怪物数值倍率。
     *
     * <p>这个内部类只是配置数据，不会自己寻找或修改怪物。
     * 将来怪物生成/强化事件需要读取它，并把倍率应用到实体属性。</p>
     */
    public static class MonsterModifier {
        private float healthMultiplier = 1.0f;
        private float damageMultiplier = 1.0f;
        private float speedMultiplier = 1.0f;
        private float knockbackResistance;

        public MonsterModifier() {
        }

        public MonsterModifier(float healthMultiplier, float damageMultiplier,
                               float speedMultiplier, float knockbackResistance) {
            this.healthMultiplier = healthMultiplier;
            this.damageMultiplier = damageMultiplier;
            this.speedMultiplier = speedMultiplier;
            this.knockbackResistance = knockbackResistance;
        }

        private MonsterModifier copy() {
            return new MonsterModifier(healthMultiplier, damageMultiplier, speedMultiplier, knockbackResistance);
        }

        private void validate(String stageId) {
            if (!Float.isFinite(healthMultiplier) || healthMultiplier <= 0.0f
                    || !Float.isFinite(damageMultiplier) || damageMultiplier <= 0.0f
                    || !Float.isFinite(speedMultiplier) || speedMultiplier <= 0.0f
                    || !Float.isFinite(knockbackResistance) || knockbackResistance < 0.0f) {
                throw new IllegalStateException("故事阶段怪物倍率非法：" + stageId);
            }
        }

        public float getHealthMultiplier() {
            return healthMultiplier;
        }

        public void setHealthMultiplier(float healthMultiplier) {
            this.healthMultiplier = healthMultiplier;
        }

        public float getDamageMultiplier() {
            return damageMultiplier;
        }

        public void setDamageMultiplier(float damageMultiplier) {
            this.damageMultiplier = damageMultiplier;
        }

        public float getSpeedMultiplier() {
            return speedMultiplier;
        }

        public void setSpeedMultiplier(float speedMultiplier) {
            this.speedMultiplier = speedMultiplier;
        }

        public float getKnockbackResistance() {
            return knockbackResistance;
        }

        public void setKnockbackResistance(float knockbackResistance) {
            this.knockbackResistance = knockbackResistance;
        }
    }
}
