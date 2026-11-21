package com.encdata.astronautics.client.particles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ExhaustParticleOptions(ParticleType<ExhaustParticleOptions> type, float scale) implements ParticleOptions {
    @Override
    public ParticleType<ExhaustParticleOptions> getType() {
        return type;
    }

    public static final class Type extends ParticleType<ExhaustParticleOptions> {
        private final MapCodec<ExhaustParticleOptions> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, ExhaustParticleOptions> streamCodec;

        public Type() {
            super(true);
            this.codec = Codec.FLOAT.fieldOf("scale").xmap(scale -> new ExhaustParticleOptions(this, scale),
                    ExhaustParticleOptions::scale);
            this.streamCodec = ByteBufCodecs.FLOAT.map(scale -> new ExhaustParticleOptions(this, scale),
                    ExhaustParticleOptions::scale).cast();
        }

        @Override
        public MapCodec<ExhaustParticleOptions> codec() {
            return codec;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, ExhaustParticleOptions> streamCodec() {
            return streamCodec;
        }
    }
}
