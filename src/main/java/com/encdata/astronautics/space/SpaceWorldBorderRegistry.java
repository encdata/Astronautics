package com.encdata.astronautics.space;

import net.minecraft.world.level.border.WorldBorder;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class SpaceWorldBorderRegistry {
    private static final Set<WorldBorder> SPACE_BORDERS =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    private SpaceWorldBorderRegistry() {}

    public static void mark(WorldBorder border) {
        SPACE_BORDERS.add(border);
    }

    public static boolean isSpace(WorldBorder border) {
        return SPACE_BORDERS.contains(border);
    }

    public static void unmark(WorldBorder border) {
        SPACE_BORDERS.remove(border);
    }

    public static void clear() {
        SPACE_BORDERS.clear();
    }
}
