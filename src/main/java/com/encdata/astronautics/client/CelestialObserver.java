package com.encdata.astronautics.client;

import com.encdata.astronautics.space.GalaxyData;
import com.encdata.astronautics.space.SpaceCatalog;
import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceFlightPhysics;
import com.encdata.astronautics.space.SpaceGridAddress;
import com.encdata.astronautics.space.SpacePosition;
import com.encdata.astronautics.space.SpaceVec3;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class CelestialObserver {
    private CelestialObserver() {}

    static SpaceVec3 spacePosition(Entity observer, Camera camera, Quaternionf skyOrientation) {
        SpaceVec3 observerPosition = renderPosition(observer, skyOrientation);
        Vec3 cameraOffset = camera.getPosition().subtract(observer.position());
        return observerPosition.add(new SpaceVec3(cameraOffset.x, cameraOffset.y, cameraOffset.z));
    }

    static SpaceVec3 universePosition(Entity observer) {
        SpaceVec3 position = bodyPosition(observer);
        if (!SpaceConstants.isSpace(observer.level())) return position;
        SpaceGridAddress grid;
        if (SpaceGridClientState.received()) {
            grid = SpaceGridClientState.address();
        } else {
            grid = SpaceGridAddress.ORIGIN;
        }
        return SpacePosition.normalize(grid, position.x(), position.y(), position.z()).originFrame();
    }

    static SpaceVec3 bodyPosition(Entity observer) {
        if (SpaceConstants.isSpace(observer.level())) {
            return new SpaceVec3(observer.getX(), observer.getY(), observer.getZ());
        }

        var bodyData = SpaceCatalog.bodyForDimension(observer.level().dimension()).orElse(null);
        if (bodyData != null) {
            double timeDays = timeDays(observer.level());
            SpaceVec3 planetPosition = CelestialGalaxyCache.bodyPosition(
                    bodyData.id(), timeDays);
            if (planetPosition != null) {
                if (bodyData.environment().customSkybox()
                        && SpaceFlightPhysics.logicalAltitude(observer) < SpaceFlightPhysics.GRAVITY_FADE_START) {
                    return planetPosition;
                }
                return planetPosition.add(new SpaceVec3(observer.getX(), observer.getY(), observer.getZ()));
            }
        }
        return new SpaceVec3(observer.getX(), observer.getY(), observer.getZ());
    }

    static double timeDays(Level level) {
        return level.getDayTime() / 24000.0;
    }

    static Quaternionf skyOrientation(Entity observer) {
        var bodyData = SpaceCatalog.bodyForDimension(observer.level().dimension()).orElse(null);
        if (bodyData == null) return new Quaternionf();
        if (!bodyData.environment().customSkybox()) return new Quaternionf();

        double timeDays = timeDays(observer.level());
        SpaceVec3 bodyPosition = null;
        SpaceVec3 sunPosition = null;
        for (CelestialGalaxyCache.ResolvedGalaxy resolved :
                CelestialGalaxyCache.resolve(timeDays)) {
            GalaxyData galaxy = resolved.galaxy();
            var positions = resolved.positions();
            SpaceVec3 foundBody = positions.get(bodyData.id());
            if (foundBody == null) continue;
            bodyPosition = foundBody;
            for (GalaxyData.StarData star : galaxy.stars()) {
                sunPosition = positions.get(star.id());
                if (sunPosition != null) break;
            }
            break;
        }
        if (bodyPosition == null || sunPosition == null) return new Quaternionf();

        SpaceVec3 sunVector = sunPosition.subtract(bodyPosition);
        Vector3f source = new Vector3f((float) sunVector.x(), (float) sunVector.y(), (float) sunVector.z());
        if (source.lengthSquared() < 1.0e-8f) return new Quaternionf();
        source.normalize();

        String axisName = bodyData.environment().skyRotationAxis().toLowerCase(java.util.Locale.ROOT);
        Vector3f axis = switch (axisName) {
            case "x" -> new Vector3f(1.0f, 0.0f, 0.0f);
            case "y" -> new Vector3f(0.0f, 1.0f, 0.0f);
            default -> new Vector3f(0.0f, 0.0f, 1.0f);
        };

        Vector3f horizon = axis.z != 0.0f ? new Vector3f(1.0f, 0.0f, 0.0f)
                : new Vector3f(0.0f, 0.0f, 1.0f);

        float dayAngle = bodyData.visual().rotationAt(timeDays);

        Quaternionf faceSunAtHorizon = new Quaternionf().rotationTo(source, horizon);
        Quaternionf rotateDay = new Quaternionf().rotateAxis(dayAngle, axis.x, axis.y, axis.z);
        return rotateDay.mul(faceSunAtHorizon);
    }

    private static SpaceVec3 renderPosition(Entity observer, Quaternionf skyOrientation) {
        var bodyData = SpaceCatalog.bodyForDimension(observer.level().dimension()).orElse(null);
        if (bodyData != null) {
            if (bodyData.environment().customSkybox()
                    && SpaceFlightPhysics.logicalAltitude(observer) >= SpaceFlightPhysics.GRAVITY_FADE_START) {
                double timeDays = timeDays(observer.level());
                SpaceVec3 planetPosition = CelestialGalaxyCache.bodyPosition(bodyData.id(), timeDays);
                if (planetPosition != null) {
                    Vector3f renderDownOffset = new Vector3f(0.0f,
                            (float) (bodyData.radius() + 1000.0), 0.0f);
                    skyOrientation.transformInverse(renderDownOffset);
                    return planetPosition.add(new SpaceVec3(renderDownOffset.x,
                            renderDownOffset.y, renderDownOffset.z));
                }
            }
        }
        return bodyPosition(observer);
    }
}
