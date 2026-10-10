package com.hhy.dreamingfishcore.gameplay.zombie_system.boss.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.zombie_system.boss.ZombieCommanderEntity;
import com.hhy.dreamingfishcore.gameplay.zombie_system.boss.ZombieCommanderSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Boss 战 BGM 的起停管理。
 *
 * <p>只在「附近有存活的指挥官、而当前这首已经不在了」时起新的一首；一旦播起来就交给
 * {@link CommanderMusicSound} 自己按距离淡入淡出，这里不再插手 —— 否则每 tick 判一次远近、
 * 反复 stop/start，听起来就是一卡一卡。</p>
 *
 * <p>起播前先 {@code stopPlaying()} 掉原版背景音乐：两首叠在一起很难听，而且原版的
 * {@code MusicManager} 在 Boss 战这种场景本来也不该继续放。它自己会重新计时，通常十几分钟后
 * 才会想再放，所以只停这一次就够。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class CommanderMusicManager {
    /** 与 CommanderMusicSound 的静默半径保持一致。 */
    private static final double START_RANGE = 48.0D;

    /** 当前这一首；null 表示没在播。 */
    @Nullable
    private static CommanderMusicSound playing;

    private CommanderMusicManager() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            playing = null;
            return;
        }
        if (playing != null && !playing.isStopped()) {
            return;  // 还在播，让它自己处理淡入淡出
        }
        playing = null;
        if (!hasCommanderNearby(minecraft)) {
            return;
        }
        minecraft.getMusicManager().stopPlaying();
        CommanderMusicSound sound =
                new CommanderMusicSound(ZombieCommanderSounds.COMMANDER_MUSIC.get());
        playing = sound;
        minecraft.getSoundManager().play(sound);
    }

    private static boolean hasCommanderNearby(Minecraft minecraft) {
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof ZombieCommanderEntity commander
                    && commander.isAlive()
                    && minecraft.player.distanceTo(commander) <= START_RANGE) {
                return true;
            }
        }
        return false;
    }
}
