package com.devourer.client;

import com.devourer.ModParticles;
import com.devourer.Net;
import com.devourer.PlayerDevour;
import com.devourer.client.gui.AbilitiesScreen;
import com.devourer.client.particle.BloodParticle;
import com.devourer.client.render.DevourFeature;
import com.devourer.client.render.ZombieFormFeature;
import com.devourer.tentacle.AnchorFlags;
import com.devourer.tentacle.TentacleAbility;
import com.devourer.tentacle.TentacleNet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
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
    private static KeyBinding abilitiesKey, useKey, nextKey, prevKey;
    private static boolean useWasDown;

    @Override
    public void onInitializeClient() {
        ParticleFactoryRegistry reg = ParticleFactoryRegistry.getInstance();
        reg.register(ModParticles.BLOOD, BloodParticle.Factory::new);

        ClientPlayNetworking.registerGlobalReceiver(Net.SYNC, (client, handler, buf, sender) -> {
            UUID id = buf.readUuid();
            NbtCompound nbt = buf.readNbt();
            client.execute(() -> ClientData.DATA.put(id, PlayerDevour.fromNbt(nbt == null ? new NbtCompound() : nbt)));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientData.DATA.clear();
            ClientTentacles.clear();
            AnchorFlags.clearClient();
            useWasDown = false;
        });

        // o que cada tentáculo está fazendo (alvos no mundo, modo de repouso, âncora)
        ClientPlayNetworking.registerGlobalReceiver(TentacleNet.STATE, (client, handler, buf, sender) -> {
            UUID id = buf.readUuid();
            boolean anchored = buf.readBoolean();
            byte[] mode = new byte[4];
            Vec3d[] target = new Vec3d[4];
            for (int i = 0; i < 4; i++) {
                mode[i] = buf.readByte();
                if (mode[i] == 1) target[i] = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            }
            client.execute(() -> {
                ClientTentacles.View v = ClientTentacles.update(id);
                for (int i = 0; i < 4; i++) {
                    v.mode[i] = mode[i];
                    v.target[i] = target[i];
                    if (target[i] == null) v.cur[i] = null; // sem alvo: a próxima vez começa do alvo novo
                }
                v.anchored = anchored;
                v.time = client.world == null ? 0 : client.world.getTime();
                AnchorFlags.setClient(id, anchored);
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(TentacleNet.HUD, (client, handler, buf, sender) -> {
            int sel = buf.readByte(), lvl = buf.readByte(), cd = buf.readShort(), cdMax = buf.readShort();
            int pm = buf.readShort(), am = buf.readShort();
            client.execute(() -> {
                ClientTentacles.selected = sel;
                ClientTentacles.level = lvl;
                ClientTentacles.cooldown = cd;
                ClientTentacles.cooldownMax = Math.max(1, cdMax);
                ClientTentacles.passiveMask = pm;
                ClientTentacles.activeMask = am;
            });
        });

        abilitiesKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.devourer.abilities", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.devourer"));
        useKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.devourer.tentacle_use", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.devourer"));
        nextKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.devourer.tentacle_next", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.devourer"));
        prevKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.devourer.tentacle_prev", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.devourer"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (abilitiesKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new AbilitiesScreen());
            }
            AuraParticles.tick(client);
            tickTentacleKeys(client);
        });

        HudRenderCallback.EVENT.register(DevourerClient::renderHud);

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof PlayerEntityRenderer pr) {
                helper.register(new DevourFeature(pr));
                helper.register(new ZombieFormFeature(pr));
            }
        });
    }

    // ------------------------------------------------------------------ teclas dos tentáculos

    private static void tickTentacleKeys(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;
        boolean down = useKey.isPressed() && client.currentScreen == null;
        if (down != useWasDown) {
            useWasDown = down;
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeByte(down ? 1 : 0);
            ClientPlayNetworking.send(TentacleNet.USE, buf);
        }
        while (nextKey.wasPressed()) sendCycle(1);
        while (prevKey.wasPressed()) sendCycle(-1);
    }

    private static void sendCycle(int dir) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(dir);
        ClientPlayNetworking.send(TentacleNet.CYCLE, buf);
    }

    /** Linha pequena acima da hotbar: habilidade selecionada, nível, recarga e o que está ligado. */
    private static void renderHud(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || mc.currentScreen != null) return;
        TentacleAbility a = TentacleAbility.ALL[Math.floorMod(ClientTentacles.selected, TentacleAbility.ALL.length)];
        boolean locked = ClientTentacles.level <= 0;
        boolean on = (((a.passive() ? ClientTentacles.passiveMask : ClientTentacles.activeMask) >> a.ordinal()) & 1) != 0;
        String label = "[" + useKey.getBoundKeyLocalizedText().getString() + "] " + a.displayName
                + (locked ? " (bloqueada)" : " Nv" + ClientTentacles.level) + (on ? " ●" : "");
        int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
        int tw = mc.textRenderer.getWidth(label);
        int x = w / 2 - tw / 2, y = h - 58;
        int color = locked ? 0xFF888888 : on ? 0xFFFF5555 : 0xFFFFFFFF;
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal(label), x, y, color);
        if (ClientTentacles.cooldown > 0) {
            int bw = 60;
            int filled = (int) (bw * (1f - Math.min(1f, ClientTentacles.cooldown / (float) ClientTentacles.cooldownMax)));
            ctx.fill(w / 2 - bw / 2, y + 11, w / 2 + bw / 2, y + 13, 0x88000000);
            ctx.fill(w / 2 - bw / 2, y + 11, w / 2 - bw / 2 + filled, y + 13, 0xFFAA2222);
        }
    }
}
