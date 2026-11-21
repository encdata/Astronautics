package com.encdata.astronautics.content.blocks.engine;

import com.encdata.astronautics.client.particles.ExhaustParticleOptions;
import com.encdata.astronautics.registry.AstronauticsDamageSources;
import com.encdata.astronautics.registry.client.AstronauticsParticles;
import com.encdata.astronautics.space.SpaceConstants;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class ExhaustEffects {
    private static final double VIEW_DISTANCE = 256.0;
    private static final int BURN_INTERVAL = 5;
    private static final double BURN_SPREAD = 0.04;

    private ExhaustEffects() {}

    public static void emit(ServerLevel level, BlockPos blockPos, Vec3 exit, Vec3 direction, double power, double size) {
        if (power <= 0.0) return;
        if (direction.lengthSqr() < 1.0e-12) return;
        direction = direction.normalize();
        RandomSource random = level.getRandom();

        var ship = Sable.HELPER.getContaining(level, blockPos);
        if (ship != null) {
            exit = Sable.HELPER.projectOutOfSubLevel(level, exit);
            Quaterniond orientation = ship.logicalPose().orientation();
            Vector3d d = orientation.transform(new Vector3d(direction.x, direction.y, direction.z));
            direction = new Vec3(d.x, d.y, d.z).normalize();
        }
        Vec3 sideA = perpendicular(direction);
        Vec3 sideB = direction.cross(sideA);

        int flames = (int) Math.ceil((3.0 + 2.0 * power) * Math.sqrt(size));
        double flameSpeed = (0.45 + 0.7 * power) * Math.sqrt(size);
        float flameScale = (float) (size * (0.55 + 0.55 * power));
        for (int i = 0; i < flames; i++) {
            Vec3 at = exit.add(disc(random, sideA, sideB, size * 0.14));
            Vec3 velocity = direction.scale(flameSpeed * (0.8 + random.nextDouble() * 0.4))
                    .add(disc(random, sideA, sideB, 0.025 * size * power));
            send(level, AstronauticsParticles.EXHAUST_PLUME.get(), flameScale, at, velocity);
        }

        int clouds = SpaceConstants.isSpace(level) ? 0 : 1 + (int) Math.ceil(power * 1.5);
        double reach = (1.2 + 1.8 * power) * size;
        for (int i = 0; i < clouds; i++) {
            Vec3 at = exit.add(direction.scale(reach * (0.6 + random.nextDouble() * 0.6)))
                    .add(disc(random, sideA, sideB, size * 0.5));
            Vec3 velocity = direction.scale(0.22 + 0.2 * power).add(disc(random, sideA, sideB, 0.08));
            send(level, AstronauticsParticles.EXHAUST_CLOUD.get(), (float) (size * (0.7 + 0.6 * power)), at, velocity);
        }

        double range = (16.0 + 26.0 * power) * Math.sqrt(size);
        BlockHitResult hit = level.clip(new ClipContext(exit, exit.add(direction.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        double hitDistance = hit.getLocation().distanceTo(exit);
        boolean hitOnRay = hit.getType() == HitResult.Type.BLOCK && hitDistance <= range + 1.0e-4;
        double burnLength = hitOnRay ? hitDistance : range;
        burnEntities(level, exit, direction, burnLength, power, size);
        if (hitOnRay) {
            double closeness = 1.0 - hitDistance / range;
            Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
            Vec3 surfaceA = perpendicular(normal);
            Vec3 surfaceB = normal.cross(surfaceA);
            int spread = (int) Math.ceil((1 + 2 * size) * closeness * (0.5 + power));
            for (int i = 0; i < spread; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                Vec3 out = surfaceA.scale(Math.cos(angle)).add(surfaceB.scale(Math.sin(angle)));
                Vec3 at = hit.getLocation().add(normal.scale(0.4 + random.nextDouble() * 0.4))
                        .add(out.scale(random.nextDouble() * size));
                double speed = (0.25 + 0.45 * closeness) * (0.6 + power) * (0.7 + random.nextDouble() * 0.6);
                send(level, AstronauticsParticles.EXHAUST_CLOUD.get(), (float) (size * (0.9 + 0.8 * closeness)),
                        at, out.scale(speed).add(normal.scale(0.04)));
            }

            if (closeness > 0.6 && random.nextDouble() < power) {
                double angle = random.nextDouble() * Math.PI * 2;
                Vec3 out = surfaceA.scale(Math.cos(angle)).add(surfaceB.scale(Math.sin(angle)));
                send(level, AstronauticsParticles.EXHAUST_PLUME.get(), flameScale,
                        hit.getLocation().add(normal.scale(0.3)), out.scale(0.5 * flameSpeed));
            }
        }
    }

    private static void burnEntities(ServerLevel level, Vec3 exit, Vec3 direction, double length, double power,
                                     double size) {
        if (level.getRandom().nextInt(BURN_INTERVAL) != 0 || length <= 0.0) return;
        Vec3 end = exit.add(direction.scale(length));
        double maxRadius = size * 0.5 + length * BURN_SPREAD + 0.5;
        AABB area = new AABB(exit, end).inflate(maxRadius);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
            Vec3 center = entity.getBoundingBox().getCenter();
            Vec3 offset = center.subtract(exit);
            double along = offset.dot(direction);
            if (along < -0.5 || along > length) continue;
            double radius = size * 0.5 + Math.max(0.0, along) * BURN_SPREAD + entity.getBbWidth() * 0.5;
            if (offset.subtract(direction.scale(along)).lengthSqr() > radius * radius) continue;
            double falloff = 1.0 - Math.max(0.0, along) / length;
            float damage = (float) Math.max(1.0, (2.0 + 6.0 * power) * Math.max(0.6, size) * falloff);
            if (entity.hurt(AstronauticsDamageSources.exhaust(level), damage)) {
                entity.igniteForSeconds((float) (2.0 + 3.0 * falloff));
                entity.push(direction.x * 0.25 * power * falloff, direction.y * 0.25 * power * falloff,
                        direction.z * 0.25 * power * falloff);
                entity.hurtMarked = true;
            }
        }
    }

    private static void send(ServerLevel level, net.minecraft.core.particles.ParticleType<ExhaustParticleOptions> type,
                             float scale, Vec3 at, Vec3 velocity) {
        ExhaustParticleOptions options = new ExhaustParticleOptions(type, scale);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(at) > VIEW_DISTANCE * VIEW_DISTANCE) continue;
            level.sendParticles(player, options, true, at.x, at.y, at.z, 0, velocity.x, velocity.y, velocity.z, 1.0);
        }
    }

    private static Vec3 perpendicular(Vec3 v) {
        Vec3 axis = Math.abs(v.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        return v.cross(axis).normalize();
    }

    private static Vec3 disc(RandomSource random, Vec3 a, Vec3 b, double radius) {
        double angle = random.nextDouble() * Math.PI * 2;
        double r = Math.sqrt(random.nextDouble()) * radius;
        return a.scale(Math.cos(angle) * r).add(b.scale(Math.sin(angle) * r));
    }
}
