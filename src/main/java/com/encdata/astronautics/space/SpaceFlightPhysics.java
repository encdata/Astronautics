package com.encdata.astronautics.space;

import com.encdata.astronautics.content.misc.ShipAtmosphereSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SpaceFlightPhysics {
    public static final double GRAVITY_FADE_START = 8500.0;
    public static final double SPACE_ALTITUDE = 10000.0;
    private static final double VANILLA_GRAVITY_PER_TICK = 0.08;
    private static final double MINIMUM_OUTWARD_SPEED = 0.05;
    private static final int ROOM_GRACE_TICKS = 10;
    private static final Map<UUID, Integer> ROOM_GRACE = new HashMap<>();

    private SpaceFlightPhysics() {}

    public static void tickPlayer(Player player) {
        if (SpaceConstants.isSpace(player.level())) {
            if (player.level().isClientSide()) return;
            if (inRoomWithGrace(player)) {
                player.setNoGravity(false);
                player.resetFallDistance();
            } else {
                suspendGravity(player);
            }
            return;
        }

        if (!SpaceCatalog.isPlanetDimension(player.level().dimension())) {
            return;
        }
        if (player.getAbilities().flying) {
            return;
        }

        double altitude = logicalAltitude(player);
        if (altitude >= SPACE_ALTITUDE) {
            if (!player.isNoGravity()) {
                player.setDeltaMovement(withOutwardVelocity(player));
            }
            suspendGravity(player);
            return;
        }

        applyPlanetGravity(player, altitude);
    }

    private static boolean inRoomWithGrace(Player player) {
        if (ShipAtmosphereSystem.isInPressurizedRoom(player)) {
            ROOM_GRACE.put(player.getUUID(), ROOM_GRACE_TICKS);
            return true;
        }
        Integer left = ROOM_GRACE.get(player.getUUID());
        if (left == null) return false;
        if (left <= 1) {
            ROOM_GRACE.remove(player.getUUID());
        } else {
            ROOM_GRACE.put(player.getUUID(), left - 1);
        }
        return true;
    }

    public static void tickLivingEntity(LivingEntity entity) {
        if (entity instanceof Player || entity.level().isClientSide()
                || SpaceShipIntegration.containing(entity) != null
                || !SpaceCatalog.isPlanetDimension(entity.level().dimension())) {
            return;
        }

        double altitude = logicalAltitude(entity);
        if (altitude >= SPACE_ALTITUDE) {
            suspendGravity(entity);
            return;
        }

        applyPlanetGravity(entity, altitude);
    }

    public static double logicalAltitude(Entity entity) {
        return Math.max(0.0, entity.getY());
    }

    private static double gravityMultiplier(Level level) {
        PlanetEnvironmentData environment = SpaceCatalog.environmentForDimension(level.dimension());
        return environment.gravity() / PlanetEnvironmentData.EARTH_GRAVITY;
    }

    private static void applyPlanetGravity(Entity entity, double altitude) {
        entity.setNoGravity(false);
        applyGravityCorrection(entity, gravityMultiplier(entity.level()), gravityFade(altitude));
    }

    private static void applyGravityCorrection(Entity entity, double gravityMultiplier, double fade) {
        double effectiveMultiplier = 1.0 + (gravityMultiplier - 1.0) * (1.0 - fade);
        entity.setDeltaMovement(entity.getDeltaMovement().add(
                0.0, VANILLA_GRAVITY_PER_TICK * (1.0 - effectiveMultiplier), 0.0));
    }

    public static double gravityFade(double altitude) {
        return Math.clamp((altitude - GRAVITY_FADE_START)
                / (SPACE_ALTITUDE - GRAVITY_FADE_START), 0.0, 1.0);
    }

    private static void suspendGravity(Entity entity) {
        entity.setNoGravity(true);
        entity.resetFallDistance();
    }

    private static Vec3 withOutwardVelocity(Entity entity) {
        Vec3 position = entity.position();
        Vec3 outward = new Vec3(position.x, position.y, position.z);
        if (outward.lengthSqr() < 1.0e-8) {
            outward = new Vec3(0.0, 1.0, 0.0);
        } else {
            outward = outward.normalize();
        }

        Vec3 velocity = entity.getDeltaMovement();
        double radialSpeed = velocity.dot(outward);
        Vec3 tangentialVelocity = velocity.subtract(outward.scale(radialSpeed));
        return tangentialVelocity.add(outward.scale(Math.max(radialSpeed, MINIMUM_OUTWARD_SPEED)));
    }
}
