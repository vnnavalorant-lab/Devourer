package com.devourer.tentacle;

import com.devourer.DevourerMod;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Pacotes das habilidades de tentáculo. */
public final class TentacleNet {
    /** Cliente -> servidor: byte 1 = tecla de uso apertada, 0 = solta. */
    public static final Identifier USE = new Identifier(DevourerMod.ID, "t_use");
    /** Cliente -> servidor: byte +1 / -1 = próxima / anterior habilidade. */
    public static final Identifier CYCLE = new Identifier(DevourerMod.ID, "t_cycle");
    /** Servidor -> clientes que veem o jogador: o que cada tentáculo deve fazer. */
    public static final Identifier STATE = new Identifier(DevourerMod.ID, "t_state");
    /** Servidor -> dono: habilidade selecionada, nível, recarga e passivas ligadas (para o HUD). */
    public static final Identifier HUD = new Identifier(DevourerMod.ID, "t_hud");

    private static PacketByteBuf buildState(ServerPlayerEntity p, TentacleRt rt) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(p.getUuid());
        buf.writeBoolean(rt.anchored);
        for (int i = 0; i < TentacleRt.CHAINS; i++) {
            buf.writeByte(rt.mode[i]);
            if (rt.mode[i] == TentacleRt.M_TARGET) {
                Vec3d t = rt.target[i];
                buf.writeDouble(t.x);
                buf.writeDouble(t.y);
                buf.writeDouble(t.z);
            }
        }
        return buf;
    }

    public static void sendState(ServerPlayerEntity p, TentacleRt rt) {
        // um buffer novo por destinatário (o envio consome o buffer)
        ServerPlayNetworking.send(p, STATE, buildState(p, rt));
        for (ServerPlayerEntity o : PlayerLookup.tracking(p)) {
            ServerPlayNetworking.send(o, STATE, buildState(p, rt));
        }
    }

    public static void sendHud(ServerPlayerEntity p, int selected, int level, int cooldown, int cooldownMax,
                               int passiveMask, int activeMask) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(selected);
        buf.writeByte(level);
        buf.writeShort(cooldown);
        buf.writeShort(cooldownMax);
        buf.writeShort(passiveMask);
        buf.writeShort(activeMask);
        ServerPlayNetworking.send(p, HUD, buf);
    }

    private TentacleNet() {
    }
}
