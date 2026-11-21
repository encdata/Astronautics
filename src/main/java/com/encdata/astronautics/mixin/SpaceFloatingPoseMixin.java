package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceMovement;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class SpaceFloatingPoseMixin {
    @ModifyExpressionValue(method = "updatePlayerPose", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;isSwimming()Z"))
    private boolean astronautics$lieDownWhenFloating(boolean swimming) {
        return swimming || SpaceMovement.isFloating((Player) (Object) this);
    }
}
