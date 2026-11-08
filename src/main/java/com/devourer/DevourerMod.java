package com.devourer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class DevourerMod implements ModInitializer {
    public static final String ID = "devourer";

    @Override
    public void onInitialize() {
        ModParticles.register();
        Commands.register();

        // Matou uma criatura -> devora
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof PlayerEntity) return;
            if (!(source.getAttacker() instanceof ServerPlayerEntity player)) return;
            devour(player, entity);
        });

        // Sincronização para o render dos outros jogadores
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Net.syncToAll(handler.getPlayer()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> Net.syncToAll(newP));
        EntityTrackingEvents.START_TRACKING.register((tracked, player) -> {
            if (tracked instanceof ServerPlayerEntity tp) Net.sync(tp, player);
        });
    }

    private static void devour(ServerPlayerEntity player, LivingEntity victim) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        String id = Registries.ENTITY_TYPE.getId(victim.getType()).toString();
        DevourState st = DevourState.of(server);
        PlayerDevour d = st.get(player.getUuid());

        Mount mount = Mount.random(victim.getRandom());
        d.parts.add(new DevourPart(id, mount, victim.getRandom().nextInt()));
        while (d.parts.size() > PlayerDevour.MAX_PARTS) d.parts.remove(0);
        st.markDirty();

        ServerWorld w = (ServerWorld) victim.getWorld();
        double x = victim.getX(), y = victim.getY() + victim.getHeight() / 2, z = victim.getZ();
        double sx = victim.getWidth() / 2, sy = victim.getHeight() / 3;
        w.spawnParticles(ModParticles.BLOOD, x, y, z, 45, sx, sy, sx, 0.16);
        w.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 1.2f, 0.5f);
        w.playSound(null, x, y, z, SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.0f, 0.8f);

        MutableText msg = Text.literal("Devorou ").formatted(Formatting.DARK_RED)
                .append(victim.getName().copy().formatted(Formatting.WHITE))
                .append(Text.literal("  [" + mount.label + "]").formatted(Formatting.GRAY));
        player.sendMessage(msg, true);

        Net.syncToAll(player);
    }
}
