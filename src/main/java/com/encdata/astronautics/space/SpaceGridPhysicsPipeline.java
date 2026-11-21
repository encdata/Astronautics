package com.encdata.astronautics.space;

import com.encdata.astronautics.Astronautics;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.math.BigInteger;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.UUID;

final class SpaceGridPhysicsPipeline implements InvocationHandler {
    private static final double FRAME_REBASE_DISTANCE = SpacePhysicsFrameGrid.SPACING * 0.375;
    private static final double FRAME_JOIN_DISTANCE = FRAME_REBASE_DISTANCE * 0.5;
    private static final double FRAME_MERGE_SPREAD = SpaceConstants.SHIP_GRID_ATTACH_RANGE;
    private static final double FRAME_SPLIT_SPREAD = FRAME_MERGE_SPREAD * 1.25;
    private static final double GRID_IMAGE_RANGE = 4096.0;
    private static final double ROPE_RANGE = 1.0e12;
    private static final double ANCHOR_RANGE = 1.0e12;
    private static final int FRAME_HOST_SEARCH = 64;
    private static final ThreadLocal<Map<UUID, SpacePosition>> TRANSFER_POSITIONS = new ThreadLocal<>();

    private final ServerLevel spaceLevel;
    private final PhysicsPipeline pipeline;
    private final Map<Long, PhysicsPipeline> sectionOwners = new HashMap<>();
    private final Map<KinematicContraption, PhysicsPipeline> contraptionOwners = new HashMap<>();
    private final Map<UUID, List<GridRope>> framedRopes = new HashMap<>();
    private final List<WorldConstraint> worldConstraints = new ArrayList<>();
    private final Set<UUID> teleportedShips = new HashSet<>();
    private boolean teleporting;
    private final Map<ShipConstraint, Set<UUID>> shipConstraints = new HashMap<>();
    private long lastSkippedConstraintWarning = Long.MIN_VALUE;

    SpaceGridPhysicsPipeline(ServerLevel spaceLevel, PhysicsPipeline pipeline) {
        this.spaceLevel = spaceLevel;
        this.pipeline = pipeline;
    }

    static <T> T withTransferPositions(Map<UUID, SpacePosition> positions, Supplier<T> action) {
        Map<UUID, SpacePosition> previous = TRANSFER_POSITIONS.get();
        TRANSFER_POSITIONS.set(positions);
        try {
            return action.get();
        } finally {
            if (previous == null) TRANSFER_POSITIONS.remove();
            else TRANSFER_POSITIONS.set(previous);
        }
    }

    static Object invoke(PhysicsPipeline pipeline, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(pipeline, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        if (name.equals("tick")) maintainFrames();
        if (isPipelineLifecycle(name)) {
            for (PhysicsPipeline host : activeHostPipelines()) invoke(host, method, args);
        }

        switch (name) {
            case "handleChunkSectionAddition" -> {
                addSection((LevelChunkSection) args[0], (int) args[1], (int) args[2], (int) args[3],
                        (boolean) args[4]);
                return null;
            }
            case "handleChunkSectionRemoval" -> {
                removeSection((int) args[0], (int) args[1], (int) args[2]);
                return null;
            }
            case "handleBlockChange" -> {
                SectionPos sectionPos = (SectionPos) args[0];
                PhysicsPipeline owner = sectionOwners.get(sectionPos.asLong());
                if (owner == null) owner = sectionPipeline(sectionPos.x(), sectionPos.z());
                if (owner != null) invoke(owner, method, args);
                return null;
            }
        }

        if (args != null && args.length == 1 && args[0] instanceof KinematicContraption contraption) {
            if (name.equals("add")) {
                PhysicsPipeline owner = contraptionPipeline(contraption);
                owner.add(contraption);
                contraptionOwners.put(contraption, owner);
                return null;
            }
            if (name.equals("remove")) {
                PhysicsPipeline owner = contraptionOwners.remove(contraption);
                (owner == null ? pipeline : owner).remove(contraption);
                return null;
            }
        }

        if (args != null && args.length > 0 && args[0] instanceof ServerSubLevel ship) {
            if (name.equals("add") && args.length == 2) {
                addShip(ship, (Pose3dc) args[1]);
                return null;
            }
            PhysicsPipeline host = hostPipeline(ship);
            if (host != null) {
                return invokeShipMethod(host, ship, method, args);
            }
        }

        if (name.equals("addRope") && args != null && args.length == 1 && args[0] instanceof RopePhysicsObject rope) {
            return createRope(rope);
        }

        if (name.equals("addConstraint") && args != null && args.length == 3) {
            PhysicsPipeline host;
            try {
                host = constraintPipeline(args[0], args[1]);
            } catch (FrameMismatch exception) {
                warnSkippedConstraint(exception.getMessage());
                return inertHandle((PhysicsConstraintConfiguration<?>) args[2]);
            }
            if (host != null) {
                if (args[0] == null || args[1] == null) return addWorldConstraint(host, method, args);
                Object handle = SpaceGridPhysicsPipelineProvider.addConstraint(host, method, args);
                if (handle instanceof PhysicsConstraintHandle constraint) {
                    markConstraint(args[0], args[1]);
                    ShipConstraint tracked = new ShipConstraint(method,
                            (ServerSubLevel) args[0], (ServerSubLevel) args[1],
                            (PhysicsConstraintConfiguration<?>) args[2], constraint,
                            frameOf((ServerSubLevel) args[0]).level());
                    shipConstraints.put(tracked, Set.of(((ServerSubLevel) args[0]).getUniqueId(),
                            ((ServerSubLevel) args[1]).getUniqueId()));
                    return tracked.handle;
                }
                return handle;
            }
        }

        return invoke(pipeline, method, args);
    }

    private static boolean isPipelineLifecycle(String name) {
        return name.equals("prePhysicsTicks") || name.equals("physicsTick")
                || name.equals("postPhysicsTicks") || name.equals("tick");
    }

    private List<PhysicsPipeline> activeHostPipelines() {
        SpaceGridDirectory directory = SpaceGridDirectory.get(spaceLevel);
        SubLevelContainer container = SubLevelContainer.getContainer(spaceLevel);
        if (container == null) return List.of();

        Set<SpaceGridAddress> owners = new HashSet<>();
        for (Object object : container.getAllSubLevels()) {
            if (object instanceof ServerSubLevel ship) {
                SpaceGridAddress owner = directory.frameOwnerAddress(ship.getUniqueId());
                if (owner != null) owners.add(owner);
            }
        }

        List<PhysicsPipeline> result = new ArrayList<>(owners.size());
        for (SpaceGridAddress owner : owners) {
            ServerLevel level = SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), owner);
            PhysicsPipeline wrapped = SubLevelPhysicsSystem.require(level).getPipeline();
            if (wrapped instanceof SpaceGridHostPipeline host) {
                result.add(host.astronautics$rawPipeline());
            }
        }
        return result;
    }

    private void addShip(ServerSubLevel ship, Pose3dc sourcePose) {
        UUID id = ship.getUniqueId();
        SpaceGridDirectory directory = SpaceGridDirectory.get(spaceLevel);
        Map<UUID, SpacePosition> transferPositions = TRANSFER_POSITIONS.get();
        SpacePosition transferPosition = transferPositions == null ? null : transferPositions.get(id);
        SpaceGridAddress logicalOwner = transferPosition == null
                ? directory.shipAddress(id)
                : transferPosition.grid();
        SpaceGridShipState state = null;
        if (logicalOwner != null) {
            ServerLevel stateLevel = SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), logicalOwner);
            state = SpaceGridShipData.get(stateLevel).get(id);
        }

        SpacePosition position;
        SpaceGridAddress projection;
        Pose3d pose;
        if (transferPosition != null) {
            position = transferPosition;
            projection = position.grid();
            state = state(position, sourcePose, new Vector3d(), new Vector3d());
            pose = new Pose3d(sourcePose);
        } else if (state == null) {
            Vector3d sourcePosition = new Vector3d(sourcePose.position());
            SpaceGridAddress baseGrid = logicalOwner == null ? creationGrid(sourcePose.position()) : logicalOwner;
            position = SpacePosition.normalize(baseGrid,
                    sourcePosition.x, sourcePosition.y, sourcePosition.z);
            projection = baseGrid;
            logicalOwner = position.grid();
            state = state(position, sourcePose, new Vector3d(), new Vector3d());
            pose = new Pose3d(sourcePose);
        } else {
            position = new SpacePosition(logicalOwner, state.x(), state.y(), state.z());
            projection = position.grid();
            pose = pose(state, sourcePose.scale());
        }

        SpaceGridAddress previousFrameOwner = directory.frameOwnerAddress(id);
        Optional<FrameLocation> existingFrame = existingFrame(id, previousFrameOwner);
        if (existingFrame.isPresent() && SpacePhysicsFrameGrid.toPhysicsPosition(
                existingFrame.get().frame().cell(), existingFrame.get().frame().origin(), position).isEmpty()) {
            dropMembership(existingFrame.get().level(), id);
            existingFrame = Optional.empty();
        }
        if (existingFrame.isEmpty()) existingFrame = findFrame(position, projection, null);
        FrameLocation location = existingFrame.isPresent() ? existingFrame.get()
                : allocateFrame(position, projection, id);
        SpaceGridDirectory.get(spaceLevel).setShipAddress(id, logicalOwner);
        SpaceGridDirectory.get(spaceLevel).setFrameOwnerAddress(id,
                SpaceGridLevelFactory.addressOf(spaceLevel.getServer(), location.level().dimension()));
        if (!location.frame().ships().contains(id)) {
            SpaceGridFrameData.get(location.level()).addShip(location.frame().id(), id);
        }

        ServerLevel stateLevel = SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), logicalOwner);
        SpaceGridShipData.get(stateLevel).put(id, state);
        PhysicsPipeline host = rawPipeline(location.level());
        host.add(ship, physicsPose(location.frame(), pose, position));
        ship.logicalPose().position().set(projectionPosition(location.frame(), position));
        ship.logicalPose().orientation().set(pose.orientation());
    }

    private Optional<FrameLocation> existingFrame(UUID shipId, SpaceGridAddress frameOwner) {
        if (frameOwner == null) return Optional.empty();
        ServerLevel level = SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), frameOwner);
        return SpaceGridFrameData.get(level).frameForShip(shipId)
                .map(frame -> new FrameLocation(level, frame));
    }

    private Optional<FrameLocation> findFrame(SpacePosition position, SpaceGridAddress projection,
                                              UUID excludedFrame) {
        FrameLocation nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (ServerLevel level : spaceLevel.getServer().getAllLevels()) {
            if (!SpaceConstants.isGridLevel(level.dimension())) continue;
            for (SpaceGridFrameData.Frame frame : SpaceGridFrameData.get(level).frames()) {
                if (frame.id().equals(excludedFrame) || !frame.projectionGrid().equals(projection)) continue;
                Optional<SpaceVec3> relative = frame.origin().deltaWithin(position,
                        Math.sqrt(3.0) * FRAME_JOIN_DISTANCE);
                if (relative.isEmpty()) continue;
                SpaceVec3 delta = relative.get();
                if (Math.abs(delta.x()) > FRAME_JOIN_DISTANCE
                        || Math.abs(delta.y()) > FRAME_JOIN_DISTANCE
                        || Math.abs(delta.z()) > FRAME_JOIN_DISTANCE) continue;
                double distance = delta.x() * delta.x() + delta.y() * delta.y() + delta.z() * delta.z();
                if (distance < nearestDistance) {
                    nearest = new FrameLocation(level, frame);
                    nearestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(nearest);
    }

    private Object invokeShipMethod(PhysicsPipeline host, ServerSubLevel ship, Method method, Object[] args)
            throws Throwable {
        UUID id = ship.getUniqueId();
        SpaceGridDirectory directory = SpaceGridDirectory.get(spaceLevel);
        SpaceGridAddress owner = directory.shipAddress(id);
        SpaceGridAddress frameOwner = directory.frameOwnerAddress(id);
        if (owner == null || frameOwner == null) return invoke(pipeline, method, args);

        SpaceGridFrameData.Frame frame = frame(id, frameOwner);
        switch (method.getName()) {
            case "remove" -> {
                Object result = invoke(host, method, args);
                releaseSections(ship);
                dropMembership(SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), frameOwner), id);
                return result;
            }
            case "wakeUp", "onStatsChanged", "getLinearVelocity", "getAngularVelocity",
                    "addLinearAndAngularVelocity", "applyLinearAndAngularImpulse" -> {
                return invoke(host, method, args);
            }
            case "teleport" -> {
                Vector3d requested = new Vector3d((org.joml.Vector3dc) args[1]);
                SpacePosition position = nearShip(ship, frame.projectionGrid(), requested);
                Optional<SpaceVec3> physics = SpacePhysicsFrameGrid.toPhysicsPosition(
                        frame.cell(), frame.origin(), position);
                if (physics.isEmpty()) {
                    FrameLocation location = new FrameLocation(
                            SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), frameOwner), frame);
                    if ((frame.ships().size() > 1 && !frame.constrained())
                            || !rebaseForTeleport(ship, frameOwner, frame, position)) {
                        Pose3d target = new Pose3d(new Vector3d(position.x(), position.y(), position.z()),
                                new Quaterniond((org.joml.Quaterniondc) args[2]),
                                new Vector3d(ship.logicalPose().rotationPoint()),
                                new Vector3d(ship.logicalPose().scale()));
                        teleporting = true;
                        try {
                            moveToOwnFrame(ship, location, target, position);
                        } finally {
                            teleporting = false;
                        }
                    }
                    host = hostPipeline(ship);
                    frameOwner = directory.frameOwnerAddress(id);
                    frame = frame(id, frameOwner);
                    physics = SpacePhysicsFrameGrid.toPhysicsPosition(frame.cell(), frame.origin(), position);
                }
                Vector3d translated = vector(physics.orElseThrow());
                Object[] translatedArgs = args.clone();
                translatedArgs[1] = translated;
                Object result = invoke(host, method, translatedArgs);
                teleportedShips.add(id);
                ship.logicalPose().position().set(projectionPosition(frame, position));
                ship.logicalPose().orientation().set((org.joml.Quaterniondc) args[2]);
                storePose(ship, owner, position, new Quaterniond((org.joml.Quaterniondc) args[2]));
                return result;
            }
            case "applyImpulse" -> {
                return invoke(host, method, args);
            }
            case "readPose" -> {
                Pose3d physical = new Pose3d();
                host.readPose(ship, physical);
                SpacePosition position = SpacePhysicsFrameGrid.toLogicalPosition(
                        frame.cell(), frame.origin(), new SpaceVec3(
                                physical.position().x, physical.position().y, physical.position().z));
                Quaterniond orientation = new Quaterniond(physical.orientation());
                SpaceGridAddress currentProjection = frame.projectionGrid();
                Optional<SpacePosition> center = averageFramePosition(frame, id, position);
                SpaceGridAddress projection = center.isPresent()
                        ? SpaceGrids.stableFrame(currentProjection, center.get(),
                        SpaceConstants.SHIP_GRID_ATTACH_RANGE * 0.5)
                        : currentProjection;
                if (projection != null && !frame.projectionGrid().equals(projection)) {
                    frame = SpaceGridFrameData.get(SpaceGridLevelFactory.getOrCreate(
                            spaceLevel.getServer(), frameOwner)).setProjectionGrid(frame.id(), projection);
                }
                Vector3d projected = projectionPosition(frame, position);
                if (!teleportedShips.remove(id)) carryAcrossProjection(ship, frame, projected);
                Pose3d logical = new Pose3d(
                        projected, orientation,
                        new Vector3d(ship.logicalPose().rotationPoint()), new Vector3d(ship.logicalPose().scale()));
                ship.logicalPose().position().set(projected);
                ship.logicalPose().orientation().set(orientation);
                ((Pose3d) args[1]).set(logical);
                storePose(ship, owner, position, orientation);
                if (needsReframe(frame, position)) {
                    rebaseFrame(frameOwner, frame);
                }
                return args[1];
            }
        }
        return invoke(host, method, args);
    }

    private void carryAcrossProjection(ServerSubLevel ship, SpaceGridFrameData.Frame frame, Vector3d projected) {
        Vector3d last = ((Pose3d) ship.lastPose()).position();
        long gridX = Math.round((projected.x - last.x) / SpacePosition.GRID_SIZE);
        long gridY = Math.round((projected.y - last.y) / SpacePosition.GRID_SIZE);
        long gridZ = Math.round((projected.z - last.z) / SpacePosition.GRID_SIZE);
        if (gridX == 0L && gridY == 0L && gridZ == 0L) return;

        Vec3 jump = new Vec3(gridX * SpacePosition.GRID_SIZE, gridY * SpacePosition.GRID_SIZE,
                gridZ * SpacePosition.GRID_SIZE);
        AABB area = ship.boundingBox().toMojang();
        Vec3 center = area.getCenter();
        boolean boundsMoved = Math.abs(center.x - last.x) > SpacePosition.GRID_HALF_SIZE
                || Math.abs(center.y - last.y) > SpacePosition.GRID_HALF_SIZE
                || Math.abs(center.z - last.z) > SpacePosition.GRID_HALF_SIZE;
        if (boundsMoved) area = area.move(-jump.x, -jump.y, -jump.z);
        last.add(jump.x, jump.y, jump.z);

        if (!Double.isFinite(area.getSize()) || area.getSize() > SpacePhysicsFrameGrid.SPACING) return;
        SpaceGridAddress to = frame.projectionGrid();
        SpaceGridAddress from = to.offset(BigInteger.valueOf(gridX), BigInteger.valueOf(gridY),
                BigInteger.valueOf(gridZ));
        SpaceGrids.carryEntities(spaceLevel, area.inflate(SpaceConstants.SHIP_GRID_ATTACH_RANGE), from, to, jump);
    }

    private void setLogicalPosition(ServerSubLevel ship, SpaceGridFrameData.Frame frame, SpacePosition position) {
        Vector3d projected = projectionPosition(frame, position);
        if (!teleporting) carryAcrossProjection(ship, frame, projected);
        ship.logicalPose().position().set(projected);
    }

    private PhysicsPipeline constraintPipeline(Object bodyA, Object bodyB) {
        if (!(bodyA instanceof ServerSubLevel) && !(bodyB instanceof ServerSubLevel)) return null;
        if ((bodyA != null && !(bodyA instanceof ServerSubLevel))
                || (bodyB != null && !(bodyB instanceof ServerSubLevel))) {
            throw new FrameMismatch("a space ship cannot be joined to a body from another physics scene");
        }
        ServerSubLevel shipB = bodyB instanceof ServerSubLevel value ? value : null;
        ServerSubLevel ship = bodyA instanceof ServerSubLevel value ? value : shipB;
        PhysicsPipeline host = hostPipeline(ship);
        if (host == null) throw new FrameMismatch("ship " + ship.getUniqueId() + " has no physics frame");
        if (shipB != null && shipB != ship) {
            SpaceGridDirectory directory = SpaceGridDirectory.get(spaceLevel);
            SpaceGridAddress ownerA = directory.frameOwnerAddress(ship.getUniqueId());
            SpaceGridAddress ownerB = directory.frameOwnerAddress(shipB.getUniqueId());
            if (ownerA == null || ownerB == null) {
                throw new FrameMismatch("ships " + ship.getUniqueId() + " and " + shipB.getUniqueId()
                        + " do not both have physics frames");
            }
            if (!frame(ship.getUniqueId(), ownerA).id().equals(frame(shipB.getUniqueId(), ownerB).id())) {
                if (!mergeFrames(ship, shipB)) {
                    throw new FrameMismatch("ships " + ship.getUniqueId() + " and " + shipB.getUniqueId()
                            + " cannot share one physics frame");
                }
                host = hostPipeline(ship);
            }
        }
        return host;
    }

    private void warnSkippedConstraint(String reason) {
        long now = spaceLevel.getGameTime();
        if (now - lastSkippedConstraintWarning < 200) return;
        lastSkippedConstraintWarning = now;
        Astronautics.LOGGER.warn("Skipping a space constraint because {}", reason);
    }

    private static PhysicsConstraintHandle inertHandle(PhysicsConstraintConfiguration<?> config) {
        Class<?> type = switch (config) {
            case FixedConstraintConfiguration ignored -> FixedConstraintHandle.class;
            case FreeConstraintConfiguration ignored -> FreeConstraintHandle.class;
            case RotaryConstraintConfiguration ignored -> RotaryConstraintHandle.class;
            case GenericConstraintConfiguration ignored -> GenericConstraintHandle.class;
        };
        return (PhysicsConstraintHandle) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isValid" -> false;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "InertSpaceConstraint";
                    default -> null;
                });
    }

    private void refreshConstrainedFrames(Map<UUID, ServerSubLevel> ships) {
        shipConstraints.entrySet().removeIf(entry -> !entry.getKey().isLive());
        worldConstraints.removeIf(constraint -> !constraint.isLive());
        Set<UUID> held = new HashSet<>();
        for (Set<UUID> ids : shipConstraints.values()) held.addAll(ids);
        for (WorldConstraint constraint : worldConstraints) held.add(constraint.ship.getUniqueId());
        for (List<GridRope> ropes : framedRopes.values()) {
            for (GridRope rope : ropes) {
                for (RopeAttachment attachment : rope.attachments.values()) {
                    if (attachment.subLevel() != null) held.add(attachment.subLevel().getUniqueId());
                }
            }
        }
        for (FrameLocation location : activeFrames(ships.keySet())) {
            if (!location.frame().constrained()) continue;
            boolean anyHeld = false;
            for (UUID shipId : location.frame().ships()) {
                if (held.contains(shipId)) {
                    anyHeld = true;
                    break;
                }
            }
            if (!anyHeld) SpaceGridFrameData.get(location.level()).setConstrained(location.frame().id(), false);
        }
    }

    private static final class FrameMismatch extends RuntimeException {
        private FrameMismatch(String message) {
            super(message, null, false, false);
        }
    }

    private Object addWorldConstraint(PhysicsPipeline host, Method method, Object[] args) {
        worldConstraints.removeIf(constraint -> !constraint.isLive());
        boolean worldFirst = args[0] == null;
        ServerSubLevel ship = (ServerSubLevel) (worldFirst ? args[1] : args[0]);
        PhysicsConstraintConfiguration<?> config = (PhysicsConstraintConfiguration<?>) args[2];
        alignProjection(ship);
        WorldConstraint constraint = new WorldConstraint(ship, worldFirst, method, config);
        constraint.build(host, frameOf(ship).level());
        markConstraint(args[0], args[1]);
        worldConstraints.add(constraint);
        return constraint.handle;
    }

    private void reanchorWorldConstraints(FrameLocation location) {
        PhysicsPipeline host = rawPipeline(location.level());
        for (WorldConstraint constraint : List.copyOf(worldConstraints)) {
            if (location.frame().ships().contains(constraint.ship.getUniqueId())) {
                constraint.moveTo(host, location.level());
            }
        }
    }

    private SpacePosition worldPosition(ServerSubLevel ship, Vector3dc position) {
        return SpacePosition.normalize(SpaceGrids.shipGrid(spaceLevel, ship), position.x(), position.y(), position.z());
    }

    private Vector3d physicsPosition(ServerSubLevel ship, SpacePosition position) {
        FrameLocation location = frameOf(ship);
        if (location == null) throw new IllegalStateException("space ship has no physics frame for its constraint");
        SpaceVec3 delta = location.frame().origin().deltaWithin(position, ANCHOR_RANGE)
                .orElseThrow(() -> new IllegalStateException("constraint anchor is too far from its ship"));
        SpacePhysicsFrameGrid.Cell cell = location.frame().cell();
        return new Vector3d(SpacePhysicsFrameGrid.centerX(cell) + delta.x(),
                SpacePhysicsFrameGrid.centerY(cell) + delta.y(),
                SpacePhysicsFrameGrid.centerZ(cell) + delta.z());
    }

    private static Vector3dc worldAnchor(PhysicsConstraintConfiguration<?> config, boolean first) {
        return switch (config) {
            case FixedConstraintConfiguration value -> first ? value.pos1() : value.pos2();
            case FreeConstraintConfiguration value -> first ? value.pos1() : value.pos2();
            case RotaryConstraintConfiguration value -> first ? value.pos1() : value.pos2();
            case GenericConstraintConfiguration value -> first ? value.pos1() : value.pos2();
        };
    }

    private static PhysicsConstraintConfiguration<?> withWorldAnchor(PhysicsConstraintConfiguration<?> config,
                                                                     boolean first, Vector3dc anchor) {
        return switch (config) {
            case FixedConstraintConfiguration value -> new FixedConstraintConfiguration(
                    first ? anchor : value.pos1(), first ? value.pos2() : anchor, value.orientation());
            case FreeConstraintConfiguration value -> new FreeConstraintConfiguration(
                    first ? anchor : value.pos1(), first ? value.pos2() : anchor, value.orientation());
            case RotaryConstraintConfiguration value -> new RotaryConstraintConfiguration(
                    first ? anchor : value.pos1(), first ? value.pos2() : anchor, value.normal1(), value.normal2());
            case GenericConstraintConfiguration value -> new GenericConstraintConfiguration(
                    first ? anchor : value.pos1(), first ? value.pos2() : anchor,
                    value.orientation1(), value.orientation2(), value.lockedAxes());
        };
    }

    private void markConstraint(Object bodyA, Object bodyB) {
        ServerSubLevel ship = bodyA instanceof ServerSubLevel value
                ? value : (ServerSubLevel) bodyB;
        SpaceGridAddress owner = SpaceGridDirectory.get(spaceLevel).frameOwnerAddress(ship.getUniqueId());
        SpaceGridFrameData data = SpaceGridFrameData.get(
                SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), owner));
        data.markConstrained(frame(ship.getUniqueId(), owner).id());
    }

    private static SpacePosition nearShip(ServerSubLevel ship, SpaceGridAddress frameGrid, Vector3dc requested) {
        SpacePosition plain = SpacePosition.normalize(frameGrid, requested.x(), requested.y(), requested.z());
        SpacePosition current = SpaceProjection.shipPosition(ship);
        if (current.deltaWithin(plain, GRID_IMAGE_RANGE).isPresent()) return plain;

        SpacePosition best = plain;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    SpacePosition candidate = SpacePosition.normalize(frameGrid.offset(
                                    BigInteger.valueOf(dx), BigInteger.valueOf(dy), BigInteger.valueOf(dz)),
                            requested.x(), requested.y(), requested.z());
                    Optional<SpaceVec3> delta = current.deltaWithin(candidate, GRID_IMAGE_RANGE);
                    if (delta.isEmpty()) continue;
                    double distance = delta.get().x() * delta.get().x() + delta.get().y() * delta.get().y()
                            + delta.get().z() * delta.get().z();
                    if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    private void alignProjection(ServerSubLevel ship) {
        FrameLocation location = frameOf(ship);
        if (location == null) return;
        SpaceGridAddress current = location.frame().projectionGrid();
        SpaceGridAddress desired = averageFramePosition(location.frame(), ship.getUniqueId(),
                        SpaceProjection.shipPosition(ship))
                .map(center -> SpaceGrids.stableFrame(current, center,
                        SpaceConstants.SHIP_GRID_ATTACH_RANGE * 0.5)).orElse(current);
        if (desired.equals(current)) return;
        SpaceGridFrameData.Frame updated = SpaceGridFrameData.get(location.level())
                .setProjectionGrid(location.frame().id(), desired);
        PhysicsPipeline host = rawPipeline(location.level());
        Map<UUID, ServerSubLevel> ships = loadedShips();
        for (UUID shipId : updated.ships()) {
            ServerSubLevel member = ships.get(shipId);
            if (member == null || member.isRemoved()) continue;
            setLogicalPosition(member, updated, readShipPose(host, updated, member).position());
        }
    }

    private Optional<SpacePosition> averageFramePosition(SpaceGridFrameData.Frame frame,
                                                         UUID updatedShip, SpacePosition updatedPosition) {
        Map<UUID, ServerSubLevel> ships = loadedShips();
        List<SpacePosition> positions = new ArrayList<>(frame.ships().size());
        for (UUID shipId : frame.ships()) {
            ServerSubLevel member = ships.get(shipId);
            if (member == null || member.isRemoved()) return Optional.empty();
            positions.add(shipId.equals(updatedShip) ? updatedPosition : SpaceProjection.shipPosition(member));
        }
        return SpacePosition.average(positions, SpaceConstants.SHIP_GRID_ATTACH_RANGE);
    }

    private SpaceGridAddress creationGrid(org.joml.Vector3dc position) {
        SpaceGridAddress context = SpaceGrids.context();
        double nearest = 4096.0 * 4096.0;
        double nearestInContext = nearest;
        SpaceGridAddress grid = null;
        SpaceGridAddress gridInContext = null;
        for (ServerPlayer player : spaceLevel.players()) {
            double distance = player.distanceToSqr(position.x(), position.y(), position.z());
            if (distance < nearest) {
                nearest = distance;
                grid = SpaceProjection.frameGrid(player);
            }
            if (context != null && distance < nearestInContext && context.equals(SpaceGrids.of(player))) {
                nearestInContext = distance;
                gridInContext = SpaceProjection.frameGrid(player);
            }
        }
        SpaceGridDirectory directory = SpaceGridDirectory.get(spaceLevel);
        boolean playerNearby = grid != null;
        for (ServerSubLevel ship : loadedShips().values()) {
            if (directory.shipAddress(ship.getUniqueId()) == null) continue;
            double distance = ship.logicalPose().position().distanceSquared(position);
            if (!playerNearby && distance < nearest) {
                nearest = distance;
                grid = SpaceGrids.shipGrid(spaceLevel, ship);
            }
            if (context != null && distance < nearestInContext
                    && context.equals(SpaceGrids.simulationGrid(spaceLevel, ship))) {
                nearestInContext = distance;
                gridInContext = SpaceGrids.shipGrid(spaceLevel, ship);
            }
        }
        if (gridInContext != null) return gridInContext;
        if (context != null) return context;
        return grid == null ? SpaceGridAddress.ORIGIN : grid;
    }

    private void maintainFrames() {
        Map<UUID, ServerSubLevel> ships = loadedShips();
        teleportedShips.retainAll(ships.keySet());
        if (ships.isEmpty()) {
            refreshConstrainedFrames(ships);
            return;
        }
        for (FrameLocation location : activeFrames(ships.keySet())) pruneFrame(location, ships);
        refreshConstrainedFrames(ships);
        splitSpreadFrames(ships);
        mergeNearbyFrames(ships);
    }

    private Map<UUID, ServerSubLevel> loadedShips() {
        SubLevelContainer container = SubLevelContainer.getContainer(spaceLevel);
        if (container == null) return Map.of();
        Map<UUID, ServerSubLevel> ships = new HashMap<>();
        for (Object object : container.getAllSubLevels()) {
            if (object instanceof ServerSubLevel ship) ships.put(ship.getUniqueId(), ship);
        }
        return ships;
    }

    private FrameLocation pruneFrame(FrameLocation location, Map<UUID, ServerSubLevel> ships) {
        for (UUID shipId : location.frame().ships()) {
            ServerSubLevel ship = ships.get(shipId);
            if (ship == null) dropMembership(location.level(), shipId);
        }
        return SpaceGridFrameData.get(location.level()).frame(location.frame().id())
                .map(frame -> new FrameLocation(location.level(), frame))
                .orElse(null);
    }

    private void dropMembership(ServerLevel frameLevel, UUID shipId) {
        SpaceGridFrameData.get(frameLevel).removeShip(shipId);
        SpaceGridDirectory directory = SpaceGridDirectory.get(spaceLevel);
        SpaceGridAddress levelAddress = SpaceGridLevelFactory.addressOf(spaceLevel.getServer(), frameLevel.dimension());
        if (levelAddress.equals(directory.frameOwnerAddress(shipId))) directory.removeFrameOwner(shipId);
    }

    private FrameLocation allocateFrame(SpacePosition position, SpaceGridAddress projection, UUID shipId) {
        Map<UUID, ServerSubLevel> ships = loadedShips();
        ServerLevel level = frameHost(position.grid(), ships);
        return new FrameLocation(level, SpaceGridFrameData.get(level).allocate(position, projection, shipId));
    }

    private ServerLevel frameHost(SpaceGridAddress preferred, Map<UUID, ServerSubLevel> ships) {
        for (int step = 0; step < FRAME_HOST_SEARCH; step++) {
            SpaceGridAddress address = preferred.offset(BigInteger.ZERO, BigInteger.valueOf(step), BigInteger.ZERO);
            ServerLevel level = SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), address);
            for (SpaceGridFrameData.Frame frame : SpaceGridFrameData.get(level).frames()) {
                pruneFrame(new FrameLocation(level, frame), ships);
            }
            if (SpaceGridFrameData.get(level).hasFreeCell()) return level;
        }
        throw new IllegalStateException("no free physics frame cells near space grid " + preferred);
    }

    private void splitSpreadFrames(Map<UUID, ServerSubLevel> ships) {
        for (FrameLocation location : activeFrames(ships.keySet())) {
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

    private void moveToOwnFrame(ServerSubLevel ship, FrameLocation source,
                                Pose3dc targetPose, SpacePosition targetPosition) {
        PhysicsPipeline sourcePipeline = rawPipeline(source.level());
        ShipFramePose currentPose = readShipPose(sourcePipeline, source.frame(), ship);
        DetachedShip detached = new DetachedShip(currentPose, contraptionsFor(ship, sourcePipeline));
        Pose3dc pose = targetPose == null ? detached.pose().pose() : targetPose;
        SpacePosition position = targetPosition == null ? detached.pose().position() : targetPosition;
        SpaceGridAddress projection = targetPosition == null ? source.frame().projectionGrid() : position.grid();
        Optional<FrameLocation> existing = findFrame(position, projection, source.frame().id());
        if (existing.isPresent()) {
            FrameLocation target = existing.get();
            if (source.level() == target.level()) {
                shiftIntoFrame(ship, source, target, pose, position);
                return;
            }
            transferShip(ship, source, target, pose, position, detached, Set.of(ship.getUniqueId()));
            return;
        }

        ServerLevel allocationLevel = frameHost(position.grid(), loadedShips());
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
            List<FrameLocation> frames = activeFrames(ships.keySet());
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
        location = pruneFrame(location, ships);
        if (location == null) return null;
        PhysicsPipeline host = rawPipeline(location.level());
        Map<UUID, SpacePosition> positions = new HashMap<>();
        for (UUID shipId : location.frame().ships()) {
            ServerSubLevel ship = ships.get(shipId);
            if (ship == null || ship.isRemoved()) return null;
            Vector3d physical = host.readPose(ship, new Pose3d()).position();
            positions.put(shipId, SpacePhysicsFrameGrid.toLogicalPosition(location.frame().cell(),
                    location.frame().origin(), new SpaceVec3(physical.x, physical.y, physical.z)));
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

    private List<FrameLocation> activeFrames(Set<UUID> activeShips) {
        SpaceGridDirectory directory = SpaceGridDirectory.get(spaceLevel);
        Map<UUID, FrameLocation> frames = new HashMap<>();
        for (UUID shipId : activeShips) {
            SpaceGridAddress owner = directory.frameOwnerAddress(shipId);
            if (owner == null) continue;
            ServerLevel level = SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), owner);
            SpaceGridFrameData.get(level).frameForShip(shipId)
                    .ifPresent(frame -> frames.putIfAbsent(frame.id(), new FrameLocation(level, frame)));
        }
        return new ArrayList<>(frames.values());
    }

    private boolean mergeFrames(ServerSubLevel firstShip, ServerSubLevel secondShip) {
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
                    readShipPose(rawPipeline(source.level()), source.frame(), ship),
                    contraptionsFor(ship, rawPipeline(source.level())));
            transferShip(ship, source, target, detached.pose().pose(), detached.pose().position(),
                    detached, movingShips);
        }
        if (source.frame().constrained()) targetData.markConstrained(target.frame().id());
        FrameLocation merged = new FrameLocation(target.level(),
                targetData.frame(target.frame().id()).orElse(target.frame()));
        if (sameScene) reanchorWorldConstraints(merged);
        moveRopes(source.frame().id(), merged);
        return true;
    }

    private void shiftIntoFrame(ServerSubLevel ship, FrameLocation source, FrameLocation target) {
        ShipFramePose pose = readShipPose(rawPipeline(source.level()), source.frame(), ship);
        shiftIntoFrame(ship, source, target, pose.pose(), pose.position());
    }

    private void shiftIntoFrame(ServerSubLevel ship, FrameLocation source, FrameLocation target,
                                Pose3dc targetPose, SpacePosition targetPosition) {
        PhysicsPipeline scene = rawPipeline(target.level());
        ShipFramePose previous = readShipPose(scene, source.frame(), ship);
        Pose3d previousPhysical = scene.readPose(ship, new Pose3d());
        SpaceGridFrameData data = SpaceGridFrameData.get(target.level());
        SpaceVec3 physical = SpacePhysicsFrameGrid.toPhysicsPosition(
                target.frame().cell(), target.frame().origin(), targetPosition).orElseThrow();
        try {
            scene.teleport(ship, vector(physical), targetPose.orientation());
            Vector3d projected = projectionPosition(target.frame(), targetPosition);
            carryAcrossProjection(ship, target.frame(), projected);
            ship.logicalPose().position().set(projected);
            ship.logicalPose().orientation().set(targetPose.orientation());
            storePose(ship, SpaceGridDirectory.get(spaceLevel).shipAddress(ship.getUniqueId()),
                    targetPosition, new Quaterniond(targetPose.orientation()));
            data.moveShip(ship.getUniqueId(), target.frame().id());
        } catch (RuntimeException | Error failure) {
            try {
                scene.teleport(ship, previousPhysical.position(), previousPhysical.orientation());
                Vector3d sourceProjection = projectionPosition(source.frame(), previous.position());
                carryAcrossProjection(ship, source.frame(), sourceProjection);
                ship.logicalPose().position().set(sourceProjection);
                ship.logicalPose().orientation().set(previous.pose().orientation());
                storePose(ship, SpaceGridDirectory.get(spaceLevel).shipAddress(ship.getUniqueId()),
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
        PhysicsPipeline sourcePipeline = rawPipeline(source.level());
        PhysicsPipeline targetPipeline = rawPipeline(target.level());
        SpaceGridFrameData targetData = SpaceGridFrameData.get(target.level());
        List<SectionTransfer> sections = List.of();
        List<KinematicContraption> addedContraptions = new ArrayList<>();
        boolean shipAdded = true;
        boolean membershipAdded = targetData.frameForShip(ship.getUniqueId())
                .map(frame -> frame.id().equals(target.frame().id())).orElse(false);
        try {
            targetPipeline.add(ship, physicsPose(target.frame(), pose, position));
            sections = addSectionsTo(targetPipeline, ship);
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
            finishSectionTransfer(sections, targetPipeline);
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
            setLogicalPosition(ship, target.frame(), position);
            ship.logicalPose().orientation().set(pose.orientation());
            storePose(ship, directory.shipAddress(ship.getUniqueId()), position,
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
            if (sectionsMoved) restoreSectionTransfer(sections, failure);
            if (shipRemoved) {
                try {
                    sourcePipeline.add(ship, physicsPose(source.frame(), detached.pose().pose(),
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
                    Vector3d sourceProjection = projectionPosition(source.frame(), detached.pose().position());
                    carryAcrossProjection(ship, source.frame(), sourceProjection);
                    ship.logicalPose().position().set(sourceProjection);
                    ship.logicalPose().orientation().set(detached.pose().pose().orientation());
                    storePose(ship, directory.shipAddress(ship.getUniqueId()), detached.pose().position(),
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
        migrateConstraints(ship, target, movingShips);
    }

    private void migrateConstraints(ServerSubLevel ship, FrameLocation target, Set<UUID> movingShips) {
        boolean constrained = false;
        for (WorldConstraint constraint : List.copyOf(worldConstraints)) {
            if (constraint.ship != ship) continue;
            try {
                constraint.moveTo(rawPipeline(target.level()), target.level());
                constrained |= constraint.isLive();
            } catch (RuntimeException | Error failure) {
                constraint.retire();
                Astronautics.LOGGER.warn("Could not move a world constraint with ship {}", ship.getUniqueId(), failure);
            }
        }

        SpaceGridAddress targetAddress = SpaceGridLevelFactory.addressOf(
                spaceLevel.getServer(), target.level().dimension());
        SpaceGridDirectory directory = SpaceGridDirectory.get(spaceLevel);
        for (Map.Entry<ShipConstraint, Set<UUID>> entry : List.copyOf(shipConstraints.entrySet())) {
            Set<UUID> ids = entry.getValue();
            if (ids.stream().noneMatch(movingShips::contains)) continue;
            boolean pending = movingShips.stream().anyMatch(id -> !isShipOwnedBy(directory, id, targetAddress));
            if (pending) continue;

            ShipConstraint constraint = entry.getKey();
            if (ids.stream().allMatch(id -> isShipOwnedBy(directory, id, targetAddress))) {
                try {
                    constraint.moveTo(rawPipeline(target.level()), target.level());
                    constrained |= constraint.isLive();
                } catch (RuntimeException | Error failure) {
                    constraint.retire();
                    Astronautics.LOGGER.warn("Could not move a ship constraint into physics frame {}",
                            target.frame().id(), failure);
                }
            } else {
                constraint.retire();
            }
        }
        if (constrained) SpaceGridFrameData.get(target.level()).markConstrained(target.frame().id());
    }

    private static boolean isShipOwnedBy(SpaceGridDirectory directory, UUID shipId, SpaceGridAddress address) {
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
        rollbackSections(targetPipeline, sections, failure);
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
    private record SectionTransfer(long key, int x, int y, int z, PhysicsPipeline previous,
                                  LevelChunkSection section) {}

    private Optional<SpacePosition> mergedOrigin(FrameLocation target, FrameLocation source,
                                                  Map<UUID, ServerSubLevel> ships) {
        List<SpacePosition> positions = new ArrayList<>();
        PhysicsPipeline targetPipeline = rawPipeline(target.level());
        PhysicsPipeline sourcePipeline = rawPipeline(source.level());
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
        PhysicsPipeline pipeline = rawPipeline(location.level());
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
                pipeline.teleport(ships.get(entry.getKey()), vector(translatedPositions.get(entry.getKey())),
                        entry.getValue().pose().orientation());
            }

            SpaceGridFrameData.Frame updated = data.setOrigin(location.frame().id(), origin);
            originChanged = true;
            for (Map.Entry<UUID, ShipFramePose> entry : poses.entrySet()) {
                ServerSubLevel ship = ships.get(entry.getKey());
                ShipFramePose pose = entry.getValue();
                ship.logicalPose().position().set(projectionPosition(updated, pose.position()));
                ship.logicalPose().orientation().set(pose.pose().orientation());
                storePose(ship, SpaceGridDirectory.get(spaceLevel).shipAddress(ship.getUniqueId()),
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
                ship.logicalPose().position().set(projectionPosition(location.frame(), previous.position()));
                ship.logicalPose().orientation().set(previous.pose().orientation());
                try {
                    storePose(ship, SpaceGridDirectory.get(spaceLevel).shipAddress(shipId),
                            previous.position(), new Quaterniond(previous.pose().orientation()));
                } catch (RuntimeException | Error rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }

        SpaceGridFrameData.Frame updated = data.frame(location.frame().id()).orElseThrow();
        FrameLocation rebased = new FrameLocation(location.level(), updated);
        moveRopes(location.frame().id(), rebased);
        reanchorWorldConstraints(rebased);
        return rebased;
    }

    private ShipFramePose readShipPose(PhysicsPipeline pipeline, SpaceGridFrameData.Frame frame,
                                       ServerSubLevel ship) {
        Pose3d physical = pipeline.readPose(ship, new Pose3d());
        SpacePosition position = SpacePhysicsFrameGrid.toLogicalPosition(frame.cell(), frame.origin(),
                new SpaceVec3(physical.position().x, physical.position().y, physical.position().z));
        Pose3d logical = new Pose3d(new Vector3d(position.x(), position.y(), position.z()),
                new Quaterniond(physical.orientation()), new Vector3d(ship.logicalPose().rotationPoint()),
                new Vector3d(ship.logicalPose().scale()));
        logical.position().set(projectionPosition(frame, position));
        return new ShipFramePose(position, logical,
                pipeline.getLinearVelocity(ship, new Vector3d()),
                pipeline.getAngularVelocity(ship, new Vector3d()));
    }

    private record ShipFramePose(SpacePosition position, Pose3d pose,
                                 Vector3d linearVelocity, Vector3d angularVelocity) {}

    private boolean needsReframe(SpaceGridFrameData.Frame frame, SpacePosition position) {
        Optional<SpaceVec3> relative = frame.origin().deltaWithin(position,
                Math.sqrt(3.0) * SpacePhysicsFrameGrid.SPACING * 0.5);
        if (relative.isEmpty()) return true;
        SpaceVec3 delta = relative.get();
        return Math.abs(delta.x()) > FRAME_REBASE_DISTANCE
                || Math.abs(delta.y()) > FRAME_REBASE_DISTANCE
                || Math.abs(delta.z()) > FRAME_REBASE_DISTANCE;
    }

    private void rebaseFrame(SpaceGridAddress frameOwner, SpaceGridFrameData.Frame frame) {
        Map<UUID, ServerSubLevel> ships = loadedShips();
        FrameLocation location = pruneFrame(new FrameLocation(
                SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), frameOwner), frame), ships);
        if (location == null) return;
        Map<UUID, SpacePosition> positions = framePositions(location, ships);
        if (positions == null) return;
        center(List.copyOf(positions.values())).ifPresent(origin -> rebaseFrame(location, origin, ships));
    }

    private boolean rebaseForTeleport(ServerSubLevel teleportedShip, SpaceGridAddress frameOwner,
                                   SpaceGridFrameData.Frame frame, SpacePosition targetPosition) {
        Map<UUID, ServerSubLevel> ships = loadedShips();
        ServerLevel level = SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), frameOwner);
        FrameLocation location = pruneFrame(new FrameLocation(level, frame), ships);
        if (location == null) throw new IllegalStateException("teleported ship lost its physics frame");
        frame = location.frame();
        PhysicsPipeline host = rawPipeline(level);
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

    private void storePose(ServerSubLevel ship, SpaceGridAddress previousOwner,
                           SpacePosition position, Quaterniond orientation) {
        SpaceGridDirectory directory = SpaceGridDirectory.get(spaceLevel);
        SpaceGridAddress nextOwner = position.grid();
        SpaceGridAddress frameOwner = directory.frameOwnerAddress(ship.getUniqueId());
        PhysicsPipeline host = frameOwner == null ? null : hostPipeline(ship);
        Vector3d linear = host == null ? new Vector3d() : host.getLinearVelocity(ship, new Vector3d());
        Vector3d angular = host == null ? new Vector3d() : host.getAngularVelocity(ship, new Vector3d());
        SpaceGridShipState state = new SpaceGridShipState(
                position.x(), position.y(), position.z(),
                orientation.x, orientation.y, orientation.z, orientation.w,
                ship.logicalPose().rotationPoint().x, ship.logicalPose().rotationPoint().y,
                ship.logicalPose().rotationPoint().z,
                linear.x, linear.y, linear.z, angular.x, angular.y, angular.z);
        if (!nextOwner.equals(previousOwner)) {
            ServerLevel previousLevel = SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), previousOwner);
            SpaceGridShipData.get(previousLevel).remove(ship.getUniqueId());
            directory.setShipAddress(ship.getUniqueId(), nextOwner);
        }
        ServerLevel stateLevel = SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), nextOwner);
        SpaceGridShipData.get(stateLevel).put(ship.getUniqueId(), state);
    }

    private SpaceGridFrameData.Frame frame(UUID shipId, SpaceGridAddress frameOwner) {
        ServerLevel level = SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), frameOwner);
        return SpaceGridFrameData.get(level).frameForShip(shipId)
                .orElseThrow(() -> new IllegalStateException("ship has no persisted physics frame: " + shipId));
    }

    private PhysicsPipeline hostPipeline(ServerSubLevel ship) {
        SpaceGridAddress owner = SpaceGridDirectory.get(spaceLevel).frameOwnerAddress(ship.getUniqueId());
        if (owner == null) return null;
        ServerLevel level = SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), owner);
        PhysicsPipeline wrapped = SubLevelPhysicsSystem.require(level).getPipeline();
        return wrapped instanceof SpaceGridHostPipeline host ? host.astronautics$rawPipeline() : wrapped;
    }

    private PhysicsPipeline rawPipeline(ServerLevel level) {
        PhysicsPipeline wrapped = SubLevelPhysicsSystem.require(level).getPipeline();
        return wrapped instanceof SpaceGridHostPipeline host ? host.astronautics$rawPipeline() : wrapped;
    }

    private PhysicsPipeline sectionPipeline(int chunkX, int chunkZ) {
        SubLevelContainer container = SubLevelContainer.getContainer(spaceLevel);
        if (container == null || !container.inBounds(chunkX, chunkZ)) return pipeline;
        LevelPlot plot = container.getPlot(chunkX, chunkZ);
        if (plot == null || !(plot.getSubLevel() instanceof ServerSubLevel ship)) return null;
        return hostPipeline(ship);
    }

    private void addSection(LevelChunkSection section, int x, int y, int z, boolean uploadDataIfGlobal) {
        PhysicsPipeline owner = sectionPipeline(x, z);
        if (owner == null) {
            Astronautics.LOGGER.warn("skipping space plot section {} {} {} without a physics frame", x, y, z);
            return;
        }
        if (owner != pipeline) {
            PhysicsPipeline previous = sectionOwners.put(SectionPos.asLong(x, y, z), owner);
            if (previous != null && previous != owner) previous.handleChunkSectionRemoval(x, y, z);
        }
        owner.handleChunkSectionAddition(section, x, y, z, uploadDataIfGlobal);
    }

    private void removeSection(int x, int y, int z) {
        PhysicsPipeline owner = sectionOwners.remove(SectionPos.asLong(x, y, z));
        if (owner != null) {
            owner.handleChunkSectionRemoval(x, y, z);
            return;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(spaceLevel);
        if (container == null || !container.inBounds(x, z)) pipeline.handleChunkSectionRemoval(x, y, z);
    }

    private List<SectionTransfer> addSectionsTo(PhysicsPipeline target, ServerSubLevel ship) {
        List<SectionTransfer> transfers = new ArrayList<>();
        try {
            for (PlotChunkHolder holder : ship.getPlot().getLoadedChunks()) {
                LevelChunk chunk = holder.getChunk();
                ChunkPos pos = chunk.getPos();
                LevelChunkSection[] sections = chunk.getSections();
                for (int index = 0; index < chunk.getSectionsCount(); index++) {
                    int sectionY = chunk.getSectionYFromSectionIndex(index);
                    long key = SectionPos.asLong(pos.x, sectionY, pos.z);
                    PhysicsPipeline previous = sectionOwners.get(key);
                    if (previous == target || previous == null && sections[index].hasOnlyAir()) continue;
                    transfers.add(new SectionTransfer(key, pos.x, sectionY, pos.z, previous, sections[index]));
                    target.handleChunkSectionAddition(sections[index], pos.x, sectionY, pos.z, true);
                }
            }
        } catch (RuntimeException | Error failure) {
            rollbackSections(target, transfers, failure);
            throw failure;
        }
        return transfers;
    }

    private static void rollbackSections(PhysicsPipeline target, List<SectionTransfer> transfers,
                                         Throwable failure) {
        for (int i = transfers.size() - 1; i >= 0; i--) {
            SectionTransfer transfer = transfers.get(i);
            try {
                target.handleChunkSectionRemoval(transfer.x(), transfer.y(), transfer.z());
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    private void finishSectionTransfer(List<SectionTransfer> transfers, PhysicsPipeline target) {
        List<SectionTransfer> removed = new ArrayList<>();
        try {
            for (SectionTransfer transfer : transfers) {
                if (transfer.previous() != null) {
                    removed.add(transfer);
                    transfer.previous().handleChunkSectionRemoval(transfer.x(), transfer.y(), transfer.z());
                }
            }
            for (SectionTransfer transfer : transfers) {
                sectionOwners.put(transfer.key(), target);
            }
        } catch (RuntimeException | Error failure) {
            restoreRemovedSections(removed, failure);
            for (SectionTransfer transfer : transfers) {
                if (transfer.previous() == null) sectionOwners.remove(transfer.key());
                else sectionOwners.put(transfer.key(), transfer.previous());
            }
            throw failure;
        }
    }

    private void restoreSectionTransfer(List<SectionTransfer> transfers, Throwable failure) {
        for (int i = transfers.size() - 1; i >= 0; i--) {
            SectionTransfer transfer = transfers.get(i);
            if (transfer.previous() == null) {
                sectionOwners.remove(transfer.key());
                continue;
            }
            try {
                transfer.previous().handleChunkSectionAddition(transfer.section(), transfer.x(),
                        transfer.y(), transfer.z(), true);
                sectionOwners.put(transfer.key(), transfer.previous());
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    private static void restoreRemovedSections(List<SectionTransfer> transfers, Throwable failure) {
        for (int i = transfers.size() - 1; i >= 0; i--) {
            SectionTransfer transfer = transfers.get(i);
            try {
                transfer.previous().handleChunkSectionAddition(transfer.section(), transfer.x(),
                        transfer.y(), transfer.z(), true);
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    private void releaseSections(ServerSubLevel ship) {
        SubLevelContainer container = SubLevelContainer.getContainer(spaceLevel);
        if (container == null) return;
        LevelPlot plot = ship.getPlot();
        var iterator = sectionOwners.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long key = entry.getKey();
            if (container.getPlot(SectionPos.x(key), SectionPos.z(key)) != plot) continue;
            entry.getValue().handleChunkSectionRemoval(SectionPos.x(key), SectionPos.y(key), SectionPos.z(key));
            iterator.remove();
        }
    }

    private PhysicsPipeline contraptionPipeline(KinematicContraption contraption) {
        if (Sable.HELPER.getContaining(spaceLevel, contraption.sable$getPosition()) instanceof ServerSubLevel ship) {
            PhysicsPipeline host = hostPipeline(ship);
            if (host != null) return host;
        }
        return pipeline;
    }

    private List<KinematicContraption> contraptionsFor(ServerSubLevel ship, PhysicsPipeline source) {
        List<KinematicContraption> attached = new ArrayList<>();
        for (Map.Entry<KinematicContraption, PhysicsPipeline> entry : contraptionOwners.entrySet()) {
            if (entry.getValue() == source
                    && Sable.HELPER.getContaining(spaceLevel, entry.getKey().sable$getPosition()) == ship) {
                attached.add(entry.getKey());
            }
        }
        return attached;
    }

    private static Pose3d physicsPose(SpaceGridFrameData.Frame frame, Pose3dc logical, SpacePosition position) {
        SpaceVec3 translated = SpacePhysicsFrameGrid.toPhysicsPosition(frame.cell(), frame.origin(), position)
                .orElseThrow(() -> new IllegalStateException("ship position lies outside its physics frame"));
        return new Pose3d(vector(translated), new Quaterniond(logical.orientation()),
                new Vector3d(logical.rotationPoint()), new Vector3d(logical.scale()));
    }

    private static Pose3d pose(SpaceGridShipState state, org.joml.Vector3dc scale) {
        return new Pose3d(new Vector3d(state.x(), state.y(), state.z()),
                new Quaterniond(state.rotationX(), state.rotationY(), state.rotationZ(), state.rotationW()),
                new Vector3d(state.rotationPointX(), state.rotationPointY(), state.rotationPointZ()),
                new Vector3d(scale));
    }

    private static SpaceGridShipState state(SpacePosition position, Pose3dc pose,
                                            Vector3d linear, Vector3d angular) {
        return new SpaceGridShipState(
                position.x(), position.y(), position.z(),
                pose.orientation().x(), pose.orientation().y(), pose.orientation().z(), pose.orientation().w(),
                pose.rotationPoint().x(), pose.rotationPoint().y(), pose.rotationPoint().z(),
                linear.x, linear.y, linear.z, angular.x, angular.y, angular.z);
    }

    private static Vector3d vector(SpaceVec3 vector) {
        return new Vector3d(vector.x(), vector.y(), vector.z());
    }

    private static Vector3d projectionPosition(SpaceGridFrameData.Frame frame, SpacePosition position) {
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

    private record FrameLocation(ServerLevel level, SpaceGridFrameData.Frame frame) {}

    private RopeHandle createRope(RopePhysicsObject rope) {
        Vector3d first = rope.getPoints().getFirst();
        SpaceGridAddress grid = creationGrid(first);
        SpacePosition start = SpacePosition.normalize(grid, first.x, first.y, first.z);
        FrameLocation frame = null;
        for (FrameLocation candidate : activeFrames(loadedShips().keySet())) {
            if (SpacePhysicsFrameGrid.toPhysicsPosition(candidate.frame().cell(), candidate.frame().origin(), start)
                    .isPresent()) {
                frame = candidate;
                break;
            }
        }
        GridRope handle = new GridRope(rope.getCollisionRadius(), grid, frame);
        handle.build(rope.getPoints());
        return handle;
    }

    private void moveRopes(UUID frameId, FrameLocation target) {
        List<GridRope> ropes = framedRopes.remove(frameId);
        if (ropes == null) return;
        for (GridRope rope : ropes) rope.relocate(target);
    }

    private FrameLocation frameOf(ServerSubLevel ship) {
        SpaceGridAddress owner = SpaceGridDirectory.get(spaceLevel).frameOwnerAddress(ship.getUniqueId());
        if (owner == null) return null;
        return new FrameLocation(SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), owner),
                frame(ship.getUniqueId(), owner));
    }

    private boolean isConstraintSceneLoaded(ServerLevel level) {
        return level != null && SpaceGridLevelFactory.find(spaceLevel.getServer(), level.dimension()) == level;
    }

    private static Object[] copyConstraintArgs(Object[] args) {
        if (args == null) return new Object[0];
        Object[] copy = args.clone();
        for (int i = 0; i < copy.length; i++) {
            if (copy[i] instanceof Vector3dc vector) copy[i] = new Vector3d(vector);
            else if (copy[i] instanceof org.joml.Quaterniondc quaternion) {
                copy[i] = new Quaterniond(quaternion);
            } else if (copy[i] instanceof Object[] values) copy[i] = values.clone();
        }
        return copy;
    }

    private final class ShipConstraint implements InvocationHandler {
        private final Method addMethod;
        private final ServerSubLevel bodyA;
        private final ServerSubLevel bodyB;
        private final PhysicsConstraintConfiguration<?> config;
        private final Map<String, Setting> settings = new LinkedHashMap<>();
        private PhysicsConstraintHandle inner;
        private PhysicsConstraintHandle handle;
        private ServerLevel ownerLevel;
        private boolean active = true;

        private ShipConstraint(Method addMethod, ServerSubLevel bodyA,
                               ServerSubLevel bodyB, PhysicsConstraintConfiguration<?> config,
                               PhysicsConstraintHandle inner, ServerLevel ownerLevel) {
            this.addMethod = addMethod;
            this.bodyA = bodyA;
            this.bodyB = bodyB;
            this.config = config;
            this.inner = inner;
            this.ownerLevel = ownerLevel;
            handle = createHandle();
        }

        private boolean isLive() {
            if (!active) return false;
            if (!isConstraintSceneLoaded(ownerLevel)) {
                active = false;
                return false;
            }
            if (inner.isValid()) return true;
            active = false;
            return false;
        }

        private void moveTo(PhysicsPipeline host, ServerLevel level) {
            if (!active) return;
            if (!isConstraintSceneLoaded(ownerLevel)) {
                active = false;
                return;
            }

            PhysicsConstraintHandle replacement = createNative(host);
            try {
                for (Setting setting : settings.values()) applyTo(replacement, setting.method(), setting.args());
            } catch (RuntimeException | Error failure) {
                if (isConstraintSceneLoaded(level) && replacement.isValid()) replacement.remove();
                throw failure;
            }

            PhysicsConstraintHandle previous = inner;
            if (isConstraintSceneLoaded(ownerLevel) && previous.isValid()) previous.remove();
            inner = replacement;
            ownerLevel = level;
        }

        private PhysicsConstraintHandle createNative(PhysicsPipeline host) {
            return (PhysicsConstraintHandle) call(() -> SpaceGridPhysicsPipelineProvider.addConstraint(
                    host, addMethod, new Object[]{bodyA, bodyB, config}));
        }

        private PhysicsConstraintHandle createHandle() {
            List<Class<?>> types = new ArrayList<>();
            for (Class<?> type : List.of(FixedConstraintHandle.class, FreeConstraintHandle.class,
                    RotaryConstraintHandle.class, GenericConstraintHandle.class)) {
                if (type.isInstance(inner)) types.add(type);
            }
            return (PhysicsConstraintHandle) Proxy.newProxyInstance(PhysicsConstraintHandle.class.getClassLoader(),
                    types.toArray(Class<?>[]::new), this);
        }

        private void retire() {
            if (!active) return;
            if (isConstraintSceneLoaded(ownerLevel) && inner.isValid()) inner.remove();
            active = false;
            shipConstraints.remove(this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "remove" -> {
                    retire();
                    return null;
                }
                case "isValid" -> {
                    return isLive();
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "toString" -> {
                    return "SpaceConstraint[" + (active ? inner : "removed") + "]";
                }
            }

            if (!isLive()) throw new IllegalStateException("constraint physics scene is no longer active");
            Object[] stored = copyConstraintArgs(args);
            if (method.getName().startsWith("set") || method.getName().equals("lockAxes")) {
                String key = stored.length > 0 && stored[0] instanceof Enum<?> axis
                        ? method.getName() + ":" + axis.name() : method.getName();
                settings.put(key, new Setting(method, stored));
            }
            return applyTo(inner, method, stored);
        }

        private Object applyTo(PhysicsConstraintHandle target, Method method, Object[] args) {
            return call(() -> {
                try {
                    return method.invoke(target, args);
                } catch (InvocationTargetException exception) {
                    throw exception.getCause();
                }
            });
        }

        private record Setting(Method method, Object[] args) {}
    }

    private final class WorldConstraint implements InvocationHandler {
        private final ServerSubLevel ship;
        private final boolean worldFirst;
        private final Method addMethod;
        private final PhysicsConstraintConfiguration<?> config;
        private final SpacePosition anchor;
        private final Map<String, Setting> settings = new LinkedHashMap<>();
        private PhysicsConstraintHandle inner;
        private PhysicsConstraintHandle handle;
        private ServerLevel ownerLevel;
        private boolean active = true;

        private WorldConstraint(ServerSubLevel ship, boolean worldFirst, Method addMethod,
                                PhysicsConstraintConfiguration<?> config) {
            this.ship = ship;
            this.worldFirst = worldFirst;
            this.addMethod = addMethod;
            this.config = config;
            this.anchor = worldPosition(ship, worldAnchor(config, worldFirst));
        }

        private void build(PhysicsPipeline host, ServerLevel level) {
            PhysicsConstraintHandle created = createNative(host);
            for (Setting setting : settings.values()) applyTo(created, setting.method(), setting.args());
            inner = created;
            ownerLevel = level;
            if (handle == null) handle = createHandle();
        }

        private boolean isLive() {
            if (!active) return false;
            if (!isConstraintSceneLoaded(ownerLevel)) {
                active = false;
                return false;
            }
            if (inner.isValid()) return true;
            active = false;
            return false;
        }

        private void moveTo(PhysicsPipeline host, ServerLevel level) {
            if (!active) return;
            if (level == ownerLevel) {
                rebuild(host, level);
                return;
            }
            if (!isConstraintSceneLoaded(ownerLevel)) {
                active = false;
                return;
            }
            PhysicsConstraintHandle previous = inner;
            ServerLevel previousLevel = ownerLevel;
            PhysicsConstraintHandle replacement = createNative(host);
            try {
                for (Setting setting : settings.values()) applyTo(replacement, setting.method(), setting.args());
                if (isConstraintSceneLoaded(previousLevel) && previous.isValid()) previous.remove();
            } catch (RuntimeException | Error failure) {
                if (isConstraintSceneLoaded(level) && replacement.isValid()) replacement.remove();
                throw failure;
            }
            inner = replacement;
            ownerLevel = level;
        }

        private void rebuild(PhysicsPipeline host, ServerLevel level) {
            if (!isLive()) return;
            PhysicsConstraintHandle previous = inner;
            PhysicsConstraintHandle replacement = createNative(host);
            try {
                for (Setting setting : settings.values()) applyTo(replacement, setting.method(), setting.args());
                if (previous.isValid()) previous.remove();
            } catch (RuntimeException | Error failure) {
                if (isConstraintSceneLoaded(level) && replacement.isValid()) replacement.remove();
                throw failure;
            }
            inner = replacement;
            ownerLevel = level;
        }

        private PhysicsConstraintHandle createNative(PhysicsPipeline host) {
            PhysicsConstraintConfiguration<?> physical = withWorldAnchor(config, worldFirst,
                    physicsPosition(ship, anchor));
            Object[] args = worldFirst ? new Object[]{null, ship, physical} : new Object[]{ship, null, physical};
            return (PhysicsConstraintHandle) call(() ->
                    SpaceGridPhysicsPipelineProvider.addConstraint(host, addMethod, args));
        }

        private void retire() {
            if (!active) return;
            worldConstraints.remove(this);
            if (isConstraintSceneLoaded(ownerLevel) && inner.isValid()) inner.remove();
            active = false;
        }

        private PhysicsConstraintHandle createHandle() {
            List<Class<?>> types = new ArrayList<>();
            for (Class<?> type : List.of(FixedConstraintHandle.class, FreeConstraintHandle.class,
                    RotaryConstraintHandle.class, GenericConstraintHandle.class)) {
                if (type.isInstance(inner)) types.add(type);
            }
            return (PhysicsConstraintHandle) Proxy.newProxyInstance(PhysicsConstraintHandle.class.getClassLoader(),
                    types.toArray(Class<?>[]::new), this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "remove" -> {
                    retire();
                    return null;
                }
                case "isValid" -> {
                    return isLive();
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "toString" -> {
                    return "WorldConstraint[" + inner + "]";
                }
            }
            if (!isLive()) throw new IllegalStateException("constraint physics scene is no longer active");
            Object[] stored = copyConstraintArgs(args);
            if (isWorldFrame(method)) stored[0] = worldPosition(ship, (Vector3dc) args[0]);
            if (method.getName().startsWith("set") || method.getName().equals("lockAxes")) {
                String key = stored.length > 0 && stored[0] instanceof Enum<?> axis
                        ? method.getName() + ":" + axis.name() : method.getName();
                settings.put(key, new Setting(method, stored));
            }
            return applyTo(inner, method, stored);
        }

        private Object applyTo(PhysicsConstraintHandle target, Method method, Object[] args) {
            Object[] actual = args;
            if (isWorldFrame(method)) {
                actual = args.clone();
                actual[0] = physicsPosition(ship, (SpacePosition) args[0]);
            }
            Object[] finalArgs = actual;
            return call(() -> {
                try {
                    return method.invoke(target, finalArgs);
                } catch (InvocationTargetException exception) {
                    throw exception.getCause();
                }
            });
        }

        private boolean isWorldFrame(Method method) {
            return method.getName().equals(worldFirst ? "setFrame1" : "setFrame2");
        }

        private record Setting(Method method, Object[] args) {}
    }

    private static Object call(ThrowingSupplier action) {
        try {
            return action.get();
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new IllegalStateException(throwable);
        }
    }

    private interface ThrowingSupplier {
        Object get() throws Throwable;
    }

    private final class GridRope implements RopeHandle {
        private final double radius;
        private final SpaceGridAddress grid;
        private final List<Vector3d> points = new ArrayList<>();
        private final Map<AttachmentPoint, RopeAttachment> attachments = new EnumMap<>(AttachmentPoint.class);
        private FrameLocation frame;
        private RopeHandle inner;
        private double firstSegmentLength = -1.0;

        private GridRope(double radius, SpaceGridAddress grid, FrameLocation frame) {
            this.radius = radius;
            this.grid = grid;
            this.frame = frame;
        }

        private void build(List<Vector3d> logical) {
            points.clear();
            List<Vector3d> physical = new ArrayList<>(logical.size());
            for (Vector3d point : logical) {
                points.add(new Vector3d(point));
                physical.add(toScene(point));
            }
            PhysicsPipeline scene = frame == null ? pipeline : rawPipeline(frame.level());
            inner = scene.addRope(new RopePhysicsObject(physical, radius));
            if (frame != null) framedRopes.computeIfAbsent(frame.frame().id(), id -> new ArrayList<>()).add(this);
        }

        private void relocate(FrameLocation target) {
            refreshPoints();
            inner.remove();
            untrack();
            frame = target;
            build(new ArrayList<>(points));
            if (firstSegmentLength >= 0.0) inner.setFirstSegmentLength(firstSegmentLength);
            for (Map.Entry<AttachmentPoint, RopeAttachment> entry : attachments.entrySet()) {
                RopeAttachment attachment = entry.getValue();
                if (attachment.subLevel() != null && !inFrame(attachment.subLevel())) continue;
                inner.setAttachment(entry.getKey(), attachment.subLevel() == null
                        ? toScene(attachment.location()) : attachment.location(), attachment.subLevel());
            }
        }

        private boolean inFrame(ServerSubLevel ship) {
            FrameLocation shipFrame = frameOf(ship);
            return frame != null && shipFrame != null && shipFrame.frame().id().equals(frame.frame().id());
        }

        private void untrack() {
            if (frame == null) return;
            List<GridRope> ropes = framedRopes.get(frame.frame().id());
            if (ropes != null) ropes.remove(this);
        }

        private void refreshPoints() {
            List<Vector3d> physical = new ArrayList<>(points.size());
            for (int i = 0; i < points.size(); i++) physical.add(new Vector3d());
            inner.readPose(physical);
            for (int i = 0; i < points.size(); i++) points.get(i).set(fromScene(physical.get(i)));
        }

        private Vector3d toScene(Vector3dc logical) {
            if (frame == null) return new Vector3d(logical);
            SpacePosition position = SpacePosition.normalize(grid, logical.x(), logical.y(), logical.z());
            SpaceVec3 offset = frame.frame().origin().deltaWithin(position, ROPE_RANGE).orElseThrow();
            SpacePhysicsFrameGrid.Cell cell = frame.frame().cell();
            return new Vector3d(SpacePhysicsFrameGrid.centerX(cell) + offset.x(),
                    SpacePhysicsFrameGrid.centerY(cell) + offset.y(),
                    SpacePhysicsFrameGrid.centerZ(cell) + offset.z());
        }

        private Vector3d fromScene(Vector3dc physical) {
            if (frame == null) return new Vector3d(physical);
            SpacePosition position = SpacePhysicsFrameGrid.toLogicalPosition(frame.frame().cell(),
                    frame.frame().origin(), new SpaceVec3(physical.x(), physical.y(), physical.z()));
            SpaceVec3 local = new SpacePosition(grid, 0.0, 0.0, 0.0)
                    .deltaWithin(position, ROPE_RANGE).orElseThrow();
            return new Vector3d(local.x(), local.y(), local.z());
        }

        @Override
        public void readPose(List<Vector3d> dest) {
            List<Vector3d> physical = new ArrayList<>(dest.size());
            for (int i = 0; i < dest.size(); i++) physical.add(new Vector3d());
            inner.readPose(physical);
            points.clear();
            for (int i = 0; i < dest.size(); i++) {
                Vector3d logical = fromScene(physical.get(i));
                dest.get(i).set(logical);
                points.add(logical);
            }
        }

        @Override
        public void remove() {
            inner.remove();
            untrack();
        }

        @Override
        public void setFirstSegmentLength(double length) {
            firstSegmentLength = length;
            inner.setFirstSegmentLength(length);
        }

        @Override
        public void removeFirstPoint() {
            inner.removeFirstPoint();
            if (!points.isEmpty()) points.removeFirst();
        }

        @Override
        public void addPoint(Vector3dc position) {
            inner.addPoint(toScene(position));
            points.addFirst(new Vector3d(position));
        }

        @Override
        public void setAttachment(AttachmentPoint attachmentPoint, Vector3dc location, ServerSubLevel subLevel) {
            if (subLevel != null && hostPipeline(subLevel) != null && !inFrame(subLevel)) {
                RopeAttachment other = attachments.get(attachmentPoint == AttachmentPoint.START
                        ? AttachmentPoint.END : AttachmentPoint.START);
                if (other != null && other.subLevel() != null && hostPipeline(other.subLevel()) != null
                        && !mergeFrames(other.subLevel(), subLevel)) {
                    Astronautics.LOGGER.warn("rope between ships {} and {} cannot share a physics frame",
                            other.subLevel().getUniqueId(), subLevel.getUniqueId());
                    return;
                }
                if (!inFrame(subLevel)) relocate(frameOf(subLevel));
            }
            if (subLevel != null && frame != null) {
                SpaceGridFrameData.get(frame.level()).markConstrained(frame.frame().id());
            }
            attachments.put(attachmentPoint, new RopeAttachment(new Vector3d(location), subLevel));
            inner.setAttachment(attachmentPoint, subLevel == null ? toScene(location) : location, subLevel);
        }

        @Override
        public void wakeUp() {
            inner.wakeUp();
        }
    }

    private record RopeAttachment(Vector3d location, ServerSubLevel subLevel) {}
}
