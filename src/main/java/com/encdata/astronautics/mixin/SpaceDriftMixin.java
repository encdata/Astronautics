package com.encdata.astronautics.mixin;

import com.encdata.astronautics.client.ZeroGGrab;
import com.encdata.astronautics.space.SpaceMovement;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class SpaceDriftMixin {
    @Unique
    private boolean astronautics$adrift;
    @Unique
    private boolean astronautics$weightless;
    @Unique
    private Vec3 astronautics$momentum = Vec3.ZERO;

    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3 astronautics$pushOffSurfacesOnly(Vec3 travelVector) {
        LivingEntity self = (LivingEntity) (Object) this;
        astronautics$weightless = SpaceMovement.isWeightless(self);
        boolean holding = astronautics$weightless && self instanceof LocalPlayer player && ZeroGGrab.hold(player);
        astronautics$adrift = holding || astronautics$weightless && !SpaceMovement.touchesSurface(self);
        astronautics$momentum = self.getDeltaMovement();
        return astronautics$adrift ? Vec3.ZERO : travelVector;
    }

    @ModifyExpressionValue(method = "travel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;shouldDiscardFriction()Z"))
    private boolean astronautics$noDragInVacuum(boolean discardFriction) {
        return discardFriction || astronautics$adrift;
    }

    @Inject(method = "travel", at = @At("TAIL"))
    private void astronautics$bounceOffWalls(Vec3 travelVector, CallbackInfo ci) {
        if (!astronautics$weightless) return;
        LivingEntity self = (LivingEntity) (Object) this;
        self.setDeltaMovement(SpaceMovement.bounce(astronautics$momentum, self.getDeltaMovement()));
    }
}
