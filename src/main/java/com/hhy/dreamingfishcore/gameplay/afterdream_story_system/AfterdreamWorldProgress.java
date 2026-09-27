package com.hhy.dreamingfishcore.gameplay.afterdream_story_system;

/** 余梦期全服倒计时和一次性公告的持久化状态。 */
public final class AfterdreamWorldProgress {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final long TICKS_PER_GAME_DAY = 24_000L;
    public static final long MASK_COUNTDOWN_TICKS = TICKS_PER_GAME_DAY * 2L;
    /** 第二阶段开场后，丧尸记忆公告的延迟；按世界 gameTime 计算，重启不会清零。 */
    public static final long ZOMBIE_DIGGING_COUNTDOWN_TICKS = TICKS_PER_GAME_DAY * 2L;

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private long maskCountdownStartedAtActiveTick = -1L;
    private long maskAvailableAtActiveTick = -1L;
    private boolean maskAnnouncementSent;
    private boolean maskDistributionAvailable;
    private boolean virusEvolutionAnnouncementSent;
    private long firstMaskGrantedAtActiveTick = -1L;
    private long zombieDiggingCountdownStartedAtGameTime = -1L;
    private long zombieDiggingAvailableAtGameTime = -1L;
    private boolean zombieDiggingEnabled;
    private boolean zombieDiggingAnnouncementSent;

    public AfterdreamWorldProgress() {
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public long getMaskCountdownStartedAtActiveTick() {
        return maskCountdownStartedAtActiveTick;
    }

    public long getMaskAvailableAtActiveTick() {
        return maskAvailableAtActiveTick;
    }

    public boolean isMaskAnnouncementSent() {
        return maskAnnouncementSent;
    }

    public boolean isMaskDistributionAvailable() {
        return maskDistributionAvailable;
    }

    public boolean isVirusEvolutionAnnouncementSent() {
        return virusEvolutionAnnouncementSent;
    }

    public long getFirstMaskGrantedAtActiveTick() {
        return firstMaskGrantedAtActiveTick;
    }

    public long getZombieDiggingCountdownStartedAtGameTime() {
        return zombieDiggingCountdownStartedAtGameTime;
    }

    public long getZombieDiggingAvailableAtGameTime() {
        return zombieDiggingAvailableAtGameTime;
    }

    public boolean isZombieDiggingEnabled() {
        return zombieDiggingEnabled;
    }

    public boolean isZombieDiggingAnnouncementSent() {
        return zombieDiggingAnnouncementSent;
    }

    /** 第二阶段第一次加载时启动；重复调用绝不重置截止时间。 */
    public boolean startZombieDiggingCountdown(long gameTime) {
        if (zombieDiggingCountdownStartedAtGameTime >= 0L) {
            return false;
        }
        long start = Math.max(0L, gameTime);
        zombieDiggingCountdownStartedAtGameTime = start;
        zombieDiggingAvailableAtGameTime = safeAdd(start, ZOMBIE_DIGGING_COUNTDOWN_TICKS);
        return true;
    }

    /** 当前世界时间是否已经到达丧尸记忆公告的发布时间。 */
    public boolean isZombieDiggingDue(long gameTime) {
        return !zombieDiggingEnabled
                && zombieDiggingAvailableAtGameTime >= 0L
                && gameTime >= zombieDiggingAvailableAtGameTime;
    }

    public boolean markZombieDiggingEnabled() {
        if (zombieDiggingEnabled) {
            return false;
        }
        zombieDiggingEnabled = true;
        return true;
    }

    public boolean markZombieDiggingAnnouncementSent() {
        if (zombieDiggingAnnouncementSent) {
            return false;
        }
        zombieDiggingAnnouncementSent = true;
        return true;
    }

    /** 第一个玩家完成首次接待时启动；重复调用绝不重置截止时间。 */
    public boolean startMaskCountdown(long activeTick) {
        if (maskCountdownStartedAtActiveTick >= 0L) {
            return false;
        }
        long start = Math.max(0L, activeTick);
        maskCountdownStartedAtActiveTick = start;
        maskAvailableAtActiveTick = safeAdd(start, MASK_COUNTDOWN_TICKS);
        return true;
    }

    /** 返回本次 tick 是否刚刚到达面具公告时间。 */
    public boolean advanceMaskCountdown(long activeTick) {
        if (maskDistributionAvailable || maskAvailableAtActiveTick < 0L
                || activeTick < maskAvailableAtActiveTick) {
            return false;
        }
        maskDistributionAvailable = true;
        return true;
    }

    public boolean markMaskAnnouncementSent() {
        if (maskAnnouncementSent) {
            return false;
        }
        maskAnnouncementSent = true;
        maskDistributionAvailable = true;
        return true;
    }

    /**
     * 从感染系统的全局事实恢复“面具已经可以领取”。
     *
     * <p>面具发放会先写入感染系统的世界旗标，再写入本章节文件。服务器如果恰好
     * 在这两个写入之间重启，章节文件可能仍显示倒计时未到，但全局感染规则已经
     * 正确切换。此方法只补齐可领取事实，不伪造首次领取时间，也不会重复发放物品。</p>
     */
    public boolean reconcileMaskDistributionAvailable(boolean globalMaskGranted) {
        if (!globalMaskGranted || maskDistributionAvailable) {
            return false;
        }
        maskDistributionAvailable = true;
        return true;
    }

    public boolean markFirstMaskGranted(long activeTick) {
        if (firstMaskGrantedAtActiveTick >= 0L) {
            return false;
        }
        firstMaskGrantedAtActiveTick = Math.max(0L, activeTick);
        return true;
    }

    public boolean markVirusEvolutionAnnouncementSent() {
        if (virusEvolutionAnnouncementSent) {
            return false;
        }
        virusEvolutionAnnouncementSent = true;
        return true;
    }

    /** 校验已经放入统一故事存档的余梦期世界事实。 */
    public void validateState(long activeTicks) {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("不支持的余梦期世界状态版本：" + schemaVersion);
        }
        validateTick(maskCountdownStartedAtActiveTick, activeTicks, "面具倒计时起点");
        if (maskAvailableAtActiveTick < -1L) {
            throw new IllegalStateException("面具可领取时间非法：" + maskAvailableAtActiveTick);
        }
        validateTick(firstMaskGrantedAtActiveTick, activeTicks, "首次面具领取时间");
        if (maskCountdownStartedAtActiveTick < 0L && maskAvailableAtActiveTick >= 0L) {
            throw new IllegalStateException("面具可领取时间缺少倒计时起点");
        }
        if (maskCountdownStartedAtActiveTick >= 0L
                && maskAvailableAtActiveTick < maskCountdownStartedAtActiveTick) {
            throw new IllegalStateException("面具可领取时间早于倒计时起点");
        }
        if (maskAnnouncementSent && !maskDistributionAvailable) {
            throw new IllegalStateException("面具公告已发布但面具尚不可领取");
        }
        if (firstMaskGrantedAtActiveTick >= 0L && !maskDistributionAvailable) {
            throw new IllegalStateException("已经发放面具但全局面具规则尚未开启");
        }
        validateGameTime(zombieDiggingCountdownStartedAtGameTime, "丧尸挖掘倒计时起点");
        validateGameTime(zombieDiggingAvailableAtGameTime, "丧尸挖掘公告时间");
        if (zombieDiggingCountdownStartedAtGameTime < 0L
                && zombieDiggingAvailableAtGameTime >= 0L) {
            throw new IllegalStateException("丧尸挖掘公告时间缺少倒计时起点");
        }
        if (zombieDiggingCountdownStartedAtGameTime >= 0L
                && zombieDiggingAvailableAtGameTime < zombieDiggingCountdownStartedAtGameTime) {
            throw new IllegalStateException("丧尸挖掘公告时间早于倒计时起点");
        }
        if (zombieDiggingEnabled && zombieDiggingCountdownStartedAtGameTime < 0L) {
            throw new IllegalStateException("丧尸挖掘已开启但没有倒计时起点");
        }
        if (zombieDiggingAnnouncementSent && !zombieDiggingEnabled) {
            throw new IllegalStateException("丧尸挖掘公告已发布但能力尚未开启");
        }
    }

    private static void validateTick(long value, long activeTicks, String name) {
        if (value < -1L || value > activeTicks) {
            throw new IllegalStateException(name + "超出在线活动时间范围：" + value);
        }
    }

    private static void validateGameTime(long value, String name) {
        if (value < -1L) {
            throw new IllegalStateException(name + "不能为负数：" + value);
        }
    }

    public boolean repair() {
        boolean changed = false;
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("不支持的余梦期世界状态版本：" + schemaVersion);
        }
        if (maskCountdownStartedAtActiveTick < -1L) {
            maskCountdownStartedAtActiveTick = -1L;
            changed = true;
        }
        if (maskAvailableAtActiveTick < -1L) {
            maskAvailableAtActiveTick = -1L;
            changed = true;
        }
        if (firstMaskGrantedAtActiveTick < -1L) {
            firstMaskGrantedAtActiveTick = -1L;
            changed = true;
        }
        if (zombieDiggingCountdownStartedAtGameTime < -1L) {
            zombieDiggingCountdownStartedAtGameTime = -1L;
            changed = true;
        }
        if (zombieDiggingAvailableAtGameTime < -1L) {
            zombieDiggingAvailableAtGameTime = -1L;
            changed = true;
        }
        if (maskCountdownStartedAtActiveTick < 0L && maskAvailableAtActiveTick >= 0L) {
            throw new IllegalStateException("余梦期面具截止时间缺少倒计时起点");
        }
        if (maskCountdownStartedAtActiveTick >= 0L && maskAvailableAtActiveTick < 0L) {
            maskAvailableAtActiveTick = safeAdd(
                    maskCountdownStartedAtActiveTick, MASK_COUNTDOWN_TICKS);
            changed = true;
        }
        if (maskAnnouncementSent && !maskDistributionAvailable) {
            maskDistributionAvailable = true;
            changed = true;
        }
        if (firstMaskGrantedAtActiveTick >= 0L && !maskDistributionAvailable) {
            maskDistributionAvailable = true;
            changed = true;
        }
        if (zombieDiggingCountdownStartedAtGameTime < 0L
                && zombieDiggingAvailableAtGameTime >= 0L) {
            throw new IllegalStateException("丧尸挖掘公告时间缺少倒计时起点");
        }
        if (zombieDiggingCountdownStartedAtGameTime >= 0L
                && zombieDiggingAvailableAtGameTime < 0L) {
            zombieDiggingAvailableAtGameTime = safeAdd(
                    zombieDiggingCountdownStartedAtGameTime, ZOMBIE_DIGGING_COUNTDOWN_TICKS);
            changed = true;
        }
        if (zombieDiggingAnnouncementSent && !zombieDiggingEnabled) {
            zombieDiggingEnabled = true;
            changed = true;
        }
        return changed;
    }

    private static long safeAdd(long first, long second) {
        if (second > 0L && first > Long.MAX_VALUE - second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }
}
