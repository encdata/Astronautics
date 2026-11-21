package com.encdata.astronautics;

import com.encdata.astronautics.client.BreathabilityClientState;
import com.encdata.astronautics.client.ZeroGGrab;
import com.encdata.astronautics.client.ZeroGOrientation;
import com.encdata.astronautics.content.misc.ShipAtmosphereSystem;
import com.encdata.astronautics.network.BreathabilitySyncPayload;
import com.encdata.astronautics.network.GrabPayload;
import com.encdata.astronautics.network.OrientationPayload;
import com.encdata.astronautics.network.ReentryStatePayload;
import com.encdata.astronautics.network.ReentryStateStore;
import com.encdata.astronautics.network.ShipPushPayload;
import com.encdata.astronautics.network.SpaceGridPoseRebasePayload;
import com.encdata.astronautics.network.SpaceGridSyncPayload;
import com.encdata.astronautics.network.UniverseSyncPayload;
import com.encdata.astronautics.network.UniverseSyncStore;
import com.encdata.astronautics.space.GalaxyData;
import com.encdata.astronautics.space.CrewPushes;
import com.encdata.astronautics.space.ReentryState;
import com.encdata.astronautics.space.ReentrySystem;
import com.encdata.astronautics.space.SpaceCatalog;
import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceGridAddress;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AstronauticsNetworkSync {
    private static final long REENTRY_SYNC_INTERVAL_TICKS = 20L;
    private static final int UNIVERSE_RESYNC_DELAY_TICKS = 40;
    private static final Map<UUID, Boolean> BREATHABILITY = new HashMap<>();
    private static final Map<UUID, Long> PENDING_UNIVERSE_SYNCS = new HashMap<>();
    private static final Map<UUID, Map<UUID, ReentrySync>> REENTRY_SYNC = new HashMap<>();
    private static final Map<UUID, SpaceGridSync> SPACE_GRIDS = new HashMap<>();

    private AstronauticsNetworkSync() {}

    static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(Astronautics.MOD_ID);
        registrar.playToClient(
                UniverseSyncPayload.TYPE,
                UniverseSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    var level = net.minecraft.client.Minecraft.getInstance().level;
                    long clientTime = level == null ? 0L : level.getGameTime();
                    UniverseSyncStore.replace(payload, clientTime);
                }));
        registrar.playToClient(
                SpaceGridSyncPayload.TYPE,
                SpaceGridSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.encdata.astronautics.client.SpaceGridClientState.set(
                                payload.revision(), payload.address())));
        registrar.playToClient(
                SpaceGridPoseRebasePayload.TYPE,
                SpaceGridPoseRebasePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.encdata.astronautics.client.SpaceGridClientRebase.apply(payload)));
        registrar.playToClient(
                BreathabilitySyncPayload.TYPE,
                BreathabilitySyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        BreathabilityClientState.setBreathable(payload.breathable())));
        registrar.playToClient(
                ReentryStatePayload.TYPE,
                ReentryStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ReentryStateStore.put(payload)));
        registrar.playToServer(
                ShipPushPayload.TYPE,
                ShipPushPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        CrewPushes.receive(player, payload.hold(), payload.velocityChange());
                    }
                }));
        registrar.playBidirectional(
                GrabPayload.TYPE,
                GrabPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        CrewPushes.relayGrab(player, payload);
                    } else {
                        ZeroGGrab.showGrab(payload);
                    }
                }));
        registrar.playBidirectional(
                OrientationPayload.TYPE,
                OrientationPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        CrewPushes.relayOrientation(player, payload.orientation());
                    } else {
                        ZeroGOrientation.showOrientation(payload);
                    }
                }));
    }

    static void clear() {
        BREATHABILITY.clear();
        PENDING_UNIVERSE_SYNCS.clear();
        REENTRY_SYNC.clear();
        SPACE_GRIDS.clear();
        CrewPushes.clear();
    }

    static void removePlayer(UUID playerId) {
        BREATHABILITY.remove(playerId);
        PENDING_UNIVERSE_SYNCS.remove(playerId);
        REENTRY_SYNC.remove(playerId);
        SPACE_GRIDS.remove(playerId);
        CrewPushes.removePlayer(playerId);
    }

    static void syncBreathability(ServerPlayer player) {
        boolean breathable = ShipAtmosphereSystem.canBreathe(player);
        Boolean previous = BREATHABILITY.put(player.getUUID(), breathable);
        if (previous == null || previous != breathable) {
            player.connection.send(new ClientboundCustomPayloadPacket(
                    new BreathabilitySyncPayload(breathable)));
        }
    }

    public static void syncSpaceGrid(ServerPlayer player) {
        SpaceGridAddress address = SpaceConstants.isSpace(player.level())
                ? com.encdata.astronautics.space.SpaceProjection.frameGrid(player)
                : SpaceGridAddress.ORIGIN;
        SpaceGridSync previous = SPACE_GRIDS.get(player.getUUID());
        if (previous != null && address.equals(previous.address())) {
            if (previous.level() != player.level().dimension()) {
                SPACE_GRIDS.put(player.getUUID(),
                        new SpaceGridSync(previous.revision(), address, player.level().dimension()));
            }
            return;
        }
        if (previous != null && previous.level() == player.level().dimension()
                && com.encdata.astronautics.space.SpaceGrids.pulledOntoShip(player, previous.address(), address)) {
            com.encdata.astronautics.space.SpaceGrids.shiftClient(player, previous.address(), address);
            com.encdata.astronautics.space.SpaceGrids.moveToFrame(player,
                    player.getX(), player.getY(), player.getZ());
            return;
        }
        sendSpaceGrid(player, address);
    }

    public static void resetSpaceGrid(ServerPlayer player) {
        sendSpaceGrid(player, SpaceConstants.isSpace(player.level())
                ? com.encdata.astronautics.space.SpaceProjection.frameGrid(player)
                : SpaceGridAddress.ORIGIN);
    }

    private static void sendSpaceGrid(ServerPlayer player, SpaceGridAddress address) {
        SpaceGridSync previous = SPACE_GRIDS.get(player.getUUID());
        if (previous != null && address.equals(previous.address())) return;
        long revision = previous == null ? 0L : previous.revision() + 1L;
        SPACE_GRIDS.put(player.getUUID(), new SpaceGridSync(revision, address, player.level().dimension()));
        player.connection.send(new ClientboundCustomPayloadPacket(new SpaceGridSyncPayload(revision, address)));
    }

    public static long rebaseSpaceGrid(ServerPlayer player, SpaceGridAddress address) {
        SpaceGridSync previous = SPACE_GRIDS.get(player.getUUID());
        long revision = previous == null ? 0L : previous.revision() + 1L;
        SPACE_GRIDS.put(player.getUUID(), new SpaceGridSync(revision, address, player.level().dimension()));
        return revision;
    }

    private record SpaceGridSync(long revision, SpaceGridAddress address,
                                 net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> level) {}

    static void syncReentryStates(MinecraftServer server) {
        syncReentryStatesToPlayers(server.getPlayerList().getPlayers(), false, server.getTickCount());
    }

    static void syncReentryStatesTo(ServerPlayer player, boolean force) {
        syncReentryStatesToPlayers(List.of(player), force, player.server.getTickCount());
    }

    private static void syncReentryStatesToPlayers(Iterable<ServerPlayer> players,
                                                   boolean force, long tick) {
        Map<UUID, ReentryState> states = ReentrySystem.states();
        for (ServerPlayer player : players) {
            String dimensionId = player.level().dimension().location().toString();
            for (var entry : states.entrySet()) {
                if (entry.getValue().dimensionId().equals(dimensionId)) {
                    syncReentryStateTo(player, entry.getKey(), entry.getValue(), force, tick);
                }
            }
        }
    }

    private static void syncReentryStateTo(ServerPlayer player, UUID shipId,
                                           ReentryState state, boolean force, long tick) {
        int hash = reentryHash(state);
        Map<UUID, ReentrySync> syncStates = REENTRY_SYNC.computeIfAbsent(player.getUUID(),
                ignored -> new HashMap<>());
        ReentrySync previous = syncStates.get(shipId);
        if (!force && previous != null && previous.hash() == hash
                && tick - previous.tick() < REENTRY_SYNC_INTERVAL_TICKS) return;
        syncStates.put(shipId, new ReentrySync(hash, tick));
        player.connection.send(new ClientboundCustomPayloadPacket(new ReentryStatePayload(shipId, state)));
    }

    private record ReentrySync(int hash, long tick) {}

    private static int reentryHash(ReentryState state) {
        int result = state.active() ? 1 : 0;
        result = hashQuantized(result, state.position().x, 20.0);
        result = hashQuantized(result, state.position().y, 20.0);
        result = hashQuantized(result, state.position().z, 20.0);
        result = hashQuantized(result, state.inwardSpeed(), 100.0);
        result = hashQuantized(result, state.speed(), 100.0);
        result = hashQuantized(result, state.density(), 1000.0);
        result = hashQuantized(result, state.planetDirection().x, 100.0);
        result = hashQuantized(result, state.planetDirection().y, 100.0);
        result = hashQuantized(result, state.planetDirection().z, 100.0);
        result = hashQuantized(result, state.velocity().x, 100.0);
        result = hashQuantized(result, state.velocity().y, 100.0);
        result = hashQuantized(result, state.velocity().z, 100.0);
        result = 31 * result + state.dimensionId().hashCode();
        result = 31 * result + state.planetId().hashCode();
        result = 31 * result + state.surfaceSamples().size();
        for (var sample : state.surfaceSamples()) {
            result = hashQuantized(result, sample.position().x, 4.0);
            result = hashQuantized(result, sample.position().y, 4.0);
            result = hashQuantized(result, sample.position().z, 4.0);
            result = hashQuantized(result, sample.normal().x, 100.0);
            result = hashQuantized(result, sample.normal().y, 100.0);
            result = hashQuantized(result, sample.normal().z, 100.0);
            result = hashQuantized(result, sample.exposure(), 100.0);
        }
        return result;
    }

    private static int hashQuantized(int hash, double value, double scale) {
        return 31 * hash + Long.hashCode(Math.round(value * scale));
    }

    static void syncUniverseToPlayers(MinecraftServer server) {
        if (server == null) return;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        UniverseSyncPayload payload = universePayload(server.overworld().getGameTime());
        for (ServerPlayer player : players) sendUniverse(player, payload);
    }

    static void syncUniverse(ServerPlayer player) {
        sendUniverse(player, universePayload(player.server.overworld().getGameTime()));
    }

    private static UniverseSyncPayload universePayload(long gameTime) {
        List<String> json = new ArrayList<>();
        for (GalaxyData galaxy : SpaceCatalog.galaxies().values()) {
            var encoded = GalaxyData.CODEC
                    .encodeStart(com.mojang.serialization.JsonOps.INSTANCE, galaxy);
            json.add(encoded.getOrThrow().toString());
        }
        return new UniverseSyncPayload(SpaceCatalog.revision(), gameTime, json);
    }

    private static void sendUniverse(ServerPlayer player, UniverseSyncPayload payload) {
        player.connection.send(new ClientboundCustomPayloadPacket(payload));
    }

    static void scheduleUniverseResync(ServerPlayer player) {
        PENDING_UNIVERSE_SYNCS.put(player.getUUID(),
                (long) player.server.getTickCount() + UNIVERSE_RESYNC_DELAY_TICKS);
    }

    static void resendPendingUniverseCatalogs(MinecraftServer server) {
        long tick = server.getTickCount();
        Iterator<Map.Entry<UUID, Long>> pending = PENDING_UNIVERSE_SYNCS.entrySet().iterator();
        while (pending.hasNext()) {
            Map.Entry<UUID, Long> entry = pending.next();
            if (entry.getValue() > tick) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) syncUniverse(player);
            pending.remove();
        }
    }
}
