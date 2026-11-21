package com.encdata.astronautics.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

public record SpaceVec3(double x, double y, double z) {
    private static final double NORMALIZATION_EPSILON = 1.0e-12;

    public static final Codec<SpaceVec3> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("x").forGetter(SpaceVec3::x),
            Codec.DOUBLE.fieldOf("y").forGetter(SpaceVec3::y),
            Codec.DOUBLE.fieldOf("z").forGetter(SpaceVec3::z)
    ).apply(instance, SpaceVec3::new));

    public static final SpaceVec3 ZERO = new SpaceVec3(0.0, 0.0, 0.0);

    public SpaceVec3 add(SpaceVec3 other) {
        return new SpaceVec3(x + other.x, y + other.y, z + other.z);
    }

    public SpaceVec3 subtract(SpaceVec3 other) {
        return new SpaceVec3(x - other.x, y - other.y, z - other.z);
    }

    public SpaceVec3 scale(double factor) {
        return new SpaceVec3(x * factor, y * factor, z * factor);
    }

    public SpaceVec3 normalize() {
        double length = Math.sqrt(x * x + y * y + z * z);
        return length < NORMALIZATION_EPSILON ? ZERO : scale(1.0 / length);
    }

    public Vec3 toVec3() {
        return new Vec3(x, y, z);
    }
}
