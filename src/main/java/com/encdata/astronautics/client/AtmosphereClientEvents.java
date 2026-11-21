package com.encdata.astronautics.client;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.space.SpaceCatalog;
import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceFlightPhysics;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Astronautics.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class AtmosphereClientEvents {
    private AtmosphereClientEvents() {}

    @SubscribeEvent
    public static void computeFogColor(ViewportEvent.ComputeFogColor event) {
        Camera camera = event.getCamera();
        Entity entity = camera.getEntity();
        if (entity == null) return;
        Level level = entity.level();
        var dimension = level.dimension();
        boolean inSpace = SpaceConstants.isSpace(level);
        if (!dimension.equals(Level.OVERWORLD) && !inSpace
                && !SpaceCatalog.isPlanetDimension(dimension)) return;
        if (inSpace) {
            event.setRed(0.0f);
            event.setGreen(0.0f);
            event.setBlue(0.0f);
            return;
        }
        if (SpaceCatalog.usesCustomSkybox(dimension) && SpaceCatalog.isPlanetDimension(dimension)) {
            event.setRed(0.0f);
            event.setGreen(0.0f);
            event.setBlue(0.0f);
            return;
        }
        float localAtmosphere = localAtmosphereStrength(entity);
        if (localAtmosphere > 0.0f) {
            int color = SpaceCatalog.bodyForDimension(dimension)
                    .map(value -> value.visual().atmosphere().color()).orElse(0x000000);
            float red = ((color >> 16) & 255) / 255.0f;
            float green = ((color >> 8) & 255) / 255.0f;
            float blue = (color & 255) / 255.0f;
            float tint = Math.min(0.16f, localAtmosphere * 0.16f);
            event.setRed(event.getRed() * (1.0f - tint) + red * tint);
            event.setGreen(event.getGreen() * (1.0f - tint) + green * tint);
            event.setBlue(event.getBlue() * (1.0f - tint) + blue * tint);
            return;
        }
        double altitude = SpaceFlightPhysics.logicalAltitude(entity);
        if (altitude < SpaceFlightPhysics.GRAVITY_FADE_START) return;
        float fade = (float) SpaceFlightPhysics.gravityFade(altitude);
        event.setRed(event.getRed() * (1.0f - fade));
        event.setGreen(event.getGreen() * (1.0f - fade));
        event.setBlue(event.getBlue() * (1.0f - fade));
    }

    @SubscribeEvent
    public static void renderFog(ViewportEvent.RenderFog event) {
        Entity entity = event.getCamera().getEntity();
        if (entity == null) return;
        Level level = entity.level();
        var dimension = level.dimension();
        if (SpaceConstants.isSpace(level)) {
            event.setCanceled(true);
            return;
        }
        float localAtmosphere = localAtmosphereStrength(entity);
        if (localAtmosphere <= 0.0f) return;
        var body = SpaceCatalog.bodyForDimension(dimension);
        if (body.isEmpty()) return;
        var atmosphere = body.get().visual().atmosphere();
        float density = atmosphere.density() * localAtmosphere;
        if (density <= 0.001f) return;
        float attenuation = 1.0f - Math.clamp(density * atmosphere.horizonHaze() * 0.38f, 0.0f, 0.48f);
        float far = Math.max(64.0f, event.getFarPlaneDistance() * attenuation);
        event.setNearPlaneDistance(far * 0.72f);
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
    }

    static float localAtmosphereStrength(Entity observer) {
        var dimension = observer.level().dimension();
        if (!SpaceCatalog.usesCustomSkybox(dimension) || !SpaceCatalog.isPlanetDimension(dimension)) return 0.0f;

        var atmosphere = SpaceCatalog.bodyForDimension(dimension)
                .map(body -> body.visual().atmosphere())
                .orElse(null);
        if (atmosphere == null || !atmosphere.enabled()) return 0.0f;

        double altitude = SpaceFlightPhysics.logicalAltitude(observer);
        if (altitude >= SpaceFlightPhysics.GRAVITY_FADE_START) return 0.0f;
        return 1.0f;
    }
}
