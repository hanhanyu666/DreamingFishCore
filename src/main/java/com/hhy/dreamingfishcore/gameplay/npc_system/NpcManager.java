package com.hhy.dreamingfishcore.gameplay.npc_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.gameplay.npc_system.network.Packet_OpenNpcDialogueGUI;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcMessageManager;
import com.hhy.dreamingfishcore.gameplay.opening_story_system.OpeningStoryProgressManager;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import com.hhy.dreamingfishcore.gameplay.npc_system.entity.StoryNpcEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class NpcManager {
    public static final String ENTITY_NPC_ID_TAG = "DreamingFishCoreNpcId";
    static final String DIALOGUE_FAVORABILITY_EFFECT_ID = "interaction:dialogue:first";
    private static final boolean GIFT_INTERACTIONS_ENABLED = false;
    private static final double MAX_INTERACTION_DISTANCE_SQR = 8.0D * 8.0D;
    private static final Path NPC_DATA_PATH = FMLPaths.CONFIGDIR.get()
            .resolve(DreamingFishCore.MODID)
            .resolve("npc_data.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final Type NPC_MAP_TYPE = new TypeToken<Map<Integer, NpcData>>() {}.getType();

    private static Map<Integer, NpcData> npcCache = new ConcurrentHashMap<>();
    private static boolean configWritable;

    public static void init() {
        load();
    }

    public static synchronized void load() {
        if (Files.notExists(NPC_DATA_PATH)) {
            npcCache = new ConcurrentHashMap<>();
            createDefaultNpc();
            ensureBuiltInOpeningNpcs();
            configWritable = true;
            save();
            return;
        }

        try {
            if (Files.size(NPC_DATA_PATH) == 0L) {
                configWritable = false;
                DreamingFishCore.LOGGER.error("NPC 配置为空，保留当前内存数据并拒绝覆盖文件：{}", NPC_DATA_PATH);
                return;
            }

            Map<Integer, NpcData> loaded = JsonDataStore.read(
                    NPC_DATA_PATH,
                    GSON,
                    NPC_MAP_TYPE,
                    ConcurrentHashMap::new);
            npcCache = new ConcurrentHashMap<>(loaded);
            configWritable = true;
            if (npcCache.isEmpty()) {
                createDefaultNpc();
            }
            if (ensureBuiltInOpeningNpcs()) {
                save();
            }
            DreamingFishCore.LOGGER.info("NPC数据加载完成，共 {} 个NPC", npcCache.size());
        } catch (Exception exception) {
            configWritable = false;
            DreamingFishCore.LOGGER.error(
                    "NPC 配置及备份读取失败，保留当前内存数据并拒绝覆盖文件：{}",
                    NPC_DATA_PATH,
                    exception);
        }
    }

    public static synchronized boolean save() {
        if (!configWritable) {
            DreamingFishCore.LOGGER.error("NPC 配置未安全加载，拒绝覆盖文件：{}", NPC_DATA_PATH);
            return false;
        }
        try {
            JsonDataStore.writeAtomic(NPC_DATA_PATH, GSON, npcCache);
            return true;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("保存NPC数据失败：{}", NPC_DATA_PATH, exception);
            return false;
        }
    }

    public static Optional<NpcData> getNpc(int npcId) {
        return Optional.ofNullable(npcCache.get(npcId));
    }

    public static List<NpcData> getAllNpcs() {
        List<NpcData> npcs = new ArrayList<>(npcCache.values());
        npcs.sort(Comparator.comparingInt(NpcData::getNpcId));
        return npcs;
    }

    public static boolean openNpcDialogue(ServerPlayer player, int npcId) {
        return openNpcDialogue(player, npcId, -1);
    }

    public static boolean openNpcDialogue(ServerPlayer player, int npcId, int entityId) {
        Optional<NpcData> npc = getNpc(npcId);
        if (npc.isEmpty()) {
            return false;
        }
        DreamingFishCore_NetworkManager.sendToClient(new Packet_OpenNpcDialogueGUI(createViewData(player, npc.get(), entityId)), player);
        if (!NpcMessageManager.deliverInteractionMessage(player, npcId)) {
            // 即使本次没有新消息，也刷新关系值与可能刚解锁的预设回复。
            NpcMessageManager.syncToClient(player);
        }
        return true;
    }

    public static void handleInteraction(ServerPlayer player, int npcId, int entityId, NpcInteractionType interactionType) {
        if (!isValidInteractionTarget(player, npcId, entityId)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c你距离该 NPC 太远，或 NPC 已经失效。"));
            DreamingFishCore.LOGGER.warn("拒绝玩家 {} 的无效 NPC 交互请求：npcId={}, entityId={}",
                    player.getScoreboardName(), npcId, entityId);
            return;
        }

        Optional<NpcData> optionalNpc = getNpc(npcId);
        if (optionalNpc.isEmpty()) {
            return;
        }

        NpcData npc = optionalNpc.get();
        // 赠礼功能尚未开放。服务端也必须拒绝，不能只依赖客户端隐藏按钮。
        if (interactionType == NpcInteractionType.GIFT_ITEM && !GIFT_INTERACTIONS_ENABLED) {
            return;
        }

        int requiredFavorability = npc.getActionFavorabilityRequirements().getOrDefault(interactionType.name(), 0);
        if (!NpcRelationManager.canUseAction(npcId, player.getUUID(), interactionType, requiredFavorability)) {
            openNpcDialogue(player, npcId, entityId);
            return;
        }

        if (interactionType == NpcInteractionType.DIALOGUE) {
            // 每名玩家与每个 NPC 的首次交谈只奖励一次。effectId 会随关系数据持久化，
            // 因此反复点击、重新打开界面、重连或重启服务器都不能重复获得好感度。
            NpcRelationManager.applyFavorabilityEffect(
                    npcId,
                    player.getUUID(),
                    DIALOGUE_FAVORABILITY_EFFECT_ID,
                    1);
            // 白芷剧情在玩家点击“交谈”后推进；这里不重新打开界面，
            // 让客户端保留当前台词索引，不会每次点击都跳回第一句。
            OpeningStoryProgressManager.onNpcInteraction(player, npcId);
        } else if (interactionType == NpcInteractionType.GIFT_ITEM) {
            handleGift(player, npc);
        }

        if (interactionType == NpcInteractionType.DIALOGUE) {
            NpcMessageManager.syncToClient(player);
            return;
        }
        openNpcDialogue(player, npcId, entityId);
    }

    /** reload 后把新配置重新写入所有当前已加载的专用 NPC，并由实体数据自动同步给客户端。 */
    public static int refreshLoadedStoryNpcs(MinecraftServer server) {
        int refreshed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity loadedEntity : level.getEntities().getAll()) {
                if (loadedEntity instanceof StoryNpcEntity entity) {
                    Optional<NpcData> npc = getNpc(entity.getNpcId());
                    if (npc.isPresent()) {
                        entity.applyNpcData(npc.get());
                        refreshed++;
                    }
                }
            }
        }
        return refreshed;
    }

    /**
     * 校验客户端提交的 NPC 与实体绑定。管理员通过 /npc open 打开的测试界面没有实体 ID，
     * 仅允许管理员继续操作；普通玩家必须与同维度、仍存活且绑定正确的实体保持合理距离。
     */
    private static boolean isValidInteractionTarget(ServerPlayer player, int npcId, int entityId) {
        if (entityId < 0) {
            return player.hasPermissions(2);
        }

        Entity target = player.serverLevel().getEntity(entityId);
        if (target == null || !target.isAlive() || target.level() != player.level()) {
            return false;
        }
        if (!target.getPersistentData().contains(ENTITY_NPC_ID_TAG)
                || target.getPersistentData().getInt(ENTITY_NPC_ID_TAG) != npcId) {
            return false;
        }
        return player.distanceToSqr(target) <= MAX_INTERACTION_DISTANCE_SQR;
    }

    public static NpcDialogueViewData createViewData(ServerPlayer player, NpcData npc) {
        return createViewData(player, npc, -1);
    }

    public static NpcDialogueViewData createViewData(ServerPlayer player, NpcData npc, int entityId) {
        NpcRelationData relation = NpcRelationManager.getRelation(npc.getNpcId(), player.getUUID());
        NpcThoughtData thought = npc.getCurrentThought();
        return new NpcDialogueViewData(
                npc.getNpcId(),
                entityId,
                npc.getNpcName(),
                npc.getNpcIntroduction(),
                npc.getNpcGender(),
                npc.getNpcProfession(),
                npc.getStoryStageId(),
                OpeningStoryProgressManager.getDialogueOverride(player, npc.getNpcId())
                        .orElseGet(npc::getDialogues),
                thought == null ? "" : thought.getThoughtText(),
                thought == null ? "" : thought.getWantedItemId(),
                relation.getFavorability(),
                relation.getRelationType().getDisplayName(),
                getAvailableActionNames(player, npc)
        );
    }

    private static void handleGift(ServerPlayer player, NpcData npc) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        NpcThoughtData thought = npc.getCurrentThought();
        int favorability = 2;
        if (thought != null && itemId.equals(thought.getWantedItemId())) {
            favorability = thought.getFavorabilityReward();
        }
        stack.shrink(1);
        NpcRelationManager.addFavorability(npc.getNpcId(), player.getUUID(), favorability);
    }

    private static List<String> getAvailableActionNames(ServerPlayer player, NpcData npc) {
        List<String> actions = new ArrayList<>();
        for (NpcInteractionType type : NpcInteractionType.values()) {
            if (type == NpcInteractionType.GIFT_ITEM && !GIFT_INTERACTIONS_ENABLED) {
                continue;
            }
            int required = npc.getActionFavorabilityRequirements().getOrDefault(type.name(), 0);
            if (NpcRelationManager.canUseAction(npc.getNpcId(), player.getUUID(), type, required)) {
                actions.add(type.name());
            }
        }
        return actions;
    }

    private static void createDefaultNpc() {
        NpcData npc = new NpcData(1, "剧情记录员", "他记录着服务器共同推进的故事，也会记住每位玩家与他的交情。", "未知", "记录员");
        List<String> dialogues = new ArrayList<>();
        dialogues.add("欢迎回来。这个世界的故事不是一个人写完的。");
        dialogues.add("如果你带来了我正在寻找的东西，我会记住这份帮助。");
        npc.setDialogues(dialogues);
        npc.setStoryStageId(1);
        npc.setCurrentThought(new NpcThoughtData("我现在想要一个苹果，用来确认赠礼系统是否正常。", "minecraft:apple", "", 20, 0.0D));
        Map<String, Integer> requirements = new HashMap<>();
        requirements.put(NpcInteractionType.DIALOGUE.name(), 0);
        requirements.put(NpcInteractionType.GIFT_ITEM.name(), 0);
        requirements.put(NpcInteractionType.FOLLOW.name(), 300);
        requirements.put(NpcInteractionType.SET_HOME.name(), 300);
        requirements.put(NpcInteractionType.VIEW_BACKPACK.name(), 600);
        requirements.put(NpcInteractionType.ASSIGN_TASK.name(), 100);
        requirements.put(NpcInteractionType.WARNING_RULES.name(), 600);
        npc.setActionFavorabilityRequirements(requirements);
        npcCache.put(npc.getNpcId(), npc);
    }

    /**
     * 为开场内容补齐约定的临时 NPC 槽位。
     *
     * <p>生产服可能已经有自己的 npc_data.json，因此只使用 putIfAbsent，
     * 不会覆盖服主编辑过的同编号人物。</p>
     */
    private static boolean ensureBuiltInOpeningNpcs() {
        Map<Integer, NpcData> builtInNpcs = BuiltInNpcProfileCatalog.loadProfiles();
        if (!builtInNpcs.isEmpty()) {
            boolean changed = false;
            for (NpcData npc : builtInNpcs.values()) {
                if (npc != null) {
                    changed |= putOpeningNpc(npc);
                }
            }
            return changed;
        }
        return ensureFallbackOpeningNpcs();
    }

    /** 资源意外缺失时仍保留最小可运行人物集合。 */
    private static boolean ensureFallbackOpeningNpcs() {
        boolean changed = false;
        changed |= putOpeningNpc(createOpeningNpc(
                100,
                "梦屿应急联络员",
                "负责逐光会筹建、救援调度和临时约章的轮值席位。",
                "应急通信协调员",
                "先别问总部在哪。我们现在连一张不会过时的总部平面图都没有。"));
        changed |= putOpeningNpc(createOpeningNpc(
                101,
                "白芷",
                "梦屿中央医院感染医学科住院医师，目前在梦屿与外缘带地区的阿拜多斯医院工作。\n"
                        + "随着你们逐渐的认识，你对她的了解会变多",
                "临床观察员",
                "镇上正在筹建逐光会。先照顾好伤员，等你安顿下来，我再把具体安排告诉你。"));
        changed |= putOpeningNpc(createOpeningNpc(
                102,
                "江晚",
                "负责整理感染风险记录，始终区分已经证实的事实和仍待验证的推测。",
                "风险记录员",
                "已知事实、合理推测和没有证据的愿望，必须写在不同的栏里。"));
        changed |= putOpeningNpc(createOpeningNpc(
                103,
                "梁朔",
                "仍在外缘带维护供电、供水和通信设施的基础设施工程师。",
                "基础设施工程师",
                "这里是外缘中继。听见就回一个字，别发长句，电压不够你们客套。"));
        changed |= putOpeningNpc(createOpeningNpc(
                104,
                "尉迟南",
                "通兰天文台的远距通信值守员，保存着尚未解释的异常信号记录。",
                "远距通信值守员",
                "我需要先确认时间戳。能收到和应该广播，是两个问题。"));
        changed |= putOpeningNpc(createOpeningNpc(
                105,
                "周岑",
                "负责组织人类逐光联合会筹备、人员登记与基地建设的临时负责人。",
                "逐光会筹备处负责人",
                "先把要做的事和能给出的保障写清楚，再让别人决定要不要加入。"));
        return changed;
    }

    private static boolean putOpeningNpc(NpcData npc) {
        if (npcCache.containsKey(npc.getNpcId())) {
            return false;
        }
        npcCache.put(npc.getNpcId(), npc);
        return true;
    }

    private static NpcData createOpeningNpc(
            int npcId,
            String name,
            String introduction,
            String profession,
            String openingDialogue) {
        NpcData npc = new NpcData(npcId, name, introduction, "未知", profession);
        npc.setStoryStageId(1);
        npc.setDialogues(new ArrayList<>(List.of(openingDialogue)));
        return npc;
    }

}
