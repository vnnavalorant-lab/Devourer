package com.devourer.client;

import com.devourer.PlayerDevour;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientData {
    public static final Map<UUID, PlayerDevour> DATA = new ConcurrentHashMap<>();

    private ClientData() {
    }
}
