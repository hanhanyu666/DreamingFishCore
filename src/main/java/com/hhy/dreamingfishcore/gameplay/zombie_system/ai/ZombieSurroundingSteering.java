package com.hhy.dreamingfishcore.gameplay.zombie_system.ai;

import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Pure horizontal steering math used after vanilla navigation has selected
 * its next path node. It never creates or replaces a path, so doors, detours
 * and the obstruction fallback gate continue to use the normal zombie route.
 */
public final class ZombieSurroundingSteering {
    static final int SLOT_COUNT = 24;
    private static final double TWO_PI = Math.PI * 2.0D;
    private static final double EPSILON_SQUARED = 1.0E-8D;

    private ZombieSurroundingSteering() {
    }

    /** Returns the UUID-stable angular slot used by one zombie. */
    static int slotIndex(UUID zombieId) {
        return Math.floorMod(zombieId.hashCode(), SLOT_COUNT);
    }

    /** Unit vector shared by visible circling and hidden-target staging. */
    static Vec3 slotDirection(UUID zombieId) {
        double angle = TWO_PI * slotIndex(zombieId) / SLOT_COUNT;
        return new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
    }

    /** Returns the center of a zombie's preferred position around the target. */
    static Vec3 slotPoint(UUID zombieId, Vec3 targetPosition, double radius) {
        return targetPosition.add(slotDirection(zombieId).scale(radius));
    }

    /**
     * Blends the current vanilla path direction with an angular route toward a
     * stable ring slot and a short-lived local crowd-avoidance vector.
     */
    public static Vec3 calculateDirection(
            UUID zombieId,
            Vec3 zombiePosition,
            Vec3 pathWaypoint,
            Vec3 targetPosition,
            Vec3 crowdAvoidance,
            double radius,
            double activationRange,
            double steeringStrength) {
        Vec3 pathDirection = horizontal(pathWaypoint.subtract(zombiePosition));
        double pathLengthSquared = pathDirection.lengthSqr();
        if (pathLengthSquared < EPSILON_SQUARED) {
            return Vec3.ZERO;
        }
        pathDirection = pathDirection.scale(1.0D / Math.sqrt(pathLengthSquared));

        Vec3 fromTarget = horizontal(zombiePosition.subtract(targetPosition));
        double targetDistance = Math.sqrt(fromTarget.lengthSqr());
        Vec3 radialDirection;
        if (targetDistance < 1.0E-4D) {
            Vec3 preferred = slotPoint(zombieId, targetPosition, radius).subtract(targetPosition);
            radialDirection = horizontal(preferred).normalize();
            targetDistance = 0.0D;
        } else {
            radialDirection = fromTarget.scale(1.0D / targetDistance);
        }

        Vec3 preferredSlot = slotPoint(zombieId, targetPosition, radius);
        double currentAngle = Math.atan2(radialDirection.z, radialDirection.x);
        double preferredAngle = Math.atan2(
                preferredSlot.z - targetPosition.z,
                preferredSlot.x - targetPosition.x);
        double angularError = wrapRadians(preferredAngle - currentAngle);

        // Move around the player instead of cutting through their center when
        // the claimed slot is on the opposite side.
        Vec3 tangent = new Vec3(-radialDirection.z, 0.0D, radialDirection.x);
        double tangentAmount = clamp(Math.abs(angularError) / (Math.PI / 2.0D), 0.0D, 1.0D);
        if (angularError < 0.0D) {
            tangent = tangent.scale(-1.0D);
        }

        double radialError = clamp((targetDistance - radius) / Math.max(radius, 0.25D), -1.0D, 1.0D);
        Vec3 slotDirection = tangent.scale(tangentAmount)
                .add(radialDirection.scale(-radialError * 0.8D));
        if (slotDirection.lengthSqr() < EPSILON_SQUARED) {
            slotDirection = pathDirection;
        } else {
            slotDirection = slotDirection.normalize();
        }

        double proximity = clamp(
                (activationRange - targetDistance)
                        / Math.max(activationRange - radius, 0.25D),
                0.0D,
                1.0D);
        double slotWeight = clamp(steeringStrength, 0.0D, 1.0D)
                * (0.2D + proximity * 0.8D);
        Vec3 result = pathDirection.scale(1.0D - slotWeight)
                .add(slotDirection.scale(slotWeight));

        Vec3 horizontalCrowdAvoidance = horizontal(crowdAvoidance);
        if (horizontalCrowdAvoidance.lengthSqr() >= EPSILON_SQUARED) {
            double crowdWeight = 0.18D + proximity * 0.22D;
            result = result.add(horizontalCrowdAvoidance.normalize().scale(crowdWeight));
        }

        return result.lengthSqr() < EPSILON_SQUARED ? pathDirection : result.normalize();
    }

    private static Vec3 horizontal(Vec3 value) {
        return new Vec3(value.x, 0.0D, value.z);
    }

    private static double wrapRadians(double value) {
        double wrapped = value % TWO_PI;
        if (wrapped >= Math.PI) {
            wrapped -= TWO_PI;
        } else if (wrapped < -Math.PI) {
            wrapped += TWO_PI;
        }
        return wrapped;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
