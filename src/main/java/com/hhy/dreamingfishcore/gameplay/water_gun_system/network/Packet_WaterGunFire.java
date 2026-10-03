package com.hhy.dreamingfishcore.gameplay.water_gun_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.water_gun_system.Item_WaterGun;
import com.hhy.dreamingfishcore.gameplay.water_gun_system.WaterGunConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端请求「扣一次扳机」。
 *
 * <p>不信任客户端：服务端自己确认主手确实是水枪、配置是开的、冷却已过，然后才消耗水量并生成
 * 水柱。冷却用原版的 {@code ItemCooldowns}（不是自己维护计时表）——它自带客户端同步，
 * 于是物品栏上会有一条正在回满的冷却条，玩家不用猜射速。</p>
 *
 * <p>这个包经过 {@code DreamingFishCore_NetworkManager} 的 {@code authenticated(...)} 包装，
 * 所以处理器一定跑在服务端主线程、且玩家已通过登录验证。</p>
 */
public record Packet_WaterGunFire() implements CustomPacketPayload {
    public static final Type<Packet_WaterGunFire> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "water_gun/fire"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_WaterGunFire> STREAM_CODEC =
            StreamCodec.of(Packet_WaterGunFire::encode, Packet_WaterGunFire::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, Packet_WaterGunFire packet) {
    }

    private static Packet_WaterGunFire decode(FriendlyByteBuf buffer) {
        return new Packet_WaterGunFire();
    }

    public static void handle(Packet_WaterGunFire packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        WaterGunConfig.Resolved settings = WaterGunConfig.current().resolve();
        if (!settings.enabled()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof Item_WaterGun waterGun)) {
            return;
        }
        if (player.getCooldowns().isOnCooldown(waterGun)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (Item_WaterGun.fireJet(serverLevel, player, stack)) {
            player.getCooldowns().addCooldown(waterGun, settings.fireIntervalTicks());
        } else {
            player.displayClientMessage(
                    Component.translatable(Item_WaterGun.KEY_EMPTY).withStyle(ChatFormatting.GRAY), true);
        }
    }
}
