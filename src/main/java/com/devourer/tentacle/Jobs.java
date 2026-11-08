package com.devourer.tentacle;

import com.devourer.ModParticles;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Todas as habilidades de tentáculo em execução. Cada classe é uma habilidade (ou parte dela). */
public final class Jobs {

    private static boolean gone(LivingEntity v) {
        return v == null || !v.isAlive() || v.isRemoved();
    }

    // =================================================================================== Agarrão Dorsal
    /** Tentáculos nº 1 (e nº 2 no Nv2+) agarram o(s) mob(s) mais próximo(s) e os seguram atrás do jogador. */
    public static final class Grab extends Job {
        private final List<MobEntity> victims;

        public Grab(int[] chains, List<MobEntity> victims) {
            super(TentacleAbility.AGARRAO, chains);
            this.victims = victims;
            for (MobEntity v : victims) hold(v);
        }

        @Override
        public void tick(Ctx c) {
            age++;
            if (!c.p.isAlive() || releaseRequested || age > 200) {
                done = true;
                return;
            }
            Vec3d fwd = Vec3d.fromPolar(0, c.p.getYaw());
            Vec3d right = new Vec3d(-fwd.z, 0, fwd.x);
            boolean any = false;
            for (int i = 0; i < victims.size(); i++) {
                MobEntity v = victims.get(i);
                if (gone(v) || v.distanceTo(c.p) > 10) continue;
                any = true;
                c.rt.aim(chains[i], Targets.center(v));
                if (age < 4) continue; // o alvo do IK "salta" para a vítima em 4 ticks
                double side = victims.size() == 1 ? 0 : (i == 0 ? 0.8 : -0.8);
                Vec3d anchor = c.p.getPos().add(fwd.multiply(-1.15)).add(right.multiply(side)).add(0, 0.25, 0);
                Targets.pull(v, anchor, 0.45, 0.8);
                Targets.slow(v);
                int t = age - 4;
                if (t > 0 && t % 20 == 0) {
                    float dmg = 1f + (c.level >= 3 ? Math.min(4f, t / 60f) : 0f); // Nv3: o aperto aumenta
                    Targets.hurt(c.p, v, dmg, c.level >= 3);                      // Nv3: quebra a defesa
                    Targets.blood(c.w, Targets.center(v), 6);
                    Targets.sound(c.w, v.getPos(), SoundEvents.ENTITY_SLIME_SQUISH, 0.8f, 0.6f);
                }
            }
            if (!any) done = true;
        }

        @Override
        public void onEnd(Ctx c) {
            c.rt.cooldown[TentacleAbility.AGARRAO.ordinal()] = 15;
        }
    }

    // =================================================================================== Lança Peristáltica
    /** Tentáculo nº 2 dispara em linha reta, fura o(s) primeiro(s) mob(s) e os puxa até o jogador. */
    public static final class Lance extends Job {
        private final List<MobEntity> hits;
        private final double reach;
        private final int maxHealthAllowed;
        private int hook;
        private boolean impacted;

        public Lance(int[] chains, List<MobEntity> hits, double reach, int level) {
            super(TentacleAbility.LANCA, chains);
            this.hits = hits;
            this.reach = reach;
            this.maxHealthAllowed = level >= 3 ? Integer.MAX_VALUE : level == 2 ? 60 : 20; // "mobs leves" no Nv1
            for (MobEntity v : hits) hold(v);
        }

        @Override
        public void tick(Ctx c) {
            age++;
            if (!c.p.isAlive() || age > 60) {
                done = true;
                return;
            }
            Vec3d eye = c.p.getEyePos();
            if (age <= 4) { // a cadeia se estica de uma vez
                Vec3d end = hits.isEmpty() ? Targets.rayEnd(c.p, reach) : Targets.center(hits.get(0));
                c.rt.aim(chains[0], eye.add(end.subtract(eye).multiply(age / 4.0)));
                if (age == 1) Targets.sound(c.w, c.p.getPos(), SoundEvents.ENTITY_FISHING_BOBBER_THROW, 1f, 0.5f);
                if (age == 4 && !impacted) {
                    impacted = true;
                    for (MobEntity v : hits) {
                        if (gone(v)) continue;
                        Targets.hurt(c.p, v, c.level >= 3 ? 3f : 2f, false);
                        Targets.blood(c.w, Targets.center(v), 14);
                        Targets.sound(c.w, v.getPos(), SoundEvents.ENTITY_GENERIC_BIG_FALL, 0.6f, 1.4f);
                    }
                    if (hits.isEmpty()) {
                        c.p.sendMessage(Text.literal("A lança não acertou nada.").formatted(Formatting.GRAY), true);
                    }
                }
                return;
            }
            if (hits.isEmpty()) {
                if (age >= 8) done = true;
                return;
            }

            boolean arrived = false, any = false;
            Vec3d goal = c.p.getPos().add(Vec3d.fromPolar(0, c.p.getYaw()).multiply(1.0)).add(0, 0.2, 0);
            for (int i = 0; i < hits.size(); i++) {
                MobEntity v = hits.get(i);
                if (gone(v)) continue;
                any = true;
                c.rt.aim(chains[0], Targets.center(v));
                if (v.getMaxHealth() > maxHealthAllowed) { // pesado demais para este nível: só a pancada
                    if (age == 5) c.p.sendMessage(Text.literal("Pesado demais para a Lança deste nível.").formatted(Formatting.GRAY), true);
                    continue;
                }
                if (v.distanceTo(c.p) < 2.0) {
                    arrived = true;
                    Targets.pin(v);
                } else {
                    double gain = (age % 3 == 0) ? 0.9 : 0.2; // recolhe em solavancos curtos
                    Targets.pull(v, goal, gain, 1.1);
                }
            }
            if (!any) {
                done = true;
                return;
            }
            if (arrived) {
                // Nv3: ponta farpada, a vítima fica presa sofrendo dano por 3s
                if (c.level >= 3 && hook < 60) {
                    hook++;
                    for (MobEntity v : hits) {
                        if (gone(v)) continue;
                        Targets.slow(v);
                        if (hook % 10 == 0) {
                            Targets.hurt(c.p, v, 1f, true);
                            Targets.blood(c.w, Targets.center(v), 5);
                        }
                    }
                } else {
                    done = true;
                }
            }
        }

        @Override
        public void onEnd(Ctx c) {
            c.rt.cooldown[TentacleAbility.LANCA.ordinal()] = 25;
        }
    }

    // =================================================================================== Tentáculo-Boca
    /** Tentáculo nº 4 (com dentes) morde a vítima e a arrasta até a boca do tronco. */
    public static final class Bite extends Job {
        private final MobEntity v;
        private boolean bitten, arrived;
        private int hold;

        public Bite(int[] chains, MobEntity v) {
            super(TentacleAbility.BOCA, chains);
            this.v = v;
            hold(v);
        }

        @Override
        public void tick(Ctx c) {
            age++;
            if (!c.p.isAlive() || gone(v) || age > 160) {
                done = true;
                return;
            }
            c.rt.aim(chains[0], Targets.center(v));
            if (age < 5) return; // bote
            if (!bitten) {
                bitten = true;
                Targets.hurt(c.p, v, 3f, false);
                c.p.heal(1f);
                Targets.blood(c.w, Targets.center(v), 12);
                Targets.sound(c.w, v.getPos(), SoundEvents.ENTITY_FOX_BITE, 1f, 0.6f);
                if (gone(v)) {
                    done = true;
                    return;
                }
            }
            Vec3d mouth = Targets.mouth(c.p);
            if (!arrived) {
                Targets.pull(v, mouth, 0.35, 0.7);
                Targets.helpless(v);
                if (c.level >= 2 && age % 10 == 0) { // Nv2: mastiga durante o arrasto
                    Targets.hurt(c.p, v, 1.5f, false);
                    c.p.heal(0.5f);
                    Targets.sound(c.w, v.getPos(), SoundEvents.ENTITY_GENERIC_EAT, 0.8f, 0.7f);
                }
                if (v.getPos().distanceTo(mouth) < 1.2 || age > 80) {
                    arrived = true;
                    Targets.hurt(c.p, v, 3f, false);
                    c.p.heal(2f);
                    c.p.getHungerManager().add(2, 0.6f);
                    Targets.blood(c.w, mouth, 18);
                    Targets.sound(c.w, mouth, SoundEvents.ENTITY_PLAYER_BURP, 0.8f, 0.6f);
                    if (c.level < 3) done = true;
                }
            } else if (hold < 80) { // Nv3: alimenta o jogador enquanto segura
                hold++;
                Targets.pull(v, mouth, 0.5, 0.5);
                Targets.helpless(v);
                if (hold % 20 == 0) {
                    Targets.hurt(c.p, v, 2f, false);
                    c.p.heal(1f);
                    c.p.getHungerManager().add(1, 0.4f);
                    Targets.sound(c.w, mouth, SoundEvents.ENTITY_GENERIC_EAT, 0.8f, 0.6f);
                }
            } else {
                done = true;
            }
        }

        @Override
        public void onEnd(Ctx c) {
            c.rt.cooldown[TentacleAbility.BOCA.ordinal()] = 20;
        }
    }

    // =================================================================================== Guindaste de Carne
    /** Dois tentáculos erguem o mob até 3 blocos; ao soltar a tecla, batem com ele no chão. */
    public static final class Crane extends Job {
        private final MobEntity v;
        private final double baseY;
        private boolean slam;
        private int slamTicks;

        public Crane(int[] chains, MobEntity v) {
            super(TentacleAbility.GUINDASTE, chains);
            this.v = v;
            this.baseY = v.getY();
            hold(v);
        }

        @Override
        public void tick(Ctx c) {
            age++;
            if (!c.p.isAlive() || gone(v)) {
                done = true;
                return;
            }
            c.rt.aim(chains[0], Targets.center(v));              // cintura
            c.rt.aim(chains[1], v.getPos().add(0, 0.2, 0));      // pernas
            Targets.helpless(v);
            v.fallDistance = 0;

            if (!slam) {
                if (releaseRequested || age > 200) {
                    slam = true;
                    return;
                }
                Vec3d fwd = Vec3d.fromPolar(0, c.p.getYaw());
                Vec3d right = new Vec3d(-fwd.z, 0, fwd.x);
                double lift = Math.min(3.0, age * 0.12);
                Vec3d goal = new Vec3d(c.p.getX() + fwd.x * 2.2, baseY + lift, c.p.getZ() + fwd.z * 2.2);
                if (c.level >= 3) { // Nv3: balança a vítima e ela bate nos outros mobs
                    goal = goal.add(right.multiply(Math.sin(age * 0.15) * 1.2));
                    if (age % 20 == 0) {
                        for (MobEntity o : c.w.getEntitiesByClass(MobEntity.class, v.getBoundingBox().expand(1.8),
                                e -> e != v && Targets.valid(c.p, e))) {
                            Targets.hurt(c.p, o, 3f, false);
                            o.takeKnockback(0.8, c.p.getX() - o.getX(), c.p.getZ() - o.getZ());
                            Targets.blood(c.w, Targets.center(o), 8);
                        }
                    }
                }
                Targets.pull(v, goal, 0.3, 0.6);
                if (age % 25 == 0) Targets.sound(c.w, v.getPos(), SoundEvents.ENTITY_SKELETON_HURT, 0.6f, 0.5f); // rangido
            } else {
                slamTicks++;
                v.setVelocity(0, -1.8, 0);
                v.velocityModified = true;
                if ((v.isOnGround() && slamTicks > 1) || slamTicks > 25) {
                    float dmg = c.level >= 3 ? 9f : c.level == 2 ? 6f : 3f;
                    Targets.hurt(c.p, v, dmg, false);
                    Vec3d at = v.getPos();
                    c.w.spawnParticles(ModParticles.BLOOD, at.x, at.y + 0.2, at.z, 40, 0.6, 0.1, 0.6, 0.18);
                    Targets.sound(c.w, at, SoundEvents.ENTITY_GENERIC_BIG_FALL, 1.2f, 0.6f);
                    if (c.level >= 3) {
                        for (MobEntity o : c.w.getEntitiesByClass(MobEntity.class, v.getBoundingBox().expand(3.0),
                                e -> e != v && Targets.valid(c.p, e))) {
                            Targets.hurt(c.p, o, 5f, false);
                            o.takeKnockback(1.0, at.x - o.getX(), at.z - o.getZ());
                        }
                    }
                    done = true;
                }
            }
        }

        @Override
        public void onEnd(Ctx c) {
            c.rt.cooldown[TentacleAbility.GUINDASTE.ordinal()] = 30;
        }
    }

    // =================================================================================== Colheita Múltipla
    /** Os 4 tentáculos agarram até 4 mobs e os fazem girar em volta do jogador. */
    public static final class Harvest extends Job {
        private final List<MobEntity> victims;
        private double base;

        public Harvest(int[] chains, List<MobEntity> victims) {
            super(TentacleAbility.COLHEITA, chains);
            this.victims = victims;
            for (MobEntity v : victims) hold(v);
        }

        @Override
        public void tick(Ctx c) {
            age++;
            if (!c.p.isAlive() || releaseRequested || age > 200) {
                done = true;
                return;
            }
            boolean any = false;
            int n = victims.size();
            for (int i = 0; i < n; i++) {
                MobEntity v = victims.get(i);
                if (gone(v) || v.distanceTo(c.p) > 12) continue;
                any = true;
                double a = base + i * (Math.PI * 2 / n);
                Vec3d goal = c.p.getPos().add(Math.cos(a) * 1.9, 0.15, Math.sin(a) * 1.9);
                Targets.pull(v, goal, 0.4, 0.7);
                Targets.slow(v);
                c.rt.aim(chains[i], Targets.center(v));
                int period = c.level >= 3 ? 20 : 30; // Nv3: dano contínuo independente e mais forte
                if ((age + i * 5) % period == 0) {
                    Targets.hurt(c.p, v, c.level >= 3 ? 2f : 1f, false);
                    Targets.blood(c.w, Targets.center(v), 6);
                }
                if ((age + i * 7) % 40 == 0) { // alimentação em massa
                    c.p.getHungerManager().add(1, 0.3f);
                    Targets.sound(c.w, v.getPos(), SoundEvents.ENTITY_SLIME_SQUISH, 0.7f, 0.5f);
                }
            }
            base += 0.09;
            if (!any) done = true;
        }

        @Override
        public void onEnd(Ctx c) {
            c.rt.cooldown[TentacleAbility.COLHEITA.ordinal()] = 60;
        }
    }

    // =================================================================================== Âncora de Aderência
    /** Fixa dois tentáculos na parede/teto: o corpo escala (via mixin de isClimbing) e, no Nv2+, anda pelo teto. */
    public static final class Anchor extends Job {
        private Vec3d lastPos;
        private double climbed;

        public Anchor(int[] chains) {
            super(TentacleAbility.ANCORA, chains);
        }

        @Override
        public void tick(Ctx c) {
            age++;
            if (!c.p.isAlive() || releaseRequested || c.p.isSpectator()) {
                done = true;
                return;
            }
            if (!c.p.isCreative() && c.p.getHungerManager().getFoodLevel() < 3) {
                c.p.sendMessage(Text.literal("Fome demais para se agarrar.").formatted(Formatting.RED), true);
                done = true;
                return;
            }
            c.rt.anchored = true;

            Vec3d wall = Targets.wallPoint(c.p);
            Vec3d ceil = c.level >= 2 ? Targets.ceilingPoint(c.p) : null;
            boolean hanging = ceil != null && !c.p.isSneaking();

            // teto (Nv2): sem gravidade enquanto encostado nele; agachar solta
            if (hanging) {
                if (!c.rt.noGravSet) {
                    c.p.setNoGravity(true);
                    c.rt.noGravSet = true;
                    Targets.sound(c.w, c.p.getPos(), SoundEvents.BLOCK_HONEY_BLOCK_SLIDE, 1f, 0.8f);
                }
                c.p.fallDistance = 0;
            } else if (c.rt.noGravSet) {
                c.p.setNoGravity(false);
                c.rt.noGravSet = false;
            }

            // visual: uma cadeia se projeta e gruda, a outra solta e busca novo apoio (como uma lagarta)
            boolean phase = (age / 12) % 2 == 0;
            Vec3d anchorPt = ceil != null ? ceil : wall;
            if (anchorPt != null) {
                double up = ceil != null ? 0 : 0.8;
                c.rt.aim(chains[0], anchorPt.add(0, phase ? up : up * 0.3, 0));
                c.rt.aim(chains[1], anchorPt.add(ceil != null ? 0.4 : 0, !phase ? up : up * 0.3, 0));
                if (age % 14 == 0) c.w.spawnParticles(ParticleTypes.ITEM_SLIME, anchorPt.x, anchorPt.y, anchorPt.z, 4, 0.1, 0.1, 0.1, 0.02);
            }

            // fome: consome enquanto está fixado no ar
            boolean attached = anchorPt != null && !c.p.isOnGround();
            if (attached) c.p.addExhaustion(0.03f);

            if (lastPos != null && attached) {
                climbed += Math.abs(c.p.getY() - lastPos.y);
                if (climbed >= 1.0) {
                    climbed = 0;
                    Targets.sound(c.w, c.p.getPos(), SoundEvents.BLOCK_HONEY_BLOCK_STEP, 0.7f, 1.2f);
                }
            }
            lastPos = c.p.getPos();
        }

        @Override
        public void onEnd(Ctx c) {
            c.rt.anchored = false;
            if (c.rt.noGravSet) {
                c.p.setNoGravity(false);
                c.rt.noGravSet = false;
            }
            c.rt.cooldown[TentacleAbility.ANCORA.ordinal()] = 10;
        }
    }

    // =================================================================================== Raízes Farejadoras
    /** Um tentáculo da zona passiva agarra um mob que passou perto. Nv3: arrasta até a boca e morde. */
    public static final class RootGrab extends Job {
        private final MobEntity v;

        public RootGrab(int[] chains, MobEntity v) {
            super(TentacleAbility.RAIZES, chains);
            this.v = v;
            hold(v);
        }

        @Override
        public void tick(Ctx c) {
            age++;
            if (!c.p.isAlive() || gone(v) || age > 110 || !c.rt.passive[TentacleAbility.RAIZES.ordinal()]) {
                done = true;
                return;
            }
            c.rt.aim(chains[0], Targets.center(v));
            if (age < 8) return; // a cadeia levanta como uma cobra e ataca
            Targets.helpless(v);
            if (c.level >= 3) {
                Vec3d mouth = Targets.mouth(c.p);
                Targets.pull(v, mouth, 0.25, 0.5);
                if (v.getPos().distanceTo(mouth) < 1.3) {
                    Targets.hurt(c.p, v, 3f, false);
                    c.p.heal(1f);
                    Targets.blood(c.w, mouth, 12);
                    Targets.sound(c.w, mouth, SoundEvents.ENTITY_GENERIC_EAT, 0.8f, 0.6f);
                    done = true;
                }
            } else {
                Targets.pin(v);
                if (age % 20 == 0) {
                    Targets.hurt(c.p, v, 1f, false);
                    Targets.blood(c.w, Targets.center(v), 4);
                }
            }
        }

        @Override
        public void onEnd(Ctx c) {
            c.rt.cooldown[TentacleAbility.RAIZES.ordinal()] = 30;
        }
    }

    // =================================================================================== Lambada (Inquietação / Interceptores)
    /** Um chicote curto de um tentáculo em direção a um ponto/entidade, com uma ação opcional no impacto. */
    public static final class Lash extends Job {
        private final Supplier<Vec3d> where;
        private final int hitAt, total;
        private final Consumer<Ctx> onHit;

        public Lash(TentacleAbility owner, int chain, Supplier<Vec3d> where, int hitAt, int total, Consumer<Ctx> onHit) {
            super(owner, new int[]{chain});
            this.where = where;
            this.hitAt = hitAt;
            this.total = total;
            this.onHit = onHit;
        }

        @Override
        public void tick(Ctx c) {
            age++;
            c.rt.aim(chains[0], where.get());
            if (age == hitAt && onHit != null) onHit.accept(c);
            if (age >= total || !c.p.isAlive()) done = true;
        }
    }

    // =================================================================================== Passivas (chamadas todo tick)

    /** Raízes: tentáculos livres deitam no chão e agarram o que passar em até 3 (Nv1) / 5 (Nv2+) blocos. */
    static void tickRoots(Job.Ctx c) {
        TentacleRt rt = c.rt;
        for (int i = 0; i < TentacleRt.CHAINS; i++) {
            if (!rt.busy[i]) rt.mode[i] = TentacleRt.M_ROOTS;
        }
        int idx = TentacleAbility.RAIZES.ordinal();
        if (rt.cooldown[idx] > 0 || c.p.age % 6 != 0) return;
        double range = c.level >= 2 ? 5.0 : 3.0;
        List<MobEntity> near = Targets.nearest(c.p, rt, range, 1);
        if (near.isEmpty()) return;
        int[] ch = rt.claim(1, 0, 1, 2, 3);
        if (ch == null) return;
        rt.jobs.add(new RootGrab(ch, near.get(0)));
        rt.cooldown[idx] = 10;
        Targets.sound(c.w, c.p.getPos(), SoundEvents.ENTITY_SLIME_SQUISH_SMALL, 0.8f, 0.5f);
    }

    /** Inquietação: parado ou ferido, os tentáculos se agitam e pegam drops, XP (Nv2) e mobs fracos (Nv3). */
    static void tickNervous(Job.Ctx c) {
        TentacleRt rt = c.rt;
        boolean idle = c.p.getVelocity().horizontalLengthSquared() < 0.0004;
        boolean hurt = c.p.hurtTime > 0 || c.p.getHealth() < c.p.getMaxHealth() * 0.5f;
        if (!idle && !hurt) return;
        for (int i = 0; i < TentacleRt.CHAINS; i++) {
            if (!rt.busy[i]) rt.mode[i] = TentacleRt.M_NERVOUS;
        }
        int idx = TentacleAbility.INQUIETACAO.ordinal();
        if (rt.cooldown[idx] > 0 || c.p.age % 10 != 0) return;

        double range = c.level >= 2 ? 4.0 : 3.0;
        var box = c.p.getBoundingBox().expand(range);

        List<ItemEntity> items = c.w.getEntitiesByClass(ItemEntity.class, box,
                e -> e.isAlive() && e.distanceTo(c.p) <= range);
        if (!items.isEmpty()) {
            ItemEntity it = items.get(0);
            int[] ch = rt.claim(1, 2, 3, 1, 0);
            if (ch == null) return;
            rt.jobs.add(new Lash(TentacleAbility.INQUIETACAO, ch[0], () -> it.getPos(), 5, 8, cc -> {
                if (it.isAlive()) {
                    it.setPickupDelay(0);
                    it.setPosition(cc.p.getX(), cc.p.getY() + 0.2, cc.p.getZ()); // "leva à boca": o jogador pega na hora
                    cc.p.heal(0.5f);
                    Targets.sound(cc.w, cc.p.getPos(), SoundEvents.ENTITY_SLIME_SQUISH_SMALL, 0.7f, 1.4f);
                }
            }));
            rt.cooldown[idx] = 20;
            return;
        }

        if (c.level >= 2) {
            List<ExperienceOrbEntity> orbs = c.w.getEntitiesByClass(ExperienceOrbEntity.class, box,
                    e -> e.isAlive() && e.distanceTo(c.p) <= range);
            if (!orbs.isEmpty()) {
                ExperienceOrbEntity orb = orbs.get(0);
                int[] ch = rt.claim(1, 2, 3, 1, 0);
                if (ch == null) return;
                rt.jobs.add(new Lash(TentacleAbility.INQUIETACAO, ch[0], () -> orb.getPos(), 5, 8, cc -> {
                    if (orb.isAlive()) {
                        cc.p.addExperience(orb.getExperienceAmount());
                        orb.discard();
                        cc.p.heal(0.5f);
                        Targets.sound(cc.w, cc.p.getPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 0.7f);
                    }
                }));
                rt.cooldown[idx] = 20;
                return;
            }
        }

        if (c.level >= 3) {
            List<MobEntity> weak = new ArrayList<>(Targets.nearest(c.p, rt, 3.0, 1));
            weak.removeIf(m -> m.getMaxHealth() > 10);
            if (!weak.isEmpty()) {
                MobEntity m = weak.get(0);
                int[] ch = rt.claim(1, 2, 3, 1, 0);
                if (ch == null) return;
                rt.jobs.add(new Lash(TentacleAbility.INQUIETACAO, ch[0], () -> Targets.center(m), 4, 8, cc -> {
                    if (m.isAlive()) {
                        Targets.hurt(cc.p, m, 2f, false);
                        Targets.blood(cc.w, Targets.center(m), 8);
                        Targets.sound(cc.w, m.getPos(), SoundEvents.ENTITY_SLIME_ATTACK, 0.8f, 0.7f);
                    }
                }));
                rt.cooldown[idx] = 20;
            }
        }
    }

    /** Interceptores: projéteis vindos de frente são cortados (Nv1: 1 por vez, Nv2: 2, Nv3: devolvidos). */
    static void tickIntercept(Job.Ctx c) {
        TentacleRt rt = c.rt;
        int idx = TentacleAbility.INTERCEPTORES.ordinal();
        if (rt.cooldown[idx] > 0) return;
        Vec3d eye = c.p.getEyePos();
        Vec3d look = c.p.getRotationVec(1.0f);
        List<net.minecraft.entity.projectile.ProjectileEntity> projs = c.w.getEntitiesByClass(
                net.minecraft.entity.projectile.ProjectileEntity.class, c.p.getBoundingBox().expand(6),
                e -> e.isAlive() && e.getOwner() != c.p && e.getVelocity().lengthSquared() > 0.04);
        projs.sort(java.util.Comparator.comparingDouble(e -> e.squaredDistanceTo(c.p)));
        int max = c.level >= 2 ? 2 : 1;
        int done = 0;
        for (var pr : projs) {
            if (done >= max) break;
            Vec3d rel = pr.getPos().subtract(eye);
            if (rel.length() > 3.4) continue;
            boolean incoming = pr.getVelocity().dotProduct(rel) < 0;                    // vindo na direção do jogador
            boolean front = rel.lengthSquared() < 1e-6 || look.dotProduct(rel.normalize()) > 0.1; // de frente
            if (!incoming || !front) continue;
            int[] ch = rt.claim(1, 3, 2, 1, 0);
            if (ch == null) break;
            final Vec3d at = pr.getPos();
            rt.jobs.add(new Lash(TentacleAbility.INTERCEPTORES, ch[0], () -> at, 0, 6, null));
            if (c.level >= 3 && !(pr instanceof net.minecraft.entity.projectile.ExplosiveProjectileEntity)) {
                pr.setVelocity(pr.getVelocity().multiply(-1.0)); // Nv3: devolve
                pr.setOwner(c.p);
                pr.velocityModified = true;
            } else {
                pr.discard();
            }
            c.w.spawnParticles(ModParticles.BLOOD, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.1);
            Targets.sound(c.w, at, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.3f);
            done++;
        }
        if (done > 0) rt.cooldown[idx] = 6;
    }

    private Jobs() {
    }
}
