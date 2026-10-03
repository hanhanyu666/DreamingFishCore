package com.hhy.dreamingfishcore.gameplay.zombie_system.archer;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 射手僵尸与骨刺投射物使用的声音事件。
 *
 * <p>事件 ID 必须与 {@code assets/dreamingfishcore/sounds.json} 里的键逐字一致——原版在加载
 * {@code sounds.json} 时用的是「文件所在命名空间 + 键」拼出事件 ID，所以这里注册成
 * {@code dreamingfishcore:entity.archer_zombie.shoot}，json 里的键就要写
 * {@code entity.archer_zombie.shoot}。</p>
 *
 * <p>音源本身借助 {@code sounds.json} 的 {@code "type": "event"} 别名复用了原版音效：这样
 * 不必往仓库里再塞 ogg，同时模组仍然拥有自己命名空间下、可供资源包覆盖的独立事件。</p>
 */
public final class ArcherZombieSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, DreamingFishCore.MODID);

    /** 蓄力抬手：僵尸低吼，提示玩家「它要出手了」。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> ARCHER_ZOMBIE_CHARGE =
            register("entity.archer_zombie.charge");

    /** 发射骨刺：破空声。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> ARCHER_ZOMBIE_SHOOT =
            register("entity.archer_zombie.shoot");

    /** 骨刺命中方块或实体。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> BONE_SPIKE_HIT =
            register("entity.bone_spike.hit");

    private ArcherZombieSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(
                name,
                () -> SoundEvent.createVariableRangeEvent(
                        ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
