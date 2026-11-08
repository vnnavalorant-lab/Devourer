package com.devourer;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;

public class PlayerDevour {
    public static final int MAX_PARTS = 14;

    public final List<DevourPart> parts = new ArrayList<>();
    /** Modelo zombie_form sobre o jogador (comando /infectado on|off). */
    public boolean infected = true;

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        NbtList pl = new NbtList();
        for (DevourPart p : parts) pl.add(p.toNbt());
        n.put("parts", pl);
        n.putBoolean("infected", infected);
        return n;
    }

    public static PlayerDevour fromNbt(NbtCompound n) {
        PlayerDevour d = new PlayerDevour();
        NbtList pl = n.getList("parts", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < pl.size(); i++) d.parts.add(DevourPart.fromNbt(pl.getCompound(i)));
        d.infected = !n.contains("infected") || n.getBoolean("infected");
        return d;
    }
}
