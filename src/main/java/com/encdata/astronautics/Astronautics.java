package com.encdata.astronautics;

import com.encdata.astronautics.content.blocks.center_of_mass_core.CenterOfMassCoreBlockEntity;
import com.encdata.astronautics.content.misc.ShipAtmosphereSystem;
import com.encdata.astronautics.registry.AstronauticsBlockEntities;
import com.encdata.astronautics.registry.AstronauticsBlocks;
import com.encdata.astronautics.registry.AstronauticsCreativeTabs;
import com.encdata.astronautics.registry.AstronauticsFluids;
import com.encdata.astronautics.registry.AstronauticsForceGroups;
import com.encdata.astronautics.registry.AstronauticsItems;
import com.encdata.astronautics.registry.AstronauticsMenus;
import com.encdata.astronautics.registry.client.AstronauticsParticles;
import com.encdata.astronautics.registry.client.AstronauticsSounds;
import com.encdata.astronautics.server.AstronauticsPlayerTickHandler;
import com.encdata.astronautics.server.AstronauticsServerRegistrations;
import com.encdata.astronautics.space.AstronauticsDimensionTransfer;
import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.ReentrySystem;
import com.encdata.astronautics.space.SableSpacePhysicsBridge;
import com.encdata.astronautics.space.SpaceGrids;
import com.encdata.astronautics.space.SpaceRuntime;
import com.encdata.astronautics.space.SpaceGridAddress;
import com.encdata.astronautics.space.SpaceGridLevelFactory;
import com.encdata.astronautics.space.SpaceShipActivation;
import com.encdata.astronautics.space.SpaceShipPlanetEntry;
import com.encdata.astronautics.space.SpaceWorldBorderRegistry;
import com.encdata.astronautics.world.ModDensityFunctions;
import com.encdata.astronautics.world.ModWorldCarvers;
import com.mojang.logging.LogUtils;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import org.slf4j.Logger;

@Mod(Astronautics.MOD_ID)
public final class Astronautics {
    public static final String MOD_ID = "astronautics";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final SpaceRuntime SPACE_RUNTIME = new SpaceRuntime();
    private static volatile MinecraftServer ACTIVE_SERVER;
    public static final double PLANET_ARRIVAL_Y = 8_000.0;

    public Astronautics(IEventBus modEventBus) {
        AstronauticsForceGroups.register(modEventBus);
        AstronauticsBlocks.BLOCKS.register(modEventBus);
        AstronauticsBlocks.ITEMS.register(modEventBus);
        AstronauticsBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        AstronauticsItems.ITEMS.register(modEventBus);
        AstronauticsMenus.MENUS.register(modEventBus);
        AstronauticsSounds.SOUNDS.register(modEventBus);
        AstronauticsParticles.PARTICLES.register(modEventBus);
        AstronauticsCreativeTabs.register(modEventBus);
        ModWorldCarvers.WORLD_CARVERS.register(modEventBus);
        ModDensityFunctions.DENSITY_FUNCTIONS.register(modEventBus);
        AstronauticsFluids.FLUID_TYPES.register(modEventBus);
        AstronauticsFluids.FLUIDS.register(modEventBus);
        AstronauticsFluids.BLOCKS.register(modEventBus);
        AstronauticsFluids.ITEMS.register(modEventBus);
        modEventBus.addListener(AstronauticsNetworkSync::registerPayloads);
        modEventBus.addListener(AstronauticsServerRegistrations::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(AstronauticsServerRegistrations::registerReloadListeners);
        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(this::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(SpaceGrids::onEntityJoin);
        SableSpacePhysicsBridge.register();
    }

    private void onServerStarted(ServerStartedEvent event) {
        ACTIVE_SERVER = event.getServer();
        AstronauticsDimensionTransfer.resetShutdownState();
        SpaceGridLevelFactory.getOrCreate(event.getServer(), SpaceGridAddress.ORIGIN);
    }

    private void onServerStopping(ServerStoppingEvent event) {
        AstronauticsDimensionTransfer.beginShutdown();
        SpaceGridLevelFactory.flushPending(event.getServer());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        SpaceShipActivation.reset();
        AstronauticsDimensionTransfer.resetShutdownState();
        AstronauticsNetworkSync.clear();
        ReentrySystem.clearStates();
        ShipAtmosphereSystem.clear();
        SPACE_RUNTIME.clear();
        SpaceWorldBorderRegistry.clear();
        ACTIVE_SERVER = null;
    }

    private void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof Level level)) return;
        CenterOfMassCoreBlockEntity.unload(level);
        if (SpaceConstants.isSpace(level)) {
            SpaceWorldBorderRegistry.unmark(level.getWorldBorder());
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        AstronauticsDimensionTransfer.tick();
        ServerLevel spaceLevel = server.getLevel(SpaceConstants.SPACE_LEVEL);
        if (spaceLevel != null) {
            SpaceShipActivation.tick(spaceLevel);
            SpaceShipPlanetEntry.tick(spaceLevel);
        }
        ShipAtmosphereSystem.tickAll(server.getTickCount());
        ReentrySystem.expireStates(server.getTickCount());
        AstronauticsNetworkSync.resendPendingUniverseCatalogs(server);
        if (server.getTickCount() % 5 == 0) AstronauticsNetworkSync.syncReentryStates(server);
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncPlayerAfterLogin(player);
    }

    private void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncPlayerAfterLogin(player);
    }

    private void syncPlayerAfterLogin(ServerPlayer player) {
        AstronauticsNetworkSync.syncUniverse(player);
        AstronauticsNetworkSync.scheduleUniverseResync(player);
        AstronauticsNetworkSync.syncReentryStatesTo(player, true);
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (SpaceConstants.isSpace(player.level())
                && player.getVehicle() != null) player.stopRiding();
        SPACE_RUNTIME.removePlayer(player.getUUID());
        AstronauticsNetworkSync.removePlayer(player.getUUID());
    }

    public static void syncUniverseToPlayers() {
        AstronauticsNetworkSync.syncUniverseToPlayers(ACTIVE_SERVER);
    }

    private void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AstronauticsPlayerTickHandler.tick(player, AstronauticsNetworkSync::syncBreathability);
            AstronauticsNetworkSync.syncSpaceGrid(player);
        }
    }

    private void registerCommands(RegisterCommandsEvent event) {
        AstronauticsCommands.register(event);
    }
}
