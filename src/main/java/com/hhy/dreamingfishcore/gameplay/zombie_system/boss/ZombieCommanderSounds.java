package com.hhy.dreamingfishcore.gameplay.zombie_system.boss;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 尸潮指挥官使用的声音事件。
 *
 * <p>目前只有一条 Boss 战 BGM。它是**长音轨**，所以 {@code sounds.json} 里标了 {@code stream}
 * （流式解码，避免整首 900KB 常驻内存），并且用 {@code createFixedRangeEvent} 把可听半径开到
 * 64 格 —— 默认的 {@code createVariableRangeEvent} 只按音量给 16 格，远一点就听不见了。</p>
 *
 * <p>事件 ID 必须与 {@code assets/dreamingfishcore/sounds.json} 里的键逐字一致。</p>
 */
public final class ZombieCommanderSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, DreamingFishCore.MODID);

    /** Boss 战 BGM：A 小调、104 BPM、32 小节循环，由 tools/compose_commander_bgm.py 生成。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> COMMANDER_MUSIC =
            register("music.zombie_commander");

    private ZombieCommanderSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(
                name,
                () -> SoundEvent.createFixedRangeEvent(
                        ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, name),
                        64.0F));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
