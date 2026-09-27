package com.hhy.dreamingfishcore.gameplay.storybook_system;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import com.hhy.dreamingfishcore.item.DreamingFishCore_Items;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.gameplay.storybook_system.network.Packet_OpenStoryBookGUI;
import com.hhy.dreamingfishcore.gameplay.storybook_system.network.Packet_OpenStoryFragmentGUI;
import net.minecraft.advancements.Advancement;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 片段数据管理器
 * 负责加载和管理片段配置数据，以及玩家的随记本数据
 * 配置文件路径: config/dreamingfishcore/data/fragment_data.json
 * 玩家数据路径: <世界存档>/data/dreamingfishcore/storybook/player_progress.json
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public class StoryBookDataManager {

    private static final Path FRAGMENT_DATA_PATH = FMLPaths.CONFIGDIR.get()
            .resolve(DreamingFishCore.MODID)
            .resolve("data")
            .resolve("fragment_data.json");

    // ==================== 片段配置数据 ====================
    // 片段缓存：fragmentId -> FragmentData
    public static Map<Integer, FragmentData> FRAGMENT_CACHE = new ConcurrentHashMap<>();

    // 阶段片段索引：stageId -> List<FragmentData>
    public static Map<Integer, List<FragmentData>> STAGE_INDEX = new ConcurrentHashMap<>();

    // 章节片段索引：chapterId -> List<FragmentData>
    public static Map<Integer, List<FragmentData>> CHAPTER_INDEX = new ConcurrentHashMap<>();

    // ==================== 玩家随记本数据 ====================
    // 玩家数据：playerUUID -> StoryBookData
    public static Map<UUID, StoryBookData> PLAYER_DATA_CACHE = new ConcurrentHashMap<>();

    // 待保存的玩家数据队列
    private static final Set<UUID> DIRTY_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final int MAX_FRAGMENT_ENTRIES = 16_384;
    private static final int MAX_PLAYER_RECORDS = 16_384;
    private static final int MAX_PLAYER_FRAGMENT_ENTRIES = 16_384;
    private static final int MAX_FRAGMENT_TITLE_LENGTH = 512;
    private static final int MAX_FRAGMENT_CONTENT_LENGTH = 32_768;
    private static final int MAX_FRAGMENT_META_LENGTH = 256;
    /** 网络包和玩家排序请求共用的硬上限，避免客户端 VarInt 放大主线程工作量。 */
    public static final int MAX_NETWORK_ORDER_ENTRIES = 16_384;
    public static final int MAX_NETWORK_BOOK_ENTRIES = 16_384;
    public static final int MAX_NETWORK_TEXT_LENGTH = 32_768;

    private static boolean loaded;
    /** 片段定义只有在整份文件成功解析和校验后才允许覆盖。 */
    private static boolean fragmentConfigWritable;
    /** 玩家世界档案与片段定义分开保护；档案读取失败时绝不覆盖原档。 */
    private static boolean playerDataWritable;

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private static final Type FRAGMENT_LIST_TYPE = new TypeToken<List<FragmentData>>() {}.getType();
    private static final Type PLAYER_DATA_TYPE = new TypeToken<Map<String, StoryBookData>>() {}.getType();

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerUuid = player.getUUID();
            PLAYER_DATA_CACHE.computeIfAbsent(playerUuid, k -> new StoryBookData());
        }
    }

    // ==================== 片段配置数据加载/保存 ====================

    /**
     * 加载片段配置数据
     */
    public static synchronized void loadFragmentData() {
        FRAGMENT_CACHE.clear();
        STAGE_INDEX.clear();
        CHAPTER_INDEX.clear();
        // 每次重载都从只读保护开始；不能沿用上一个世界/上一次成功加载的状态。
        fragmentConfigWritable = false;

        if (Files.notExists(FRAGMENT_DATA_PATH) && !saveDefaultFragmentConfig()) {
            return;
        }

        try {
            if (Files.size(FRAGMENT_DATA_PATH) == 0L) {
                fragmentConfigWritable = false;
                DreamingFishCore.LOGGER.error("片段配置为空，拒绝覆盖文件：{}", FRAGMENT_DATA_PATH);
                return;
            }

            List<FragmentData> fragmentList = JsonDataStore.read(
                    FRAGMENT_DATA_PATH,
                    GSON,
                    FRAGMENT_LIST_TYPE,
                    ArrayList::new);
            validateFragmentList(fragmentList);

            // 先在临时索引中完整构建，最后一次性提交，避免半份坏配置留在运行缓存。
            Map<Integer, FragmentData> fragments = new LinkedHashMap<>();
            Map<Integer, List<FragmentData>> stages = new LinkedHashMap<>();
            Map<Integer, List<FragmentData>> chapters = new LinkedHashMap<>();
            for (FragmentData fragment : fragmentList) {
                fragments.put(fragment.getId(), fragment);
                stages.computeIfAbsent(fragment.getStageId(), k -> new ArrayList<>()).add(fragment);
                chapters.computeIfAbsent(fragment.getChapterId(), k -> new ArrayList<>()).add(fragment);
            }

            FRAGMENT_CACHE.putAll(fragments);
            STAGE_INDEX.putAll(stages);
            CHAPTER_INDEX.putAll(chapters);
            fragmentConfigWritable = true;
        } catch (Exception exception) {
            FRAGMENT_CACHE.clear();
            STAGE_INDEX.clear();
            CHAPTER_INDEX.clear();
            fragmentConfigWritable = false;
            DreamingFishCore.LOGGER.error(
                    "片段配置及备份读取失败，拒绝覆盖原文件：{}",
                    FRAGMENT_DATA_PATH,
                    exception);
        }

        DreamingFishCore.LOGGER.info("片段数据加载完成，共 {} 条片段，{} 个阶段，{} 个章节",
                FRAGMENT_CACHE.size(), STAGE_INDEX.size(), CHAPTER_INDEX.size());
    }

    /**
     * 保存片段配置数据
     */
    public static synchronized boolean saveFragmentData() {
        if (!fragmentConfigWritable) {
            DreamingFishCore.LOGGER.error("片段配置未安全加载，拒绝覆盖文件：{}", FRAGMENT_DATA_PATH);
            return false;
        }

        List<FragmentData> fragmentList = new ArrayList<>(FRAGMENT_CACHE.values());
        fragmentList.sort(Comparator.comparingInt(FragmentData::getId));
        try {
            JsonDataStore.writeAtomic(FRAGMENT_DATA_PATH, GSON, fragmentList);
            return true;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("保存片段数据失败：{}", FRAGMENT_DATA_PATH, exception);
            return false;
        }
    }

    /**
     * 保存默认片段配置
     */
    private static boolean saveDefaultFragmentConfig() {
        // 开服前暂不投放随机线索。保留空配置，后续可直接加入正式片段。
        List<FragmentData> defaultFragments = new ArrayList<>();

        try {
            JsonDataStore.writeAtomic(FRAGMENT_DATA_PATH, GSON, defaultFragments);
            // 这里只负责创建缺失模板；是否可写由后续完整读取+校验决定。
            fragmentConfigWritable = false;
            DreamingFishCore.LOGGER.info("默认片段配置已保存");
            return true;
        } catch (Exception exception) {
            fragmentConfigWritable = false;
            DreamingFishCore.LOGGER.error("保存默认片段配置失败：{}", FRAGMENT_DATA_PATH, exception);
            return false;
        }
    }

    // ==================== 玩家数据加载/保存 ====================

    /**
     * 加载当前世界的玩家随记本数据。片段定义仍从全局配置加载。
     */
    public static synchronized void loadWorldData(MinecraftServer server) {
        // 世界切换/重复启动时不能残留上一个世界的片段、索引或可写状态。
        clearWorldCache();
        loadFragmentData();

        if (server == null) {
            loaded = true;
            playerDataWritable = false;
            DreamingFishCore.LOGGER.error("无法加载随记本玩家数据：服务器实例为空，进入只读保护");
            return;
        }

        try {
            Path playerPath = playerDataPath(server);
            if (Files.exists(playerPath) && Files.size(playerPath) == 0L) {
                throw new IllegalStateException(
                        "随记本玩家数据文件为空，已拒绝覆盖原文件：" + playerPath);
            }
            Map<String, StoryBookData> dataMap = JsonDataStore.read(
                    playerPath, GSON, PLAYER_DATA_TYPE, HashMap::new);
            Map<UUID, StoryBookData> loadedPlayers = new LinkedHashMap<>();
            validatePlayerDataMap(dataMap);
            for (Map.Entry<String, StoryBookData> entry : dataMap.entrySet()) {
                UUID uuid = UUID.fromString(entry.getKey());
                loadedPlayers.put(uuid, entry.getValue());
            }
            PLAYER_DATA_CACHE.putAll(loadedPlayers);
            playerDataWritable = true;
            DreamingFishCore.LOGGER.info("随记本玩家数据加载完成，共 {} 个玩家", PLAYER_DATA_CACHE.size());
        } catch (Exception exception) {
            PLAYER_DATA_CACHE.clear();
            playerDataWritable = false;
            DreamingFishCore.LOGGER.error("读取世界随记本数据失败，本次会话不会覆盖损坏文件", exception);
        } finally {
            loaded = true;
        }
    }

    /**
     * 只在玩家随记本数据发生变化时保存。
     */
    public static synchronized boolean saveIfDirty(MinecraftServer server) {
        if (!loaded || DIRTY_PLAYERS.isEmpty()) {
            return true;
        }
        if (!playerDataWritable) {
            DreamingFishCore.LOGGER.error("随记本玩家数据未安全加载，拒绝覆盖原文件");
            return false;
        }
        if (server == null) {
            DreamingFishCore.LOGGER.error("无法保存随记本玩家数据：服务器实例为空");
            return false;
        }

        Map<String, StoryBookData> dataMap = new HashMap<>();
        for (Map.Entry<UUID, StoryBookData> entry : PLAYER_DATA_CACHE.entrySet()) {
            dataMap.put(entry.getKey().toString(), entry.getValue());
        }

        try {
            JsonDataStore.writeAtomic(playerDataPath(server), GSON, dataMap);
            DIRTY_PLAYERS.clear();
            return true;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("写入世界随记本数据失败，保留 dirty 状态等待下次保存", exception);
            return false;
        }
    }

    /**
     * 服务器关闭后释放世界级静态缓存，避免下一次开服串档。
     */
    public static synchronized void clearWorldCache() {
        FRAGMENT_CACHE.clear();
        STAGE_INDEX.clear();
        CHAPTER_INDEX.clear();
        PLAYER_DATA_CACHE.clear();
        DIRTY_PLAYERS.clear();
        loaded = false;
        fragmentConfigWritable = false;
        playerDataWritable = false;
    }

    private static void validateFragmentList(List<FragmentData> fragments) {
        if (fragments == null) {
            throw new IllegalStateException("片段配置根节点不是数组");
        }
        if (fragments.size() > MAX_FRAGMENT_ENTRIES) {
            throw new IllegalStateException("片段配置条目超过上限：" + MAX_FRAGMENT_ENTRIES);
        }
        Set<Integer> ids = new HashSet<>();
        for (FragmentData fragment : fragments) {
            if (fragment == null) {
                throw new IllegalStateException("片段配置包含空条目");
            }
            if (fragment.getId() <= 0 || !ids.add(fragment.getId())) {
                throw new IllegalStateException("片段 ID 非法或重复：" + fragment.getId());
            }
            // 1.20.1 的内置片段使用 chapterId=0 作为历史展示分组；继续允许读取，
            // 但仍拒绝负数，避免升级后把原本可用的配置误判为损坏并锁成只读。
            if (fragment.getStageId() <= 0 || fragment.getChapterId() < 0) {
                throw new IllegalStateException("片段阶段 ID 必须为正数且章节 ID 不能为负数：" + fragment.getId());
            }
            requireLength(fragment.getAuthorName(), MAX_FRAGMENT_META_LENGTH, "authorName", fragment.getId());
            requireLength(fragment.getTime(), MAX_FRAGMENT_META_LENGTH, "time", fragment.getId());
            requireLength(fragment.getTitle(), MAX_FRAGMENT_TITLE_LENGTH, "title", fragment.getId());
            requireLength(fragment.getContent(), MAX_FRAGMENT_CONTENT_LENGTH, "content", fragment.getId());
        }
    }

    private static void validatePlayerDataMap(Map<String, StoryBookData> dataMap) {
        if (dataMap == null) {
            throw new IllegalStateException("随记本玩家数据根节点不是对象");
        }
        if (dataMap.size() > MAX_PLAYER_RECORDS) {
            throw new IllegalStateException("随记本玩家记录超过上限：" + MAX_PLAYER_RECORDS);
        }
        for (Map.Entry<String, StoryBookData> entry : dataMap.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalStateException("随记本玩家 UUID 为空");
            }
            UUID.fromString(entry.getKey());
            StoryBookData data = entry.getValue();
            if (data == null) {
                throw new IllegalStateException("随记本玩家记录为空：" + entry.getKey());
            }
            if (data.getFragmentPageUseCount() < 0
                    || data.getUnlockedFragmentIds().size() > MAX_PLAYER_FRAGMENT_ENTRIES
                    || data.getReadFragmentIds().size() > MAX_PLAYER_FRAGMENT_ENTRIES
                    || data.getUnlockedChapterIds().size() > MAX_PLAYER_FRAGMENT_ENTRIES
                    || data.getObtainedOrder().size() > MAX_PLAYER_FRAGMENT_ENTRIES) {
                throw new IllegalStateException("随记本玩家集合超过上限：" + entry.getKey());
            }
            validatePositiveIds(data.getUnlockedFragmentIds(), "已解锁片段", entry.getKey());
            validatePositiveIds(data.getReadFragmentIds(), "已读片段", entry.getKey());
            validatePositiveIds(data.getUnlockedChapterIds(), "已解锁章节", entry.getKey());
            Set<Integer> ordered = new HashSet<>();
            for (Integer id : data.getObtainedOrder()) {
                if (id == null || id <= 0 || !ordered.add(id)) {
                    throw new IllegalStateException("随记本排序列表含非法/重复片段：" + entry.getKey());
                }
            }
        }
    }

    private static void validatePositiveIds(Set<Integer> ids, String label, String playerId) {
        for (Integer id : ids) {
            if (id == null || id <= 0) {
                throw new IllegalStateException(label + "含非法 ID：" + playerId);
            }
        }
    }

    private static void requireLength(String value, int maxLength, String field, int fragmentId) {
        if (value == null || value.length() > maxLength) {
            throw new IllegalStateException(
                    "片段 " + fragmentId + " 的 " + field + " 为空或过长（上限 " + maxLength + "）");
        }
    }

    private static Path playerDataPath(MinecraftServer server) {
        return WorldDataPaths.resolve(server, "storybook", "player_progress.json");
    }

    /**
     * 标记玩家数据为脏（需要保存）
     */
    public static void markPlayerDirty(UUID playerUuid) {
        ensureLoaded();
        if (playerUuid != null) {
            DIRTY_PLAYERS.add(playerUuid);
        }
    }

    // ==================== 片段查询方法 ====================

    /**
     * 根据片段ID获取片段数据
     */
    public static FragmentData getFragment(int fragmentId) {
        return FRAGMENT_CACHE.get(fragmentId);
    }

    /**
     * 根据阶段ID获取该阶段的所有片段
     */
    public static List<FragmentData> getFragmentsByStage(int stageId) {
        return new ArrayList<>(STAGE_INDEX.getOrDefault(stageId, Collections.emptyList()));
    }

    /**
     * 根据章节ID获取该章节的所有片段
     */
    public static List<FragmentData> getFragmentsByChapter(int chapterId) {
        return new ArrayList<>(CHAPTER_INDEX.getOrDefault(chapterId, Collections.emptyList()));
    }

    /**
     * 获取所有片段
     */
    public static Map<Integer, FragmentData> getAllFragments() {
        return new HashMap<>(FRAGMENT_CACHE);
    }

    /**
     * 获取片段总数
     */
    public static int getFragmentCount() {
        return FRAGMENT_CACHE.size();
    }

    /**
     * 检查片段是否存在
     */
    public static boolean hasFragment(int fragmentId) {
        return FRAGMENT_CACHE.containsKey(fragmentId);
    }

    // ==================== 玩家数据查询方法 ====================

    /**
     * 获取玩家的随记本数据
     */
    public static StoryBookData getPlayerStoryBook(UUID playerUuid) {
        ensureLoaded();
        if (playerUuid == null) {
            throw new IllegalArgumentException("玩家 UUID 不能为空");
        }
        return PLAYER_DATA_CACHE.computeIfAbsent(playerUuid, ignored -> new StoryBookData());
    }

    private static void ensureLoaded() {
        if (!loaded) {
            throw new IllegalStateException("随记本玩家数据尚未随世界加载");
        }
    }

    /**
     * 获取玩家的随记本数据（ServerPlayer）
     */
    public static StoryBookData getPlayerStoryBook(ServerPlayer player) {
        return getPlayerStoryBook(player.getUUID());
    }

    /**
     * 解锁片段
     */
    public static boolean unlockFragmentForPlayer(UUID playerUuid, int fragmentId) {
        if (!hasFragment(fragmentId)) {
            return false;
        }

        StoryBookData storyBook = getPlayerStoryBook(playerUuid);
        boolean unlocked = storyBook.unlockFragment(fragmentId);
        if (unlocked) {
            markPlayerDirty(playerUuid);
        }
        return unlocked;
    }

    /**
     * 解锁章节
     */
    public static boolean unlockChapterForPlayer(UUID playerUuid, int chapterId) {
        StoryBookData storyBook = getPlayerStoryBook(playerUuid);
        boolean unlocked = storyBook.unlockChapter(chapterId);
        if (unlocked) {
            markPlayerDirty(playerUuid);
        }
        return unlocked;
    }

    /**
     * 标记片段已读
     */
    public static void markFragmentReadForPlayer(UUID playerUuid, int fragmentId) {
        StoryBookData storyBook = getPlayerStoryBook(playerUuid);
        storyBook.markFragmentRead(fragmentId);
        markPlayerDirty(playerUuid);
    }

    /**
     * 检查玩家是否拥有随记本
     */
    public static boolean playerHasStoryBook(UUID playerUuid) {
        StoryBookData storyBook = PLAYER_DATA_CACHE.get(playerUuid);
        return storyBook != null && storyBook.hasStoryBook();
    }

    public static List<StoryBookEntryViewData> getStoryBookEntriesForPlayer(UUID playerUuid) {
        StoryBookData storyBook = getPlayerStoryBook(playerUuid);
        List<StoryBookEntryViewData> entries = new ArrayList<>();
        for (Integer fragmentId : storyBook.getSortedFragmentIds()) {
            FragmentData fragmentData = getFragment(fragmentId);
            if (fragmentData == null) {
                continue;
            }
            entries.add(new StoryBookEntryViewData(
                    fragmentData.getId(),
                    fragmentData.getStageId(),
                    fragmentData.getChapterId(),
                    fragmentData.getTitle(),
                    fragmentData.getContent(),
                    fragmentData.getTime(),
                    fragmentData.getAuthorName(),
                    storyBook.hasReadFragment(fragmentId)
            ));
        }
        return entries;
    }

    /**
     * 给予玩家随记本
     */
    public static void giveStoryBookToPlayer(UUID playerUuid) {
        StoryBookData storyBook = getPlayerStoryBook(playerUuid);
        if (!storyBook.hasStoryBook()) {
            storyBook.setHasStoryBook(true);
            markPlayerDirty(playerUuid);
        }
    }

    /**
     * 检查玩家是否开始旅程
     */
    public static boolean playerJourneyStarted(UUID playerUuid) {
        StoryBookData storyBook = PLAYER_DATA_CACHE.get(playerUuid);
        return storyBook != null && storyBook.isJourneyStarted();
    }

    /**
     * 开始玩家旅程
     */
    public static void startPlayerJourney(UUID playerUuid) {
        StoryBookData storyBook = getPlayerStoryBook(playerUuid);
        if (!storyBook.isJourneyStarted()) {
            storyBook.setJourneyStarted(true);
            markPlayerDirty(playerUuid);
        }
    }

    /**
     * 增加玩家残页使用次数
     */
    public static int incrementFragmentPageUseCount(UUID playerUuid) {
        StoryBookData storyBook = getPlayerStoryBook(playerUuid);
        storyBook.incrementFragmentPageUseCount();
        markPlayerDirty(playerUuid);
        return storyBook.getFragmentPageUseCount();
    }

    /**
     * 获取玩家残页使用次数
     */
    public static int getFragmentPageUseCount(UUID playerUuid) {
        StoryBookData storyBook = PLAYER_DATA_CACHE.get(playerUuid);
        return storyBook != null ? storyBook.getFragmentPageUseCount() : 0;
    }

    public static void openStoryBook(ServerPlayer player) {
        DreamingFishCore_NetworkManager.sendToClient(
                new Packet_OpenStoryBookGUI(getStoryBookEntriesForPlayer(player.getUUID())),
                player
        );
    }

    public static void updateFragmentOrderForPlayer(UUID playerUuid, List<Integer> orderedFragmentIds) {
        if (playerUuid == null || orderedFragmentIds == null
                || orderedFragmentIds.size() > MAX_NETWORK_ORDER_ENTRIES) {
            return;
        }

        StoryBookData storyBook = getPlayerStoryBook(playerUuid);
        Set<Integer> unlocked = storyBook.getUnlockedFragmentIds();
        Set<Integer> seen = new HashSet<>();
        for (Integer fragmentId : orderedFragmentIds) {
            // The client may omit an entry while sorting a stale view, but it
            // must never introduce an unknown/duplicate ID into the world
            // state.  StoryBookData will append any owned IDs that were omitted.
            if (fragmentId == null || fragmentId <= 0
                    || !unlocked.contains(fragmentId) || !seen.add(fragmentId)) {
                return;
            }
        }
        List<Integer> before = storyBook.getSortedFragmentIds();
        storyBook.setObtainedOrder(orderedFragmentIds);
        if (!before.equals(storyBook.getSortedFragmentIds())) {
            markPlayerDirty(playerUuid);
        }
    }

    public static boolean useFragmentPage(ServerPlayer player) {
        return useFragmentPage(player, null);
    }

    /**
     * 使用片段残页。
     * 如果物品指定了 fragmentId，则直接读取对应 json 片段；否则按默认顺序补全下一条内容。
     */
    public static boolean useFragmentPage(ServerPlayer player, Integer specifiedFragmentId) {
        UUID playerUuid = player.getUUID();
        StoryBookData storyBook = getPlayerStoryBook(playerUuid);

        if (specifiedFragmentId == null) {
            player.sendSystemMessage(Component.literal("§c这张残页没有绑定编号，无法整理出新的内容。"));
            return false;
        }

        if (!hasFragment(specifiedFragmentId)) {
            player.sendSystemMessage(Component.literal("§c指定的残页编号不存在：§f" + specifiedFragmentId));
            return false;
        }

        boolean firstUse = !storyBook.isJourneyStarted();
        giveStoryBookToPlayer(playerUuid);
        ensurePlayerHasStoryBookItem(player);

        if (firstUse) {
            startPlayerJourney(playerUuid);
            grantJourneyStartedAdvancement(player);
            player.sendSystemMessage(Component.literal("§6已解锁成就：§e远旅开端"));
        }

        incrementFragmentPageUseCount(playerUuid);

        int fragmentIdToDisplay = specifiedFragmentId;
        if (!storyBook.hasUnlockedFragment(fragmentIdToDisplay)) {
            unlockFragmentForPlayer(playerUuid, fragmentIdToDisplay);
            FragmentData fragmentData = getFragment(fragmentIdToDisplay);
            if (fragmentData != null) {
                // The fragment definition owns its chapter.  Do not use a
                // synthetic chapter 0: StoryBookData intentionally rejects
                // non-positive chapter IDs, and the first real page should
                // unlock the chapter it actually belongs to.
                unlockChapterForPlayer(playerUuid, fragmentData.getChapterId());
                player.sendSystemMessage(Component.literal("§a你拼出了新的内容：§f" + fragmentData.getTitle()));
            }
        } else {
            player.sendSystemMessage(Component.literal("§7你翻开了已收录的残页：§f" + specifiedFragmentId));
        }

        FragmentData fragmentToDisplay = getFragment(fragmentIdToDisplay);
        if (fragmentToDisplay == null) {
            player.sendSystemMessage(Component.literal("§c这张残页上没有可读取的内容。"));
            return false;
        }

        markFragmentReadForPlayer(playerUuid, fragmentIdToDisplay);
        DreamingFishCore_NetworkManager.sendToClient(new Packet_OpenStoryFragmentGUI(fragmentToDisplay), player);
        levelUpStoryFeedback(player);
        markPlayerDirty(playerUuid);
        return true;
    }

    private static int getLatestUnlockedFragmentId(StoryBookData storyBook) {
        List<Integer> obtainedOrder = storyBook.getObtainedOrder();
        if (!obtainedOrder.isEmpty()) {
            return obtainedOrder.get(obtainedOrder.size() - 1);
        }
        return storyBook.getUnlockedFragmentIds().stream()
                .max(Integer::compareTo)
                .orElse(-1);
    }

    private static void ensurePlayerHasStoryBookItem(ServerPlayer player) {
        ItemStack storyBookStack = new ItemStack(DreamingFishCore_Items.STORY_BOOK.get());
        if (player.getInventory().contains(storyBookStack)) {
            return;
        }

        if (!player.addItem(storyBookStack)) {
            player.drop(storyBookStack, false);
        }
    }

    private static void grantJourneyStartedAdvancement(ServerPlayer player) {
        var advancement = player.server.getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "storybook/journey_started"));
        if (advancement != null) {
            player.getAdvancements().award(advancement, "triggered");
        }
    }

    private static void levelUpStoryFeedback(ServerPlayer player) {
        player.level().playSound(
                null,
                player.blockPosition(),
                SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );
    }
}
