package com.encdata.astronautics.client.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

public final class ExhaustCloudParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float scale;
    private double driftXd;
    private double driftYd;
    private double driftZd;

    private ExhaustCloudParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                                 float scale, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd);
        this.sprites = sprites;
        this.scale = scale;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.driftXd = xd;
        this.driftYd = yd;
        this.driftZd = zd;
        this.lifetime = 50 + this.random.nextInt(50) + (int) (scale * 15);
        this.hasPhysics = false;
        this.friction = 0.97F;
        this.gravity = 0.0F;
        float grey = 0.42F + this.random.nextFloat() * 0.2F;
        setColor(grey, grey, grey * 1.03F);
        this.alpha = 0.9F;
        this.quadSize = scale * 0.6F;
        this.roll = this.random.nextFloat() * (float) Math.PI * 2;
        this.oRoll = this.roll;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oRoll = this.roll;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        float t = this.age / (float) this.lifetime;
        this.quadSize = this.scale * (0.6F + 2.9F * (1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t)));
        this.alpha = 0.9F;
        this.roll += 0.004F;
        this.driftYd += 0.0025D;
        this.xd = this.driftXd;
        this.yd = this.driftYd;
        this.zd = this.driftZd;
        this.move(this.xd, this.yd, this.zd);
        this.driftXd *= this.friction;
        this.driftYd *= this.friction;
        this.driftZd *= this.friction;
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
            return new ExhaustCloudParticle(level, x, y, z, xd, yd, zd, options.scale(), sprites);
        }
    }
}
