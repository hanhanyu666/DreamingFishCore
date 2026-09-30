package com.hhy.dreamingfishcore.gameplay.organization_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.economy_bridge.EconomySystemBridge;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 玩家组织的服务端唯一写入口。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li><b>服务器权威</b>：所有变更都在这里校验并落盘，客户端只拿快照、只发请求；</li>
 *   <li><b>一人一会</b>：一个玩家同一时间只能属于一个组织，查表 {@link #PLAYER_ORGANIZATION} 保证 O(1)；</li>
 *   <li><b>权限单点</b>：能否审批/邀请/踢人/改职位一律走 {@link OrganizationPermissions}；</li>
 *   <li><b>落盘时机</b>：与项目其他世界数据一致，标脏后由 {@code WorldDataLifecycleEvents} 定期与关服时写入。</li>
 * </ul>
 */
public final class OrganizationManager {

    private static final String DATA_FILE = "organizations.json";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    /** 组织 id → 组织。保持插入顺序，便于稳定输出。 */
    private static final Map<String, Organization> ORGANIZATIONS = new LinkedHashMap<>();
    /** 玩家 UUID 字符串 → 组织 id。 */
    private static final Map<String, String> PLAYER_ORGANIZATION = new HashMap<>();

    private static boolean loaded;
    private static boolean dirty;
    /**
     * 本次启动没能安全读到存档（文件损坏 / 版本不支持 / IO 失败）。
     *
     * <p>置位后**禁止一切写盘与写操作**：内存里是空列表，一旦回写就会把玩家真实的组织数据
     * 清成空数组 —— 一次瞬时读取失败就能造成不可逆的数据丢失。重启后会自动重试读取。</p>
     */
    private static boolean persistenceUnsafe;

    private OrganizationManager() {
    }

    // ==================== 生命周期 ====================

    public static synchronized void loadWorldData(MinecraftServer server) {
        ORGANIZATIONS.clear();
        PLAYER_ORGANIZATION.clear();
        loaded = false;
        dirty = false;
        persistenceUnsafe = false;

        if (server == null) {
            return;
        }
        try {
            OrganizationDocument document = JsonDataStore.read(
                    WorldDataPaths.resolve(server, DATA_FILE),
                    GSON,
                    OrganizationDocument.class,
                    OrganizationDocument::new);
            LoadOutcome outcome = classify(document);
            if (outcome == LoadOutcome.UNSUPPORTED_SCHEMA) {
                // 例如用旧版本打开新版本写的存档：保留文件内容，本次只读且绝不回写。
                int fileVersion = document == null ? -1 : document.getSchemaVersion();
                enterReadOnlyProtectionOnVersionMismatch(
                        fileVersion, OrganizationDocument.CURRENT_SCHEMA_VERSION);
                return;
            }
            if (outcome == LoadOutcome.FAILED) {
                enterReadOnlyProtection("组织数据读取结果不可用（解析为空）");
                return;
            }
            int skipped = 0;
            for (Organization organization : document.getOrganizations()) {
                if (organization == null) {
                    skipped++;
                    continue;
                }
                try {
                    organization.validate();
                } catch (RuntimeException exception) {
                    skipped++;
                    DreamingFishCore.LOGGER.error("跳过损坏的组织条目：{}", organization.id(), exception);
                    continue;
                }
                // 资金池/领地登记只是可修复的小问题，静默归一而不是跳过整个组织。
                if (organization.normalizeLinkageData()) {
                    dirty = true;
                }
                if (ORGANIZATIONS.containsKey(organization.id())) {
                    skipped++;
                    DreamingFishCore.LOGGER.error("跳过重复的组织 id：{}", organization.id());
                    continue;
                }
                ORGANIZATIONS.put(organization.id(), organization);
            }
            rebuildPlayerIndex();
            loaded = true;
            DreamingFishCore.LOGGER.info(
                    "组织数据加载完成：{} 个组织，跳过 {} 条",
                    ORGANIZATIONS.size(), skipped);
        } catch (IOException | RuntimeException exception) {
            enterReadOnlyProtectionOnFailure("组织数据加载失败", exception);
        }
    }

    /** 读档结果的分类；抽成纯函数便于单测（决定是否进入只读保护）。 */
    enum LoadOutcome {
        /** 文件正常，含"文件不存在、使用默认空文档"的情况。 */
        OK,
        /** 文件 schema 版本不是本版本能安全处理的。 */
        UNSUPPORTED_SCHEMA,
        /** 读取结果不可用。 */
        FAILED
    }

    static LoadOutcome classify(OrganizationDocument document) {
        if (document == null) {
            return LoadOutcome.FAILED;
        }
        if (document.getSchemaVersion() != OrganizationDocument.CURRENT_SCHEMA_VERSION) {
            return LoadOutcome.UNSUPPORTED_SCHEMA;
        }
        return LoadOutcome.OK;
    }

    /**
     * 进入只读保护：内存保持空数据供界面显示，但禁止写操作与写盘。
     *
     * <p>这样既不会让终端页面卡在"正在同步"，也不会用空数据覆盖玩家存档。</p>
     */
    private static void enterReadOnlyProtection(String reason) {
        persistenceUnsafe = true;
        loaded = true;
        DreamingFishCore.LOGGER.error(
                "{}，本次启动进入只读保护（不会覆盖存档，重启后自动重试）", reason);
    }

    /** 读档抛异常时进入只读保护，并把异常打出来。 */
    private static void enterReadOnlyProtectionOnFailure(String reason, Throwable cause) {
        persistenceUnsafe = true;
        loaded = true;
        DreamingFishCore.LOGGER.error(
                "{}，本次启动进入只读保护（不会覆盖存档，重启后自动重试）", reason, cause);
    }

    /** 存档 schema 版本超出本版本处理能力时进入只读保护。 */
    private static void enterReadOnlyProtectionOnVersionMismatch(int fileVersion, int supportedVersion) {
        persistenceUnsafe = true;
        loaded = true;
        DreamingFishCore.LOGGER.error(
                "组织数据版本不支持（文件 schemaVersion={}，本版本支持 {}），"
                        + "本次启动进入只读保护（不会覆盖存档，重启后自动重试）",
                fileVersion, supportedVersion);
    }

    public static synchronized boolean saveIfDirty(MinecraftServer server) {
        if (persistenceUnsafe) {
            // 读档就没成功：内存里是空列表，回写等于把玩家的组织清空。
            if (dirty) {
                DreamingFishCore.LOGGER.error(
                        "组织数据处于只读保护中，已跳过本次保存以免覆盖存档");
                dirty = false;
            }
            return true;
        }
        if (!dirty || server == null || !loaded) {
            return true;
        }
        OrganizationDocument document = new OrganizationDocument();
        document.setOrganizations(new ArrayList<>(ORGANIZATIONS.values()));
        try {
            JsonDataStore.writeAtomic(WorldDataPaths.resolve(server, DATA_FILE), GSON, document);
            dirty = false;
            return true;
        } catch (IOException exception) {
            DreamingFishCore.LOGGER.error("组织数据保存失败，保留脏标记等待重试", exception);
            return false;
        }
    }

    public static synchronized void clearWorldCache() {
        ORGANIZATIONS.clear();
        PLAYER_ORGANIZATION.clear();
        loaded = false;
        dirty = false;
        persistenceUnsafe = false;
    }

    /** 玩家登录时刷新离线显示名。 */
    public static synchronized void onPlayerLogin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        findByPlayer(player.getUUID()).ifPresent(organization -> {
            organization.refreshMemberName(player.getUUID(), player.getScoreboardName());
            markDirty();
        });
    }

    private static void markDirty() {
        dirty = true;
    }

    private static void rebuildPlayerIndex() {
        PLAYER_ORGANIZATION.clear();
        for (Organization organization : ORGANIZATIONS.values()) {
            for (String playerId : organization.members().keySet()) {
                PLAYER_ORGANIZATION.put(playerId, organization.id());
            }
        }
    }

    // ==================== 查询 ====================

    public static synchronized Optional<Organization> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(ORGANIZATIONS.get(id.trim()));
    }

    public static synchronized Optional<Organization> findByName(String name) {
        String key = OrganizationNames.normalize(name);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return ORGANIZATIONS.values().stream()
                .filter(organization -> OrganizationNames.normalize(organization.name()).equals(key))
                .findFirst();
    }

    public static synchronized Optional<Organization> findByPlayer(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        String organizationId = PLAYER_ORGANIZATION.get(playerId.toString());
        return organizationId == null ? Optional.empty() : findById(organizationId);
    }

    public static synchronized List<Organization> all() {
        List<Organization> list = new ArrayList<>(ORGANIZATIONS.values());
        list.sort(Comparator
                .comparingInt((Organization organization) -> -organization.memberCount())
                .thenComparing(Organization::name));
        return list;
    }

    public static synchronized int count() {
        return ORGANIZATIONS.size();
    }

    /** 组织数据是否已加载（设备维护等低频逻辑据此跳过启动期）。 */
    public static synchronized boolean isLoaded() {
        return loaded;
    }

    /** 已加载**且读档可靠**才允许写入：读档失败时保持只读，避免把空数据写回去盖掉存档。 */
    private static boolean writable() {
        return loaded && !persistenceUnsafe;
    }

    /** 只读保护下给玩家的失败原因；区分"还没加载"与"加载失败"两种情况。 */
    private static String notWritableMessage() {
        return persistenceUnsafe
                ? "组织数据读取失败，本次已进入只读保护；请重启服务器后重试"
                : "组织数据尚未就绪";
    }

    private static OrganizationConfig config() {
        return OrganizationConfig.current();
    }

    private static boolean requireAuthenticated(ServerPlayer player) {
        return player != null && AuthSessionGuard.isAuthenticated(player);
    }

    // ==================== 创建与解散 ====================

    public static synchronized OrganizationResult create(ServerPlayer player, String rawName) {
        if (!writable()) {
            return OrganizationResult.fail(notWritableMessage());
        }
        if (!requireAuthenticated(player)) {
            return OrganizationResult.fail("登录认证未完成");
        }
        OrganizationConfig config = config();
        if (!config.isEnabled()) {
            return OrganizationResult.fail("服务器当前未启用组织功能");
        }
        if (findByPlayer(player.getUUID()).isPresent()) {
            return OrganizationResult.fail("你已经属于一个组织，需要先退出");
        }
        String name = rawName == null ? "" : rawName.trim();
        String invalid = OrganizationNames.validate(name, config.getNameMinLength(),
                config.getNameMaxLength());
        if (invalid != null) {
            return OrganizationResult.fail(invalid);
        }
        if (findByName(name).isPresent()) {
            return OrganizationResult.fail("已经存在同名组织");
        }
        if (ORGANIZATIONS.size() >= config.getMaxOrganizations()) {
            return OrganizationResult.fail("本服务器组织数量已达上限（"
                    + config.getMaxOrganizations() + "）");
        }

        int cost = config.getCreationCost();
        if (cost > 0) {
            // 只在经济服务可用时拦"余额不足"；不可用时按免费放行（服主可能压根没装经济系统）。
            int balance = EconomySystemBridge.balance(player);
            if (balance >= 0 && balance < cost) {
                return OrganizationResult.fail(
                        "梦鱼币不足：创建组织需要 " + cost + " 梦鱼币，你当前有 " + balance);
            }
        }

        long now = System.currentTimeMillis();
        Organization organization = new Organization(
                UUID.randomUUID().toString(), name, player.getUUID().toString(),
                player.getScoreboardName(), now);
        ORGANIZATIONS.put(organization.id(), organization);
        PLAYER_ORGANIZATION.put(player.getUUID().toString(), organization.id());
        markDirty();

        if (cost > 0) {
            OrganizationResult charged = chargeCreationCost(player, organization, cost);
            if (charged != null) {
                return charged;
            }
        }

        DreamingFishCore.LOGGER.info("玩家 {} 创建了组织「{}」（实付 {} 梦鱼币）",
                player.getScoreboardName(), name, organization.creationCostPaid());
        return OrganizationResult.ok(cost > 0 && organization.creationCostPaid() > 0
                ? "组织「" + name + "」已创建，你是会长（消耗 " + organization.creationCostPaid() + " 梦鱼币）"
                : "组织「" + name + "」已创建，你是会长");
    }

    /**
     * 扣除创建费，并把结果落到组织记录上。
     *
     * <p>顺序刻意是"先建好再扣款"：扣款失败时可以在同一把锁里把组织**完整回滚**，
     * 不会出现"钱扣了但组织没建成"。成功扣款后立即写盘一次，避免"钱已扣、数据只在内存"；
     * 这次写盘失败则退款并回滚。</p>
     *
     * @return {@code null} 表示成功；否则返回给玩家的失败结果
     */
    private static OrganizationResult chargeCreationCost(ServerPlayer player, Organization organization,
                                                         int cost) {
        EconomySystemBridge.MutationResult charged = EconomySystemBridge.debit(
                player, cost, "organization/create",
                "创建组织「" + organization.name() + "」");
        if (charged == EconomySystemBridge.MutationResult.NOT_AVAILABLE) {
            // 经济服务不可用：按免费放行，组织记录里的实付金额保持 0，解散时也不会退款。
            DreamingFishCore.LOGGER.warn("经济服务不可用，本次创建组织未收取费用：{}",
                    organization.name());
            return null;
        }
        if (charged != EconomySystemBridge.MutationResult.SUCCESS) {
            rollbackOrganization(organization);
            return OrganizationResult.fail(charged == EconomySystemBridge.MutationResult.INSUFFICIENT_FUNDS
                    ? "梦鱼币不足：创建组织需要 " + cost + " 梦鱼币"
                    : "扣款失败，组织未创建（经济服务拒绝了本次交易）");
        }

        organization.setCreationCostPaid(cost);
        if (!saveIfDirty(player.getServer())) {
            // 钱已经扣掉，但组织数据写不进存档：退款并回滚，别让玩家白花钱。
            EconomySystemBridge.MutationResult refunded = EconomySystemBridge.credit(
                    player, cost, "organization/create_rollback",
                    "创建组织失败退款「" + organization.name() + "」");
            rollbackOrganization(organization);
            DreamingFishCore.LOGGER.error("组织创建后写盘失败，已回滚并尝试退款（退款结果 {}）：{}",
                    refunded, organization.name());
            return OrganizationResult.fail(refunded == EconomySystemBridge.MutationResult.SUCCESS
                    ? "组织数据写入失败，创建已取消，费用已退回"
                    : "组织数据写入失败，创建已取消；退款未能完成，请联系服主");
        }
        return null;
    }

    /** 把刚创建的组织从内存里彻底撤掉（扣款/写盘失败时使用）。 */
    private static void rollbackOrganization(Organization organization) {
        ORGANIZATIONS.remove(organization.id());
        PLAYER_ORGANIZATION.values().removeIf(organization.id()::equals);
    }

    public static synchronized OrganizationResult disband(ServerPlayer player) {
        Organization organization = findByPlayer(player.getUUID()).orElse(null);
        if (organization == null) {
            return OrganizationResult.fail("你还没有组织");
        }
        OrganizationRank rank = organization.rankOf(player.getUUID()).orElse(null);
        if (!OrganizationPermissions.canDisband(rank)) {
            return OrganizationResult.fail("只有会长可以解散组织");
        }
        return disbandInternal(organization, "组织「" + organization.name() + "」已解散", player);
    }

    /** 服主强制解散（命令层专用）。 */
    public static synchronized OrganizationResult forceDisband(String organizationId) {
        Organization organization = findById(organizationId).orElse(null);
        if (organization == null) {
            return OrganizationResult.fail("组织不存在");
        }
        // 强制解散是管理手段，不发放退款。
        return disbandInternal(organization, "组织「" + organization.name() + "」已被服主解散", null);
    }

    private static OrganizationResult disbandInternal(Organization organization, String message,
                                                     ServerPlayer refundTo) {
        ORGANIZATIONS.remove(organization.id());
        for (String playerId : organization.members().keySet()) {
            PLAYER_ORGANIZATION.remove(playerId);
        }
        // 组织没了，它绑定的聚居地设备也必须停下：否则设备会一直"工作"，
        // 维护费却再也扣不到任何人头上。
        int unbound = SettlementFilterRegistry.unbindAllOf(organization.id());
        if (unbound > 0) {
            DreamingFishCore.LOGGER.info("组织「{}」解散，已解除 {} 台设备的绑定",
                    organization.name(), unbound);
        }
        markDirty();

        String refundNote = refundDisbandCost(organization, refundTo)
                + refundDisbandFunds(organization, refundTo);
        DreamingFishCore.LOGGER.info("组织「{}」已解散（实付创建费 {}，资金池 {}，退款说明：{}）",
                organization.name(), organization.creationCostPaid(), organization.funds(),
                refundNote.isEmpty() ? "无" : refundNote);
        return OrganizationResult.ok(message + refundNote);
    }

    /**
     * 解散时把资金池余额全额退给发起人。
     *
     * <p>只有会长能解散，所以发起人就是会长；余额是成员共同捐进来的，按用户确认的规则全额退。
     * 退款失败只记日志、不阻塞解散 —— 组织已经从内存里摘掉了，卡在这里只会让状态更难看。</p>
     */
    private static String refundDisbandFunds(Organization organization, ServerPlayer refundTo) {
        int funds = organization.funds();
        if (funds <= 0 || refundTo == null) {
            return "";
        }
        EconomySystemBridge.MutationResult result = EconomySystemBridge.credit(
                refundTo, funds, "organization/funds_refund",
                "解散组织「" + organization.name() + "」退还资金池余额");
        if (result != EconomySystemBridge.MutationResult.SUCCESS) {
            DreamingFishCore.LOGGER.error("组织「{}」解散时资金池退款失败（应退 {}，结果 {}）",
                    organization.name(), funds, result);
            return "（应退资金池 " + funds + " 梦鱼币，但经济服务未能发放，请联系服主）";
        }
        return "（已退回资金池 " + funds + " 梦鱼币）";
    }

    /**
     * 按**实付**创建费退还一部分给解散发起人。
     *
     * @return 追加给玩家的说明文本（无退款时为空串）
     */
    private static String refundDisbandCost(Organization organization, ServerPlayer refundTo) {
        int refund = config().refundFor(organization.creationCostPaid());
        if (refund <= 0 || refundTo == null) {
            return "";
        }
        EconomySystemBridge.MutationResult result = EconomySystemBridge.credit(
                refundTo, refund, "organization/disband_refund",
                "解散组织「" + organization.name() + "」退还部分创建费");
        if (result != EconomySystemBridge.MutationResult.SUCCESS) {
            return "（应退 " + refund + " 梦鱼币，但经济服务未能发放，请联系服主）";
        }
        // 退款已到账，立刻尝试落盘一次；失败只记录，不影响玩家已收到的退款。
        if (!saveIfDirty(refundTo.getServer())) {
            DreamingFishCore.LOGGER.error(
                    "解散退款已发放，但组织数据写盘失败，重启后该组织可能重新出现：{}",
                    organization.name());
        }
        return "（已退回 " + refund + " 梦鱼币）";
    }

    // ==================== 申请 / 邀请 ====================

    public static synchronized OrganizationResult apply(ServerPlayer player, String organizationId) {
        if (!requireAuthenticated(player)) {
            return OrganizationResult.fail("登录认证未完成");
        }
        Organization organization = findById(organizationId).orElse(null);
        if (organization == null) {
            return OrganizationResult.fail("组织不存在");
        }
        if (findByPlayer(player.getUUID()).isPresent()) {
            return OrganizationResult.fail("你已经属于一个组织");
        }
        if (organization.isMember(player.getUUID())) {
            return OrganizationResult.fail("你已经是该组织的成员");
        }
        if (organization.applications().containsKey(player.getUUID().toString())) {
            return OrganizationResult.fail("你已经提交过申请，等待审批");
        }
        if (organization.memberCount() >= config().getMaxMembers()) {
            return OrganizationResult.fail("该组织成员已满");
        }
        organization.applications().put(player.getUUID().toString(), System.currentTimeMillis());
        markDirty();
        return OrganizationResult.ok("已提交加入「" + organization.name() + "」的申请");
    }

    public static synchronized OrganizationResult cancelApplication(ServerPlayer player,
                                                                    String organizationId) {
        Organization organization = findById(organizationId).orElse(null);
        if (organization == null || player == null) {
            return OrganizationResult.fail("组织不存在");
        }
        if (organization.applications().remove(player.getUUID().toString()) == null) {
            return OrganizationResult.fail("你还没有提交申请");
        }
        markDirty();
        return OrganizationResult.ok("已撤回对「" + organization.name() + "」的申请");
    }

    public static synchronized OrganizationResult reviewApplication(ServerPlayer actor,
                                                                    String applicantId,
                                                                    boolean accept) {
        Organization organization = findByPlayer(actor.getUUID()).orElse(null);
        if (organization == null) {
            return OrganizationResult.fail("你还没有组织");
        }
        OrganizationRank rank = organization.rankOf(actor.getUUID()).orElse(null);
        if (!OrganizationPermissions.canReviewApplications(rank)) {
            return OrganizationResult.fail("只有干部及以上可以审批申请");
        }
        String key = applicantId == null ? "" : applicantId.trim();
        if (organization.applications().remove(key) == null) {
            return OrganizationResult.fail("该申请已不存在");
        }
        if (!accept) {
            markDirty();
            return OrganizationResult.ok("已拒绝该申请");
        }
        if (PLAYER_ORGANIZATION.containsKey(key)) {
            markDirty();
            return OrganizationResult.fail("该玩家已经加入了其他组织，申请已作废");
        }
        if (organization.memberCount() >= config().getMaxMembers()) {
            markDirty();
            return OrganizationResult.fail("组织成员已满，无法通过该申请");
        }
        UUID applicant = safeUuid(key);
        if (applicant == null) {
            markDirty();
            return OrganizationResult.fail("申请数据无效");
        }
        String name = displayNameOf(applicant);
        organization.addMember(applicant, name, OrganizationRank.MEMBER, System.currentTimeMillis());
        PLAYER_ORGANIZATION.put(key, organization.id());
        markDirty();
        return OrganizationResult.ok("已同意 " + name + " 加入组织");
    }

    public static synchronized OrganizationResult invite(ServerPlayer actor, ServerPlayer target) {
        Organization organization = findByPlayer(actor.getUUID()).orElse(null);
        if (organization == null) {
            return OrganizationResult.fail("你还没有组织");
        }
        if (target == null) {
            return OrganizationResult.fail("只能邀请在线玩家");
        }
        OrganizationRank rank = organization.rankOf(actor.getUUID()).orElse(null);
        if (!OrganizationPermissions.canInvite(rank)) {
            return OrganizationResult.fail("只有干部及以上可以邀请");
        }
        if (target.getUUID().equals(actor.getUUID())) {
            return OrganizationResult.fail("不能邀请自己");
        }
        if (findByPlayer(target.getUUID()).isPresent()) {
            return OrganizationResult.fail(target.getScoreboardName() + " 已经属于某个组织");
        }
        if (organization.memberCount() >= config().getMaxMembers()) {
            return OrganizationResult.fail("组织成员已满");
        }
        String key = target.getUUID().toString();
        if (organization.invites().containsKey(key)) {
            return OrganizationResult.fail("已经邀请过对方，等待其接受");
        }
        organization.invites().put(key, System.currentTimeMillis());
        organization.applications().remove(key);
        organization.refreshMemberName(target.getUUID(), target.getScoreboardName());
        markDirty();
        return OrganizationResult.ok("已邀请 " + target.getScoreboardName() + " 加入组织");
    }

    public static synchronized OrganizationResult respondInvite(ServerPlayer player,
                                                                 String organizationId,
                                                                 boolean accept) {
        if (!requireAuthenticated(player)) {
            return OrganizationResult.fail("登录认证未完成");
        }
        Organization organization = findById(organizationId).orElse(null);
        if (organization == null) {
            return OrganizationResult.fail("组织不存在");
        }
        String key = player.getUUID().toString();
        if (organization.invites().remove(key) == null) {
            return OrganizationResult.fail("没有待处理的邀请");
        }
        if (!accept) {
            markDirty();
            return OrganizationResult.ok("已婉拒「" + organization.name() + "」的邀请");
        }
        if (findByPlayer(player.getUUID()).isPresent()) {
            markDirty();
            return OrganizationResult.fail("你已经属于一个组织");
        }
        if (organization.memberCount() >= config().getMaxMembers()) {
            markDirty();
            return OrganizationResult.fail("该组织成员已满");
        }
        organization.addMember(player.getUUID(), player.getScoreboardName(),
                OrganizationRank.MEMBER, System.currentTimeMillis());
        PLAYER_ORGANIZATION.put(key, organization.id());
        markDirty();
        return OrganizationResult.ok("已加入组织「" + organization.name() + "」");
    }

    // ==================== 成员管理 ====================

    public static synchronized OrganizationResult leave(ServerPlayer player) {
        Organization organization = findByPlayer(player.getUUID()).orElse(null);
        if (organization == null) {
            return OrganizationResult.fail("你还没有组织");
        }
        if (organization.isLeader(player.getUUID())) {
            return OrganizationResult.fail("会长不能直接退出，请先转让会长或解散组织");
        }
        organization.removeMember(player.getUUID());
        PLAYER_ORGANIZATION.remove(player.getUUID().toString());
        markDirty();
        return OrganizationResult.ok("已退出组织「" + organization.name() + "」");
    }

    public static synchronized OrganizationResult kick(ServerPlayer actor, UUID targetId) {
        Organization organization = findByPlayer(actor.getUUID()).orElse(null);
        if (organization == null) {
            return OrganizationResult.fail("你还没有组织");
        }
        if (targetId == null) {
            return OrganizationResult.fail("目标无效");
        }
        if (targetId.equals(actor.getUUID())) {
            return OrganizationResult.fail("不能把自己踢出组织");
        }
        OrganizationRank actorRank = organization.rankOf(actor.getUUID()).orElse(null);
        OrganizationRank targetRank = organization.rankOf(targetId).orElse(null);
        if (targetRank == null) {
            return OrganizationResult.fail("对方不是本组织成员");
        }
        if (!OrganizationPermissions.canKick(actorRank, targetRank)) {
            return OrganizationResult.fail("你的职位不足以移出对方");
        }
        String targetName = organization.members().get(targetId.toString()).lastName();
        organization.removeMember(targetId);
        PLAYER_ORGANIZATION.remove(targetId.toString());
        markDirty();
        return OrganizationResult.ok("已将 " + targetName + " 移出组织");
    }

    public static synchronized OrganizationResult setRank(ServerPlayer actor, UUID targetId,
                                                          OrganizationRank newRank) {
        Organization organization = findByPlayer(actor.getUUID()).orElse(null);
        if (organization == null) {
            return OrganizationResult.fail("你还没有组织");
        }
        if (targetId == null || newRank == null) {
            return OrganizationResult.fail("目标或职位无效");
        }
        if (targetId.equals(actor.getUUID())) {
            return OrganizationResult.fail("不能修改自己的职位");
        }
        OrganizationRank actorRank = organization.rankOf(actor.getUUID()).orElse(null);
        OrganizationRank targetRank = organization.rankOf(targetId).orElse(null);
        if (targetRank == null) {
            return OrganizationResult.fail("对方不是本组织成员");
        }
        if (!OrganizationPermissions.canChangeRank(actorRank, targetRank, newRank)) {
            return OrganizationResult.fail("你的职位不足以做出这个任免");
        }
        String targetName = organization.members().get(targetId.toString()).lastName();
        organization.members().get(targetId.toString()).setRank(newRank);
        markDirty();
        return OrganizationResult.ok("已将 " + targetName + " 的职位调整为 " + newRank.displayName());
    }

    public static synchronized OrganizationResult transferLeadership(ServerPlayer actor,
                                                                      UUID targetId) {
        Organization organization = findByPlayer(actor.getUUID()).orElse(null);
        if (organization == null) {
            return OrganizationResult.fail("你还没有组织");
        }
        OrganizationRank actorRank = organization.rankOf(actor.getUUID()).orElse(null);
        if (!OrganizationPermissions.canTransferLeadership(actorRank)) {
            return OrganizationResult.fail("只有会长可以转让会长职位");
        }
        if (targetId == null || targetId.equals(actor.getUUID())) {
            return OrganizationResult.fail("请选择其他成员接任");
        }
        Organization.Member target = organization.members().get(targetId.toString());
        if (target == null) {
            return OrganizationResult.fail("对方不是本组织成员");
        }
        organization.members().get(actor.getUUID().toString()).setRank(OrganizationRank.MEMBER);
        target.setRank(OrganizationRank.LEADER);
        organization.setLeaderId(targetId.toString());
        markDirty();
        return OrganizationResult.ok("已将会长转让给 " + target.lastName());
    }

    public static synchronized OrganizationResult setAnnouncement(ServerPlayer actor, String text) {
        Organization organization = findByPlayer(actor.getUUID()).orElse(null);
        if (organization == null) {
            return OrganizationResult.fail("你还没有组织");
        }
        OrganizationRank rank = organization.rankOf(actor.getUUID()).orElse(null);
        if (!OrganizationPermissions.canEditAnnouncement(rank)) {
            return OrganizationResult.fail("只有干部及以上可以编辑公告");
        }
        String invalid = OrganizationNames.validateAnnouncement(text,
                config().getAnnouncementMaxLength());
        if (invalid != null) {
            return OrganizationResult.fail(invalid);
        }
        organization.setAnnouncement(text == null ? "" : text.strip());
        markDirty();
        return OrganizationResult.ok("组织公告已更新");
    }

    public static synchronized OrganizationResult rename(ServerPlayer actor, String rawName) {
        Organization organization = findByPlayer(actor.getUUID()).orElse(null);
        if (organization == null) {
            return OrganizationResult.fail("你还没有组织");
        }
        OrganizationRank rank = organization.rankOf(actor.getUUID()).orElse(null);
        if (!OrganizationPermissions.canRename(rank)) {
            return OrganizationResult.fail("只有会长可以修改组织名称");
        }
        OrganizationConfig config = config();
        String name = rawName == null ? "" : rawName.trim();
        String invalid = OrganizationNames.validate(name, config.getNameMinLength(),
                config.getNameMaxLength());
        if (invalid != null) {
            return OrganizationResult.fail(invalid);
        }
        Optional<Organization> sameName = findByName(name);
        if (sameName.isPresent() && !sameName.get().id().equals(organization.id())) {
            return OrganizationResult.fail("已经存在同名组织");
        }
        String previous = organization.name();
        organization.setName(name);
        markDirty();
        return OrganizationResult.ok("组织名称已从「" + previous + "」改为「" + name + "」");
    }

    // ==================== 快照 ====================

    /** 构建一次同步所需的全部只读数据。 */
    // ==================== 资金池（A/D 联动） ====================

    /**
     * 成员向组织资金池捐款。
     *
     * <p>顺序是"先扣个人账户 → 入组织账 → 立刻写盘；写盘失败就退回个人账户"。
     * 这样既不会出现"钱花了组织没记上"，也不会出现"组织账加了钱但个人没扣"。</p>
     */
    public static synchronized OrganizationResult deposit(ServerPlayer player, int amount) {
        if (!requireAuthenticated(player)) {
            return OrganizationResult.fail("登录认证未完成");
        }
        if (!writable()) {
            return OrganizationResult.fail(notWritableMessage());
        }
        Organization organization = findByPlayer(player.getUUID()).orElse(null);
        if (organization == null) {
            return OrganizationResult.fail("你还没有组织");
        }
        if (!OrganizationPermissions.canDepositFunds(organization.rankOf(player.getUUID()).orElse(null))) {
            return OrganizationResult.fail("你不是组织成员");
        }
        if (amount <= 0) {
            return OrganizationResult.fail("捐款金额必须大于 0");
        }
        int limit = config().getMaxDeposit();
        if (amount > limit) {
            return OrganizationResult.fail("单次捐款不能超过 " + limit + " 梦鱼币");
        }

        EconomySystemBridge.MutationResult result = EconomySystemBridge.debit(
                player, amount, "organization/deposit", "向组织「" + organization.name() + "」捐款");
        if (result == EconomySystemBridge.MutationResult.NOT_AVAILABLE) {
            return OrganizationResult.fail("经济服务暂不可用，无法捐款");
        }
        if (result == EconomySystemBridge.MutationResult.INSUFFICIENT_FUNDS) {
            int balance = EconomySystemBridge.balance(player);
            return OrganizationResult.fail("梦鱼币不足：需要 " + amount
                    + (balance >= 0 ? "，你当前有 " + balance : ""));
        }
        if (result != EconomySystemBridge.MutationResult.SUCCESS) {
            return OrganizationResult.fail("捐款被经济服务拒绝，请稍后重试");
        }

        int funds = organization.depositFunds(amount);
        markDirty();
        if (!saveIfDirty(player.getServer())) {
            // 组织账没落盘：把钱退回去，避免"扣了个人余额但组织余额重启后消失"。
            organization.withdrawFundsUpTo(amount);
            markDirty();
            EconomySystemBridge.MutationResult refund = EconomySystemBridge.credit(
                    player, amount, "organization/deposit_rollback",
                    "组织资金池写入失败，捐款已退回");
            DreamingFishCore.LOGGER.error("组织「{}」捐款写盘失败，已回滚（退款结果 {}）",
                    organization.name(), refund);
            return OrganizationResult.fail(
                    refund == EconomySystemBridge.MutationResult.SUCCESS
                            ? "组织数据写入失败，捐款已退回你的账户"
                            : "组织数据写入失败，且退款未能发放，请联系服主");
        }
        DreamingFishCore.LOGGER.info("玩家 {} 向组织「{}」捐款 {} 梦鱼币（资金池 {}）",
                player.getScoreboardName(), organization.name(), amount, funds);
        return OrganizationResult.ok("已向组织「" + organization.name() + "」捐款 " + amount
                + " 梦鱼币，当前资金池 " + funds + " 梦鱼币");
    }

    /**
     * 从组织资金池扣维护费（供聚居地抑制设备的维护周期调用）。
     *
     * @return 实际扣到的金额；小于 {@code amount} 表示资金不足，设备应当停机
     */
    public static synchronized int chargeMaintenance(String organizationId, int amount) {
        if (!writable() || organizationId == null || organizationId.isBlank() || amount <= 0) {
            return 0;
        }
        Organization organization = findById(organizationId).orElse(null);
        if (organization == null) {
            return 0;
        }
        int taken = organization.withdrawFundsUpTo(amount);
        if (taken > 0) {
            markDirty();
        }
        return taken;
    }

    /** 组织资金池余额；组织不存在时返回 -1。 */
    public static synchronized int fundsOf(String organizationId) {
        Organization organization = findById(organizationId).orElse(null);
        return organization == null ? -1 : organization.funds();
    }

    // ==================== 组织领地（只存引用，A/B 联动） ====================

    /** 按领地 id 反查登记它的组织；设备据此判断"站的是不是自己组织的领地"。 */
    public static synchronized Optional<Organization> findByTerritory(String territoryId) {
        if (territoryId == null || territoryId.isBlank()) {
            return Optional.empty();
        }
        for (Organization organization : ORGANIZATIONS.values()) {
            if (organization.hasTerritory(territoryId)) {
                return Optional.of(organization);
            }
        }
        return Optional.empty();
    }

    /** 会长/副会长把自己名下的领地登记为组织领地。 */
    public static synchronized OrganizationResult registerTerritory(ServerPlayer actor, String territoryId) {
        if (!requireAuthenticated(actor)) {
            return OrganizationResult.fail("登录认证未完成");
        }
        if (!writable()) {
            return OrganizationResult.fail(notWritableMessage());
        }
        Organization organization = findByPlayer(actor.getUUID()).orElse(null);
        if (organization == null) {
            return OrganizationResult.fail("你还没有组织");
        }
        if (!OrganizationPermissions.canManageTerritories(
                organization.rankOf(actor.getUUID()).orElse(null))) {
            return OrganizationResult.fail("只有会长与副会长可以登记组织领地");
        }
        String id = territoryId == null ? "" : territoryId.trim();
        if (id.isEmpty()) {
            return OrganizationResult.fail("请指定要登记的领地");
        }
        if (organization.hasTerritory(id)) {
            return OrganizationResult.fail("这块领地已经登记给本组织了");
        }
        Organization holder = findByTerritory(id).orElse(null);
        if (holder != null) {
            return OrganizationResult.fail("这块领地已经登记给组织「" + holder.name() + "」");
        }
        int limit = config().getMaxRegisteredTerritories();
        if (organization.territoryCount() >= limit) {
            return OrganizationResult.fail("每个组织最多登记 " + limit + " 块领地");
        }
        MinecraftServer server = actor.getServer();
        if (!EconomySystemBridge.isTerritoryReadable(server)) {
            return OrganizationResult.fail("经济服务暂不可用，无法登记领地");
        }
        EconomySystemBridge.TerritoryInfo info = EconomySystemBridge.findTerritory(server, id).orElse(null);
        if (info == null) {
            return OrganizationResult.fail("找不到这块领地（可能已被移除）");
        }
        if (!info.ownerId().equals(actor.getUUID().toString())) {
            return OrganizationResult.fail("只能登记你自己名下的领地");
        }

        organization.registerTerritory(id, System.currentTimeMillis());
        markDirty();
        DreamingFishCore.LOGGER.info("玩家 {} 把领地「{}」登记为组织「{}」的组织领地",
                actor.getScoreboardName(), info.name(), organization.name());
        return OrganizationResult.ok("已把领地「" + info.name() + "」登记为组织领地（"
                + organization.territoryCount() + "/" + limit + "）");
    }

    /** 移除一条组织领地登记（未登记的 id 与已失效的 id 都接受）。 */
    public static synchronized OrganizationResult unregisterTerritory(ServerPlayer actor, String territoryId) {
        if (!requireAuthenticated(actor)) {
            return OrganizationResult.fail("登录认证未完成");
        }
        if (!writable()) {
            return OrganizationResult.fail(notWritableMessage());
        }
        Organization organization = findByPlayer(actor.getUUID()).orElse(null);
        if (organization == null) {
            return OrganizationResult.fail("你还没有组织");
        }
        if (!OrganizationPermissions.canManageTerritories(
                organization.rankOf(actor.getUUID()).orElse(null))) {
            return OrganizationResult.fail("只有会长与副会长可以移除组织领地");
        }
        String id = territoryId == null ? "" : territoryId.trim();
        if (id.isEmpty() || !organization.unregisterTerritory(id)) {
            return OrganizationResult.fail("这块领地没有登记给本组织");
        }
        markDirty();
        return OrganizationResult.ok("已移除组织领地登记（剩余 " + organization.territoryCount() + " 块）");
    }

    /**
     * 重新校验所有组织的领地登记，摘除失效项。
     *
     * <p><b>经济服务读不到时直接返回</b>：把"读不到"当成"没有领地"会把所有登记一次清空，
     * 那是比不校验严重得多的错误。</p>
     *
     * @return 摘除的登记数量
     */
    public static synchronized int reconcileTerritories(MinecraftServer server) {
        if (!writable() || server == null || !EconomySystemBridge.isTerritoryReadable(server)) {
            return 0;
        }
        int removed = 0;
        for (Organization organization : ORGANIZATIONS.values()) {
            List<String> stale = new ArrayList<>();
            for (String id : organization.territoryIds().keySet()) {
                EconomySystemBridge.TerritoryInfo info =
                        EconomySystemBridge.findTerritory(server, id).orElse(null);
                if (info == null) {
                    stale.add(id);
                    continue;
                }
                UUID owner = parseUuid(info.ownerId());
                // 领地主人已经退会/转会后，这块地不再是本组织的地盘。
                if (owner == null || !organization.isMember(owner)) {
                    stale.add(id);
                }
            }
            int count = organization.unregisterTerritories(stale);
            if (count > 0) {
                removed += count;
                markDirty();
                DreamingFishCore.LOGGER.info("组织「{}」摘除 {} 条失效的领地登记：{}",
                        organization.name(), count, stale);
            }
        }
        return removed;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static synchronized OrganizationViewData.Snapshot buildSnapshot(ServerPlayer player,
                                                                          MinecraftServer server) {
        OrganizationConfig config = config();
        Organization mine = player == null ? null : findByPlayer(player.getUUID()).orElse(null);
        String myId = mine == null ? "" : mine.id();

        List<OrganizationViewData.Summary> summaries = new ArrayList<>();
        for (Organization organization : all()) {
            OrganizationViewData.Relation relation = OrganizationViewData.Relation.NONE;
            if (player != null) {
                if (organization.isMember(player.getUUID())) {
                    relation = OrganizationViewData.Relation.MEMBER;
                } else if (organization.applications().containsKey(player.getUUID().toString())) {
                    relation = OrganizationViewData.Relation.APPLIED;
                } else if (organization.invites().containsKey(player.getUUID().toString())) {
                    relation = OrganizationViewData.Relation.INVITED;
                }
            }
            summaries.add(new OrganizationViewData.Summary(
                    organization.id(), organization.name(), organization.memberCount(),
                    displayNameOf(safeUuid(organization.leaderId())), relation));
        }

        OrganizationViewData.Detail detail = mine == null ? null
                : buildDetail(mine, player, server);

        return new OrganizationViewData.Snapshot(config.isEnabled(), config.getMaxMembers(),
                config.getNameMaxLength(), config.getAnnouncementMaxLength(),
                config.getCreationCost(), myId, List.copyOf(summaries), detail);
    }

    private static OrganizationViewData.Detail buildDetail(Organization organization,
                                                           ServerPlayer player,
                                                           MinecraftServer server) {
        OrganizationRank myRank = player == null ? null
                : organization.rankOf(player.getUUID()).orElse(null);
        List<OrganizationViewData.MemberLine> members = new ArrayList<>();
        for (Map.Entry<String, Organization.Member> entry : organization.sortedMembers()) {
            members.add(toLine(entry.getKey(), entry.getValue(), server));
        }
        List<OrganizationViewData.MemberLine> applicants = new ArrayList<>();
        organization.applications().keySet().stream().sorted().forEach(key -> {
            UUID id = safeUuid(key);
            applicants.add(new OrganizationViewData.MemberLine(
                    key, displayNameOf(id), OrganizationRank.MEMBER.serializedName(),
                    OrganizationRank.MEMBER.displayName(), isOnline(server, id)));
        });
        List<OrganizationViewData.MemberLine> invited = new ArrayList<>();
        organization.invites().keySet().stream().sorted().forEach(key -> {
            UUID id = safeUuid(key);
            invited.add(new OrganizationViewData.MemberLine(
                    key, displayNameOf(id), OrganizationRank.MEMBER.serializedName(),
                    OrganizationRank.MEMBER.displayName(), isOnline(server, id)));
        });
        return new OrganizationViewData.Detail(
                organization.id(), organization.name(), organization.announcement(),
                myRank == null ? "" : myRank.serializedName(),
                myRank == null ? "" : myRank.displayName(),
                OrganizationPermissions.canReviewApplications(myRank),
                OrganizationPermissions.canInvite(myRank),
                OrganizationPermissions.canEditAnnouncement(myRank),
                myRank != null && myRank.atLeast(OrganizationRank.OFFICER),
                List.copyOf(members), List.copyOf(applicants), List.copyOf(invited),
                organization.createdAtEpochMillis());
    }

    private static OrganizationViewData.MemberLine toLine(String playerId,
                                                          Organization.Member member,
                                                          MinecraftServer server) {
        UUID id = safeUuid(playerId);
        return new OrganizationViewData.MemberLine(
                playerId, displayNameOf(id, member.lastName()),
                member.rank().serializedName(), member.rank().displayName(),
                isOnline(server, id));
    }

    private static boolean isOnline(MinecraftServer server, UUID playerId) {
        return server != null && playerId != null
                && server.getPlayerList().getPlayer(playerId) != null;
    }

    private static String displayNameOf(UUID playerId) {
        return playerId == null ? "未知玩家" : displayNameOf(playerId, "");
    }

    private static String displayNameOf(UUID playerId, String fallback) {
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return playerId == null ? "未知玩家" : "玩家 " + playerId.toString().substring(0, 8);
    }

    private static UUID safeUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** 按玩家名在在线列表里找人（命令与界面共用）。 */
    public static ServerPlayer findOnlinePlayer(MinecraftServer server, String name) {
        if (server == null || name == null || name.isBlank()) {
            return null;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        return server.getPlayerList().getPlayers().stream()
                .filter(player -> player.getScoreboardName().toLowerCase(Locale.ROOT).equals(key))
                .findFirst()
                .orElse(null);
    }

    /** 本地化辅助：把关系枚举转成界面用词。 */
    public static String relationLabel(OrganizationViewData.Relation relation) {
        return switch (relation) {
            case APPLIED -> "已申请";
            case INVITED -> "已邀请";
            case MEMBER -> "成员";
            case NONE -> "未加入";
        };
    }
}
