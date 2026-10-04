package com.hhy.dreamingfishcore.gameplay.raid_system.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.raid_system.RaidManifest;
import com.hhy.dreamingfishcore.gameplay.raid_system.anchor.RaidAnchor;
import com.hhy.dreamingfishcore.gameplay.raid_system.anchor.RaidAnchorService;
import com.hhy.dreamingfishcore.gameplay.raid_system.anchor.RaidAnchorZoneLookup;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

/**
 * 把算好的战利品清单**真的放进世界**（设计稿 §13 第 11~14 步的消费端）。
 *
 * <p>安全规则（都是"宁可少放，不可吞东西"）：</p>
 * <ol>
 *   <li><b>只在空容器里填</b>：容器非空就跳过并记问题，绝不覆盖玩家已有的东西；</li>
 *   <li><b>锚点位置允许一格误差</b>：锚点是准心打在方块表面记录的（贴面 0.02 格），
 *       所以从坐标本身与它的六个邻格里找容器，顺序固定以保证可复现；</li>
 *   <li><b>幂等</b>：填过的容器逐个记账（锚点、维度、坐标、放了什么）写进存档，
 *       重启或反复执行都不会翻倍；</li>
 *   <li><b>清理要核实</b>：只有容器里装的仍是"当初我们放进去的那批"才允许清空，
 *       玩家动过就跳过并记日志。</li>
 * </ol>
 */
public final class RaidLootApplier {

    private static final String SAVE_SUBDIR = "dreamingfishcore";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** 放进容器里的一摞物品（只记 id 与数量，便于单测与 JSON 持久化）。 */
    public record AppliedStack(String itemId, int count) {

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("item", itemId);
            json.addProperty("count", count);
            return json;
        }

        public static AppliedStack fromJson(JsonObject json) {
            if (json == null || !json.has("item")) {
                return null;
            }
            return new AppliedStack(json.get("item").getAsString(),
                    json.has("count") ? json.get("count").getAsInt() : 1);
        }
    }

    /**
     * 一个被填过的容器。
     *
     * @param replacedItems 覆盖前容器里原有的东西（没覆盖则为空）。
     *                      记下来是为了 {@code raid end} 时能**把地图还原成原样**，
     *                      而不是把服主原本放在箱子里的东西永久吞掉。
     */
    public record AppliedContainer(String anchorId, String dimension, int x, int y, int z,
                                   List<AppliedStack> items, List<AppliedStack> replacedItems) {

        public AppliedContainer {
            items = items == null ? List.of() : List.copyOf(items);
            replacedItems = replacedItems == null ? List.of() : List.copyOf(replacedItems);
        }

        public boolean overwroteExistingContent() {
            return !replacedItems.isEmpty();
        }

        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("anchor", anchorId);
            json.addProperty("dimension", dimension);
            JsonArray position = new JsonArray();
            position.add(x);
            position.add(y);
            position.add(z);
            json.add("position", position);
            JsonArray items = new JsonArray();
            this.items.forEach(stack -> items.add(stack.toJson()));
            json.add("items", items);
            if (!replacedItems.isEmpty()) {
                JsonArray replaced = new JsonArray();
                this.replacedItems.forEach(stack -> replaced.add(stack.toJson()));
                json.add("replaced_items", replaced);
            }
            return json;
        }

        public static AppliedContainer fromJson(JsonObject json) {
            if (json == null || !json.has("position")) {
                return null;
            }
            JsonArray position = json.getAsJsonArray("position");
            List<AppliedStack> items = readStacks(json.get("items"));
            List<AppliedStack> replaced = readStacks(json.get("replaced_items"));
            return new AppliedContainer(json.has("anchor") ? json.get("anchor").getAsString() : "",
                    json.has("dimension") ? json.get("dimension").getAsString() : "",
                    position.get(0).getAsInt(), position.get(1).getAsInt(), position.get(2).getAsInt(),
                    items, replaced);
        }

        private static List<AppliedStack> readStacks(JsonElement element) {
            List<AppliedStack> stacks = new ArrayList<>();
            if (element != null && element.isJsonArray()) {
                for (JsonElement item : element.getAsJsonArray()) {
                    if (item.isJsonObject()) {
                        AppliedStack stack = AppliedStack.fromJson(item.getAsJsonObject());
                        if (stack != null) {
                            stacks.add(stack);
                        }
                    }
                }
            }
            return stacks;
        }
    }

    private RaidLootApplier() {
    }

    /**
     * 是否允许清空这个容器：必须与当初放进去的东西**完全一致**（数量按物品汇总比较）。
     *
     * <p>纯函数，单独测：玩家如果动过箱子（拿走或塞了别的），就不动它。</p>
     */
    public static boolean canClear(List<AppliedStack> expected, List<AppliedStack> current) {
        Map<String, Integer> expectedCounts = count(expected);
        Map<String, Integer> currentCounts = count(current);
        return expectedCounts.equals(currentCounts);
    }

    private static Map<String, Integer> count(List<AppliedStack> stacks) {
        Map<String, Integer> counts = new TreeMap<>();
        if (stacks != null) {
            for (AppliedStack stack : stacks) {
                if (stack != null && stack.itemId() != null && stack.count() > 0) {
                    counts.merge(stack.itemId(), stack.count(), Integer::sum);
                }
            }
        }
        return counts;
    }

    /**
     * 把计划填进世界。
     *
     * @param overwriteNonEmpty 容器里已有东西时是否清空后填入（服主可关；
     *                          开启时会把原有内容记账，结束时还原）
     * @return 给人看的摘要（命令直接回显）
     */
    public static List<String> apply(MinecraftServer server, RaidManifest manifest,
                                     Map<String, RaidLootPlanner.ZonePlan> plan,
                                     boolean overwriteNonEmpty) {
        List<String> messages = new ArrayList<>();
        if (server == null || manifest == null || plan == null || plan.isEmpty()) {
            messages.add("没有可应用的计划");
            return messages;
        }
        RaidAnchorService.ensureLoaded(server);

        List<AppliedContainer> applied = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        int filled = 0;
        int items = 0;
        int overwritten = 0;

        for (RaidLootPlanner.ZonePlan zonePlan : plan.values()) {
            for (LootAllocator.PointAllocation point : zonePlan.allocation().points()) {
                if (point.itemIds().isEmpty()) {
                    continue;
                }
                Optional<RaidAnchor> anchor = RaidAnchorService.catalog().byId(point.anchorId());
                if (anchor.isEmpty()) {
                    problems.add("锚点已不存在，跳过：" + point.anchorId());
                    continue;
                }
                ServerLevel level = levelOf(server, anchor.get());
                if (level == null) {
                    problems.add("找不到锚点所在维度，跳过：" + point.anchorId());
                    continue;
                }
                Optional<Container> container = findContainer(level, anchor.get());
                if (container.isEmpty()) {
                    problems.add("锚点附近没有可用的容器，跳过：" + point.anchorId());
                    continue;
                }
                Container target = container.get();
                List<AppliedStack> replaced = List.of();
                if (!target.isEmpty()) {
                    if (!overwriteNonEmpty) {
                        problems.add("容器里已经有东西，跳过（未开启覆盖）：" + point.anchorId());
                        continue;
                    }
                    // 覆盖前先把原有内容记下来，结束时能还回去（不吞服主原本放在箱子里的东西）
                    replaced = currentStacks(target);
                    target.clearContent();
                    target.setChanged();
                }

                List<AppliedStack> placed = fill(target, point.itemIds(), problems, point.anchorId());
                if (placed.isEmpty()) {
                    continue;
                }
                BlockPos pos = containerPos(level, anchor.get());
                applied.add(new AppliedContainer(point.anchorId(), level.dimension().location().toString(),
                        pos.getX(), pos.getY(), pos.getZ(), placed, replaced));
                filled++;
                items += placed.size();
                if (!replaced.isEmpty()) {
                    overwritten++;
                }
            }
        }

        writeRecord(server, manifest.raidId(), applied);
        messages.add("已填充容器 " + filled + " 个，放入物品 " + items + " 件"
                + (overwritten > 0 ? "（其中 " + overwritten + " 个覆盖了原有内容，结束时会还原）" : ""));
        if (filled == 0 && !plan.isEmpty()) {
            messages.add("  一个都没填上——检查锚点位置是否对着容器（锚点是准心打在方块表面记的）");
        }
        problems.stream().limit(10).forEach(problem -> messages.add("  " + problem));
        if (problems.size() > 10) {
            messages.add("  …（还有 " + (problems.size() - 10) + " 条问题）");
        }
        DreamingFishCore.LOGGER.info("[raid_loot] 填充完成：容器 {} 个，物品 {} 件，问题 {} 条",
                filled, items, problems.size());
        return messages;
    }

    /**
     * 清空本局填进去的东西（对局结束时调用）。
     *
     * <p>逐个核实内容是否仍与当初一致：玩家动过的容器一律跳过。</p>
     */
    public static List<String> clear(MinecraftServer server, long raidId) {
        List<String> messages = new ArrayList<>();
        if (server == null) {
            messages.add("服务器尚未就绪");
            return messages;
        }
        List<AppliedContainer> recorded = readRecord(server, raidId);
        if (recorded.isEmpty()) {
            messages.add("本局没有填充记录，无需清理");
            return messages;
        }

        int cleared = 0;
        int skipped = 0;
        boolean restored = false;
        for (AppliedContainer container : recorded) {
            ServerLevel level = levelOf(server, container.dimension());
            if (level == null) {
                skipped++;
                continue;
            }
            if (!(level.getBlockEntity(container.pos()) instanceof Container target)) {
                skipped++;
                continue;
            }
            if (!canClear(container.items(), currentStacks(target))) {
                messages.add("  容器内容已被改动，跳过清理：" + container.anchorId());
                skipped++;
                continue;
            }
            target.clearContent();
            // 覆盖过的容器：把原有内容还回去，让地图恢复成开局前的样子
            if (container.overwroteExistingContent()) {
                int slot = 0;
                for (AppliedStack stack : container.replacedItems()) {
                    if (slot >= target.getContainerSize()) {
                        messages.add("  原有内容装不回容器（容量变小？），已跳过：" + container.anchorId());
                        break;
                    }
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(stack.itemId()));
                    if (item == null || item == net.minecraft.world.item.Items.AIR) {
                        continue;
                    }
                    target.setItem(slot++, new ItemStack(item, Math.max(1, stack.count())));
                    restored = true;
                }
            }
            target.setChanged();
            cleared++;
        }

        Path path = recordPath(server, raidId);
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            DreamingFishCore.LOGGER.error("[raid_loot] 删除填充记录失败：{}", path, exception);
        }
        messages.add("已清理容器 " + cleared + " 个（跳过 " + skipped + " 个）"
                + (restored ? "，其中覆盖过的已还原原有内容" : ""));
        return messages;
    }

    /** 把物品 id 列表放进容器；装不下就记问题，不丢不覆盖。 */
    private static List<AppliedStack> fill(Container container, List<String> itemIds,
                                           List<String> problems, String anchorId) {
        List<AppliedStack> placed = new ArrayList<>();
        int slot = 0;
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (String itemId : itemIds) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                problems.add("物品 id 不存在，跳过：" + itemId);
                continue;
            }
            int maxStack = Math.max(1, new ItemStack(item).getMaxStackSize());
            int remaining = merged.getOrDefault(itemId, 0) + 1;
            merged.put(itemId, remaining);
            if (remaining > maxStack) {
                // 超过一组：另起一槽
                merged.put(itemId, 1);
                remaining = 1;
            }
            boolean stacked = false;
            for (int index = 0; index < slot && index < container.getContainerSize(); index++) {
                ItemStack existing = container.getItem(index);
                if (!existing.isEmpty() && existing.is(item) && existing.getCount() < maxStack) {
                    existing.grow(1);
                    container.setChanged();
                    stacked = true;
                    break;
                }
            }
            if (stacked) {
                continue;
            }
            if (slot >= container.getContainerSize()) {
                problems.add("容器装不下，剩余物品未放入：" + anchorId + "（已放 " + placed.size() + " 件）");
                break;
            }
            container.setItem(slot, new ItemStack(item, 1));
            slot++;
        }
        container.setChanged();
        merged.forEach((itemId, count) -> placed.add(new AppliedStack(itemId, count)));
        return placed;
    }

    private static List<AppliedStack> currentStacks(Container container) {
        Map<String, Integer> counts = new TreeMap<>();
        for (int index = 0; index < container.getContainerSize(); index++) {
            ItemStack stack = container.getItem(index);
            if (!stack.isEmpty()) {
                counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                        stack.getCount(), Integer::sum);
            }
        }
        List<AppliedStack> stacks = new ArrayList<>();
        counts.forEach((itemId, count) -> stacks.add(new AppliedStack(itemId, count)));
        return stacks;
    }

    /** 锚点所在维度：锚点不存维度，用它的区域所属维度。 */
    private static ServerLevel levelOf(MinecraftServer server, RaidAnchor anchor) {
        return RaidAnchorZoneLookup.dimensionOf(anchor.zone())
                .map(id -> levelOf(server, id))
                .orElse(null);
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

    /** 锚点坐标本身 + 六个邻格里找容器；顺序固定（先自身，再 -X/+X/-Y/+Y/-Z/+Z）保证可复现。 */
    private static Optional<Container> findContainer(ServerLevel level, RaidAnchor anchor) {
        for (BlockPos pos : candidatePositions(anchor)) {
            if (level.getBlockEntity(pos) instanceof Container container) {
                return Optional.of(container);
            }
        }
        return Optional.empty();
    }

    private static BlockPos containerPos(ServerLevel level, RaidAnchor anchor) {
        for (BlockPos pos : candidatePositions(anchor)) {
            if (level.getBlockEntity(pos) instanceof Container) {
                return pos;
            }
        }
        return new BlockPos(anchor.blockX(), anchor.blockY(), anchor.blockZ());
    }

    private static List<BlockPos> candidatePositions(RaidAnchor anchor) {
        BlockPos base = new BlockPos(anchor.blockX(), anchor.blockY(), anchor.blockZ());
        return List.of(base, base.west(), base.east(), base.below(), base.above(), base.north(), base.south());
    }

    // ---------------------------------------------------------------- 持久化

    private static Path recordPath(MinecraftServer server, long raidId) {
        return server.getWorldPath(LevelResource.ROOT).resolve(SAVE_SUBDIR)
                .resolve("raid_applied_" + raidId + ".json");
    }

    private static void writeRecord(MinecraftServer server, long raidId, List<AppliedContainer> applied) {
        JsonObject root = new JsonObject();
        root.addProperty("raid_id", raidId);
        JsonArray array = new JsonArray();
        applied.forEach(container -> array.add(container.toJson()));
        root.add("containers", array);
        Path path = recordPath(server, raidId);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            DreamingFishCore.LOGGER.error("[raid_loot] 填充记录写入失败：{}", path, exception);
        }
    }

    public static List<AppliedContainer> readRecord(MinecraftServer server, long raidId) {
        List<AppliedContainer> containers = new ArrayList<>();
        Path path = recordPath(server, raidId);
        if (!Files.isRegularFile(path)) {
            return containers;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()
                    || !element.getAsJsonObject().has("containers")) {
                return containers;
            }
            for (JsonElement item : element.getAsJsonObject().getAsJsonArray("containers")) {
                if (!item.isJsonObject()) {
                    continue;
                }
                AppliedContainer container = AppliedContainer.fromJson(item.getAsJsonObject());
                if (container != null) {
                    containers.add(container);
                }
            }
        } catch (IOException | RuntimeException exception) {
            DreamingFishCore.LOGGER.error("[raid_loot] 填充记录读取失败：{}", path, exception);
        }
        return containers;
    }
}
