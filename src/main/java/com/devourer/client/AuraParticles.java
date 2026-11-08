package com.devourer.client;

import com.devourer.ModParticles;
import com.devourer.PlayerDevour;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.random.Random;

/**
 * Sangue pingando das partes coladas ao jogador (quanto mais partes, mais intenso).
 */
public final class AuraParticles {

    public static void tick(MinecraftClient mc) {
        ClientWorld w = mc.world;
        if (w == null || mc.isPaused()) return;
        Random r = w.getRandom();

        for (AbstractClientPlayerEntity p : w.getPlayers()) {
            if (p.isInvisible() || p.isSpectator()) continue;
            PlayerDevour d = ClientData.DATA.get(p.getUuid());
            int n = d == null ? 0 : d.parts.size();

            // sangue pingando das partes presas
            if (n > 0 && r.nextFloat() < Math.min(0.9f, 0.06f * n)) {
                w.addParticle(ModParticles.BLOOD,
                        p.getX() + (r.nextDouble() - 0.5) * 0.9, p.getY() + 0.3 + r.nextDouble() * 1.2,
                        p.getZ() + (r.nextDouble() - 0.5) * 0.9, 0, 0, 0);
            }
        }
    }

    private AuraParticles() {
    }
}
