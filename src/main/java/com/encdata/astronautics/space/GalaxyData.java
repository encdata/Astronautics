package com.encdata.astronautics.space;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record GalaxyData(
        ResourceLocation id,
        String name,
        SpaceVec3 position,
        double timeScale,
        List<StarData> stars
) {
    public static final Codec<GalaxyData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(GalaxyData::id),
            Codec.STRING.fieldOf("name").forGetter(GalaxyData::name),
            SpaceVec3.CODEC.optionalFieldOf("position", SpaceVec3.ZERO).forGetter(GalaxyData::position),
            Codec.DOUBLE.optionalFieldOf("time_scale", 1.0)
                    .forGetter(GalaxyData::timeScale),
            StarData.CODEC.listOf().optionalFieldOf("stars", List.of())
                    .forGetter(GalaxyData::stars)
    ).apply(instance, GalaxyData::new));

    public static GalaxyData decode(JsonElement json) {
        return CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(IllegalArgumentException::new);
    }

    public record StarData(
            ResourceLocation id,
            String name,
            SpaceVec3 position,
            double radius,
            double mass,
            double gravityScale,
            double luminosity,
            double temperature,
            CelestialVisualData visual,
            List<CelestialBodyData> bodies
    ) {
        public static final Codec<StarData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(StarData::id),
                Codec.STRING.fieldOf("name").forGetter(StarData::name),
                SpaceVec3.CODEC.optionalFieldOf("position", SpaceVec3.ZERO).forGetter(StarData::position),
                Codec.DOUBLE.optionalFieldOf("radius", 1.0).forGetter(StarData::radius),
                Codec.DOUBLE.optionalFieldOf("mass", 0.0).forGetter(StarData::mass),
                Codec.DOUBLE.optionalFieldOf("gravity_scale", 1.0).forGetter(StarData::gravityScale),
                Codec.DOUBLE.optionalFieldOf("luminosity", 1.0).forGetter(StarData::luminosity),
                Codec.DOUBLE.optionalFieldOf("temperature", 5772.0).forGetter(StarData::temperature),
                CelestialVisualData.CODEC.optionalFieldOf("visual", CelestialVisualData.defaults())
                        .forGetter(StarData::visual),
                CelestialBodyData.CODEC.listOf().optionalFieldOf("bodies", List.of())
                        .forGetter(StarData::bodies)
        ).apply(instance, StarData::new));
    }
}
