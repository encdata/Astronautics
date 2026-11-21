package com.encdata.astronautics.client;

import com.encdata.astronautics.space.GalaxyData;
import com.encdata.astronautics.space.SpaceCatalog;
import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceVec3;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.DirectionalLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class AstronauticsLightingEngine {
    private static final AstronauticsLightingEngine INSTANCE = new AstronauticsLightingEngine();
    private LightRenderHandle<DirectionalLightData> primaryHandle;
    private LightRenderHandle<DirectionalLightData> secondaryHandle;
    private int lastPrimaryHash;
    private int lastSecondaryHash;

    private AstronauticsLightingEngine() {}

    public static AstronauticsLightingEngine instance() { return INSTANCE; }

    public void tick(Level currentLevel) {
        if (currentLevel == null || (!SpaceConstants.isSpace(currentLevel)
                && !SpaceCatalog.usesCustomSkybox(currentLevel.dimension()))) {
            clear();
            return;
        }
        Entity observer = Minecraft.getInstance().getCameraEntity();
        if (observer == null) return;
        double timeDays = CelestialObserver.timeDays(currentLevel);
        SpaceVec3 receiver = CelestialObserver.universePosition(observer);
        Quaternionf celestialOrientation = CelestialObserver.skyOrientation(observer);
        Candidate primary = null;
        Candidate secondary = null;
        for (CelestialGalaxyCache.ResolvedGalaxy resolved : CelestialGalaxyCache.resolve(timeDays)) {
            GalaxyData galaxy = resolved.galaxy();
            var positions = resolved.positions();
            for (GalaxyData.StarData star : galaxy.stars()) {
                SpaceVec3 position = positions.get(star.id());
                if (position == null) continue;
                double dx = position.x() - receiver.x();
                double dy = position.y() - receiver.y();
                double dz = position.z() - receiver.z();
                double distanceSquared = dx * dx + dy * dy + dz * dz;
                if (distanceSquared < 1.0e-8) continue;
                float scale = (float) (1.0 / Math.sqrt(distanceSquared));
                Vector3f direction = new Vector3f((float) (dx * scale),
                        (float) (dy * scale), (float) (dz * scale));
                celestialOrientation.transform(direction).normalize();
                Vector3f color = StarColor.fromTemperature(star.temperature())
                        .mul(AtmosphericLightTransmission.transmittance(observer, direction));
                Candidate candidate = new Candidate(direction,
                        color,
                        (float) Math.clamp(star.luminosity() / Math.max(1.0, distanceSquared) * 100_000.0, 0.05, 8.0));
                if (primary == null || candidate.brightness > primary.brightness) {
                    secondary = primary;
                    primary = candidate;
                } else if (secondary == null || candidate.brightness > secondary.brightness) {
                    secondary = candidate;
                }
            }
        }
        if (primary == null) {
            clear();
            return;
        }
        apply(primary, secondary == null ? new Candidate(new Vector3f(primary.direction).negate(),
                new Vector3f(primary.color).mul(0.15f), 0.0f) : secondary);
    }

    private void apply(Candidate primary, Candidate secondary) {
        int primaryHash = hash(primary);
        int secondaryHash = hash(secondary);
        if (primaryHandle == null) {
            DirectionalLightData data = new DirectionalLightData();
            setLight(data, primary);
            primaryHandle = VeilRenderSystem.renderer().getLightRenderer().addLight(data);
        } else if (primaryHash != lastPrimaryHash) {
            setLight(primaryHandle.getLightData(), primary);
            primaryHandle.markDirty();
        }
        if (secondaryHandle == null && secondary.brightness > 0.0f) {
            DirectionalLightData data = new DirectionalLightData();
            setLight(data, secondary);
            secondaryHandle = VeilRenderSystem.renderer().getLightRenderer().addLight(data);
        } else if (secondaryHandle != null && secondary.brightness <= 0.0f) {
            secondaryHandle.free();
            secondaryHandle = null;
        } else if (secondaryHandle != null && secondaryHash != lastSecondaryHash) {
            setLight(secondaryHandle.getLightData(), secondary);
            secondaryHandle.markDirty();
        }
        lastPrimaryHash = primaryHash;
        lastSecondaryHash = secondaryHash;
    }

    private static void setLight(DirectionalLightData data, Candidate candidate) {
        data.setDirection(candidate.direction)
                .setColor(candidate.color.x, candidate.color.y, candidate.color.z)
                .setBrightness(candidate.brightness);
    }

    public void clear() {
        if (primaryHandle != null) primaryHandle.free();
        if (secondaryHandle != null) secondaryHandle.free();
        primaryHandle = null;
        secondaryHandle = null;
        lastPrimaryHash = 0;
        lastSecondaryHash = 0;
    }

    private static int hash(Candidate candidate) {
        int result = 17;
        result = 31 * result + Float.floatToIntBits(candidate.direction.x);
        result = 31 * result + Float.floatToIntBits(candidate.direction.y);
        result = 31 * result + Float.floatToIntBits(candidate.direction.z);
        result = 31 * result + Float.floatToIntBits(candidate.color.x);
        result = 31 * result + Float.floatToIntBits(candidate.color.y);
        result = 31 * result + Float.floatToIntBits(candidate.color.z);
        return 31 * result + Float.floatToIntBits(candidate.brightness);
    }

    private record Candidate(Vector3f direction, Vector3f color, float brightness) {}
}
