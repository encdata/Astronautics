package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceGrids;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SpaceGridInteractMixin {
    @Shadow public ServerPlayer player;

    @WrapOperation(
            method = "handleInteract",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ServerboundInteractPacket;getTarget(Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/entity/Entity;"),
            require = 1
    )
    private Entity astronautics$targetAcrossGridEdge(ServerboundInteractPacket packet, ServerLevel level,
                                                     Operation<Entity> original) {
        if (!SpaceGrids.isMainSpace(level)) return original.call(packet, level);
        Entity target = SpaceGrids.anyGrid(() -> original.call(packet, level));
        return target == null || SpaceGrids.viewOffset(this.player, target) == null ? null : target;
    }

    @WrapOperation(
            method = "handleInteract",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;canInteractWithEntity(Lnet/minecraft/world/phys/AABB;D)Z"),
            require = 1
    )
    private boolean astronautics$reachAcrossGridEdge(ServerPlayer player, AABB box, double distance,
                                                     Operation<Boolean> original, @Local Entity target) {
        Vec3 offset = SpaceGrids.isMainSpace(player.level()) ? SpaceGrids.viewOffset(player, target) : Vec3.ZERO;
        if (offset == null) return false;
        return original.call(player, offset.equals(Vec3.ZERO) ? box : box.move(offset), distance);
    }
}
