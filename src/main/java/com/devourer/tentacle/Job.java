package com.devourer.tentacle;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Uma habilidade em execução. Ocupa tentáculos (chains) até terminar. */
public abstract class Job {
    /** Contexto de um tick. */
    public static final class Ctx {
        public final ServerPlayerEntity p;
        public final ServerWorld w;
        public final TentacleRt rt;
        public final int level;

        Ctx(ServerPlayerEntity p, ServerWorld w, TentacleRt rt, int level) {
            this.p = p;
            this.w = w;
            this.rt = rt;
            this.level = level;
        }
    }

    public final TentacleAbility ability;
    public final int[] chains;
    public int age;
    public boolean releaseRequested;
    public boolean done;
    /** Vítimas seguras por este job (para os demais não pegarem a mesma). */
    public final List<UUID> held = new ArrayList<>();

    protected Job(TentacleAbility ability, int[] chains) {
        this.ability = ability;
        this.chains = chains;
    }

    protected void hold(Entity e) {
        held.add(e.getUuid());
    }

    public abstract void tick(Ctx c);

    /** Chamado uma vez ao terminar (normalmente, solto, ou jogador morto/desconectado). */
    public void onEnd(Ctx c) {
    }
}
