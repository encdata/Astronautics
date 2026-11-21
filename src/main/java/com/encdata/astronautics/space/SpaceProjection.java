package com.encdata.astronautics.space;

import com.encdata.astronautics.Astronautics;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3d;

import java.math.BigInteger;
import java.util.Optional;

public final class SpaceProjection {
    private static final double MAX_PROJECTION_DISTANCE = 30_000_000.0;

    private SpaceProjection() {}

    public static Optional<Pose3d> projectShipPose(ServerPlayer observer, ServerSubLevel ship,
                                                   Pose3dc sourcePose) {
        if (!SpaceConstants.isSpace(observer.level()) || ship.getLevel() != observer.level()) {
            return Optional.empty();
        }

        SpacePosition observerPosition = observerPosition(observer);
        SpacePosition shipPosition = shipPosition(ship);
        Vector3d currentPosition = new Vector3d(ship.logicalPose().position());
        Vector3d sourcePosition = new Vector3d(sourcePose.position());
        SpacePosition targetPosition = SpacePosition.normalize(shipPosition.grid(),
                shipPosition.x() + sourcePosition.x - currentPosition.x,
                shipPosition.y() + sourcePosition.y - currentPosition.y,
                shipPosition.z() + sourcePosition.z - currentPosition.z);
        Optional<SpaceVec3> relative = observerPosition.deltaWithin(targetPosition, MAX_PROJECTION_DISTANCE);
        if (relative.isEmpty()) return Optional.empty();

        SpaceVec3 delta = relative.get();
        Pose3d projected = new Pose3d(sourcePose);
        projected.position().set(observer.getX() + delta.x(),
                observer.getY() + delta.y(), observer.getZ() + delta.z());
        return Optional.of(projected);
    }

    public static boolean isWithinRange(ServerPlayer observer, ServerSubLevel ship, double range) {
        SpacePosition observerPosition = observerPosition(observer);
        return observerPosition.deltaWithin(shipPosition(ship), range).isPresent();
    }

    public static SpacePosition observerPosition(ServerPlayer observer) {
        return SpacePosition.normalize(frameGrid(observer), observer.getX(), observer.getY(), observer.getZ());
    }

    public static SpaceGridAddress frameGrid(ServerPlayer player) {
        SpacePlayerState state = Astronautics.SPACE_RUNTIME.addPlayer(player.getUUID());
        if (!state.nbtLoaded()) {
            state.loadFromTag(player.getPersistentData().getCompound(SpacePlayerState.PERSISTENT_DATA_KEY));
        }
        return frameGrid(state.spacePosition(), player.getX(), player.getY(), player.getZ());
    }

    public static SpaceGridAddress frameGrid(SpacePosition known, double x, double y, double z) {
        long shiftX = gridShift(known.x(), x);
        long shiftY = gridShift(known.y(), y);
        long shiftZ = gridShift(known.z(), z);
        if (shiftX == 0L && shiftY == 0L && shiftZ == 0L) return known.grid();
        return known.grid().offset(BigInteger.valueOf(shiftX), BigInteger.valueOf(shiftY), BigInteger.valueOf(shiftZ));
    }

    private static long gridShift(double knownLocal, double local) {
        return Math.round((knownLocal - local) / SpacePosition.GRID_SIZE);
    }

    public static SpacePosition shipPosition(ServerSubLevel ship) {
        if (!(ship.getLevel() instanceof ServerLevel level) || !SpaceConstants.isSpace(level)) {
            return SpacePosition.normalize(SpaceGridAddress.ORIGIN,
                    ship.logicalPose().position().x, ship.logicalPose().position().y,
                    ship.logicalPose().position().z);
        }

        SpaceGridDirectory directory = SpaceGridDirectory.get(level);
        SpaceGridAddress address = directory.shipAddress(ship.getUniqueId());
        if (address == null) address = SpaceGridAddress.ORIGIN;
        ServerLevel stateLevel = SpaceGridLevelFactory.getOrCreate(level.getServer(), address);
        SpaceGridShipState state = SpaceGridShipData.get(stateLevel).get(ship.getUniqueId());
        if (state == null) {
            Vector3d position = new Vector3d(ship.logicalPose().position());
            return SpacePosition.normalize(address, position.x, position.y, position.z);
        }
        return new SpacePosition(address, state.x(), state.y(), state.z());
    }
}
