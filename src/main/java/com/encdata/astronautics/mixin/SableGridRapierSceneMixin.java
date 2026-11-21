package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceGridHostPipeline;
import com.encdata.astronautics.space.SpaceGridPhysicsPipelineProvider;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.Rapier3D", remap = false)
public abstract class SableGridRapierSceneMixin {
    @Redirect(
            method = "getSceneHandle(Lnet/minecraft/server/level/ServerLevel;)J",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem;getPipeline()Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;"),
            remap = false
    )
    private static PhysicsPipeline astronautics$unwrapGridPipeline(SubLevelPhysicsSystem system) {
        PhysicsPipeline constraintPipeline = SpaceGridPhysicsPipelineProvider.constraintPipeline();
        if (constraintPipeline != null) return constraintPipeline;

        PhysicsPipeline pipeline = system.getPipeline();
        return pipeline instanceof SpaceGridHostPipeline host
                ? host.astronautics$rawPipeline()
                : pipeline;
    }
}
