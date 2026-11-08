package com.devourer.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Gota de sangue pesada: cai com gravidade e, ao tocar o chão, vira uma poça plana
 * (desenhada deitada no piso) que escurece e seca devagar.
 */
@Environment(EnvType.CLIENT)
public class BloodParticle extends SpriteBillboardParticle {
    private final SpriteProvider sp;
    private boolean stuck = false;
    private final float puddleSize;
    private final float rot;

    BloodParticle(ClientWorld w, double x, double y, double z, double vx, double vy, double vz, SpriteProvider sp) {
        super(w, x, y, z, vx, vy, vz);
        this.sp = sp;
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
        this.maxAge = 200;
        this.scale = 0.035f + random.nextFloat() * 0.04f;
        this.puddleSize = 0.09f + random.nextFloat() * 0.12f;
        this.rot = random.nextFloat() * 6.2831f;
        this.gravityStrength = 1.0f;
        this.collidesWithWorld = true;
        float shade = 0.5f + random.nextFloat() * 0.3f;
        this.setColor(shade, 0.03f, 0.04f);
        this.alpha = 0.95f;
        this.setSprite(sp.getSprite(0, 1)); // gota
    }

    @Override
    public void tick() {
        this.prevPosX = x;
        this.prevPosY = y;
        this.prevPosZ = z;
        if (age++ >= maxAge) {
            markDead();
            return;
        }
        if (!stuck) {
            velocityY -= 0.045 * gravityStrength;
            move(velocityX, velocityY, velocityZ);
            velocityX *= 0.97;
            velocityZ *= 0.97;
            if (age > 120) { markDead(); return; } // gota que nunca tocou o chão
            if (onGround) {
                stuck = true;
                velocityX = velocityY = velocityZ = 0;
                scale = puddleSize;
                alpha = 0.9f;
                setSprite(sp.getSprite(1, 1)); // poça
                maxAge = age + 160 + random.nextInt(120);
            }
        } else {
            // a poça cresce um pouco ao ser "absorvida" e seca escurecendo
            int left = maxAge - age;
            if (scale < puddleSize * 1.25f) scale += 0.0008f;
            red = Math.max(0.28f, red - 0.0006f);
            if (left < 60) alpha = 0.9f * (left / 60f);
        }
    }

    @Override
    public void buildGeometry(VertexConsumer vc, Camera camera, float td) {
        if (!stuck) {
            super.buildGeometry(vc, camera, td);
            return;
        }
        Vec3d cam = camera.getPos();
        float cx = (float) (MathHelper.lerp(td, prevPosX, x) - cam.getX());
        float cy = (float) (MathHelper.lerp(td, prevPosY, y) - cam.getY()) + 0.012f;
        float cz = (float) (MathHelper.lerp(td, prevPosZ, z) - cam.getZ());
        float s = scale, cs = MathHelper.cos(rot) * s, sn = MathHelper.sin(rot) * s;
        float u0 = getMinU(), u1 = getMaxU(), v0 = getMinV(), v1 = getMaxV();
        int l = getBrightness(td);
        // quad horizontal, girado por rot
        vc.vertex(cx + (-cs + sn), cy, cz + (-sn - cs)).texture(u1, v1).color(red, green, blue, alpha).light(l).next();
        vc.vertex(cx + (-cs - sn), cy, cz + (-sn + cs)).texture(u1, v0).color(red, green, blue, alpha).light(l).next();
        vc.vertex(cx + (cs - sn), cy, cz + (sn + cs)).texture(u0, v0).color(red, green, blue, alpha).light(l).next();
        vc.vertex(cx + (cs + sn), cy, cz + (sn - cs)).texture(u0, v1).color(red, green, blue, alpha).light(l).next();
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider sp;

        public Factory(SpriteProvider sp) {
            this.sp = sp;
        }

        @Override
        public Particle createParticle(DefaultParticleType t, ClientWorld w, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new BloodParticle(w, x, y, z, vx, vy, vz, sp);
        }
    }
}
