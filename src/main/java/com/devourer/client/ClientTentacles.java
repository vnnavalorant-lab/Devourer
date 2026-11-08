package com.devourer.client;

import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** O que cada tentáculo de cada jogador está fazendo (vem do servidor) e o estado do HUD do próprio jogador. */
public final class ClientTentacles {
    public static final class View {
        public final byte[] mode = new byte[4];
        public final Vec3d[] target = new Vec3d[4];
        /** Alvo suavizado que o renderer usa (o alvo do servidor "salta", este desliza até ele). */
        public final Vec3d[] cur = new Vec3d[4];
        public long time;
        public boolean anchored;
    }

    private static final Map<UUID, View> VIEWS = new ConcurrentHashMap<>();

    /** Estado do HUD do jogador local. */
    public static int selected, level, cooldown, cooldownMax = 60, passiveMask, activeMask;

    public static View update(UUID id) {
        return VIEWS.computeIfAbsent(id, k -> new View());
    }

    /** Null se não houver dados recentes (o servidor manda a cada tick enquanto algo está ativo). */
    public static View get(UUID id, long worldTime) {
        View v = VIEWS.get(id);
        if (v == null || worldTime - v.time > 8) return null;
        return v;
    }

    public static void clear() {
        VIEWS.clear();
        selected = level = cooldown = passiveMask = activeMask = 0;
    }

    private ClientTentacles() {
    }
}
