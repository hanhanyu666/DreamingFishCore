package com.hhy.dreamingfishcore.gameplay.blueprint_system.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.blueprint_system.PlayerBlueprintData;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = DreamingFishCore.MODID)
public class BlueprintEventHandler {
    // 玩家重生事件（核心）
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();  // 死亡前的玩家
        Player newPlayer = event.getEntity();   // 重生后的玩家

        // 只在真实死亡时清空蓝图
        if (event.isWasDeath()
                && ((original instanceof net.minecraft.server.level.ServerPlayer originalServer
                && AuthSessionGuard.isAuthenticated(originalServer))
                || (newPlayer instanceof net.minecraft.server.level.ServerPlayer newServer
                && AuthSessionGuard.isAuthenticated(newServer)))) {
            handleDeathBlueprints(original, newPlayer);
        }
        // 注意：维度转换（如去末地）也会触发 Clone，但 isWasDeath() 为 false
    }

    private static void handleDeathBlueprints(Player original, Player newPlayer) {
        // 清空新玩家的蓝图数据
        PlayerBlueprintData.clearAllUnlocks(newPlayer);

        // 发送死亡提示消息
        sendDeathMessage(newPlayer);
    }

    private static void sendDeathMessage(Player player) {
        if (!player.level().isClientSide()) {
            player.sendSystemMessage(
                    Component.literal("§c你遗忘了所有学习过的蓝图")
            );

            player.sendSystemMessage(
                    Component.literal("§7需要重新寻找蓝图来解锁物品")
            );
        }
    }
}
