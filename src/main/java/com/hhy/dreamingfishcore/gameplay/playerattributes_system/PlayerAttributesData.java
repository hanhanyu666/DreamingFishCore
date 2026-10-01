package com.hhy.dreamingfishcore.gameplay.playerattributes_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionIdentity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * 玩家属性数据实体类（体力、SAN值、勇气值、感染值）
 * 所有属性最大值与玩家等级关联，提供属性消耗/恢复的边界检查
 */
public class PlayerAttributesData {
    public static final int INFECTION_LEVEL_NONE = 0;
    public static final int INFECTION_LEVEL_ONE = 1;
    public static final int INFECTION_LEVEL_TWO = 2;
    /** 感染值的持久化上限；旧阶段使用的 100 仍由感染系统按玩家状态决定。 */
    public static final float INFECTION_STORAGE_MAX = 200.0F;

    /** Minecraft 的原版最大生命基础值；等级生命通过独立 modifier 叠加在其上。 */
    private static final double VANILLA_MAX_HEALTH = 20.0D;
    private static final double HEALTH_EPSILON = 1.0E-6D;
    private static final ResourceLocation LEVEL_HEALTH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "level_health");

    // 基础字段
    private UUID playerUUID;
    private String playerName;
    private int level; // 关联等级

    // 体力属性
    private int maxStrength;
    private int currentStrength;

    // 勇气值
    private float maxCourage;
    private float currentCourage;

    // 感染值（持久化范围 0-200；具体阶段的感染阈值由感染系统决定）
    private float currentInfection;

    // 是否为感染者（兼容旧存档；具体等级见 infectionLevel）
    private boolean isInfected;

    // 感染身份等级：0=幸存者，1=不稳定感染者，2=稳定感染者。旧存档缺失该字段时按状态迁移。
    // 「传播复发」不是更高的等级，而是稳定感染者的临时状态，见 relapseUntilActiveTick。
    private int infectionLevel;

    // 是否已经通过江晚的剧情交互领取过防护面具；这是领取事实，不改变感染等级。
    private boolean protectiveMaskReceived;

    /**
     * 面具阶段新晋一级感染者的治疗截止活动 tick。
     * -1 表示不是“新一级”治疗窗口（旧一级感染者也使用 -1）。
     */
    private long infectionTreatmentDeadlineActiveTick = -1L;

    /**
     * 传播复发的到期活动 tick（稳定感染者因重伤或高污染暂时重新释放异常因子）。
     * -1 表示当前没有复发；只有感染等级 2 才可能非 -1。
     */
    private long relapseUntilActiveTick = -1L;

    /**
     * 传播复发结束后的冷却截止活动 tick。
     * -1 表示不在冷却期；冷却用于避免同一名玩家被连续触发复发。
     */
    private long relapseCooldownUntilActiveTick = -1L;

    // 复活点数（0-100，感染者死亡时消耗）
    private float respawnPoint;
    private com.hhy.dreamingfishcore.gameplay.hospital_system.DailyTemplateSupportProgress dailyTemplateSupport = new com.hhy.dreamingfishcore.gameplay.hospital_system.DailyTemplateSupportProgress();

    //血量系统：保存等级系统自身的目标值，不包含饰品、装备及其他模组的 modifier
    private double maxHealth;

    // 防重复提示标记（各属性不足时避免刷屏）
    private boolean strengthWarned;
    private boolean courageWarned;
    private boolean infectionWarned;

    /**
     * 无参构造（Gson反序列化必须）
     */
    public PlayerAttributesData() {
        this.playerUUID = UUID.randomUUID();
        this.playerName = "";
        this.level = 1;

        // 初始化属性（默认等级1的最大值）
        this.maxStrength = calculateMaxStrengthByLevel(level);
        this.currentStrength = maxStrength;

        this.maxCourage = calculateMaxCourageByLevel(level);
        this.currentCourage = maxCourage / 2;

        this.maxHealth = calculateMaxHealthByLevel(level);

        this.currentInfection = 0;
        this.isInfected = false;
        this.infectionLevel = INFECTION_LEVEL_NONE;
        this.protectiveMaskReceived = false;
        this.infectionTreatmentDeadlineActiveTick = -1L;
        this.relapseUntilActiveTick = -1L;
        this.relapseCooldownUntilActiveTick = -1L;
        this.respawnPoint = 100;

        // 初始化提示标记
        this.strengthWarned = false;
        this.courageWarned = false;
        this.infectionWarned = false;
    }

    /**
     * 从ServerPlayer初始化（新玩家）
     */
    public PlayerAttributesData(ServerPlayer player) {
        this.playerUUID = player.getUUID();
        this.playerName = player.getScoreboardName();
        this.level = 1; // 默认初始等级1

        // 等级关联初始化属性最大值
        this.maxStrength = calculateMaxStrengthByLevel(level);
        this.currentStrength = maxStrength;

        this.maxCourage = calculateMaxCourageByLevel(level);
        this.currentCourage = maxCourage / 2;

        this.maxHealth = calculateMaxHealthByLevel(level);
        this.syncMaxHealthToPlayer(player);

        this.currentInfection = 0;
        this.isInfected = false;
        this.infectionLevel = INFECTION_LEVEL_NONE;
        this.protectiveMaskReceived = false;
        this.infectionTreatmentDeadlineActiveTick = -1L;
        this.relapseUntilActiveTick = -1L;
        this.relapseCooldownUntilActiveTick = -1L;
        this.respawnPoint = 100;

        // 初始化提示标记
        this.strengthWarned = false;
        this.courageWarned = false;
        this.infectionWarned = false;

        DreamingFishCore.LOGGER.info("玩家 {} 属性数据初始化完成（等级1）", player.getScoreboardName());
    }

    /**
     * 自定义初始化（指定UUID、名称、等级）
     */
    public PlayerAttributesData(UUID playerUUID, String playerName, int level) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.level = level;

        // 等级关联初始化属性最大值
        this.maxStrength = calculateMaxStrengthByLevel(level);
        this.currentStrength = maxStrength;

        this.maxCourage = calculateMaxCourageByLevel(level);
        this.currentCourage = maxCourage / 2;

        this.maxHealth = calculateMaxHealthByLevel(level);

        this.currentInfection = 0;
        this.isInfected = false;
        this.infectionLevel = INFECTION_LEVEL_NONE;
        this.protectiveMaskReceived = false;
        this.infectionTreatmentDeadlineActiveTick = -1L;
        this.relapseUntilActiveTick = -1L;
        this.relapseCooldownUntilActiveTick = -1L;
        this.respawnPoint = 100;

        // 初始化提示标记
        this.strengthWarned = false;
        this.courageWarned = false;
        this.infectionWarned = false;
    }

    /**
     * 体力最大值：基础100，每级+5
     */
    public int calculateMaxStrengthByLevel(int level) {
        return 700 + (level - 1) * 60;
    }

    /**
     * 勇气值最大值：基础100，每级+4
     */
    public int calculateMaxCourageByLevel(int level) {
//        return 100 + (level - 1) * 4;
        return 100;
    }

    /**
     * 最大血量计算
     * @param level 玩家等级
     * @return 对应等级的最大血量
     */
    public double calculateMaxHealthByLevel(int level) {
        int caculateHealth = 20 + (int)(level / 5) * 2;
        if (caculateHealth > 40) {
            caculateHealth = 40;
        }
        return caculateHealth;
    }

    /**
     * 计算等级系统对最大生命值的增量，不包含原版基础值和其他模组的属性加成。
     */
    public double calculateHealthBonusByLevel(int level) {
        return Math.max(0.0D, calculateMaxHealthByLevel(level) - VANILLA_MAX_HEALTH);
    }

    // ========== 等级更新（同步更新所有属性最大值） ==========
    public void setLevel(int level, ServerPlayer player) {  // 新增ServerPlayer参数
        // 在更新内存中的等级上限前先处理旧版 base value，避免等级升级时丢失旧值迁移信息。
        if (player != null) {
            AttributeInstance maxHealthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealthAttribute != null) {
                migrateLegacyBaseValue(maxHealthAttribute);
            }
        }
        this.level = level;

        // 更新各属性最大值（包含最大血量）
        this.maxStrength = calculateMaxStrengthByLevel(level);
        this.maxCourage = calculateMaxCourageByLevel(level);
        this.maxHealth = calculateMaxHealthByLevel(level);

        // 防止当前属性超过新最大值
        this.currentStrength = Math.min(this.currentStrength, maxStrength);
        this.currentCourage = Math.min(this.currentCourage, maxCourage);

        // ========== 同步生命值到玩家实体 ==========
        // 同步生命值到玩家实体（使用自定义方法）
        if (player != null) {
            syncMaxHealthToPlayer(player);  // 调用自定义同步方法
        }

        DreamingFishCore.LOGGER.info("玩家 {} 等级更新为{}，属性最大值同步完成", playerName, level);
    }

    public void syncMaxHealthToPlayer(ServerPlayer player) {
        if (player == null) return;

        // 获取玩家的最大生命值属性
        AttributeInstance maxHealthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttribute == null) return;

        // 旧版本曾把等级生命直接写入 base value。这里只在能明确识别旧值时恢复
        // 原版基础值，避免已有存档在切换到 modifier 方案后把等级加成重复计算。
        migrateLegacyBaseValue(maxHealthAttribute);

        // 等级生命只维护本模组自己的持久 modifier，绝不触碰护符、装备或其他模组的 modifier。
        // 持久化 modifier 可随玩家退出/重新加入保存；重生或登录时仍会通过本方法重建。
        double levelHealthBonus = calculateHealthBonusByLevel(this.level);
        if (levelHealthBonus > HEALTH_EPSILON) {
            maxHealthAttribute.addOrReplacePermanentModifier(new AttributeModifier(
                    LEVEL_HEALTH_MODIFIER_ID,
                    levelHealthBonus,
                    AttributeModifier.Operation.ADD_VALUE));
        } else {
            maxHealthAttribute.removeModifier(LEVEL_HEALTH_MODIFIER_ID);
        }

        // 不主动改写当前生命值。属性变更会由 LivingEntity 在最终 modifier 刷新后
        // 按实体的有效最大生命值（包含护符/装备/其他模组加成）执行必要的下限裁剪，
        // 避免外部饰品尚未完成刷新时被误裁到等级基础值。
    }

    /**
     * 将旧版“把等级上限写进 base value”的存档迁移回原版基础值。
     * 仅当当前 base 恰好等于旧版保存的等级上限时才迁移，避免覆盖外部模组自己的 base 设置。
     */
    private void migrateLegacyBaseValue(AttributeInstance maxHealthAttribute) {
        double legacyBaseValue = this.maxHealth;
        if (legacyBaseValue > VANILLA_MAX_HEALTH + HEALTH_EPSILON
                && Math.abs(maxHealthAttribute.getBaseValue() - legacyBaseValue) <= HEALTH_EPSILON) {
            maxHealthAttribute.setBaseValue(VANILLA_MAX_HEALTH);
            DreamingFishCore.LOGGER.info(
                    "玩家 {} 的旧版等级生命基础值已迁移为 modifier（{} → {}）",
                    playerName, legacyBaseValue, VANILLA_MAX_HEALTH);
        }
    }

    /**
     * 自定义血量恢复、
     * @param player 服务端玩家实例
     * @param healAmount 回血数值（正数，药品配置的回血值）
     * @return 是否恢复成功（false：已达最大血量，无需恢复）
     */
    public boolean restoreCustomHealth(ServerPlayer player, double healAmount) {
        // 非空校验
        if (player == null || healAmount <= 0) {
            return false;
        }
        // 获取当前血量（从玩家实体同步，避免数据不一致）
        double currentHealth = player.getHealth();
        // 以实体最终最大生命值为上限，包含饰品/装备/其他模组的加成。
        double effectiveMaxHealth = player.getMaxHealth();
        if (currentHealth >= effectiveMaxHealth) {
            return false;
        }
        // 计算新血量（不超过最大血量）
        double newHealth = Math.min(currentHealth + healAmount, effectiveMaxHealth);
        // 同步血量到玩家实体
        player.setHealth((float) newHealth);
        // 同步客户端显示（防止血量显示异常）
        player.setHealth(player.getHealth());
        DreamingFishCore.LOGGER.info("玩家 {} 使用自定义药品回血：{} → {}（最大血量：{}）",
                this.playerName, currentHealth, newHealth, effectiveMaxHealth);
        return true;
    }

    /**
     * 强制设置当前血量（用于特殊场景：如药品副作用、受伤扣血）
     * @param player 服务端玩家实例
     * @param newHealth 目标血量
     */
    public void setCustomHealth(ServerPlayer player, double newHealth) {
        if (player == null) {
            return;
        }
        // 边界控制：不低于0，不超过实体最终最大血量（包含外部加成）。
        double finalHealth = Math.max(0, Math.min(newHealth, player.getMaxHealth()));
        player.setHealth((float) finalHealth);
        player.setHealth(player.getHealth()); // 同步客户端
    }

    // 体力消耗（返回是否消耗成功）
    public boolean consumeStrength(int amount) {
        if (currentStrength >= amount) {
            currentStrength -= amount;
            return true;
        }
        return false;
    }

    // 体力恢复（带上限）
    public void restoreStrength(int amount) {
        currentStrength = Math.min(currentStrength + amount, maxStrength);
        // 恢复后重置提示标记
        if (currentStrength > maxStrength * 0.2) {
            this.strengthWarned = false;
        }
    }

    // 勇气值消耗
    public boolean consumeCourage(int amount) {
        if (currentCourage >= amount) {
            currentCourage -= amount;
            return true;
        }
        return false;
    }

    // 勇气值恢复
    public void restoreCourage(int amount) {
        currentCourage = Math.min(currentCourage + amount, maxCourage);
        if (currentCourage > maxCourage * 0.2) {
            this.courageWarned = false;
        }
    }

    /** 返回等级系统自身的生命值目标，不含实体上的外部属性修饰符。 */
    public double getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(double maxHealth) {
        this.maxHealth = maxHealth;
    }

    // 感染值增加（持久化上限200；阶段阈值由感染系统决定）
    public void addInfection(float amount) {
        currentInfection = Math.min(currentInfection + amount, INFECTION_STORAGE_MAX);
    }

    // 感染值减少（下限0）
    public void reduceInfection(float amount) {
        currentInfection = Math.max(currentInfection - amount, 0);
        if (currentInfection < 80) { // 感染值低于80重置提示
            this.infectionWarned = false;
        }
    }

    // ========== 属性不足/超标判断（提示触发依据） ==========
    public boolean isStrengthLow() {
        return currentStrength <= maxStrength * 0.2; // 体力低于20%
    }

    public boolean isCourageLow() {
        return currentCourage <= maxCourage * 0.2; // 勇气值低于20%
    }

    public boolean isInfectionHigh() {
        return currentInfection >= 80; // 感染值高于80%
    }

    // ========== Getter/Setter（Gson序列化+外部调用） ==========
    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public void setPlayerUUID(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public int getLevel() {
        return level;
    }

    public int getMaxStrength() {
        return maxStrength;
    }

    public void setMaxStrength(int maxStrength) {
        this.maxStrength = maxStrength;
    }

    public int getCurrentStrength() {
        return currentStrength;
    }

    public void setCurrentStrength(int currentStrength) {
        this.currentStrength = currentStrength;
    }

    public float getMaxCourage() {
        return maxCourage;
    }

    public void setMaxCourage(float maxCourage) {
        this.maxCourage = maxCourage;
    }

    public float getCurrentCourage() {
        return currentCourage;
    }

    public void setCurrentCourage(float currentCourage) {
        this.currentCourage = currentCourage;
    }

    public float getCurrentInfection() {
        return currentInfection;
    }

    public void setCurrentInfection(float currentInfection) {
        if (Float.isNaN(currentInfection) || Float.isInfinite(currentInfection)) {
            this.currentInfection = 0.0F;
            return;
        }
        this.currentInfection = Math.max(0.0F, Math.min(currentInfection, INFECTION_STORAGE_MAX));
    }

    public boolean isStrengthWarned() {
        return strengthWarned;
    }

    public void setStrengthWarned(boolean strengthWarned) {
        this.strengthWarned = strengthWarned;
    }

    public boolean isCourageWarned() {
        return courageWarned;
    }

    public void setCourageWarned(boolean courageWarned) {
        this.courageWarned = courageWarned;
    }

    public boolean isInfectionWarned() {
        return infectionWarned;
    }

    public void setInfectionWarned(boolean infectionWarned) {
        this.infectionWarned = infectionWarned;
    }

    public boolean isInfected() {
        return isInfected || infectionLevel > INFECTION_LEVEL_NONE;
    }

    public void setInfected(boolean infected) {
        isInfected = infected;
        if (infected) {
            if (infectionLevel == INFECTION_LEVEL_NONE) {
                infectionLevel = INFECTION_LEVEL_ONE;
            }
        } else {
            infectionLevel = INFECTION_LEVEL_NONE;
        }
    }

    public int getInfectionLevel() {
        return normalizeInfectionLevel(infectionLevel);
    }

    /**
     * 设置感染身份等级并同步旧的布尔状态。
     * 等级 0 表示幸存者，1 表示不稳定感染者，2 表示稳定感染者；1、2 均表示感染者。
     */
    public void setInfectionLevel(int infectionLevel) {
        int normalized = normalizeInfectionLevel(infectionLevel);
        this.infectionLevel = normalized;
        this.isInfected = normalized > INFECTION_LEVEL_NONE;
        if (normalized != INFECTION_LEVEL_ONE) {
            this.infectionTreatmentDeadlineActiveTick = -1L;
        }
        // 传播复发只属于稳定感染者；离开该身份时一并清空复发窗口与冷却。
        if (normalized != INFECTION_LEVEL_TWO) {
            clearRelapseState();
        }
    }

    public boolean hasReceivedProtectiveMask() {
        return protectiveMaskReceived;
    }

    public long getInfectionTreatmentDeadlineActiveTick() {
        return infectionTreatmentDeadlineActiveTick;
    }

    public boolean hasPendingInfectionTreatmentWindow() {
        return getInfectionLevel() == INFECTION_LEVEL_ONE
                && infectionTreatmentDeadlineActiveTick >= 0L;
    }

    /** 仅由面具阶段感染规则设置；旧一级感染者不会自动获得该截止时间。 */
    public void startInfectionTreatmentWindow(long deadlineActiveTick) {
        if (getInfectionLevel() != INFECTION_LEVEL_ONE) {
            return;
        }
        infectionTreatmentDeadlineActiveTick = Math.max(0L, deadlineActiveTick);
    }

    public void setInfectionTreatmentDeadlineActiveTick(long deadlineActiveTick) {
        if (deadlineActiveTick < -1L) {
            infectionTreatmentDeadlineActiveTick = -1L;
        } else {
            infectionTreatmentDeadlineActiveTick = deadlineActiveTick;
        }
    }

    public void clearInfectionTreatmentDeadline() {
        infectionTreatmentDeadlineActiveTick = -1L;
    }

    public void setProtectiveMaskReceived(boolean protectiveMaskReceived) {
        this.protectiveMaskReceived = protectiveMaskReceived;
    }

    /**
     * 记录玩家已经从医疗组领取面具。
     * 面具只记录领取事实，不会改变玩家的感染等级，也不会缩放感染值。
     *
     * @return 是否修改了玩家属性
     */
    public boolean recordProtectiveMaskReceipt() {
        if (protectiveMaskReceived) {
            return false;
        }
        protectiveMaskReceived = true;
        return true;
    }

    public boolean isLevelOneInfected() {
        return isInfected() && getInfectionLevel() == INFECTION_LEVEL_ONE;
    }

    public boolean isLevelTwoInfected() {
        return isInfected() && getInfectionLevel() == INFECTION_LEVEL_TWO;
    }

    // ========== 感染身份（CONTEXT.md「感染身份」） ==========

    /** 不稳定感染者：正在经历剧烈修复与突变，会被动影响附近幸存者。 */
    public boolean isUnstableInfected() {
        return isInfected() && getInfectionLevel() == INFECTION_LEVEL_ONE;
    }

    /** 稳定感染者：突变已经稳定；处于传播复发时不算稳定状态。 */
    public boolean isStableInfected() {
        return isInfected() && getInfectionLevel() == INFECTION_LEVEL_TWO && !hasActiveRelapseWindow();
    }

    /** 传播复发：稳定感染者暂时重新释放异常因子。 */
    public boolean isRelapsing() {
        return isInfected() && getInfectionLevel() == INFECTION_LEVEL_TWO && hasActiveRelapseWindow();
    }

    /** 当前感染身份；供服务端规则与界面文案统一取用。 */
    public InfectionIdentity getInfectionIdentity() {
        return InfectionIdentity.of(this);
    }

    public long getRelapseUntilActiveTick() {
        return relapseUntilActiveTick;
    }

    /**
     * 是否处于传播复发窗口内。
     *
     * <p>这里只判断"有没有窗口"，不比较当前时间：到期由感染系统的服务端 tick 统一清除，
     * 因此调用方无需持有活动时钟即可得到一致的身份判定。</p>
     */
    public boolean hasActiveRelapseWindow() {
        return getInfectionLevel() == INFECTION_LEVEL_TWO && relapseUntilActiveTick >= 0L;
    }

    /** 开始传播复发；只有稳定感染者可以进入。 */
    public boolean beginRelapse(long untilActiveTick) {
        if (getInfectionLevel() != INFECTION_LEVEL_TWO) {
            return false;
        }
        relapseUntilActiveTick = Math.max(0L, untilActiveTick);
        return true;
    }

    public void setRelapseUntilActiveTick(long untilActiveTick) {
        relapseUntilActiveTick = untilActiveTick < -1L ? -1L : untilActiveTick;
    }

    /** 结束复发；冷却由调用方另行设置。 */
    public void endRelapse() {
        relapseUntilActiveTick = -1L;
    }

    public long getRelapseCooldownUntilActiveTick() {
        return relapseCooldownUntilActiveTick;
    }

    public void setRelapseCooldownUntilActiveTick(long untilActiveTick) {
        relapseCooldownUntilActiveTick = untilActiveTick < -1L ? -1L : untilActiveTick;
    }

    public boolean isRelapseCoolingDown() {
        return relapseCooldownUntilActiveTick >= 0L;
    }

    /** 身份离开"稳定感染者"时清空全部复发状态（复发窗口与冷却）。 */
    public void clearRelapseState() {
        relapseUntilActiveTick = -1L;
        relapseCooldownUntilActiveTick = -1L;
    }

    /**
     * 修复感染等级与旧布尔字段之间的不一致。
     *
     * <p>感染值达到多少才转为感染者由感染系统根据当前阶段判断；这里不能仅凭原始数值
     * 推断等级，否则面具发放后重启服务器时会把 100 点误判成一级感染。</p>
     *
     * @return 是否发生了需要写回存档的变化
     */
    public boolean normalizeInfectionState() {
        boolean changed = false;
        float boundedInfection = currentInfection;
        if (Float.isNaN(boundedInfection) || Float.isInfinite(boundedInfection)) {
            boundedInfection = 0.0F;
        } else {
            boundedInfection = Math.max(0.0F, Math.min(boundedInfection, INFECTION_STORAGE_MAX));
        }
        if (Float.compare(currentInfection, boundedInfection) != 0) {
            currentInfection = boundedInfection;
            changed = true;
        }

        int normalizedLevel = normalizeInfectionLevel(infectionLevel);
        if (normalizedLevel != infectionLevel) {
            infectionLevel = normalizedLevel;
            changed = true;
        }
        // 旧存档中的 isInfected=true 没有等级字段，统一迁移为一级感染者。
        if (isInfected && infectionLevel == INFECTION_LEVEL_NONE) {
            infectionLevel = INFECTION_LEVEL_ONE;
            changed = true;
        }
        // 等级字段是新事实来源；即使旧布尔字段缺失，也不能把二级状态当成幸存者。
        if (infectionLevel > INFECTION_LEVEL_NONE && !isInfected) {
            isInfected = true;
            changed = true;
        }
        if (infectionLevel == INFECTION_LEVEL_NONE && isInfected) {
            isInfected = false;
            changed = true;
        }
        long normalizedDeadline = infectionTreatmentDeadlineActiveTick;
        if (normalizedDeadline < -1L) {
            normalizedDeadline = -1L;
        }
        if (normalizedLevel != INFECTION_LEVEL_ONE) {
            normalizedDeadline = -1L;
        }
        if (normalizedDeadline != infectionTreatmentDeadlineActiveTick) {
            infectionTreatmentDeadlineActiveTick = normalizedDeadline;
            changed = true;
        }

        // 传播复发窗口与冷却：只对稳定感染者有意义，且不允许比 -1 更小的哨兵值。
        long normalizedRelapse = relapseUntilActiveTick < -1L ? -1L : relapseUntilActiveTick;
        if (normalizedLevel != INFECTION_LEVEL_TWO) {
            normalizedRelapse = -1L;
        }
        if (normalizedRelapse != relapseUntilActiveTick) {
            relapseUntilActiveTick = normalizedRelapse;
            changed = true;
        }
        long normalizedCooldown = relapseCooldownUntilActiveTick < -1L ? -1L : relapseCooldownUntilActiveTick;
        if (normalizedLevel != INFECTION_LEVEL_TWO) {
            normalizedCooldown = -1L;
        }
        if (normalizedCooldown != relapseCooldownUntilActiveTick) {
            relapseCooldownUntilActiveTick = normalizedCooldown;
            changed = true;
        }
        return changed;
    }

    private static int normalizeInfectionLevel(int level) {
        return Math.max(INFECTION_LEVEL_NONE, Math.min(level, INFECTION_LEVEL_TWO));
    }

    // ========== 复活点数相关 ==========
    public com.hhy.dreamingfishcore.gameplay.hospital_system.DailyTemplateSupportProgress getDailyTemplateSupport() {
        if (dailyTemplateSupport == null) dailyTemplateSupport = new com.hhy.dreamingfishcore.gameplay.hospital_system.DailyTemplateSupportProgress();
        return dailyTemplateSupport;
    }

    public float getRespawnPoint() {
        return respawnPoint;
    }

    public void setRespawnPoint(float respawnPoint) {
        this.respawnPoint = Math.max(0, Math.min(respawnPoint, 100));
    }

    /**
     * 消耗复活点数（感染者死亡时调用）
     * @param amount 消耗量
     * @return 是否成功消耗（false=复活点数不足）
     */
    public boolean consumeRespawnPoint(float amount) {
        if (respawnPoint >= amount) {
            respawnPoint -= amount;
            return true;
        }
        // 不足时扣到0
        respawnPoint = 0;
        return false;
    }

    /**
     * 恢复复活点数
     * @param amount 恢复量
     */
    public void restoreRespawnPoint(float amount) {
        respawnPoint = Math.min(respawnPoint + amount, 100);
    }

    /**
     * 检查复活点数是否耗尽
     */
    public boolean isRespawnPointDepleted() {
        return respawnPoint <= 0;
    }
}
