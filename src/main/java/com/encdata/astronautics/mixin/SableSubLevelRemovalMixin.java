package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceConstants;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ServerSubLevel.class, remap = false)
public abstract class SableSubLevelRemovalMixin {
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/common/ModConfigSpec$DoubleValue;getAsDouble()D",
                    ordinal = 0),
            remap = false)
    private double astronautics$spaceMinimumY(ModConfigSpec.DoubleValue config) {
        ServerSubLevel subLevel = (ServerSubLevel) (Object) this;
        return SpaceConstants.isSpace(subLevel.getLevel())
                ? Double.NEGATIVE_INFINITY : config.getAsDouble();
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/common/ModConfigSpec$DoubleValue;getAsDouble()D",
                    ordinal = 1),
            remap = false)
    private double astronautics$spaceMaximumY(ModConfigSpec.DoubleValue config) {
        ServerSubLevel subLevel = (ServerSubLevel) (Object) this;
        return SpaceConstants.isSpace(subLevel.getLevel())
                ? Double.POSITIVE_INFINITY : config.getAsDouble();
    }
}
