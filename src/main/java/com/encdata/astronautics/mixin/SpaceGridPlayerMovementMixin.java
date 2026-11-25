package com.encdata.astronautics.mixin;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.AstronauticsNetworkSync;
import com.encdata.astronautics.space.SpaceGrids;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SpaceGridPlayerMovementMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void astronautics$followShipCoordinates(ServerboundMovePlayerPacket packet, CallbackInfo callback) {
        if (SpaceGrids.isMainSpace(player.level())) AstronauticsNetworkSync.syncSpaceGrid(player);
    }
}
