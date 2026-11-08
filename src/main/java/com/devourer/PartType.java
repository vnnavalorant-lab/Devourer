package com.devourer;

import net.minecraft.util.math.random.Random;

public enum PartType {
    HEAD("Cabeça"), ARM("Braço"), LEG("Perna"), TORSO("Tronco"), TAIL("Cauda"), EYE("Olho"), JAW("Mandíbula");

    public final String label;

    PartType(String label) {
        this.label = label;
    }

    public static PartType random(Random r) {
        PartType[] v = values();
        return v[r.nextInt(v.length)];
    }
}
