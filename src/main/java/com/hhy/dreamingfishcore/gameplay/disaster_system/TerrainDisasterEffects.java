package com.hhy.dreamingfishcore.gameplay.disaster_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule.DayPlan;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule.EventType;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 地形天灾的效果（黑潮 / 孢子瘟疫 / 天降火雨 / 酸雨 / 沙尘暴）。
 *
 * <p>共同前提：**只在对应群系里发作**（见 {@link EventType#biomeTags()}）。
 * 通用的三灾（大尸潮 / 寒霜 / 风暴）仍然全世界生效，在 {@link DisasterEffects} 里。</p>
 *
 * <p>这一版的取舍（都写在这里，免得以后当 bug 查）：</p>
 * <ul>
 *   <li>**不做任何拆方块的腐蚀**：酸雨不掉木头、陨石不挖坑。地形天灾一旦能改方块，
 *       就可能拆掉玩家的家，这比"效果不够狠"严重得多。</li>
 *   <li>**不做浓雾/变色**：需要客户端迷雾事件与新的同步包（协议 +1），留到下一步；
 *       现在先用状态效果与实体把"压迫感"做出来。</li>
 *   <li>护甲磨损用 `setDamageValue` 直接加，而不是 `hurtAndBreak`——后者的签名在 1.21 变动过，
 *       直接用最稳的写法，效果一样（护甲掉耐久）。</li>
 * </ul>
 */
public final class TerrainDisasterEffects {

    /** 黑潮：泡水时多久上一次负面效果。 */
    private static final int BLIGHT_INTERVAL = 100;
    /** 孢子：多久上一次饥饿/掉血。 */
    private static final int SPORE_INTERVAL = 120;
    /** 火雨：多久砸一颗。 */
    private static final int METEOR_INTERVAL = 140;
    /** 酸雨：多久腐蚀一次（掉血 + 磨护甲）。 */
    private static final int ACID_INTERVAL = 100;
    /** 沙尘暴：多久推一次。 */
    private static final int SANDSTORM_INTERVAL = 2;
    /** 每种地形天灾每次最多影响/生成多少，防止人多时堆爆。 */
    private static final int MAX_SPAWNS_PER_PLAYER = 2;

    private TerrainDisasterEffects() {
    }

    /** 每 tick 调用（内部按 tickCount 节流，绝大多数 tick 直接返回）。 */
    public static void tick(MinecraftServer server, DayPlan plan) {
        if (server == null || plan == null || plan.events().isEmpty()) {
            return;
        }
        for (EventType type : plan.events()) {
            if (!type.isBiomeBound() && type != EventType.METEOR_RAIN) {
                continue;   // 通用三灾由 DisasterEffects 处理
            }
            switch (type) {
                case OCEAN_BLIGHT -> tickOceanBlight(server);
                case SPORE_PLAGUE -> tickSporePlague(server);
                case METEOR_RAIN -> {
                    tickMeteorRain(server);     // 排期（节流）
                    tickMeteorImpacts(server);  // 每 tick 推进落地
                }
                case ACID_RAIN -> tickAcidRain(server);
                case SANDSTORM -> tickSandstorm(server);
                default -> {
                }
            }
        }
    }

    /** 该玩家是否处在某个天灾适用的群系里。 */
    static boolean isInEventBiome(ServerPlayer player, EventType type) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!type.isBiomeBound()) {
            return true;
        }
        var biome = level.getBiome(player.blockPosition());
        for (String raw : type.biomeTags()) {
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null) {
                continue;
            }
            if (biome.is(TagKey.create(Registries.BIOME, id))) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 海洋黑潮

    /** 泡在被污染的海水里：中毒 + 虚弱；海里的溺尸会往岸上游。 */
    private static void tickOceanBlight(MinecraftServer server) {
        if (Math.floorMod(server.getTickCount(), BLIGHT_INTERVAL) != 0) {
            return;
        }
        ServerLevel level = server.overworld();
        for (ServerPlayer player : eligible(server)) {
            if (!isInEventBiome(player, EventType.OCEAN_BLIGHT) || !player.isInWater()) {
                continue;
            }
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 0, true, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0, true, true, true));
            player.displayClientMessage(net.minecraft.network.chat.Component
                    .literal("§2[天灾] 黑潮的水在往你皮肤里钻……快上岸。"), true);
        }
        // 溺尸只在有人泡在污染海里时才有意义（否则会白刷在远处）
        for (ServerPlayer player : eligible(server)) {
            if (!isInEventBiome(player, EventType.OCEAN_BLIGHT) || !player.isInWater()) {
                continue;
            }
            for (int index = 0; index < MAX_SPAWNS_PER_PLAYER; index++) {
                spawnNear(level, player, EntityType.DROWNED, 6.0D, 16.0D);
            }
        }
    }

    // ---------------------------------------------------------------- 孢子瘟疫

    /** 森林里的孢子：饥饿 + 偶尔掉血；雾里会刷出无声的快僵尸。 */
    private static void tickSporePlague(MinecraftServer server) {
        if (Math.floorMod(server.getTickCount(), SPORE_INTERVAL) != 0) {
            return;
        }
        ServerLevel level = server.overworld();
        for (ServerPlayer player : eligible(server)) {
            if (!isInEventBiome(player, EventType.SPORE_PLAGUE)) {
                continue;
            }
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 240, 1, true, false, true));
            if (server.getTickCount() % (SPORE_INTERVAL * 2) == 0) {
                player.hurt(level.damageSources().magic(), 1.0F);
            }
            // 无声、略快的僵尸：孢子瘟疫"听不见脚步"的设定
            Mob zombie = EntityType.ZOMBIE.create(level);
            if (zombie != null) {
                var speed = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null) {
                    speed.addPermanentModifier(new AttributeModifier(
                            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "spore_plague_speed"),
                            0.35D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
                zombie.setSilent(true);
                zombie.setPersistenceRequired();
                placeNear(zombie, player, 12.0D, 28.0D);
                level.addFreshEntity(zombie);
            }
        }
    }

    // ---------------------------------------------------------------- 天降火雨

    /** 陨石砸向玩家附近的空地：只留火与冲击，**不破坏方块**。 */
    /** 陨石：落地前的预警时间（tick）。1.5 秒，够人跑开，也够"看着它砸下来"。 */
    private static final int METEOR_WARNING_TICKS = 30;


    /** 已经排期、还没落地的陨石。 */
    private record PendingMeteor(long impactTick, double x, double y, double z) {
    }

    private static final List<PendingMeteor> PENDING_METEORS = new java.util.ArrayList<>();

    /**
     * 排一颗陨石：有 {@link #METEOR_AIM_PERCENT}% 概率**锁定一名玩家**（落点在他脚下附近），
     * 其余随机落在附近空地。落地前 {@link #METEOR_WARNING_TICKS} tick 就开始冒烟迹，
     * 被锁定的人还会收到动作栏警告——**要砸人，但得让人有机会跑**。
     */
    private static void tickMeteorRain(MinecraftServer server) {
        if (Math.floorMod(server.getTickCount(), METEOR_INTERVAL) != 0) {
            return;
        }
        List<ServerPlayer> players = eligible(server);
        if (players.isEmpty()) {
            return;
        }
        ServerLevel level = server.overworld();
        var random = level.getRandom();
        ServerPlayer anchor = players.get(random.nextInt(players.size()));
        // **纯随机落点**：0~40 格的均匀分布——不瞄准谁，但近处也会落，所以偶尔真会砸到人
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = random.nextDouble() * 40.0D;
        double x = anchor.getX() + Math.cos(angle) * distance;
        double z = anchor.getZ() + Math.sin(angle) * distance;
        var surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new net.minecraft.core.BlockPos((int) Math.round(x), anchor.getBlockY(), (int) Math.round(z)));
        PENDING_METEORS.add(new PendingMeteor(
                server.getTickCount() + METEOR_WARNING_TICKS,
                surface.getX() + 0.5D, surface.getY(), surface.getZ() + 0.5D));

        for (ServerPlayer player : players) {
            if (player.distanceToSqr(anchor) < 64.0D * 64.0D) {
                player.displayClientMessage(net.minecraft.network.chat.Component
                        .literal("§6[天灾] 天上又有东西烧起来了。"), true);
                break;
            }
        }
    }

    /** 每 tick 推进：给未落地的陨石冒烟迹；到点了就砸（爆炸伤害照常结算，但不拆方块、不点火）。 */
    private static void tickMeteorImpacts(MinecraftServer server) {
        if (PENDING_METEORS.isEmpty()) {
            return;
        }
        ServerLevel level = server.overworld();
        var iterator = PENDING_METEORS.iterator();
        while (iterator.hasNext()) {
            PendingMeteor meteor = iterator.next();
            long remaining = meteor.impactTick() - server.getTickCount();
            if (remaining > 0) {
                // 预警：一条从高空垂下来的烟迹，越接近落地越短（像东西正在下来）
                if (remaining % 4 == 0) {
                    double height = remaining * 1.6D;
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                            meteor.x(), meteor.y() + height, meteor.z(), 6, 0.25D, 0.25D, 0.25D, 0.02D);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                            meteor.x(), meteor.y() + height, meteor.z(), 4, 0.15D, 0.15D, 0.15D, 0.02D);
                }
                continue;
            }
            iterator.remove();
            detonate(level, meteor);
        }
    }

    /** 陨石落地：爆炸（有伤害、不拆方块）+ 粒子 + 闷响 + 刷一只焦尸。 */
    private static void detonate(ServerLevel level, PendingMeteor meteor) {
        double cx = meteor.x();
        double cy = meteor.y() + 0.5D;
        double cz = meteor.z();
        // ExplosionInteraction.NONE：**不破坏方块**，但伤害与击退照常结算（所以能砸人）
        level.explode(null, cx, cy, cz, 2.6F, Level.ExplosionInteraction.NONE);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                cx, cy, cz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
                cx, meteor.y() + 0.2D, cz, 28, 1.6D, 0.5D, 1.6D, 0.0D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                cx, cy, cz, 46, 0.9D, 1.1D, 0.9D, 0.05D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                cx, cy + 0.4D, cz, 30, 1.2D, 1.0D, 1.2D, 0.02D);
        level.playSound(null, cx, meteor.y(), cz,
                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                net.minecraft.sounds.SoundSource.BLOCKS, 4.0F, 0.85F);
        Mob charred = com.hhy.dreamingfishcore.gameplay.zombie_system.charred.CharredZombieEntities
                .CHARRED_ZOMBIE.get().create(level);
        if (charred != null) {
            charred.moveTo(cx, meteor.y(), cz, 0.0F, 0.0F);
            charred.setPersistenceRequired();
            level.addFreshEntity(charred);
        }
    }

    // ---------------------------------------------------------------- 酸雨

    /** 恶地淋雨：掉血 + 磨护甲耐久（魔法伤害，无视护甲）。**不腐蚀方块。** */
    private static void tickAcidRain(MinecraftServer server) {
        if (Math.floorMod(server.getTickCount(), ACID_INTERVAL) != 0) {
            return;
        }
        ServerLevel level = server.overworld();
        for (ServerPlayer player : eligible(server)) {
            if (!isInEventBiome(player, EventType.ACID_RAIN)) {
                continue;
            }
            if (!level.canSeeSky(player.blockPosition())) {
                continue;   // 躲在石头屋檐下就安全
            }
            player.hurt(level.damageSources().magic(), 1.0F);
            // 20% 概率磨一件护甲（直接加损伤值，避免 1.21 里 hurtAndBreak 的签名问题）
            if (server.getTickCount() % (ACID_INTERVAL * 5) == 0) {
                damageRandomArmor(player);
            }
            player.displayClientMessage(net.minecraft.network.chat.Component
                    .literal("§a[天灾] 酸雨在啃你的装备……"), true);
        }
    }

    private static void damageRandomArmor(ServerPlayer player) {
        var armor = player.getInventory();
        int slot = player.getRandom().nextInt(4);
        ItemStack stack = armor.getItem(36 + slot);
        if (stack.isEmpty() || !stack.isDamageableItem()) {
            return;
        }
        stack.setDamageValue(stack.getDamageValue() + 1);
    }

    // ---------------------------------------------------------------- 沙尘暴

    /** 恶地的强风：把玩家往一个方向推（复用风暴的风向推导），并挂缓慢。 */
    private static void tickSandstorm(MinecraftServer server) {
        if (Math.floorMod(server.getTickCount(), SANDSTORM_INTERVAL) != 0) {
            return;
        }
        DisasterSchedule.Schedule schedule = DisasterService.schedule();
        if (schedule == null) {
            return;
        }
        double[] wind = DisasterTiming.windDirection(schedule.seed() ^ 0x5A5D_5A5DL, DisasterService.today(server));
        ServerLevel level = server.overworld();
        // 沙尘暴比普通风暴更用力（2.2 倍）
        double push = DisasterTiming.WIND_PUSH * 2.2D
                * DisasterTiming.windGustFactor(server.getTickCount());
        for (ServerPlayer player : eligible(server)) {
            if (!isInEventBiome(player, EventType.SANDSTORM)) {
                continue;
            }
            if (player.isCrouching()) {
                continue;   // 与风暴同一套免疫：蹲下就稳
            }
            if (!level.canSeeSky(player.blockPosition())) {
                continue;   // 屋里/洞里不被沙尘暴吹
            }
            // 与风暴同一套：平滑冲量 + 显式速度包（沙尘暴是风暴的 2.2 倍）
            double step = 0.035D * 2.2D * DisasterTiming.windGustFactor(server.getTickCount());
            player.push(wind[0] * step, 0.0D, wind[1] * step);
            player.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    40, 0, true, false, false));
        }
    }

    // ---------------------------------------------------------------- 工具

    /** 参与地形天灾判定的玩家：生存/冒险 + 主世界。 */
    static List<ServerPlayer> eligible(MinecraftServer server) {
        return server.getPlayerList().getPlayers().stream()
                .filter(player -> !player.isCreative() && !player.isSpectator())
                .filter(player -> player.level().dimension() == Level.OVERWORLD)
                .toList();
    }

    /** 在玩家周围 [min, max] 格的地表生成一个实体。 */
    private static void spawnNear(ServerLevel level, ServerPlayer player, EntityType<?> type,
                                  double min, double max) {
        var entity = type.create(level);
        if (entity == null) {
            return;
        }
        placeNear(entity, player, min, max);
        level.addFreshEntity(entity);
    }

    private static void placeNear(net.minecraft.world.entity.Entity entity, ServerPlayer player,
                                  double min, double max) {
        var random = player.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = min + random.nextDouble() * Math.max(0.1D, max - min);
        int x = (int) Math.round(player.getX() + Math.cos(angle) * distance);
        int z = (int) Math.round(player.getZ() + Math.sin(angle) * distance);
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        var surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new net.minecraft.core.BlockPos(x, player.getBlockY(), z));
        entity.moveTo(surface.getX() + 0.5D, surface.getY(), surface.getZ() + 0.5D,
                random.nextFloat() * 360.0F, 0.0F);
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
    }
}
