package com.encdata.astronautics.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;

public final class MoonCraterDensityFunction extends BlendedNoise {
    private static final double EJECTA_REACH = 2.2;
    private static final double RIM_WIDTH = 0.16;
    private static final double MAX_DEPTH = 48.0;
    private static final double PEAK_RADIUS = 40.0;

    public static final MapCodec<MoonCraterDensityFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument")
                            .forGetter(function -> function.argument),
                    Codec.doubleRange(32.0, 4096.0).optionalFieldOf("spacing", 160.0)
                            .forGetter(function -> function.spacing),
                    Codec.doubleRange(2.0, 512.0).optionalFieldOf("min_radius", 8.0)
                            .forGetter(function -> function.minRadius),
                    Codec.doubleRange(2.0, 512.0).optionalFieldOf("max_radius", 32.0)
                            .forGetter(function -> function.maxRadius),
                    Codec.doubleRange(0.01, 1.0).optionalFieldOf("depth", 0.32)
                            .forGetter(function -> function.depth),
                    Codec.doubleRange(0.0, 1.0).optionalFieldOf("rim_height", 0.09)
                            .forGetter(function -> function.rimHeight),
                    Codec.doubleRange(0.0, 1.0).optionalFieldOf("chance", 0.6)
                            .forGetter(function -> function.chance),
                    Codec.LONG.optionalFieldOf("salt", 0L)
                            .forGetter(function -> function.salt)
            ).apply(instance, MoonCraterDensityFunction::new));
    public static final KeyDispatchDataCodec<MoonCraterDensityFunction> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private final long seed;
    private final DensityFunction argument;
    private final double spacing;
    private final double minRadius;
    private final double maxRadius;
    private final double depth;
    private final double rimHeight;
    private final double chance;
    private final long salt;

    public MoonCraterDensityFunction(DensityFunction argument, double spacing, double minRadius, double maxRadius,
                                     double depth, double rimHeight, double chance, long salt) {
        this(salt, argument, spacing, minRadius, maxRadius, depth, rimHeight, chance, salt);
    }

    private MoonCraterDensityFunction(long seed, DensityFunction argument, double spacing, double minRadius,
                                      double maxRadius, double depth, double rimHeight, double chance, long salt) {
        super(new XoroshiroRandomSource(seed), 0, 0, 0, 0, 0);
        this.seed = seed;
        this.argument = argument;
        this.spacing = spacing;
        this.minRadius = Math.min(minRadius, maxRadius);
        this.maxRadius = Math.min(Math.max(minRadius, maxRadius), spacing / (EJECTA_REACH * 1.05));
        this.depth = depth;
        this.rimHeight = rimHeight;
        this.chance = chance;
        this.salt = salt;
    }

    @Override
    public BlendedNoise withNewRandom(RandomSource random) {
        return new MoonCraterDensityFunction(random.nextLong() ^ salt, argument, spacing, minRadius, maxRadius,
                depth, rimHeight, chance, salt);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new MoonCraterDensityFunction(seed, argument.mapAll(visitor), spacing, minRadius,
                maxRadius, depth, rimHeight, chance, salt));
    }

    @Override
    public double compute(FunctionContext context) {
        double offset = offsetAt(context.blockX(), context.blockZ());
        if (Math.abs(offset) < 0.05) return argument.compute(context);
        int shiftedY = (int) Math.round(context.blockY() - offset);
        return argument.compute(new SinglePointContext(context.blockX(), shiftedY, context.blockZ()));
    }

    public double offsetAt(int blockX, int blockZ) {
        int cellX = (int) Math.floor(blockX / spacing);
        int cellZ = (int) Math.floor(blockZ / spacing);
        double bowl = 0.0;
        double raised = 0.0;

        for (int x = cellX - 1; x <= cellX + 1; x++) {
            for (int z = cellZ - 1; z <= cellZ + 1; z++) {
                long cellSeed = mix(seed, x, z);
                if (unit(cellSeed) >= chance) continue;

                double size = unit(cellSeed + 3L);
                double radius = minRadius + size * size * (maxRadius - minRadius);
                double margin = radius * EJECTA_REACH / spacing;
                double centerX = (x + margin + unit(cellSeed + 1L) * Math.max(0.0, 1.0 - 2.0 * margin)) * spacing;
                double centerZ = (z + margin + unit(cellSeed + 2L) * Math.max(0.0, 1.0 - 2.0 * margin)) * spacing;
                double dx = blockX - centerX;
                double dz = blockZ - centerZ;
                double distance = Math.sqrt(dx * dx + dz * dz) / radius;
                if (distance >= EJECTA_REACH) continue;

                double craterDepth = Math.min(MAX_DEPTH, radius * depth);
                double rim = radius * rimHeight;
                if (distance < 1.0) {
                    double floorEdge = radius >= PEAK_RADIUS ? 0.45 : 0.15;
                    bowl = Math.min(bowl, -craterDepth * (1.0 - smoothstep(floorEdge, 1.0, distance)));
                    if (radius >= PEAK_RADIUS) {
                        raised += craterDepth * 0.4 * Math.exp(-(distance * distance) / (0.14 * 0.14));
                    }
                }
                double fromRim = (distance - 1.0) / RIM_WIDTH;
                raised += rim * Math.exp(-fromRim * fromRim);
                if (distance > 1.0) {
                    raised += rim * 0.3 * (1.0 - smoothstep(1.0, EJECTA_REACH, distance));
                }
            }
        }
        return bowl + raised;
    }

    @Override
    public double minValue() {
        return argument.minValue();
    }

    @Override
    public double maxValue() {
        return argument.maxValue();
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = Math.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static long mix(long seed, int x, int z) {
        long value = seed ^ ((long) x * 0x9E3779B97F4A7C15L) ^ ((long) z * 0xC2B2AE3D27D4EB4FL);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static double unit(long value) {
        long mixed = mix(value, 0, 0);
        return (mixed >>> 11) * 0x1.0p-53;
    }
}
