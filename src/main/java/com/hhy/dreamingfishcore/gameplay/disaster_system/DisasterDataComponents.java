package com.hhy.dreamingfishcore.gameplay.disaster_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;

/**
 * 天灾分析数据用的数据组件。
 *
 * <p>一份数据"属于哪种天灾"是**物品本身的数据**，而不是八个独立物品：
 * 这样合成、掉落、堆叠都只处理一种物品，界面里靠名字区分。</p>
 */
public final class DisasterDataComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, DreamingFishCore.MODID);

    /** 天灾类型（存 {@link DisasterSchedule.EventType} 的名字）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> DISASTER_TYPE =
            COMPONENTS.register("disaster_type", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    private DisasterDataComponents() {
    }
}
