package com.encdata.astronautics.space;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

final class DimensionTransferPassengers {
    private DimensionTransferPassengers() {}

    static List<Snapshot> snapshot(ServerLevel source, Collection<ServerSubLevel> chain,
                                   ServerPlayer trigger, ServerSubLevel anchor) {
        List<Snapshot> passengers = new ArrayList<>();
        boolean triggerIncluded = false;
        SpaceGridAddress shipGrid = SpaceGrids.isMainSpace(source) && anchor != null
                ? SpaceGrids.simulationGrid(source, anchor) : null;
        for (ServerPlayer player : source.players()) {
            if (shipGrid != null && player != trigger && !shipGrid.equals(SpaceGrids.of(player))) continue;
            ServerSubLevel subLevel = SpaceShipIntegration.containing(player) instanceof ServerSubLevel direct
                    && chain.contains(direct)
                    ? direct
                    : DimensionTransferGeometry.nearestInChain(player.position(), chain, 5.0);
            if (subLevel == null) continue;

            passengers.add(snapshot(source, player, subLevel));
            triggerIncluded |= player == trigger;
        }

        if (trigger != null && !triggerIncluded) passengers.add(snapshot(source, trigger, anchor));
        return passengers;
    }

    private static Snapshot snapshot(ServerLevel source, ServerPlayer player, ServerSubLevel subLevel) {
        Vec3 position = player.position();
        Vector3d localPosition = new Vector3d(position.x, position.y, position.z)
                .sub(new Vector3d(subLevel.logicalPose().position()));
        Entity vehicle = player.getVehicle();
        BlockPos seatOffset = DimensionTransferSeats.createSeatPlotOffset(source, player, vehicle, subLevel);
        return new Snapshot(player, subLevel.getUniqueId(), localPosition, vehicle, seatOffset);
    }

    record Snapshot(ServerPlayer player, UUID subLevelId, Vector3d localPosition,
                    Entity vehicle, BlockPos createSeatPlotOffset) {}
}
