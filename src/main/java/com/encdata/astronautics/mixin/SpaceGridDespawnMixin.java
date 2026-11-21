package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceGrids;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Mob.class)
public abstract class SpaceGridDespawnMixin {
    @WrapOperation(
            method = "checkDespawn",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getNearestPlayer(Lnet/minecraft/world/entity/Entity;D)Lnet/minecraft/world/entity/player/Player;"),
            require = 1
    )
    private Player astronautics$nearestAcrossGrids(Level level, Entity self, double distance,
                                                   Operation<Player> original) {
        if (!SpaceGrids.isMainSpace(level)) return original.call(level, self, distance);
        return SpaceGrids.nearestPlayer((ServerLevel) level, self);
    }

    @WrapOperation(
            method = "checkDespawn",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"),
            require = 1
    )
    private double astronautics$distanceAcrossGrids(Entity player, Entity self, Operation<Double> original) {
        if (!(player instanceof ServerPlayer serverPlayer) || !SpaceGrids.isMainSpace(self.level())) {
            return original.call(player, self);
        }
        return SpaceGrids.distanceSqr(serverPlayer, self);
    }
}
