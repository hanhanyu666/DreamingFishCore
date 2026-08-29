package com.hhy.dreamingfishcore.gameplay.zhuiguang_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.playerdata_system.PlayerData;
import com.hhy.dreamingfishcore.server.playerdata_system.PlayerDataManager;
import com.hhy.dreamingfishcore.server.playerdata_system.event.LoginSync;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 逐光会成员身份的服务端唯一写入口。
 *
 * <p>该身份是玩家自愿选择的组织归属，与感染身份、NPC关系、Rank和称号无关。</p>
 */
public final class ZhuiguangMembershipManager {
    private ZhuiguangMembershipManager() {
    }

    public static boolean isMember(UUID playerId) {
        return PlayerDataManager.getPlayerData(playerId).isZhuiguangMember();
    }

    public static boolean isMember(ServerPlayer player) {
        return isMember(player.getUUID());
    }

    /**
     * 设置当前成员身份并同步所有在线客户端。
     *
     * @return 身份是否实际发生变化
     */
    public static boolean setMember(ServerPlayer player, boolean member) {
        if (!PlayerDataManager.hasPlayerData(player)) {
            PlayerDataManager.initPlayerData(player);
        }
        PlayerData data = PlayerDataManager.getPlayerData(player.getUUID());
        if (data.isZhuiguangMember() == member) {
            return false;
        }

        data.setZhuiguangMember(member);
        PlayerDataManager.markDirty();
        LoginSync.broadcastPlayerDataIncludingOwner(player);
        DreamingFishCore.LOGGER.info(
                "玩家 {} 的逐光会成员身份已更新为 {}",
                player.getScoreboardName(),
                member ? "成员" : "独立协作者");
        return true;
    }

    public static String getDisplayName(boolean member) {
        return member ? "逐光会成员" : "独立协作者";
    }
}
