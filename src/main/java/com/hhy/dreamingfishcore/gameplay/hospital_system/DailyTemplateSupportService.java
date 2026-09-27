package com.hhy.dreamingfishcore.gameplay.hospital_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.npc_system.StoryNpcContentPolicy;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.PendingDeathData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.RespawnPointSyncManager;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import java.util.UUID;

/**
 * 每日维护只通过有距离和身份校验的 NPC 动作进入。
 * 扣物与付款收据一起保存进玩家 NBT，确认落盘后才写入属性中的余量及每日记录。
 * 已付款但未到账的操作可在重连时补齐；旧收据不会再次增加余量。
 */
public final class DailyTemplateSupportService {
    private static final String PAYMENT_KEY = "DreamingFishCore_HospitalPayment";
    private DailyTemplateSupportService() { }

    public static boolean isAvailable(ServerPlayer player, int npcId) {
        return npcId == StoryNpcContentPolicy.MEDICAL_STAFF_ID && HospitalStory.isStarted()
                && player != null && AuthSessionGuard.isAuthenticated(player)
                && PlayerAttributesDataManager.areWritesEnabled()
                && player.isAlive() && !player.isSpectator() && !PendingDeathData.hasPendingRecord(player);
    }

    public static Item configuredItem() {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(HospitalConfig.get().dailyItemId()));
        if (item == Items.AIR) throw new IllegalStateException("每日维护物品不存在：" + HospitalConfig.get().dailyItemId());
        return item;
    }

    public static String description(ServerPlayer player) {
        var config = HospitalConfig.get();
        var data = PlayerAttributesDataManager.findStoredPlayerAttributesData(player.getUUID());
        String status = !HospitalStory.isStarted() ? "临时维护尚未开始，请留意医疗组公告。"
                : data == null ? "正在等待你的个人档案。"
                : data.getDailyTemplateSupport().getLastClaimDay() >= currentDay(player) ? "今天已经完成维护，下一个主世界游戏日可以再来。"
                : data.getRespawnPoint() >= 100 ? "你的模板重建余量已经充足，无需提交物资。" : "今天可以办理维护。";
        return "我负责模板维护和物资登记。每个主世界游戏日提交 " + config.dailyItemCount() + " 个"
                + materialName(configuredItem()) + "，恢复 " + number(config.dailyRestorePoints())
                + " 点模板重建余量，最高恢复到 100。\n\n" + (data == null ? "" : "当前余量：" + number(data.getRespawnPoint()) + "/100。\n") + status
                + "\n\n确认办理时，请选择“提交并维护”。普通交谈不会扣除物品。";
    }

    public static String claim(ServerPlayer player) {
        if (!isAvailable(player, StoryNpcContentPolicy.MEDICAL_STAFF_ID)) throw new IllegalStateException("当前无法办理模板维护");
        // 先完成上次已经付款的操作；无论跨天与否，本次都不会再收第二份材料。
        if (hasPayment(player)) {
            settlePayment(player);
            return "已核对上次的维护申请，没有重复收取材料。";
        }
        var data = PlayerAttributesDataManager.findStoredPlayerAttributesData(player.getUUID());
        if (data == null) throw new IllegalStateException("个人档案尚未就绪，未收取物品");
        long day = currentDay(player);
        if (data.getDailyTemplateSupport().getLastClaimDay() >= day) throw new IllegalStateException("本游戏日已经领取，请在主世界进入下一天后再来");
        if (!data.getDailyTemplateSupport().canClaim(day, data.getRespawnPoint())) throw new IllegalStateException("模板重建余量已满或当前数据无法维护，未收取物品");
        var config = HospitalConfig.get();
        Item item = configuredItem();
        int total = 0;
        for (ItemStack stack : player.getInventory().items) if (stack.is(item)) total += stack.getCount();
        for (ItemStack stack : player.getInventory().offhand) if (stack.is(item)) total += stack.getCount();
        if (total < config.dailyItemCount()) throw new IllegalStateException("需要 " + config.dailyItemCount() + " 个"
                + materialName(item) + "，材料不足，未扣除物品");
        CompoundTag payment = new CompoundTag();
        payment.putString("id", UUID.randomUUID().toString());
        payment.putLong("day", day);
        payment.putFloat("amount", config.dailyRestorePoints());
        int remaining = consume(player.getInventory().items, item, config.dailyItemCount());
        consume(player.getInventory().offhand, item, remaining);
        persisted(player).put(PAYMENT_KEY, payment);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        float before = data.getRespawnPoint();
        settlePayment(player);
        return "维护完成，模板重建余量恢复 " + number(data.getRespawnPoint() - before) + " 点（"
                + number(data.getRespawnPoint()) + "/100）。下一个主世界游戏日可以再次办理。";
    }

    private static int consume(Iterable<ItemStack> stacks, Item item, int required) {
        for (ItemStack stack : stacks) {
            if (required == 0) break;
            if (!stack.is(item)) continue;
            int take = Math.min(required, stack.getCount());
            stack.shrink(take);
            required -= take;
        }
        return required;
    }

    /** 登录只补齐已有付款，不自动收物，也不自动领取当天维护。 */
    public static void recoverPayment(ServerPlayer player) {
        if (player == null || !AuthSessionGuard.isAuthenticated(player)
                || !PlayerAttributesDataManager.areWritesEnabled() || !hasPayment(player)) return;
        try {
            settlePayment(player);
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.error("玩家 {} 的模板维护付款等待重试", player.getScoreboardName(), exception);
        }
    }

    private static void settlePayment(ServerPlayer player) {
        CompoundTag payment = persisted(player).getCompound(PAYMENT_KEY);
        String id = payment.getString("id");
        UUID.fromString(id);
        long day = payment.getLong("day");
        float amount = payment.getFloat("amount");
        DailyTemplateSupportProgress.restoredPoints(0, amount);
        if (day < 0) throw new IllegalStateException("维护付款日期非法");
        var data = PlayerAttributesDataManager.findStoredPlayerAttributesData(player.getUUID());
        if (data == null) throw new IllegalStateException("维护付款等待个人档案恢复");
        var progress = data.getDailyTemplateSupport();
        TemplateSupportSettlement.settle(progress, id, day, data.getRespawnPoint(), amount, () -> {
            // 原版保存可能吞掉 I/O 异常，因此必须核对磁盘上的收据再记账。
            player.server.getPlayerList().saveAll();
            try {
                var path = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getUUID() + ".dat");
                CompoundTag saved = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
                String savedId = saved.getCompound("NeoForgeData").getCompound(Player.PERSISTED_NBT_TAG)
                        .getCompound(PAYMENT_KEY).getString("id");
                if (!id.equals(savedId)) throw new IllegalStateException("维护付款尚未保存");
            } catch (Exception exception) {
                throw new IllegalStateException("物资已登记，付款记录等待保存；请稍后重试，不会重复扣除", exception);
            }
        }, points -> {
            data.setRespawnPoint(points);
            PlayerAttributesDataManager.markDirty();
        }, () -> PlayerAttributesDataManager.saveIfDirty(player.server));
        persisted(player).remove(PAYMENT_KEY);
        // 已记账的旧收据即使在这次保存失败后重现，也会被日期记录去重。
        player.server.getPlayerList().saveAll();
        RespawnPointSyncManager.syncRespawnPointToClient(player);
    }

    private static boolean hasPayment(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(PAYMENT_KEY, 10);
    }

    private static CompoundTag persisted(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, 10)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static long currentDay(ServerPlayer player) { return DailyTemplateSupportProgress.dayAt(player.server.overworld().getDayTime()); }
    private static String materialName(Item item) {
        // 默认中文对白在服务端组装，不能依赖服务端的英文物品翻译。
        return item == Items.GOLDEN_APPLE ? "普通金苹果" : new ItemStack(item).getHoverName().getString();
    }
    private static String number(float value) { return String.format(java.util.Locale.ROOT, "%.1f", value); }
}
