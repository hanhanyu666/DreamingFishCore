package com.hhy.dreamingfishcore.gameplay.raid_system.anchor;

import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationDefinition;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationManager;
import java.util.Optional;
import net.minecraft.core.BlockPos;

/**
 * 把现有任务地点体系包成锚点系统认识的 {@link RaidAnchorCatalog.ZoneLookup}。
 *
 * <p>刻意不新建"区域"概念：任务地点已经有三维盒子、世界数据、命令、HUD 与边界渲染，
 * 锚点只是引用它的 id。这里做两件事：</p>
 * <ul>
 *   <li>{@code exists}：任务地点在册即算存在；</li>
 *   <li>{@code contains}：按该地点**自己的维度**做盒子判定（并给 1 格外扩容差，避免贴墙的锚点被误判）。</li>
 * </ul>
 *
 * <p>维度匹配交给命令层：放置锚点时要求玩家所在维度与该地点一致，这样接口不必携带维度参数。</p>
 */
public final class RaidAnchorZoneLookup implements RaidAnchorCatalog.ZoneLookup {

    /** 放置/校验容差：锚点贴着墙脚或站在门槛上时不该被判定为越界。 */
    private static final int TOLERANCE = 1;

    public static final RaidAnchorZoneLookup INSTANCE = new RaidAnchorZoneLookup();

    private RaidAnchorZoneLookup() {
    }

    @Override
    public boolean exists(String zoneId) {
        return zoneId != null && TaskLocationManager.getLocation(zoneId).isPresent();
    }

    @Override
    public boolean contains(String zoneId, double x, double y, double z) {
        Optional<TaskLocationDefinition> location = TaskLocationManager.getLocation(zoneId);
        if (location.isEmpty()) {
            return false;
        }
        TaskLocationDefinition definition = location.get();
        BlockPos min = definition.getMin();
        BlockPos max = definition.getMax();
        int blockX = (int) Math.floor(x);
        int blockY = (int) Math.floor(y);
        int blockZ = (int) Math.floor(z);
        return blockX >= min.getX() - TOLERANCE && blockX <= max.getX() + TOLERANCE
                && blockY >= min.getY() - TOLERANCE && blockY <= max.getY() + TOLERANCE
                && blockZ >= min.getZ() - TOLERANCE && blockZ <= max.getZ() + TOLERANCE;
    }

    /** 该区域所属维度的 id（命令层用来判断玩家是否站在同一个维度）。 */
    public static Optional<String> dimensionOf(String zoneId) {
        return TaskLocationManager.getLocation(zoneId)
                .map(definition -> definition.getDimensionKey().location().toString());
    }
}
