package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceGrids;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TargetingConditions.class)
public abstract class SpaceGridTargetingMixin {
    @ModifyReturnValue(method = "test", at = @At("RETURN"), require = 1)
    private boolean astronautics$sameGridTarget(boolean result,
                                                @Local(argsOnly = true, ordinal = 0) LivingEntity attacker,
                                                @Local(argsOnly = true, ordinal = 1) LivingEntity target) {
        if (!result || !SpaceGrids.isMainSpace(target.level())) return result;
        return SpaceGrids.sees(attacker != null ? SpaceGrids.of(attacker) : SpaceGrids.context(), target);
    }
}
