package com.hhy.dreamingfishcore.gameplay.npc_system.entity;

import com.hhy.dreamingfishcore.gameplay.npc_system.NpcAppearanceData;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcData;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcManager;
import com.hhy.dreamingfishcore.gameplay.npc_system.StoryNpcContentPolicy;
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
    /** 由 /npc spawn 写入；锁定后 NPC 保持生成者指定的朝向，不被近距离玩家的注视逻辑改写。 */
    private static final String LOCK_SPAWN_FACING_TAG = "DreamingFishCoreLockSpawnFacing";
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

    /**
     * 设置由生成者决定的初始朝向，并同步 Mob 渲染所使用的身体/头部旋转字段。
     * Entity#moveTo 只更新 yRot/xRot，直接创建 Mob 时若不补齐这些字段，模型会
     * 采用构造阶段的随机或 0 度身体朝向。锁定标记会随实体 NBT 保存。
     */
    public void setSpawnFacing(float yaw, float pitch) {
        setYRot(yaw);
        setXRot(pitch);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        yRotO = yaw;
        xRotO = pitch;
        yHeadRotO = yaw;
        yBodyRotO = yaw;
        getPersistentData().putBoolean(LOCK_SPAWN_FACING_TAG, true);
    }

    @Override
    public void tick() {
        // 旧世界里可能还保存着已下线角色的实体；配置白名单收口后让它们
        // 在第一次服务端 tick 自动消失，避免继续占据交互和渲染入口。
        // 只依据本轮明确的内容白名单判断下线角色，不依赖 NpcManager 当前是否成功
        // 读取配置。这样配置文件暂时损坏时，保留的白芷/周岑实体不会被误删；
        // 已删除角色仍会在第一次服务端 tick 消失。
        if (!level().isClientSide && !StoryNpcContentPolicy.isRetained(getNpcId())) {
            discard();
            return;
        }
        super.tick();
        if (!level().isClientSide
                && !getPersistentData().getBoolean(LOCK_SPAWN_FACING_TAG)
                && tickCount % 10 == 0) {
            Player player = level().getNearestPlayer(this, 8.0D);
            if (player != null) getLookControl().setLookAt(player, 30.0F, 30.0F);
        }
    }

    @Override public boolean hurt(DamageSource source, float amount) { return false; }

    /**
     * LivingEntity.kill() 默认会再次调用 hurt(genericKill)。剧情 NPC 为了防止玩家误伤而
     * 拒绝所有 hurt，因此如果不覆盖这里，原版 /kill 会显示执行成功但实体仍然存在。
     * 管理员的显式清理命令必须直接走实体移除路径，同时保留普通攻击免疫。
     */
    @Override
    public void kill() {
        remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
    }

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
