package com.encdata.astronautics.mixin;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.AstronauticsNetworkSync;
import com.encdata.astronautics.space.SpaceGrids;
import com.encdata.astronautics.space.SpacePosition;
import com.encdata.astronautics.space.SpaceShipIntegration;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SpaceGridPlayerMovementMixin {
    @Shadow public ServerPlayer player;
    @Shadow private Vec3 awaitingPositionFromClient;

    @Inject(
            method = "handleMovePlayer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                    shift = At.Shift.AFTER),
            cancellable = true
    )
    private void astronautics$dropStaleGridPosition(ServerboundMovePlayerPacket packet, CallbackInfo callback) {
        if (!SpaceGrids.isMainSpace(this.player.level()) || !packet.hasPosition()) return;
        if (SpaceShipIntegration.containing(this.player) != null
                || Astronautics.SPACE_RUNTIME.addPlayer(this.player.getUUID()).shipId() != null) return;

        if (!astronautics$crossedGrid(this.player.getX(), packet.getX(this.player.getX()))
                && !astronautics$crossedGrid(this.player.getY(), packet.getY(this.player.getY()))
                && !astronautics$crossedGrid(this.player.getZ(), packet.getZ(this.player.getZ()))) return;

        if (this.awaitingPositionFromClient == null) {
            this.player.connection.teleport(this.player.getX(), this.player.getY(), this.player.getZ(),
                    this.player.getYRot(), this.player.getXRot());
        }
        callback.cancel();
    }

    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void astronautics$followShipCoordinates(ServerboundMovePlayerPacket packet, CallbackInfo callback) {
        if (SpaceGrids.isMainSpace(this.player.level())) AstronauticsNetworkSync.syncSpaceGrid(this.player);
    }

    private static boolean astronautics$crossedGrid(double current, double packet) {
        return Math.abs(current - packet) > SpacePosition.GRID_SIZE * 0.75
                && current * packet < 0.0
                && Math.abs(current) > SpacePosition.GRID_HALF_SIZE * 0.5
                && Math.abs(packet) > SpacePosition.GRID_HALF_SIZE * 0.5;
    }
}
