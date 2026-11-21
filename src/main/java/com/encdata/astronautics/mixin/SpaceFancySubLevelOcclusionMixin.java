package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceConstants;
import dev.ryanhcode.sable.sublevel.render.fancy.FancySubLevelOcclusionData;
import dev.ryanhcode.sable.sublevel.render.fancy.FancySubLevelRenderData;
import foundry.veil.api.client.render.CullFrustum;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = FancySubLevelOcclusionData.class, remap = false)
public abstract class SpaceFancySubLevelOcclusionMixin {
    @Shadow @Final private FancySubLevelRenderData data;

    @Redirect(
            method = "update",
            at = @At(value = "INVOKE", target = "Lfoundry/veil/api/client/render/CullFrustum;testSphere(Lorg/joml/Vector3dc;F)Z")
    )
    private boolean astronautics$spaceSectionsIgnoreVanillaFarPlane(CullFrustum frustum, Vector3dc position, float radius) {
        if (SpaceConstants.isSpace(this.data.getSubLevel().getLevel())) {
            return true;
        }
        return frustum.testSphere(position, radius);
    }
}
