package com.encdata.astronautics.mixin;

import com.encdata.astronautics.content.blocks.center_of_mass_core.CenterOfMassCoreBlockEntity;
import dev.ryanhcode.sable.api.physics.mass.MergedMassTracker;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MergedMassTracker.class, remap = false)
public abstract class MergedMassTrackerMixin {
    @Shadow @Final private ServerSubLevel subLevel;
    @Shadow @Nullable private Vector3d centerOfMass;

    @Inject(method = "uploadData", at = @At("HEAD"), remap = false)
    private void astronautics$applyCenterOfMassCore(CallbackInfo ci) {
        if (centerOfMass == null) return;
        var core = CenterOfMassCoreBlockEntity.findActiveCore(subLevel);
        if (core != null) {
            centerOfMass.set(core.getX() + 0.5, core.getY() + 0.5, core.getZ() + 0.5);
        }
    }
}
