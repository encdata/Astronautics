package com.encdata.astronautics.space;

import com.encdata.astronautics.Astronautics;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class SpaceGridFrameTransitions {
    private static final double FRAME_REBASE_DISTANCE = SpacePhysicsFrameGrid.SPACING * 0.375;
    private static final double FRAME_MERGE_SPREAD = SpaceConstants.SHIP_GRID_ATTACH_RANGE;
    private static final double FRAME_SPLIT_SPREAD = FRAME_MERGE_SPREAD * 1.25;

    private final SpaceGridPhysicsPipeline owner;
    private final ServerLevel spaceLevel;
    private final SpaceGridFrameRegistry frames;
    private final Map<KinematicContraption, PhysicsPipeline> contraptionOwners;
    private final Set<UUID> teleportedShips;
    private boolean teleporting;

    SpaceGridFrameTransitions(SpaceGridPhysicsPipeline owner, ServerLevel spaceLevel,
                              SpaceGridFrameRegistry frames, Map<KinematicContraption, PhysicsPipeline> contraptionOwners,
                              Set<UUID> teleportedShips) {
        this.owner = owner;
        this.spaceLevel = spaceLevel;
        this.frames = frames;
        this.contraptionOwners = contraptionOwners;
        this.teleportedShips = teleportedShips;
    }

    void setTeleporting(boolean teleporting) {
        this.teleporting = teleporting;
    }

    boolean isTeleporting() {
        return teleporting;
    }

    void maintainFrames() {
        Map<UUID, ServerSubLevel> ships = frames.loadedShips();
        teleportedShips.retainAll(ships.keySet());
        if (ships.isEmpty()) {
            owner.refreshConstrainedFrames(ships);
            return;
        }
        owner.refreshConstrainedFrames(ships);
        splitSpreadFrames(ships);
        mergeNearbyFrames(ships);
    }

    private void splitSpreadFrames(Map<UUID, ServerSubLevel> ships) {
        for (FrameLocation location : frames.activeFrames(ships.keySet())) {
            FrameLocation current = location;
            while (current != null && !current.frame().constrained() && current.frame().ships().size() > 1) {
                Map<UUID, SpacePosition> positions = framePositions(current, ships);
                if (positions == null || spread(positions.values()) <= FRAME_SPLIT_SPREAD) break;

                UUID farthest = null;
                double farthestDistance = -1.0;
                for (Map.Entry<UUID, SpacePosition> entry : positions.entrySet()) {
                    double distance = axisDistance(current.frame().origin(), entry.getValue());
                    if (distance > farthestDistance) {
                        farthest = entry.getKey();
                        farthestDistance = distance;
                    }
                }
                moveToOwnFrame(ships.get(farthest), current, null, null);
                current = SpaceGridFrameData.get(current.level()).frame(current.frame().id())
                        .map(frame -> new FrameLocation(location.level(), frame))
                        .orElse(null);
            }
        }
    }

    void moveToOwnFrame(ServerSubLevel ship, FrameLocation source,
                                Pose3dc targetPose, SpacePosition targetPosition) {
        PhysicsPipeline sourcePipeline = owner.rawPipeline(source.level());
        ShipFramePose currentPose = readShipPose(sourcePipeline, source.frame(), ship);
        DetachedShip detached = new DetachedShip(currentPose, owner.contraptionsFor(ship, sourcePipeline));
        Pose3dc pose = targetPose == null ? detached.pose().pose() : targetPose;
        SpacePosition position = targetPosition == null ? detached.pose().position() : targetPosition;
        SpaceGridAddress projection = targetPosition == null ? source.frame().projectionGrid() : position.grid();
        Optional<FrameLocation> existing = frames.findFrame(position, projection, source.frame().id());
        if (existing.isPresent()) {
            FrameLocation target = existing.get();
            if (source.level() == target.level()) {
                shiftIntoFrame(ship, source, target, pose, position);
                return;
            }
            transferShip(ship, source, target, pose, position, detached, Set.of(ship.getUniqueId()));
            return;
        }

        ServerLevel allocationLevel = frames.frameHost(position.grid());
        if (source.level() == allocationLevel) {
            SpaceGridFrameData data = SpaceGridFrameData.get(allocationLevel);
            SpaceGridFrameData.Frame allocated = data.allocateEmpty(position, projection);
            FrameLocation target = new FrameLocation(allocationLevel, allocated);
            try {
                shiftIntoFrame(ship, source, target, pose, position);
            } catch (RuntimeException | Error failure) {
                data.removeEmptyFrame(allocated.id());
                throw failure;
            }
            return;
        }
        FrameLocation target = new FrameLocation(allocationLevel,
                SpaceGridFrameData.get(allocationLevel).allocate(position, projection, ship.getUniqueId()));
        transferShip(ship, source, target, pose, position, detached, Set.of(ship.getUniqueId()));
    }

    private void mergeNearbyFrames(Map<UUID, ServerSubLevel> ships) {
        boolean merged;
        do {
            merged = false;
            List<FrameLocation> frames = this.frames.activeFrames(ships.keySet());
            outer:
            for (int i = 0; i < frames.size(); i++) {
                for (int j = i + 1; j < frames.size(); j++) {
                    FrameLocation first = frames.get(i);
                    FrameLocation second = frames.get(j);
                    if (first.frame().id().equals(second.frame().id())) continue;
                    if (!closeEnoughToMerge(first, second, ships)) continue;
                    if (mergeFrames(first, second, ships)) {
                        merged = true;
                        break outer;
                    }
                }
            }
        } while (merged);
    }

    private boolean closeEnoughToMerge(FrameLocation first, FrameLocation second, Map<UUID, ServerSubLevel> ships) {
        if (axisDistance(first.frame().origin(), second.frame().origin())
                > FRAME_MERGE_SPREAD + SpacePhysicsFrameGrid.SPACING) return false;
        Map<UUID, SpacePosition> firstPositions = framePositions(first, ships);
        Map<UUID, SpacePosition> secondPositions = framePositions(second, ships);
        if (firstPositions == null || secondPositions == null) return false;
        List<SpacePosition> positions = new ArrayList<>(firstPositions.values());
        positions.addAll(secondPositions.values());
        return spread(positions) <= FRAME_MERGE_SPREAD;
    }

    private Map<UUID, SpacePosition> framePositions(FrameLocation location, Map<UUID, ServerSubLevel> ships) {
        PhysicsPipeline host = owner.rawPipeline(location.level());
        Map<UUID, SpacePosition> positions = new HashMap<>();
        for (UUID shipId : location.frame().ships()) {
            ServerSubLevel ship = ships.get(shipId);
            if (ship == null || ship.isRemoved()) return null;
            Vector3d physical = host.readPose(ship, new Pose3d()).position();
            positions.put(shipId, SpaceGridFrameCoordinates.toLogicalPosition(location.frame(), physical));
        }
        return positions;
    }

    private static double spread(Iterable<SpacePosition> positions) {
        SpacePosition base = null;
        double minX = 0.0, minY = 0.0, minZ = 0.0, maxX = 0.0, maxY = 0.0, maxZ = 0.0;
        for (SpacePosition position : positions) {
            if (base == null) {
                base = position;
                continue;
            }
            Optional<SpaceVec3> delta = base.deltaWithin(position, SpacePhysicsFrameGrid.SPACING * 4.0);
            if (delta.isEmpty()) return Double.POSITIVE_INFINITY;
            SpaceVec3 value = delta.get();
            minX = Math.min(minX, value.x());
            minY = Math.min(minY, value.y());
            minZ = Math.min(minZ, value.z());
            maxX = Math.max(maxX, value.x());
            maxY = Math.max(maxY, value.y());
            maxZ = Math.max(maxZ, value.z());
        }
        return Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
    }

    private static double axisDistance(SpacePosition origin, SpacePosition position) {
        Optional<SpaceVec3> delta = origin.deltaWithin(position, SpacePhysicsFrameGrid.SPACING * 4.0);
        if (delta.isEmpty()) return Double.POSITIVE_INFINITY;
        SpaceVec3 value = delta.get();
        return Math.max(Math.abs(value.x()), Math.max(Math.abs(value.y()), Math.abs(value.z())));
    }

    boolean mergeFrames(ServerSubLevel firstShip, ServerSubLevel secondShip) {
        if (firstShip.isRemoved() || secondShip.isRemoved()) return false;
        SpaceGridDirectory directory = SpaceGridDirectory.get(spaceLevel);
        SpaceGridAddress firstOwner = directory.frameOwnerAddress(firstShip.getUniqueId());
        SpaceGridAddress secondOwner = directory.frameOwnerAddress(secondShip.getUniqueId());
        if (firstOwner == null || secondOwner == null) return false;
        FrameLocation first = new FrameLocation(
                SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), firstOwner),
                frame(firstShip.getUniqueId(), firstOwner));
        FrameLocation second = new FrameLocation(
                SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), secondOwner),
                frame(secondShip.getUniqueId(), secondOwner));
        if (first.frame().id().equals(second.frame().id())) return true;
        Map<UUID, ServerSubLevel> ships = new HashMap<>();
        ships.put(firstShip.getUniqueId(), firstShip);
        ships.put(secondShip.getUniqueId(), secondShip);
        SubLevelContainer container = SubLevelContainer.getContainer(spaceLevel);
        if (container != null) {
            for (Object object : container.getAllSubLevels()) {
                if (object instanceof ServerSubLevel ship) ships.put(ship.getUniqueId(), ship);
            }
        }
        return mergeFrames(first, second, ships);
    }

    private boolean mergeFrames(FrameLocation first, FrameLocation second,
                                Map<UUID, ServerSubLevel> ships) {
        FrameLocation target = first;
        FrameLocation source = second;
        boolean sameScene = first.level() == second.level();
        boolean keepConstraints = !sameScene && (first.frame().constrained() || second.frame().constrained());
        if (!sameScene && first.frame().constrained() && second.frame().constrained()) return false;
        if (second.frame().constrained() && !first.frame().constrained()) {
            target = second;
            source = first;
        }
        Optional<SpacePosition> origin = mergedOrigin(target, source, ships);
        if (origin.isEmpty()) {
            if (keepConstraints) return false;
            FrameLocation swap = target;
            target = source;
            source = swap;
            origin = mergedOrigin(target, source, ships);
        }
        if (origin.isEmpty()) return false;

        if (!target.frame().origin().equals(origin.get())) {
            target = rebaseFrame(target, origin.get(), ships);
        }

        SpaceGridFrameData targetData = SpaceGridFrameData.get(target.level());
        Set<UUID> movingShips = new HashSet<>();
        for (UUID shipId : source.frame().ships()) {
            if (ships.containsKey(shipId)) movingShips.add(shipId);
        }
        for (UUID shipId : List.copyOf(source.frame().ships())) {
            ServerSubLevel ship = ships.get(shipId);
            if (ship == null) continue;
            if (sameScene) {
                shiftIntoFrame(ship, source, target);
                continue;
            }
            DetachedShip detached = new DetachedShip(
                    readShipPose(owner.rawPipeline(source.level()), source.frame(), ship),
                    owner.contraptionsFor(ship, owner.rawPipeline(source.level())));
            transferShip(ship, source, target, detached.pose().pose(), detached.pose().position(),
                    detached, movingShips);
        }
        if (source.frame().constrained()) targetData.markConstrained(target.frame().id());
        FrameLocation merged = new FrameLocation(target.level(),
                targetData.frame(target.frame().id()).orElse(target.frame()));
        if (sameScene) owner.reanchorWorldConstraints(merged);
        owner.moveRopes(source.frame().id(), merged);
        return true;
    }

    private void shiftIntoFrame(ServerSubLevel ship, FrameLocation source, FrameLocation target) {
        ShipFramePose pose = readShipPose(owner.rawPipeline(source.level()), source.frame(), ship);
        shiftIntoFrame(ship, source, target, pose.pose(), pose.position());
    }

    private void shiftIntoFrame(ServerSubLevel ship, FrameLocation source, FrameLocation target,
                                Pose3dc targetPose, SpacePosition targetPosition) {
        PhysicsPipeline scene = owner.rawPipeline(target.level());
        ShipFramePose previous = readShipPose(scene, source.frame(), ship);
        Pose3d previousPhysical = scene.readPose(ship, new Pose3d());
        SpaceGridFrameData data = SpaceGridFrameData.get(target.level());
        SpaceVec3 physical = SpacePhysicsFrameGrid.toPhysicsPosition(
                target.frame().cell(), target.frame().origin(), targetPosition).orElseThrow();
        try {
            scene.teleport(ship, SpaceGridFrameCoordinates.toVector(physical), targetPose.orientation());
            Vector3d projected = SpaceGridFrameCoordinates.toProjectionPosition(target.frame(), targetPosition);
            owner.carryAcrossProjection(ship, target.frame(), projected);
            ship.logicalPose().position().set(projected);
            ship.logicalPose().orientation().set(targetPose.orientation());
            owner.storePose(ship, SpaceGridDirectory.get(spaceLevel).shipAddress(ship.getUniqueId()),
                    targetPosition, new Quaterniond(targetPose.orientation()));
            data.moveShip(ship.getUniqueId(), target.frame().id());
        } catch (RuntimeException | Error failure) {
            try {
                scene.teleport(ship, previousPhysical.position(), previousPhysical.orientation());
                Vector3d sourceProjection = SpaceGridFrameCoordinates.toProjectionPosition(source.frame(), previous.position());
                owner.carryAcrossProjection(ship, source.frame(), sourceProjection);
                ship.logicalPose().position().set(sourceProjection);
                ship.logicalPose().orientation().set(previous.pose().orientation());
                owner.storePose(ship, SpaceGridDirectory.get(spaceLevel).shipAddress(ship.getUniqueId()),
                        previous.position(), new Quaterniond(previous.pose().orientation()));
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            try {
                UUID currentFrame = data.frameForShip(ship.getUniqueId()).map(SpaceGridFrameData.Frame::id)
                        .orElse(null);
                if (!source.frame().id().equals(currentFrame)) {
                    data.removeShip(ship.getUniqueId());
                    data.restoreMembership(source.frame(), ship.getUniqueId());
                }
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    private void transferShip(ServerSubLevel ship, FrameLocation source, FrameLocation target,
                              Pose3dc pose, SpacePosition position, DetachedShip detached,
                              Set<UUID> movingShips) {
        PhysicsPipeline sourcePipeline = owner.rawPipeline(source.level());
        PhysicsPipeline targetPipeline = owner.rawPipeline(target.level());
        SpaceGridFrameData targetData = SpaceGridFrameData.get(target.level());
        List<SectionTransfer> sections = List.of();
        List<KinematicContraption> addedContraptions = new ArrayList<>();
        boolean shipAdded = true;
        boolean membershipAdded = targetData.frameForShip(ship.getUniqueId())
                .map(frame -> frame.id().equals(target.frame().id())).orElse(false);
        try {
            targetPipeline.add(ship, SpaceGridFrameCoordinates.toPhysicsPose(target.frame(), pose, position));
            sections = owner.addSectionsTo(targetPipeline, ship);
            for (KinematicContraption contraption : detached.contraptions()) {
                addedContraptions.add(contraption);
                targetPipeline.add(contraption);
            }
            targetPipeline.addLinearAndAngularVelocity(ship,
                    detached.pose().linearVelocity(), detached.pose().angularVelocity());

            if (!membershipAdded) {
                membershipAdded = true;
                targetData.addShip(target.frame().id(), ship.getUniqueId());
            }
        } catch (RuntimeException | Error failure) {
            rollbackTargetTransfer(targetPipeline, ship, addedContraptions, sections,
                    targetData, membershipAdded, shipAdded, failure);
            throw failure;
        }

        SpaceGridDirectory directory = SpaceGridDirectory.get(spaceLevel);
        SpaceGridFrameData sourceData = SpaceGridFrameData.get(source.level());
        List<KinematicContraption> removedContraptions = new ArrayList<>();
        boolean sectionsMoved = false;
        boolean shipRemoved = false;
        boolean membershipRemoved = false;
        boolean ownerChanged = false;
        boolean projectionChanged = false;
        try {
            owner.finishSectionTransfer(sections, targetPipeline);
            sectionsMoved = true;
            for (KinematicContraption contraption : detached.contraptions()) {
                sourcePipeline.remove(contraption);
                removedContraptions.add(contraption);
            }
            shipRemoved = true;
            sourcePipeline.remove(ship);
            membershipRemoved = true;
            sourceData.removeShip(ship.getUniqueId());
            ownerChanged = true;
            directory.setFrameOwnerAddress(ship.getUniqueId(),
                    SpaceGridLevelFactory.addressOf(spaceLevel.getServer(), target.level().dimension()));
            projectionChanged = true;
            owner.setLogicalPosition(ship, target.frame(), position);
            ship.logicalPose().orientation().set(pose.orientation());
            owner.storePose(ship, directory.shipAddress(ship.getUniqueId()), position,
                    new Quaterniond(pose.orientation()));
        } catch (RuntimeException | Error failure) {
            rollbackTargetTransfer(targetPipeline, ship, addedContraptions, sections,
                    targetData, membershipAdded, shipAdded, failure);
            if (ownerChanged) {
                directory.setFrameOwnerAddress(ship.getUniqueId(),
                        SpaceGridLevelFactory.addressOf(spaceLevel.getServer(), source.level().dimension()));
            }
            if (membershipRemoved) {
                try {
                    sourceData.restoreMembership(source.frame(), ship.getUniqueId());
                } catch (RuntimeException | Error rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            if (sectionsMoved) owner.restoreSectionTransfer(sections, failure);
            if (shipRemoved) {
                try {
                    sourcePipeline.add(ship, SpaceGridFrameCoordinates.toPhysicsPose(source.frame(), detached.pose().pose(),
                            detached.pose().position()));
                    sourcePipeline.addLinearAndAngularVelocity(ship, detached.pose().linearVelocity(),
                            detached.pose().angularVelocity());
                } catch (RuntimeException | Error rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            for (int i = removedContraptions.size() - 1; i >= 0; i--) {
                KinematicContraption contraption = removedContraptions.get(i);
                try {
                    sourcePipeline.add(contraption);
                    contraptionOwners.put(contraption, sourcePipeline);
                } catch (RuntimeException | Error rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            if (projectionChanged) {
                try {
                    Vector3d sourceProjection = SpaceGridFrameCoordinates.toProjectionPosition(source.frame(), detached.pose().position());
                    owner.carryAcrossProjection(ship, source.frame(), sourceProjection);
                    ship.logicalPose().position().set(sourceProjection);
                    ship.logicalPose().orientation().set(detached.pose().pose().orientation());
                    owner.storePose(ship, directory.shipAddress(ship.getUniqueId()), detached.pose().position(),
                            new Quaterniond(detached.pose().pose().orientation()));
                } catch (RuntimeException | Error rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }

        for (KinematicContraption contraption : detached.contraptions()) {
            contraptionOwners.put(contraption, targetPipeline);
        }
        owner.migrateConstraints(ship, target, movingShips);
    }

    static boolean isShipOwnedBy(SpaceGridDirectory directory, UUID shipId, SpaceGridAddress address) {
        return address.equals(directory.frameOwnerAddress(shipId));
    }

    private static void rollbackTargetTransfer(PhysicsPipeline targetPipeline, ServerSubLevel ship,
                                               List<KinematicContraption> addedContraptions,
                                               List<SectionTransfer> sections, SpaceGridFrameData targetData,
                                               boolean membershipAdded, boolean shipAdded, Throwable failure) {
        for (int i = addedContraptions.size() - 1; i >= 0; i--) {
            try {
                targetPipeline.remove(addedContraptions.get(i));
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
        SpaceGridPhysicsPipeline.rollbackSections(targetPipeline, sections, failure);
        if (shipAdded) {
            try {
                targetPipeline.remove(ship);
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
        if (membershipAdded) {
            try {
                targetData.removeShip(ship.getUniqueId());
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    private record DetachedShip(ShipFramePose pose, List<KinematicContraption> contraptions) {}
    record SectionTransfer(long key, int x, int y, int z, PhysicsPipeline previous,
                           LevelChunkSection section) {}

    private Optional<SpacePosition> mergedOrigin(FrameLocation target, FrameLocation source,
                                                  Map<UUID, ServerSubLevel> ships) {
        List<SpacePosition> positions = new ArrayList<>();
        PhysicsPipeline targetPipeline = owner.rawPipeline(target.level());
        PhysicsPipeline sourcePipeline = owner.rawPipeline(source.level());
        for (UUID shipId : target.frame().ships()) {
            ServerSubLevel ship = ships.get(shipId);
            if (ship == null || ship.isRemoved()) return Optional.empty();
            positions.add(readShipPose(targetPipeline, target.frame(), ship).position());
        }
        for (UUID shipId : source.frame().ships()) {
            ServerSubLevel ship = ships.get(shipId);
            if (ship == null || ship.isRemoved()) return Optional.empty();
            positions.add(readShipPose(sourcePipeline, source.frame(), ship).position());
        }
        Optional<SpacePosition> center = center(positions);
        if (center.isEmpty()) return Optional.empty();
        for (SpacePosition position : positions) {
            if (SpacePhysicsFrameGrid.toPhysicsPosition(target.frame().cell(), center.get(), position).isEmpty()) {
                return Optional.empty();
            }
        }
        return center;
    }

    private Optional<SpacePosition> center(List<SpacePosition> positions) {
        if (positions.isEmpty()) return Optional.empty();
        SpacePosition base = positions.getFirst();
        double minX = 0.0;
        double minY = 0.0;
        double minZ = 0.0;
        double maxX = 0.0;
        double maxY = 0.0;
        double maxZ = 0.0;
        double range = SpacePhysicsFrameGrid.SPACING * 2.0;
        for (int i = 1; i < positions.size(); i++) {
            Optional<SpaceVec3> delta = base.deltaWithin(positions.get(i), range);
            if (delta.isEmpty()) return Optional.empty();
            SpaceVec3 value = delta.get();
            minX = Math.min(minX, value.x());
            minY = Math.min(minY, value.y());
            minZ = Math.min(minZ, value.z());
            maxX = Math.max(maxX, value.x());
            maxY = Math.max(maxY, value.y());
            maxZ = Math.max(maxZ, value.z());
        }
        return Optional.of(base.add(new SpaceVec3(
                (minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5)));
    }

    private FrameLocation rebaseFrame(FrameLocation location, SpacePosition origin,
                                      Map<UUID, ServerSubLevel> ships) {
        return rebaseFrame(location, origin, ships, Map.of());
    }

    private FrameLocation rebaseFrame(FrameLocation location, SpacePosition origin,
                                      Map<UUID, ServerSubLevel> ships, Map<UUID, SpacePosition> targets) {
        PhysicsPipeline pipeline = owner.rawPipeline(location.level());
        Map<UUID, ShipFramePose> poses = new HashMap<>();
        Map<UUID, ShipFramePose> previousPoses = new HashMap<>();
        Map<UUID, Pose3d> physicalPoses = new HashMap<>();
        SpaceGridFrameData data = SpaceGridFrameData.get(location.level());
        for (UUID shipId : location.frame().ships()) {
            ServerSubLevel ship = ships.get(shipId);
            if (ship == null || ship.isRemoved()) return location;
            ShipFramePose pose = readShipPose(pipeline, location.frame(), ship);
            previousPoses.put(shipId, pose);
            physicalPoses.put(shipId, pipeline.readPose(ship, new Pose3d()));
            SpacePosition target = targets.get(shipId);
            poses.put(shipId, target == null ? pose
                    : new ShipFramePose(target, pose.pose(), pose.linearVelocity(), pose.angularVelocity()));
        }
        Map<UUID, SpaceVec3> translatedPositions = new HashMap<>();
        for (Map.Entry<UUID, ShipFramePose> entry : poses.entrySet()) {
            Optional<SpaceVec3> translated = SpacePhysicsFrameGrid.toPhysicsPosition(
                    location.frame().cell(), origin, entry.getValue().position());
            if (translated.isEmpty()) return location;
            translatedPositions.put(entry.getKey(), translated.get());
        }

        List<UUID> teleported = new ArrayList<>();
        boolean originChanged = false;
        try {
            for (Map.Entry<UUID, ShipFramePose> entry : poses.entrySet()) {
                teleported.add(entry.getKey());
                pipeline.teleport(ships.get(entry.getKey()), SpaceGridFrameCoordinates.toVector(translatedPositions.get(entry.getKey())),
                        entry.getValue().pose().orientation());
            }

            SpaceGridFrameData.Frame updated = data.setOrigin(location.frame().id(), origin);
            originChanged = true;
            for (Map.Entry<UUID, ShipFramePose> entry : poses.entrySet()) {
                ServerSubLevel ship = ships.get(entry.getKey());
                ShipFramePose pose = entry.getValue();
                ship.logicalPose().position().set(SpaceGridFrameCoordinates.toProjectionPosition(updated, pose.position()));
                ship.logicalPose().orientation().set(pose.pose().orientation());
                owner.storePose(ship, SpaceGridDirectory.get(spaceLevel).shipAddress(ship.getUniqueId()),
                        pose.position(), new Quaterniond(pose.pose().orientation()));
            }
        } catch (RuntimeException | Error failure) {
            if (originChanged) {
                try {
                    data.setOrigin(location.frame().id(), location.frame().origin());
                } catch (RuntimeException | Error rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            for (int i = teleported.size() - 1; i >= 0; i--) {
                UUID shipId = teleported.get(i);
                try {
                    Pose3d previous = physicalPoses.get(shipId);
                    pipeline.teleport(ships.get(shipId), previous.position(), previous.orientation());
                } catch (RuntimeException | Error rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            for (Map.Entry<UUID, ShipFramePose> entry : poses.entrySet()) {
                UUID shipId = entry.getKey();
                ServerSubLevel ship = ships.get(shipId);
                ShipFramePose previous = previousPoses.get(shipId);
                ship.logicalPose().position().set(SpaceGridFrameCoordinates.toProjectionPosition(location.frame(), previous.position()));
                ship.logicalPose().orientation().set(previous.pose().orientation());
                try {
                    owner.storePose(ship, SpaceGridDirectory.get(spaceLevel).shipAddress(shipId),
                            previous.position(), new Quaterniond(previous.pose().orientation()));
                } catch (RuntimeException | Error rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }

        SpaceGridFrameData.Frame updated = data.frame(location.frame().id()).orElseThrow();
        FrameLocation rebased = new FrameLocation(location.level(), updated);
        owner.moveRopes(location.frame().id(), rebased);
        owner.reanchorWorldConstraints(rebased);
        return rebased;
    }

    ShipFramePose readShipPose(PhysicsPipeline pipeline, SpaceGridFrameData.Frame frame,
                                       ServerSubLevel ship) {
        Pose3d physical = pipeline.readPose(ship, new Pose3d());
        SpacePosition position = SpaceGridFrameCoordinates.toLogicalPosition(frame, physical.position());
        Pose3d logical = new Pose3d(new Vector3d(position.x(), position.y(), position.z()),
                new Quaterniond(physical.orientation()), new Vector3d(ship.logicalPose().rotationPoint()),
                new Vector3d(ship.logicalPose().scale()));
        logical.position().set(SpaceGridFrameCoordinates.toProjectionPosition(frame, position));
        return new ShipFramePose(position, logical,
                pipeline.getLinearVelocity(ship, new Vector3d()),
                pipeline.getAngularVelocity(ship, new Vector3d()));
    }

    record ShipFramePose(SpacePosition position, Pose3d pose,
                         Vector3d linearVelocity, Vector3d angularVelocity) {}

    boolean needsReframe(SpaceGridFrameData.Frame frame, SpacePosition position) {
        Optional<SpaceVec3> relative = frame.origin().deltaWithin(position,
                Math.sqrt(3.0) * SpacePhysicsFrameGrid.SPACING * 0.5);
        if (relative.isEmpty()) return true;
        SpaceVec3 delta = relative.get();
        return Math.abs(delta.x()) > FRAME_REBASE_DISTANCE
                || Math.abs(delta.y()) > FRAME_REBASE_DISTANCE
                || Math.abs(delta.z()) > FRAME_REBASE_DISTANCE;
    }

    void rebaseFrame(SpaceGridAddress frameOwner, SpaceGridFrameData.Frame frame) {
        Map<UUID, ServerSubLevel> ships = frames.loadedShips();
        FrameLocation location = new FrameLocation(
                SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), frameOwner), frame);
        Map<UUID, SpacePosition> positions = framePositions(location, ships);
        if (positions == null) return;
        center(List.copyOf(positions.values())).ifPresent(origin -> rebaseFrame(location, origin, ships));
    }

    boolean rebaseForTeleport(ServerSubLevel teleportedShip, SpaceGridAddress frameOwner,
                                   SpaceGridFrameData.Frame frame, SpacePosition targetPosition) {
        Map<UUID, ServerSubLevel> ships = frames.loadedShips();
        ServerLevel level = SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), frameOwner);
        FrameLocation location = new FrameLocation(level, frame);
        frame = location.frame();
        PhysicsPipeline host = owner.rawPipeline(level);
        List<SpacePosition> positions = new ArrayList<>();
        for (UUID shipId : frame.ships()) {
            ServerSubLevel ship = ships.get(shipId);
            if (ship == null || ship.isRemoved()) return false;
            positions.add(shipId.equals(teleportedShip.getUniqueId())
                    ? targetPosition
                    : readShipPose(host, frame, ship).position());
        }
        SpacePosition origin = center(positions).orElse(null);
        boolean fits = origin != null;
        for (SpacePosition position : positions) {
            if (!fits) break;
            fits = SpacePhysicsFrameGrid.toPhysicsPosition(frame.cell(), origin, position).isPresent();
        }
        if (!fits) {
            Astronautics.LOGGER.warn("Ship {} was teleported too far from its connected ships, moving it to its own physics frame",
                    teleportedShip.getUniqueId());
            return false;
        }
        rebaseFrame(location, origin, ships, Map.of(teleportedShip.getUniqueId(), targetPosition));
        return true;
    }

    private SpaceGridFrameData.Frame frame(UUID shipId, SpaceGridAddress frameOwner) {
        ServerLevel level = SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), frameOwner);
        return SpaceGridFrameData.get(level).frameForShip(shipId)
                .orElseThrow(() -> new IllegalStateException("ship has no persisted physics frame: " + shipId));
    }
}
