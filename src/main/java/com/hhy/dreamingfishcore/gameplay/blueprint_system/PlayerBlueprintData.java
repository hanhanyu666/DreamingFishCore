package com.hhy.dreamingfishcore.gameplay.blueprint_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.item.items.Item_Blueprint;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家的蓝图进度 + 全服共用的「哪些物品需要蓝图」索引。
 *
 * <p><b>玩家侧</b>：已学会的物品 ID 存在玩家 NBT 的 {@code unlocked_items} 里。
 * 死亡会清空（见 {@code BlueprintEventHandler}）。</p>
 *
 * <p><b>全服侧</b>：{@code RecipeManagerMixin} 在工作台配方加载完成后把「配方 → 输出物品」
 * 交给这里，再由 {@link BlueprintConfig} 过滤出可以出现在抽取池里的物品。
 * 因此本类的池是**懒重建**的：配方重载或配置重载都会把 {@link #markPoolDirty()} 标脏。</p>
 *
 * <p>本类只做纯逻辑，不依赖任何服务端世界状态，方便单测。</p>
 */
public final class PlayerBlueprintData {

    private static final String UNLOCKED_ITEMS_KEY = "unlocked_items";
    private static final String BLUEPRINT_ITEMS_KEY = "blueprint_items";

    /** 配方 ID → 输出物品 ID。同一个物品可能有多个配方，池里按物品去重。 */
    private static final Map<ResourceLocation, String> WORKBENCH_RECIPE_OUTPUTS = new ConcurrentHashMap<>();

    /** 抽取池（已按配置过滤、按物品去重），懒重建。 */
    private static final List<String> BLUEPRINT_POOL = new ArrayList<>();
    private static volatile boolean poolDirty = true;

    /** 创造模式标签页用的一次性列表。 */
    private static final List<ItemStack> BLUEPRINT_ITEM_STACKS = new ArrayList<>();

    private PlayerBlueprintData() {
    }

    // ==================== 配方索引 ====================

    /** 配方重载前清空索引。 */
    public static synchronized void clearWorkbenchRecipes() {
        WORKBENCH_RECIPE_OUTPUTS.clear();
        BLUEPRINT_ITEM_STACKS.clear();
        poolDirty = true;
    }

    /** 记录一条工作台配方。只关心「输出是什么物品」，不关心配方本身。 */
    public static synchronized void addWorkbenchRecipe(ResourceLocation recipeId, Recipe<?> recipe) {
        String outputItemId = readOutputItemId(recipe);
        if (outputItemId == null || outputItemId.isEmpty()) {
            return;
        }
        WORKBENCH_RECIPE_OUTPUTS.put(recipeId, outputItemId);
        poolDirty = true;
    }

    /** 供测试与配置重载使用：让抽取池下次访问时重建。 */
    public static synchronized void markPoolDirty() {
        poolDirty = true;
    }

    /**
     * 从一个配方里读出输出物品 ID。
     *
     * <p>用空的 {@link RegistryAccess}：这里只需要物品身份，不需要完整注册表；
     * 少数配方会因此抛异常，按「读不出来」处理而不是让整个配方加载失败。</p>
     */
    private static String readOutputItemId(Recipe<?> recipe) {
        try {
            ItemStack output = recipe.getResultItem(RegistryAccess.EMPTY);
            if (output != null && !output.isEmpty()) {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(output.getItem());
                if (key != null) {
                    return key.toString();
                }
            }
        } catch (Exception ignored) {
            // 读不出输出就不参与蓝图系统。
        }
        return null;
    }

    // ==================== 抽取池 ====================

    /** 当前抽取池（不可变快照）。 */
    public static synchronized List<String> getBlueprintPool() {
        refreshPool();
        return List.copyOf(BLUEPRINT_POOL);
    }

    private static void refreshPool() {
        if (!poolDirty) {
            return;
        }
        BlueprintConfig config = BlueprintConfig.current();
        Set<String> deduplicated = new LinkedHashSet<>();
        for (String itemId : WORKBENCH_RECIPE_OUTPUTS.values()) {
            if (config.isInBlueprintPool(itemId)) {
                deduplicated.add(itemId);
            }
        }
        BLUEPRINT_POOL.clear();
        BLUEPRINT_POOL.addAll(deduplicated);
        poolDirty = false;
    }

    /**
     * 掉落用：从池里随机抽一张**该玩家还没学过**的蓝图。
     *
     * <p>没有玩家上下文（例如宝箱）时用 {@link #createRandomBlueprint(RandomSource)}。</p>
     *
     * @return 抽不到时返回 {@link ItemStack#EMPTY}（池为空，或该玩家已经学完池里的全部蓝图）
     */
    public static ItemStack createRandomBlueprintFor(Player player, RandomSource random) {
        List<String> candidates = excludeLearned(getBlueprintPool(), getRawUnlockedItems(player));
        return pick(candidates, random);
    }

    /**
     * 从候选池里剔掉玩家已经学会的物品。
     *
     * <p>抽成不依赖 {@code Player} 的纯函数，这样“按玩家去重”这条规则可以直接单测。</p>
     */
    static List<String> excludeLearned(Collection<String> pool, Collection<String> learned) {
        List<String> candidates = new ArrayList<>();
        for (String itemId : pool) {
            if (!learned.contains(itemId)) {
                candidates.add(itemId);
            }
        }
        return candidates;
    }

    /**
     * 无玩家上下文时随机抽一张蓝图（宝箱掉落走这条）。
     *
     * <p>宝箱战利品拿不到击杀者，无法按玩家去重，因此这里不做「已学过」过滤。</p>
     */
    public static ItemStack createRandomBlueprint(RandomSource random) {
        return pick(getBlueprintPool(), random);
    }

    private static ItemStack pick(List<String> candidates, RandomSource random) {
        if (candidates.isEmpty()) {
            return ItemStack.EMPTY;
        }
        String itemId = candidates.get(random.nextInt(candidates.size()));
        return Item_Blueprint.createBlueprint(itemId);
    }

    /**
     * 加载完成后打一遍诊断日志。
     *
     * <p>重点提醒两类容易被配置坑到的物品：被黑名单排除、又不在默认放行/赦免命名空间里的物品
     * —— 它们抽不到蓝图，玩家也就永远无法通过工作台合成。</p>
     */
    public static synchronized void logPoolDiagnostics() {
        BlueprintConfig config = BlueprintConfig.current();
        if (!config.isEnabled()) {
            DreamingFishCore.LOGGER.info("蓝图限制未启用（config/dreamingfishcore/blueprint.json 的 enabled 为 false），所有配方照常可合成");
            return;
        }

        refreshPool();
        DreamingFishCore.LOGGER.info("蓝图抽取池已就绪：{} 个物品（工作台配方输出 {} 条）",
                BLUEPRINT_POOL.size(), WORKBENCH_RECIPE_OUTPUTS.size());

        List<String> unreachable = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String itemId : WORKBENCH_RECIPE_OUTPUTS.values()) {
            if (!config.isExemptFromBlueprint(itemId)
                    && !config.isInBlueprintPool(itemId)
                    && seen.add(itemId)) {
                unreachable.add(itemId);
            }
        }
        if (!unreachable.isEmpty()) {
            DreamingFishCore.LOGGER.warn(
                    "以下 {} 个物品被黑名单/白名单挡在抽取池外，也不在默认放行或赦免命名空间里，"
                            + "玩家将无法通过工作台合成它们（应确保有别的获取途径）：{}",
                    unreachable.size(), unreachable);
        }
    }

    // ==================== 玩家进度 ====================

    /** 学习一张蓝图。已经学过时不重复写入。 */
    public static void unlockItem(Player player, String itemId) {
        if (player == null || itemId == null || itemId.isEmpty()) {
            return;
        }
        CompoundTag playerData = player.getPersistentData();
        ListTag unlockedList = playerData.contains(UNLOCKED_ITEMS_KEY, 9)
                ? playerData.getList(UNLOCKED_ITEMS_KEY, 8)
                : new ListTag();

        for (int i = 0; i < unlockedList.size(); i++) {
            if (itemId.equals(unlockedList.getString(i))) {
                return;
            }
        }
        unlockedList.add(StringTag.valueOf(itemId));
        playerData.put(UNLOCKED_ITEMS_KEY, unlockedList);
    }

    /**
     * 玩家能不能合成这件物品。
     *
     * <p>「免蓝图」（总开关关闭 / 默认放行 / 赦免命名空间）一律放行；其余物品必须学过对应蓝图。
     * 注意：被黑名单排除、又不在豁免范围内的物品**同样会被拦**，这是刻意的设计。</p>
     */
    public static boolean canCraftItem(Player player, String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return true;
        }
        if (BlueprintConfig.current().isExemptFromBlueprint(itemId)) {
            return true;
        }
        return hasLearned(player, itemId);
    }

    /** 玩家能不能合成这件物品（按合成结果判断）。 */
    public static boolean canCraftItem(Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null || canCraftItem(player, key.toString());
    }

    /** 玩家是否学过这件物品的蓝图。 */
    public static boolean hasLearned(Player player, String itemId) {
        if (player == null || itemId == null) {
            return false;
        }
        CompoundTag playerData = player.getPersistentData();
        if (!playerData.contains(UNLOCKED_ITEMS_KEY, 9)) {
            return false;
        }
        ListTag unlockedList = playerData.getList(UNLOCKED_ITEMS_KEY, 8);
        for (int i = 0; i < unlockedList.size(); i++) {
            if (itemId.equals(unlockedList.getString(i))) {
                return true;
            }
        }
        return false;
    }

    /** 玩家学过、且确实需要蓝图的物品数量（免蓝图的默认物品不计入）。 */
    public static int getLearnedBlueprintCount(Player player) {
        BlueprintConfig config = BlueprintConfig.current();
        int count = 0;
        for (String itemId : getRawUnlockedItems(player)) {
            if (!config.isExemptFromBlueprint(itemId)) {
                count++;
            }
        }
        return count;
    }

    /** 玩家学过、且确实是靠蓝图学来的物品（命令与排查用）。 */
    public static Set<String> getLearnedBlueprintItems(Player player) {
        BlueprintConfig config = BlueprintConfig.current();
        Set<String> learned = new LinkedHashSet<>();
        for (String itemId : getRawUnlockedItems(player)) {
            if (!config.isExemptFromBlueprint(itemId)) {
                learned.add(itemId);
            }
        }
        return learned;
    }

    private static List<String> getRawUnlockedItems(Player player) {
        List<String> items = new ArrayList<>();
        if (player == null) {
            return items;
        }
        CompoundTag playerData = player.getPersistentData();
        if (!playerData.contains(UNLOCKED_ITEMS_KEY, 9)) {
            return items;
        }
        ListTag unlockedList = playerData.getList(UNLOCKED_ITEMS_KEY, 8);
        for (int i = 0; i < unlockedList.size(); i++) {
            items.add(unlockedList.getString(i));
        }
        return items;
    }

    /** 清空玩家的蓝图进度（死亡遗忘 / 命令重置）。 */
    public static void clearAllUnlocks(Player player) {
        if (player == null) {
            return;
        }
        player.getPersistentData().remove(UNLOCKED_ITEMS_KEY);
    }

    // ==================== 创造模式标签页 ====================

    /** 为创造模式标签页准备「每种可抽取蓝图各一张」。重复调用不会累加。 */
    public static synchronized void initAllBlueprintItems() {
        BLUEPRINT_ITEM_STACKS.clear();
        for (String itemId : getBlueprintPool()) {
            BLUEPRINT_ITEM_STACKS.add(Item_Blueprint.createBlueprint(itemId));
        }
    }

    public static synchronized List<ItemStack> getAllBlueprintItems() {
        return List.copyOf(BLUEPRINT_ITEM_STACKS);
    }

    /** 只读的配方输出快照，供命令统计使用。 */
    public static synchronized Map<ResourceLocation, String> getRecipeOutputs() {
        return new HashMap<>(WORKBENCH_RECIPE_OUTPUTS);
    }

    /**
     * 「玩家已有蓝图物品」的记录。
     *
     * <p>保留旧行为：供老存档与外部工具读取，当前没有业务逻辑依赖它。</p>
     */
    public static Set<String> getAllBlueprintItems(Player player) {
        Set<String> blueprints = new LinkedHashSet<>();
        CompoundTag playerData = player.getPersistentData();
        if (playerData.contains(BLUEPRINT_ITEMS_KEY, 9)) {
            ListTag blueprintList = playerData.getList(BLUEPRINT_ITEMS_KEY, 8);
            for (int i = 0; i < blueprintList.size(); i++) {
                blueprints.add(blueprintList.getString(i));
            }
        }
        return blueprints;
    }

    /** 兼容旧调用：把物品 ID 记进「已有蓝图物品」列表。 */
    public static void addBlueprintItem(Player player, String blueprintItemId) {
        CompoundTag playerData = player.getPersistentData();
        ListTag blueprintList = playerData.contains(BLUEPRINT_ITEMS_KEY, 9)
                ? playerData.getList(BLUEPRINT_ITEMS_KEY, 8)
                : new ListTag();
        for (int i = 0; i < blueprintList.size(); i++) {
            if (blueprintItemId.equals(blueprintList.getString(i))) {
                return;
            }
        }
        blueprintList.add(StringTag.valueOf(blueprintItemId));
        playerData.put(BLUEPRINT_ITEMS_KEY, blueprintList);
    }
}
