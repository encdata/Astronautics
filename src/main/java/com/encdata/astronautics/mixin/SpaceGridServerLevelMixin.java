package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceGridAddress;
import com.encdata.astronautics.space.SpaceGridEntityGetter;
import com.encdata.astronautics.space.SpaceGrids;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Predicate;

@Mixin(ServerLevel.class)
public abstract class SpaceGridServerLevelMixin {
    @Shadow public abstract List<ServerPlayer> players();

    @WrapMethod(method = "tickNonPassenger")
    private void astronautics$tickInEntityGrid(Entity entity, Operation<Void> original) {
        if (!astronautics$mainSpace()) {
            original.call(entity);
            return;
        }
        SpaceGrids.wrapAcrossEdge(entity);
        SpaceGridAddress previous = SpaceGrids.enter(SpaceGrids.of(entity));
        SpaceGridAddress previousFrame = SpaceGrids.enterFrame(SpaceGrids.frameOf(entity));
        try {
            original.call(entity);
        } finally {
            SpaceGrids.exitFrame(previousFrame);
            SpaceGrids.exit(previous);
        }
    }

    @WrapMethod(method = "tickPassenger")
    private void astronautics$tickInVehicleGrid(Entity vehicle, Entity passenger, Operation<Void> original) {
        if (!astronautics$mainSpace()) {
            original.call(vehicle, passenger);
            return;
        }
        SpaceGrids.followVehicle(vehicle, passenger);
        SpaceGridAddress previous = SpaceGrids.enter(SpaceGrids.of(passenger));
        SpaceGridAddress previousFrame = SpaceGrids.enterFrame(SpaceGrids.frameOf(passenger));
        try {
            original.call(vehicle, passenger);
        } finally {
            SpaceGrids.exitFrame(previousFrame);
            SpaceGrids.exit(previous);
        }
    }

    @WrapMethod(method = "tickBlock")
    private void astronautics$tickBlockInShipGrid(BlockPos pos, Block block, Operation<Void> original) {
        SpaceGridAddress previous = astronautics$enterShipGrid(pos);
        try {
            original.call(pos, block);
        } finally {
            SpaceGrids.exit(previous);
        }
    }

    @WrapMethod(method = "tickFluid")
    private void astronautics$tickFluidInShipGrid(BlockPos pos, Fluid fluid, Operation<Void> original) {
        SpaceGridAddress previous = astronautics$enterShipGrid(pos);
        try {
            original.call(pos, fluid);
        } finally {
            SpaceGrids.exit(previous);
        }
    }

    @ModifyReturnValue(method = "getEntities()Lnet/minecraft/world/level/entity/LevelEntityGetter;", at = @At("RETURN"))
    private LevelEntityGetter<Entity> astronautics$sameGridEntities(LevelEntityGetter<Entity> entities) {
        return astronautics$mainSpace() ? new SpaceGridEntityGetter(entities) : entities;
    }

    @Inject(
            method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void astronautics$sameGridParticles(ServerPlayer player, boolean longDistance, double x, double y, double z,
                                                Packet<?> packet, CallbackInfoReturnable<Boolean> cir) {
        if (!astronautics$mainSpace()) return;
        ServerLevel level = (ServerLevel) (Object) this;
        if (SpaceGrids.placedInSpace(level, x, z)) {
            Vec3 image = player.level() == level ? SpaceGrids.imageFor(player, x, y, z) : null;
            double range = longDistance ? 512.0 : 32.0;
            boolean near = image != null && Sable.HELPER.distanceSquaredWithSubLevels(level, player.position(),
                    x + image.x, y + image.y, z + image.z) < range * range;
            if (near) player.connection.send(packet);
            cir.setReturnValue(near);
            return;
        }
        SpaceGridAddress source = SpaceGrids.context();
        if (source != null && !source.equals(SpaceGrids.of(player))) cir.setReturnValue(false);
    }

    @WrapOperation(
            method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/Holder;)Lnet/minecraft/world/level/Explosion;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(DDD)D"),
            require = 1
    )
    private double astronautics$sameGridExplosion(ServerPlayer player, double x, double y, double z,
                                                  Operation<Double> original, @Local(argsOnly = true) Entity source) {
        if (astronautics$mainSpace()) {
            if (SpaceGrids.placedInSpace((ServerLevel) (Object) this, x, z)) {
                Vec3 image = SpaceGrids.imageFor(player, x, y, z);
                return image == null ? Double.MAX_VALUE
                        : original.call(player, x + image.x, y + image.y, z + image.z);
            }
            SpaceGridAddress grid = source != null ? SpaceGrids.of(source) : SpaceGrids.context();
            if (grid != null && !grid.equals(SpaceGrids.of(player))) return Double.MAX_VALUE;
        }
        return original.call(player, x, y, z);
    }

    @WrapMethod(method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/Holder;)Lnet/minecraft/world/level/Explosion;")
    private Explosion astronautics$explodeInSourceFrame(Entity source, DamageSource damageSource,
                                                        ExplosionDamageCalculator calculator,
                                                        double x, double y, double z, float radius, boolean fire,
                                                        Level.ExplosionInteraction interaction,
                                                        ParticleOptions small, ParticleOptions large,
                                                        Holder<SoundEvent> sound, Operation<Explosion> original) {
        if (source == null || !astronautics$mainSpace()) {
            return original.call(source, damageSource, calculator, x, y, z, radius, fire, interaction,
                    small, large, sound);
        }
        SpaceGridAddress previous = SpaceGrids.enterFrame(SpaceGrids.frameOf(source));
        try {
            return original.call(source, damageSource, calculator, x, y, z, radius, fire, interaction,
                    small, large, sound);
        } finally {
            SpaceGrids.exitFrame(previous);
        }
    }

    public @Nullable Player getNearestPlayer(double x, double y, double z, double distance,
                                             @Nullable Predicate<Entity> predicate) {
        SpaceGridAddress viewer = astronautics$mainSpace() ? SpaceGrids.context() : null;
        double nearestDistance = -1.0;
        Player nearest = null;
        for (Player player : this.players()) {
            if (predicate != null && !predicate.test(player)) continue;
            if (viewer != null && !viewer.equals(SpaceGrids.of(player))) continue;
            double playerDistance = player.distanceToSqr(x, y, z);
            if ((distance < 0.0 || playerDistance < distance * distance)
                    && (nearestDistance == -1.0 || playerDistance < nearestDistance)) {
                nearestDistance = playerDistance;
                nearest = player;
            }
        }
        return nearest;
    }

    public boolean hasNearbyAlivePlayer(double x, double y, double z, double distance) {
        SpaceGridAddress viewer = astronautics$mainSpace() ? SpaceGrids.context() : null;
        for (Player player : this.players()) {
            if (!EntitySelector.NO_SPECTATORS.test(player) || !EntitySelector.LIVING_ENTITY_STILL_ALIVE.test(player)) {
                continue;
            }
            if (viewer != null && !viewer.equals(SpaceGrids.of(player))) continue;
            double playerDistance = player.distanceToSqr(x, y, z);
            if (distance < 0.0 || playerDistance < distance * distance) return true;
        }
        return false;
    }

    @Unique
    private SpaceGridAddress astronautics$enterShipGrid(BlockPos pos) {
        SpaceGridAddress previous = SpaceGrids.context();
        if (astronautics$mainSpace()) {
            SpaceGridAddress grid = SpaceGrids.plotGrid((ServerLevel) (Object) this, pos);
            if (grid != null) SpaceGrids.enter(grid);
        }
        return previous;
    }

    @Unique
    private boolean astronautics$mainSpace() {
        return SpaceGrids.isMainSpace((ServerLevel) (Object) this);
    }
}
