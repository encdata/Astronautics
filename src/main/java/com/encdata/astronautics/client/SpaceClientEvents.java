package com.encdata.astronautics.client;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.client.CelestialGalaxyCache.ResolvedGalaxy;
import com.encdata.astronautics.client.sky.BackgroundStarRenderer;
import com.encdata.astronautics.network.ReentryStateStore;
import com.encdata.astronautics.space.GalaxyData;
import com.encdata.astronautics.space.SpaceCatalog;
import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceVec3;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

@EventBusSubscriber(modid = Astronautics.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class SpaceClientEvents {
    private record DirectionalStar(Vector3f direction, double irradiance) {}

    private SpaceClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        BackgroundStarRenderer.tick(minecraft.level != null,
                SpaceSkyClient.shouldSuppressVanillaSky(minecraft.gameRenderer.getMainCamera()));
        CelestialMeshCache.preloadTick();
        CelestialRenderEvents.warmShader();
        if (minecraft.level == null) {
            CelestialRenderEvents.levelChanged(null);
            ReentryStateStore.clear();
            SpaceGridClientState.clear();
            return;
        }
        if (CelestialRenderEvents.levelChanged(minecraft.level)) {
            ReentryStateStore.clear();
        }
        updateSpaceShaderLights(minecraft.level);
        AstronauticsLightingEngine.instance().tick(minecraft.level);
}
    static void updateSpaceShaderLights(Level level) {
        if (!SpaceConstants.isSpace(level)
                && !SpaceCatalog.usesCustomSkybox(level.dimension())) {
            return;
        }
        Entity observer = Minecraft.getInstance().getCameraEntity();
        if (observer == null) return;

        double timeDays = CelestialObserver.timeDays(level);
        SpaceVec3 receiver = CelestialObserver.universePosition(observer);
        DirectionalStar primary = null;
        DirectionalStar secondary = null;
        for (ResolvedGalaxy resolved : CelestialGalaxyCache.resolve(timeDays)) {
            for (GalaxyData.StarData star : resolved.galaxy().stars()) {
                SpaceVec3 position = resolved.positions().get(star.id());
                if (position == null) continue;
                double dx = position.x() - receiver.x();
                double dy = position.y() - receiver.y();
                double dz = position.z() - receiver.z();
                double distanceSquared = dx * dx + dy * dy + dz * dz;
                if (distanceSquared < 1.0e-8) continue;
                double inverseDistance = 1.0 / Math.sqrt(distanceSquared);
                double irradiance = CelestialLighting.starLuminosity(position) / distanceSquared;
                if (primary == null || Double.compare(irradiance, primary.irradiance()) > 0) {
                    secondary = primary;
                    primary = new DirectionalStar(starDirection(dx, dy, dz, inverseDistance), irradiance);
                } else if (secondary == null || Double.compare(irradiance, secondary.irradiance()) > 0) {
                    secondary = new DirectionalStar(starDirection(dx, dy, dz, inverseDistance), irradiance);
                }
            }
        }
        if (primary == null) return;
        Vector3f primaryDirection = new Vector3f(primary.direction());
        Vector3f secondaryDirection = secondary == null
                ? new Vector3f(primary.direction()).negate()
                : new Vector3f(secondary.direction());
        var celestialOrientation = CelestialObserver.skyOrientation(observer);
        celestialOrientation.transform(primaryDirection).normalize();
        celestialOrientation.transform(secondaryDirection).normalize();

        RenderSystem.setShaderLights(primaryDirection, secondaryDirection);
    }

    private static Vector3f starDirection(double dx, double dy, double dz, double inverseDistance) {
        return new Vector3f((float) (dx * inverseDistance), (float) (dy * inverseDistance),
                (float) (dz * inverseDistance));
    }

}
