package com.encdata.astronautics.advancement;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.content.blocks.center_of_mass_core.CenterOfMassCoreBlockEntity;
import com.encdata.astronautics.content.blocks.rcs_thruster.RcsThrusterBlockEntity;
import com.encdata.astronautics.content.blocks.stabilizing_thruster.StabilizingThrusterBlockEntity;
import com.encdata.astronautics.content.blocks.atmospheric_control.AtmosphericControlUnitBlockEntity;
import com.encdata.astronautics.space.SpaceCatalog;
import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceShipActivation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

public final class AstronauticsAdvancementService {
    private static final String VISITED_OFFWORLD_TAG = "astronautics_visited_offworld";
    private static final int OUTPOST_SCAN_RADIUS = 8;
    private static final ResourceLocation MOON = ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "moon");
    private static final ResourceLocation MARS = ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "mars");
    private static final ResourceLocation VENUS = ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "venus");

    private AstronauticsAdvancementService() {}

    public static void tick(ServerPlayer player) {
        var dimension = player.level().dimension();
        var dimensionId = dimension.location();
        if (dimension.equals(SpaceConstants.SPACE_LEVEL)) {
            grant(player, "one_small_step", "space");
        } else if (dimensionId.equals(MOON)) {
            grant(player, "lunar_module", "moon");
            grant(player, "three_worlds", "moon");
        } else if (dimensionId.equals(MARS)) {
            grant(player, "red_frontier", "mars");
            grant(player, "three_worlds", "mars");
        } else if (dimensionId.equals(VENUS)) {
            grant(player, "clouded_world", "venus");
            grant(player, "three_worlds", "venus");
        }
        updateHomecomingAdvancement(player, dimension);

        if (isOtherWorld(dimension)
                && Sable.HELPER.getContaining(player) == null && hasPoweredOxygenOutpost(player)) {
            grant(player, "the_new_home", "planetary_outpost");
        }
        if (!dimension.equals(SpaceConstants.SPACE_LEVEL)) return;
        var ship = Sable.HELPER.getContaining(player);
        if (!(ship instanceof ServerSubLevel subLevel)
                || !SpaceShipActivation.isWithinRange(player, subLevel)) return;

        if (CenterOfMassCoreBlockEntity.findActiveCore(subLevel) != null) {
            grant(player, "ground_control", "ship_control");
        }

        boolean thruster = false;
        boolean stabilizing = false;
        for (var actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof RcsThrusterBlockEntity rcs && rcs.isActive()) thruster = true;
            if (actor instanceof StabilizingThrusterBlockEntity stabilizer && stabilizer.isActive()) stabilizing = true;
        }
        if (thruster) grant(player, "ignition", "thruster");
        if (stabilizing) grant(player, "steady_as_she_goes", "stabilization");
    }

    private static void updateHomecomingAdvancement(ServerPlayer player, ResourceKey<Level> dimension) {
        if (isOtherWorld(dimension)) {
            player.getPersistentData().putBoolean(VISITED_OFFWORLD_TAG, true);
        }
        if (dimension.equals(Level.OVERWORLD)
                && player.getPersistentData().getBoolean(VISITED_OFFWORLD_TAG)) {
            grant(player, "mankind_was_born_on_earth", "homecoming");
        }
    }

    private static boolean isOtherWorld(ResourceKey<Level> dimension) {
        return !dimension.equals(Level.OVERWORLD) && SpaceCatalog.isPlanetDimension(dimension);
    }

    private static boolean hasPoweredOxygenOutpost(ServerPlayer player) {
        Level level = player.level();
        BlockPos center = player.blockPosition();
        BlockPos min = center.offset(-OUTPOST_SCAN_RADIUS, -OUTPOST_SCAN_RADIUS, -OUTPOST_SCAN_RADIUS);
        BlockPos max = center.offset(OUTPOST_SCAN_RADIUS, OUTPOST_SCAN_RADIUS, OUTPOST_SCAN_RADIUS);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!(level.getBlockEntity(pos) instanceof AtmosphericControlUnitBlockEntity distributor)
                    || !distributor.isPowered() || distributor.oxygenTank().isEmpty()) continue;
            return true;
        }
        return false;
    }

    private static void grant(ServerPlayer player, String advancement, String criterion) {
        var holder = player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, advancement));
        if (holder != null) player.getAdvancements().award(holder, criterion);
    }
}
