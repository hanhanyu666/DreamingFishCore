package com.hhy.dreamingfishcore.server.persistence;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Stateless resolver for runtime data stored below the active world save.
 */
public final class WorldDataPaths {
    private WorldDataPaths() {
    }

    public static Path resolve(MinecraftServer server, String first, String... more) {
        Objects.requireNonNull(server, "server");
        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path dataRoot = worldRoot.resolve("data").resolve(DreamingFishCore.MODID).normalize();
        Path resolved = dataRoot.resolve(Path.of(first, more)).normalize();
        if (!resolved.startsWith(dataRoot)) {
            throw new IllegalArgumentException("数据路径越过世界数据目录：" + resolved);
        }
        return resolved;
    }
}
