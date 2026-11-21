package com.encdata.astronautics.mixin;

import com.encdata.astronautics.registry.AstronauticsItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void astronautics$hideCapeForSuit(PoseStack poses,
            MultiBufferSource buffers, int light, AbstractClientPlayer player,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (player.getItemBySlot(EquipmentSlot.CHEST)
                .is(AstronauticsItems.SPACE_SUIT_CHESTPLATE.get())) ci.cancel();
    }
}
