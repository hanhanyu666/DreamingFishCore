package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.common.util.Utf8JsonFileIO;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.RevivalInfoManager;
import com.hhy.dreamingfishcore.item.DreamingFishCore_Items;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.util.UUID;

/** 客户端请求使用复活护符解封因复活点耗尽而被封禁的玩家。 */
public class Packet_RevivalRequest implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<Packet_RevivalRequest> TYPE =
            new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            DreamingFishCore.MODID,
                            "playerattribute_system/death_system/packet_revival_request"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_RevivalRequest> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(
                    (buf, packet) -> Packet_RevivalRequest.encode(packet, buf),
                    Packet_RevivalRequest::decode);

    private static final int MAX_PLAYER_NAME_LENGTH = 64;
    private final String playerName;

    public Packet_RevivalRequest(String playerName) {
        this.playerName = playerName;
    }

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(Packet_RevivalRequest packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.playerName == null ? "" : packet.playerName, MAX_PLAYER_NAME_LENGTH);
    }

    public static Packet_RevivalRequest decode(FriendlyByteBuf buf) {
        return new Packet_RevivalRequest(buf.readUtf(MAX_PLAYER_NAME_LENGTH));
    }

    public static void handle(Packet_RevivalRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> process(packet, context));
    }

    private static void process(Packet_RevivalRequest packet, IPayloadContext context) {
        ServerPlayer sender = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (sender == null || packet == null) {
            return;
        }

        String targetName = packet.playerName == null ? "" : packet.playerName.trim();
        if (targetName.isEmpty() || targetName.length() > MAX_PLAYER_NAME_LENGTH) {
            fail(sender, "§c玩家名称无效！");
            return;
        }
        if (!hasRevivalCharm(sender)) {
            fail(sender, "§c你需要手持复活护符才能复活玩家！");
            DreamingFishCore.LOGGER.warn("玩家 {} 在未持有复活护符时提交了复活请求",
                    sender.getScoreboardName());
            return;
        }

        UserBanList banList = sender.server.getPlayerList().getBans();
        Path banFile = sender.server.getServerDirectory().resolve("banned-players.json");
        JsonObject foundEntry;
        try {
            foundEntry = findBanEntry(banFile, targetName);
        } catch (IOException | RuntimeException exception) {
            DreamingFishCore.LOGGER.error("读取封禁文件失败", exception);
            fail(sender, "§c读取封禁数据失败！");
            return;
        }
        if (foundEntry == null) {
            fail(sender, "§c玩家 " + targetName + " 未被封禁！");
            return;
        }

        UUID targetUuid;
        try {
            if (!foundEntry.has("uuid")) {
                throw new IllegalArgumentException("missing uuid");
            }
            targetUuid = UUID.fromString(foundEntry.get("uuid").getAsString());
        } catch (RuntimeException exception) {
            fail(sender, "§c封禁数据中的玩家 UUID 无效，无法复活。");
            DreamingFishCore.LOGGER.error("死亡封禁条目 UUID 无效：{}", targetName, exception);
            return;
        }

        if (targetUuid.equals(sender.getUUID())) {
            fail(sender, "§c不能使用复活护符复活自己。");
            return;
        }

        com.mojang.authlib.GameProfile profile =
                new com.mojang.authlib.GameProfile(targetUuid, targetName);
        // banned-players.json 只是查找 UUID 的输入，最终权限以服务器当前的 ban list 为准。
        UserBanListEntry originalBanEntry = banList.get(profile);
        if (originalBanEntry == null || !banList.isBanned(profile)) {
            fail(sender, "§c玩家 " + targetName + " 未被封禁！");
            return;
        }
        if (!"DeathSystem".equals(originalBanEntry.getSource())) {
            fail(sender, "§c该玩家不是因复活点耗尽被封禁，无法用复活护符复活！");
            DreamingFishCore.LOGGER.warn("玩家 {} 尝试用复活护符复活非死亡封禁的玩家 {}",
                    sender.getScoreboardName(), targetName);
            return;
        }

        // 所有可失败的读取和校验都在解除封禁前完成，避免出现“已解封但属性未恢复”。
        PlayerAttributesData senderData = PlayerAttributesDataManager.findStoredPlayerAttributesData(sender.getUUID());
        PlayerAttributesData targetData = PlayerAttributesDataManager.findStoredPlayerAttributesData(targetUuid);
        if (senderData == null || targetData == null) {
            fail(sender, "§c玩家属性数据不完整，复活操作已取消；请联系管理员检查存档。");
            DreamingFishCore.LOGGER.error("复活 {} 时缺少属性数据（施救者={}, 目标={}）",
                    targetName, senderData != null, targetData != null);
            return;
        }

        AttributesSnapshot senderBackup = AttributesSnapshot.capture(senderData);
        AttributesSnapshot targetBackup = AttributesSnapshot.capture(targetData);
        RevivalInfoManager.RevivalInfo previousRevivalInfo =
                RevivalInfoManager.getRevivalInfo(targetUuid);
        ItemStack mainHandBackup = sender.getMainHandItem().copy();
        ItemStack offHandBackup = sender.getOffhandItem().copy();
        boolean banRemoved = false;
        boolean charmConsumed = false;
        boolean committed = false;
        try {
            boolean senderIsInfected = senderData.isInfected();
            senderData.setRespawnPoint(senderData.getRespawnPoint() / 2.0F);
            targetData.setInfected(senderIsInfected);
            targetData.setCurrentInfection(senderIsInfected ? 100.0F : 0.0F);
            targetData.setRespawnPoint(100.0F);
            targetData.setCurrentCourage(50.0F);
            targetData.setCurrentStrength(targetData.getMaxStrength());

            PlayerAttributesDataManager.saveSinglePlayerData(targetUuid, targetData);
            PlayerAttributesDataManager.saveSinglePlayerData(sender.getUUID(), senderData);
            RevivalInfoManager.setRevivalInfo(targetUuid, sender.getScoreboardName(), senderIsInfected);

            // 先确保属性和复活提示已经落盘，再触碰不可逆的封禁状态。
            if (!PlayerAttributesDataManager.saveIfDirty(sender.server)
                    || !RevivalInfoManager.saveIfDirty(sender.server)) {
                throw new IllegalStateException("复活数据无法落盘");
            }

            // 先标记“可能已消费”，再执行 shrink；即使第三方物品实现抛异常，
            // 回滚分支也会恢复两只手的快照，不留下半个护符。
            charmConsumed = !sender.getAbilities().instabuild;
            if (!consumeRevivalCharm(sender)) {
                throw new IllegalStateException("复活护符在提交过程中消失");
            }

            banList.remove(profile);
            banRemoved = true;
            // StoredUserList.remove 会吞掉保存异常；显式保存并复查，避免内存已解封而文件仍封禁。
            banList.save();
            if (banList.isBanned(profile)) {
                throw new IllegalStateException("封禁列表未能移除目标");
            }
            committed = true;
        } catch (Exception exception) {
            // 在事务提交前的任何失败都恢复内存、物品和封禁状态。
            restoreAttributes(sender.getUUID(), senderData, senderBackup);
            restoreAttributes(targetUuid, targetData, targetBackup);
            restoreRevivalInfo(targetUuid, previousRevivalInfo);
            try {
                PlayerAttributesDataManager.saveIfDirty(sender.server);
                RevivalInfoManager.saveIfDirty(sender.server);
            } catch (RuntimeException rollbackDataException) {
                exception.addSuppressed(rollbackDataException);
            }
            if (charmConsumed) {
                try {
                    sender.setItemInHand(InteractionHand.MAIN_HAND, mainHandBackup);
                    sender.setItemInHand(InteractionHand.OFF_HAND, offHandBackup);
                    sender.getInventory().setChanged();
                    sender.containerMenu.broadcastChanges();
                } catch (RuntimeException rollbackItemException) {
                    exception.addSuppressed(rollbackItemException);
                }
            }
            if (banRemoved) {
                try {
                    banList.add(originalBanEntry);
                    banList.save();
                } catch (RuntimeException rollbackBanException) {
                    exception.addSuppressed(rollbackBanException);
                } catch (IOException rollbackBanException) {
                    exception.addSuppressed(rollbackBanException);
                }
            }
            DreamingFishCore.LOGGER.error("复活玩家 {} 的事务失败，已回滚可回滚状态",
                    targetName, exception);
            fail(sender, "§c复活失败，未完成的状态已回滚；请稍后再试。");
        }

        if (committed) {
            boolean senderIsInfected = senderData.isInfected();
            sender.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§d§l✦ 复活成功 ✦\n§f您已复活玩家 §e" + targetName
                            + (senderIsInfected
                            ? "\n§7由于您是感染者，该玩家以感染者身份复活"
                            : "\n§7由于您是幸存者，该玩家以幸存者身份复活")
                            + "\n§c您失去了一半的复活点数"));
            DreamingFishCore.LOGGER.info("玩家 {} 使用复活护符复活了玩家 {}",
                    sender.getScoreboardName(), targetName);
        }
    }

    private static JsonObject findBanEntry(Path banFile, String targetName) throws IOException {
        try (Reader reader = Utf8JsonFileIO.openReader(banFile.toFile())) {
            JsonArray bans = new Gson().fromJson(reader, JsonArray.class);
            if (bans == null) {
                return null;
            }
            for (JsonElement element : bans) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject ban = element.getAsJsonObject();
                if (ban.has("name") && targetName.equalsIgnoreCase(ban.get("name").getAsString())) {
                    return ban;
                }
            }
            return null;
        }
    }

    private static void restoreAttributes(UUID uuid, PlayerAttributesData data,
                                          AttributesSnapshot backup) {
        backup.restore(data);
        try {
            PlayerAttributesDataManager.saveSinglePlayerData(uuid, data);
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.error("回滚玩家 {} 的属性数据失败", uuid, exception);
        }
    }

    private static void restoreRevivalInfo(UUID targetUuid,
                                           RevivalInfoManager.RevivalInfo previous) {
        try {
            if (previous == null) {
                RevivalInfoManager.removeRevivalInfo(targetUuid);
            } else {
                RevivalInfoManager.setRevivalInfo(
                        targetUuid, previous.getReviverName(), previous.isReviverInfected());
            }
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.error("回滚玩家 {} 的复活提示记录失败", targetUuid, exception);
        }
    }

    private static void fail(ServerPlayer player, String message) {
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
    }

    private static boolean consumeRevivalCharm(ServerPlayer player) {
        if (player.getAbilities().instabuild) {
            return hasRevivalCharm(player);
        }
        ItemStack mainHandItem = player.getMainHandItem();
        if (mainHandItem.is(DreamingFishCore_Items.REVIVAL_CHARM.get())
                && !mainHandItem.isEmpty()) {
            mainHandItem.shrink(1);
            player.setItemInHand(InteractionHand.MAIN_HAND, mainHandItem);
            return true;
        }

        ItemStack offHandItem = player.getOffhandItem();
        if (offHandItem.is(DreamingFishCore_Items.REVIVAL_CHARM.get())
                && !offHandItem.isEmpty()) {
            offHandItem.shrink(1);
            player.setItemInHand(InteractionHand.OFF_HAND, offHandItem);
            return true;
        }
        return false;
    }

    private static boolean hasRevivalCharm(ServerPlayer player) {
        return player.getMainHandItem().is(DreamingFishCore_Items.REVIVAL_CHARM.get())
                || player.getOffhandItem().is(DreamingFishCore_Items.REVIVAL_CHARM.get());
    }

    private record AttributesSnapshot(boolean infected, float infection, float respawnPoint,
                                      float courage, int strength) {
        private static AttributesSnapshot capture(PlayerAttributesData data) {
            return new AttributesSnapshot(data.isInfected(), data.getCurrentInfection(),
                    data.getRespawnPoint(), data.getCurrentCourage(), data.getCurrentStrength());
        }

        private void restore(PlayerAttributesData data) {
            data.setInfected(infected);
            data.setCurrentInfection(infection);
            data.setRespawnPoint(respawnPoint);
            data.setCurrentCourage(courage);
            data.setCurrentStrength(strength);
        }
    }
}
