package com.hhy.dreamingfishcore.gameplay.map_region_system;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 地图上要显示的一块区域（**区块粒度**，与 Xaero 的高亮粒度一致）。
 *
 * <p>为什么用品区块矩形而不是多边形：本模组的领地/地点本来就是方块范围，
 * 而 Xaero 的高亮器是按 (chunkX, chunkZ) 询问颜色的——用区块矩形是零转换成本，
 * 且和"领地那样一块块填色"的观感一致。</p>
 *
 * @param kind 用于区分颜色与图例：领地 / 剧情地点 / 外缘（尸潮区）
 */
public record MapRegion(String id, String name, ResourceKey<Level> dimension,
                        int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ,
                        int argb, Kind kind) {

    /** 区域类型。 */
    public enum Kind {
        CLAIM("领地", 0x8038A8FF),
        STORY("剧情地区", 0x80FFCC55),
        FRONTIER("外缘", 0x80FF6666);

        private final String displayName;
        private final int defaultColor;

        Kind(String displayName, int defaultColor) {
            this.displayName = displayName;
            this.defaultColor = defaultColor;
        }

        public String displayName() {
            return displayName;
        }

        public int defaultColor() {
            return defaultColor;
        }
    }

    /**
     * 由方块坐标范围构造（自动换算成区块范围，**正确处理负坐标**）。
     *
     * <p>负坐标必须用 {@code >> 4} 而不是 {@code / 16}：-1 方块属于区块 -1，
     * 而整除会给出 0（经典差一错误）。</p>
     */
    public static MapRegion fromBlockBox(String id, String name, ResourceKey<Level> dimension,
                                         int minX, int minZ, int maxX, int maxZ, Kind kind) {
        int lowX = Math.min(minX, maxX) >> 4;
        int lowZ = Math.min(minZ, maxZ) >> 4;
        int highX = Math.max(minX, maxX) >> 4;
        int highZ = Math.max(minZ, maxZ) >> 4;
        return new MapRegion(id, name, dimension, lowX, lowZ, highX, highZ, kind.defaultColor(), kind);
    }

    /** 这个区块是否落在区域内（含边界）。 */
    public boolean contains(int chunkX, int chunkZ) {
        return chunkX >= minChunkX && chunkX <= maxChunkX
                && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
    }

    /** 区块数量（用于日志统计）。 */
    public int chunkCount() {
        return (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
    }
}
