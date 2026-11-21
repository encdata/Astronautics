package com.encdata.astronautics.mixin;

import com.encdata.astronautics.client.SableSnapshotPending;
import com.encdata.astronautics.space.SpaceConstants;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotDualPacket;
import dev.ryanhcode.sable.network.packets.PacketReceiveMode;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientboundSableSnapshotDualPacket.class, remap = false)
public abstract class SableSnapshotOrderingMixin {
    @Shadow @Final private int interpolationTick;

    @Inject(
            method = "handleClient(Lnet/minecraft/world/level/Level;Ldev/ryanhcode/sable/network/packets/PacketReceiveMode;)V",
            at = @At("HEAD"), cancellable = true, remap = false
    )
    private void astronautics$holdSnapshotsUntilShipExists(Level level, PacketReceiveMode mode,
                                                            CallbackInfo callback) {
        if (!SpaceConstants.isSpace(level)
                || !(SubLevelContainer.getContainer(level) instanceof ClientSubLevelContainer container)) return;

        ClientboundSableSnapshotDualPacket packet = (ClientboundSableSnapshotDualPacket) (Object) this;
        boolean hasUntrackedPlot = packet.entries().stream().anyMatch(entry ->
                !(container.getSubLevel(ChunkPos.getX(entry.plotCoordinate()),
                        ChunkPos.getZ(entry.plotCoordinate())) instanceof ClientSubLevel));
        if (!hasUntrackedPlot) return;

        for (ClientboundSableSnapshotDualPacket.Entry entry : packet.entries()) {
            var subLevel = container.getSubLevel(ChunkPos.getX(entry.plotCoordinate()),
                    ChunkPos.getZ(entry.plotCoordinate()));
            if (subLevel instanceof ClientSubLevel clientSubLevel) {
                container.getInterpolation().receiveSnapshot(clientSubLevel, interpolationTick,
                        entry.pose(), mode);
            } else {
                SableSnapshotPending.store(level, interpolationTick, entry, mode);
            }
        }
        callback.cancel();
    }
}
