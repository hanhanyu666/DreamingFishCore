package com.hhy.dreamingfishcore.gameplay.raid_system.map;

import com.hhy.dreamingfishcore.DreamingFishCore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;

/**
 * 区域块（"地图零件"）的抓取、放置与重置——随机地图的第一步。
 *
 * <p>为什么用原版 {@link StructureTemplate} 而不是自己读写方块：它能连**方块实体**
 * （箱子、刷怪笼、告示牌、熔炉…）一起存取，放置时还会正确处理光照与邻居更新。
 * 自己写这两块必然出问题（箱子变空壳、光照错乱、红石乱跳）。</p>
 *
 * <p>存盘位置与其他世界层数据一致：{@code <存档>/dreamingfishcore/regions/<名字>.nbt}。
 * 放在存档里而不是数据包里，是因为这是**服主自己搭的东西**，要能直接备份/复制/分享。</p>
 *
 * <p>用法设想（随机地图路线）：在专用维度里搭好一间"房间"，抓取成零件；
 * 每局由布局规划器挑选零件、按坐标放置；重置就是把零件重新放一遍——
 * 因为那片区域归系统所有，不需要记录原状态。</p>
 */
public final class RaidRegionService {

    /** 单次抓取的体积上限，防止手滑框到半张图（32³ = 32768 方块）。 */
    public static final int MAX_VOLUME = 32 * 32 * 32;
    public static final int MAX_EDGE = 128;

    private static final String SUBDIR = "dreamingfishcore";
    private static final String REGION_DIR = "regions";

    private RaidRegionService() {
    }

    private static Path directory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(SUBDIR).resolve(REGION_DIR);
    }

    private static Path file(MinecraftServer server, String name) {
        return directory(server).resolve(safeName(name) + ".nbt");
    }

    /** 只允许字母数字下划线与连字符，避免路径穿越。 */
    static String safeName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
    }

    /** 抓取一片区域存成零件。 */
    public static List<String> capture(MinecraftServer server, ServerLevel level, String name,
                                       BlockPos from, BlockPos to, boolean withEntities) {
        List<String> messages = new ArrayList<>();
        if (server == null || level == null || from == null || to == null) {
            messages.add("参数不完整");
            return messages;
        }
        String safe = safeName(name);
        if (safe.isEmpty()) {
            messages.add("名字不能为空（只允许字母、数字、下划线与连字符）");
            return messages;
        }
        int sizeX = Math.abs(to.getX() - from.getX()) + 1;
        int sizeY = Math.abs(to.getY() - from.getY()) + 1;
        int sizeZ = Math.abs(to.getZ() - from.getZ()) + 1;
        int volume = sizeX * sizeY * sizeZ;
        if (sizeX > MAX_EDGE || sizeY > MAX_EDGE || sizeZ > MAX_EDGE) {
            messages.add("边长上限 " + MAX_EDGE + "，你框的是 " + sizeX + "×" + sizeY + "×" + sizeZ);
            return messages;
        }
        if (volume > MAX_VOLUME) {
            messages.add("体积上限 " + MAX_VOLUME + " 方块，你框了 " + volume
                    + " 个（" + sizeX + "×" + sizeY + "×" + sizeZ + "）");
            return messages;
        }

        BlockPos origin = new BlockPos(Math.min(from.getX(), to.getX()),
                Math.min(from.getY(), to.getY()), Math.min(from.getZ(), to.getZ()));
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, origin, new Vec3i(sizeX, sizeY, sizeZ), withEntities, null);
        template.setAuthor("dreamingfishcore");

        Path path = file(server, safe);
        try {
            Files.createDirectories(path.getParent());
            CompoundTag tag = template.save(new CompoundTag());
            NbtIo.writeCompressed(tag, path);
        } catch (IOException | RuntimeException exception) {
            DreamingFishCore.LOGGER.error("[raid_region] 抓取失败：{}", path, exception);
            messages.add("抓取失败：" + exception.getMessage());
            return messages;
        }
        messages.add("已抓取零件 " + safe + "：" + sizeX + "×" + sizeY + "×" + sizeZ
                + "（" + volume + " 方块，" + (withEntities ? "含实体" : "不含实体")
                + "），原点 " + origin.toShortString() + "，存放于 " + path);
        return messages;
    }

    /** 把零件放到指定坐标（覆盖目标区域）。 */
    public static List<String> place(MinecraftServer server, ServerLevel level, String name,
                                     BlockPos pos, boolean withEntities) {
        List<String> messages = new ArrayList<>();
        if (server == null || level == null || pos == null) {
            messages.add("参数不完整");
            return messages;
        }
        StructureTemplate template = read(server, name, level);
        if (template == null) {
            messages.add("没有找到零件 " + safeName(name) + "（先 raid region capture，或 raid region list 看看有什么）");
            return messages;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setIgnoreEntities(!withEntities);
        template.placeInWorld(level, pos, pos, settings, RandomSource.create(0L), Block.UPDATE_ALL);
        Vec3i size = template.getSize();
        messages.add("已放置零件 " + safeName(name) + " 于 " + pos.toShortString()
                + "，尺寸 " + size.getX() + "×" + size.getY() + "×" + size.getZ());
        return messages;
    }

    /**
     * 重置一片区域：先清成空气再放回零件。
     *
     * <p>专用维度的竞技场靠这个回到"干净"状态——因为是系统所有的区域，
     * 不需要像变体方块那样记录原状态。</p>
     */
    public static List<String> reset(MinecraftServer server, ServerLevel level, String name,
                                     BlockPos pos, boolean withEntities) {
        List<String> messages = new ArrayList<>();
        StructureTemplate template = read(server, name, level);
        if (template == null) {
            messages.add("没有找到零件 " + safeName(name));
            return messages;
        }
        Vec3i size = template.getSize();
        clear(level, pos, new BlockPos(pos.getX() + size.getX() - 1,
                pos.getY() + size.getY() - 1, pos.getZ() + size.getZ() - 1));
        messages.add("已清空 " + size.getX() + "×" + size.getY() + "×" + size.getZ() + " 区域");
        messages.addAll(place(server, level, name, pos, withEntities));
        return messages;
    }

    /** 把一片区域清成空气（做"清空竞技场"用）。 */
    public static int clear(ServerLevel level, BlockPos from, BlockPos to) {
        if (level == null || from == null || to == null) {
            return 0;
        }
        int cleared = 0;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS);
                cleared++;
            }
        }
        return cleared;
    }

    /** 列出已抓取的零件（名字 + 体积），按名字排序。 */
    public static List<String> list(MinecraftServer server, ServerLevel level) {
        List<String> lines = new ArrayList<>();
        Path directory = directory(server);
        if (!Files.isDirectory(directory)) {
            lines.add("还没有任何零件（站在建筑前用 raid region capture <名字> 抓取）");
            return lines;
        }
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".nbt")).forEach(files::add);
        } catch (IOException exception) {
            lines.add("目录读取失败：" + exception.getMessage());
            return lines;
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        lines.add("零件 " + files.size() + " 个（" + directory + "）");
        for (Path path : files) {
            String name = path.getFileName().toString().replace(".nbt", "");
            StructureTemplate template = read(server, name, level);
            if (template == null) {
                lines.add("  " + name + "（读取失败）");
                continue;
            }
            Vec3i size = template.getSize();
            lines.add("  " + name + "：" + size.getX() + "×" + size.getY() + "×" + size.getZ()
                    + "（" + (size.getX() * size.getY() * size.getZ()) + " 方块）");
        }
        return lines;
    }

    private static StructureTemplate read(MinecraftServer server, String name, ServerLevel level) {
        String safe = safeName(name);
        if (server == null || safe.isEmpty()) {
            return null;
        }
        Path path = file(server, safe);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            StructureTemplate template = new StructureTemplate();
            template.load(level.holderLookup(Registries.BLOCK), tag);
            return template;
        } catch (IOException | RuntimeException exception) {
            DreamingFishCore.LOGGER.error("[raid_region] 读取零件失败：{}", path, exception);
            return null;
        }
    }

    /** 该维度是否可作为竞技场（仅作提示：主世界也能用，只是重置会覆盖真实地形）。 */
    public static boolean isOverworld(Level level) {
        return level != null && level.dimension() == Level.OVERWORLD;
    }
}
