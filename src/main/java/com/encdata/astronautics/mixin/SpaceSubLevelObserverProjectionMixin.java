package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceProjection;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotDualPacket;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(value = SubLevelTrackingSystem.class, remap = false)
public abstract class SpaceSubLevelObserverProjectionMixin {
    @ModifyArg(
            method = "sendFullSync",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/network/packets/tcp/ClientboundStartTrackingSubLevelPacket;<init>(JLjava/util/UUID;Ldev/ryanhcode/sable/companion/math/Pose3dc;Ldev/ryanhcode/sable/companion/math/Pose3d;Ldev/ryanhcode/sable/companion/math/BoundingBox3ic;Ljava/lang/String;I)V"),
            index = 2
    )
    private Pose3dc astronautics$projectPreviousPose(Pose3dc pose,
                                                      @Local(argsOnly = true) ServerPlayer player,
                                                      @Local(argsOnly = true) ServerSubLevel ship) {
        return SpaceProjection.projectShipPose(player, ship, pose).<Pose3dc>map(value -> value).orElse(pose);
    }

    @ModifyArg(
            method = "sendFullSync",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/network/packets/tcp/ClientboundStartTrackingSubLevelPacket;<init>(JLjava/util/UUID;Ldev/ryanhcode/sable/companion/math/Pose3dc;Ldev/ryanhcode/sable/companion/math/Pose3d;Ldev/ryanhcode/sable/companion/math/BoundingBox3ic;Ljava/lang/String;I)V"),
            index = 3
    )
    private Pose3d astronautics$projectCurrentPose(Pose3d pose,
                                                    @Local(argsOnly = true) ServerPlayer player,
                                                    @Local(argsOnly = true) ServerSubLevel ship) {
        return SpaceProjection.projectShipPose(player, ship, pose).orElse(pose);
    }

    @ModifyArg(
            method = "sendMovementUpdates",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/network/packets/ClientboundSableSnapshotDualPacket;<init>(ILjava/util/List;)V"),
            index = 1
    )
    private List<ClientboundSableSnapshotDualPacket.Entry> astronautics$projectMovementSnapshots(
            List<ClientboundSableSnapshotDualPacket.Entry> entries,
            @Local(name = "player") ServerPlayer player) {
        if (!SpaceConstants.SPACE_LEVEL.equals(player.level().dimension())) return entries;

        SubLevelContainer container = SubLevelContainer.getContainer(player.level());
        if (container == null || entries.isEmpty()) return entries;

        List<ClientboundSableSnapshotDualPacket.Entry> projected = new ArrayList<>(entries.size());
        for (ClientboundSableSnapshotDualPacket.Entry entry : entries) {
            SubLevel candidate = container.getSubLevel(
                    ChunkPos.getX(entry.plotCoordinate()), ChunkPos.getZ(entry.plotCoordinate()));
            if (!(candidate instanceof ServerSubLevel ship)) continue;
            Optional<Pose3d> pose = SpaceProjection.projectShipPose(player, ship, entry.pose());
            if (pose.isEmpty()) continue;
            projected.add(new ClientboundSableSnapshotDualPacket.Entry(
                    entry.plotCoordinate(), pose.get(), entry.linearVelocity(), entry.angularVelocity()));
        }
        return projected;
    }
}
