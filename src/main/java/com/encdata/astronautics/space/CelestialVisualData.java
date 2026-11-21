package com.encdata.astronautics.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

public record CelestialVisualData(
        Optional<ResourceLocation> texture,
        Optional<ResourceLocation> emissiveTexture,
        Optional<ResourceLocation> normalTexture,
        Optional<ResourceLocation> specularTexture,
        boolean glow,
        double rotationPeriod,
        float emissiveStrength,
        boolean emissiveNightOnly,
        boolean flipHorizontal,
        CelestialRenderData render,
        List<TextureLayerData> layers
) {
    private static final double FULL_ROTATION_RADIANS = Math.PI * 2.0;
    private static final CelestialVisualData DEFAULT = new CelestialVisualData(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            false, 0.0, 1.0f, false, false, CelestialRenderData.defaults(), List.of()
    );

    public static final Codec<CelestialVisualData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("texture").forGetter(CelestialVisualData::texture),
            ResourceLocation.CODEC.optionalFieldOf("emissive_texture").forGetter(CelestialVisualData::emissiveTexture),
            ResourceLocation.CODEC.optionalFieldOf("normal_texture").forGetter(CelestialVisualData::normalTexture),
            ResourceLocation.CODEC.optionalFieldOf("specular_texture").forGetter(CelestialVisualData::specularTexture),
            Codec.BOOL.optionalFieldOf("glow", DEFAULT.glow()).forGetter(CelestialVisualData::glow),
            Codec.DOUBLE.optionalFieldOf("rotation_period", DEFAULT.rotationPeriod())
                    .forGetter(CelestialVisualData::rotationPeriod),
            Codec.FLOAT.optionalFieldOf("emissive_strength", DEFAULT.emissiveStrength())
                    .forGetter(CelestialVisualData::emissiveStrength),
            Codec.BOOL.optionalFieldOf("emissive_night_only", DEFAULT.emissiveNightOnly())
                    .forGetter(CelestialVisualData::emissiveNightOnly),
            Codec.BOOL.optionalFieldOf("flip_horizontal", DEFAULT.flipHorizontal())
                    .forGetter(CelestialVisualData::flipHorizontal),
            CelestialRenderData.CODEC.optionalFieldOf("render", DEFAULT.render())
                    .forGetter(CelestialVisualData::render),
            TextureLayerData.CODEC.listOf().optionalFieldOf("layers", DEFAULT.layers())
                    .forGetter(CelestialVisualData::layers)
    ).apply(instance, CelestialVisualData::new));

    public static CelestialVisualData defaults() {
        return DEFAULT;
    }

    public CelestialLightingData lighting() { return render.lighting(); }
    public AtmosphereData atmosphere() { return render.atmosphere(); }
    public CelestialGeometryData geometry() { return render.geometry(); }

    public float rotationAt(double timeDays) {
        if (rotationPeriod <= 0.0) {
            return 0.0f;
        }
        return (float) ((timeDays / rotationPeriod) * FULL_ROTATION_RADIANS);
    }
}
