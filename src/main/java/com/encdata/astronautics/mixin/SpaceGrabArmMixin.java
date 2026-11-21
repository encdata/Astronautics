package com.encdata.astronautics.mixin;

import com.encdata.astronautics.client.ZeroGGrab;
import com.encdata.astronautics.space.SpaceMovement;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HumanoidModel.class, priority = 1100)
public abstract class SpaceGrabArmMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void astronautics$reachForHold(LivingEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        float partialTick = ageInTicks - entity.tickCount;
        ZeroGGrab.poseHandleArms(entity, model, partialTick);
        if (!(entity instanceof AbstractClientPlayer player) || model.swimAmount <= 0f
                || !player.isVisuallySwimming() || !SpaceMovement.isFloating(player)) return;
        ZeroGGrab.poseArm(player, player.getMainArm() == HumanoidArm.RIGHT ? model.rightArm : model.leftArm,
                partialTick);
    }
}
