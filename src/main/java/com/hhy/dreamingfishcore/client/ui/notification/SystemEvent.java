package com.hhy.dreamingfishcore.client.ui.notification;

import com.hhy.dreamingfishcore.server.server_ui_system.network.SystemMessageKind;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 右上角系统消息的结构化内容：是谁、发生了什么。有了它，消息卡片可以画头像或进度图标，并按类型配色。
 *
 * @param kind     事件类型
 * @param playerId 当事玩家（画头像用），未知时为 null
 * @param player   当事玩家的名字
 * @param rank     当事玩家的 Rank 名（如 {@code OPERATOR}），没有 Rank 时为空串或 {@code NO_RANK}
 * @param icon     进度的图标物品，其他事件为空
 * @param headline 进度名称等需要突出的内容，没有时为空组件
 */
public record SystemEvent(SystemMessageKind kind, @Nullable UUID playerId, String player, String rank, ItemStack icon,
                          Component headline) {

    public boolean hasRank() {
        return rank != null && !rank.isBlank() && !"NO_RANK".equalsIgnoreCase(rank);
    }
}
