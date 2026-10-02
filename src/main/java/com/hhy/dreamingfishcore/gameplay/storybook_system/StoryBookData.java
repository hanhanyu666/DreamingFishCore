package com.hhy.dreamingfishcore.gameplay.storybook_system;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 玩家随记本数据。
 * 只保存玩家自己的收集、阅读、章节解锁和排序状态。
 */
public class StoryBookData {
    private boolean hasStoryBook;
    private boolean journeyStarted;
    private int fragmentPageUseCount;
    private Set<Integer> unlockedFragmentIds = new LinkedHashSet<>();
    private Set<Integer> readFragmentIds = new HashSet<>();
    private Set<Integer> unlockedChapterIds = new LinkedHashSet<>();
    private List<Integer> obtainedOrder = new ArrayList<>();

    // ==================== 里程碑 2：稳定 ID 的永久发现记录 ====================

    /**
     * 玩家永久发现的线索（稳定字符串 ID）。
     *
     * <p>旧的 int 集合刻意保留原样：旧存档里的 {@code [4,1]} 必须还能反序列化，
     * 否则整个世界的随记本会直接进只读保护。升级后的事实写在这里，
     * 加载时由 {@link #migrateLegacyClueIds} 把旧编号映射进来（幂等，可重复调用）。</p>
     */
    private Set<String> discoveredClueIds = new LinkedHashSet<>();
    private Set<String> readClueIds = new HashSet<>();
    private List<String> clueOrder = new ArrayList<>();

    public StoryBookData() {
    }

    public boolean hasStoryBook() {
        return hasStoryBook;
    }

    public void setHasStoryBook(boolean hasStoryBook) {
        this.hasStoryBook = hasStoryBook;
    }

    public boolean isJourneyStarted() {
        return journeyStarted;
    }

    public void setJourneyStarted(boolean journeyStarted) {
        this.journeyStarted = journeyStarted;
    }

    public int getFragmentPageUseCount() {
        return fragmentPageUseCount;
    }

    public void setFragmentPageUseCount(int fragmentPageUseCount) {
        this.fragmentPageUseCount = Math.max(0, fragmentPageUseCount);
    }

    public void incrementFragmentPageUseCount() {
        this.fragmentPageUseCount++;
    }

    public Set<Integer> getUnlockedFragmentIds() {
        return unlockedFragmentIds;
    }

    public void setUnlockedFragmentIds(Set<Integer> unlockedFragmentIds) {
        this.unlockedFragmentIds = unlockedFragmentIds != null
                ? new LinkedHashSet<>(unlockedFragmentIds)
                : new LinkedHashSet<>();
        rebuildObtainedOrder();
    }

    public Set<Integer> getReadFragmentIds() {
        return readFragmentIds;
    }

    public void setReadFragmentIds(Set<Integer> readFragmentIds) {
        this.readFragmentIds = readFragmentIds != null
                ? new HashSet<>(readFragmentIds)
                : new HashSet<>();
    }

    public Set<Integer> getUnlockedChapterIds() {
        return unlockedChapterIds;
    }

    public void setUnlockedChapterIds(Set<Integer> unlockedChapterIds) {
        this.unlockedChapterIds = unlockedChapterIds != null
                ? new LinkedHashSet<>(unlockedChapterIds)
                : new LinkedHashSet<>();
    }

    public List<Integer> getObtainedOrder() {
        return obtainedOrder;
    }

    public void setObtainedOrder(List<Integer> obtainedOrder) {
        this.obtainedOrder = obtainedOrder != null
                ? new ArrayList<>(obtainedOrder)
                : new ArrayList<>();
        normalizeObtainedOrder();
    }

    /**
     * 获取排序后的片段ID列表（玩家自定义顺序）
     */
    public List<Integer> getSortedFragmentIds() {
        return new ArrayList<>(obtainedOrder);
    }

    /**
     * 移动片段到指定位置
     * @param fragmentId 片段ID
     * @param newPosition 目标位置（0-based）
     * @return 是否移动成功
     */
    public boolean moveFragmentTo(int fragmentId, int newPosition) {
        if (!unlockedFragmentIds.contains(fragmentId)) {
            return false;
        }

        int currentIndex = obtainedOrder.indexOf(fragmentId);
        if (currentIndex < 0) {
            return false;
        }

        // 确保新位置在有效范围内
        newPosition = Math.max(0, Math.min(newPosition, obtainedOrder.size() - 1));

        obtainedOrder.remove(currentIndex);
        obtainedOrder.add(newPosition, fragmentId);
        return true;
    }

    /**
     * 交换两个片段的位置
     * @param fragmentId1 第一个片段ID
     * @param fragmentId2 第二个片段ID
     * @return 是否交换成功
     */
    public boolean swapFragments(int fragmentId1, int fragmentId2) {
        if (!unlockedFragmentIds.contains(fragmentId1) || !unlockedFragmentIds.contains(fragmentId2)) {
            return false;
        }

        int index1 = obtainedOrder.indexOf(fragmentId1);
        int index2 = obtainedOrder.indexOf(fragmentId2);

        if (index1 < 0 || index2 < 0) {
            return false;
        }

        obtainedOrder.set(index1, fragmentId2);
        obtainedOrder.set(index2, fragmentId1);
        return true;
    }

    /**
     * 将片段移动到最前面
     */
    public boolean moveFragmentToFront(int fragmentId) {
        return moveFragmentTo(fragmentId, 0);
    }

    /**
     * 将片段移动到最后面
     */
    public boolean moveFragmentToBack(int fragmentId) {
        return moveFragmentTo(fragmentId, obtainedOrder.size() - 1);
    }

    /**
     * 向前移动一位
     */
    public boolean moveFragmentUp(int fragmentId) {
        int currentIndex = obtainedOrder.indexOf(fragmentId);
        if (currentIndex <= 0) {
            return false;
        }
        return moveFragmentTo(fragmentId, currentIndex - 1);
    }

    /**
     * 向后移动一位
     */
    public boolean moveFragmentDown(int fragmentId) {
        int currentIndex = obtainedOrder.indexOf(fragmentId);
        if (currentIndex < 0 || currentIndex >= obtainedOrder.size() - 1) {
            return false;
        }
        return moveFragmentTo(fragmentId, currentIndex + 1);
    }

    public boolean unlockFragment(int fragmentId) {
        if (fragmentId <= 0) {
            return false;
        }
        boolean added = unlockedFragmentIds.add(fragmentId);
        if (added && !obtainedOrder.contains(fragmentId)) {
            obtainedOrder.add(fragmentId);
        }
        return added;
    }

    public boolean hasUnlockedFragment(int fragmentId) {
        return unlockedFragmentIds.contains(fragmentId);
    }

    public void markFragmentRead(int fragmentId) {
        if (fragmentId > 0) {
            readFragmentIds.add(fragmentId);
        }
    }

    public boolean hasReadFragment(int fragmentId) {
        return readFragmentIds.contains(fragmentId);
    }

    public boolean unlockChapter(int chapterId) {
        if (chapterId <= 0) {
            return false;
        }
        return unlockedChapterIds.add(chapterId);
    }

    public boolean hasUnlockedChapter(int chapterId) {
        return unlockedChapterIds.contains(chapterId);
    }

    public int getUnlockedFragmentCount() {
        return unlockedFragmentIds.size();
    }

    public int getReadFragmentCount() {
        return readFragmentIds.size();
    }

    public void clearAllFragments() {
        unlockedFragmentIds.clear();
        readFragmentIds.clear();
        obtainedOrder.clear();
    }

    private void rebuildObtainedOrder() {
        if (obtainedOrder == null) {
            obtainedOrder = new ArrayList<>();
        }
        if (obtainedOrder.isEmpty()) {
            obtainedOrder.addAll(unlockedFragmentIds);
            return;
        }
        normalizeObtainedOrder();
    }

    private void normalizeObtainedOrder() {
        LinkedHashSet<Integer> normalized = new LinkedHashSet<>();
        for (Integer fragmentId : obtainedOrder) {
            if (fragmentId != null && unlockedFragmentIds.contains(fragmentId)) {
                normalized.add(fragmentId);
            }
        }
        for (Integer fragmentId : unlockedFragmentIds) {
            if (fragmentId != null) {
                normalized.add(fragmentId);
            }
        }
        obtainedOrder = new ArrayList<>(normalized);
    }

    // ==================== 里程碑 2：永久发现记录 ====================

    public Set<String> getDiscoveredClueIds() {
        if (discoveredClueIds == null) {
            discoveredClueIds = new LinkedHashSet<>();
        }
        return discoveredClueIds;
    }

    public void setDiscoveredClueIds(Set<String> ids) {
        this.discoveredClueIds = ids != null ? new LinkedHashSet<>(ids) : new LinkedHashSet<>();
        normalizeClueRecords();
    }

    public Set<String> getReadClueIds() {
        if (readClueIds == null) {
            readClueIds = new HashSet<>();
        }
        return readClueIds;
    }

    public void setReadClueIds(Set<String> ids) {
        this.readClueIds = ids != null ? new HashSet<>(ids) : new HashSet<>();
        normalizeClueRecords();
    }

    public List<String> getClueOrder() {
        if (clueOrder == null) {
            clueOrder = new ArrayList<>();
        }
        return clueOrder;
    }

    public void setClueOrder(List<String> order) {
        this.clueOrder = order != null ? new ArrayList<>(order) : new ArrayList<>();
        normalizeClueRecords();
    }

    /**
     * 登记一条线索为"已发现"。
     *
     * <p>按 ADR 0009，**发现即永久记录**：发放的那一刻就写进这里，之后的残页物品只是
     * 查看与分享的载体，丢了不影响知识。</p>
     *
     * @return 是否是本次新发现的（false 表示早就有了，或参数非法）
     */
    public boolean discoverClue(String clueId) {
        if (clueId == null || clueId.isBlank()) {
            return false;
        }
        boolean added = getDiscoveredClueIds().add(clueId);
        if (added && !getClueOrder().contains(clueId)) {
            getClueOrder().add(clueId);
        }
        return added;
    }

    public boolean hasDiscoveredClue(String clueId) {
        return clueId != null && !clueId.isBlank() && getDiscoveredClueIds().contains(clueId);
    }

    public void markClueRead(String clueId) {
        if (clueId != null && !clueId.isBlank() && hasDiscoveredClue(clueId)) {
            getReadClueIds().add(clueId);
        }
    }

    public boolean hasReadClue(String clueId) {
        return clueId != null && !clueId.isBlank() && getReadClueIds().contains(clueId);
    }

    public int getDiscoveredClueCount() {
        return getDiscoveredClueIds().size();
    }

    /** 已发现线索的稳定顺序：先按记录顺序，漏掉的按发现集合补齐。 */
    public List<String> getSortedClueIds() {
        normalizeClueRecords();
        return List.copyOf(getClueOrder());
    }

    /**
     * 把旧整数编号迁移成稳定 ID。
     *
     * <p>{@code mapper} 由调用方提供（生产用 {@code ClueCatalog::idForLegacy}，测试给假映射），
     * 这样随记本本身不依赖线索目录。旧字段**不清空**：目录出问题时还能靠它恢复，
     * 也让这次升级保持可回退。</p>
     *
     * @return 是否发生了迁移
     */
    public boolean migrateLegacyClueIds(java.util.function.IntFunction<String> mapper) {
        if (mapper == null) {
            return false;
        }
        boolean changed = false;
        for (Integer legacyId : new ArrayList<>(unlockedFragmentIds)) {
            if (legacyId == null) {
                continue;
            }
            String clueId = mapper.apply(legacyId);
            if (clueId == null || clueId.isBlank()) {
                continue;
            }
            changed |= discoverClue(clueId);
            if (readFragmentIds.contains(legacyId)) {
                if (!hasReadClue(clueId)) {
                    getReadClueIds().add(clueId);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** 去空白、去重、补齐顺序；加载后与每次集合变更后都安全可重复调用。 */
    public void normalizeClueRecords() {
        Set<String> discovered = new LinkedHashSet<>();
        for (String clueId : getDiscoveredClueIds()) {
            if (clueId != null && !clueId.isBlank()) {
                discovered.add(clueId);
            }
        }
        discoveredClueIds = discovered;

        Set<String> read = new HashSet<>();
        for (String clueId : getReadClueIds()) {
            if (clueId != null && !clueId.isBlank() && discovered.contains(clueId)) {
                read.add(clueId);
            }
        }
        readClueIds = read;

        LinkedHashSet<String> order = new LinkedHashSet<>();
        for (String clueId : getClueOrder()) {
            if (clueId != null && discovered.contains(clueId)) {
                order.add(clueId);
            }
        }
        order.addAll(discovered);
        clueOrder = new ArrayList<>(order);
    }
}
