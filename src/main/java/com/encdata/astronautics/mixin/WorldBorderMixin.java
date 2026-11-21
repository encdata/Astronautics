package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceWorldBorderRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldBorder.class)
public abstract class WorldBorderMixin {
    @Inject(method = "isWithinBounds(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void astronautics$allowBlockPos(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (isSpaceBorder()) cir.setReturnValue(true);
    }

    @Inject(method = "isWithinBounds(Lnet/minecraft/world/level/ChunkPos;)Z", at = @At("HEAD"), cancellable = true)
    private void astronautics$allowChunkPos(ChunkPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (isSpaceBorder()) cir.setReturnValue(true);
    }

    @Inject(method = "isWithinBounds(DD)Z", at = @At("HEAD"), cancellable = true)
    private void astronautics$allowPosition(double x, double z, CallbackInfoReturnable<Boolean> cir) {
        if (isSpaceBorder()) cir.setReturnValue(true);
    }

    @Inject(method = "isWithinBounds(DDD)Z", at = @At("HEAD"), cancellable = true)
    private void astronautics$allowPositionWithMargin(double x, double z, double margin,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (isSpaceBorder()) cir.setReturnValue(true);
    }

    @Inject(method = "isWithinBounds(Lnet/minecraft/world/phys/AABB;)Z", at = @At("HEAD"), cancellable = true)
    private void astronautics$allowBox(AABB box, CallbackInfoReturnable<Boolean> cir) {
        if (isSpaceBorder()) cir.setReturnValue(true);
    }

    @Inject(method = "getDistanceToBorder(Lnet/minecraft/world/entity/Entity;)D", at = @At("HEAD"), cancellable = true)
    private void astronautics$unlimitedEntityDistance(Entity entity, CallbackInfoReturnable<Double> cir) {
        if (isSpaceBorder()) cir.setReturnValue(Double.MAX_VALUE);
    }

    @Inject(method = "getDistanceToBorder(DD)D", at = @At("HEAD"), cancellable = true)
    private void astronautics$unlimitedDistance(double x, double z, CallbackInfoReturnable<Double> cir) {
        if (isSpaceBorder()) cir.setReturnValue(Double.MAX_VALUE);
    }

    private boolean isSpaceBorder() {
        return SpaceWorldBorderRegistry.isSpace((WorldBorder) (Object) this);
    }
}
