package com.encdata.astronautics;

import com.encdata.astronautics.server.AstronauticsPlayerTickHandler;
import com.encdata.astronautics.space.AstronauticsDimensionTransfer;
import com.encdata.astronautics.space.AstronauticsShipApi;
import com.encdata.astronautics.space.CelestialPositionResolver;
import com.encdata.astronautics.space.SpaceCatalog;
import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceGridAddress;
import com.encdata.astronautics.space.SpacePosition;
import com.encdata.astronautics.space.SpaceProjection;
import com.encdata.astronautics.space.SpaceShipIntegration;
import com.encdata.astronautics.space.SpaceVec3;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

final class AstronauticsCommands {

    private AstronauticsCommands() {}

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("astronautics")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("enter").executes(context -> enterSpace(context.getSource())))
                .then(Commands.literal("return").executes(context -> returnToEarth(context.getSource())))
                .then(Commands.literal("tp")
                        .then(Commands.argument("planet", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider
                                        .suggestResource(SpaceCatalog.bodyIds(), builder))
                                .executes(context -> teleportToPlanet(context.getSource(),
                                        ResourceLocationArgument.getId(context, "planet"), true))
                                .then(Commands.literal("exterior")
                                        .executes(context -> teleportToPlanet(context.getSource(),
                                                ResourceLocationArgument.getId(context, "planet"), false)))
                                .then(Commands.literal("inside")
                                        .executes(context -> teleportToPlanet(context.getSource(),
                                                ResourceLocationArgument.getId(context, "planet"), true))))));
    }

    private static int teleportToPlanet(CommandSourceStack source, ResourceLocation planetId, boolean inside) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;

        var planetData = SpaceCatalog.body(planetId).orElse(null);
        if (planetData == null) {
            source.sendFailure(Component.literal("Unknown Astronautics planet: " + planetId));
            return 0;
        }

        if (inside) {
            var dimensionId = planetData.dimension().orElse(null);
            if (dimensionId == null) {
                source.sendFailure(Component.literal("This body has no interior dimension."));
                return 0;
            }
            var dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
            ServerLevel destination = player.server.getLevel(dimension);
            if (destination == null) {
                source.sendFailure(Component.literal("Planet dimension is not available."));
                return 0;
            }
            teleport(player, destination, 0.5, 300.0, 0.5, false);
            return 1;
        }

        ServerLevel space = player.server.getLevel(SpaceConstants.SPACE_LEVEL);
        if (space == null) return 0;
        double timeDays = space.getDayTime() / 24000.0;
        SpaceVec3 position = CelestialPositionResolver.resolveBody(
                planetData.id(), SpaceCatalog.galaxies().values(), timeDays);
        if (position != null) {
            SpacePosition logicalPosition = SpacePosition.normalize(SpaceGridAddress.ORIGIN,
                    position.x(),
                    position.y() + planetData.radius() + AstronauticsPlayerTickHandler.PLANET_EXIT_CLEARANCE,
                    position.z());
            teleport(player, space, logicalPosition.x(), logicalPosition.y(), logicalPosition.z(), true);
            var state = Astronautics.SPACE_RUNTIME.addPlayer(player.getUUID());
            state.detachFromShip();
            state.setSpacePosition(logicalPosition);
            state.saveToPlayer(player);
            return 1;
        }
        source.sendFailure(Component.literal("Could not resolve the planet position."));
        return 0;
    }

    private static void teleport(ServerPlayer player, ServerLevel destination,
                                 double x, double y, double z, boolean noGravity) {
        player.teleportTo(destination, x, y, z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.setNoGravity(noGravity);
    }

    private static int returnToEarth(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)
                || !SpaceConstants.isSpace(player.level())
                || AstronauticsDimensionTransfer.isPlayerTransferPending(player.getUUID())) {
            return 0;
        }

        var state = Astronautics.SPACE_RUNTIME.addPlayer(player.getUUID());
        ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
        if (overworld == null) return 0;

        var nearbyShip = SpaceShipIntegration.findLiveShip(player, SpaceConstants.SHIP_SEARCH_DISTANCE);
        SpaceVec3 destinationRoot = returnLocalCoordinate(player, overworld);
        if (nearbyShip != null) {
            return AstronauticsShipApi.transferShip(player, overworld, destinationRoot, nearbyShip) ? 1 : 0;
        }

        player.changeDimension(new DimensionTransition(overworld, destinationRoot.toVec3(), Vec3.ZERO,
                player.getYRot(), player.getXRot(), DimensionTransition.DO_NOTHING));
        state.detachFromShip();
        player.setNoGravity(false);
        state.saveToPlayer(player);
        return 1;
    }

    private static SpaceVec3 returnLocalCoordinate(ServerPlayer player, ServerLevel destination) {
        SpaceVec3 universe = SpaceProjection.observerPosition(player).originFrame();
        double x = universe.x();
        double z = universe.z();
        var body = SpaceCatalog.bodyForDimension(destination.dimension()).orElse(null);
        if (body != null) {
            double timeDays = destination.getDayTime() / 24000.0;
            SpaceVec3 position = CelestialPositionResolver.resolveBody(
                    body.id(), SpaceCatalog.galaxies().values(), timeDays);
            if (position != null) {
                x -= position.x();
                z -= position.z();
            }
        }
        return new SpaceVec3(x, Astronautics.PLANET_ARRIVAL_Y, z);
    }

    private static int enterSpace(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)
                || SpaceConstants.isSpace(player.level())) return 0;

        ServerLevel space = player.server.getLevel(SpaceConstants.SPACE_LEVEL);
        if (space == null) return 0;
        var state = Astronautics.SPACE_RUNTIME.addPlayer(player.getUUID());
        SpacePosition logicalDeparture = AstronauticsPlayerTickHandler.logicalSpaceExitPosition(player);
        SpaceVec3 departure = new SpaceVec3(
                logicalDeparture.x(), logicalDeparture.y(), logicalDeparture.z());
        ServerSubLevel nearbyShip = SpaceShipIntegration.findLiveShip(player, SpaceConstants.SHIP_SEARCH_DISTANCE);
        if (nearbyShip != null) {
            if (!AstronauticsShipApi.transferShip(
                    player, space, departure, nearbyShip, logicalDeparture)) return 0;
            state.saveToPlayer(player);
            return 1;
        }

        state.setSpacePosition(logicalDeparture);
        player.teleportTo(space, departure.x(), departure.y(), departure.z(), player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.setNoGravity(true);
        return 1;
    }

}
