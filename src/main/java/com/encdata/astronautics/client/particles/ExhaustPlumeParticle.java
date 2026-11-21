package com.encdata.astronautics.client.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;

public final class ExhaustPlumeParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float scale;
    private double plumeXd;
    private double plumeYd;
    private double plumeZd;

    private ExhaustPlumeParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                                 float scale, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd);
        this.sprites = sprites;
        this.scale = scale * 0.7F;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.plumeXd = xd;
        this.plumeYd = yd;
        this.plumeZd = zd;
        this.lifetime = 38 + this.random.nextInt(13);
        this.hasPhysics = false;
        this.quadSize = scale * 2.0F;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        float t = this.age / (float) this.lifetime;
        this.quadSize = this.scale * 4.0F * (0.5F + 1.1F * (1.0F - (1.0F - t) * (1.0F - t)));

        if (t < 0.2F) {
            float k = t / 0.2F;
            setColor(Mth.lerp(k, 0.25F, 0.85F), Mth.lerp(k, 0.55F, 0.92F), 1.0F);
        } else if (t < 0.55F) {
            float k = (t - 0.2F) / 0.35F;
            setColor(Mth.lerp(k, 0.85F, 1.0F), Mth.lerp(k, 0.92F, 0.28F),
                    Mth.lerp(k, 1.0F, 0.08F));
        } else {
            float k = (t - 0.55F) / 0.45F;
            setColor(Mth.lerp(k, 1.0F, 0.52F), Mth.lerp(k, 0.28F, 0.03F),
                    Mth.lerp(k, 0.08F, 0.01F));
        }
        this.alpha = 0.9F;
        this.xd = this.plumeXd;
        this.yd = this.plumeYd;
        this.zd = this.plumeZd;
        this.move(this.xd, this.yd, this.zd);
        this.plumeXd *= 0.992D;
        this.plumeYd *= 0.992D;
        this.plumeZd *= 0.992D;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 15728880;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<ExhaustParticleOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(ExhaustParticleOptions options, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new ExhaustPlumeParticle(level, x, y, z, xd, yd, zd, options.scale(), sprites);
        }
    }
}
