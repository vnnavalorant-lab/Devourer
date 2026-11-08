package com.devourer.tentacle;

import com.devourer.ModParticles;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Funções comuns: escolha de alvos, movimento das vítimas, dano e efeitos. */
public final class Targets {

    /** Quem pode ser agarrado: qualquer mob, menos pets do jogador, aldeões/comerciantes e chefes. */
    public static boolean valid(ServerPlayerEntity p, Entity e) {
        if (!(e instanceof MobEntity m) || e == p || !m.isAlive() || m.isRemoved()) return false;
        if (e instanceof EnderDragonEntity || e instanceof WitherEntity || e instanceof MerchantEntity) return false;
        if (e instanceof TameableEntity t && t.isTamed() && t.getOwnerUuid() != null && t.getOwnerUuid().equals(p.getUuid())) return false;
        return !m.isInvulnerable();
    }

    /** Mobs válidos a até {@code range} blocos, do mais próximo ao mais distante, ignorando os já presos. */
    public static List<MobEntity> nearest(ServerPlayerEntity p, TentacleRt rt, double range, int max) {
        ServerWorld w = (ServerWorld) p.getWorld();
        Box box = p.getBoundingBox().expand(range);
        List<MobEntity> list = new ArrayList<>(w.getEntitiesByClass(MobEntity.class, box,
                e -> valid(p, e) && !rt.isHeld(e.getUuid()) && e.distanceTo(p) <= range));
        list.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(p)));
        return list.size() > max ? new ArrayList<>(list.subList(0, max)) : list;
    }

    /** Mobs atravessados por um raio a partir do olho do jogador, do mais perto ao mais longe (parando em blocos). */
    public static List<MobEntity> ray(ServerPlayerEntity p, TentacleRt rt, double range, int max) {
        ServerWorld w = (ServerWorld) p.getWorld();
        Vec3d eye = p.getEyePos();
        Vec3d dir = p.getRotationVec(1.0f);
        Vec3d end = eye.add(dir.multiply(range));
        var block = w.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        if (block.getType() != HitResult.Type.MISS) end = block.getPos();
        final Vec3d fEnd = end;
        Box box = new Box(eye, fEnd).expand(1.5);
        List<MobEntity> cands = w.getEntitiesByClass(MobEntity.class, box, e -> valid(p, e) && !rt.isHeld(e.getUuid()));
        List<MobEntity> hit = new ArrayList<>();
        for (MobEntity e : cands) {
            Optional<Vec3d> r = e.getBoundingBox().expand(0.4).raycast(eye, fEnd);
            if (r.isPresent()) {
                hit.add(e);
            }
        }
        // ordena pela distância do ponto de entrada
        List<MobEntity> sorted = new ArrayList<>(hit);
        sorted.sort(Comparator.comparingDouble(e -> e.getBoundingBox().expand(0.4).raycast(eye, fEnd).map(eye::squaredDistanceTo).orElse(1e9)));
        return sorted.size() > max ? new ArrayList<>(sorted.subList(0, max)) : sorted;
    }

    /** Ponto final do raio (até onde a lança vai se não acertar nada). */
    public static Vec3d rayEnd(ServerPlayerEntity p, double range) {
        ServerWorld w = (ServerWorld) p.getWorld();
        Vec3d eye = p.getEyePos();
        Vec3d end = eye.add(p.getRotationVec(1.0f).multiply(range));
        var block = w.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        return block.getType() != HitResult.Type.MISS ? block.getPos() : end;
    }

    public static Vec3d center(Entity e) {
        return e.getPos().add(0, e.getHeight() * 0.5, 0);
    }

    /** "Boca" do tronco: na frente do corpo, na altura da barriga. */
    public static Vec3d mouth(ServerPlayerEntity p) {
        return p.getPos().add(Vec3d.fromPolar(0, p.getYaw()).multiply(1.15)).add(0, 0.1, 0);
    }

    /** Puxa a vítima (pela velocidade, então as colisões com blocos continuam valendo) em direção a {@code goal}. */
    public static void pull(Entity e, Vec3d goal, double gain, double max) {
        Vec3d v = goal.subtract(e.getPos()).multiply(gain);
        if (v.length() > max) v = v.normalize().multiply(max);
        e.setVelocity(v);
        e.velocityModified = true;
        e.fallDistance = 0;
    }

    /** Segura a vítima no lugar (zera a velocidade horizontal). */
    public static void pin(Entity e) {
        Vec3d v = e.getVelocity();
        e.setVelocity(0, Math.min(v.y, 0), 0);
        e.velocityModified = true;
        e.fallDistance = 0;
    }

    /** Lentidão máxima (a vítima não anda); renovada a cada tick. */
    public static void slow(LivingEntity e) {
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 6, 6, false, false));
    }

    /** Indefesa: não anda, não causa dano e perde o alvo. */
    public static void helpless(LivingEntity e) {
        slow(e);
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 6, 9, false, false));
        if (e instanceof MobEntity m) m.setTarget(null);
    }

    /** Dano atribuído ao jogador (então o abate conta e o mob é devorado). {@code ignoreArmor} usa dano mágico. */
    public static void hurt(ServerPlayerEntity p, LivingEntity v, float amount, boolean ignoreArmor) {
        ServerWorld w = (ServerWorld) p.getWorld();
        DamageSource src = ignoreArmor ? w.getDamageSources().indirectMagic(p, p) : w.getDamageSources().playerAttack(p);
        v.timeUntilRegen = 0;
        v.damage(src, amount);
    }

    public static void blood(ServerWorld w, Vec3d pos, int n) {
        w.spawnParticles(ModParticles.BLOOD, pos.x, pos.y, pos.z, n, 0.25, 0.25, 0.25, 0.12);
    }

    public static void sound(ServerWorld w, Vec3d pos, SoundEvent s, float vol, float pitch) {
        w.playSound(null, pos.x, pos.y, pos.z, s, SoundCategory.PLAYERS, vol, pitch);
    }

    /** Ponto de parede sólida mais próximo (até ~0.9 bloco) em volta do tronco do jogador, ou null. */
    public static Vec3d wallPoint(ServerPlayerEntity p) {
        ServerWorld w = (ServerWorld) p.getWorld();
        Vec3d from = p.getPos().add(0, 1.0, 0);
        Vec3d best = null;
        double bestD = 1e9;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            Vec3d to = from.add(Math.cos(a) * 0.9, 0, Math.sin(a) * 0.9);
            var hit = w.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
            if (hit.getType() != HitResult.Type.MISS) {
                double d = from.squaredDistanceTo(hit.getPos());
                if (d < bestD) {
                    bestD = d;
                    best = hit.getPos();
                }
            }
        }
        return best;
    }

    /** Ponto do teto logo acima da cabeça (até ~0.7 bloco), ou null. */
    public static Vec3d ceilingPoint(ServerPlayerEntity p) {
        ServerWorld w = (ServerWorld) p.getWorld();
        Vec3d from = p.getPos().add(0, p.getHeight() - 0.1, 0);
        var hit = w.raycast(new RaycastContext(from, from.add(0, 0.8, 0), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        return hit.getType() != HitResult.Type.MISS ? hit.getPos() : null;
    }

    private Targets() {
    }
}
