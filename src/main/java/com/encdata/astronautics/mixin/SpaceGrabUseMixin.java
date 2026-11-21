package com.encdata.astronautics.mixin;

import com.encdata.astronautics.client.ZeroGGrab;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class SpaceGrabUseMixin {
    @WrapOperation(method = "startUseItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult astronautics$grabWhenNothingHappens(MultiPlayerGameMode gameMode, LocalPlayer player,
            InteractionHand hand, BlockHitResult hit, Operation<InteractionResult> original) {
        InteractionResult result = original.call(gameMode, player, hand, hit);
        if (result == InteractionResult.PASS && hand == InteractionHand.MAIN_HAND && ZeroGGrab.tryGrab(player, hit)) {
            return InteractionResult.CONSUME;
        }
        return result;
    }
}
