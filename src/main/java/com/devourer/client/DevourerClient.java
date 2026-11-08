package com.devourer.client;

import com.devourer.ModParticles;
import com.devourer.Net;
import com.devourer.PlayerDevour;
import com.devourer.client.gui.AbilitiesScreen;
import com.devourer.client.particle.BloodParticle;
import com.devourer.client.render.DevourFeature;
import com.devourer.client.render.ZombieFormFeature;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.nbt.NbtCompound;

import org.lwjgl.glfw.GLFW;

import java.util.UUID;

public class DevourerClient implements ClientModInitializer {
    private static KeyBinding abilitiesKey;

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

        abilitiesKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.devourer.abilities", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.devourer"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (abilitiesKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new AbilitiesScreen());
            }
            AuraParticles.tick(client);
        });

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof PlayerEntityRenderer pr) {
                helper.register(new DevourFeature(pr));
                helper.register(new ZombieFormFeature(pr));
            }
        });
    }
}
