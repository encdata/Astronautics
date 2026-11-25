package com.encdata.astronautics.space;

import com.encdata.astronautics.space.SpaceGridFrameData.Frame;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.math.BigInteger;

final class SpaceGridFrameCoordinates {
    private static final double ROPE_RANGE = 1.0e12;

    private SpaceGridFrameCoordinates() {}

    static Pose3d toPhysicsPose(Frame frame, Pose3dc logical, SpacePosition position) {
        SpaceVec3 translated = SpacePhysicsFrameGrid.toPhysicsPosition(
                frame.cell(), frame.origin(), position)
                .orElseThrow(() -> new IllegalStateException("ship position lies outside its physics frame"));
        return new Pose3d(toVector(translated), new Quaterniond(logical.orientation()),
                new Vector3d(logical.rotationPoint()), new Vector3d(logical.scale()));
    }

    static Vector3d toVector(SpaceVec3 vector) {
        return new Vector3d(vector.x(), vector.y(), vector.z());
    }

    static SpacePosition toLogicalPosition(Frame frame, Vector3dc physicsPosition) {
        return SpacePhysicsFrameGrid.toLogicalPosition(frame.cell(), frame.origin(),
                new SpaceVec3(physicsPosition.x(), physicsPosition.y(), physicsPosition.z()));
    }

    static Vector3d toRopeScenePosition(Frame frame, SpaceGridAddress grid, Vector3dc logicalPosition) {
        SpacePosition position = SpacePosition.normalize(grid,
                logicalPosition.x(), logicalPosition.y(), logicalPosition.z());
        SpaceVec3 offset = frame.origin().deltaWithin(position, ROPE_RANGE).orElseThrow();
        SpacePhysicsFrameGrid.Cell cell = frame.cell();
        return new Vector3d(SpacePhysicsFrameGrid.centerX(cell) + offset.x(),
                SpacePhysicsFrameGrid.centerY(cell) + offset.y(),
                SpacePhysicsFrameGrid.centerZ(cell) + offset.z());
    }

    static Vector3d fromRopeScenePosition(Frame frame, SpaceGridAddress grid, Vector3dc physicsPosition) {
        SpacePosition position = toLogicalPosition(frame, physicsPosition);
        SpaceVec3 local = new SpacePosition(grid, 0.0, 0.0, 0.0)
                .deltaWithin(position, ROPE_RANGE).orElseThrow();
        return new Vector3d(local.x(), local.y(), local.z());
    }

    static Vector3d toProjectionPosition(Frame frame, SpacePosition position) {
        BigInteger x = position.grid().x().subtract(frame.projectionGrid().x());
        BigInteger y = position.grid().y().subtract(frame.projectionGrid().y());
        BigInteger z = position.grid().z().subtract(frame.projectionGrid().z());
        if (x.bitLength() > 52 || y.bitLength() > 52 || z.bitLength() > 52) {
            throw new IllegalStateException("ship assembly lies outside its local space grid range");
        }
        return new Vector3d(
                x.doubleValue() * SpacePosition.GRID_SIZE + position.x(),
                y.doubleValue() * SpacePosition.GRID_SIZE + position.y(),
                z.doubleValue() * SpacePosition.GRID_SIZE + position.z());
    }
}
