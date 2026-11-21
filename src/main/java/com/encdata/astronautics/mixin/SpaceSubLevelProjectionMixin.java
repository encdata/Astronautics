package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceConstants;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class SpaceSubLevelProjectionMixin {
    @Unique private static final float ASTRONAUTICS$SPACE_DEPTH_FAR = 16_384.0f;

    @ModifyExpressionValue(method = "getProjectionMatrix",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;getDepthFar()F"))
    private float astronautics$extendSpaceDepthFar(float depthFar) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !SpaceConstants.isSpace(minecraft.level)) {
            return depthFar;
        }
        return Math.max(depthFar, ASTRONAUTICS$SPACE_DEPTH_FAR);
    }
}
