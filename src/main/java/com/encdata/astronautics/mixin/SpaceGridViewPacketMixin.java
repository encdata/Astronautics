package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceGrids;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class SpaceGridViewPacketMixin {
    @ModifyVariable(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Packet<?> astronautics$moveIntoViewerGrid(Packet<?> packet) {
        if (!((Object) this instanceof ServerGamePacketListenerImpl game) || game.player == null) return packet;
        return SpaceGrids.viewPacket(packet, game.player);
    }
}
