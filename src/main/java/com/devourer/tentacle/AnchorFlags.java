package com.devourer.tentacle;

import net.minecraft.entity.player.PlayerEntity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quem está com a Âncora de Aderência ativa. Há dois conjuntos porque no jogo single-player cliente e servidor
 * rodam na mesma JVM: o servidor escreve em SERVER, os pacotes recebidos no cliente escrevem em CLIENT.
 * O mixin de LivingEntity#isClimbing consulta o conjunto do lado em que está rodando.
 */
public final class AnchorFlags {
    private static final Set<UUID> SERVER = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> CLIENT = ConcurrentHashMap.newKeySet();

    public static void setServer(UUID id, boolean on) {
        if (on) SERVER.add(id);
        else SERVER.remove(id);
    }

    public static void setClient(UUID id, boolean on) {
        if (on) CLIENT.add(id);
        else CLIENT.remove(id);
    }

    public static void clearClient() {
        CLIENT.clear();
    }

    public static boolean isAnchored(PlayerEntity p) {
        return p.getWorld().isClient ? CLIENT.contains(p.getUuid()) : SERVER.contains(p.getUuid());
    }

    private AnchorFlags() {
    }
}
