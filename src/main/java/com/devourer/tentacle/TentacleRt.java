package com.devourer.tentacle;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Estado de execução (não salvo) das habilidades de tentáculo de um jogador. */
public final class TentacleRt {
    public static final int CHAINS = 4;
    /** Modos que o cliente entende para cada tentáculo. */
    public static final byte M_DEFAULT = 0, M_TARGET = 1, M_ROOTS = 2, M_NERVOUS = 3;

    public int selected = 0;
    public final boolean[] passive = new boolean[TentacleAbility.ALL.length];
    public final int[] cooldown = new int[TentacleAbility.ALL.length];
    public final List<Job> jobs = new ArrayList<>();
    public final boolean[] busy = new boolean[CHAINS];

    // saída do tick (o que o cliente deve desenhar)
    public final byte[] mode = new byte[CHAINS];
    public final Vec3d[] target = new Vec3d[CHAINS];
    public boolean anchored;
    public boolean noGravSet;
    public boolean wasActive;
    public int hudTimer;
    public int lastHud = -1;

    public void resetOutput() {
        for (int i = 0; i < CHAINS; i++) {
            mode[i] = M_DEFAULT;
            target[i] = null;
        }
    }

    public void aim(int chain, Vec3d pos) {
        mode[chain] = M_TARGET;
        target[chain] = pos;
    }

    /** Reserva {@code n} tentáculos (preferindo os listados); devolve null se não houver livres suficientes. */
    public int[] claim(int n, int... prefer) {
        int[] out = new int[n];
        boolean[] taken = new boolean[CHAINS];
        int c = 0;
        for (int i : prefer) {
            if (c < n && !busy[i] && !taken[i]) {
                out[c++] = i;
                taken[i] = true;
            }
        }
        for (int i = 0; i < CHAINS && c < n; i++) {
            if (!busy[i] && !taken[i]) {
                out[c++] = i;
                taken[i] = true;
            }
        }
        if (c < n) return null;
        for (int i : out) busy[i] = true;
        return out;
    }

    public boolean anyFree() {
        for (boolean b : busy) if (!b) return true;
        return false;
    }

    public boolean isHeld(UUID id) {
        for (Job j : jobs) if (j.held.contains(id)) return true;
        return false;
    }

    public Job find(TentacleAbility a) {
        for (Job j : jobs) if (j.ability == a && !j.done) return j;
        return null;
    }
}
