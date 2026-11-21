package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceGridLevelFactory;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline", remap = false)
public abstract class SableGridRapierPlotMixin {
    @Shadow @Final @Mutable private LevelAccelerator accelerator;

    @Inject(method = "<init>(Lnet/minecraft/server/level/ServerLevel;)V", at = @At("RETURN"), remap = false)
    private void astronautics$useMainSpacePlotChunks(ServerLevel level, CallbackInfo ci) {
        if (!SpaceConstants.isGridLevel(level.dimension())) return;
        ServerLevel mainSpace = SpaceGridLevelFactory.currentPlotLevel();
        if (mainSpace == null) return;
        ((SableLevelAcceleratorAccessor) accelerator).astronautics$setLevel(mainSpace);
    }

    @Redirect(
            method = "handleChunkSectionAddition",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/sublevel/SubLevelContainer;getContainer(Lnet/minecraft/server/level/ServerLevel;)Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;"),
            remap = false
    )
    private ServerSubLevelContainer astronautics$mainSpacePlotContainer(ServerLevel level) {
        return ServerSubLevelContainer.getContainer(astronautics$plotLevel(level));
    }

    @Redirect(
            method = "handleChunkSectionRemoval",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/sublevel/SubLevelContainer;getContainer(Lnet/minecraft/server/level/ServerLevel;)Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;"),
            remap = false
    )
    private ServerSubLevelContainer astronautics$mainSpaceRemovalContainer(ServerLevel level) {
        return ServerSubLevelContainer.getContainer(astronautics$plotLevel(level));
    }

    @Redirect(
            method = "addConstraint",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;getContainer(Lnet/minecraft/server/level/ServerLevel;)Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;"),
            remap = false
    )
    private ServerSubLevelContainer astronautics$mainSpaceConstraintContainer(ServerLevel level) {
        return ServerSubLevelContainer.getContainer(astronautics$plotLevel(level));
    }

    @Redirect(
            method = "handleBlockChange",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"),
            remap = false
    )
    private net.minecraft.world.level.block.state.BlockState astronautics$mainSpaceBlockState(ServerLevel level, BlockPos pos) {
        return astronautics$plotLevel(level).getBlockState(pos);
    }

    @Redirect(
            method = {"add(Ldev/ryanhcode/sable/api/sublevel/KinematicContraption;)V", "updateContraptionPose"},
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/ActiveSableCompanion;getContaining(Lnet/minecraft/world/level/Level;Lorg/joml/Vector3dc;)Ldev/ryanhcode/sable/sublevel/SubLevel;"),
            remap = false
    )
    private SubLevel astronautics$mainSpaceContraptionMount(ActiveSableCompanion helper, Level level, Vector3dc position) {
        return helper.getContaining(level instanceof ServerLevel serverLevel ? astronautics$plotLevel(serverLevel) : level,
                position);
    }

    @Redirect(
            method = "processCollisionEffects",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"),
            require = 1,
            remap = false
    )
    private int astronautics$mainSpaceCollisionParticles(ServerLevel level, ParticleOptions particle,
                                                        double x, double y, double z, int count,
                                                        double dx, double dy, double dz, double speed) {
        return astronautics$plotLevel(level).sendParticles(particle, x, y, z, count, dx, dy, dz, speed);
    }

    @Redirect(
            method = "processCollisionEffects",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;playSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"),
            require = 1,
            remap = false
    )
    private void astronautics$mainSpaceCollisionSound(ServerLevel level, Player player, double x, double y, double z,
                                                      SoundEvent sound, SoundSource source, float volume, float pitch) {
        astronautics$plotLevel(level).playSound(player, x, y, z, sound, source, volume, pitch);
    }

    @Redirect(
            method = "updateContraptionPoses",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem;require(Lnet/minecraft/world/level/Level;)Ldev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem;"),
            require = 1,
            remap = false
    )
    private SubLevelPhysicsSystem astronautics$mainSpacePhysicsSystem(Level level) {
        return SubLevelPhysicsSystem.require(level instanceof ServerLevel serverLevel
                ? astronautics$plotLevel(serverLevel) : level);
    }

    private ServerLevel astronautics$plotLevel(ServerLevel physicsLevel) {
        if (!SpaceConstants.isGridLevel(physicsLevel.dimension())) return physicsLevel;
        ServerLevel mainSpace = SpaceGridLevelFactory.spaceLevel(physicsLevel.getServer());
        return mainSpace == null ? physicsLevel : mainSpace;
    }

}
