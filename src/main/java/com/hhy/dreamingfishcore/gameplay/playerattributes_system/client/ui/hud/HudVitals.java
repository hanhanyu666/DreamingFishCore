package com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.cache.PlayerAttributesClientCache;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.courage.PlayerCourageManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionIdentity;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.PlayerInfectionManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.limb_health_system.LimbType;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.strength.client.sync.PlayerStrengthClientSync;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** 单帧体征快照：一帧内所有 HUD 区域读取同一份数值。 */
record HudVitals(
        float health,
        float maxHealth,
        int food,
        int armor,
        float infection,
        int infectionMaximum,
        int infectionLevel,
        InfectionIdentity identity,
        float courage,
        float maxCourage,
        int strength,
        int maxStrength,
        float templatePoints,
        float templateCost,
        int air,
        int maxAir,
        boolean showAir,
        int experienceLevel,
        float experienceProgress,
        boolean[] regionPlated,
        float[] regionDurability,
        int equipmentKey
) {
    static final LimbType[] LIMBS = LimbType.values();
    /** 快照只在当前帧内使用，部位数组逐帧复用，避免在高帧率下持续分配。 */
    private static final boolean[] PLATED = new boolean[LIMBS.length];
    private static final float[] DURABILITY = new float[LIMBS.length];

    static HudVitals capture(Player player) {
        UUID uuid = player.getUUID();
        int maxStrength = PlayerStrengthClientSync.getMaxStrengthClient(player);
        float maxCourage = PlayerCourageManager.getMaxCourageClient(player);
        int infectionMaximum = PlayerInfectionManager.getInfectionMaximumClient(player);

        boolean[] plated = PLATED;
        float[] durability = DURABILITY;
        int equipmentKey = 1;
        for (LimbType limb : LIMBS) {
            ItemStack stack = player.getItemBySlot(limb.getEquipmentSlot());
            int region = limb.ordinal();
            plated[region] = !stack.isEmpty()
                    && (stack.getItem() instanceof ArmorItem || stack.isDamageableItem());
            durability[region] = stack.isDamageableItem() && stack.getMaxDamage() > 0
                    ? (stack.getMaxDamage() - stack.getDamageValue()) / (float) stack.getMaxDamage()
                    : 1.0F;
            equipmentKey = 31 * equipmentKey + (stack.isEmpty() ? 0 : System.identityHashCode(stack.getItem()));
        }

        int air = player.getAirSupply();
        int maxAir = player.getMaxAirSupply();
        return new HudVitals(
                player.getHealth(),
                Math.max(1.0F, player.getMaxHealth()),
                player.getFoodData().getFoodLevel(),
                player.getArmorValue(),
                PlayerInfectionManager.getCurrentInfectionClient(player),
                infectionMaximum <= 0 ? 100 : infectionMaximum,
                PlayerAttributesClientCache.getInfectionLevel(uuid),
                PlayerAttributesClientCache.getInfectionIdentity(uuid),
                PlayerCourageManager.getCurrentCourageClient(player),
                maxCourage <= 0.0F ? 100.0F : maxCourage,
                PlayerStrengthClientSync.getCurrentStrengthClient(player),
                maxStrength <= 0 ? 100 : maxStrength,
                PlayerAttributesClientCache.getRespawnPoint(uuid),
                PlayerAttributesClientCache.getNormalRespawnCost(uuid),
                air,
                maxAir,
                player.isUnderWater() || air < maxAir,
                player.experienceLevel,
                player.experienceProgress,
                plated,
                durability,
                equipmentKey
        );
    }

    float healthRatio() {
        return HudPalette.clamp01(health / maxHealth);
    }

    float infectionRatio() {
        return infectionLevel > 0 ? 1.0F : HudPalette.clamp01(infection / infectionMaximum);
    }

    float courageRatio() {
        return HudPalette.clamp01(courage / maxCourage);
    }

    float strengthRatio() {
        return HudPalette.clamp01(strength / (float) maxStrength);
    }

    boolean infected() {
        return infectionLevel > 0;
    }
}
