package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceMovement;
import dev.simulated_team.simulated.content.blocks.handle.ClientHandleHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientHandleHandler.class, remap = false)
public abstract class SimulatedHandleHoldMixin {
    @Inject(method = "startHold", at = @At("HEAD"), cancellable = true)
    private void astronautics$notWhileFloating(Level level, Player player, BlockPos pos, CallbackInfo ci) {
        if (SpaceMovement.isFloating(player)) ci.cancel();
    }
}
