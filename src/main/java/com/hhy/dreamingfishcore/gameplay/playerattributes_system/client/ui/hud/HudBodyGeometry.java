package com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 左下角人形的纯几何数据，不依赖 Minecraft。
 *
 * <p>模型方块投影后栅格化到一张小网格，每个像素记录自己属于哪个身体部件。描边、部件接缝、
 * 护甲内圈、受击部位和感染蔓延深度都从这张网格推导；渲染层只提交合并后的矩形，
 * 不会把重叠方块的所有棱线画成一团线框。</p>
 */
final class HudBodyGeometry {
    /** 部位编号与 {@code LimbType} 的声明顺序一致。 */
    static final int REGION_HEAD = 0;
    static final int REGION_CHEST = 1;
    static final int REGION_LEGS = 2;
    static final int REGION_FEET = 3;
    static final int REGION_COUNT = 4;

    private static final double MODEL_SCALE = 2.0D;
    private static final double MODEL_YAW = Math.toRadians(-24.0D);
    private static final double MODEL_PITCH = Math.toRadians(-6.0D);
    private static final int GRID_MARGIN = 2;
    /** 感染先沿轮廓蔓延，再向躯干深处推进；少量噪声让推进边缘略显斑驳，而不是整齐的等高线。 */
    private static final float INFECTION_DEPTH_WEIGHT = 0.88F;
    private static final float INFECTION_NOISE_WEIGHT = 0.12F;

    // 按从前到后的顺序栅格化：先写入的部件遮挡后写入的部件。
    private static final int PART_HEAD = 1;
    private static final int PART_NEAR_ARM = 2;
    private static final int PART_BODY = 3;
    private static final int PART_FAR_ARM = 4;
    private static final int PART_NEAR_LEG = 5;
    private static final int PART_NEAR_FOOT = 6;
    private static final int PART_FAR_LEG = 7;
    private static final int PART_FAR_FOOT = 8;
    private static final int[] PART_REGION = {
            -1, REGION_HEAD, REGION_CHEST, REGION_CHEST, REGION_CHEST,
            REGION_LEGS, REGION_FEET, REGION_LEGS, REGION_FEET
    };

    // 固定视角下，这三个面覆盖一个方块的完整投影。
    private static final int[][] VISIBLE_FACES = {
            {0, 4, 6, 2},
            {2, 6, 7, 3},
            {4, 5, 7, 6}
    };

    final int topY;
    final int bottomY;
    final int leftX;
    final int rightX;
    final Span[] halo;
    final Span[] filled;
    final Span[] interior;
    final Span[] seams;
    final Span[][] outline = new Span[REGION_COUNT][];
    final Span[][] innerRing = new Span[REGION_COUNT][];
    final Span[][] regionInterior = new Span[REGION_COUNT][];

    private final int originX;
    private final int originY;
    private final int width;
    private final int height;
    private final byte[] owner;
    /** 每个内部像素被感染覆盖所需的最低感染比例；非内部像素为 2。 */
    private final float[] infectionThreshold;
    private int cachedInfectionKey = -1;
    private Span[] cachedInfection = new Span[0];

    private HudBodyGeometry(OwnerGrid grid) {
        this.originX = grid.originX;
        this.originY = grid.originY;
        this.width = grid.width;
        this.height = grid.height;
        this.owner = grid.owner;
        this.infectionThreshold = new float[width * height];

        boolean[] body = new boolean[width * height];
        int minRow = height;
        int maxRow = -1;
        int minColumn = width;
        int maxColumn = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (owner[y * width + x] != 0) {
                    body[y * width + x] = true;
                    minRow = Math.min(minRow, y);
                    maxRow = Math.max(maxRow, y);
                    minColumn = Math.min(minColumn, x);
                    maxColumn = Math.max(maxColumn, x);
                }
            }
        }
        this.topY = originY + minRow;
        this.bottomY = originY + maxRow + 1;
        this.leftX = originX + minColumn;
        this.rightX = originX + maxColumn + 1;

        boolean[] edge = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                edge[y * width + x] = body[y * width + x] && touches(body, x, y, false);
            }
        }

        int[] depth = computeDepth(body, edge);
        int maxDepth = 1;
        for (int value : depth) {
            maxDepth = Math.max(maxDepth, value);
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (!body[index] || edge[index]) {
                    infectionThreshold[index] = 2.0F;
                    continue;
                }
                float normalizedDepth = maxDepth <= 1 ? 0.0F : (depth[index] - 1) / (float) (maxDepth - 1);
                float threshold = INFECTION_DEPTH_WEIGHT * normalizedDepth
                        + INFECTION_NOISE_WEIGHT * noise(originX + x, originY + y);
                infectionThreshold[index] = Math.min(0.995F, threshold);
            }
        }

        this.halo = collect((x, y) -> !isSet(body, x, y) && touches(body, x, y, true));
        this.filled = collect((x, y) -> isSet(body, x, y));
        this.interior = collect((x, y) -> isSet(body, x, y) && !isSet(edge, x, y));
        this.seams = collect((x, y) -> isSet(body, x, y) && !isSet(edge, x, y)
                && (differentPart(x, y, x + 1, y) || differentPart(x, y, x, y + 1)));
        for (int region = 0; region < REGION_COUNT; region++) {
            final int target = region;
            outline[region] = collect((x, y) -> isSet(edge, x, y) && regionAt(x, y) == target);
            innerRing[region] = collect((x, y) -> isSet(body, x, y) && !isSet(edge, x, y)
                    && regionAt(x, y) == target && touches(edge, x, y, true));
            regionInterior[region] = collect((x, y) -> isSet(body, x, y) && !isSet(edge, x, y)
                    && regionAt(x, y) == target);
        }
    }

    /**
     * 构建一帧人形。
     *
     * @param centerX   人形中心的 GUI 横坐标
     * @param footY     脚底的 GUI 纵坐标
     * @param walkSwing 行走摆臂幅度（弧度），静止时为 0
     */
    static HudBodyGeometry build(int centerX, int footY, double walkSwing) {
        List<Projected> parts = new ArrayList<>(8);
        parts.add(new Projected(PART_HEAD, cuboid(centerX, footY, 0.0D, 23.0D, 0.0D,
                -4.0D, 0.0D, -4.0D, 4.0D, 8.0D, 4.0D, 0.0D, 0.0D)));
        parts.add(new Projected(PART_NEAR_ARM, cuboid(centerX, footY, 6.1D, 23.7D, 0.0D,
                -2.0D, -11.7D, -2.0D, 2.0D, 0.0D, 2.0D, -walkSwing, 0.065D)));
        parts.add(new Projected(PART_BODY, cuboid(centerX, footY, 0.0D, 12.0D, 0.0D,
                -4.0D, 0.0D, -2.0D, 4.0D, 12.0D, 2.0D, 0.0D, 0.0D)));
        parts.add(new Projected(PART_FAR_ARM, cuboid(centerX, footY, -6.1D, 23.7D, 0.0D,
                -2.0D, -11.7D, -2.0D, 2.0D, 0.0D, 2.0D, walkSwing, -0.065D)));
        // 腿部拆成护腿与靴子两段，二者共用髋部轴心，摆动时保持对齐。
        parts.add(new Projected(PART_NEAR_LEG, cuboid(centerX, footY, 2.35D, 12.0D, 0.0D,
                -1.85D, -8.0D, -2.0D, 1.85D, 0.0D, 2.0D, walkSwing, 0.022D)));
        parts.add(new Projected(PART_NEAR_FOOT, cuboid(centerX, footY, 2.35D, 12.0D, 0.0D,
                -1.85D, -12.0D, -2.0D, 1.85D, -8.0D, 2.0D, walkSwing, 0.022D)));
        parts.add(new Projected(PART_FAR_LEG, cuboid(centerX, footY, -2.35D, 12.0D, 0.0D,
                -1.85D, -8.0D, -2.0D, 1.85D, 0.0D, 2.0D, -walkSwing, -0.022D)));
        parts.add(new Projected(PART_FAR_FOOT, cuboid(centerX, footY, -2.35D, 12.0D, 0.0D,
                -1.85D, -12.0D, -2.0D, 1.85D, -8.0D, 2.0D, -walkSwing, -0.022D)));

        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (Projected part : parts) {
            for (double[] point : part.points()) {
                minX = Math.min(minX, point[0]);
                maxX = Math.max(maxX, point[0]);
                minY = Math.min(minY, point[1]);
                maxY = Math.max(maxY, point[1]);
            }
        }
        int originX = (int) Math.floor(minX) - GRID_MARGIN;
        int originY = (int) Math.floor(minY) - GRID_MARGIN;
        int width = (int) Math.ceil(maxX) - originX + GRID_MARGIN + 1;
        int height = (int) Math.ceil(maxY) - originY + GRID_MARGIN + 1;

        OwnerGrid grid = new OwnerGrid(originX, originY, width, height);
        for (Projected part : parts) {
            for (int[] face : VISIBLE_FACES) {
                grid.rasterize(part.points(), face, (byte) part.id());
            }
        }
        return new HudBodyGeometry(grid);
    }

    /** 感染覆盖的像素；按 0.25% 量化缓存，数值不变的帧不会重新扫描网格。 */
    Span[] infection(float ratio) {
        float clamped = HudPalette.clamp01(ratio);
        int key = Math.round(clamped * 400.0F);
        if (key == cachedInfectionKey) {
            return cachedInfection;
        }
        float threshold = key / 400.0F;
        cachedInfectionKey = key;
        cachedInfection = key == 0
                ? new Span[0]
                : collect((x, y) -> x >= 0 && x < width && y >= 0 && y < height
                && infectionThreshold[y * width + x] < threshold);
        return cachedInfection;
    }

    private int[] computeDepth(boolean[] body, boolean[] edge) {
        int[] depth = new int[width * height];
        int[] queue = new int[width * height];
        int head = 0;
        int tail = 0;
        for (int index = 0; index < depth.length; index++) {
            if (edge[index]) {
                queue[tail++] = index;
            } else {
                depth[index] = body[index] ? -1 : 0;
            }
        }
        while (head < tail) {
            int index = queue[head++];
            int x = index % width;
            int y = index / width;
            int next = depth[index] + 1;
            tail = visit(body, depth, queue, tail, x + 1, y, next);
            tail = visit(body, depth, queue, tail, x - 1, y, next);
            tail = visit(body, depth, queue, tail, x, y + 1, next);
            tail = visit(body, depth, queue, tail, x, y - 1, next);
        }
        return depth;
    }

    private int visit(boolean[] body, int[] depth, int[] queue, int tail, int x, int y, int value) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return tail;
        }
        int index = y * width + x;
        if (!body[index] || depth[index] != -1) {
            return tail;
        }
        depth[index] = value;
        queue[tail] = index;
        return tail + 1;
    }

    private boolean differentPart(int x, int y, int otherX, int otherY) {
        if (otherX < 0 || otherY < 0 || otherX >= width || otherY >= height) {
            return false;
        }
        byte other = owner[otherY * width + otherX];
        return other != 0 && other != owner[y * width + x];
    }

    private int regionAt(int x, int y) {
        return PART_REGION[owner[y * width + x]];
    }

    private boolean isSet(boolean[] mask, int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height && mask[y * width + x];
    }

    /** 8 邻域内是否存在与 {@code wanted} 相同状态的像素。 */
    private boolean touches(boolean[] mask, int x, int y, boolean wanted) {
        for (int offsetY = -1; offsetY <= 1; offsetY++) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                if ((offsetX != 0 || offsetY != 0) && isSet(mask, x + offsetX, y + offsetY) == wanted) {
                    return true;
                }
            }
        }
        return false;
    }

    private Span[] collect(PixelTest test) {
        List<Span> rows = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            int runStart = Integer.MIN_VALUE;
            for (int x = 0; x <= width; x++) {
                boolean active = x < width && test.test(x, y);
                if (active && runStart == Integer.MIN_VALUE) {
                    runStart = x;
                } else if (!active && runStart != Integer.MIN_VALUE) {
                    rows.add(new Span(originX + runStart, originY + y, originX + x, originY + y + 1));
                    runStart = Integer.MIN_VALUE;
                }
            }
        }
        return mergeVertically(rows);
    }

    /**
     * 连续多行横向范围相同的像素合并成一个矩形。覆盖的像素完全相同，但每帧提交的四边形少得多。
     */
    private static Span[] mergeVertically(List<Span> rows) {
        List<Span> merged = new ArrayList<>(rows.size());
        Map<Long, Integer> latestByBounds = new HashMap<>();
        for (Span row : rows) {
            long key = (long) row.left() << 32 | row.right() & 0xFFFFFFFFL;
            Integer previousIndex = latestByBounds.get(key);
            if (previousIndex != null) {
                Span previous = merged.get(previousIndex);
                if (previous.bottom() == row.top()) {
                    merged.set(previousIndex, new Span(previous.left(), previous.top(),
                            previous.right(), row.bottom()));
                    continue;
                }
            }
            latestByBounds.put(key, merged.size());
            merged.add(row);
        }
        return merged.toArray(Span[]::new);
    }

    private static double[][] cuboid(int centerX, int footY,
                                     double pivotX, double pivotY, double pivotZ,
                                     double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ,
                                     double rotationX, double rotationZ) {
        double[][] local = {
                {minX, minY, minZ}, {maxX, minY, minZ},
                {minX, maxY, minZ}, {maxX, maxY, minZ},
                {minX, minY, maxZ}, {maxX, minY, maxZ},
                {minX, maxY, maxZ}, {maxX, maxY, maxZ}
        };
        double sinX = Math.sin(rotationX);
        double cosX = Math.cos(rotationX);
        double sinZ = Math.sin(rotationZ);
        double cosZ = Math.cos(rotationZ);
        double[][] projected = new double[local.length][];
        for (int i = 0; i < local.length; i++) {
            double[] point = local[i];
            double rotatedY = point[1] * cosX - point[2] * sinX;
            double rotatedZ = point[1] * sinX + point[2] * cosX;
            double rotatedX = point[0] * cosZ - rotatedY * sinZ;
            rotatedY = point[0] * sinZ + rotatedY * cosZ;
            projected[i] = project(centerX, footY,
                    pivotX + rotatedX, pivotY + rotatedY, pivotZ + rotatedZ);
        }
        return projected;
    }

    private static double[] project(int centerX, int footY, double x, double y, double z) {
        double yawX = x * Math.cos(MODEL_YAW) - z * Math.sin(MODEL_YAW);
        double yawZ = x * Math.sin(MODEL_YAW) + z * Math.cos(MODEL_YAW);
        double pitchY = y * Math.cos(MODEL_PITCH) - yawZ * Math.sin(MODEL_PITCH);
        return new double[]{centerX + yawX * MODEL_SCALE, footY - pitchY * MODEL_SCALE};
    }

    private static float noise(int x, int y) {
        int hash = x * 374761393 + y * 668265263;
        hash = (hash ^ hash >>> 13) * 1274126177;
        hash ^= hash >>> 16;
        return (hash & 0xFFFF) / 65536.0F;
    }

    /** 屏幕坐标下的矩形，右、下边界不包含。 */
    record Span(int left, int top, int right, int bottom) {
    }

    private record Projected(int id, double[][] points) {
    }

    /** 记录每个像素归属部件的栅格；部件按从前到后的顺序写入，已占用的像素不再覆盖。 */
    private static final class OwnerGrid {
        private final int originX;
        private final int originY;
        private final int width;
        private final int height;
        private final byte[] owner;

        private OwnerGrid(int originX, int originY, int width, int height) {
            this.originX = originX;
            this.originY = originY;
            this.width = width;
            this.height = height;
            this.owner = new byte[width * height];
        }

        private void rasterize(double[][] points, int[] face, byte part) {
            double polygonMinY = Double.MAX_VALUE;
            double polygonMaxY = -Double.MAX_VALUE;
            for (int index : face) {
                polygonMinY = Math.min(polygonMinY, points[index][1]);
                polygonMaxY = Math.max(polygonMaxY, points[index][1]);
            }
            int startRow = Math.max(0, (int) Math.floor(polygonMinY) - originY);
            int endRow = Math.min(height - 1, (int) Math.ceil(polygonMaxY) - originY);
            double[] crossings = new double[face.length];

            for (int row = startRow; row <= endRow; row++) {
                double scanY = originY + row + 0.5D;
                int count = 0;
                for (int i = 0; i < face.length; i++) {
                    double[] a = points[face[i]];
                    double[] b = points[face[(i + 1) % face.length]];
                    if ((a[1] <= scanY && b[1] > scanY) || (b[1] <= scanY && a[1] > scanY)) {
                        double progress = (scanY - a[1]) / (b[1] - a[1]);
                        crossings[count++] = a[0] + (b[0] - a[0]) * progress;
                    }
                }
                Arrays.sort(crossings, 0, count);
                for (int i = 0; i + 1 < count; i += 2) {
                    // 以像素中心采样，相邻部件的边界不会互相膨胀。
                    int startColumn = Math.max(0, (int) Math.ceil(crossings[i] - 0.5D) - originX);
                    int endColumn = Math.min(width - 1, (int) Math.floor(crossings[i + 1] - 0.5D) - originX);
                    for (int column = startColumn; column <= endColumn; column++) {
                        int index = row * width + column;
                        if (owner[index] == 0) {
                            owner[index] = part;
                        }
                    }
                }
            }
        }
    }

    @FunctionalInterface
    private interface PixelTest {
        boolean test(int x, int y);
    }
}
