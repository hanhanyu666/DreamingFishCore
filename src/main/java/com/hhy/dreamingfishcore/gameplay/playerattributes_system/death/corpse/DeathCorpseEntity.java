package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.compat.CorpseAccessoryCompat;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.compat.CorpseAccessoryEntry;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

/** 一个可保存进区块 NBT、可交互取回物品的玩家尸体。 */
public final class DeathCorpseEntity extends Entity implements Container {
    public static final int MENU_SIZE = 54;
    private static final int FIXED_SLOT_COUNT = DeathCorpseInventory.MAIN_SIZE
            + DeathCorpseInventory.ARMOR_SIZE + DeathCorpseInventory.OFFHAND_SIZE;
    private static final int EMPTY_DESPAWN_TICKS = 20;
    private static final long CORPSE_LIFETIME_MILLIS = 24L * 60L * 60L * 1000L;

    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(
            DeathCorpseEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> OWNER_NAME = SynchedEntityData.defineId(
            DeathCorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> LOCKED = SynchedEntityData.defineId(
            DeathCorpseEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<ItemStack> MAIN_HAND = SynchedEntityData.defineId(
            DeathCorpseEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> OFF_HAND = SynchedEntityData.defineId(
            DeathCorpseEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> FEET = SynchedEntityData.defineId(
            DeathCorpseEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> LEGS = SynchedEntityData.defineId(
            DeathCorpseEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> CHEST = SynchedEntityData.defineId(
            DeathCorpseEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> HEAD = SynchedEntityData.defineId(
            DeathCorpseEntity.class, EntityDataSerializers.ITEM_STACK);

    private DeathCorpseInventory corpseInventory = new DeathCorpseInventory();
    private boolean resolved;
    private int emptyTicks;
    private long createdAtMillis = System.currentTimeMillis();
    private boolean suppressItemDrops;
    private double recoveryX;
    private double recoveryY;
    private double recoveryZ;
    private boolean dangerRelocated;

    public DeathCorpseEntity(EntityType<? extends DeathCorpseEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    public void initialize(UUID corpseId,
                           UUID ownerId,
                           String ownerName,
                           DeathCorpseInventory inventory,
                           double x,
                           double y,
                           double z,
                           float yRot,
                           boolean resolved,
                           boolean locked,
                           double recoveryX,
                           double recoveryY,
                           double recoveryZ,
                           boolean dangerRelocated) {
        setUUID(corpseId);
        setOwner(ownerId, ownerName);
        corpseInventory = inventory;
        syncRenderedEquipment();
        setPos(x, y, z);
        setYRot(yRot);
        setXRot(0.0F);
        this.resolved = resolved;
        setLocked(locked);
        this.recoveryX = recoveryX;
        this.recoveryY = recoveryY;
        this.recoveryZ = recoveryZ;
        this.dangerRelocated = dangerRelocated;
        this.createdAtMillis = System.currentTimeMillis();
    }

    void setCorpseInventory(DeathCorpseInventory inventory) {
        corpseInventory = inventory;
        syncRenderedEquipment();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(OWNER_NAME, "");
        builder.define(LOCKED, false);
        builder.define(MAIN_HAND, ItemStack.EMPTY);
        builder.define(OFF_HAND, ItemStack.EMPTY);
        builder.define(FEET, ItemStack.EMPTY);
        builder.define(LEGS, ItemStack.EMPTY);
        builder.define(CHEST, ItemStack.EMPTY);
        builder.define(HEAD, ItemStack.EMPTY);
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide && hasExpired()) {
            suppressItemDrops = true;
            discard();
            return;
        }

        if (!isNoGravity()) {
            Vec3 movement = getDeltaMovement();
            double verticalMovement;
            if (isEyeInFluid(FluidTags.WATER) || isEyeInFluid(FluidTags.LAVA)) {
                verticalMovement = Math.min(0.035D, movement.y + 0.01D);
            } else {
                verticalMovement = Math.max(-1.5D, movement.y - 0.0625D);
            }
            setDeltaMovement(movement.x * 0.75D, verticalMovement, movement.z * 0.75D);
            move(MoverType.SELF, getDeltaMovement());

        }

        if (!level().isClientSide && getY() < level().getMinBuildHeight()) {
            rescueFromVoid();
        }

        if (!level().isClientSide && resolved && isEmpty()) {
            emptyTicks++;
            if (emptyTicks >= EMPTY_DESPAWN_TICKS) {
                discard();
            }
        } else if (!isEmpty()) {
            emptyTicks = 0;
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!resolved) {
            player.displayClientMessage(Component.translatable("message.dreamingfishcore.corpse.pending"), true);
            return InteractionResult.CONSUME;
        }

        if (!canPlayerAccess(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.dreamingfishcore.corpse.locked", getOwnerName()), true);
            return InteractionResult.CONSUME;
        }

        if (player.isShiftKeyDown()) {
            boolean transferred = transferAllTo(player);
            player.displayClientMessage(Component.translatable(transferred
                    ? "message.dreamingfishcore.corpse.taken_all"
                    : "message.dreamingfishcore.corpse.inventory_full"), true);
            return InteractionResult.CONSUME;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, playerInventory, ignored) -> ChestMenu.sixRows(containerId, playerInventory, this),
                    getDisplayName()));
            player.displayClientMessage(Component.translatable("message.dreamingfishcore.corpse.quick_take_hint"), true);
            if (corpseInventory.getAdditionalSize() > MENU_SIZE - FIXED_SLOT_COUNT) {
                player.displayClientMessage(Component.translatable("message.dreamingfishcore.corpse.more_items"), true);
            }
        }
        return InteractionResult.CONSUME;
    }

    public boolean transferAllTo(Player player) {
        if (!canPlayerAccess(player)) {
            return false;
        }
        boolean restoreAccessorySlots = getOwnerUuid()
                .map(ownerId -> ownerId.equals(player.getUUID()))
                .orElse(false);
        DeathCorpseInventory.TransferResult result = corpseInventory.transferAllTo(
                player, restoreAccessorySlots);
        setChanged();
        if (result.complete()) {
            discard();
        }
        return result.complete();
    }

    /**
     * 付费复活使用的事务式还原。空间不足时同时回滚玩家栏与尸体，防止先拿走一部分
     * 再改选普通复活而绕过保留物品费用。
     */
    public boolean transferAllToAtomically(Player player) {
        if (!canPlayerAccess(player)) {
            return false;
        }
        DeathCorpseInventory corpseBackup = corpseInventory.copy();
        ListTag playerBackup = player.getInventory().save(new ListTag());

        DeathCorpseInventory.TransferResult result = corpseInventory.transferAllTo(player, true);
        if (!result.complete()) {
            List<CorpseAccessoryEntry> restoredSlots = result.restoredAccessorySlots();
            for (int index = restoredSlots.size() - 1; index >= 0; index--) {
                CorpseAccessoryCompat.rollbackRestore(player, restoredSlots.get(index));
            }
            corpseInventory = corpseBackup;
            player.getInventory().load(playerBackup);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            setChanged();
            return false;
        }

        setChanged();
        discard();
        return true;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
        if (resolved && isEmpty()) {
            emptyTicks = 0;
        }
    }

    public boolean isResolved() {
        return resolved;
    }

    public boolean isLocked() {
        return entityData.get(LOCKED);
    }

    public void setLocked(boolean locked) {
        entityData.set(LOCKED, locked);
    }

    public boolean canPlayerAccess(Player player) {
        if (!isLocked()) {
            return true;
        }
        return getOwnerUuid().map(ownerId -> ownerId.equals(player.getUUID())).orElse(false);
    }

    private boolean hasExpired() {
        return System.currentTimeMillis() - createdAtMillis >= CORPSE_LIFETIME_MILLIS;
    }

    public Optional<UUID> getOwnerUuid() {
        return entityData.get(OWNER_UUID);
    }

    public String getOwnerName() {
        return entityData.get(OWNER_NAME);
    }

    private void setOwner(UUID uuid, String name) {
        entityData.set(OWNER_UUID, Optional.ofNullable(uuid));
        entityData.set(OWNER_NAME, name == null ? "" : name);
    }

    public ItemStack getRenderedEquipment(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> entityData.get(MAIN_HAND);
            case OFFHAND -> entityData.get(OFF_HAND);
            case FEET -> entityData.get(FEET);
            case LEGS -> entityData.get(LEGS);
            case CHEST -> entityData.get(CHEST);
            case HEAD -> entityData.get(HEAD);
            default -> ItemStack.EMPTY;
        };
    }

    private void syncRenderedEquipment() {
        entityData.set(MAIN_HAND, corpseInventory.getEquipment(EquipmentSlot.MAINHAND).copy());
        entityData.set(OFF_HAND, corpseInventory.getEquipment(EquipmentSlot.OFFHAND).copy());
        entityData.set(FEET, corpseInventory.getEquipment(EquipmentSlot.FEET).copy());
        entityData.set(LEGS, corpseInventory.getEquipment(EquipmentSlot.LEGS).copy());
        entityData.set(CHEST, corpseInventory.getEquipment(EquipmentSlot.CHEST).copy());
        entityData.set(HEAD, corpseInventory.getEquipment(EquipmentSlot.HEAD).copy());
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(isLocked()
                ? "entity.dreamingfishcore.death_corpse.locked_named"
                : "entity.dreamingfishcore.death_corpse.named", getOwnerName());
    }

    @Override
    public boolean isPickable() {
        return isAlive();
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    public boolean wasDangerRelocated() {
        return dangerRelocated;
    }

    private void rescueFromVoid() {
        teleportTo(recoveryX, recoveryY, recoveryZ);
        setDeltaMovement(Vec3.ZERO);
        boolean firstDangerRelocation = !dangerRelocated;
        dangerRelocated = true;
        if (firstDangerRelocation) {
            DeathCorpseManager.onCorpseDangerRelocated(this);
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(0.5D);
    }

    @Override
    public int getContainerSize() {
        return MENU_SIZE;
    }

    @Override
    public boolean isEmpty() {
        return corpseInventory.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= MENU_SIZE) {
            return ItemStack.EMPTY;
        }
        if (slot < DeathCorpseInventory.MAIN_SIZE) {
            return corpseInventory.getMain(slot);
        }
        slot -= DeathCorpseInventory.MAIN_SIZE;
        if (slot < DeathCorpseInventory.ARMOR_SIZE) {
            return corpseInventory.getArmor(slot);
        }
        slot -= DeathCorpseInventory.ARMOR_SIZE;
        if (slot < DeathCorpseInventory.OFFHAND_SIZE) {
            return corpseInventory.getOffhand(slot);
        }
        return corpseInventory.getAdditional(slot - DeathCorpseInventory.OFFHAND_SIZE);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack current = getItem(slot);
        if (current.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = current.split(amount);
        if (current.isEmpty()) {
            setItem(slot, ItemStack.EMPTY);
        }
        setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack current = getItem(slot);
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = current.copy();
        setItem(slot, ItemStack.EMPTY);
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= MENU_SIZE) {
            return;
        }
        if (slot < DeathCorpseInventory.MAIN_SIZE) {
            corpseInventory.setMain(slot, stack);
            return;
        }
        slot -= DeathCorpseInventory.MAIN_SIZE;
        if (slot < DeathCorpseInventory.ARMOR_SIZE) {
            corpseInventory.setArmor(slot, stack);
            return;
        }
        slot -= DeathCorpseInventory.ARMOR_SIZE;
        if (slot < DeathCorpseInventory.OFFHAND_SIZE) {
            corpseInventory.setOffhand(slot, stack);
            return;
        }
        corpseInventory.setAdditional(slot - DeathCorpseInventory.OFFHAND_SIZE, stack);
    }

    @Override
    public void setChanged() {
        emptyTicks = 0;
        if (!level().isClientSide && resolved && isAlive() && isEmpty()) {
            discard();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return isAlive() && canPlayerAccess(player) && player.distanceToSqr(this) <= 64.0D;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    /** 锁定尸体时拒绝漏斗等无玩家上下文的自动抽取。 */
    @Override
    public boolean canTakeItem(Container destination, int slot, ItemStack stack) {
        return !isLocked();
    }

    @Override
    public void stopOpen(Player player) {
        corpseInventory.compactAdditionalItems();
        setChanged();
    }

    @Override
    public void clearContent() {
        corpseInventory.clear();
        setChanged();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy() && !suppressItemDrops && !corpseInventory.isEmpty()) {
            for (ItemStack stack : corpseInventory.getAllItems()) {
                Containers.dropItemStack(level(), getX(), getY(), getZ(), stack.copy());
            }
            corpseInventory.clear();
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        UUID ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        setOwner(ownerId, tag.getString("OwnerName"));
        resolved = tag.getBoolean("Resolved");
        setLocked(tag.contains("Locked") && tag.getBoolean("Locked"));
        emptyTicks = tag.getInt("EmptyTicks");
        createdAtMillis = tag.contains("CreatedAtMillis")
                ? tag.getLong("CreatedAtMillis")
                : System.currentTimeMillis();
        recoveryX = tag.contains("RecoveryX") ? tag.getDouble("RecoveryX") : getX();
        recoveryY = tag.contains("RecoveryY") ? tag.getDouble("RecoveryY") : Math.max(
                getY(), level().getMinBuildHeight() + 1.0D);
        recoveryZ = tag.contains("RecoveryZ") ? tag.getDouble("RecoveryZ") : getZ();
        dangerRelocated = tag.getBoolean("DangerRelocated");
        if (tag.contains("Inventory")) {
            corpseInventory = DeathCorpseInventory.load(registryAccess(), tag.getCompound("Inventory"));
        }
        syncRenderedEquipment();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        getOwnerUuid().ifPresent(uuid -> tag.putUUID("Owner", uuid));
        tag.putString("OwnerName", getOwnerName());
        tag.putBoolean("Resolved", resolved);
        tag.putBoolean("Locked", isLocked());
        tag.putInt("EmptyTicks", emptyTicks);
        tag.putLong("CreatedAtMillis", createdAtMillis);
        tag.putDouble("RecoveryX", recoveryX);
        tag.putDouble("RecoveryY", recoveryY);
        tag.putDouble("RecoveryZ", recoveryZ);
        tag.putBoolean("DangerRelocated", dangerRelocated);
        tag.put("Inventory", corpseInventory.save(registryAccess()));
    }
}
