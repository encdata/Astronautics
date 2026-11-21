package com.encdata.astronautics.space;

import com.encdata.astronautics.Astronautics;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class SpaceConstants {
    public static final double SHIP_SEARCH_DISTANCE = 32.0;
    public static final double SHIP_GRID_ATTACH_RANGE = 1_000.0;
    public static final double SHIP_TRACKING_RANGE = 10_000.0;
    public static final ResourceKey<Level> SPACE_LEVEL = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "space")
    );

    private SpaceConstants() {}

    public static boolean isSpace(Level level) {
        return level.dimension().equals(SPACE_LEVEL) || isGridLevel(level.dimension());
    }

    public static boolean isGridLevel(ResourceKey<Level> level) {
        return level.location().getNamespace().equals(Astronautics.MOD_ID)
                && level.location().getPath().startsWith("grid/");
    }
}
