package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceGrids;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class SpaceGridKnockbackMixin {
    @ModifyExpressionValue(
            method = "hurt",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/DamageSource;getSourcePosition()Lnet/minecraft/world/phys/Vec3;"),
            require = 1
    )
    private Vec3 astronautics$useSourceImageForKnockback(Vec3 sourcePosition,
                                                         @Local(argsOnly = true) DamageSource damageSource) {
        if (sourcePosition == null || !(((Object) this) instanceof ServerPlayer target)) return sourcePosition;

        Entity source = damageSource.getDirectEntity();
        if (source == null) source = damageSource.getEntity();
        if (source == null) return sourcePosition;

        Vec3 offset = SpaceGrids.viewOffset(target, source);
        return offset == null || offset.equals(Vec3.ZERO) ? sourcePosition : sourcePosition.add(offset);
    }
}
