package com.encdata.astronautics.client;

import com.encdata.astronautics.space.SpaceCatalog;
import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceFlightPhysics;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;

public final class SpaceSkyClient {
    static final double CELESTIAL_VISIBILITY_START = 1000.0;

    private SpaceSkyClient() {}

    public static boolean shouldSuppressVanillaSky(Camera camera) {
        Entity entity = camera.getEntity();
        if (entity == null) return false;
        if (SpaceConstants.isSpace(entity.level())) return true;

        var dimension = entity.level().dimension();
        boolean customSky = SpaceCatalog.usesCustomSkybox(dimension);
        boolean planet = SpaceCatalog.isPlanetDimension(dimension);
        if (!customSky && !planet) return false;

        double altitude = SpaceFlightPhysics.logicalAltitude(entity);
        return (customSky && altitude < SpaceFlightPhysics.SPACE_ALTITUDE)
                || (planet && altitude >= SpaceSkyClient.CELESTIAL_VISIBILITY_START);
    }
}
