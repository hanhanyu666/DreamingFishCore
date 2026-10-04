package com.hhy.dreamingfishcore.gameplay.raid_system.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.raid_system.RaidService;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 按地图变体切换方块（设计稿 §6.3 的方式②：只切少量方块）。
 *
 * <p>工作方式刻意做得"可逆且好验证"：地图作者站在目标方块前用命令登记一条
 * "在变体 X 生效时，这个方块要变成 Y"，服务端**记下它原本是什么**；
 * 之后应用就是把登记的方块换掉，还原就是把原位放回去。</p>
 *
 * <p>为什么不做结构粘贴（设计稿的方式①）：项目里没有任何 {@code StructureTemplate} 用法，
 * 而粘贴必须在区块加载/玩家进入之前完成、中途失败就是半张图。第一版只做小范围方块状态切换，
 * 每次改动都记原状态，随时可回滚。</p>
 *
 * <p>记录写在 {@code <存档>/dreamingfishcore/raid_variant_blocks.json}（绑对局号）：
 * 重启后仍能正确还原，不会因为"记不得了"而把地图留在被改过的状态。</p>
 */
public final class RaidVariantBlocks {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String SAVE_SUBDIR = "dreamingfishcore";
    private static final String FILE_NAME = "raid_variant_blocks.json";

    /**
     * 一条登记。
     *
     * @param variantId  这个方块属于哪个变体状态（变体 id 需在同一张图内唯一）
     * @param dimension  维度
     * @param x/y/z      方块坐标
     * @param original   原方块 id（用于还原）
     * @param replaced   变体生效时要变成什么
     */
    public record Entry(String variantId, String dimension, int x, int y, int z,
                        String original, String replaced) {

        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("variant", variantId);
            json.addProperty("dimension", dimension);
            JsonArray position = new JsonArray();
            position.add(x);
            position.add(y);
            position.add(z);
            json.add("position", position);
            json.addProperty("original", original);
            json.addProperty("replaced", replaced);
            return json;
        }

        public static Entry fromJson(JsonObject json) {
            if (json == null || !json.has("position") || !json.has("replaced")) {
                return null;
            }
            JsonArray position = json.getAsJsonArray("position");
            return new Entry(json.has("variant") ? json.get("variant").getAsString() : "",
                    json.has("dimension") ? json.get("dimension").getAsString() : "minecraft:overworld",
                    position.get(0).getAsInt(), position.get(1).getAsInt(), position.get(2).getAsInt(),
                    json.has("original") ? json.get("original").getAsString() : "minecraft:air",
                    json.get("replaced").getAsString());
        }
    }

    private static volatile List<Entry> entries = List.of();
    private static volatile long boundRaidId = -1L;

    private RaidVariantBlocks() {
    }

    public static List<Entry> entries() {
        return entries;
    }

    public static synchronized void clear() {
        entries = List.of();
        boundRaidId = -1L;
    }

    private static Path path(MinecraftServer server) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve(SAVE_SUBDIR).resolve(FILE_NAME);
    }

    /** 让登记与当前对局对齐（重启读回、换局丢弃）。 */
    public static synchronized void ensureForCurrentRaid(MinecraftServer server) {
        if (server == null) {
            return;
        }
        var manifest = RaidService.current().orElse(null);
        if (manifest == null) {
            return;
        }
        if (boundRaidId == manifest.raidId()) {
            return;
        }
        boundRaidId = manifest.raidId();
        entries = read(server, manifest.raidId());
    }

    /**
     * 登记一条：把某个方块在变体生效时替换成别的方块。
     *
     * @return 给人看的回执
     */
    public static synchronized String record(MinecraftServer server, ServerLevel level, BlockPos pos,
                                             String variantId, String replacedBlockId) {
        if (server == null || level == null || pos == null) {
            return "参数不完整";
        }
        ensureForCurrentRaid(server);
        Block replaced = resolveBlock(replacedBlockId);
        if (replaced == null) {
            return "方块 id 不存在：" + replacedBlockId;
        }
        String dimension = level.dimension().location().toString();
        BlockState original = level.getBlockState(pos);
        String originalId = BuiltInRegistries.BLOCK.getKey(original.getBlock()).toString();

        // 同一个位置重复登记就覆盖（换主意很常见），不留下两条冲突记录
        List<Entry> next = new ArrayList<>(entries);
        next.removeIf(entry -> entry.dimension().equals(dimension) && entry.pos().equals(pos));
        next.add(new Entry(variantId, dimension, pos.getX(), pos.getY(), pos.getZ(), originalId,
                replacedBlockId.trim().toLowerCase(Locale.ROOT)));
        entries = List.copyOf(next);
        write(server);
        return "已登记：变体 " + variantId + " 生效时，" + pos.toShortString() + " 由 "
                + originalId + " 变为 " + replacedBlockId;
    }

    /**
     * 应用：把指定变体（或"本局选中的变体"）对应的方块换掉。
     *
     * @param variantFilter 非空时只应用这个变体的登记；为空则用本局选中的变体
     * @return 给人看的摘要
     */
    public static synchronized List<String> apply(MinecraftServer server, String variantFilter) {
        List<String> messages = new ArrayList<>();
        if (server == null) {
            messages.add("服务器尚未就绪");
            return messages;
        }
        ensureForCurrentRaid(server);
        if (entries.isEmpty()) {
            messages.add("还没有登记任何变体方块（站在目标方块前用 raid variant_block 登记）");
            return messages;
        }
        java.util.Set<String> wanted = new java.util.TreeSet<>();
        if (variantFilter != null && !variantFilter.isBlank()) {
            wanted.add(variantFilter.trim());
        } else {
            RaidService.current().ifPresent(manifest -> wanted.addAll(manifest.activeVariants()));
        }
        if (wanted.isEmpty()) {
            messages.add("本局没有选中的地图变体（先 raid variants，或指定 raid variants apply <变体id>）");
            return messages;
        }

        int changed = 0;
        int skipped = 0;
        for (Entry entry : entries) {
            if (!wanted.contains(entry.variantId())) {
                continue;
            }
            ServerLevel level = levelOf(server, entry.dimension());
            if (level == null) {
                skipped++;
                continue;
            }
            Block block = resolveBlock(entry.replaced());
            if (block == null) {
                skipped++;
                continue;
            }
            level.setBlockAndUpdate(entry.pos(), block.defaultBlockState());
            changed++;
        }
        messages.add("已按变体 " + wanted + " 切换方块 " + changed + " 个"
                + (skipped > 0 ? "（跳过 " + skipped + " 个）" : ""));
        return messages;
    }

    /** 还原：把每条登记的原方块放回去（不清空登记，之后还能再应用）。 */
    public static synchronized List<String> restore(MinecraftServer server) {
        List<String> messages = new ArrayList<>();
        if (server == null) {
            messages.add("服务器尚未就绪");
            return messages;
        }
        ensureCurrentIfPossible(server);
        if (entries.isEmpty()) {
            messages.add("没有需要还原的变体方块");
            return messages;
        }
        int restored = 0;
        int skipped = 0;
        for (Entry entry : entries) {
            ServerLevel level = levelOf(server, entry.dimension());
            if (level == null) {
                skipped++;
                continue;
            }
            Block block = resolveBlock(entry.original());
            if (block == null) {
                skipped++;
                continue;
            }
            level.setBlockAndUpdate(entry.pos(), block.defaultBlockState());
            restored++;
        }
        messages.add("已还原变体方块 " + restored + " 个" + (skipped > 0 ? "（跳过 " + skipped + " 个）" : ""));
        return messages;
    }

    private static void ensureCurrentIfPossible(MinecraftServer server) {
        if (boundRaidId < 0L) {
            // 没有对局时也要能还原（例如服主手动收拾），所以直接读文件里最后一份记录
            entries = read(server, -1L);
        }
    }

    private static Block resolveBlock(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return null;
        }
        String normalized = blockId.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        ResourceLocation id = ResourceLocation.tryParse(normalized);
        if (id == null) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block == null || block == Blocks.AIR && !normalized.equals("minecraft:air")) {
            return null;
        }
        return block;
    }

    private static ServerLevel levelOf(MinecraftServer server, String dimensionId) {
        try {
            ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.parse(dimensionId));
            return server.getLevel(key);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    // ---------------------------------------------------------------- 持久化

    private static List<Entry> read(MinecraftServer server, long raidId) {
        List<Entry> loaded = new ArrayList<>();
        Path path = path(server);
        if (!Files.isRegularFile(path)) {
            return loaded;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()) {
                return loaded;
            }
            JsonObject json = element.getAsJsonObject();
            if (raidId >= 0L && json.has("raid_id") && json.get("raid_id").getAsLong() != raidId) {
                return loaded;      // 上一局的登记，丢弃
            }
            JsonElement array = json.get("blocks");
            if (array != null && array.isJsonArray()) {
                for (JsonElement item : array.getAsJsonArray()) {
                    if (item.isJsonObject()) {
                        Entry entry = Entry.fromJson(item.getAsJsonObject());
                        if (entry != null) {
                            loaded.add(entry);
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            DreamingFishCore.LOGGER.error("[raid_variant_blocks] 登记读取失败：{}", path, exception);
        }
        return loaded;
    }

    private static void write(MinecraftServer server) {
        var manifest = RaidService.current().orElse(null);
        JsonObject root = new JsonObject();
        root.addProperty("raid_id", manifest == null ? boundRaidId : manifest.raidId());
        JsonArray array = new JsonArray();
        entries.forEach(entry -> array.add(entry.toJson()));
        root.add("blocks", array);
        Path path = path(server);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            DreamingFishCore.LOGGER.error("[raid_variant_blocks] 登记写入失败：{}", path, exception);
        }
    }

    /** 供命令打印。 */
    public static synchronized List<String> describe() {
        List<String> lines = new ArrayList<>();
        lines.add("变体方块登记 " + entries.size() + " 条");
        for (Entry entry : entries) {
            lines.add("  [" + entry.variantId() + "] " + entry.pos().toShortString() + "（"
                    + entry.dimension().replace("minecraft:", "") + "）："
                    + entry.original() + " → " + entry.replaced());
        }
        return lines;
    }
}
