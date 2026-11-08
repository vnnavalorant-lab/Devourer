package com.devourer;

import net.minecraft.nbt.NbtCompound;

/**
 * Um membro arrancado de uma criatura e preso ao corpo do jogador.
 * O cliente escolhe qual membro real do modelo da espécie desenhar (braço, perna, cauda...) a partir da seed.
 */
public record DevourPart(String species, Mount mount, int seed) {
    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putString("s", species);
        n.putByte("t", (byte) mount.ordinal());
        n.putInt("seed", seed);
        return n;
    }

    public static DevourPart fromNbt(NbtCompound n) {
        Mount[] v = Mount.values();
        // dados antigos (tipos de parte, incluindo cabeça) viram posições válidas no corpo
        int t = Math.floorMod(n.getByte("t"), v.length);
        return new DevourPart(n.getString("s"), v[t], n.getInt("seed"));
    }
}
