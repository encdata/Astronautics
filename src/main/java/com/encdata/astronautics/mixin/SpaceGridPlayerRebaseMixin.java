package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpacePosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class SpaceGridPlayerRebaseMixin {
    @Unique private Vec3 astronautics$positionBeforeCorrection;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void astronautics$capturePosition(ClientboundPlayerPositionPacket packet, CallbackInfo callback) {
        Player player = Minecraft.getInstance().player;
        this.astronautics$positionBeforeCorrection = player == null ? null : player.position();
    }

    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void astronautics$resetInterpolationAfterGridWrap(ClientboundPlayerPositionPacket packet,
                                                               CallbackInfo callback) {
        Vec3 previous = this.astronautics$positionBeforeCorrection;
        this.astronautics$positionBeforeCorrection = null;
        Player player = Minecraft.getInstance().player;
        if (previous == null || player == null) return;

        Vec3 current = player.position();
        if (!astronautics$crossedGrid(previous.x, current.x)
                && !astronautics$crossedGrid(previous.y, current.y)
                && !astronautics$crossedGrid(previous.z, current.z)) return;

        player.xo = current.x;
        player.yo = current.y;
        player.zo = current.z;
    }

    @Unique
    private static boolean astronautics$crossedGrid(double previous, double current) {
        return Math.abs(previous - current) > SpacePosition.GRID_SIZE * 0.75
                && previous * current < 0.0
                && Math.abs(previous) > SpacePosition.GRID_HALF_SIZE * 0.5
                && Math.abs(current) > SpacePosition.GRID_HALF_SIZE * 0.5;
    }
}
