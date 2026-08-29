package com.hhy.dreamingfishcore.gameplay.marker_system;

import net.minecraft.Util;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MarkerManager {
    public static final double NEARBY_PLAYER_RANGE = 100.0D;
    public static final double NEARBY_PLAYER_RANGE_SQR = NEARBY_PLAYER_RANGE * NEARBY_PLAYER_RANGE;

    private static final Map<UUID, MarkerData> MARKERS = new LinkedHashMap<>();
    private static List<MarkerData> ACTIVE_SNAPSHOT = List.of();
    private static boolean snapshotDirty = true;

    private MarkerManager() {
    }

    public static void addOrReplace(UUID ownerId, String ownerName, Vec3 position, long createdAtMs) {
        if (ownerId == null || position == null) {
            return;
        }

        MARKERS.put(ownerId, new MarkerData(ownerId, ownerName, position, createdAtMs));
        snapshotDirty = true;
    }

    public static Collection<MarkerData> getActiveMarkers() {
        long now = Util.getMillis();
        removeExpired(now);
        if (snapshotDirty) {
            ACTIVE_SNAPSHOT = List.copyOf(MARKERS.values());
            snapshotDirty = false;
        }
        return ACTIVE_SNAPSHOT;
    }

    public static void removeExpired(long nowMs) {
        Iterator<MarkerData> iterator = MARKERS.values().iterator();
        boolean changed = false;
        while (iterator.hasNext()) {
            if (iterator.next().isExpired(nowMs)) {
                iterator.remove();
                changed = true;
            }
        }
        if (changed) {
            snapshotDirty = true;
        }
    }

    public static void clear() {
        if (!MARKERS.isEmpty()) {
            MARKERS.clear();
            snapshotDirty = true;
        }
    }
}
