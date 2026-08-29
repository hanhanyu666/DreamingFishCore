package com.hhy.dreamingfishcore.gameplay.task_location_system;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 一个由服主在固定地图中定义的官方任务地点。
 *
 * <p>任务地点只描述“哪里是剧情区域”以及“该区域当前是否启用”。
 * 它不保存任务成功/失败状态，也不会自行推进故事阶段。</p>
 */
public final class TaskLocationDefinition {
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._:/-]{0,127}");
    private static final int WORLD_COORDINATE_LIMIT = 30_000_000;

    /** 稳定 ID，例如 dreamingfishcore:old_medical_center。 */
    private String id;
    /** 面向服主和日志显示的地点名称。 */
    private String name;
    /** Minecraft 维度 ID，例如 minecraft:overworld。 */
    private String dimension = Level.OVERWORLD.location().toString();
    /** 闭区间最小角点。 */
    private Point min = new Point();
    /** 闭区间最大角点。 */
    private Point max = new Point();
    /** 停用后不参与保护、玩家收集和位置查询。 */
    private boolean enabled = true;
    /** 地点运行模式；旧配置缺少该字段时保持原有的强制保护行为。 */
    private String mode = TaskLocationMode.PROTECTED.name();

    /** Gson 反序列化需要无参构造方法。 */
    public TaskLocationDefinition() {
    }

    public TaskLocationDefinition(String id, String name, ResourceKey<Level> dimension,
                                  BlockPos first, BlockPos second) {
        this(id, name, dimension, first, second, TaskLocationMode.PROTECTED);
    }

    public TaskLocationDefinition(String id, String name, ResourceKey<Level> dimension,
                                  BlockPos first, BlockPos second, TaskLocationMode mode) {
        this.id = id;
        this.name = name;
        this.dimension = Objects.requireNonNull(dimension, "dimension").location().toString();
        this.mode = Objects.requireNonNull(mode, "mode").name();
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        this.min = new Point(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()));
        this.max = new Point(
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));
    }

    /** 在候选配置安装前执行完整校验，避免半份错误配置进入运行内存。 */
    void validate() {
        requireValidId(id, "任务地点");
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("任务地点名称不能为空：" + id);
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(dimension);
        if (dimensionId == null) {
            throw new IllegalStateException("任务地点维度 ID 非法：" + id + " / " + dimension);
        }
        if (min == null || max == null) {
            throw new IllegalStateException("任务地点缺少边界点：" + id);
        }
        min.validate(id, "min");
        max.validate(id, "max");
        if (min.x > max.x || min.y > max.y || min.z > max.z) {
            throw new IllegalStateException("任务地点边界必须按 min 到 max 排列：" + id);
        }
        // Normalize accepted lower-case spellings before the definition is installed or written.
        mode = getMode().name();
    }

    public static void requireValidId(String value, String label) {
        if (value == null || !ID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " ID 非法：" + value);
        }
    }

    public boolean contains(ResourceKey<Level> levelDimension, BlockPos position) {
        if (!enabled || levelDimension == null || position == null
                || !getDimensionKey().equals(levelDimension)) {
            return false;
        }
        return position.getX() >= min.x && position.getX() <= max.x
                && position.getY() >= min.y && position.getY() <= max.y
                && position.getZ() >= min.z && position.getZ() <= max.z;
    }

    /** 同维度地点若共享至少一个方块，就视为重叠。 */
    boolean intersects(TaskLocationDefinition other) {
        if (other == null || !getDimensionKey().equals(other.getDimensionKey())) {
            return false;
        }
        return min.x <= other.max.x && max.x >= other.min.x
                && min.y <= other.max.y && max.y >= other.min.y
                && min.z <= other.max.z && max.z >= other.min.z;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDimension() {
        return dimension;
    }

    public ResourceKey<Level> getDimensionKey() {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension));
    }

    public BlockPos getMin() {
        return new BlockPos(min.x, min.y, min.z);
    }

    public BlockPos getMax() {
        return new BlockPos(max.x, max.y, max.z);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public TaskLocationMode getMode() {
        return TaskLocationMode.parse(mode);
    }

    public boolean isBuildable() {
        return getMode() == TaskLocationMode.BUILDABLE;
    }

    public boolean forcesAdventure() {
        return isEnabled() && getMode() == TaskLocationMode.PROTECTED;
    }

    /** Block edits are fully protected only for authored-scene locations. */
    public boolean protectsBlocks() {
        return isEnabled() && getMode() == TaskLocationMode.PROTECTED;
    }

    /** NPCs and authored decorations remain protected in both active modes. */
    public boolean protectsEntities() {
        return isEnabled();
    }

    /**
     * Returns whether an EconomySystem claim footprint is completely inside this region's X/Z
     * footprint. EconomySystem requires both selection points on one Y level, but its territory
     * permissions ignore the stored Y values and protect the full vertical column.
     */
    public boolean containsClaim(ResourceKey<Level> levelDimension, BlockPos first, BlockPos second) {
        if (!isEnabled() || !isBuildable() || levelDimension == null
                || first == null || second == null || !getDimensionKey().equals(levelDimension)
                || first.getY() != second.getY()) {
            return false;
        }
        int minX = Math.min(first.getX(), second.getX());
        int maxX = Math.max(first.getX(), second.getX());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxZ = Math.max(first.getZ(), second.getZ());
        return minX >= min.x && maxX <= max.x
                && minZ >= min.z && maxZ <= max.z;
    }

    /**
     * Returns whether an EconomySystem claim footprint intersects this story location in X/Z.
     * Stored claim Y is deliberately ignored because EconomySystem applies permissions through
     * the whole vertical column. The two selected points must still share one Y, as required by
     * EconomySystem's claim-wand protocol.
     */
    public boolean intersectsClaim(ResourceKey<Level> levelDimension, BlockPos first, BlockPos second) {
        if (!isEnabled() || levelDimension == null || first == null || second == null
                || !getDimensionKey().equals(levelDimension) || first.getY() != second.getY()) {
            return false;
        }
        int minX = Math.min(first.getX(), second.getX());
        int maxX = Math.max(first.getX(), second.getX());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxZ = Math.max(first.getZ(), second.getZ());
        return minX <= max.x && maxX >= min.x
                && minZ <= max.z && maxZ >= min.z;
    }

    /** JSON 中使用的简单坐标值对象。 */
    private static final class Point {
        private int x;
        private int y;
        private int z;

        private Point() {
        }

        private Point(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private void validate(String locationId, String pointName) {
            if (Math.abs((long) x) > WORLD_COORDINATE_LIMIT
                    || Math.abs((long) z) > WORLD_COORDINATE_LIMIT) {
                throw new IllegalStateException(
                        "任务地点坐标超出世界范围：" + locationId + " / " + pointName);
            }
        }
    }
}
