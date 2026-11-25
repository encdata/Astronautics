package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceFlightPhysics;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LivingEntity.class)
public abstract class PlanetFallDamageMixin {
    @ModifyArg(
            method = "causeFallDamage",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;calculateFallDamage(FF)I"),
            index = 0,
            require = 1
    )
    private float astronautics$scaleFallDistanceByGravity(float distance) {
        return SpaceFlightPhysics.fallDamageDistance((LivingEntity) (Object) this, distance);
    }
}
