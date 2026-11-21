package com.encdata.astronautics.client;

import com.encdata.astronautics.space.SpaceCatalog;
import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceFlightPhysics;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;

public final class SpaceSkyClient {
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
                || (planet && altitude >= SpaceFlightPhysics.GRAVITY_FADE_START);
    }
}
