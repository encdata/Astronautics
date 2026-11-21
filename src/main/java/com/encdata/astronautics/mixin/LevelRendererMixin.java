package com.encdata.astronautics.mixin;

import com.encdata.astronautics.client.SpaceSkyClient;
import com.encdata.astronautics.space.SpaceConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void astronautics$suppressSpacePrecipitation(CallbackInfo ci) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (camera.getEntity() != null && SpaceConstants.isSpace(camera.getEntity().level())) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void astronautics$suppressPlanetSky(Matrix4f modelView, Matrix4f projection, float partialTick,
                                             Camera camera, boolean isFoggy, Runnable skyFogSetup,
                                             CallbackInfo ci) {
        if (SpaceSkyClient.shouldSuppressVanillaSky(camera)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void astronautics$suppressSpaceClouds(PoseStack poseStack, Matrix4f modelView, Matrix4f projection,
                                                float partialTick, double cameraX, double cameraY, double cameraZ,
                                                CallbackInfo ci) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (camera.getEntity() != null && SpaceSkyClient.shouldSuppressVanillaSky(camera)) {
            ci.cancel();
        }
    }
}
