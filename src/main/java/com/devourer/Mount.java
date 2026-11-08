package com.devourer;

import net.minecraft.util.math.random.Random;

/** Onde a parte devorada fica presa no jogador. Nunca na cabeça. */
public enum Mount {
    BACK("nas costas"),
    ARM_R("no braço direito"),
    ARM_L("no braço esquerdo"),
    LEG_R("na perna direita"),
    LEG_L("na perna esquerda");

    public final String label;

    Mount(String label) {
        this.label = label;
    }

    /** Costas têm mais chance, já que cabem mais membros ali. */
    public static Mount random(Random r) {
        Mount[] pool = {BACK, BACK, BACK, ARM_R, ARM_L, LEG_R, LEG_L};
        return pool[r.nextInt(pool.length)];
    }
}
