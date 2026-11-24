package com.encdata.astronautics.server;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.advancement.AstronauticsAdvancementService;
import com.encdata.astronautics.content.misc.ShipAtmosphereSystem;
import com.encdata.astronautics.space.AstronauticsDimensionTransfer;
import com.encdata.astronautics.space.AstronauticsShipApi;
import com.encdata.astronautics.space.CelestialPositionResolver;
import com.encdata.astronautics.space.SpaceCatalog;
import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceFlightPhysics;
import com.encdata.astronautics.space.SpaceGridAddress;
import com.encdata.astronautics.space.SpaceGridDirectory;
import com.encdata.astronautics.space.SpaceGridLevelFactory;
import com.encdata.astronautics.space.SpaceGridShipData;
import com.encdata.astronautics.space.SpaceGridShipState;
import com.encdata.astronautics.space.SpaceGrids;
import com.encdata.astronautics.space.SpacePlayerState;
import com.encdata.astronautics.space.SpaceShipIntegration;
import com.encdata.astronautics.space.SpacePosition;
import com.encdata.astronautics.space.SpaceProjection;
import com.encdata.astronautics.space.SpaceVec3;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.List;
import java.util.function.Consumer;

public final class AstronauticsPlayerTickHandler {
    private static final double PLANET_ENTRY_CLEARANCE = 150.0;
    private static final int SHIP_FRAME_JOIN_TICKS = 1;
    public static final double PLANET_EXIT_CLEARANCE = 1_000.0;
    private static final SpaceVec3 PLANET_REENTRY_ENTRY =
            new SpaceVec3(0.5, Astronautics.PLANET_ARRIVAL_Y, 0.5);

    private AstronauticsPlayerTickHandler() {}

    public static void tick(ServerPlayer player, Consumer<ServerPlayer> syncBreathability) {
        Level level = player.level();
        ShipAtmosphereSystem.tickPlayer(player);
        syncBreathability.accept(player);
        AstronauticsAdvancementService.tick(player);

        var state = Astronautics.SPACE_RUNTIME.addPlayer(player.getUUID());
        if (!state.nbtLoaded()) {
            state.loadFromTag(player.getPersistentData().getCompound(SpacePlayerState.PERSISTENT_DATA_KEY));
        }
        if (SpaceConstants.isSpace(level)) {
            var containing = SpaceShipIntegration.containing(player);
            if (!(containing instanceof ServerSubLevel ship)
                    || !SpaceShipIntegration.isWithinRange(
                    player, ship, SpaceConstants.SHIP_GRID_ATTACH_RANGE)) {
                var container = SubLevelContainer.getContainer(level);
                var remembered = container == null || state.shipId() == null
                        ? null : container.getSubLevel(state.shipId());
                containing = remembered instanceof ServerSubLevel ship
                        && SpaceShipIntegration.isWithinRange(player, ship, SpaceConstants.SHIP_GRID_ATTACH_RANGE)
                        ? ship
                        : SpaceShipIntegration.findLiveShip(player, SpaceConstants.SHIP_GRID_ATTACH_RANGE);
            }
            boolean attachedInMainSpace = containing instanceof ServerSubLevel
                    && SpaceGrids.isMainSpace(level);
            boolean detachedFromShip = false;
            if (!(containing instanceof ServerSubLevel)) state.countOffShipFrame(false);
            if (containing instanceof ServerSubLevel ship) {
                SpaceGridAddress shipGrid = SpaceGrids.isMainSpace(level)
                        ? SpaceGrids.shipGrid((ServerLevel) level, ship) : null;
                boolean offShipFrame = shipGrid != null && !player.isPassenger()
                        && !shipGrid.equals(SpaceGrids.frameOf(player));
                if (state.countOffShipFrame(offShipFrame) >= SHIP_FRAME_JOIN_TICKS) {
                    SpaceGrids.joinShipFrame(player, ship);
                    state.countOffShipFrame(false);
                }
                if (shipGrid != null) {
                    SpaceGridAddress simulationGrid = SpaceGrids.simulationGrid((ServerLevel) level, ship);
                    if (!simulationGrid.equals(SpaceGrids.of(player))) SpaceGrids.set(player, simulationGrid);
                }
                var pose = ship.logicalPose().position();
                state.setShipId(ship.getUniqueId());
                var local = new Vector3d(
                        player.getX() - pose.x,
                        player.getY() - pose.y,
                        player.getZ() - pose.z);
                state.setLocalPosition(new SpaceVec3(local.x, local.y, local.z));
                state.setSpacePosition(playerSpacePosition(player, ship, state));
            } else if (state.shipId() != null && SpaceGrids.isMainSpace(level)) {
                var container = SubLevelContainer.getContainer(level);
                var remembered = container == null ? null : container.getSubLevel(state.shipId());
                if (remembered instanceof ServerSubLevel ship && !ship.isRemoved()
                        && ship.getLevel() == level) {
                    state.setSpacePosition(playerSpacePosition(player, ship, state));
                }
                state.detachFromShip();
                detachedFromShip = true;
            }
            SpaceGridAddress playerGrid = attachedInMainSpace
                    ? SpaceGrids.shipGrid((ServerLevel) level, (ServerSubLevel) containing)
                    : detachedFromShip ? state.spacePosition().grid()
                    : SpaceGrids.isMainSpace(level) ? player.isPassenger()
                    ? SpaceProjection.frameGrid(player) : SpaceGrids.playerAnchorGrid(player)
                    : SpaceGridLevelFactory.addressOf(player.server, level.dimension());
            if (playerGrid == null) {
                playerGrid = SpaceProjection.frameGrid(state.spacePosition(),
                        player.getX(), player.getY(), player.getZ());
            }
            if (SpaceGrids.isMainSpace(level) && !detachedFromShip) {
                SpaceGrids.alignPlayerFrame(player, playerGrid);
            }
            SpacePosition position = detachedFromShip
                    ? state.spacePosition()
                    : SpacePosition.normalize(playerGrid, player.getX(), player.getY(), player.getZ());
            boolean wrap = !attachedInMainSpace
                    && (!SpaceGrids.isMainSpace(level) || detachedFromShip)
                    && (position.x() != player.getX() || position.y() != player.getY()
                    || position.z() != player.getZ());
            if (wrap) {
                if (SpaceGrids.isMainSpace(level)) SpaceGrids.rebasePlayer(player, position.grid());
                if (SpaceGrids.isMainSpace(level) && !player.isPassenger()) {
                    SpaceGrids.moveToFrame(player, position.x(), position.y(), position.z());
                } else {
                    Vec3 velocity = player.getDeltaMovement();
                    player.teleportTo(player.serverLevel(), position.x(), position.y(), position.z(),
                            player.getYRot(), player.getXRot());
                    player.setDeltaMovement(velocity);
                }
            }
            if (SpaceGrids.isMainSpace(level)) {
                SpaceGridAddress ownerGrid = attachedInMainSpace
                        ? SpaceGrids.simulationGrid((ServerLevel) level, (ServerSubLevel) containing)
                        : playerGrid;
                SpaceGrids.set(player, ownerGrid);
            }
            if (!attachedInMainSpace) state.setSpacePosition(position);
        }
        if (SpaceCatalog.isPlanetDimension(level.dimension())
                && SpaceFlightPhysics.logicalAltitude(player)
                >= SpaceFlightPhysics.SPACE_ALTITUDE) {
            if (AstronauticsDimensionTransfer.isPlayerTransferPending(player.getUUID())) {
                return;
            }
            ServerLevel spaceLevel = player.server.getLevel(SpaceConstants.SPACE_LEVEL);
            if (spaceLevel == null) return;

            SpacePosition logicalDeparture = logicalSpaceExitPosition(player);
            SpaceVec3 departure = new SpaceVec3(
                    logicalDeparture.x(), logicalDeparture.y(), logicalDeparture.z());
            state.setSpacePosition(logicalDeparture);
            var nearbyShip = SpaceShipIntegration.findLiveShip(player, SpaceConstants.SHIP_SEARCH_DISTANCE);
            if (nearbyShip != null) {
                if (AstronauticsShipApi.transferShip(
                        player, spaceLevel, departure, nearbyShip, logicalDeparture)) {
                    state.saveToPlayer(player);
                } else {
                    Astronautics.LOGGER.warn("Automatic ship transfer failed for player {}", player.getGameProfile().getName());
                }
                return;
            }
            player.changeDimension(new DimensionTransition(
                    spaceLevel,
                    new Vec3(departure.x(), departure.y(), departure.z()),
                    Vec3.ZERO,
                    player.getYRot(),
                    player.getXRot(),
                    DimensionTransition.DO_NOTHING
            ));
            state.saveToPlayer(player);
            return;
        }

        if (SpaceConstants.isSpace(level) && enterNearbyPlanetSlice(player, state)) {
            return;
        }
        state.saveToPlayer(player);
    }

    private static SpacePosition playerSpacePosition(ServerPlayer player, ServerSubLevel ship,
                                                     SpacePlayerState playerState) {
        ServerLevel spaceLevel = player.server.getLevel(SpaceConstants.SPACE_LEVEL);
        if (spaceLevel == null) return playerState.spacePosition();
        SpaceGridDirectory directory = SpaceGridDirectory.get(spaceLevel);
        SpaceGridAddress address = directory.shipAddress(ship.getUniqueId());
        if (address == null) address = playerState.spacePosition().grid();

        SpaceGridShipState shipState = null;
        ServerLevel stateLevel = SpaceGridLevelFactory.getOrCreate(player.server, address);
        shipState = SpaceGridShipData.get(stateLevel).get(ship.getUniqueId());
        var pose = ship.logicalPose().position();
        if (shipState == null) {
            return SpacePosition.normalize(address,
                    player.getX(), player.getY(), player.getZ());
        }

        SpaceGridAddress playerFrame = SpaceProjection.frameGrid(playerState.spacePosition(),
                player.getX(), player.getY(), player.getZ());
        SpaceGridAddress shipFrame = SpaceProjection.frameGrid(
                new SpacePosition(address, shipState.x(), shipState.y(), shipState.z()), pose.x, pose.y, pose.z);
        return SpacePosition.normalize(address,
                shipState.x() + player.getX() - pose.x
                        + playerFrame.x().subtract(shipFrame.x()).doubleValue() * SpacePosition.GRID_SIZE,
                shipState.y() + player.getY() - pose.y
                        + playerFrame.y().subtract(shipFrame.y()).doubleValue() * SpacePosition.GRID_SIZE,
                shipState.z() + player.getZ() - pose.z
                        + playerFrame.z().subtract(shipFrame.z()).doubleValue() * SpacePosition.GRID_SIZE);
    }

    private static boolean enterNearbyPlanetSlice(ServerPlayer player,
                                                  SpacePlayerState state) {
        if (AstronauticsDimensionTransfer.isPlayerTransferPending(player.getUUID())) {
            return false;
        }

        List<ResourceLocation> bodyIds = SpaceCatalog.bodyIds();
        if (bodyIds.isEmpty()) return false;

        int lane = Math.floorMod(player.server.getTickCount(), 3);
        double timeDays = player.serverLevel().getDayTime() / 24000.0;
        SpaceVec3 playerPosition = state.spacePosition().originFrame();
        double playerShipRadius = shipBoundingRadius(player);
        for (int index = lane; index < bodyIds.size(); index += 3) {
            var bodyResult = SpaceCatalog.body(bodyIds.get(index));
            if (bodyResult.isEmpty()) continue;
            var body = bodyResult.get();
            var dimension = body.dimension();
            if (dimension.isEmpty()) continue;

            SpaceVec3 bodyPosition = CelestialPositionResolver.resolveBody(
                    body.id(), SpaceCatalog.galaxies().values(), timeDays);
            if (bodyPosition == null) continue;

            double dx = playerPosition.x() - bodyPosition.x();
            double dy = playerPosition.y() - bodyPosition.y();
            double dz = playerPosition.z() - bodyPosition.z();
            double entryRadius = body.radius() + PLANET_ENTRY_CLEARANCE + playerShipRadius;
            if (dx * dx + dy * dy + dz * dz > entryRadius * entryRadius) continue;

            var destinationKey = ResourceKey.create(
                    Registries.DIMENSION, dimension.get());
            ServerLevel destination = player.server.getLevel(destinationKey);
            if (destination == null) return false;

            var ship = SpaceShipIntegration.findLiveShip(player, SpaceConstants.SHIP_SEARCH_DISTANCE);
            if (ship != null) {
                return AstronauticsShipApi.transferShip(
                        player, destination, PLANET_REENTRY_ENTRY, ship);
            }

            player.changeDimension(new DimensionTransition(destination, PLANET_REENTRY_ENTRY.toVec3(), Vec3.ZERO,
                    player.getYRot(), player.getXRot(), DimensionTransition.DO_NOTHING));
            player.setNoGravity(false);
            state.detachFromShip();
            state.saveToPlayer(player);
            return true;
        }
        return false;
    }

    private static double shipBoundingRadius(Player player) {
        var subLevel = SpaceShipIntegration.containing(player);
        if (!(subLevel instanceof ServerSubLevel serverSubLevel)) return 0.0;
        var bounds = serverSubLevel.getPlot().getBoundingBox();
        double x = bounds.maxX() - bounds.minX();
        double y = bounds.maxY() - bounds.minY();
        double z = bounds.maxZ() - bounds.minZ();
        return Math.sqrt(x * x + y * y + z * z) * 0.5;
    }

    public static SpaceVec3 spaceExitPosition(Player player) {
        SpaceVec3 fallback = new SpaceVec3(player.getX(), player.getY() + 100.0, player.getZ());
        var bodyResult = SpaceCatalog.bodyForDimension(player.level().dimension());
        if (bodyResult.isEmpty()) return fallback;
        var body = bodyResult.get();

        double timeDays = player.level().getDayTime() / 24000.0;
        SpaceVec3 position = CelestialPositionResolver.resolveBody(
                body.id(), SpaceCatalog.galaxies().values(), timeDays);
        if (position == null) return fallback;
        return position.add(new SpaceVec3(0.0,
                body.radius() + PLANET_EXIT_CLEARANCE + shipBoundingRadius(player), 0.0));
    }

    public static SpacePosition logicalSpaceExitPosition(Player player) {
        SpaceVec3 position = spaceExitPosition(player);
        return SpacePosition.normalize(SpaceGridAddress.ORIGIN,
                position.x(), position.y(), position.z());
    }

}
