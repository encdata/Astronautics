package com.encdata.astronautics.space;

import net.minecraft.world.phys.Vec3;

public record ReentrySurfaceSample(
        Vec3 position,
        Vec3 normal,
        float exposure
) {
}
