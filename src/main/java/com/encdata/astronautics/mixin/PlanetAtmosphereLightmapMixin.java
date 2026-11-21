package com.encdata.astronautics.mixin;

import com.encdata.astronautics.client.AtmosphericLightTransmission;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

@Mixin(LightTexture.class)
public abstract class PlanetAtmosphereLightmapMixin {
    @ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getSkyDarken(F)F", ordinal = 0))
    private float astronautics$followPlanetSunCycle(float vanillaBrightness) {
        return AtmosphericLightTransmission.alignSkyBrightness(
                Minecraft.getInstance().getCameraEntity(), vanillaBrightness);
    }

    @ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE",
            target = "Lorg/joml/Vector3f;mul(F)Lorg/joml/Vector3f;", ordinal = 0))
    private Vector3f astronautics$filterSunlightThroughAtmosphere(Vector3f skyLight) {
        return AtmosphericLightTransmission.filterSkyLight(
                Minecraft.getInstance().getCameraEntity(), skyLight);
    }
}
