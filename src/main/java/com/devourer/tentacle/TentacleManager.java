package com.devourer.tentacle;

import com.devourer.DevourState;
import com.devourer.PlayerDevour;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servidor: recebe a tecla de uso, cria/termina as habilidades, roda as passivas e avisa os clientes do que
 * cada tentáculo está fazendo. Tudo é autoritativo no servidor (dano, movimento das vítimas, fome...).
 */
public final class TentacleManager {
    private static final Map<UUID, TentacleRt> RT = new ConcurrentHashMap<>();

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(TentacleNet.USE, (server, player, handler, buf, sender) -> {
            boolean pressed = buf.readByte() != 0;
            server.execute(() -> onUse(player, pressed));
        });
        ServerPlayNetworking.registerGlobalReceiver(TentacleNet.CYCLE, (server, player, handler, buf, sender) -> {
            int dir = buf.readByte() >= 0 ? 1 : -1;
            server.execute(() -> onCycle(player, dir));
        });
        ServerTickEvents.END_SERVER_TICK.register(TentacleManager::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> handler.getPlayer().setNoGravity(false));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> cleanup(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ServerPlayerEntity p : new ArrayList<>(server.getPlayerManager().getPlayerList())) cleanup(p);
        });
    }

    private static TentacleRt rt(ServerPlayerEntity p) {
        return RT.computeIfAbsent(p.getUuid(), k -> new TentacleRt());
    }

    private static Job.Ctx ctx(ServerPlayerEntity p, TentacleRt rt, TentacleAbility a, PlayerDevour d) {
        return new Job.Ctx(p, (ServerWorld) p.getWorld(), rt, Catalog.level(d, a));
    }

    private static PlayerDevour devour(ServerPlayerEntity p) {
        MinecraftServer s = p.getServer();
        return DevourState.of(s).get(p.getUuid());
    }

    // ------------------------------------------------------------------ entrada do jogador

    private static void onCycle(ServerPlayerEntity p, int dir) {
        TentacleRt rt = rt(p);
        int n = TentacleAbility.ALL.length;
        rt.selected = Math.floorMod(rt.selected + dir, n);
        TentacleAbility a = TentacleAbility.ALL[rt.selected];
        int lvl = Catalog.level(devour(p), a);
        MutableText msg = Text.literal(a.displayName).formatted(lvl > 0 ? Formatting.WHITE : Formatting.DARK_GRAY);
        msg.append(Text.literal(lvl > 0 ? "  Nv" + lvl : "  (bloqueada)").formatted(lvl > 0 ? Formatting.GREEN : Formatting.RED));
        if (a.mode == TentacleAbility.Mode.TOGGLE) {
            boolean on = a.passive() ? rt.passive[a.ordinal()] : rt.find(a) != null;
            msg.append(Text.literal(on ? "  [ligada]" : "  [desligada]").formatted(on ? Formatting.DARK_RED : Formatting.GRAY));
        }
        p.sendMessage(msg, true);
        rt.lastHud = -1; // força atualizar o HUD
    }

    private static void onUse(ServerPlayerEntity p, boolean pressed) {
        TentacleRt rt = rt(p);
        TentacleAbility a = TentacleAbility.ALL[rt.selected];

        if (!pressed) { // soltou a tecla: encerra as habilidades de segurar
            if (a.mode == TentacleAbility.Mode.HOLD) {
                Job j = rt.find(a);
                if (j != null) j.releaseRequested = true;
            }
            return;
        }
        if (!p.isAlive() || p.isSpectator()) return;

        PlayerDevour d = devour(p);
        int lvl = Catalog.level(d, a);
        if (lvl == 0) {
            p.sendMessage(Text.literal(a.displayName + ": bloqueada. Devore ").formatted(Formatting.RED)
                    .append(mobNames(a)), true);
            return;
        }
        int ai = a.ordinal();

        if (a.passive()) {
            rt.passive[ai] = !rt.passive[ai];
            p.sendMessage(Text.literal(a.displayName + (rt.passive[ai] ? " ligada" : " desligada") + "  Nv" + lvl)
                    .formatted(rt.passive[ai] ? Formatting.DARK_RED : Formatting.GRAY), true);
            return;
        }
        if (a == TentacleAbility.ANCORA) {
            Job j = rt.find(a);
            if (j != null) {
                if (lvl >= 3 && p.isSneaking()) {
                    dash(p, j, rt); // Nv3: Shift + tecla = arrancada entre apoios
                } else {
                    j.releaseRequested = true;
                    p.sendMessage(Text.literal("Âncora solta.").formatted(Formatting.GRAY), true);
                }
                return;
            }
        }

        // já existe uma desta habilidade rodando
        if (rt.find(a) != null) return;
        if (rt.cooldown[ai] > 0) return;

        Job.Ctx c = new Job.Ctx(p, (ServerWorld) p.getWorld(), rt, lvl);
        Job job = switch (a) {
            case AGARRAO -> startGrab(c);
            case LANCA -> startLance(c);
            case BOCA -> startBite(c);
            case GUINDASTE -> startCrane(c);
            case COLHEITA -> startHarvest(c);
            case ANCORA -> startAnchor(c);
            default -> null;
        };
        if (job != null) {
            rt.jobs.add(job);
            Targets.sound(c.w, p.getPos(), SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, 0.8f, 0.6f);
            Targets.blood(c.w, p.getPos().add(0, 1.2, 0.0), 6);
        }
    }

    private static Text mobNames(TentacleAbility a) {
        MutableText out = Text.empty();
        List<String> ids = Catalog.mobs(a.id);
        for (int i = 0; i < ids.size(); i++) {
            Identifier id = new Identifier(ids.get(i));
            Text name = Registries.ENTITY_TYPE.containsId(id) ? Registries.ENTITY_TYPE.get(id).getName() : Text.literal(ids.get(i));
            if (i > 0) out.append(Text.literal(", "));
            out.append(name.copy().formatted(Formatting.WHITE));
        }
        return out;
    }

    private static void busyMsg(ServerPlayerEntity p) {
        p.sendMessage(Text.literal("Tentáculos ocupados.").formatted(Formatting.GRAY), true);
    }

    private static void noTarget(ServerPlayerEntity p) {
        p.sendMessage(Text.literal("Nenhum alvo ao alcance.").formatted(Formatting.GRAY), true);
    }

    // ------------------------------------------------------------------ início de cada habilidade

    private static Job startGrab(Job.Ctx c) {
        int count = c.level >= 2 ? 2 : 1; // Nv2: a ponta se divide e agarra 2
        List<MobEntity> vs = Targets.nearest(c.p, c.rt, 6.0, count);
        if (vs.isEmpty()) {
            noTarget(c.p);
            return null;
        }
        int[] ch = c.rt.claim(vs.size(), 0, 1);
        if (ch == null) {
            busyMsg(c.p);
            return null;
        }
        return new Jobs.Grab(ch, vs);
    }

    private static Job startLance(Job.Ctx c) {
        double reach = c.level >= 2 ? 10.0 : 6.0;
        int[] ch = c.rt.claim(1, 1, 0, 2, 3);
        if (ch == null) {
            busyMsg(c.p);
            return null;
        }
        List<MobEntity> hits = Targets.ray(c.p, c.rt, reach, c.level >= 2 ? 2 : 1);
        return new Jobs.Lance(ch, hits, reach, c.level);
    }

    private static Job startBite(Job.Ctx c) {
        List<MobEntity> ray = Targets.ray(c.p, c.rt, 7.0, 1);
        List<MobEntity> vs = ray.isEmpty() ? Targets.nearest(c.p, c.rt, 7.0, 1) : ray;
        if (vs.isEmpty()) {
            noTarget(c.p);
            return null;
        }
        int[] ch = c.rt.claim(1, 3, 2, 1, 0);
        if (ch == null) {
            busyMsg(c.p);
            return null;
        }
        return new Jobs.Bite(ch, vs.get(0));
    }

    private static Job startCrane(Job.Ctx c) {
        float limit = c.level >= 3 ? Float.MAX_VALUE : c.level == 2 ? 40f : 14f; // pequenos / médios / grandes
        List<MobEntity> vs = Targets.nearest(c.p, c.rt, 5.0, 8);
        vs.removeIf(m -> m.getMaxHealth() > limit);
        if (vs.isEmpty()) {
            c.p.sendMessage(Text.literal("Nenhum mob do tamanho que este nível ergue.").formatted(Formatting.GRAY), true);
            return null;
        }
        int[] ch = c.rt.claim(2, 0, 1);
        if (ch == null) {
            busyMsg(c.p);
            return null;
        }
        return new Jobs.Crane(ch, vs.get(0));
    }

    private static Job startHarvest(Job.Ctx c) {
        int count = c.level >= 2 ? 4 : 2;
        List<MobEntity> vs = Targets.nearest(c.p, c.rt, 6.0, count);
        if (vs.isEmpty()) {
            noTarget(c.p);
            return null;
        }
        int[] ch = c.rt.claim(4, 0, 1, 2, 3); // enquanto dura, usa todos os tentáculos
        if (ch == null) {
            busyMsg(c.p);
            return null;
        }
        return new Jobs.Harvest(ch, vs);
    }

    private static Job startAnchor(Job.Ctx c) {
        int[] ch = c.rt.claim(2, 2, 3, 1, 0);
        if (ch == null) {
            busyMsg(c.p);
            return null;
        }
        c.p.sendMessage(Text.literal("Âncora fixada. Encoste na parede; pule/avance para subir, agache para ficar pendurado."
                + (c.level >= 2 ? " Teto: pule contra ele." : "")
                + (c.level >= 3 ? " Shift + tecla = arrancada." : "")).formatted(Formatting.DARK_GREEN), true);
        return new Jobs.Anchor(ch);
    }

    /** Anchor Nv3: arrancada rápida na direção do olhar. */
    private static void dash(ServerPlayerEntity p, Job j, TentacleRt rt) {
        int ai = TentacleAbility.ANCORA.ordinal();
        if (rt.cooldown[ai] > 0) return;
        rt.cooldown[ai] = 30;
        Vec3d v = p.getRotationVec(1.0f).multiply(1.3);
        p.setVelocity(v.x, Math.max(v.y, 0.15), v.z);
        p.velocityModified = true;
        p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
        p.addExhaustion(0.4f);
        Targets.sound((ServerWorld) p.getWorld(), p.getPos(), SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.7f);
    }

    // ------------------------------------------------------------------ tick

    private static void tick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            TentacleRt rt = RT.get(p.getUuid());
            if (rt == null) continue;
            tickPlayer(p, rt);
        }
    }

    private static void tickPlayer(ServerPlayerEntity p, TentacleRt rt) {
        PlayerDevour d = devour(p);
        for (int i = 0; i < rt.cooldown.length; i++) if (rt.cooldown[i] > 0) rt.cooldown[i]--;
        rt.resetOutput();
        rt.anchored = false;

        // passivas (perdem o efeito se o jogador perder os membros necessários)
        for (TentacleAbility a : TentacleAbility.ALL) {
            if (!a.passive() || !rt.passive[a.ordinal()]) continue;
            Job.Ctx c = ctx(p, rt, a, d);
            if (c.level == 0 || !p.isAlive()) {
                rt.passive[a.ordinal()] = false;
                continue;
            }
            switch (a) {
                case RAIZES -> Jobs.tickRoots(c);
                case INQUIETACAO -> Jobs.tickNervous(c);
                case INTERCEPTORES -> Jobs.tickIntercept(c);
                default -> {
                }
            }
        }

        // jobs ativos
        boolean anchoredNow = false;
        for (Iterator<Job> it = rt.jobs.iterator(); it.hasNext(); ) {
            Job j = it.next();
            Job.Ctx c = ctx(p, rt, j.ability, d);
            if (c.level == 0 && j.ability.mode != TentacleAbility.Mode.TOGGLE) j.releaseRequested = true;
            if (!j.done) j.tick(c);
            if (j.ability == TentacleAbility.ANCORA && !j.done) anchoredNow = true;
            if (j.done) {
                j.onEnd(c);
                for (int ch : j.chains) rt.busy[ch] = false;
                it.remove();
            }
        }
        rt.anchored = anchoredNow;
        AnchorFlags.setServer(p.getUuid(), anchoredNow);

        sync(p, rt);
        syncHud(p, rt, d);
    }

    private static void sync(ServerPlayerEntity p, TentacleRt rt) {
        boolean active = rt.anchored;
        for (int i = 0; i < TentacleRt.CHAINS; i++) if (rt.mode[i] != TentacleRt.M_DEFAULT) active = true;
        if (active || rt.wasActive) TentacleNet.sendState(p, rt); // um último pacote "tudo parado" ao terminar
        rt.wasActive = active;
    }

    private static void syncHud(ServerPlayerEntity p, TentacleRt rt, PlayerDevour d) {
        if (++rt.hudTimer < 5) return;
        rt.hudTimer = 0;
        TentacleAbility a = TentacleAbility.ALL[rt.selected];
        int lvl = Catalog.level(d, a);
        int cd = rt.cooldown[a.ordinal()];
        int passiveMask = 0, activeMask = 0;
        for (TentacleAbility x : TentacleAbility.ALL) {
            if (rt.passive[x.ordinal()]) passiveMask |= 1 << x.ordinal();
            if (rt.find(x) != null) activeMask |= 1 << x.ordinal();
        }
        int hash = rt.selected * 31 + lvl * 7 + cd * 131 + passiveMask * 17 + activeMask * 5;
        if (hash == rt.lastHud) return;
        rt.lastHud = hash;
        TentacleNet.sendHud(p, rt.selected, lvl, cd, 60, passiveMask, activeMask);
    }

    // ------------------------------------------------------------------ limpeza

    private static void cleanup(ServerPlayerEntity p) {
        TentacleRt rt = RT.remove(p.getUuid());
        AnchorFlags.setServer(p.getUuid(), false);
        if (rt == null) return;
        PlayerDevour d = devour(p);
        for (Job j : new ArrayList<>(rt.jobs)) {
            j.releaseRequested = true;
            j.onEnd(ctx(p, rt, j.ability, d));
        }
        rt.jobs.clear();
        if (rt.noGravSet) p.setNoGravity(false); // NoGravity é salvo no jogador: nunca deixar preso
    }

    private TentacleManager() {
    }
}
