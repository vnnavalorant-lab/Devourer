package com.devourer;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModParticles {
    public static final DefaultParticleType BLOOD = FabricParticleTypes.simple();

    public static void register() {
        Registry.register(Registries.PARTICLE_TYPE, new Identifier(DevourerMod.ID, "blood_drop"), BLOOD);
    }

    private ModParticles() {
    }
}
