package com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.cache;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端玩家属性缓存，归属属性系统而不是全局客户端缓存。
 */
@OnlyIn(Dist.CLIENT)
public final class PlayerAttributesClientCache {
    private static final Map<UUID, PlayerAttributesData> ATTRIBUTES = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> RESPAWN_POINTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> INFECTED = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> INFECTION_LEVELS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> INFECTION_MAXIMUMS = new ConcurrentHashMap<>();
    /** 传播复发标记：稳定感染者的临时状态，必须与感染等级一起缓存才能还原感染身份。 */
    private static final Map<UUID, Boolean> RELAPSING = new ConcurrentHashMap<>();

    private PlayerAttributesClientCache() {
    }

    public static PlayerAttributesData get(UUID uuid) {
        return ATTRIBUTES.get(uuid);
    }

    public static PlayerAttributesData getOrCreate(UUID uuid) {
        return ATTRIBUTES.computeIfAbsent(uuid, ignored -> new PlayerAttributesData());
    }

    public static void put(UUID uuid, PlayerAttributesData data) {
        if (uuid != null && data != null) {
            ATTRIBUTES.put(uuid, data);
        }
    }

    public static float getRespawnPoint(UUID uuid) {
        return RESPAWN_POINTS.getOrDefault(uuid, 100.0F);
    }

    public static void setRespawnPoint(UUID uuid, float respawnPoint) {
        RESPAWN_POINTS.put(uuid, respawnPoint);
    }

    public static boolean isInfected(UUID uuid) {
        return INFECTED.getOrDefault(uuid, false);
    }

    public static void setInfected(UUID uuid, boolean infected) {
        INFECTED.put(uuid, infected);
        if (!infected) {
            INFECTION_LEVELS.put(uuid, 0);
            RELAPSING.put(uuid, false);
        } else {
            INFECTION_LEVELS.putIfAbsent(uuid, 1);
        }
    }

    public static int getInfectionLevel(UUID uuid) {
        return INFECTION_LEVELS.getOrDefault(uuid, isInfected(uuid) ? 1 : 0);
    }

    public static void setInfectionLevel(UUID uuid, int level) {
        int normalized = Math.max(0, Math.min(level, 2));
        INFECTION_LEVELS.put(uuid, normalized);
        INFECTED.put(uuid, normalized > 0);
        if (normalized != 2) {
            // 传播复发只属于稳定感染者；等级一变就不再成立。
            RELAPSING.put(uuid, false);
        }
    }

    public static boolean isRelapsing(UUID uuid) {
        return getInfectionLevel(uuid) == 2 && RELAPSING.getOrDefault(uuid, false);
    }

    public static void setRelapsing(UUID uuid, boolean relapsing) {
        RELAPSING.put(uuid, relapsing && getInfectionLevel(uuid) == 2);
    }

    /**
     * 客户端侧的感染身份判定。
     *
     * <p>与服务端 {@link com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionIdentity}
     * 一一对应：0=幸存者、1=不稳定感染者、2=稳定感染者，等级 2 且带复发标记才是传播复发。</p>
     */
    public static com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionIdentity
            getInfectionIdentity(UUID uuid) {
        if (!isInfected(uuid)) {
            return com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionIdentity.SURVIVOR;
        }
        if (getInfectionLevel(uuid) == 1) {
            return com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionIdentity.UNSTABLE;
        }
        return isRelapsing(uuid)
                ? com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionIdentity.RELAPSE
                : com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionIdentity.STABLE;
    }

    public static int getInfectionMaximum(UUID uuid) {
        return INFECTION_MAXIMUMS.getOrDefault(uuid, 100);
    }

    public static void setInfectionMaximum(UUID uuid, int maximum) {
        INFECTION_MAXIMUMS.put(uuid, maximum >= 200 ? 200 : 100);
    }

    /**
     * 标准重建消耗：按感染身份分档（ADR 0016 幸存者最低、稳定居中、不稳定最高）。
     * 数值统一取自 {@link com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionRules}，
     * 与服务端扣费完全同源，避免界面预览与实际扣费漂移。
     */
    public static float getNormalRespawnCost(UUID uuid) {
        return com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionRules
                .respawnCost(getInfectionIdentity(uuid));
    }

    public static float getKeepInventoryCost(UUID uuid) {
        return com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionRules
                .keepInventoryCost(getInfectionIdentity(uuid));
    }

    public static int getRespawnTimes(UUID uuid) {
        float cost = getNormalRespawnCost(uuid);
        return cost > 0.0F ? (int) (getRespawnPoint(uuid) / cost) : 0;
    }

    public static void remove(UUID uuid) {
        ATTRIBUTES.remove(uuid);
        RESPAWN_POINTS.remove(uuid);
        INFECTED.remove(uuid);
        INFECTION_LEVELS.remove(uuid);
        INFECTION_MAXIMUMS.remove(uuid);
        RELAPSING.remove(uuid);
    }

    public static void clear() {
        ATTRIBUTES.clear();
        RESPAWN_POINTS.clear();
        INFECTED.clear();
        INFECTION_LEVELS.clear();
        INFECTION_MAXIMUMS.clear();
        RELAPSING.clear();
    }
}
