package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceGridLevelFactory;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DimensionPhysicsData.class, remap = false)
public abstract class SpaceGridDimensionPhysicsMixin {
    @Inject(method = "of", at = @At("HEAD"), cancellable = true)
    private static void astronautics$gridUsesSpacePhysics(Level level, CallbackInfoReturnable<DimensionPhysics> callback) {
        ServerLevel spaceLevel = astronautics$spaceLevelFor(level);
        if (spaceLevel != null) callback.setReturnValue(DimensionPhysicsData.of(spaceLevel));
    }

    @Inject(method = "getDefault", at = @At("HEAD"), cancellable = true)
    private static void astronautics$gridUsesSpaceDefaults(Level level, CallbackInfoReturnable<DimensionPhysics> callback) {
        ServerLevel spaceLevel = astronautics$spaceLevelFor(level);
        if (spaceLevel != null) callback.setReturnValue(DimensionPhysicsData.getDefault(spaceLevel));
    }

    @Unique
    private static ServerLevel astronautics$spaceLevelFor(Level level) {
        if (!(level instanceof ServerLevel) || !SpaceConstants.isGridLevel(level.dimension())) return null;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : SpaceGridLevelFactory.spaceLevel(server);
    }
}
