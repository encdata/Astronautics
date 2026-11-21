package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceMovement;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LocalPlayer.class)
public abstract class SpaceFloatingCrawlMixin {
    @ModifyExpressionValue(method = "isMovingSlowly", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isVisuallyCrawling()Z"))
    private boolean astronautics$floatingIsNotCrawling(boolean crawling) {
        return crawling && !SpaceMovement.isFloating((LocalPlayer) (Object) this);
    }
}
