package com.hhy.dreamingfishcore.gameplay.zombie_system.boss.client;

import com.hhy.dreamingfishcore.gameplay.zombie_system.boss.ZombieCommanderEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * 尸潮指挥官 BGM 的播放实例：一个跟着 Boss 走、按距离调音量的循环音源。
 *
 * <p>用 {@link AbstractTickableSoundInstance} 而不是一次性的 {@code SimpleSoundInstance}，
 * 是因为要的东西它全都没有：这首要在 Boss 走动时跟着位置跑、玩家靠近时渐响、Boss 死了要淡出，
 * 而且必须能循环。</p>
 *
 * <p>距离衰减自己算（{@code Attenuation.NONE} + 在 {@link #tick()} 里设 volume），而不是用
 * 原版那套按音量的立方衰减 —— 那套在 18~48 格之间掉得太快，会出现「走出十几格突然没声」。</p>
 */
public final class CommanderMusicSound extends AbstractTickableSoundInstance {
    /** 这个距离以内是满音量。 */
    private static final float FULL_VOLUME_RANGE = 18.0F;
    /** 超过这个距离就整首停掉（比声音事件的 64 格可听半径小，留出淡出余量）。 */
    private static final float SILENT_RANGE = 48.0F;
    /** 基准音量：BGM 不该盖过音效，压在 0.62。 */
    private static final float BASE_VOLUME = 0.62F;
    /** 每 tick 的音量渐变步长，约一秒走完全程。 */
    private static final float FADE_STEP = 0.016F;

    private final Minecraft minecraft = Minecraft.getInstance();

    CommanderMusicSound(SoundEvent event) {
        super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        this.pitch = 1.0F;
        this.relative = false;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    @Override
    public void tick() {
        ZombieCommanderEntity nearest = findNearestCommander();
        if (nearest == null) {
            // Boss 死了或玩家走远了：淡出后收摊，不做硬切（硬切会「啪」一声）
            this.volume = Mth.approach(this.volume, 0.0F, FADE_STEP * 2.0F);
            if (this.volume <= 0.002F) {
                this.stop();
            }
            return;
        }
        this.x = (float) nearest.getX();
        this.y = (float) nearest.getY();
        this.z = (float) nearest.getZ();
        this.volume = Mth.approach(this.volume, targetVolume(nearest), FADE_STEP);
    }

    /** 18 格内满音量，到 48 格线性归零。 */
    private float targetVolume(ZombieCommanderEntity commander) {
        if (this.minecraft.player == null) {
            return 0.0F;
        }
        float distance = this.minecraft.player.distanceTo(commander);
        if (distance <= FULL_VOLUME_RANGE) {
            return BASE_VOLUME;
        }
        if (distance >= SILENT_RANGE) {
            return 0.0F;
        }
        float fade = 1.0F - (distance - FULL_VOLUME_RANGE) / (SILENT_RANGE - FULL_VOLUME_RANGE);
        return BASE_VOLUME * fade;
    }

    /** 场上最近的存活指挥官；超出可听范围返回 null。 */
    @Nullable
    private ZombieCommanderEntity findNearestCommander() {
        if (this.minecraft.level == null || this.minecraft.player == null) {
            return null;
        }
        ZombieCommanderEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : this.minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof ZombieCommanderEntity commander) || !commander.isAlive()) {
                continue;
            }
            double distance = this.minecraft.player.distanceTo(commander);
            if (distance < best) {
                best = distance;
                nearest = commander;
            }
        }
        return best <= SILENT_RANGE ? nearest : null;
    }
}
