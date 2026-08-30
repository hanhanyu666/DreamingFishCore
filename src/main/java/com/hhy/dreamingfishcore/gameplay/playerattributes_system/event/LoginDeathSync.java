package com.hhy.dreamingfishcore.gameplay.playerattributes_system.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.courage.PlayerCourageClientSync;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.RespawnPointSyncManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.RevivalInfoManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.DeathCorpseManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.event.DeathEventHandler;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.PlayerInfectionClientSync;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.strength.StrengthSyncManager;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import com.hhy.dreamingfishcore.server.login_system.event.PlayerAuthenticatedEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import static com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager.getPlayerAttributesData;

/** 登录后及重生后的属性、死亡状态同步。 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public class LoginDeathSync {
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !AuthSessionGuard.isAuthenticated(player)) {
            return;
        }

        PlayerAttributesData data = getPlayerAttributesData(player.getUUID());
        if (data != null) {
            data.setCurrentStrength(data.getMaxStrength());
            data.setCurrentCourage(data.getMaxCourage() / 2);
            data.syncMaxHealthToPlayer(player);
            player.setHealth((float) data.getMaxHealth());
            PlayerAttributesDataManager.markDirty();

            StrengthSyncManager.syncStrengthToClient(player);
            PlayerCourageClientSync.sendCourageDataToClient(
                    player, data.getCurrentCourage(), data.getMaxCourage());
            PlayerInfectionClientSync.sendInfectionDataToClient(
                    player, data.getCurrentInfection(), data.isInfected());
        }

        DeathCorpseManager.sendQueuedRespawnLocation(player);
    }

    /** 认证成功后才发送任何个人属性、死亡状态或复活通知。 */
    @SubscribeEvent
    public static void onPlayerAuthenticated(PlayerAuthenticatedEvent event) {
        ServerPlayer player = event.getPlayer();
        if (!AuthSessionGuard.isAuthenticated(player)) {
            return;
        }

        PlayerAttributesData attrData = getPlayerAttributesData(player.getUUID());
        StrengthSyncManager.syncStrengthToClient(player);
        if (attrData == null) {
            return;
        }
        PlayerCourageClientSync.sendCourageDataToClient(
                player, attrData.getCurrentCourage(), attrData.getMaxCourage());
        PlayerInfectionClientSync.sendInfectionDataToClient(
                player, attrData.getCurrentInfection(), attrData.isInfected());
        RespawnPointSyncManager.syncRespawnPointToClient(player);

        if (DeathEventHandler.hasDeathState(player)) {
            DeathEventHandler.restoreDeathState(player);
        }
        DeathCorpseManager.sendQueuedRespawnLocation(player);
        RevivalInfoManager.checkAndSendRevivalTip(player);
    }
}
