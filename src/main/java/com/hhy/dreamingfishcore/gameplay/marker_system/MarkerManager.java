package com.hhy.dreamingfishcore.gameplay.marker_system;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class MarkerManager {
    public static final double NEARBY_PLAYER_RANGE = 100.0D;
    public static final double NEARBY_PLAYER_RANGE_SQR = NEARBY_PLAYER_RANGE * NEARBY_PLAYER_RANGE;

    private static final Map<UUID, MarkerData> MARKERS = new LinkedHashMap<>();

    private MarkerManager() {
    }

    public static void addOrReplace(UUID ownerId, String ownerName, Vec3 position, long createdAtMs) {
        if (ownerId == null || position == null) {
            return;
        }

        MARKERS.put(ownerId, new MarkerData(ownerId, ownerName, position, createdAtMs));
    }

    public static Collection<MarkerData> getActiveMarkers() {
        long now = System.currentTimeMillis();
        removeExpired(now);
        return new ArrayList<>(MARKERS.values());
    }

    public static void removeExpired(long nowMs) {
        Iterator<MarkerData> iterator = MARKERS.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isExpired(nowMs)) {
                iterator.remove();
            }
        }
    }

    public static void clear() {
        MARKERS.clear();
    }
}
