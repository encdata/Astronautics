package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceGrids;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ActiveSableCompanion.class, remap = false)
public abstract class SableGridShipQueryMixin {
    @ModifyReturnValue(
            method = "getAllIntersecting(Lnet/minecraft/world/level/Level;Ldev/ryanhcode/sable/companion/math/BoundingBox3dc;)Ljava/lang/Iterable;",
            at = @At("RETURN"),
            require = 1
    )
    private Iterable<SubLevel> astronautics$sameGridShips(Iterable<SubLevel> ships,
                                                          @Local(argsOnly = true) Level level) {
        return SpaceGrids.isMainSpace(level) ? SpaceGrids.sameGridShips((ServerLevel) level, ships) : ships;
    }
}
