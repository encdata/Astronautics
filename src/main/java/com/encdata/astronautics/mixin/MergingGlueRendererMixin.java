package com.encdata.astronautics.mixin;

import dev.simulated_team.simulated.content.blocks.merging_glue.MergingGlueRenderer;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(value = MergingGlueRenderer.class, remap = false)
public abstract class MergingGlueRendererMixin {
    @Redirect(
            method = "renderSafe",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction;getCounterClockWise()Lnet/minecraft/core/Direction;")
    )
    private Direction astronautics$counterClockWiseOrNorth(Direction direction) {
        return direction.getAxis().isVertical() ? Direction.NORTH : direction.getCounterClockWise();
    }
}
