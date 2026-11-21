package com.encdata.astronautics.mixin;

import com.encdata.astronautics.client.SableSnapshotPending;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStartTrackingSubLevelPacket;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import foundry.veil.api.network.handler.PacketContext;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientboundStartTrackingSubLevelPacket.class, remap = false)
public abstract class SableStartTrackingMixin {
    @Inject(method = "handle", at = @At("HEAD"))
    private void astronautics$replaceExistingPlot(PacketContext context, CallbackInfo callbackInfo) {
        var level = context.level();
        if (!(SubLevelContainer.getContainer(level) instanceof ClientSubLevelContainer container)) return;
        var packet = (ClientboundStartTrackingSubLevelPacket) (Object) this;
        int plotX = ChunkPos.getX(packet.plotCoordinate());
        int plotZ = ChunkPos.getZ(packet.plotCoordinate());
        if (container.getSubLevel(plotX, plotZ) != null) {
            container.removeSubLevel(plotX, plotZ, SubLevelRemovalReason.REMOVED);
        }
    }

    @Inject(method = "handle", at = @At("RETURN"))
    private void astronautics$applyEarlySnapshots(PacketContext context, CallbackInfo callbackInfo) {
        var level = context.level();
        if (!(SubLevelContainer.getContainer(level) instanceof ClientSubLevelContainer container)) return;
        var packet = (ClientboundStartTrackingSubLevelPacket) (Object) this;
        int plotX = ChunkPos.getX(packet.plotCoordinate());
        int plotZ = ChunkPos.getZ(packet.plotCoordinate());
        if (container.getSubLevel(plotX, plotZ) instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel subLevel) {
            SableSnapshotPending.apply(level, packet.plotCoordinate(), packet.gameTick(), subLevel);
        }
    }
}
