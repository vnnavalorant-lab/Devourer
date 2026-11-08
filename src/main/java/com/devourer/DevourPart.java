package com.devourer;

import net.minecraft.nbt.NbtCompound;

/** Uma parte arrancada de uma criatura e presa ao corpo do jogador. */
public record DevourPart(String species, PartType type, int seed) {
    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putString("s", species);
        n.putByte("t", (byte) type.ordinal());
        n.putInt("seed", seed);
        return n;
    }

    public static DevourPart fromNbt(NbtCompound n) {
        PartType[] v = PartType.values();
        int t = Math.floorMod(n.getByte("t"), v.length);
        return new DevourPart(n.getString("s"), v[t], n.getInt("seed"));
    }
}
