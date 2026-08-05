package com.hhy.dreamingfishcore.gameplay.npc_system.entity;

import com.hhy.dreamingfishcore.gameplay.npc_system.NpcAppearanceData;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcData;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 专用剧情 NPC 的服务端实体。它继承 Mob 而不是 Player，所以不会进入玩家列表或 Tab。
 * 本类只保存身份、站立行为和外观的同步状态；真正画出玩家模型是客户端 renderer 的职责。
 */
public class StoryNpcEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> NPC_ID = SynchedEntityData.defineId(StoryNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(StoryNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> MODEL = SynchedEntityData.defineId(StoryNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> SHOW_NAME = SynchedEntityData.defineId(StoryNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> NPC_NAME = SynchedEntityData.defineId(StoryNpcEntity.class, EntityDataSerializers.STRING);

    public StoryNpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes();
    }

    /** SynchedEntityData 是服务端到客户端的轻量状态通道，避免客户端接触 npc_data.json。 */
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(NPC_ID, 0);
        builder.define(SKIN, "");
        builder.define(MODEL, "wide");
        builder.define(SHOW_NAME, true);
        builder.define(NPC_NAME, "");
    }

    public void applyNpcData(NpcData npc) {
        NpcAppearanceData appearance = npc.getAppearance();
        entityData.set(NPC_ID, npc.getNpcId());
        entityData.set(SKIN, appearance.getSkin());
        entityData.set(MODEL, appearance.getModel());
        entityData.set(SHOW_NAME, appearance.isShowName());
        entityData.set(NPC_NAME, npc.getNpcName());
        getPersistentData().putInt(NpcManager.ENTITY_NPC_ID_TAG, npc.getNpcId());
        setCustomName(npc.getNpcName().isEmpty() ? null : Component.literal(npc.getNpcName()));
        setCustomNameVisible(appearance.isShowName());
    }

    public int getNpcId() { return entityData.get(NPC_ID); }
    public String getSkin() { return entityData.get(SKIN); }
    public boolean isSlimModel() { return "slim".equals(entityData.get(MODEL)); }
    public boolean shouldShowNpcName() { return entityData.get(SHOW_NAME); }
    public String getNpcName() { return entityData.get(NPC_NAME); }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 10 == 0) {
            Player player = level().getNearestPlayer(this, 8.0D);
            if (player != null) getLookControl().setLookAt(player, 30.0F, 30.0F);
        }
    }

    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(net.minecraft.world.entity.Entity entity) { }
    @Override public boolean removeWhenFarAway(double distance) { return false; }

    /** NBT 负责区块卸载和服务器重启后的持久化；读取后同步字段会再次发给附近客户端。 */
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("NpcId", getNpcId());
        tag.putString("Skin", getSkin());
        tag.putString("Model", isSlimModel() ? "slim" : "wide");
        tag.putBoolean("ShowName", shouldShowNpcName());
        tag.putString("NpcName", getNpcName());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(NPC_ID, tag.getInt("NpcId"));
        entityData.set(SKIN, tag.getString("Skin"));
        entityData.set(MODEL, "slim".equalsIgnoreCase(tag.getString("Model")) ? "slim" : "wide");
        entityData.set(SHOW_NAME, !tag.contains("ShowName") || tag.getBoolean("ShowName"));
        entityData.set(NPC_NAME, tag.getString("NpcName"));
        getPersistentData().putInt(NpcManager.ENTITY_NPC_ID_TAG, getNpcId());
        setCustomName(getNpcName().isEmpty() ? null : Component.literal(getNpcName()));
        setCustomNameVisible(shouldShowNpcName());
    }
}
