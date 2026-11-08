package com.devourer;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PlayerDevour {
    public static final int MAX_PARTS = 14;

    public final List<DevourPart> parts = new ArrayList<>();
    /** ids das espécies cujo poder já foi absorvido (1 por espécie). */
    public final Set<String> powers = new LinkedHashSet<>();
    public int selected = 0;

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        NbtList pl = new NbtList();
        for (DevourPart p : parts) pl.add(p.toNbt());
        n.put("parts", pl);
        NbtList wl = new NbtList();
        for (String s : powers) wl.add(NbtString.of(s));
        n.put("powers", wl);
        n.putInt("selected", selected);
        return n;
    }

    public static PlayerDevour fromNbt(NbtCompound n) {
        PlayerDevour d = new PlayerDevour();
        NbtList pl = n.getList("parts", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < pl.size(); i++) d.parts.add(DevourPart.fromNbt(pl.getCompound(i)));
        NbtList wl = n.getList("powers", NbtElement.STRING_TYPE);
        for (int i = 0; i < wl.size(); i++) d.powers.add(wl.getString(i));
        d.selected = n.getInt("selected");
        return d;
    }
}
