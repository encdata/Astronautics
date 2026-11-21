package com.encdata.astronautics.space;

import com.encdata.astronautics.Astronautics;
import dev.egg.dimensionalsable.SubLevelWarper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AstronauticsDimensionTransfer {
    private static final Set<UUID> PENDING_PLAYER_TRANSFERS = new HashSet<>();
    private static boolean shuttingDown;

    private AstronauticsDimensionTransfer() {}

    public static void beginShutdown() {
        shuttingDown = true;
        DimensionTransferTracking.clear();
        PENDING_PLAYER_TRANSFERS.clear();
        Astronautics.LOGGER.info("Stopped Astronautics transfer work before world save");
    }

    public static void resetShutdownState() {
        shuttingDown = false;
    }

    public static boolean isPlayerTransferPending(UUID playerId) {
        return PENDING_PLAYER_TRANSFERS.contains(playerId);
    }

    public static void tick() {
        if (!shuttingDown) DimensionTransferTracking.tick();
    }

    public static boolean transferShip(ServerPlayer trigger, ServerLevel destination,
                                       SpaceVec3 destinationRoot) {
        ServerSubLevel containing = SpaceShipIntegration.findLiveShip(
                trigger, SpaceConstants.SHIP_SEARCH_DISTANCE);
        return containing != null && transferShip(trigger, destination, destinationRoot, containing);
    }

    public static boolean transferShip(ServerPlayer trigger, ServerLevel destination,
                                       SpaceVec3 destinationRoot, ServerSubLevel containing) {
        return transferShip(trigger, destination, destinationRoot, containing, null);
    }

    public static boolean transferShip(ServerPlayer trigger, ServerLevel destination,
                                       SpaceVec3 destinationRoot, ServerSubLevel containing,
                                       SpacePosition logicalDestinationRoot) {
        ServerLevel source = containing.getLevel();
        if (!SpaceGrids.isMainSpace(source)) {
            return transferGroup(trigger, destination, destinationRoot, containing, logicalDestinationRoot);
        }
        return SpaceGrids.within(SpaceGrids.simulationGrid(source, containing),
                () -> transferGroup(trigger, destination, destinationRoot, containing, logicalDestinationRoot));
    }

    private static boolean transferGroup(ServerPlayer trigger, ServerLevel destination,
                                         SpaceVec3 destinationRoot, ServerSubLevel containing,
                                         SpacePosition logicalDestinationRoot) {
        ServerLevel source = containing.getLevel();
        if (shuttingDown || source == destination) return false;

        List<ServerSubLevel> group = SubLevelWarper.getSubLevelGroup(containing);
        if (group.isEmpty()) return false;

        List<DimensionTransferPassengers.Snapshot> passengers =
                DimensionTransferPassengers.snapshot(source, group, trigger, containing);
        Set<UUID> entityIds = new HashSet<>();
        if (logicalDestinationRoot != null && SpaceConstants.SPACE_LEVEL.equals(destination.dimension())) {
            for (ServerSubLevel subLevel : group) {
                source.getEntities(null, subLevel.boundingBox().toMojang().inflate(1.0))
                        .forEach(entity -> entityIds.add(entity.getUUID()));
            }
        }
        Set<UUID> playerIds = new HashSet<>();
        for (DimensionTransferPassengers.Snapshot passenger : passengers) {
            playerIds.add(passenger.player().getUUID());
            PENDING_PLAYER_TRANSFERS.add(passenger.player().getUUID());
        }

        try {
            Vector3d target = new Vector3d(destinationRoot.x(), destinationRoot.y(), destinationRoot.z());
            int transferred;
            if (SpaceConstants.SPACE_LEVEL.equals(destination.dimension())
                    && logicalDestinationRoot != null) {
                Map<UUID, SpacePosition> positions = new HashMap<>();
                Vector3d sourceRoot = new Vector3d(containing.logicalPose().position());
                for (ServerSubLevel subLevel : group) {
                    Vector3d offset = new Vector3d(subLevel.logicalPose().position()).sub(sourceRoot);
                    positions.put(subLevel.getUniqueId(), logicalDestinationRoot.add(
                            new SpaceVec3(offset.x, offset.y, offset.z)));
                }
                for (DimensionTransferPassengers.Snapshot passenger : passengers) {
                    ServerPlayer player = passenger.player();
                    var playerState = Astronautics.SPACE_RUNTIME.addPlayer(player.getUUID());
                    playerState.setSpacePosition(logicalDestinationRoot.add(new SpaceVec3(
                            player.getX() - sourceRoot.x, player.getY() - sourceRoot.y, player.getZ() - sourceRoot.z)));
                }
                transferred = SpaceGridPhysicsPipeline.withTransferPositions(positions, () ->
                        SubLevelWarper.warpConnectedSubLevels(
                                containing, group, destination, target, new Quaterniond()));
            } else {
                transferred = SubLevelWarper.warpConnectedSubLevels(
                        containing, group, destination, target, new Quaterniond());
            }
            if (transferred != group.size()) {
                Astronautics.LOGGER.error("Dimensional Sable transferred {} of {} connected ship sublevels",
                        transferred, group.size());
                return false;
            }

            if (!entityIds.isEmpty()) {
                for (UUID entityId : entityIds) {
                    Entity moved = destination.getEntity(entityId);
                    if (moved == null) {
                        moved = destination.getServer().getPlayerList().getPlayer(entityId);
                    }
                    if (moved == null || moved.level() != destination) continue;

                    SpacePosition position = logicalDestinationRoot.add(new SpaceVec3(
                            moved.getX() - target.x,
                            moved.getY() - target.y,
                            moved.getZ() - target.z));
                    SpaceEntityState.set(moved, position);
                    if (moved instanceof ServerPlayer movedPlayer) {
                        var playerState = Astronautics.SPACE_RUNTIME.addPlayer(movedPlayer.getUUID());
                        playerState.setSpacePosition(position);
                        playerState.saveToPlayer(movedPlayer);
                    }
                }
            }

            ServerSubLevelContainer destinationContainer = SubLevelContainer.getContainer(destination);
            if (destinationContainer == null) return false;

            for (DimensionTransferPassengers.Snapshot passenger : passengers) {
                ServerSubLevel destinationShip = destinationContainer.getSubLevel(passenger.subLevelId())
                        instanceof ServerSubLevel serverSubLevel ? serverSubLevel : null;
                if (destinationShip == null || destinationShip.isRemoved()) continue;

                ServerPlayer movedPlayer = destination.getServer().getPlayerList()
                        .getPlayer(passenger.player().getUUID());
                if (movedPlayer == null) continue;
                if (movedPlayer.level() != destination) {
                    Vector3d passengerTarget = new Vector3d(destinationShip.logicalPose().position())
                            .add(passenger.localPosition());
                    Entity moved = movedPlayer.changeDimension(new DimensionTransition(
                            destination, new Vec3(passengerTarget.x, passengerTarget.y, passengerTarget.z), Vec3.ZERO,
                            movedPlayer.getYRot(), movedPlayer.getXRot(), DimensionTransition.DO_NOTHING));
                    if (!(moved instanceof ServerPlayer newPlayer)) continue;
                    movedPlayer = newPlayer;
                }

                var state = Astronautics.SPACE_RUNTIME.addPlayer(movedPlayer.getUUID());
                state.setShipId(destinationShip.getUniqueId());
                Vector3d local = new Vector3d(movedPlayer.position().x, movedPlayer.position().y,
                        movedPlayer.position().z).sub(new Vector3d(destinationShip.logicalPose().position()));
                state.setLocalPosition(new SpaceVec3(local.x, local.y, local.z));
                movedPlayer.setNoGravity(SpaceConstants.isSpace(destination));

                RigidBodyHandle handle = RigidBodyHandle.of(destinationShip);
                if (handle != null && handle.isValid()) {
                    Vector3d velocity = handle.getLinearVelocity(new Vector3d());
                    movedPlayer.setDeltaMovement(new Vec3(velocity.x, velocity.y, velocity.z));
                }
                state.saveToPlayer(movedPlayer);

                if (DimensionTransferSeats.isCreateSeat(passenger.vehicle())
                        && passenger.createSeatPlotOffset() != null) {
                    DimensionTransferSeats.sitOnCreateSeatBlock(destination, destinationShip,
                            DimensionTransferGeometry.destinationSeatPosition(
                                    destinationShip, passenger.createSeatPlotOffset()),
                            movedPlayer, new HashSet<>());
                }
            }

            DimensionTransferTracking.schedule(destination, playerIds);
            return true;
        } catch (RuntimeException exception) {
            Astronautics.LOGGER.error("Dimensional Sable failed to transfer ship {} to {}",
                    containing.getUniqueId(), destination.dimension().location(), exception);
            return false;
        } finally {
            PENDING_PLAYER_TRANSFERS.removeAll(playerIds);
        }
    }
}
