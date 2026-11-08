package com.devourer;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Dados salvos no mundo (sobrevivem a reiniciar o jogo). */
public class DevourState extends PersistentState {
    private final Map<UUID, PlayerDevour> map = new HashMap<>();

    public PlayerDevour get(UUID id) {
        return map.computeIfAbsent(id, k -> new PlayerDevour());
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound players = new NbtCompound();
        map.forEach((k, v) -> players.put(k.toString(), v.toNbt()));
        nbt.put("players", players);
        return nbt;
    }

    public static DevourState fromNbt(NbtCompound nbt) {
        DevourState s = new DevourState();
        NbtCompound players = nbt.getCompound("players");
        for (String key : players.getKeys()) {
            try {
                s.map.put(UUID.fromString(key), PlayerDevour.fromNbt(players.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return s;
    }

    public static DevourState of(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(DevourState::fromNbt, DevourState::new, "devourer_state");
    }
}
