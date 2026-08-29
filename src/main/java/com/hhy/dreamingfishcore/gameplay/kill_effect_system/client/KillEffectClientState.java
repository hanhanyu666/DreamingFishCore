package com.hhy.dreamingfishcore.gameplay.kill_effect_system.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Client-only state for the short-lived kill-effect presentation.
 *
 * <p>All methods are intentionally main-thread-only. Network handlers must enqueue calls to
 * {@link #start(int, UUID, double, double, double, float, float, long, int)} on the client
 * thread before touching this class.</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class KillEffectClientState {
    public static final int MAX_EFFECTS = 16;
    public static final double RECEIVE_RANGE = 64.0D;
    public static final double RECEIVE_RANGE_SQUARED = RECEIVE_RANGE * RECEIVE_RANGE;
    /** Hide only after the accelerated fall has placed the complete model below the surface. */
    public static final int HIDE_AFTER_TICKS = 20;
    private static final float FALL_START_PROGRESS = 0.16F;
    private static final float FALL_END_PROGRESS = 0.50F;

    private static final int MIN_DURATION_TICKS = 1;
    private static final int MAX_DURATION_TICKS = 40;
    private static final float MIN_SIZE = 0.01F;
    private static final float MAX_WIDTH = 16.0F;
    private static final float MAX_HEIGHT = 32.0F;
    private static final double WORLD_COORDINATE_LIMIT = 30_000_000.0D;
    private static final float SOUND_VOLUME = 0.72F;
    private static final float SOUND_PITCH = 1.55F;

    /** Insertion order is also the eviction order; replacing an id moves it to the newest end. */
    private static final LinkedHashMap<Integer, Snapshot> ACTIVE = new LinkedHashMap<>();
    /** Last verified live position, retained after the dying entity is removed client-side. */
    private static final Map<Integer, Vec3> LAST_POSITIONS = new HashMap<>();
    private static final Set<Integer> COLLAPSE_CUE_PLAYED = new HashSet<>();
    private static ClientLevel boundLevel;
    private static ResourceKey<Level> boundDimension;

    private KillEffectClientState() {
    }

    /**
     * Starts or replaces an effect snapshot. This method must only be called on the client main
     * thread; it deliberately uses ordinary collections rather than cross-thread synchronization.
     */
    public static void start(int entityId, UUID uuid, double x, double y, double z,
                             float width, float height, long seed, int durationTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            clearInternal();
            return;
        }

        bindTo(level);
        if (entityId < 0 || uuid == null || minecraft.player == null
                || minecraft.player.level() != level) {
            return;
        }

        double safeX = sanitizeCoordinate(x);
        double safeY = sanitizeCoordinate(y);
        double safeZ = sanitizeCoordinate(z);
        if (minecraft.player.position().distanceToSqr(safeX, safeY, safeZ) > RECEIVE_RANGE_SQUARED) {
            return;
        }

        float safeWidth = clampSize(width, MIN_SIZE, MAX_WIDTH);
        float safeHeight = clampSize(height, MIN_SIZE, MAX_HEIGHT);
        Snapshot snapshot = new Snapshot(
                entityId,
                uuid,
                level.dimension(),
                safeX,
                safeY,
                safeZ,
                safeWidth,
                safeHeight,
                seed,
                level.getGameTime(),
                clampDuration(durationTicks));

        // Remove before putting so a repeated entity id is refreshed and becomes newest.
        ACTIVE.remove(entityId);
        LAST_POSITIONS.remove(entityId);
        COLLAPSE_CUE_PLAYED.remove(entityId);
        ACTIVE.put(entityId, snapshot);
        LAST_POSITIONS.put(entityId, snapshot.position());
        while (ACTIVE.size() > MAX_EFFECTS) {
            Iterator<Integer> iterator = ACTIVE.keySet().iterator();
            if (iterator.hasNext()) {
                int removedId = iterator.next();
                iterator.remove();
                LAST_POSITIONS.remove(removedId);
                COLLAPSE_CUE_PLAYED.remove(removedId);
            } else {
                break;
            }
        }

        // One local cue per accepted start; this is deliberately not the glass-break sound.
        level.playLocalSound(safeX, safeY, safeZ, SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, SOUND_VOLUME, SOUND_PITCH, false);
        level.playLocalSound(safeX, safeY + safeHeight * 0.5F, safeZ, SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 0.42F, 0.62F, false);
    }

    /** Returns an immutable snapshot of the current render entries in oldest-to-newest order. */
    public static List<Snapshot> snapshots() {
        return ACTIVE.isEmpty() ? List.of() : List.copyOf(ACTIVE.values());
    }

    /** Alias kept for renderers that prefer a getter-style name. */
    public static List<Snapshot> getSnapshots() {
        return snapshots();
    }

    /**
     * Resolves a smooth live anchor while the dying entity still exists, then retains the final
     * verified position for the remaining lifetime of the visual effect.
     */
    public static Vec3 trackedPosition(Snapshot snapshot, float partialTick) {
        if (snapshot == null) {
            return Vec3.ZERO;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level != null && boundLevel == level
                && Objects.equals(snapshot.dimension(), level.dimension())
                && level.getEntity(snapshot.entityId()) instanceof LivingEntity entity
                && snapshot.uuid().equals(entity.getUUID())) {
            Vec3 position = entity.getPosition(clampPartialTick(partialTick));
            if (isFinite(position)) {
                LAST_POSITIONS.put(snapshot.entityId(), position);
                return position;
            }
        }
        return LAST_POSITIONS.getOrDefault(snapshot.entityId(), snapshot.position());
    }

    /**
     * Finds an entry by both id and UUID. The UUID check is mandatory so a recycled entity id
     * cannot make a new entity inherit an old effect.
     */
    public static Optional<Snapshot> snapshotFor(int entityId, UUID uuid) {
        if (uuid == null || !isCurrentClientLevel()) {
            return Optional.empty();
        }
        Snapshot snapshot = ACTIVE.get(entityId);
        return snapshot != null && uuid.equals(snapshot.uuid())
                && Objects.equals(snapshot.dimension(), boundDimension)
                ? Optional.of(snapshot)
                : Optional.empty();
    }

    /** Finds an entry for a live entity, checking both its id and UUID. */
    public static Optional<Snapshot> snapshotFor(LivingEntity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || entity.level() != minecraft.level) {
            return Optional.empty();
        }
        return snapshotFor(entity.getId(), entity.getUUID());
    }

    /** Nullable getter convenience for render code. */
    public static Snapshot getSnapshot(int entityId, UUID uuid) {
        return snapshotFor(entityId, uuid).orElse(null);
    }

    /** Nullable getter convenience for render code. */
    public static Snapshot getSnapshot(LivingEntity entity) {
        return snapshotFor(entity).orElse(null);
    }

    /**
     * Returns whether vanilla rendering should hide this entity during the effect. Hiding starts
     * after the model has visibly compressed toward the event horizon; stale id/UUID pairs never
     * hide anything.
     */
    public static boolean shouldHide(LivingEntity entity, float partialTick) {
        Snapshot snapshot = snapshotFor(entity).orElse(null);
        if (snapshot == null) {
            return false;
        }

        float elapsed = elapsedTicks(snapshot, partialTick);
        return elapsed >= HIDE_AFTER_TICKS && elapsed < snapshot.durationTicks();
    }

    /** Returns elapsed effect time in ticks, including a clamped render partial tick. */
    public static float elapsedTicks(Snapshot snapshot, float partialTick) {
        if (snapshot == null) {
            return 0.0F;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level == null
                ? snapshot.startGameTick()
                : minecraft.level.getGameTime();
        return elapsedTicks(snapshot, gameTime, partialTick);
    }

    /** Same helper with an explicit game tick, useful for deterministic render/tests. */
    public static float elapsedTicks(Snapshot snapshot, long gameTime, float partialTick) {
        if (snapshot == null) {
            return 0.0F;
        }
        if (gameTime < snapshot.startGameTick()) {
            return 0.0F;
        }
        if (gameTime == snapshot.startGameTick()) {
            return clampPartialTick(partialTick);
        }
        double elapsed = (double) (gameTime - snapshot.startGameTick()) + clampPartialTick(partialTick);
        return (float) Math.min(Float.MAX_VALUE, Math.max(0.0D, elapsed));
    }

    /** Returns normalized effect progress in the inclusive range [0, 1]. */
    public static float progress(Snapshot snapshot, float partialTick) {
        if (snapshot == null) {
            return 0.0F;
        }
        return clamp01(elapsedTicks(snapshot, partialTick) / (float) snapshot.durationTicks());
    }

    /** Convenience progress lookup that retains the UUID safety check. */
    public static float progress(int entityId, UUID uuid, float partialTick) {
        return progress(snapshotFor(entityId, uuid).orElse(null), partialTick);
    }

    /** Alias kept for renderer call sites that use a getter-style helper name. */
    public static float getProgress(Snapshot snapshot, float partialTick) {
        return progress(snapshot, partialTick);
    }

    /** Shared fall curve used by both the model transform and its surrounding pull streams. */
    public static float fallAmount(float progress) {
        float phase = clamp01((progress - FALL_START_PROGRESS)
                / Math.max(0.0001F, FALL_END_PROGRESS - FALL_START_PROGRESS));
        return phase * phase;
    }

    /** Moves the model far enough that even its top edge finishes below the block surface. */
    public static float fallDistance(float entityHeight) {
        float safeHeight = Float.isFinite(entityHeight) ? Math.max(MIN_SIZE, entityHeight) : MIN_SIZE;
        return Math.max(0.42F, safeHeight * 1.20F + 0.25F);
    }

    /** Removes expired entries and entries from a previous client level/dimension. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            clearInternal();
            return;
        }

        if (boundLevel != level || !Objects.equals(boundDimension, level.dimension())) {
            bindTo(level);
            return;
        }

        long gameTime = level.getGameTime();
        Iterator<Map.Entry<Integer, Snapshot>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Snapshot snapshot = iterator.next().getValue();
            if (level.getEntity(snapshot.entityId()) instanceof LivingEntity entity
                    && snapshot.uuid().equals(entity.getUUID())
                    && isFinite(entity.position())) {
                LAST_POSITIONS.put(snapshot.entityId(), entity.position());
            }
            float elapsed = elapsedTicks(snapshot, gameTime, 0.0F);
            if (!Objects.equals(snapshot.dimension(), level.dimension())
                    || elapsed >= snapshot.durationTicks()) {
                COLLAPSE_CUE_PLAYED.remove(snapshot.entityId());
                LAST_POSITIONS.remove(snapshot.entityId());
                iterator.remove();
            } else if (elapsed >= snapshot.durationTicks() * 0.68F
                    && COLLAPSE_CUE_PLAYED.add(snapshot.entityId())) {
                Vec3 cuePosition = LAST_POSITIONS.getOrDefault(snapshot.entityId(), snapshot.position());
                level.playLocalSound(cuePosition.x, cuePosition.y + snapshot.height() * 0.42F, cuePosition.z,
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.20F, 1.85F, false);
            }
        }
    }

    /** Clears all local effects when the client connection is closed. */
    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clearInternal();
    }

    /** Manual clear hook for client lifecycle code. */
    public static void clear() {
        clearInternal();
    }

    private static void bindTo(ClientLevel level) {
        if (boundLevel != level || !Objects.equals(boundDimension, level.dimension())) {
            ACTIVE.clear();
            LAST_POSITIONS.clear();
            COLLAPSE_CUE_PLAYED.clear();
            boundLevel = level;
            boundDimension = level.dimension();
        }
    }

    private static boolean isCurrentClientLevel() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null
                && boundLevel == minecraft.level
                && Objects.equals(boundDimension, minecraft.level.dimension());
    }

    private static void clearInternal() {
        ACTIVE.clear();
        LAST_POSITIONS.clear();
        COLLAPSE_CUE_PLAYED.clear();
        boundLevel = null;
        boundDimension = null;
    }

    private static int clampDuration(int durationTicks) {
        return Math.max(MIN_DURATION_TICKS, Math.min(MAX_DURATION_TICKS, durationTicks));
    }

    private static float clampSize(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double sanitizeCoordinate(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Math.max(-WORLD_COORDINATE_LIMIT, Math.min(WORLD_COORDINATE_LIMIT, value));
    }

    private static float clampPartialTick(float partialTick) {
        if (!Float.isFinite(partialTick)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, partialTick));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static boolean isFinite(Vec3 position) {
        return position != null
                && Double.isFinite(position.x)
                && Double.isFinite(position.y)
                && Double.isFinite(position.z);
    }

    /** Immutable data passed from the client state to a renderer. */
    public record Snapshot(int entityId, UUID uuid, ResourceKey<Level> dimension,
                           double x, double y, double z, float width, float height,
                           long seed, long startGameTick, int durationTicks) {
        public Snapshot {
            Objects.requireNonNull(uuid, "uuid");
            Objects.requireNonNull(dimension, "dimension");
            x = sanitizeCoordinate(x);
            y = sanitizeCoordinate(y);
            z = sanitizeCoordinate(z);
            width = clampSize(width, MIN_SIZE, MAX_WIDTH);
            height = clampSize(height, MIN_SIZE, MAX_HEIGHT);
            durationTicks = clampDuration(durationTicks);
        }

        public Vec3 position() {
            return new Vec3(x, y, z);
        }

        public float elapsedTicks(float partialTick) {
            return KillEffectClientState.elapsedTicks(this, partialTick);
        }

        public float progress(float partialTick) {
            return KillEffectClientState.progress(this, partialTick);
        }
    }
}
