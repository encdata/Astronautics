package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceCatalog;
import com.encdata.astronautics.space.SpaceConstants;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = EntityRenderer.class, priority = 2100)
public abstract class SpaceEntityLightMixin {

    @ModifyReturnValue(method = "getPackedLightCoords", at = @At("RETURN"))
    private int astronautics$allowStarLight(int packedLight, Entity entity, float partialTick) {
        if (!SpaceConstants.isSpace(entity.level())
                && !SpaceCatalog.usesCustomSkybox(entity.level().dimension())) {
            return packedLight;
        }

        return LightTexture.pack(LightTexture.block(packedLight), 15);
    }
}
