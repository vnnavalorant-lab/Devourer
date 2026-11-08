package com.devourer.client;

import com.devourer.ModParticles;
import com.devourer.Net;
import com.devourer.PlayerDevour;
import com.devourer.client.particle.BloodParticle;
import com.devourer.client.render.DevourFeature;
import com.devourer.client.render.ZombieFormFeature;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.nbt.NbtCompound;

import java.util.UUID;

public class DevourerClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ParticleFactoryRegistry reg = ParticleFactoryRegistry.getInstance();
        reg.register(ModParticles.BLOOD, BloodParticle.Factory::new);

        ClientPlayNetworking.registerGlobalReceiver(Net.SYNC, (client, handler, buf, sender) -> {
            UUID id = buf.readUuid();
            NbtCompound nbt = buf.readNbt();
            client.execute(() -> ClientData.DATA.put(id, PlayerDevour.fromNbt(nbt == null ? new NbtCompound() : nbt)));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientData.DATA.clear());

        ClientTickEvents.END_CLIENT_TICK.register(AuraParticles::tick);

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof PlayerEntityRenderer pr) {
                helper.register(new DevourFeature(pr));
                helper.register(new ZombieFormFeature(pr));
            }
        });
    }
}
