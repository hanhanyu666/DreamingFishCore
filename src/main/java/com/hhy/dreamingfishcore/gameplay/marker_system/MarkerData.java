package com.hhy.dreamingfishcore.gameplay.marker_system;

import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class MarkerData {
    public static final long DEFAULT_LIFETIME_MS = 8000L;

    private final UUID ownerId;
    private final String ownerName;
    private final Vec3 position;
    private final long createdAtMs;
    private final long expiresAtMs;

    public MarkerData(UUID ownerId, String ownerName, Vec3 position) {
        this(ownerId, ownerName, position, System.currentTimeMillis());
    }

    public MarkerData(UUID ownerId, String ownerName, Vec3 position, long createdAtMs) {
        this.ownerId = ownerId;
        this.ownerName = ownerName == null || ownerName.isBlank() ? "Player" : ownerName;
        this.position = position;
        this.createdAtMs = createdAtMs;
        this.expiresAtMs = createdAtMs + DEFAULT_LIFETIME_MS;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public Vec3 getPosition() {
        return position;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public boolean isExpired(long nowMs) {
        return nowMs >= expiresAtMs;
    }

    public float getFade(long nowMs) {
        long age = Math.max(0L, nowMs - createdAtMs);
        long remaining = Math.max(0L, expiresAtMs - nowMs);
        float intro = Math.min(1.0F, age / 180.0F);
        float outro = Math.min(1.0F, remaining / 650.0F);
        return Math.max(0.0F, Math.min(1.0F, intro * outro));
    }
}
