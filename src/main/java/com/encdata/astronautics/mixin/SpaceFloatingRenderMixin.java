package com.encdata.astronautics.mixin;

import com.encdata.astronautics.client.ZeroGGrab;
import com.encdata.astronautics.client.ZeroGOrientation;
import com.encdata.astronautics.space.SpaceMovement;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class SpaceFloatingRenderMixin {
    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void astronautics$tumbleAroundBodyCenter(AbstractClientPlayer player, PoseStack poses, float bob,
            float yBodyRot, float partialTick, float scale, CallbackInfo ci) {
        ZeroGGrab.markWorldPose(player);
        if (!player.isVisuallySwimming() || !SpaceMovement.isFloating(player) || player.isDeadOrDying()) return;

        poses.translate(0f, player.getBbHeight() * 0.5f / scale, 0f);
        poses.mulPose(ZeroGOrientation.bodyRotation(player, partialTick));
        poses.translate(0f, -ZeroGOrientation.BODY_CENTER, 0f);
        ci.cancel();
    }

    @Inject(method = "renderHand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
            ordinal = 0))
    private void astronautics$straightenHandleHand(PoseStack poses, MultiBufferSource buffer, int light,
            AbstractClientPlayer player, ModelPart arm, ModelPart armwear, CallbackInfo ci) {
        if (!ZeroGGrab.holdingSimulatedHandle()) return;
        arm.yRot = arm.zRot = 0f;
        armwear.yRot = armwear.zRot = 0f;
    }
}
