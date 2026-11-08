package com.devourer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * /infectado on|off  -> liga/desliga o modelo zombie_form que cobre o jogador
 * /devorar limpar    -> remove os membros de outros mobs colados no corpo
 */
public final class Commands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> build(dispatcher));
    }

    private static void build(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("infectado")
                .then(CommandManager.literal("on").executes(c -> setInfected(c, true)))
                .then(CommandManager.literal("off").executes(c -> setInfected(c, false))));

        d.register(CommandManager.literal("devorar")
                .then(CommandManager.literal("limpar").executes(Commands::clear)));
    }

    private static int setInfected(CommandContext<ServerCommandSource> c, boolean on) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        MinecraftServer s = c.getSource().getServer();
        DevourState st = DevourState.of(s);
        st.get(p.getUuid()).infected = on;
        st.markDirty();
        Net.syncToAll(p);
        c.getSource().sendFeedback(() -> Text.literal(on ? "Infectado: ON — o modelo voltou a cobrir você."
                : "Infectado: OFF — o modelo foi removido.").formatted(on ? Formatting.DARK_RED : Formatting.GRAY), false);
        return 1;
    }

    private static int clear(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        DevourState st = DevourState.of(c.getSource().getServer());
        PlayerDevour d = st.get(p.getUuid());

        int parts = d.parts.size();
        d.parts.clear();
        st.markDirty();
        Net.syncToAll(p);

        c.getSource().sendFeedback(() -> Text.literal("Removido(s) " + parts + " membro(s).")
                .formatted(Formatting.GRAY), false);
        return 1;
    }

    private Commands() {
    }
}
