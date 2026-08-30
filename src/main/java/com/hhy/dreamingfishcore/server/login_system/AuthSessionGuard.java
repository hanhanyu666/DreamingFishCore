package com.hhy.dreamingfishcore.server.login_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.login_system.event.PlayerAuthenticatedEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端会话认证状态。
 *
 * <p>认证状态只保存在内存中，绝不从玩家持久化数据恢复，避免重启后把旧会话误当成
 * 当前会话。登录成功后由 {@link #markAuthenticated(ServerPlayer)} 派发统一事件。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class AuthSessionGuard {
    /** UUID -> 当前已认证的实体；保存实体引用可避免同 UUID 重连时旧实体继承认证状态。 */
    private static final Map<UUID, ServerPlayer> AUTHENTICATED_PLAYERS = new ConcurrentHashMap<>();
    private static final Map<UUID, AttemptState> LOGIN_ATTEMPTS = new ConcurrentHashMap<>();

    private static final int MAX_FAILURES = 5;
    private static final long FAILURE_WINDOW_MILLIS = 60_000L;
    private static final long LOCKOUT_MILLIS = 30_000L;

    private AuthSessionGuard() {
    }

    /** 新连接建立时先撤销上一个实体可能留下的认证状态。 */
    public static void beginSession(ServerPlayer player) {
        if (player == null) {
            return;
        }
        // 同 UUID 的新连接建立时，旧实体若仍在玩家列表中也不能继续通过门禁。
        AUTHENTICATED_PLAYERS.remove(player.getUUID());
        try {
            PlayerLoginData data = PlayerLoginDataManager.getLoginData(player.getUUID());
            if (data != null) {
                data.setLoginSessionCompleted(false);
            }
        } catch (RuntimeException exception) {
            // 世界数据尚未加载时只清理内存状态，登录流程会在数据加载后再次处理。
            DreamingFishCore.LOGGER.debug("无法在登录开始时更新玩家会话标记", exception);
        }
    }

    /** 标记认证成功并通知各个需要在登录后同步的系统。必须在服务器主线程调用。 */
    public static void markAuthenticated(ServerPlayer player) {
        if (player == null) {
            return;
        }
        AUTHENTICATED_PLAYERS.put(player.getUUID(), player);
        try {
            PlayerLoginData data = PlayerLoginDataManager.getLoginData(player.getUUID());
            if (data != null) {
                data.setLoginSessionCompleted(true);
            }
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.debug("无法更新玩家登录会话标记", exception);
        }
        // 延迟到当前 PlayerLoggedIn/登录响应处理完成后再同步，确保玩家基础/属性数据
        // 已经初始化；同时避免在事件总线回调中嵌套派发导致重复同步。
        var server = player.getServer();
        if (server != null) {
            // 同一网络连接可能在任务执行前因死亡重生而换成新的 ServerPlayer 实体；
            // 连接对象保持不变，可据此既跟随合法 Clone，又拒绝同 UUID 的全新连接。
            var connection = player.connection;
            server.execute(() -> {
                ServerPlayer current = server.getPlayerList().getPlayer(player.getUUID());
                if (current != null
                        && current.connection == connection
                        && isAuthenticated(current)) {
                    NeoForge.EVENT_BUS.post(new PlayerAuthenticatedEvent(current));
                }
            });
        } else {
            NeoForge.EVENT_BUS.post(new PlayerAuthenticatedEvent(player));
        }
    }

    /** 玩家退出或实体销毁时撤销当前会话。 */
    public static void invalidate(ServerPlayer player) {
        if (player != null) {
            AUTHENTICATED_PLAYERS.remove(player.getUUID(), player);
        }
    }

    public static boolean isAuthenticated(ServerPlayer player) {
        return player != null && AUTHENTICATED_PLAYERS.get(player.getUUID()) == player;
    }

    /** 同时确认实体仍是该 UUID 当前连接对应的玩家，供排队到主线程的网络任务复核。 */
    public static boolean isCurrentAuthenticated(ServerPlayer player) {
        if (!isAuthenticated(player) || player.getServer() == null) {
            return false;
        }
        return player.getServer().getPlayerList().getPlayer(player.getUUID()) == player;
    }

    public static boolean isAuthenticated(IPayloadContext context) {
        return context != null && context.player() instanceof ServerPlayer player && isAuthenticated(player);
    }

    /**
     * 原版死亡重生会创建新的 {@link ServerPlayer} 实例，但它仍属于同一条网络会话。
     * 在其他 Clone/Respawn 监听器运行前迁移实体引用，避免新实体被误判为未登录。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer oldPlayer)
                || !(event.getEntity() instanceof ServerPlayer newPlayer)
                || !oldPlayer.getUUID().equals(newPlayer.getUUID())) {
            return;
        }

        AUTHENTICATED_PLAYERS.replace(oldPlayer.getUUID(), oldPlayer, newPlayer);
    }

    /** 登录/注册包使用的轻量级尝试频率限制。 */
    public static boolean allowLoginAttempt(UUID playerUUID) {
        if (playerUUID == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        synchronized (LOGIN_ATTEMPTS) {
            AttemptState state = LOGIN_ATTEMPTS.get(playerUUID);
            if (state == null || now - state.windowStartedAt >= FAILURE_WINDOW_MILLIS) {
                LOGIN_ATTEMPTS.put(playerUUID, new AttemptState(0, now, 0L));
                return true;
            }
            return state.lockedUntil <= now;
        }
    }

    public static void recordLoginFailure(UUID playerUUID) {
        if (playerUUID == null) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (LOGIN_ATTEMPTS) {
            AttemptState state = LOGIN_ATTEMPTS.get(playerUUID);
            if (state == null || now - state.windowStartedAt >= FAILURE_WINDOW_MILLIS) {
                state = new AttemptState(0, now, 0L);
            }
            int failures = state.failures + 1;
            long lockedUntil = failures >= MAX_FAILURES ? now + LOCKOUT_MILLIS : state.lockedUntil;
            LOGIN_ATTEMPTS.put(playerUUID, new AttemptState(failures, state.windowStartedAt, lockedUntil));
        }
    }

    public static void recordLoginSuccess(UUID playerUUID) {
        if (playerUUID != null) {
            LOGIN_ATTEMPTS.remove(playerUUID);
        }
    }

    /** 返回还需等待的秒数，用于向玩家显示友好提示。 */
    public static long loginRetryAfterSeconds(UUID playerUUID) {
        if (playerUUID == null) {
            return 1L;
        }
        AttemptState state = LOGIN_ATTEMPTS.get(playerUUID);
        if (state == null || state.lockedUntil <= System.currentTimeMillis()) {
            return 0L;
        }
        long remaining = state.lockedUntil - System.currentTimeMillis();
        return Math.max(1L, (remaining + 999L) / 1000L);
    }

    /** 停服时清理所有会话和尝试计数，防止跨世界/跨服务器污染。 */
    public static void clear() {
        AUTHENTICATED_PLAYERS.clear();
        LOGIN_ATTEMPTS.clear();
    }

    /** 登录前禁止玩家借助原版命令绕过自定义会话门禁；控制台命令不受影响。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommand(CommandEvent event) {
        CommandSourceStack source = event.getParseResults().getContext().getSource();
        if (source.getEntity() instanceof ServerPlayer player && !isAuthenticated(player)) {
            event.setCanceled(true);
        }
    }

    /** 登录前不允许发送聊天内容，避免旁观状态成为未认证信息通道。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerChat(ServerChatEvent event) {
        if (!isAuthenticated(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelInteractionIfUnauthenticated(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelInteractionIfUnauthenticated(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelInteractionIfUnauthenticated(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        cancelInteractionIfUnauthenticated(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        cancelInteractionIfUnauthenticated(event);
    }

    private static void cancelInteractionIfUnauthenticated(PlayerInteractEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !isAuthenticated(player)) {
            if (event instanceof ICancellableEvent cancellable) {
                cancellable.setCanceled(true);
            }
        }
    }

    private record AttemptState(int failures, long windowStartedAt, long lockedUntil) {
    }
}
