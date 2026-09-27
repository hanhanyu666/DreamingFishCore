package com.hhy.dreamingfishcore.gameplay.npc_system;

import com.hhy.dreamingfishcore.gameplay.hospital_system.HospitalStory;
import com.hhy.dreamingfishcore.gameplay.hospital_system.DailyTemplateSupportService;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.gameplay.npc_system.network.Packet_OpenNpcDialogueGUI;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcMessageManager;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
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
    private static final int MINIMAL_SHELL_ACTION_REQUIREMENT = 2_000_000_000;
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
            npcCache = new ConcurrentHashMap<>();
            boolean repaired = false;
            for (Map.Entry<Integer, NpcData> entry : loaded.entrySet()) {
                Integer id = entry.getKey();
                NpcData npc = entry.getValue();
                if (id != null && npc != null && id == npc.getNpcId() && id > 0) {
                    // Historical/custom NPC profiles are data, not disposable
                    // content.  Runtime content policy only controls which
                    // profile is used by the current story; it must never
                    // erase the operator's archive during startup.
                    npcCache.put(id, npc);
                } else {
                    repaired = true;
                }
            }
            configWritable = true;
            if (repaired || ensureBuiltInOpeningNpcs()) {
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
        return openNpcDialogue(player, npcId, -1, true);
    }

    public static boolean openNpcDialogue(ServerPlayer player, int npcId, int entityId) {
        return openNpcDialogue(player, npcId, entityId, true);
    }

    /**
     * 打开一次新的 NPC 对话会话。剧情脚本需要区分“玩家重新打开 NPC”和
     * “服务端因状态变化刷新当前界面”，所以刷新入口明确传 false，避免把
     * 同一段江晚接待误算成下一次对话。
     */
    private static boolean openNpcDialogue(
            ServerPlayer player, int npcId, int entityId, boolean newConversation) {
        Optional<NpcData> npc = getNpc(npcId);
        if (npc.isEmpty()) {
            return false;
        }
        if (newConversation) {
            HospitalStory.clearResponse(player.getUUID());
            StoryManager.onNpcDialogueOpened(player, npcId);
        }
        DreamingFishCore_NetworkManager.sendToClient(new Packet_OpenNpcDialogueGUI(createViewData(player, npc.get(), entityId)), player);
        if (!NpcMessageManager.deliverInteractionMessage(player, npcId)) {
            // 即使本次没有新消息，也刷新关系值与可能刚解锁的预设回复。
            NpcMessageManager.syncToClient(player);
        }
        return true;
    }

    public static void handleInteraction(ServerPlayer player, int npcId, int entityId, NpcInteractionType interactionType) {
        if (player == null || !AuthSessionGuard.isAuthenticated(player) || !player.isAlive()) return;
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
        if (interactionType == NpcInteractionType.HOSPITAL_REVIEW || interactionType == NpcInteractionType.DAILY_TEMPLATE_SUPPORT) {
            try {
                if (interactionType == NpcInteractionType.HOSPITAL_REVIEW) {
                    if (!HospitalStory.canReview(player, npcId)) throw new IllegalStateException("当前 NPC 无法办理正式复查");
                    HospitalStory.review(player);
                } else {
                    if (!DailyTemplateSupportService.isAvailable(player, npcId)) throw new IllegalStateException("当前 NPC 无法办理每日模板维护");
                    HospitalStory.setServiceResponse(player, npcId, DailyTemplateSupportService.claim(player));
                }
            } catch (RuntimeException exception) {
                HospitalStory.setServiceResponse(player, npcId, exception.getMessage() == null
                        ? "本次办理暂未完成，请稍后再试。" : exception.getMessage());
                DreamingFishCore.LOGGER.warn("医院服务未完成：{} / {}", player.getScoreboardName(), exception.getMessage());
            }
            openNpcDialogue(player, npcId, entityId, false);
            return;
        }
        // 赠礼功能尚未开放。服务端也必须拒绝，不能只依赖客户端隐藏按钮。
        if (interactionType == NpcInteractionType.GIFT_ITEM && !GIFT_INTERACTIONS_ENABLED) {
            return;
        }

        int requiredFavorability = npc.getActionFavorabilityRequirements().getOrDefault(interactionType.name(), 0);
        if (!NpcRelationManager.canUseAction(npcId, player.getUUID(), interactionType, requiredFavorability)) {
            openNpcDialogue(player, npcId, entityId, false);
            return;
        }

        if (interactionType == NpcInteractionType.DIALOGUE) {
            if (HospitalStory.clearResponse(player.getUUID())) {
                openNpcDialogue(player, npcId, entityId, false);
                return;
            }
            // 每名玩家与每个 NPC 的首次交谈只奖励一次。effectId 会随关系数据持久化，
            // 因此反复点击、重新打开界面、重连或重启服务器都不能重复获得好感度。
            NpcRelationManager.applyFavorabilityEffect(
                    npcId,
                    player.getUUID(),
                    DIALOGUE_FAVORABILITY_EFFECT_ID,
                    1);
            // 普通 NPC 对话由客户端本地翻页；余梦期江晚的检查流程需要服务端
            // 在每个阶段节点切换后重新下发台词，才能让“正在检查……”、检查结果
            // 和“请服用试剂”真正出现在下一次交谈中。结果节点执行奖励后也要刷新，
            // 否则客户端会停留在上一段台词，看起来像流程没有继续。
            String beforeDialogueRevision = StoryManager.getDialogueRevision(player, npcId)
                    .orElse("");
            Entity interactionTarget = entityId < 0
                    ? null
                    : player.serverLevel().getEntity(entityId);
            StoryManager.onNpcInteraction(player, npcId, interactionTarget);
            String afterDialogueRevision = StoryManager.getDialogueRevision(player, npcId)
                    .orElse("");
            if (!beforeDialogueRevision.equals(afterDialogueRevision)) {
                openNpcDialogue(player, npcId, entityId, false);
                return;
            }
        } else if (interactionType == NpcInteractionType.GIFT_ITEM) {
            handleGift(player, npc);
        }

        if (interactionType == NpcInteractionType.DIALOGUE) {
            NpcMessageManager.syncToClient(player);
            return;
        }
        openNpcDialogue(player, npcId, entityId, false);
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
                HospitalStory.serviceResponse(player, npc.getNpcId()).orElseGet(() ->
                        npc.getNpcId() == StoryNpcContentPolicy.MEDICAL_STAFF_ID
                                ? List.of(DailyTemplateSupportService.description(player))
                                : StoryManager.getDialogueOverride(player, npc.getNpcId())
                                .orElseGet(() -> StoryNpcContentPolicy.isIdentityShell(npc.getNpcId())
                                        ? List.of() : npc.getDialogues())),
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
            if (type == NpcInteractionType.HOSPITAL_REVIEW) {
                if (HospitalStory.canReview(player, npc.getNpcId())) actions.add(type.name());
                continue;
            }
            if (type == NpcInteractionType.DAILY_TEMPLATE_SUPPORT) {
                if (DailyTemplateSupportService.isAvailable(player, npc.getNpcId())) actions.add(type.name());
                continue;
            }
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

    /**
     * 为开场内容补齐约定的临时 NPC 槽位。
     *
     * <p>生产服可能已经有自己的 npc_data.json；所有内置人物都只使用
     * putIfAbsent，不会覆盖服主编辑过的同编号人物。身份壳只决定当前剧情未提供
     * 流程对白时的运行时显示，不会清空持久化档案。</p>
     */
    private static boolean ensureBuiltInOpeningNpcs() {
        Map<Integer, NpcData> builtInNpcs = BuiltInNpcProfileCatalog.loadProfiles();
        if (!builtInNpcs.isEmpty()) {
            boolean changed = false;
            for (NpcData npc : builtInNpcs.values()) {
                if (npc != null && StoryNpcContentPolicy.isRetained(npc.getNpcId())) {
                    // Only fill a missing built-in profile.  A server owner's
                    // existing profile is never rewritten by a content reload.
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
                101,
                "白芷",
                "梦屿中央医院感染医学科住院医师，目前在梦屿与外缘带地区的阿拜多斯医院工作。\n"
                        + "随着你们逐渐的认识，你对她的了解会变多",
                "临床观察员",
                "镇上正在筹建逐光会。先照顾好伤员，等你安顿下来，我再把具体安排告诉你。"));
        changed |= putOpeningNpc(createOpeningNpc(
                105,
                "周岑",
                "负责组织人类逐光联合会筹备、人员登记与基地建设的临时负责人。",
                "逐光会筹备处负责人",
                "先把要做的事和能给出的保障写清楚，再让别人决定要不要加入。"));
        changed |= putOpeningNpc(createMinimalShellNpc(
                StoryNpcContentPolicy.JIANGWAN_ID,
                "江晚",
                2,
                "dreamingfishcore:textures/entity/npc/jiangwan.png"));
        changed |= putOpeningNpc(createMinimalShellNpc(
                StoryNpcContentPolicy.LIANGSHUO_ID,
                "梁朔",
                1,
                "dreamingfishcore:textures/entity/npc/liangshuo.png"));
        changed |= putOpeningNpc(createMinimalShellNpc(
                StoryNpcContentPolicy.WEICHINAN_ID,
                "尉迟南",
                1,
                "dreamingfishcore:textures/entity/npc/weichinan.png"));
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
        npc.setDialogues(openingDialogue == null || openingDialogue.isBlank()
                ? new ArrayList<>() : new ArrayList<>(List.of(openingDialogue)));
        return npc;
    }

    /**
     * 仅在默认资源也无法读取、且该 NPC 完全缺失时建立可渲染身份壳。
     * 已有服主档案永远由 putOpeningNpc 保留，不会被这里清空。动作权限保持关闭
     * （除 DIALOGUE 外），避免回退壳因缺字段而意外开放玩法。
     */
    private static NpcData createMinimalShellNpc(
            int npcId, String name, int storyStageId, String skin) {
        NpcData npc = new NpcData(npcId, name, "", "", "");
        npc.setStoryStageId(storyStageId);
        npc.setDialogues(new ArrayList<>());
        npc.setActionFavorabilityRequirements(minimalShellActionRequirements());
        npc.setCurrentThought(null);
        npc.setWarningRules(new NpcWarningRuleData());
        NpcAppearanceData appearance = new NpcAppearanceData();
        appearance.setSkin(skin);
        appearance.setModel("slim");
        appearance.setShowName(true);
        npc.setAppearance(appearance);
        return npc;
    }

    private static Map<String, Integer> minimalShellActionRequirements() {
        Map<String, Integer> requirements = new HashMap<>();
        requirements.put("FOLLOW", MINIMAL_SHELL_ACTION_REQUIREMENT);
        requirements.put("WARNING_RULES", MINIMAL_SHELL_ACTION_REQUIREMENT);
        requirements.put("VIEW_BACKPACK", MINIMAL_SHELL_ACTION_REQUIREMENT);
        requirements.put("ASSIGN_TASK", MINIMAL_SHELL_ACTION_REQUIREMENT);
        requirements.put("GIFT_ITEM", MINIMAL_SHELL_ACTION_REQUIREMENT);
        requirements.put("DIALOGUE", 0);
        requirements.put("SET_HOME", MINIMAL_SHELL_ACTION_REQUIREMENT);
        return requirements;
    }

}
