package com.devourer.client;

import com.devourer.PlayerDevour;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientData {
    public static final Map<UUID, PlayerDevour> DATA = new ConcurrentHashMap<>();

    /** Sem dados ainda = infectado (padrão). */
    public static boolean isInfected(UUID id) {
        PlayerDevour d = DATA.get(id);
        return d == null || d.infected;
    }

    private ClientData() {
    }
}
