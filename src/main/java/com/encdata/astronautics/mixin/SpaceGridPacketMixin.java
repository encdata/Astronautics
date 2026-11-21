package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceGridAddress;
import com.encdata.astronautics.space.SpaceGrids;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PacketUtils.class)
public abstract class SpaceGridPacketMixin {
    @WrapOperation(
            method = "lambda$ensureRunningOnSameThread$0",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"),
            require = 1
    )
    private static void astronautics$handleInPlayerGrid(Packet<?> packet, PacketListener listener,
                                                        Operation<Void> original) {
        if (!(listener instanceof ServerGamePacketListenerImpl game)
                || !SpaceGrids.isMainSpace(game.player.level())) {
            original.call(packet, listener);
            return;
        }
        SpaceGridAddress previous = SpaceGrids.enter(SpaceGrids.of(game.player));
        SpaceGridAddress previousFrame = SpaceGrids.enterFrame(SpaceGrids.frameOf(game.player));
        try {
            original.call(packet, listener);
        } finally {
            SpaceGrids.exitFrame(previousFrame);
            SpaceGrids.exit(previous);
        }
    }
}
