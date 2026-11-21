package com.encdata.astronautics.client;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.network.ReentryStateStore;
import com.encdata.astronautics.space.ReentryState;
import com.encdata.astronautics.space.SpaceCatalog;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ReentryClientRenderer {
    private static final ResourceLocation PLASMA = ResourceLocation.fromNamespaceAndPath(
            Astronautics.MOD_ID, "reentry_plasma");
    private static final ResourceLocation HULL_PLASMA = ResourceLocation.fromNamespaceAndPath(
            Astronautics.MOD_ID, "reentry_hull");
    private static final double SURFACE_CLEARANCE = 0.045;
    private static final double WAKE_CELL_SIZE = 1.2;
    private static final double WAKE_LENGTH = 32.0;
    private static final long HULL_REFRESH_TICKS = 40L;
    private static final Map<ClientSubLevel, HullData> HULLS = new IdentityHashMap<>();
    private static final Map<ClientSubLevel, CachedEffect> EFFECTS = new IdentityHashMap<>();

    private ReentryClientRenderer() {}

    public static void renderPlasma(Camera camera, MultiBufferSource.BufferSource buffers) {
        if (camera.getEntity() == null || !(camera.getEntity().level() instanceof ClientLevel level)
                || !SpaceCatalog.isPlanetDimension(level.dimension())) {
            HULLS.clear();
            EFFECTS.clear();
            return;
        }

        ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            HULLS.clear();
            EFFECTS.clear();
            return;
        }

        List<ClientSubLevel> ships = container.getAllSubLevels();
        Map<UUID, ReentryState> reentryStates = ReentryStateStore.states();
        HULLS.keySet().retainAll(ships);
        EFFECTS.keySet().retainAll(ships);
        RenderType hullType = VeilRenderType.get(HULL_PLASMA);
        if (hullType == null) return;
        RenderType wakeType = VeilRenderType.get(PLASMA);

        Minecraft minecraft = Minecraft.getInstance();
        Vec3 cameraPosition = camera.getPosition();
        double time = level.getGameTime() + minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        PoseStack.Pose pose = new PoseStack().last();
        VertexConsumer hullOut = buffers.getBuffer(hullType);
        List<PendingWake> pendingWake = new ArrayList<>();
        for (ClientSubLevel ship : ships) {
            if (ship.isRemoved() || !ship.isFinalized()) continue;
            var reentryState = reentryStates.get(ship.getUniqueId());
            if (reentryState == null || !reentryState.active() || !reentryState.planetDimension()) {
                HULLS.remove(ship);
                EFFECTS.remove(ship);
                continue;
            }

            var bounds = ship.getPlot().getBoundingBox();
            if (bounds.minX() > bounds.maxX() || bounds.minY() > bounds.maxY()
                    || bounds.minZ() > bounds.maxZ()) continue;
            HullBounds hullBounds = new HullBounds(bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX() + 1.0, bounds.maxY() + 1.0, bounds.maxZ() + 1.0);
            Vector3d localCenter = new Vector3d((hullBounds.minX() + hullBounds.maxX()) * 0.5,
                    (hullBounds.minY() + hullBounds.maxY()) * 0.5,
                    (hullBounds.minZ() + hullBounds.maxZ()) * 0.5);
            Pose3dc shipPose = ship.renderPose(minecraft.getTimer().getGameTimeDeltaPartialTick(true));
            Vector3d worldCenter = shipPose.transformPosition(localCenter, new Vector3d());
            Vec3 center = new Vec3(worldCenter.x, worldCenter.y, worldCenter.z);
            if (!ReentryRenderLimits.withinRenderRange(cameraPosition, center)) continue;

            Vector3d velocity = Sable.HELPER.getVelocity(ship.getLevel(), ship, localCenter, new Vector3d());
            shipPose.orientation().transformInverse(velocity);
            double speed = velocity.length();
            Vec3 forward = speed > 1.0e-5
                    ? new Vec3(velocity.x / speed, velocity.y / speed, velocity.z / speed)
                    : EFFECTS.containsKey(ship) ? EFFECTS.get(ship).forward() : longestAxis(hullBounds);

            HullData hull = hullFor(ship, hullBounds);
            CachedEffect effect = EFFECTS.get(ship);
            boolean moving = speed > 1.0e-5;
            float strength = reentryState.strength();
            if (effect == null || effect.hullBuiltAt() != hull.builtAt()
                    || effect.forward().dot(forward) < 0.98
                    || effect.hasTrails() != moving || Math.abs(effect.strength() - strength) > 0.04f) {
                List<SurfaceQuad> surfaceMesh = buildSurfaceMesh(hull.faces(), hullBounds, localCenter, forward);
                WakeMesh wakeMesh = moving
                        ? buildWakeMesh(hull, localCenter, forward, strength)
                        : new WakeMesh(List.of(), List.of());
                double wakeLength = moving ? wakeLength(strength) : 0.0;
                effect = new CachedEffect(hull.builtAt(), forward, surfaceMesh,
                        wakeMesh.quads(), wakeMesh.bridges(),
                        moving, strength, wakeLength);
                EFFECTS.put(ship, effect);
            }
            renderHullSurface(hullOut, pose, cameraPosition, shipPose, effect.surfaceMesh(), time, 0.22f);
            if (wakeType != null && !effect.wakeMesh().isEmpty()) {
                pendingWake.add(new PendingWake(shipPose, forward, localCenter, hullBounds,
                        effect.wakeLength(), effect.wakeMesh(), effect.wakeBridges()));
            }
        }
        buffers.endBatch(hullType);
        if (wakeType != null && !pendingWake.isEmpty()) {
            ShaderProgram shader = VeilRenderSystem.setShader(PLASMA);
            if (shader != null) {
                CelestialShaderUniforms.setFloat(shader, "Time", (float) (time / 20.0));
                CelestialShaderUniforms.setFloat(shader, "WrapLayer", 0.0f);
                CelestialShaderUniforms.setFloat(shader, "CellSize", (float) WAKE_CELL_SIZE);
            }
            VertexConsumer wakeOut = buffers.getBuffer(wakeType);
            for (PendingWake wake : pendingWake) {
                renderWakeMesh(wakeOut, pose, cameraPosition, wake, 0.0f);
                renderWakeBridges(wakeOut, pose, cameraPosition, wake, 0.0f);
            }
            buffers.endBatch(wakeType);

            shader = VeilRenderSystem.setShader(PLASMA);
            if (shader != null) {
                CelestialShaderUniforms.setFloat(shader, "WrapLayer", 1.0f);
                CelestialShaderUniforms.setFloat(shader, "CellSize", (float) WAKE_CELL_SIZE);
            }
            wakeOut = buffers.getBuffer(wakeType);
            for (PendingWake wake : pendingWake) {
                renderWakeMesh(wakeOut, pose, cameraPosition, wake, 1.0f);
                renderWakeBridges(wakeOut, pose, cameraPosition, wake, 1.0f);
            }
            buffers.endBatch(wakeType);
        }
    }

    private static HullData hullFor(ClientSubLevel ship, HullBounds bounds) {
        long tick = ship.getLevel().getGameTime();
        HullData cached = HULLS.get(ship);
        if (cached != null && cached.bounds().equals(bounds) && tick - cached.builtAt() < HULL_REFRESH_TICKS) {
            return cached;
        }
        HullData rebuilt = buildHull(ship, bounds, tick);
        HULLS.put(ship, rebuilt);
        return rebuilt;
    }

    private static HullData buildHull(ClientSubLevel ship, HullBounds bounds, long tick) {
        Set<Long> blocks = new HashSet<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minX = (int) bounds.minX();
        int minY = (int) bounds.minY();
        int minZ = (int) bounds.minZ();
        int maxX = (int) Math.ceil(bounds.maxX()) - 1;
        int maxY = (int) Math.ceil(bounds.maxY()) - 1;
        int maxZ = (int) Math.ceil(bounds.maxZ()) - 1;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    if (ship.getLevel().getBlockState(pos).isAir()) continue;
                    blocks.add(BlockPos.asLong(x, y, z));
                }
            }
        }

        int airMinX = minX - 1;
        int airMinY = minY - 1;
        int airMinZ = minZ - 1;
        int airMaxX = maxX + 1;
        int airMaxY = maxY + 1;
        int airMaxZ = maxZ + 1;
        int sizeY = airMaxY - airMinY + 1;
        int sizeZ = airMaxZ - airMinZ + 1;
        int volume = Math.multiplyExact(Math.multiplyExact(airMaxX - airMinX + 1, sizeY), sizeZ);
        BitSet exteriorAir = new BitSet(volume);
        int[] airToVisit = new int[volume];
        int airCount = 0;
        for (int x = airMinX; x <= airMaxX; x++) {
            for (int y = airMinY; y <= airMaxY; y++) {
                for (int z = airMinZ; z <= airMaxZ; z++) {
                    if (x != airMinX && x != airMaxX && y != airMinY && y != airMaxY
                            && z != airMinZ && z != airMaxZ) continue;
                    int index = voxelIndex(x, y, z, airMinX, airMinY, airMinZ, sizeY, sizeZ);
                    if (!blocks.contains(BlockPos.asLong(x, y, z)) && !exteriorAir.get(index)) {
                        exteriorAir.set(index);
                        airToVisit[airCount++] = index;
                    }
                }
            }
        }
        for (int head = 0; head < airCount; head++) {
            int index = airToVisit[head];
            int z = index % sizeZ + airMinZ;
            int yz = index / sizeZ;
            int y = yz % sizeY + airMinY;
            int x = yz / sizeY + airMinX;
            for (Direction direction : Direction.values()) {
                int nextX = x + direction.getStepX();
                int nextY = y + direction.getStepY();
                int nextZ = z + direction.getStepZ();
                if (nextX < airMinX || nextX > airMaxX || nextY < airMinY || nextY > airMaxY
                        || nextZ < airMinZ || nextZ > airMaxZ
                        || blocks.contains(BlockPos.asLong(nextX, nextY, nextZ))) continue;
                int nextIndex = voxelIndex(nextX, nextY, nextZ,
                        airMinX, airMinY, airMinZ, sizeY, sizeZ);
                if (exteriorAir.get(nextIndex)) continue;
                exteriorAir.set(nextIndex);
                airToVisit[airCount++] = nextIndex;
            }
        }

        List<HullFace> exposedFaces = new ArrayList<>();
        for (long blockKey : blocks) {
            BlockPos block = BlockPos.of(blockKey);
            for (Direction direction : Direction.values()) {
                int airX = block.getX() + direction.getStepX();
                int airY = block.getY() + direction.getStepY();
                int airZ = block.getZ() + direction.getStepZ();
                int airIndex = voxelIndex(airX, airY, airZ,
                        airMinX, airMinY, airMinZ, sizeY, sizeZ);
                if (!exteriorAir.get(airIndex)) continue;
                HullFace face = new HullFace(block.getX(), block.getY(), block.getZ(), direction);
                exposedFaces.add(face);
            }
        }
        return new HullData(bounds, tick, Set.copyOf(blocks), List.copyOf(exposedFaces));
    }

    private static int voxelIndex(int x, int y, int z, int minX, int minY, int minZ,
                                  int sizeY, int sizeZ) {
        return ((x - minX) * sizeY + y - minY) * sizeZ + z - minZ;
    }


    private static WakeMesh buildWakeMesh(HullData hull, Vector3d center,
                                          Vec3 forward, float strength) {
        WakeFrame frame = wakeFrame(new Vec3(center.x, center.y, center.z), forward);
        Map<Long, WakeSeed> seedColumns = new HashMap<>();
        for (HullFace face : hull.faces()) {
            Vec3 normal = face.normal();
            double incidence = normal.dot(frame.trail());
            if (incidence < 0.1) continue;
            WakeCell seed = connectedWakeCell(frame, face);
            if (seed == null) continue;
            WakeSeed candidate = new WakeSeed(seed, face);
            seedColumns.merge(packCell(seed.j(), seed.k()), candidate,
                    (first, next) -> first.cell().i() >= next.cell().i() ? first : next);
        }

        int baseLength = (int) Math.ceil((WAKE_LENGTH * (0.35 + 0.65 * strength)) / WAKE_CELL_SIZE);
        long expectedCells = (long) seedColumns.size() * (baseLength + 1L);
        LongOpenHashSet cells = new LongOpenHashSet((int) Math.min(expectedCells, 1_000_000L));
        Set<Long> bridgedStarts = new HashSet<>();
        List<WakeBridge> bridges = new ArrayList<>(seedColumns.size());
        for (WakeSeed wakeSeed : seedColumns.values()) {
            WakeCell seed = wakeSeed.cell();
            Vec3 firstCenter = wakePoint(frame, seed.i() + 0.5, seed.j() + 0.5, seed.k() + 0.5);
            if (hull.blocks().contains(blockKeyAt(firstCenter))) continue;
            bridges.add(wakeBridge(frame, wakeSeed.face(), seed));
            bridgedStarts.add(packWakeCell(seed.i(), seed.j(), seed.k()));
            for (int step = 0; step <= baseLength; step++) {
                int i = seed.i() + step;
                Vec3 centerPoint = wakePoint(frame, i + 0.5, seed.j() + 0.5, seed.k() + 0.5);
                if (hull.blocks().contains(blockKeyAt(centerPoint))) {
                    break;
                }
                cells.add(packWakeCell(i, seed.j(), seed.k()));
            }
        }
        if (cells.isEmpty()) return new WakeMesh(List.of(), List.copyOf(bridges));

        Map<WakePlane, Set<Long>> planes = new HashMap<>();
        for (long cell : cells) {
            int i = wakeCellI(cell), j = wakeCellJ(cell), k = wakeCellK(cell);
            if (!bridgedStarts.contains(cell))
                addWakeFace(planes, cells, i, j, k, Direction.WEST, i, j, k);
            addWakeFace(planes, cells, i, j, k, Direction.EAST, i + 1, j, k);
            addWakeFace(planes, cells, i, j, k, Direction.DOWN, j, i, k);
            addWakeFace(planes, cells, i, j, k, Direction.UP, j + 1, i, k);
            addWakeFace(planes, cells, i, j, k, Direction.NORTH, k, i, j);
            addWakeFace(planes, cells, i, j, k, Direction.SOUTH, k + 1, i, j);
        }

        List<WakeQuad> mesh = new ArrayList<>();
        for (Map.Entry<WakePlane, Set<Long>> entry : planes.entrySet()) {
            Set<Long> faces = entry.getValue();
            for (long first : new ArrayList<>(faces)) {
                if (!faces.remove(first)) continue;
                int u = (int) (first >> 32), v = (int) first, width = 1, height = 1;
                while (faces.contains(packCell(u + width, v))) width++;
                boolean grow = true;
                while (grow) {
                    for (int du = 0; du < width; du++) {
                        if (!faces.contains(packCell(u + du, v + height))) { grow = false; break; }
                    }
                    if (grow) height++;
                }
                for (int du = 0; du < width; du++)
                    for (int dv = 0; dv < height; dv++) faces.remove(packCell(u + du, v + dv));
                mesh.add(new WakeQuad(entry.getKey(), u, v, width, height));
            }
        }
        return new WakeMesh(List.copyOf(mesh), List.copyOf(bridges));
    }

    private static double wakeLength(float strength) {
        int baseLength = (int) Math.ceil((WAKE_LENGTH * (0.35 + 0.65 * strength)) / WAKE_CELL_SIZE);
        return (baseLength + 1) * WAKE_CELL_SIZE;
    }

    private static void addWakeFace(Map<WakePlane, Set<Long>> planes, LongOpenHashSet cells,
                                    int i, int j, int k, Direction direction,
                                    int plane, int u, int v) {
        long neighbor = packWakeCell(i + direction.getStepX(),
                j + direction.getStepY(), k + direction.getStepZ());
        if (cells.contains(neighbor)) return;
        planes.computeIfAbsent(new WakePlane(direction, plane), ignored -> new HashSet<>())
                .add(packCell(u, v));
    }

    private static WakeFrame wakeFrame(Vec3 origin, Vec3 forward) {
        Vec3 trail = forward.scale(-1.0).normalize();
        Vec3 reference = Math.abs(trail.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 side = trail.cross(reference).normalize();
        Vec3 up = side.cross(trail).normalize();
        return new WakeFrame(origin, trail, side, up);
    }

    private static WakeCell wakeCell(WakeFrame frame, Vec3 point) {
        Vec3 relative = point.subtract(frame.origin());
        return new WakeCell((int) Math.floor(relative.dot(frame.trail()) / WAKE_CELL_SIZE),
                (int) Math.floor(relative.dot(frame.side()) / WAKE_CELL_SIZE),
                (int) Math.floor(relative.dot(frame.up()) / WAKE_CELL_SIZE));
    }

    private static WakeCell connectedWakeCell(WakeFrame frame, HullFace face) {
        List<Vec3> corners = face.corners();
        double aft = Double.NEGATIVE_INFINITY;
        for (Vec3 corner : corners) {
            aft = Math.max(aft, corner.subtract(frame.origin()).dot(frame.trail()));
        }
        Vec3 center = face.center().subtract(frame.origin());
        int i = (int) Math.ceil((aft - 1.0e-6) / WAKE_CELL_SIZE);
        int j = (int) Math.floor(center.dot(frame.side()) / WAKE_CELL_SIZE);
        int k = (int) Math.floor(center.dot(frame.up()) / WAKE_CELL_SIZE);
        return new WakeCell(i, j, k);
    }

    private static WakeBridge wakeBridge(WakeFrame frame, HullFace face, WakeCell seed) {
        List<Vec3> target = List.of(
                wakePoint(frame, seed.i(), seed.j(), seed.k()),
                wakePoint(frame, seed.i(), seed.j() + 1, seed.k()),
                wakePoint(frame, seed.i(), seed.j() + 1, seed.k() + 1),
                wakePoint(frame, seed.i(), seed.j(), seed.k() + 1));
        List<Vec3> hull = face.corners();
        double bestCost = Double.POSITIVE_INFINITY;
        List<Vec3> orderedTarget = target;
        for (int direction : new int[]{-1, 1}) {
            for (int offset = 0; offset < 4; offset++) {
                List<Vec3> candidate = new ArrayList<>(4);
                double cost = 0.0;
                for (int index = 0; index < 4; index++) {
                    Vec3 corner = target.get(Math.floorMod(offset + direction * index, 4));
                    candidate.add(corner);
                    cost += hull.get(index).distanceToSqr(corner);
                }
                if (cost < bestCost) {
                    bestCost = cost;
                    orderedTarget = List.copyOf(candidate);
                }
            }
        }
        return new WakeBridge(hull, orderedTarget);
    }

    private static long packWakeCell(int i, int j, int k) {
        return ((long) i & 0x1F_FFFF) << 42 | ((long) j & 0x1F_FFFF) << 21 | ((long) k & 0x1F_FFFF);
    }

    private static int wakeCellI(long cell) {
        return signExtendWakeCoordinate((int) (cell >>> 42));
    }

    private static int wakeCellJ(long cell) {
        return signExtendWakeCoordinate((int) (cell >>> 21));
    }

    private static int wakeCellK(long cell) {
        return signExtendWakeCoordinate((int) cell);
    }

    private static int signExtendWakeCoordinate(int coordinate) {
        coordinate &= 0x1F_FFFF;
        return (coordinate & 0x10_0000) == 0 ? coordinate : coordinate - 0x20_0000;
    }

    private static Vec3 wakePoint(WakeFrame frame, double i, double j, double k) {
        return frame.origin().add(frame.trail().scale(i * WAKE_CELL_SIZE))
                .add(frame.side().scale(j * WAKE_CELL_SIZE))
                .add(frame.up().scale(k * WAKE_CELL_SIZE));
    }

    private static long blockKeyAt(Vec3 position) {
        BlockPos block = BlockPos.containing(position.x, position.y, position.z);
        return BlockPos.asLong(block.getX(), block.getY(), block.getZ());
    }

    private static Vec3 longestAxis(HullBounds bounds) {
        double x = bounds.maxX() - bounds.minX(), y = bounds.maxY() - bounds.minY();
        double z = bounds.maxZ() - bounds.minZ();
        return x >= y && x >= z ? new Vec3(1, 0, 0)
                : y >= z ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
    }

    private static double longitudinalPosition(Vec3 point, HullBounds bounds,
                                                Vector3d center, Vec3 forward) {
        double half = Math.abs(forward.x) * (bounds.maxX() - bounds.minX()) * 0.5
                + Math.abs(forward.y) * (bounds.maxY() - bounds.minY()) * 0.5
                + Math.abs(forward.z) * (bounds.maxZ() - bounds.minZ()) * 0.5;
        if (half < 1.0e-4) return 0.5;
        Vec3 relative = point.subtract(new Vec3(center.x, center.y, center.z));
        return Math.clamp(0.5 - relative.dot(forward) / (2.0 * half), 0.0, 1.0);
    }

    private static List<SurfaceQuad> buildSurfaceMesh(List<HullFace> faces, HullBounds bounds,
                                                       Vector3d center, Vec3 forward) {
        Map<SurfacePlane, Set<Long>> planes = new HashMap<>();
        for (HullFace face : faces) {
            int bucket = (int) Math.round(longitudinalPosition(face.center(), bounds, center, forward) * 16.0);
            SurfacePlane plane = new SurfacePlane(face.direction(), surfacePlane(face), bucket);
            planes.computeIfAbsent(plane, ignored -> new HashSet<>())
                    .add(packCell(surfaceU(face), surfaceV(face)));
        }
        List<SurfaceQuad> mesh = new ArrayList<>();
        for (Map.Entry<SurfacePlane, Set<Long>> entry : planes.entrySet()) {
            Set<Long> cells = entry.getValue();
            for (long first : new ArrayList<>(cells)) {
                if (!cells.remove(first)) continue;
                int u = (int) (first >> 32), v = (int) first, width = 1, height = 1;
                while (cells.contains(packCell(u + width, v))) width++;
                boolean grow = true;
                while (grow) {
                    for (int du = 0; du < width; du++) {
                        if (!cells.contains(packCell(u + du, v + height))) { grow = false; break; }
                    }
                    if (grow) height++;
                }
                for (int du = 0; du < width; du++)
                    for (int dv = 0; dv < height; dv++) cells.remove(packCell(u + du, v + dv));
                mesh.add(new SurfaceQuad(entry.getKey(), u, v, width, height));
            }
        }
        return List.copyOf(mesh);
    }

    private static void renderHullSurface(VertexConsumer out, PoseStack.Pose pose, Vec3 camera,
                                          Pose3dc shipPose, List<SurfaceQuad> mesh, double time,
                                          float opacity) {
        for (SurfaceQuad quad : mesh) {
            SurfacePlane plane = quad.plane();
            double fixed = plane.coordinate() + (plane.direction().getAxisDirection()
                    == Direction.AxisDirection.POSITIVE ? 0.045 : -0.045);
            double u0 = quad.u(), u1 = u0 + quad.width(), v0 = quad.v(), v1 = v0 + quad.height();
            Vec3 a, b, c, d;
            if (plane.direction().getAxis() == Direction.Axis.X) {
                a = new Vec3(fixed,v0,u0); b = new Vec3(fixed,v0,u1); c = new Vec3(fixed,v1,u1); d = new Vec3(fixed,v1,u0);
            } else if (plane.direction().getAxis() == Direction.Axis.Y) {
                a = new Vec3(u0,fixed,v0); b = new Vec3(u1,fixed,v0); c = new Vec3(u1,fixed,v1); d = new Vec3(u0,fixed,v1);
            } else {
                a = new Vec3(u0,v0,fixed); b = new Vec3(u1,v0,fixed); c = new Vec3(u1,v1,fixed); d = new Vec3(u0,v1,fixed);
            }
            float pulse = 0.92f + (float)(0.04 * (Math.sin(time * 3.0 + plane.coordinate()) + 1.0));
            float[] color = gradient(plane.heatBucket() / 16.0, opacity * pulse);
            addHullVertex(out, pose, camera, shipPose, a, (float)u0,(float)v0,color);
            addHullVertex(out, pose, camera, shipPose, b, (float)u1,(float)v0,color);
            addHullVertex(out, pose, camera, shipPose, c, (float)u1,(float)v1,color);
            addHullVertex(out, pose, camera, shipPose, d, (float)u0,(float)v1,color);
        }
    }

    private static void renderWakeMesh(VertexConsumer out, PoseStack.Pose pose, Vec3 camera,
                                       PendingWake wake, float wrapLayer) {
        WakeFrame frame = wakeFrame(new Vec3(wake.center().x, wake.center().y, wake.center().z), wake.forward());
        for (WakeQuad quad : wake.mesh()) {
            WakePlane plane = quad.plane();
            double u0 = quad.u(), u1 = u0 + quad.width();
            double v0 = quad.v(), v1 = v0 + quad.height();
            double fixed = plane.coordinate();
            Vec3 a, b, c, d;
            float au, av, bu, bv, cu, cv, du, dv;
            if (plane.direction().getAxis() == Direction.Axis.X) {
                a = wakePoint(frame, fixed,u0,v0); b = wakePoint(frame, fixed,u1,v0);
                c = wakePoint(frame, fixed,u1,v1); d = wakePoint(frame, fixed,u0,v1);
                au=(float)(u0*WAKE_CELL_SIZE); av=(float)(v0*WAKE_CELL_SIZE);
                bu=(float)(u1*WAKE_CELL_SIZE); bv=av; cu=bu; cv=(float)(v1*WAKE_CELL_SIZE);
                du=au; dv=cv;
            } else if (plane.direction().getAxis() == Direction.Axis.Y) {
                a = wakePoint(frame, u0,fixed,v0); b = wakePoint(frame, u1,fixed,v0);
                c = wakePoint(frame, u1,fixed,v1); d = wakePoint(frame, u0,fixed,v1);
                au=(float)(u0*WAKE_CELL_SIZE); av=(float)(v0*WAKE_CELL_SIZE);
                bu=(float)(u1*WAKE_CELL_SIZE); bv=av; cu=bu; cv=(float)(v1*WAKE_CELL_SIZE);
                du=au; dv=cv;
            } else {
                a = wakePoint(frame, u0,v0,fixed); b = wakePoint(frame, u1,v0,fixed);
                c = wakePoint(frame, u1,v1,fixed); d = wakePoint(frame, u0,v1,fixed);
                au=(float)(u0*WAKE_CELL_SIZE); av=(float)(v0*WAKE_CELL_SIZE);
                bu=(float)(u1*WAKE_CELL_SIZE); bv=av; cu=bu; cv=(float)(v1*WAKE_CELL_SIZE);
                du=au; dv=cv;
            }

            Vec3 normal = switch (plane.direction().getAxis()) {
                case X -> frame.trail().scale(plane.direction().getStepX());
                case Y -> frame.side().scale(plane.direction().getStepY());
                case Z -> frame.up().scale(plane.direction().getStepZ());
            };
            if (wrapLayer > 0.0f) {
                Vec3 offset = normal.scale(0.025);
                a = a.add(offset); b = b.add(offset); c = c.add(offset); d = d.add(offset);
            }
            addWakeVertex(out, pose, camera, wake, a, au, av, wrapLayer);
            addWakeVertex(out, pose, camera, wake, b, bu, bv, wrapLayer);
            addWakeVertex(out, pose, camera, wake, c, cu, cv, wrapLayer);
            addWakeVertex(out, pose, camera, wake, d, du, dv, wrapLayer);
        }
    }

    private static void renderWakeBridges(VertexConsumer out, PoseStack.Pose pose, Vec3 camera,
                                          PendingWake wake, float wrapLayer) {
        WakeFrame frame = wakeFrame(new Vec3(wake.center().x, wake.center().y, wake.center().z), wake.forward());
        for (WakeBridge bridge : wake.bridges()) {
            for (int index = 0; index < 4; index++) {
                int next = (index + 1) & 3;
                Vec3 a = bridge.hullCorners().get(index);
                Vec3 b = bridge.hullCorners().get(next);
                Vec3 c = bridge.wakeCorners().get(next);
                Vec3 d = bridge.wakeCorners().get(index);
                Vec3 normal = b.subtract(a).cross(d.subtract(a)).normalize();
                if (normal.lengthSqr() < 1.0e-8) continue;
                if (wrapLayer > 0.0f) {
                    Vec3 offset = normal.scale(0.025);
                    a = a.add(offset); b = b.add(offset); c = c.add(offset); d = d.add(offset);
                }
                addBridgeVertex(out, pose, camera, wake, frame, a, wrapLayer);
                addBridgeVertex(out, pose, camera, wake, frame, b, wrapLayer);
                addBridgeVertex(out, pose, camera, wake, frame, c, wrapLayer);
                addBridgeVertex(out, pose, camera, wake, frame, d, wrapLayer);
            }
        }
    }

    private static void addBridgeVertex(VertexConsumer out, PoseStack.Pose pose, Vec3 camera,
                                        PendingWake wake, WakeFrame frame, Vec3 local, float wrapLayer) {
        Vec3 relative = local.subtract(frame.origin());
        float u = (float) relative.dot(frame.trail());
        float v = (float) relative.dot(frame.side());
        addWakeVertex(out, pose, camera, wake, local, u, v, wrapLayer);
    }

    private static void addWakeVertex(VertexConsumer out, PoseStack.Pose pose, Vec3 camera,
                                      PendingWake wake, Vec3 local, float u, float v, float wrapLayer) {
        double progress = longitudinalPosition(local, wake.bounds(), wake.center(), wake.forward());
        double halfLength = Math.abs(wake.forward().x) * (wake.bounds().maxX() - wake.bounds().minX()) * 0.5
                + Math.abs(wake.forward().y) * (wake.bounds().maxY() - wake.bounds().minY()) * 0.5
                + Math.abs(wake.forward().z) * (wake.bounds().maxZ() - wake.bounds().minZ()) * 0.5;
        Vec3 relative = local.subtract(new Vec3(wake.center().x, wake.center().y, wake.center().z));
        double distanceBehindHull = relative.dot(wake.forward().scale(-1.0)) - halfLength;
        double trailFade = 1.0 - smoothstep(0.7 * wake.wakeLength(), wake.wakeLength(), distanceBehindHull);
        float alpha = (wrapLayer == 0.0f ? 0.38f : 0.2f) * (float) trailFade;
        float[] color = gradient(progress, alpha);
        Vector3d world = wake.pose().transformPosition(new Vector3d(local.x, local.y, local.z), new Vector3d());
        addVertex(out, pose, new Vec3(world.x, world.y, world.z).subtract(camera), u, v, color);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = Math.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static int surfacePlane(HullFace face) {
        return switch (face.direction()) {
            case EAST -> face.blockX() + 1;
            case WEST -> face.blockX();
            case UP -> face.blockY() + 1;
            case DOWN -> face.blockY();
            case SOUTH -> face.blockZ() + 1;
            case NORTH -> face.blockZ();
        };
    }

    private static int surfaceU(HullFace face) {
        return face.direction().getAxis() == Direction.Axis.X ? face.blockZ() : face.blockX();
    }

    private static int surfaceV(HullFace face) {
        return face.direction().getAxis() == Direction.Axis.X ? face.blockY()
                : face.direction().getAxis() == Direction.Axis.Y ? face.blockZ() : face.blockY();
    }

    private static long packCell(int u, int v) {
        return ((long) u << 32) | (v & 0xFFFF_FFFFL);
    }

    private static void addHullVertex(VertexConsumer out, PoseStack.Pose pose, Vec3 cameraPosition,
                                      Pose3dc shipPose, Vec3 local, float u, float v, float[] color) {
        Vector3d world = shipPose.transformPosition(new Vector3d(local.x, local.y, local.z), new Vector3d());
        Vec3 relative = new Vec3(world.x, world.y, world.z).subtract(cameraPosition);
        addVertex(out, pose, relative, u, v, color);
    }


    private static float[] gradient(double progress, float alpha) {
        double t = Math.clamp(progress, 0.0, 1.0);
        float r,g,b;
        if (t < 0.5) { float m=(float)(t*2); r=1; g=0.88f-m*0.78f; b=0.12f+m*0.38f; }
        else { float m=(float)((t-0.5)*2); r=1-m*0.48f; g=0.10f-m*0.06f; b=0.50f+m*0.42f; }
        return new float[]{r,g,b,alpha};
    }

    private static void addVertex(VertexConsumer out, PoseStack.Pose pose, Vec3 position,
                                  float u, float v, float[] color) {
        out.addVertex(pose, (float)position.x, (float)position.y, (float)position.z)
                .setColor(color[0],color[1],color[2],color[3]).setUv(u,v).setNormal(pose,0,1,0);
    }


    private record HullBounds(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {}
    private record SurfacePlane(Direction direction,int coordinate,int heatBucket) {}
    private record HullFace(int blockX,int blockY,int blockZ,Direction direction) {
        Vec3 normal() { return new Vec3(direction.getStepX(),direction.getStepY(),direction.getStepZ()); }
        Vec3 blockCenter() { return new Vec3(blockX+0.5,blockY+0.5,blockZ+0.5); }
        Vec3 center() { Vec3 n=normal(); return new Vec3(blockX+0.5+n.x*0.5,blockY+0.5+n.y*0.5,blockZ+0.5+n.z*0.5); }
        List<Vec3> corners() {
            double x=blockX, y=blockY, z=blockZ;
            return switch (direction) {
                case EAST -> List.of(new Vec3(x+1,y,z),new Vec3(x+1,y+1,z),new Vec3(x+1,y+1,z+1),new Vec3(x+1,y,z+1));
                case WEST -> List.of(new Vec3(x,y,z),new Vec3(x,y,z+1),new Vec3(x,y+1,z+1),new Vec3(x,y+1,z));
                case UP -> List.of(new Vec3(x,y+1,z),new Vec3(x,y+1,z+1),new Vec3(x+1,y+1,z+1),new Vec3(x+1,y+1,z));
                case DOWN -> List.of(new Vec3(x,y,z),new Vec3(x+1,y,z),new Vec3(x+1,y,z+1),new Vec3(x,y,z+1));
                case SOUTH -> List.of(new Vec3(x,y,z+1),new Vec3(x+1,y,z+1),new Vec3(x+1,y+1,z+1),new Vec3(x,y+1,z+1));
                case NORTH -> List.of(new Vec3(x,y,z),new Vec3(x,y+1,z),new Vec3(x+1,y+1,z),new Vec3(x+1,y,z));
            };
        }
    }
    private record HullData(HullBounds bounds,long builtAt,Set<Long> blocks,List<HullFace> faces) {}
    private record SurfaceQuad(SurfacePlane plane,int u,int v,int width,int height) {}
    private record WakeCell(int i, int j, int k) {}
    private record WakeSeed(WakeCell cell, HullFace face) {}
    private record WakeMesh(List<WakeQuad> quads, List<WakeBridge> bridges) {}
    private record WakeBridge(List<Vec3> hullCorners, List<Vec3> wakeCorners) {}
    private record WakePlane(Direction direction, int coordinate) {}
    private record WakeQuad(WakePlane plane, int u, int v, int width, int height) {}
    private record WakeFrame(Vec3 origin, Vec3 trail, Vec3 side, Vec3 up) {}
    private record CachedEffect(long hullBuiltAt, Vec3 forward, List<SurfaceQuad> surfaceMesh,
                                List<WakeQuad> wakeMesh, List<WakeBridge> wakeBridges,
                                boolean hasTrails, float strength, double wakeLength) {}
    private record PendingWake(Pose3dc pose, Vec3 forward, Vector3d center, HullBounds bounds,
                               double wakeLength, List<WakeQuad> mesh, List<WakeBridge> bridges) {}
}
