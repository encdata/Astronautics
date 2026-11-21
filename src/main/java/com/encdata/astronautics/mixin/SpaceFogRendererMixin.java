package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FogRenderer.class, remap = false)
public abstract class SpaceFogRendererMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private static void astronautics$removeSpaceFog(Camera camera, FogRenderer.FogMode mode,
                                                  float viewDistance, boolean thickFog,
                                                  float partialTick, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && SpaceConstants.isSpace(minecraft.level)) {
            RenderSystem.setShaderFogStart(0.0f);
            RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        }
    }
}
