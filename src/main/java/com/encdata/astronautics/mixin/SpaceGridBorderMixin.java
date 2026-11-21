package com.encdata.astronautics.mixin;

import com.encdata.astronautics.client.SpaceGridBorderRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public abstract class SpaceGridBorderMixin {
    @Shadow private boolean renderChunkborder;

    @Inject(method = "render", at = @At("TAIL"))
    private void astronautics$renderGridBorders(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                                double camX, double camY, double camZ, CallbackInfo ci) {
        if (this.renderChunkborder && !Minecraft.getInstance().showOnlyReducedInfo()) {
            SpaceGridBorderRenderer.render(poseStack, buffers, camX, camY, camZ);
        }
    }
}
