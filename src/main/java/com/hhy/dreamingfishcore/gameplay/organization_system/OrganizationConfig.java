package com.hhy.dreamingfishcore.gameplay.organization_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.init.CommonInit;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 玩家组织的服务器配置。
 *
 * <p>文件位置：{@code config/dreamingfishcore/organization.json}。上限、名称长度、公告长度
 * 都可以由服主调整，不需要改代码。</p>
 *
 * <p>与项目其他配置一致：文件缺失时写入默认值；文件损坏时只用内存默认值，不覆盖服主的文件。</p>
 */
public final class OrganizationConfig {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static final boolean DEFAULT_ENABLED = true;
    /** 每个世界最多允许多少个组织。 */
    public static final int DEFAULT_MAX_ORGANIZATIONS = 64;
    /** 单个组织的成员上限（含会长）。 */
    public static final int DEFAULT_MAX_MEMBERS = 32;
    public static final int DEFAULT_NAME_MIN_LENGTH = 2;
    public static final int DEFAULT_NAME_MAX_LENGTH = 12;
    public static final int DEFAULT_ANNOUNCEMENT_MAX_LENGTH = 200;
    /** 创建一个组织需要消耗的梦鱼币；0 表示免费。 */
    public static final int DEFAULT_CREATION_COST = 150;
    /** 解散组织时按实付创建费的百分比退还（0 = 不退）。 */
    public static final int DEFAULT_DISBAND_REFUND_PERCENT = 50;

    private static final int HARD_MAX_ORGANIZATIONS = 4096;
    private static final int HARD_MAX_MEMBERS = 512;
    /** 创建费的硬上限：防止误填出天文数字把配置写坏。 */
    private static final int HARD_MAX_CREATION_COST = 1_000_000;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static volatile OrganizationConfig current = defaults();

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private boolean enabled = DEFAULT_ENABLED;
    private int maxOrganizations = DEFAULT_MAX_ORGANIZATIONS;
    private int maxMembers = DEFAULT_MAX_MEMBERS;
    private int nameMinLength = DEFAULT_NAME_MIN_LENGTH;
    private int nameMaxLength = DEFAULT_NAME_MAX_LENGTH;
    private int announcementMaxLength = DEFAULT_ANNOUNCEMENT_MAX_LENGTH;
    private int creationCost = DEFAULT_CREATION_COST;
    private int disbandRefundPercent = DEFAULT_DISBAND_REFUND_PERCENT;

    public OrganizationConfig() {
    }

    public static synchronized OrganizationConfig init() {
        current = load();
        return current;
    }

    public static synchronized OrganizationConfig reload() {
        current = load();
        return current;
    }

    /** 运行期查询使用的快照；不会读取磁盘。 */
    public static OrganizationConfig current() {
        return current;
    }

    public static OrganizationConfig load() {
        return load(getConfigPath());
    }

    /** 供测试与工具使用的路径重载版本。 */
    public static synchronized OrganizationConfig load(Path path) {
        OrganizationConfig defaults = defaults();

        if (Files.notExists(path)) {
            try {
                JsonDataStore.writeAtomic(path, GSON, defaults);
            } catch (IOException exception) {
                DreamingFishCore.LOGGER.warn("组织配置默认文件创建失败：{}", path, exception);
            }
            return defaults;
        }

        try {
            if (Files.size(path) == 0L) {
                DreamingFishCore.LOGGER.error("组织配置为空，保留原文件并使用默认值：{}", path);
                return defaults;
            }
            OrganizationConfig loaded = JsonDataStore.read(
                    path, GSON, OrganizationConfig.class, OrganizationConfig::defaults);
            if (loaded == null || loaded.schemaVersion != CURRENT_SCHEMA_VERSION) {
                DreamingFishCore.LOGGER.error("组织配置版本不支持，使用默认值：{}", path);
                return defaults;
            }
            if (loaded.normalize() || isMissingKnownKeys(path)) {
                JsonDataStore.writeAtomic(path, GSON, loaded);
            }
            return loaded;
        } catch (IOException | RuntimeException exception) {
            DreamingFishCore.LOGGER.error("组织配置读取失败，使用默认值且不覆盖文件：{}", path, exception);
            return defaults;
        }
    }

    private static OrganizationConfig defaults() {
        return new OrganizationConfig();
    }

    private static Path getConfigPath() {
        return CommonInit.CONFIG_DIRECTORY.resolve("organization.json");
    }

    /**
     * 旧版配置文件里缺少新加入的键时返回 true。
     *
     * <p>缺失的键本身会用默认值生效（反序列化时保留字段初始值），但服主在文件里看不到它们，
     * 也就没法调整 —— 所以检测到缺失就顺手写回一次，把新键补进文件。</p>
     */
    private static boolean isMissingKnownKeys(Path path) {
        try {
            com.google.gson.JsonObject object = GSON.fromJson(
                    Files.readString(path, java.nio.charset.StandardCharsets.UTF_8),
                    com.google.gson.JsonObject.class);
            if (object == null) {
                return false;
            }
            return !object.has("creationCost") || !object.has("disbandRefundPercent");
        } catch (IOException | RuntimeException exception) {
            // 读不动就交给正常流程，不在这里报错。
            return false;
        }
    }

    /** 修正越界值；返回是否发生了修正（需要写回文件）。 */
    boolean normalize() {
        boolean changed = false;

        int organizations = clamp(maxOrganizations, 1, HARD_MAX_ORGANIZATIONS);
        if (organizations != maxOrganizations) {
            maxOrganizations = organizations;
            changed = true;
        }
        int members = clamp(maxMembers, 1, HARD_MAX_MEMBERS);
        if (members != maxMembers) {
            maxMembers = members;
            changed = true;
        }
        int min = clamp(nameMinLength, 1, 32);
        if (min != nameMinLength) {
            nameMinLength = min;
            changed = true;
        }
        int max = clamp(nameMaxLength, nameMinLength, 32);
        if (max != nameMaxLength) {
            nameMaxLength = max;
            changed = true;
        }
        int announcement = clamp(announcementMaxLength, 0, 2000);
        if (announcement != announcementMaxLength) {
            announcementMaxLength = announcement;
            changed = true;
        }
        int cost = clamp(creationCost, 0, HARD_MAX_CREATION_COST);
        if (cost != creationCost) {
            creationCost = cost;
            changed = true;
        }
        int refund = clamp(disbandRefundPercent, 0, 100);
        if (refund != disbandRefundPercent) {
            disbandRefundPercent = refund;
            changed = true;
        }
        return changed;
    }

    private static int clamp(int value, int lower, int upper) {
        if (value < lower) {
            return lower;
        }
        return Math.min(value, upper);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxOrganizations() {
        return maxOrganizations;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public int getNameMinLength() {
        return nameMinLength;
    }

    public int getNameMaxLength() {
        return nameMaxLength;
    }

    public int getAnnouncementMaxLength() {
        return announcementMaxLength;
    }

    /** 创建一个组织需要消耗的梦鱼币；0 表示免费。 */
    public int getCreationCost() {
        return creationCost;
    }

    /** 解散组织时按实付创建费的百分比退还（0 = 不退）。 */
    public int getDisbandRefundPercent() {
        return disbandRefundPercent;
    }

    /** 解散退款金额：按**实付**金额计算，没付过钱的组织退 0。 */
    public int refundFor(int paidCost) {
        if (paidCost <= 0 || disbandRefundPercent <= 0) {
            return 0;
        }
        long refund = (long) paidCost * disbandRefundPercent / 100L;
        return (int) Math.min(paidCost, Math.max(0L, refund));
    }
}
