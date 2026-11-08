package com.devourer;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class Net {
    public static final Identifier SYNC = new Identifier(DevourerMod.ID, "sync");

    /** Envia os dados de {@code target} para {@code receiver}. */
    public static void sync(ServerPlayerEntity target, ServerPlayerEntity receiver) {
        MinecraftServer s = target.getServer();
        if (s == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(target.getUuid());
        buf.writeNbt(DevourState.of(s).get(target.getUuid()).toNbt());
        ServerPlayNetworking.send(receiver, SYNC, buf);
    }

    public static void syncToAll(ServerPlayerEntity p) {
        sync(p, p);
        for (ServerPlayerEntity o : PlayerLookup.tracking(p)) sync(p, o);
    }

    private Net() {
    }
}
