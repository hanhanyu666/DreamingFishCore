package com.hhy.dreamingfishcore.gameplay.spawner_system.client;

import com.hhy.dreamingfishcore.gameplay.spawner_system.SpawnerView;
import net.minecraft.core.BlockPos;

/**
 * 客户端的刷怪箱快照缓存。
 *
 * <p>界面只读这里的数据：服务端每次修改成功后都会补发一份快照，客户端不需要自己拼增量。
 * 退出世界时要清空（见 {@code ClientCacheManager}）。</p>
 */
public final class SpawnerConfigClientCache {

    private static SpawnerView view;
    private static BlockPos pos;
    private static String message = "";
    private static boolean lastSuccess;

    private SpawnerConfigClientCache() {
    }

    public static void set(SpawnerView newView) {
        view = newView;
        if (newView != null) {
            pos = new BlockPos(newView.x(), newView.y(), newView.z());
        }
    }

    public static SpawnerView get() {
        return view;
    }

    public static BlockPos pos() {
        return pos;
    }

    public static void setMessage(boolean success, String text) {
        lastSuccess = success;
        message = text == null ? "" : text;
    }

    public static String message() {
        return message;
    }

    public static boolean lastSuccess() {
        return lastSuccess;
    }

    public static void clear() {
        view = null;
        pos = null;
        message = "";
        lastSuccess = false;
    }
}
