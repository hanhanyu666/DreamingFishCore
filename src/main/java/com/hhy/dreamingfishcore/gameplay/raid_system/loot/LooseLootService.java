package com.hhy.dreamingfishcore.gameplay.raid_system.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.raid_system.RaidManifest;
import com.hhy.dreamingfishcore.gameplay.raid_system.RaidService;
import com.hhy.dreamingfishcore.gameplay.raid_system.RaidStatsLog;
import com.hhy.dreamingfishcore.gameplay.raid_system.anchor.RaidAnchor;
import com.hhy.dreamingfishcore.gameplay.raid_system.anchor.RaidAnchorService;
import com.hhy.dreamingfishcore.gameplay.raid_system.anchor.RaidAnchorType;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

/**
 * 露天物品节点的世界侧：生成、落盘、标记、拾取（设计稿 §8）。
 *
 * <p>这一版是**服务端权威 + 自动拾取**，刻意不新增自定义包、不动协议版本：</p>
 * <ul>
 *   <li>节点生成：用锚点系统的 {@code LOOSE_LOOT} 锚点（每放一个点就有一个节点），
 *       物品由 {@link LooseLootPlanner} 按类别标签与价值上限抽；</li>
 *   <li>显示：未拾取的节点用服务端粒子轻轻标一下（与撤离点标识同一思路）；</li>
 *   <li>拾取：生存/冒险玩家走到节点 {@value #PICKUP_RADIUS} 格内即自动拾取，
 *       服务端先判"是否已拾取/距离/背包"，再给物品并标记 picked；</li>
 *   <li>落盘：{@code <存档>/dreamingfishcore/raid_loose_loot_<对局号>.json}，含 picked 标记，
 *       所以重启后不会"又长出来"。</li>
 * </ul>
 *
 * <p>真正"世界里的物品模型 + 附近物品栏 + 手动拾取"需要 S2C/C2S 新包（届时协议提升到 0.32.0），
 * 那一刀单独做；本类是那一步的服务端基础。</p>
 */
public final class LooseLootService {

    /** 自动拾取半径（格）。想调就挪进 RaidConfig。 */
    public static final double PICKUP_RADIUS = 1.5D;
    /** 粒子标识的节流。 */
    private static final int MARKER_INTERVAL_TICKS = 10;
    private static final String SAVE_SUBDIR = "dreamingfishcore";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** 一个已生成的节点。 */
    public record Node(String anchorId, String itemId, double x, double y, double z, float yaw,
                       boolean picked) {

        public Node withPicked() {
            return new Node(anchorId, itemId, x, y, z, yaw, true);
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("anchor", anchorId);
            json.addProperty("item", itemId);
            JsonArray position = new JsonArray();
            position.add(x);
            position.add(y);
            position.add(z);
            json.add("position", position);
            json.addProperty("yaw", yaw);
            json.addProperty("picked", picked);
            return json;
        }

        public static Node fromJson(JsonObject json) {
            if (json == null || !json.has("item") || !json.has("position")) {
                return null;
            }
            JsonArray position = json.getAsJsonArray("position");
            return new Node(json.has("anchor") ? json.get("anchor").getAsString() : "",
                    json.get("item").getAsString(),
                    position.get(0).getAsDouble(), position.get(1).getAsDouble(),
                    position.get(2).getAsDouble(),
                    json.has("yaw") ? json.get("yaw").getAsFloat() : 0.0F,
                    json.has("picked") && json.get("picked").getAsBoolean());
        }
    }

    private static volatile List<Node> nodes = List.of();
    private static volatile long boundRaidId = -1L;
    private static int markerTick;

    private LooseLootService() {
    }

    public static List<Node> nodes() {
        return nodes;
    }

    public static synchronized void clear() {
        nodes = List.of();
        boundRaidId = -1L;
    }

    private static Path path(MinecraftServer server, long raidId) {
        return server.getWorldPath(LevelResource.ROOT).resolve(SAVE_SUBDIR)
                .resolve("raid_loose_loot_" + raidId + ".json");
    }

    /** 确保节点与当前对局对齐：换局重来、同局读回，没生成过就生成。 */
    public static synchronized void ensureForCurrentRaid(MinecraftServer server) {
        RaidManifest manifest = RaidService.current().orElse(null);
        if (server == null || manifest == null) {
            clear();
            return;
        }
        if (boundRaidId == manifest.raidId()) {
            return;
        }
        boundRaidId = manifest.raidId();
        List<Node> loaded = read(server, manifest.raidId());
        if (!loaded.isEmpty()) {
            nodes = loaded;
            DreamingFishCore.LOGGER.info("[raid_loose] 读回本局露天物品节点 {} 个（已拾取 {}）",
                    loaded.size(), loaded.stream().filter(Node::picked).count());
            return;
        }
        nodes = generate(server, manifest);
        write(server, manifest.raidId());
        DreamingFishCore.LOGGER.info("[raid_loose] 生成露天物品节点 {} 个", nodes.size());
    }

    /** 用 LOOSE_LOOT 锚点与物品池生成节点。 */
    private static List<Node> generate(MinecraftServer server, RaidManifest manifest) {
        RaidAnchorService.ensureLoaded(server);
        RaidLootService.ensureLoaded(server);

        List<LooseLootPlanner.Spot> spots = new ArrayList<>();
        for (RaidAnchor anchor : RaidAnchorService.catalog().byType(RaidAnchorType.LOOSE_LOOT)) {
            if (!anchor.enabled()) {
                continue;
            }
            spots.add(new LooseLootPlanner.Spot(anchor.id(), anchor.group(), anchor.weight(),
                    anchor.x(), anchor.y(), anchor.z(), anchor.rotation().y(),
                    anchor.qualityMultiplier(), new java.util.TreeSet<>(anchor.tags())));
        }
        if (spots.isEmpty()) {
            return List.of();
        }
        LooseLootPlanner.Plan plan = LooseLootPlanner.plan(spots, RaidLootService.items(),
                manifest.randomFor("loose_loot"), Map.of(), spots.size());

        List<Node> generated = new ArrayList<>();
        plan.nodes().forEach(node -> generated.add(new Node(node.anchorId(), node.itemId(),
                node.x(), node.y(), node.z(), node.yaw(), false)));
        return List.copyOf(generated);
    }

    /**
     * 每 tick：自动拾取 + 给未拾取的节点喷粒子。
     *
     * <p>拾取判定复用 {@link LooseLootPlanner#canPickup}，所以"已拾取/太远/背包满"的规则
     * 与将来接客户端包时完全一致，不会出现两套口径。</p>
     */
    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        ensureForCurrentRaid(server);
        if (nodes.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (int index = 0; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            if (node.picked()) {
                continue;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.isCreative() || player.isSpectator()) {
                    continue;       // 与其它系统口径一致：只有生存/冒险能捡
                }
                ServerLevel level = server.overworld();
                if (player.level() != level) {
                    continue;
                }
                double distance = Math.sqrt(Math.pow(player.getX() - node.x(), 2.0D)
                        + Math.pow(player.getY() - node.y(), 2.0D)
                        + Math.pow(player.getZ() - node.z(), 2.0D));
                boolean full = player.getInventory().getFreeSlot() < 0;
                LooseLootPlanner.PickupCheck check =
                        LooseLootPlanner.canPickup(false, distance, false, full);
                if (!check.allowed()) {
                    continue;
                }
                if (give(player, node)) {
                    nodes = replace(index, node.withPicked());
                    changed = true;
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "拾取：" + displayName(node.itemId())), true);
                    RaidService.current().ifPresent(manifest -> RaidStatsLog.looted(server, manifest,
                            player, node.anchorId(), node.itemId()));
                }
                break;      // 一个节点一 tick 只给一个人
            }
        }
        if (changed) {
            RaidService.current().ifPresent(manifest -> write(server, manifest.raidId()));
        }
        emitMarkers(server);
    }

    private static List<Node> replace(int index, Node node) {
        List<Node> next = new ArrayList<>(nodes);
        next.set(index, node);
        return List.copyOf(next);
    }

    /** 真的把物品给玩家：背包放不下就丢在脚下（与原版一致），返回是否给成功。 */
    private static boolean give(ServerPlayer player, Node node) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(node.itemId()));
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            DreamingFishCore.LOGGER.warn("[raid_loose] 物品 id 不存在，跳过：{}", node.itemId());
            return false;
        }
        player.getInventory().placeItemBackInInventory(new ItemStack(item, 1));
        return true;
    }

    private static String displayName(String itemId) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        return item == null ? itemId : new ItemStack(item).getHoverName().getString();
    }

    /** 未拾取的节点用粒子轻轻标一下（已拾取就不亮了，玩家一眼知道还剩什么）。 */
    private static void emitMarkers(MinecraftServer server) {
        if (++markerTick < MARKER_INTERVAL_TICKS) {
            return;
        }
        markerTick = 0;
        ServerLevel level = server.overworld();
        for (Node node : nodes) {
            if (node.picked()) {
                continue;
            }
            level.sendParticles(ParticleTypes.END_ROD, node.x(), node.y() + 0.35D, node.z(),
                    1, 0.1D, 0.05D, 0.1D, 0.0D);
        }
    }

    // ---------------------------------------------------------------- 持久化

    private static List<Node> read(MinecraftServer server, long raidId) {
        List<Node> loaded = new ArrayList<>();
        Path path = path(server, raidId);
        if (!Files.isRegularFile(path)) {
            return loaded;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()) {
                return loaded;
            }
            JsonElement array = element.getAsJsonObject().get("nodes");
            if (array == null || !array.isJsonArray()) {
                return loaded;
            }
            for (JsonElement item : array.getAsJsonArray()) {
                if (item.isJsonObject()) {
                    Node node = Node.fromJson(item.getAsJsonObject());
                    if (node != null) {
                        loaded.add(node);
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            DreamingFishCore.LOGGER.error("[raid_loose] 节点读取失败：{}", path, exception);
        }
        return loaded;
    }

    private static void write(MinecraftServer server, long raidId) {
        JsonObject root = new JsonObject();
        root.addProperty("raid_id", raidId);
        JsonArray array = new JsonArray();
        nodes.forEach(node -> array.add(node.toJson()));
        root.add("nodes", array);
        Path path = path(server, raidId);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            DreamingFishCore.LOGGER.error("[raid_loose] 节点写入失败：{}", path, exception);
        }
    }

    /** 供命令打印。 */
    public static synchronized List<String> describe() {
        List<String> lines = new ArrayList<>();
        long pending = nodes.stream().filter(node -> !node.picked()).count();
        lines.add("露天物品节点 " + nodes.size() + " 个（未拾取 " + pending + "）");
        Map<String, Integer> byItem = new LinkedHashMap<>();
        nodes.forEach(node -> byItem.merge(node.itemId() + (node.picked() ? "（已拾取）" : ""),
                1, Integer::sum));
        byItem.forEach((item, count) -> lines.add("  " + item + " × " + count));
        for (Node node : nodes) {
            if (lines.size() > 30) {
                lines.add("  …");
                break;
            }
            lines.add(String.format(Locale.ROOT, "  %s → %s @ %.1f/%.1f/%.1f%s",
                    node.anchorId(), node.itemId(), node.x(), node.y(), node.z(),
                    node.picked() ? "（已拾取）" : ""));
        }
        return lines;
    }
}
