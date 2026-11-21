package com.encdata.astronautics.client;

import com.encdata.astronautics.space.AtmosphereData;
import com.encdata.astronautics.space.CelestialBodyData;
import com.encdata.astronautics.space.SpaceCatalog;
import com.encdata.astronautics.space.SpaceFlightPhysics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class AtmosphericLightTransmission {
    static final double PLANET_RADIUS_KM = 6371.0;
    private static final int SUN_SAMPLES = 16;
    private static Entity cachedObserver;
    private static Level cachedLevel;
    private static int cachedTick = Integer.MIN_VALUE;
    private static long cachedDayTime = Long.MIN_VALUE;
    private static double cachedAltitude = Double.NaN;
    private static SunState cachedSunState;

    private AtmosphericLightTransmission() {}

    public static Vector3f transmittance(Entity observer, Vector3fc sunDirection) {
        return calculate(observer, sunDirection).transmittance();
    }

    public static float alignSkyBrightness(Entity observer, float vanillaBrightness) {
        if (observer == null || !SpaceCatalog.usesCustomSkybox(observer.level().dimension())) {
            return vanillaBrightness;
        }
        SunState sun = sunState(observer);
        if (sun == null) return vanillaBrightness;
        float daylight = Math.clamp((sun.direction().y + 0.12f) / 0.24f, 0.0f, 1.0f);
        return daylight * daylight * (3.0f - 2.0f * daylight);
    }

    public static Vector3f filterSkyLight(Entity observer, Vector3f skyLight) {
        if (observer == null || !SpaceCatalog.usesCustomSkybox(observer.level().dimension())) return skyLight;
        SunState sun = sunState(observer);
        if (sun == null || sun.atmosphere().occluded()) return skyLight;
        return skyLight.mul(sun.atmosphere().transmittance()).mul(sun.color());
    }

    private static SunState sunState(Entity observer) {
        Level level = observer.level();
        int tick = observer.tickCount;
        long dayTime = level.getDayTime();
        double altitude = observer.getY();
        if (cachedObserver == observer && cachedLevel == level && cachedTick == tick
                && cachedDayTime == dayTime && Double.compare(cachedAltitude, altitude) == 0) {
            return cachedSunState;
        }
        cachedObserver = observer;
        cachedLevel = level;
        cachedTick = tick;
        cachedDayTime = dayTime;
        cachedAltitude = altitude;
        CelestialBodyData body = SpaceCatalog.bodyForDimension(level.dimension()).orElse(null);
        CelestialLighting.SunSource source = CelestialLighting.primarySun(body, level);
        if (body == null || source == null) {
            cachedSunState = null;
            return null;
        }
        Vector3f direction = new Vector3f(source.direction());
        CelestialObserver.skyOrientation(observer).transform(direction).normalize();
        cachedSunState = new SunState(direction, source.color(), calculate(observer, body, direction));
        return cachedSunState;
    }

    private static Sample calculate(Entity observer, Vector3fc sunDirection) {
        if (observer == null || sunDirection == null) return new Sample(new Vector3f(1.0f), false);
        CelestialBodyData body = SpaceCatalog.bodyForDimension(observer.level().dimension()).orElse(null);
        if (body == null) return new Sample(new Vector3f(1.0f), false);
        return calculate(observer, body, sunDirection);
    }

    private static Sample calculate(Entity observer, CelestialBodyData body, Vector3fc sunDirection) {
        AtmosphereData atmosphere = body.visual().atmosphere();
        var scattering = atmosphere.scattering();
        float strength = AtmosphereClientEvents.localAtmosphereStrength(observer);
        if (!atmosphere.enabled() || !scattering.enabled() || strength <= 0.0f) {
            return new Sample(new Vector3f(1.0f), false);
        }

        Vector3f sun = new Vector3f(sunDirection).normalize();
        double thicknessKm = Math.max(0.001,
                atmosphere.height() / Math.max(body.radius(), 1.0) * PLANET_RADIUS_KM);
        double altitudeKm = Math.clamp(SpaceFlightPhysics.logicalAltitude(observer)
                / SpaceFlightPhysics.SPACE_ALTITUDE, 0.0, 1.0)
                * thicknessKm;
        double observerRadius = PLANET_RADIUS_KM + altitudeKm;
        double sunProjection = observerRadius * sun.y;
        double planetDiscriminant = sunProjection * sunProjection
                - (observerRadius * observerRadius - PLANET_RADIUS_KM * PLANET_RADIUS_KM);
        if (planetDiscriminant >= 0.0) {
            double nearPlanet = -sunProjection - Math.sqrt(planetDiscriminant);
            if (nearPlanet > 1.0e-6) {
                return new Sample(new Vector3f(0.0f), true);
            }
        }

        double atmosphereRadius = PLANET_RADIUS_KM + thicknessKm;
        double atmosphereDiscriminant = sunProjection * sunProjection
                - (observerRadius * observerRadius - atmosphereRadius * atmosphereRadius);
        if (atmosphereDiscriminant <= 0.0) return new Sample(new Vector3f(1.0f), false);
        double distanceToExit = -sunProjection + Math.sqrt(atmosphereDiscriminant);
        double stepKm = distanceToExit / SUN_SAMPLES;
        double rayleighDepth = 0.0;
        double mieDepth = 0.0;
        double rayleighScaleHeight = Math.max(scattering.rayleighScaleHeight(), 0.001);
        double mieScaleHeight = Math.max(scattering.mieScaleHeight(), 0.001);
        for (int index = 0; index < SUN_SAMPLES; index++) {
            double distance = (index + 0.5) * stepKm;
            double x = sun.x * distance;
            double y = observerRadius + sun.y * distance;
            double z = sun.z * distance;
            double heightKm = Math.max(Math.sqrt(x * x + y * y + z * z) - PLANET_RADIUS_KM, 0.0);
            rayleighDepth += Math.exp(-heightKm / rayleighScaleHeight) * stepKm;
            mieDepth += Math.exp(-heightKm / mieScaleHeight) * stepKm;
        }

        Vector3f transmittance = new Vector3f(
                (float) Math.exp(-scattering.rayleighRed() * rayleighDepth
                        - scattering.mieCoefficient() * mieDepth),
                (float) Math.exp(-scattering.rayleighGreen() * rayleighDepth
                        - scattering.mieCoefficient() * mieDepth),
                (float) Math.exp(-scattering.rayleighBlue() * rayleighDepth
                        - scattering.mieCoefficient() * mieDepth));
        transmittance.lerp(new Vector3f(1.0f), 1.0f - strength);
        return new Sample(transmittance, false);
    }

    private record SunState(Vector3f direction, Vector3f color, Sample atmosphere) {}
    private record Sample(Vector3f transmittance, boolean occluded) {}
}
