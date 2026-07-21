package com.hhy.dreamingfishcore.server.check_system;

import com.mojang.logging.LogUtils;
import com.hhy.dreamingfishcore.server.check_system.network.FileInspectionSecurity;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class FileInspectionSessionManager {
    private static final long SESSION_TIMEOUT_MILLIS = 120_000L;
    private static final long MIN_REQUEST_INTERVAL_MILLIS = 2_000L;
    private static final long RATE_ENTRY_RETENTION_MILLIS = 600_000L;
    private static final int MAX_ACTIVE_SESSIONS = 32;
    private static final int MAX_SESSIONS_PER_REQUESTER = 4;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> LAST_REQUEST_BY_ADMIN = new HashMap<>();
    private static final Map<UUID, Long> LAST_REQUEST_BY_TARGET = new HashMap<>();

    private FileInspectionSessionManager() {
    }

    public static synchronized Session createCheck(ServerPlayer requester, ServerPlayer target, String actionType) {
        return create(requester, target, Operation.CHECK, actionType, null);
    }

    public static synchronized Session createGet(ServerPlayer requester, ServerPlayer target, String actionType, String fileName) {
        return create(requester, target, Operation.GET, actionType, fileName);
    }

    public static synchronized Session consumeSingleResult(String requestId, ServerPlayer responder, Operation operation) {
        Session session = findValidated(requestId, responder, operation);
        if (session == null) {
            return null;
        }
        SESSIONS.remove(session.requestId);
        return session;
    }

    public static synchronized Session acceptChunk(String requestId, ServerPlayer responder, int chunkIndex, int totalChunks) {
        Session session = findValidated(requestId, responder, Operation.GET);
        if (session == null || totalChunks < 2 || totalChunks > FileInspectionSecurity.MAX_CHUNKS
                || chunkIndex < 0 || chunkIndex >= totalChunks) {
            return null;
        }

        if (session.totalChunks == -1) {
            session.totalChunks = totalChunks;
            session.receivedChunks = new BitSet(totalChunks);
        } else if (session.totalChunks != totalChunks) {
            return null;
        }

        if (session.receivedChunks.get(chunkIndex)) {
            return null;
        }
        session.receivedChunks.set(chunkIndex);
        session.expiresAt = System.currentTimeMillis() + SESSION_TIMEOUT_MILLIS;
        if (session.receivedChunks.cardinality() == totalChunks) {
            SESSIONS.remove(session.requestId);
        }
        return session;
    }

    private static Session create(ServerPlayer requester, ServerPlayer target, Operation operation,
                                  String actionType, String fileName) {
        cleanupExpired();
        String normalizedType = FileInspectionSecurity.normalizeActionType(actionType);
        if (requester == null || target == null || !requester.hasPermissions(2) || normalizedType == null) {
            return null;
        }
        if (operation == Operation.GET && !FileInspectionSecurity.isSafeFileName(fileName)) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (isRateLimited(LAST_REQUEST_BY_ADMIN, requester.getUUID(), now)
                || isRateLimited(LAST_REQUEST_BY_TARGET, target.getUUID(), now)) {
            return null;
        }
        if (SESSIONS.size() >= MAX_ACTIVE_SESSIONS) {
            return null;
        }
        long requesterSessions = SESSIONS.values().stream()
                .filter(session -> session.requesterUuid.equals(requester.getUUID()))
                .count();
        if (requesterSessions >= MAX_SESSIONS_PER_REQUESTER) {
            return null;
        }

        UUID requestId = UUID.randomUUID();
        Session session = new Session(
                requestId,
                requester.getUUID(),
                target.getUUID(),
                target.getGameProfile().getName(),
                operation,
                normalizedType,
                fileName,
                now + SESSION_TIMEOUT_MILLIS
        );
        SESSIONS.put(requestId, session);
        LAST_REQUEST_BY_ADMIN.put(requester.getUUID(), now);
        LAST_REQUEST_BY_TARGET.put(target.getUUID(), now);
        LOGGER.info("File inspection session {}: admin={} target={} operation={} type={} file={}",
                requestId,
                requester.getGameProfile().getName(),
                target.getGameProfile().getName(),
                operation,
                normalizedType,
                fileName == null ? "<manifest>" : fileName);
        return session;
    }

    private static boolean isRateLimited(Map<UUID, Long> requests, UUID playerUuid, long now) {
        Long lastRequest = requests.get(playerUuid);
        return lastRequest != null && now - lastRequest < MIN_REQUEST_INTERVAL_MILLIS;
    }

    private static Session findValidated(String requestId, ServerPlayer responder, Operation operation) {
        cleanupExpired();
        UUID parsedId;
        try {
            parsedId = UUID.fromString(requestId);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return null;
        }

        Session session = SESSIONS.get(parsedId);
        if (session == null || session.operation != operation || responder == null
                || !session.targetUuid.equals(responder.getUUID())) {
            return null;
        }
        ServerPlayer requester = responder.server.getPlayerList().getPlayer(session.requesterUuid);
        if (requester == null || !requester.hasPermissions(2)) {
            SESSIONS.remove(parsedId);
            return null;
        }
        return session;
    }

    private static void cleanupExpired() {
        long now = System.currentTimeMillis();
        Iterator<Session> iterator = SESSIONS.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().expiresAt < now) {
                iterator.remove();
            }
        }
        LAST_REQUEST_BY_ADMIN.entrySet().removeIf(entry -> now - entry.getValue() > RATE_ENTRY_RETENTION_MILLIS);
        LAST_REQUEST_BY_TARGET.entrySet().removeIf(entry -> now - entry.getValue() > RATE_ENTRY_RETENTION_MILLIS);
    }

    public enum Operation {
        CHECK,
        GET
    }

    public static final class Session {
        private final UUID requestId;
        private final UUID requesterUuid;
        private final UUID targetUuid;
        private final String targetName;
        private final Operation operation;
        private final String actionType;
        private final String fileName;
        private long expiresAt;
        private int totalChunks = -1;
        private BitSet receivedChunks;

        private Session(UUID requestId, UUID requesterUuid, UUID targetUuid, String targetName,
                        Operation operation, String actionType, String fileName, long expiresAt) {
            this.requestId = requestId;
            this.requesterUuid = requesterUuid;
            this.targetUuid = targetUuid;
            this.targetName = targetName;
            this.operation = operation;
            this.actionType = actionType;
            this.fileName = fileName;
            this.expiresAt = expiresAt;
        }

        public String requestId() {
            return requestId.toString();
        }

        public UUID requesterUuid() {
            return requesterUuid;
        }

        public String targetUuid() {
            return targetUuid.toString();
        }

        public String targetName() {
            return targetName;
        }

        public String actionType() {
            return actionType;
        }

        public String fileName() {
            return fileName;
        }
    }
}
