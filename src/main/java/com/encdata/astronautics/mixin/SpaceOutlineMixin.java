package com.encdata.astronautics.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.util.SublevelRenderOffsetHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SublevelRenderOffsetHelper.class, remap = false)
public abstract class SpaceOutlineMixin {
    @Unique private static boolean astronautics$cameraTranslationSkipped;

    @WrapOperation(method = "posePlotToProjected",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", ordinal = 1))
    private static void astronautics$skipCameraTranslation(PoseStack poseStack, double x, double y, double z, Operation<Void> original) {
        astronautics$cameraTranslationSkipped = true;
    }

    @ModifyReturnValue(method = "translation", at = @At("RETURN"))
    private static Vec3 astronautics$translationFromCamera(Vec3 translation) {
        if (!astronautics$cameraTranslationSkipped) return translation;
        astronautics$cameraTranslationSkipped = false;
        return translation.subtract(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
    }
}
