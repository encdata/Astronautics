package com.encdata.astronautics.mixin;

import com.encdata.astronautics.client.ZeroGOrientation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class SpaceFloatingTurnMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void astronautics$tumbleFreely(double yRot, double xRot, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player && ZeroGOrientation.turn(player, yRot, xRot)) {
            ci.cancel();
        }
    }
}
