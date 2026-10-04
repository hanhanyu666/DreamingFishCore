package com.hhy.dreamingfishcore.gameplay.raid_system.loot.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.raid_system.RaidService;
import com.hhy.dreamingfishcore.gameplay.raid_system.loot.network.Packet_RaidLootPickupRequest;
import com.hhy.dreamingfishcore.gameplay.raid_system.loot.network.Packet_RaidLootSync;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 露天物品的客户端交互与 HUD：右键拾取最近的一个，以及"附近物品"列表。
 *
 * <p>客户端只说"我想捡这个锚点"，距离与是否可捡由服务端重算（服务端权威）。
 * 列表则完全来自已同步的 {@link RaidLootClientCache}，不需要额外请求包。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class RaidLootClientInput {

    /** 右键拾取的最远距离（与渲染可见范围一致，略大于服务端的 1.5 格判定，好让玩家"够得着"）。 */
    private static final double INTERACT_RANGE = 3.0D;
    /** 附近物品列表的显示距离。 */
    private static final double LIST_RANGE = 8.0D;
    /** 列表最多显示几条。 */
    private static final int LIST_LIMIT = 5;

    private RaidLootClientInput() {
    }

    /** 右键（使用键）时，若附近有露天物品就请求拾取最近的那个。 */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        // 只有本局参与者才有资格（服务端还会再判一次，这里只是省一次无用包）
        if (RaidService.current().isEmpty()) {
            return;
        }
        var nearest = nearest(INTERACT_RANGE);
        if (nearest == null) {
            return;
        }
        PacketDistributor.sendToServer(new Packet_RaidLootPickupRequest(nearest.entry().anchorId()));
    }

    /** 屏幕上列出手边最近的几件露天物品（名字 + 距离）。 */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) {
            return;
        }
        List<Nearby> nearby = sorted(LIST_RANGE);
        if (nearby.isEmpty()) {
            return;
        }
        var graphics = event.getGuiGraphics();
        var font = minecraft.font;
        int lineHeight = 10;
        int x = graphics.guiWidth() - 150;
        int y = graphics.guiHeight() / 2 - (Math.min(nearby.size(), LIST_LIMIT) * lineHeight) / 2;

        graphics.drawString(font, Component.literal("附近物品（右键拾取）"), x, y - lineHeight,
                0xFFD0D0D0, true);
        int shown = 0;
        for (Nearby item : nearby) {
            if (shown++ >= LIST_LIMIT) {
                break;
            }
            String text = name(item.entry().itemId())
                    + "  " + String.format(Locale.ROOT, "%.1f", item.distance()) + "m";
            graphics.drawString(font, Component.literal(text), x, y + shown * lineHeight,
                    0xFFE0E0E0, true);
        }
    }

    /** 找最近的未拾取节点（含距离），没有就返回 null。 */
    private static Nearby nearest(double range) {
        for (Nearby candidate : sorted(range)) {
            return candidate;
        }
        return null;
    }

    private static List<Nearby> sorted(double range) {
        Minecraft minecraft = Minecraft.getInstance();
        List<Nearby> list = new ArrayList<>();
        if (minecraft.player == null) {
            return list;
        }
        for (Packet_RaidLootSync.Entry entry : RaidLootClientCache.visible()) {
            double distance = Math.sqrt(Math.pow(minecraft.player.getX() - entry.x(), 2.0D)
                    + Math.pow(minecraft.player.getY() - entry.y(), 2.0D)
                    + Math.pow(minecraft.player.getZ() - entry.z(), 2.0D));
            if (distance <= range) {
                list.add(new Nearby(entry, distance));
            }
        }
        list.sort(Comparator.comparingDouble(Nearby::distance));
        return list;
    }

    private static String name(String itemId) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        return item == null || item == Items.AIR ? itemId : new ItemStack(item).getHoverName().getString();
    }

    private record Nearby(Packet_RaidLootSync.Entry entry, double distance) {
    }
}
