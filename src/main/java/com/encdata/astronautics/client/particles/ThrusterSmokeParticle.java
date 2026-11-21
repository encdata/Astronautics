package com.encdata.astronautics.client.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public final class ThrusterSmokeParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    private ThrusterSmokeParticle(ClientLevel level, double x, double y, double z,
                                  double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd);
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = 24 + this.random.nextInt(8);
        this.quadSize = 0.22F;
        this.alpha = 0.0F;
        this.rCol = 0.56F;
        this.gCol = 0.59F;
        this.bCol = 0.64F;
        this.hasPhysics = false;
        this.setSpriteFromAge(sprites);
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
        float progress = this.age / (float) this.lifetime;
        this.alpha = 0.44F * Math.min(1.0F, progress * 7.0F) * Math.min(1.0F, (1.0F - progress) * 3.0F);
        this.quadSize = 0.22F + progress * 0.72F;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.94D;
        this.yd *= 0.94D;
        this.zd *= 0.94D;
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new ThrusterSmokeParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
