package com.encdata.astronautics.space;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class AstronauticsShipApi {
    private AstronauticsShipApi() {}

    public static ServerSubLevel findShip(Entity entity, double maxDistance) {
        return SpaceShipIntegration.findLiveShip(entity, maxDistance);
    }

    public static boolean transferShip(ServerPlayer player, ServerLevel destination,
                                       SpaceVec3 destinationRoot) {
        return AstronauticsDimensionTransfer.transferShip(player, destination, destinationRoot);
    }

    public static boolean transferShip(ServerPlayer player, ServerLevel destination,
                                       SpaceVec3 destinationRoot, ServerSubLevel ship) {
        return AstronauticsDimensionTransfer.transferShip(player, destination, destinationRoot, ship);
    }

    public static boolean transferShip(ServerPlayer player, ServerLevel destination,
                                       SpaceVec3 destinationRoot, ServerSubLevel ship,
                                       SpacePosition logicalDestinationRoot) {
        return AstronauticsDimensionTransfer.transferShip(
                player, destination, destinationRoot, ship, logicalDestinationRoot);
    }
}
