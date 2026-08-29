package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.client;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.DeathCorpseEntity;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.neoforged.neoforge.common.NeoForgeMod;

/** 复用原版玩家渲染器绘制尸体的轻量客户端玩家。 */
final class DummyCorpsePlayer extends RemotePlayer {
    DummyCorpsePlayer(ClientLevel level, GameProfile profile) {
        super(level, profile);
        AttributeInstance nameTagDistance = getAttributes().getInstance(NeoForgeMod.NAMETAG_DISTANCE);
        if (nameTagDistance != null) {
            nameTagDistance.setBaseValue(0.0D);
        }
        setPos(0.0D, 0.0D, 0.0D);
        xo = 0.0D;
        yo = 0.0D;
        zo = 0.0D;
    }

    void updateEquipment(DeathCorpseEntity corpse) {
        setItemSlot(EquipmentSlot.MAINHAND, corpse.getRenderedEquipment(EquipmentSlot.MAINHAND).copy());
        setItemSlot(EquipmentSlot.OFFHAND, corpse.getRenderedEquipment(EquipmentSlot.OFFHAND).copy());
        setItemSlot(EquipmentSlot.FEET, corpse.getRenderedEquipment(EquipmentSlot.FEET).copy());
        setItemSlot(EquipmentSlot.LEGS, corpse.getRenderedEquipment(EquipmentSlot.LEGS).copy());
        setItemSlot(EquipmentSlot.CHEST, corpse.getRenderedEquipment(EquipmentSlot.CHEST).copy());
        setItemSlot(EquipmentSlot.HEAD, corpse.getRenderedEquipment(EquipmentSlot.HEAD).copy());
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isModelPartShown(PlayerModelPart part) {
        return true;
    }
}
