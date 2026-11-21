package com.encdata.astronautics.space;

import com.encdata.astronautics.Astronautics;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Astronautics.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class SpaceChunkLoading {
    public static final TicketType<ChunkPos> ENTITY_AREA =
            TicketType.create("astronautics_space_entities", Comparator.comparingLong(ChunkPos::toLong));

    private static final int TICKET_DISTANCE = ChunkLevel.byStatus(FullChunkStatus.FULL)
            - ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
    private static final int REFRESH_INTERVAL = 2;
    private static final int PLAYER_RADIUS = 2;
    private static final double LOOKAHEAD_SECONDS = 0.75;
    private static final int MAX_COLUMNS = 16384;
    private static final double SHIP_MARGIN = 2.0;
    private static final double ABOARD_MARGIN = 6.0;
    private static final double DECK_HEADROOM = 4.0;
    private static final double POSE_SLACK_SECONDS = 0.15;
    private static final double CARRY_SPEED = 40.0;
    private static final double MAX_CARRY_SPEED = 5000.0;
    private static final int RELEASE_DELAY_TICKS = 6;
    private static final int FAST_HOLD_TICKS = 40;
    private static final double SMOOTHING = 0.5;
    private static final double EDGE_MARGIN = 64.0;
    private static final double TELEPORT_DISTANCE = 1024.0;
    private static final long MISSING = Long.MIN_VALUE;

    private static final Map<ServerLevel, State> STATES = new IdentityHashMap<>();

    private SpaceChunkLoading() {}

    public static boolean isActive(ServerLevel level) {
        return level.getChunkSource().chunkMap.getDistanceManager() instanceof SpaceTickets tickets
                && tickets.astronautics$isSpace();
    }

    public static boolean isVirtual(ServerLevel level, int chunkX, int chunkZ) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container == null || !container.inBounds(chunkX, chunkZ);
    }

    public static ProtoChunk emptyChunk(ServerLevel level, ChunkPos pos) {
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, level,
                level.registryAccess().registryOrThrow(Registries.BIOME), null);
        try {
            ServerChunkCache source = level.getChunkSource();
            chunk.fillBiomesFromNoise(source.getGenerator().getBiomeSource(), source.randomState().sampler());
            Heightmap.primeHeightmaps(chunk, ChunkStatus.FULL.heightmapsAfter());
            chunk.setLightCorrect(!level.dimensionType().hasSkyLight());
            chunk.setPersistedStatus(ChunkStatus.FULL);
            chunk.setUnsaved(false);
            return chunk;
        } catch (RuntimeException exception) {
            Astronautics.LOGGER.error("Empty space chunk {} failed, generating it", pos, exception);
            return new ProtoChunk(pos, UpgradeData.EMPTY, level,
                    level.registryAccess().registryOrThrow(Registries.BIOME), null);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !SpaceConstants.isSpace(level)
                || !isActive(level)) {
            return;
        }

        State state = STATES.computeIfAbsent(level, State::new);

        for (ServerPlayer player : level.players()) state.track(player);

        if (level.getGameTime() % REFRESH_INTERVAL != 0) return;
        collect(level, state);
        apply(level, state);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        for (State state : STATES.values()) state.motions.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) STATES.remove(level);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
    }

    private static void collect(ServerLevel level, State state) {
        LongOpenHashSet wanted = state.wanted;
        wanted.clear();

        int simulation = level.getServer().getPlayerList().getSimulationDistance();
        boolean spectatorsGenerate = level.getGameRules().getBoolean(GameRules.RULE_SPECTATORSGENERATECHUNKS);
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        long now = level.getGameTime();
        state.fastUntil.object2LongEntrySet().removeIf(entry -> entry.getLongValue() < now);

        List<SpaceChunkSweep> windows = new ArrayList<>();

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() && !spectatorsGenerate) continue;

            Motion motion = state.motions.get(player.getUUID());
            double vx = motion == null ? 0.0 : motion.vx;
            double vz = motion == null ? 0.0 : motion.vz;
            double dx = vx * LOOKAHEAD_SECONDS;
            double dz = vz * LOOKAHEAD_SECONDS;
            int chunkX = Mth.floor(player.getX()) >> 4;
            int chunkZ = Mth.floor(player.getZ()) >> 4;

            SpaceChunkSweep window = SpaceChunkSweep.ofChunks(chunkX, chunkZ, simulation, dx, dz);
            if (window == null) continue;

            windows.add(window);
            double reach = simulation * 16.0 + EDGE_MARGIN;
            List<double[]> shifts = edgeShifts(player.getX() - reach, player.getZ() - reach,
                    player.getX() + reach, player.getZ() + reach);
            for (double[] shift : shifts) {
                SpaceChunkSweep mirror = SpaceChunkSweep.ofChunks(chunkX + (int) (shift[0] / 16.0),
                        chunkZ + (int) (shift[1] / 16.0), simulation, dx, dz);
                if (mirror != null) windows.add(mirror);
            }

            double speed = Math.max(Math.sqrt(vx * vx + vz * vz), shipSpeedAround(container, player));
            if (speed > CARRY_SPEED) {
                state.fastUntil.put(player.getUUID(), now + FAST_HOLD_TICKS);
            } else if (!state.fastUntil.containsKey(player.getUUID())) {
                SpaceChunkSweep area = SpaceChunkSweep.ofChunks(chunkX, chunkZ, PLAYER_RADIUS, dx, dz);
                if (area != null) addAll(area, wanted);
                for (double[] shift : shifts) {
                    SpaceChunkSweep mirror = SpaceChunkSweep.ofChunks(chunkX + (int) (shift[0] / 16.0),
                            chunkZ + (int) (shift[1] / 16.0), PLAYER_RADIUS, dx, dz);
                    if (mirror != null) addAll(mirror, wanted);
                }
            }
        }

        double windowMinX = Double.POSITIVE_INFINITY;
        double windowMinZ = Double.POSITIVE_INFINITY;
        double windowMaxX = Double.NEGATIVE_INFINITY;
        double windowMaxZ = Double.NEGATIVE_INFINITY;
        for (SpaceChunkSweep window : windows) {
            windowMinX = Math.min(windowMinX, window.minX + Math.min(0.0, window.dx));
            windowMinZ = Math.min(windowMinZ, window.minZ + Math.min(0.0, window.dz));
            windowMaxX = Math.max(windowMaxX, window.maxX + Math.max(0.0, window.dx));
            windowMaxZ = Math.max(windowMaxZ, window.maxZ + Math.max(0.0, window.dz));
        }

        if (container != null && !windows.isEmpty()) {
            for (SubLevel subLevel : container.getAllSubLevels()) {
                if (subLevel.isRemoved()) continue;

                BoundingBox3dc bounds = subLevel.boundingBox();
                Vector3dc velocity = subLevel instanceof ServerSubLevel serverSubLevel ? serverSubLevel.latestLinearVelocity : null;
                double vx = velocity == null ? 0.0 : velocity.x();
                double vz = velocity == null ? 0.0 : velocity.z();

                SpaceChunkSweep ship = SpaceChunkSweep.of(bounds.minX() - SHIP_MARGIN, bounds.minZ() - SHIP_MARGIN,
                        bounds.maxX() + SHIP_MARGIN, bounds.maxZ() + SHIP_MARGIN,
                        vx * LOOKAHEAD_SECONDS, vz * LOOKAHEAD_SECONDS);
                if (ship == null) continue;
                List<SpaceChunkSweep> sweeps = new ArrayList<>();
                sweeps.add(ship);
                for (double[] shift : edgeShifts(bounds.minX() - EDGE_MARGIN, bounds.minZ() - EDGE_MARGIN,
                        bounds.maxX() + EDGE_MARGIN, bounds.maxZ() + EDGE_MARGIN)) {
                    SpaceChunkSweep mirror = SpaceChunkSweep.of(
                            bounds.minX() - SHIP_MARGIN + shift[0], bounds.minZ() - SHIP_MARGIN + shift[1],
                            bounds.maxX() + SHIP_MARGIN + shift[0], bounds.maxZ() + SHIP_MARGIN + shift[1],
                            vx * LOOKAHEAD_SECONDS, vz * LOOKAHEAD_SECONDS);
                    if (mirror != null) sweeps.add(mirror);
                }
                double boxMinX = windowMinX;
                double boxMinZ = windowMinZ;
                double boxMaxX = windowMaxX;
                double boxMaxZ = windowMaxZ;
                if (sweeps.stream().noneMatch(sweep -> sweep.overlaps(boxMinX, boxMinZ, boxMaxX, boxMaxZ))) continue;

                double shipSpeed = Math.sqrt(vx * vx + vz * vz);
                double minX = windowMinX;
                double minZ = windowMinZ;
                double maxX = windowMaxX;
                double maxZ = windowMaxZ;
                if (shipSpeed > MAX_CARRY_SPEED || shipSpeed > CARRY_SPEED
                        && !SpaceGrids.within(SpaceGrids.isMainSpace(level)
                        && subLevel instanceof ServerSubLevel serverShip
                        ? SpaceGrids.simulationGrid(level, serverShip) : null,
                        () -> carriesEntities(level, bounds, vx, vz, minX, minZ, maxX, maxZ))) {
                    continue;
                }

                for (SpaceChunkSweep sweep : sweeps) addClipped(sweep, windows, wanted);
            }
        }

        if (container != null) {
            wanted.removeIf((long pos) -> container.inBounds(ChunkPos.getX(pos), ChunkPos.getZ(pos)));
        }

        if (wanted.size() >= MAX_COLUMNS && !state.warnedCap) {
            state.warnedCap = true;
            Astronautics.LOGGER.warn("Space chunk limit of {} reached", MAX_COLUMNS);
        }
    }

    private static List<double[]> edgeShifts(double minX, double minZ, double maxX, double maxZ) {
        double half = SpacePosition.GRID_HALF_SIZE;
        double size = SpacePosition.GRID_SIZE;
        double shiftX = maxX >= half ? -size : minX < -half ? size : 0.0;
        double shiftZ = maxZ >= half ? -size : minZ < -half ? size : 0.0;
        if (shiftX == 0.0 && shiftZ == 0.0) return List.of();
        List<double[]> shifts = new ArrayList<>(3);
        if (shiftX != 0.0) shifts.add(new double[]{shiftX, 0.0});
        if (shiftZ != 0.0) shifts.add(new double[]{0.0, shiftZ});
        if (shiftX != 0.0 && shiftZ != 0.0) shifts.add(new double[]{shiftX, shiftZ});
        return shifts;
    }

    private static double shipSpeedAround(SubLevelContainer container, ServerPlayer player) {
        if (container == null) return 0.0;

        double speed = 0.0;
        for (SubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved() || !(subLevel instanceof ServerSubLevel serverSubLevel)) continue;

            BoundingBox3dc bounds = subLevel.boundingBox();
            if (player.getX() < bounds.minX() - ABOARD_MARGIN || player.getX() > bounds.maxX() + ABOARD_MARGIN
                    || player.getY() < bounds.minY() - ABOARD_MARGIN || player.getY() > bounds.maxY() + ABOARD_MARGIN
                    || player.getZ() < bounds.minZ() - ABOARD_MARGIN || player.getZ() > bounds.maxZ() + ABOARD_MARGIN) {
                continue;
            }

            Vector3dc velocity = serverSubLevel.latestLinearVelocity;
            double shipSpeed = Math.sqrt(velocity.x() * velocity.x() + velocity.z() * velocity.z());
            if (Double.isFinite(shipSpeed)) speed = Math.max(speed, shipSpeed);
        }
        return speed;
    }

    private static boolean carriesEntities(ServerLevel level, BoundingBox3dc bounds, double vx, double vz,
                                           double minX, double minZ, double maxX, double maxZ) {
        double slackX = Math.abs(vx) * POSE_SLACK_SECONDS + SHIP_MARGIN;
        double slackZ = Math.abs(vz) * POSE_SLACK_SECONDS + SHIP_MARGIN;
        AABB box = new AABB(
                Math.max(bounds.minX() - slackX, minX), bounds.minY() - SHIP_MARGIN, Math.max(bounds.minZ() - slackZ, minZ),
                Math.min(bounds.maxX() + slackX, maxX), bounds.maxY() + DECK_HEADROOM, Math.min(bounds.maxZ() + slackZ, maxZ));
        if (box.minX > box.maxX || box.minZ > box.maxZ) return false;

        boolean[] found = new boolean[1];
        level.getEntities().get(EntityTypeTest.forClass(Entity.class), box, entity -> {
            if (!(entity instanceof Player) && !entity.isRemoved()) {
                found[0] = true;
                return AbortableIterationConsumer.Continuation.ABORT;
            }
            return AbortableIterationConsumer.Continuation.CONTINUE;
        });
        return found[0];
    }

    private static void addAll(SpaceChunkSweep sweep, LongOpenHashSet wanted) {
        for (int z = sweep.minRow; z <= sweep.maxRow; z++) {
            if (!sweep.row(z)) continue;
            for (int x = sweep.rowMin; x <= sweep.rowMax; x++) {
                if (wanted.size() >= MAX_COLUMNS) return;
                wanted.add(ChunkPos.asLong(x, z));
            }
        }
    }

    private static void addClipped(SpaceChunkSweep ship, List<SpaceChunkSweep> windows, LongOpenHashSet wanted) {
        for (SpaceChunkSweep window : windows) {
            for (int z = Math.max(ship.minRow, window.minRow); z <= Math.min(ship.maxRow, window.maxRow); z++) {
                if (!ship.row(z) || !window.row(z)) continue;

                int from = Math.max(ship.rowMin, window.rowMin);
                int to = Math.min(ship.rowMax, window.rowMax);
                for (int x = from; x <= to; x++) {
                    if (wanted.size() >= MAX_COLUMNS) return;
                    wanted.add(ChunkPos.asLong(x, z));
                }
            }
        }
    }

    private static void apply(ServerLevel level, State state) {
        DistanceManager distanceManager = level.getChunkSource().chunkMap.getDistanceManager();
        long now = level.getGameTime();

        var wanted = state.wanted.iterator();
        while (wanted.hasNext()) {
            long pos = wanted.nextLong();
            if (state.held.put(pos, now) == MISSING) {
                ChunkPos chunk = new ChunkPos(pos);
                distanceManager.addRegionTicket(ENTITY_AREA, chunk, TICKET_DISTANCE, chunk);
            }
        }

        ObjectIterator<Long2LongMap.Entry> held = state.held.long2LongEntrySet().fastIterator();
        while (held.hasNext()) {
            Long2LongMap.Entry entry = held.next();
            if (entry.getLongValue() < now - RELEASE_DELAY_TICKS) {
                ChunkPos chunk = new ChunkPos(entry.getLongKey());
                distanceManager.removeRegionTicket(ENTITY_AREA, chunk, TICKET_DISTANCE, chunk);
                held.remove();
            }
        }

        state.wanted.clear();
    }

    public interface SpaceTickets {
        boolean astronautics$isSpace();

        void astronautics$markSpace();
    }

    private static final class State {
        final ServerLevel level;
        final Long2LongOpenHashMap held = new Long2LongOpenHashMap();
        final LongOpenHashSet wanted = new LongOpenHashSet();
        final Object2LongOpenHashMap<UUID> fastUntil = new Object2LongOpenHashMap<>();
        final Map<UUID, Motion> motions = new HashMap<>();
        boolean warnedCap;

        State(ServerLevel level) {
            this.level = level;
            this.held.defaultReturnValue(MISSING);
        }

        void track(ServerPlayer player) {
            Motion motion = motions.computeIfAbsent(player.getUUID(), id -> new Motion());
            double x = player.getX();
            double z = player.getZ();

            boolean jumped = Math.abs(x - motion.x) > TELEPORT_DISTANCE || Math.abs(z - motion.z) > TELEPORT_DISTANCE;
            if (motion.started && !jumped) {
                motion.vx += ((x - motion.x) * 20.0 - motion.vx) * SMOOTHING;
                motion.vz += ((z - motion.z) * 20.0 - motion.vz) * SMOOTHING;

                if (SpaceShipIntegration.containing(player) instanceof ServerSubLevel carrier && !carrier.isRemoved()) {
                    Vector3dc velocity = carrier.latestLinearVelocity;
                    if (velocity.x() * velocity.x() + velocity.z() * velocity.z()
                            > motion.vx * motion.vx + motion.vz * motion.vz) {
                        motion.vx = velocity.x();
                        motion.vz = velocity.z();
                    }
                }

                if (!Double.isFinite(motion.vx) || !Double.isFinite(motion.vz)) {
                    motion.vx = 0.0;
                    motion.vz = 0.0;
                }
            }

            motion.started = true;
            motion.x = x;
            motion.z = z;
        }
    }

    private static final class Motion {
        boolean started;
        double x;
        double z;
        double vx;
        double vz;
    }

}
