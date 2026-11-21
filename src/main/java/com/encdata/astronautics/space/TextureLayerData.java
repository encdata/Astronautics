package com.encdata.astronautics.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

public record TextureLayerData(
        ResourceLocation texture,
        String type,
        float opacity,
        double rotationPeriod,
        double radiusOffset,
        double uSpeed,
        double vSpeed
) {
    public static final Codec<TextureLayerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("texture").forGetter(TextureLayerData::texture),
            Codec.STRING.optionalFieldOf("type", "surface").forGetter(TextureLayerData::type),
            Codec.FLOAT.optionalFieldOf("opacity", 1.0f).forGetter(TextureLayerData::opacity),
            Codec.DOUBLE.optionalFieldOf("rotation_period", 0.0).forGetter(TextureLayerData::rotationPeriod),
            Codec.DOUBLE.optionalFieldOf("radius_offset", 0.1).forGetter(TextureLayerData::radiusOffset),
            Codec.DOUBLE.optionalFieldOf("u_speed", 0.0).forGetter(TextureLayerData::uSpeed),
            Codec.DOUBLE.optionalFieldOf("v_speed", 0.0).forGetter(TextureLayerData::vSpeed)
    ).apply(instance, TextureLayerData::new));
}
