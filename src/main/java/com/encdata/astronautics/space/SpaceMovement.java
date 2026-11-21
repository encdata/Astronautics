package com.encdata.astronautics.space;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SpaceMovement {
    private static final double CONTACT_MARGIN = 0.1;
    private static final double MIN_BOUNCE_SPEED = 0.06;
    private static final double RESTITUTION = 0.3;
    private static final double SCRAPE = 0.8;

    private SpaceMovement() {}

    public static boolean isWeightless(LivingEntity entity) {
        return entity.level().isClientSide() && isFloating(entity);
    }

    public static boolean isFloating(Entity entity) {
        return entity instanceof Player player
                && SpaceConstants.isSpace(player.level())
                && player.isNoGravity()
                && !player.getAbilities().flying
                && !player.isPassenger()
                && !player.isSpectator();
    }

    public static Vec3 bounce(Vec3 before, Vec3 after) {
        double x = rebound(before.x, after.x);
        double y = rebound(before.y, after.y);
        double z = rebound(before.z, after.z);
        boolean hitX = x != after.x;
        boolean hitY = y != after.y;
        boolean hitZ = z != after.z;
        if (!hitX && !hitY && !hitZ) return after;
        return new Vec3(hitX ? x : x * SCRAPE, hitY ? y : y * SCRAPE, hitZ ? z : z * SCRAPE);
    }

    private static double rebound(double before, double after) {
        if (Math.abs(before) < MIN_BOUNCE_SPEED || Math.abs(after) > 1.0e-7) return after;
        return -before * RESTITUTION;
    }

    public static boolean touchesSurface(Entity entity) {
        if (entity.onGround() || entity.horizontalCollision || entity.verticalCollision) return true;

        Level level = entity.level();
        AABB reach = entity.getBoundingBox().inflate(CONTACT_MARGIN);
        if (level.getBlockCollisions(entity, reach).iterator().hasNext()) return true;

        BoundingBox3d bounds = new BoundingBox3d(reach);
        BoundingBox3d local = new BoundingBox3d();
        for (SubLevel subLevel : Sable.HELPER.getAllIntersecting(level, bounds)) {
            if (subLevel.isRemoved()) continue;
            bounds.transformInverse(subLevel.logicalPose(), local);
            if (level.getBlockCollisions(null, local.toMojang()).iterator().hasNext()) return true;
        }
        return false;
    }
}
