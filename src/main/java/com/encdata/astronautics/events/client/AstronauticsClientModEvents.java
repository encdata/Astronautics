package com.encdata.astronautics.events.client;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.client.SpaceSuitArmorModel;
import com.encdata.astronautics.client.CelestialRenderEvents;
import com.encdata.astronautics.client.compat.PlayerTiltCompat;
import com.encdata.astronautics.client.particles.ThrusterFlameParticle;
import com.encdata.astronautics.client.particles.ThrusterSmokeParticle;
import com.encdata.astronautics.client.ponder.AstronauticsPonderPlugin;
import com.encdata.astronautics.client.screens.OxygenDistributorScreen;
import com.encdata.astronautics.content.blocks.center_of_mass_core.client.CenterOfMassCoreRenderer;
import com.encdata.astronautics.content.blocks.oxygen_compressor.client.OxygenCompressorScreen;
import com.encdata.astronautics.client.screens.ShipComputerScreen;
import com.encdata.astronautics.registry.AstronauticsBlockEntities;
import com.encdata.astronautics.registry.AstronauticsBlocks;
import com.encdata.astronautics.registry.AstronauticsMenus;
import com.encdata.astronautics.registry.client.AstronauticsParticles;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = Astronautics.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AstronauticsClientModEvents {
    private AstronauticsClientModEvents() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        CelestialRenderEvents.registerVeilHooks();
        PlayerTiltCompat.registerIfPresent();
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(AstronauticsBlocks.THRUSTER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(AstronauticsBlocks.STABILIZING_THRUSTER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(AstronauticsBlocks.OXYGEN_DISTRIBUTOR.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(AstronauticsBlocks.OXYGEN_COMPRESSOR.get(), RenderType.cutout());
            PonderIndex.addPlugin(new AstronauticsPonderPlugin());
        });
    }

    @SubscribeEvent
    public static void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AstronauticsBlockEntities.CENTER_OF_MASS_CORE.get(),
                CenterOfMassCoreRenderer::new);
    }

    @SubscribeEvent
    public static void registerModelLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SpaceSuitArmorModel.LAYER, SpaceSuitArmorModel::createLayer);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(AstronauticsMenus.OXYGEN_DISTRIBUTOR.get(), OxygenDistributorScreen::new);
        event.register(AstronauticsMenus.OXYGEN_COMPRESSOR.get(), OxygenCompressorScreen::new);
        event.register(AstronauticsMenus.SHIP_COMPUTER.get(), ShipComputerScreen::new);
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(AstronauticsParticles.THRUSTER_SMOKE.get(), ThrusterSmokeParticle.Provider::new);
        event.registerSpriteSet(AstronauticsParticles.THRUSTER_FLAME.get(), ThrusterFlameParticle.Provider::new);
        event.registerSpriteSet(AstronauticsParticles.EXHAUST_PLUME.get(),
                com.encdata.astronautics.client.particles.ExhaustPlumeParticle.Provider::new);
        event.registerSpriteSet(AstronauticsParticles.EXHAUST_CLOUD.get(),
                com.encdata.astronautics.client.particles.ExhaustCloudParticle.Provider::new);
    }
}
