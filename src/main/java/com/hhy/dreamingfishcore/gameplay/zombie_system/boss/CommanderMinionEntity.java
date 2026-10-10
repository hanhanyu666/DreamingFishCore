package com.hhy.dreamingfishcore.gameplay.zombie_system.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** 普通近战护卫：不挖墙、不破门、不增援；有寿命，不掉战利品或原版经验。 */
public final class CommanderMinionEntity extends Zombie {
    private UUID commanderId;
    private long expiresAt;

    public CommanderMinionEntity(EntityType<? extends CommanderMinionEntity> type, Level level) {
        super(type, level);
        this.expiresAt = level.getGameTime() + 1200;
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes().add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0D);
    }

    public void bindTo(ZombieCommanderEntity commander, int lifetimeTicks) {
        this.commanderId = commander.getUUID();
        this.expiresAt = this.level().getGameTime() + lifetimeTicks;
        this.setTarget(commander.getTarget());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel server) {
            if (server.getGameTime() >= this.expiresAt) {
                this.discard();
                return;
            }
            Entity owner = this.commanderId == null ? null : server.getEntity(this.commanderId);
            if (owner instanceof ZombieCommanderEntity commander) {
                if (!commander.isAlive()) {
                    this.discard();
                } else if (this.tickCount % 20 == 0 && commander.getTarget() != null
                        && this.canAttack(commander.getTarget())) {
                    this.setTarget(commander.getTarget());
                }
            }
            // owner 为 null 可能只是 Boss 的区块卸载；不能把卸载误判为死亡。
        }
    }

    @Override protected boolean supportsBreakDoorGoal() { return false; }
    @Override protected boolean isSunSensitive() { return false; }
    @Override protected boolean convertsInWater() { return false; }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override public boolean shouldDropExperience() { return false; }
    @Override public void setBaby(boolean baby) { super.setBaby(false); }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.commanderId != null) tag.putUUID("CommanderId", this.commanderId);
        tag.putLong("CommanderMinionExpiresAt", this.expiresAt);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.commanderId = tag.hasUUID("CommanderId") ? tag.getUUID("CommanderId") : null;
        if (tag.contains("CommanderMinionExpiresAt")) this.expiresAt = tag.getLong("CommanderMinionExpiresAt");
        this.setCanPickUpLoot(false);
    }
}
