package com.devourer;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

/** Todos os poderes. Qualquer espécie tem um poder; algumas têm um poder especial. */
public final class Powers {

    @FunctionalInterface
    public interface Active {
        void use(ServerPlayerEntity p);
    }

    public record Power(String key, String name, StatusEffect passive, int amp, Active active, int cooldown) {
    }

    private static final Map<String, Power> SPECIAL = new HashMap<>();
    private static final Map<String, Power> GENERIC = new HashMap<>();
    private static final Map<String, Long> COOLDOWN = new HashMap<>();

    private static final StatusEffect[] POOL = {
            StatusEffects.SPEED, StatusEffects.JUMP_BOOST, StatusEffects.RESISTANCE, StatusEffects.STRENGTH,
            StatusEffects.HASTE, StatusEffects.NIGHT_VISION, StatusEffects.WATER_BREATHING,
            StatusEffects.FIRE_RESISTANCE, StatusEffects.SLOW_FALLING, StatusEffects.ABSORPTION,
            StatusEffects.REGENERATION, StatusEffects.LUCK
    };

    private static void special(String id, String name, StatusEffect passive, int amp, Active a, int cd) {
        SPECIAL.put(id, new Power(id, name, passive, amp, a, cd));
    }

    static {
        special("minecraft:creeper", "Explosão Instável", null, 0, Powers::creeper, 240);
        special("minecraft:skeleton", "Rajada de Ossos", null, 0, Powers::skeleton, 100);
        special("minecraft:stray", "Rajada Gélida", null, 0, Powers::skeleton, 100);
        special("minecraft:zombie", "Fúria Podre", null, 0, Powers::zombie, 300);
        special("minecraft:husk", "Fúria Seca", null, 0, Powers::zombie, 300);
        special("minecraft:drowned", "Maré Afogada", StatusEffects.WATER_BREATHING, 0, Powers::zombie, 300);
        special("minecraft:enderman", "Salto do Vazio", null, 0, Powers::enderman, 60);
        special("minecraft:blaze", "Chamas Vivas", StatusEffects.FIRE_RESISTANCE, 0, Powers::blaze, 80);
        special("minecraft:ghast", "Lágrima Ígnea", null, 0, Powers::ghast, 140);
        special("minecraft:spider", "Teia Predadora", StatusEffects.NIGHT_VISION, 0, Powers::spider, 160);
        special("minecraft:cave_spider", "Veneno de Caverna", StatusEffects.NIGHT_VISION, 0, Powers::caveSpider, 160);
        special("minecraft:slime", "Salto Gelatinoso", StatusEffects.JUMP_BOOST, 0, Powers::slime, 60);
        special("minecraft:wither_skeleton", "Aura Murcha", null, 0, Powers::witherAura, 200);
        special("minecraft:witch", "Brew Profano", null, 0, Powers::witch, 400);
        special("minecraft:chicken", "Pena Leve", StatusEffects.SLOW_FALLING, 0, Powers::slime, 60);
        special("minecraft:iron_golem", "Punho de Ferro", StatusEffects.RESISTANCE, 0, Powers::golem, 160);
        special("minecraft:wolf", "Instinto da Matilha", StatusEffects.SPEED, 0, Powers::wolf, 200);
        special("minecraft:phantom", "Investida Fantasma", null, 0, Powers::phantom, 80);
        special("minecraft:guardian", "Olhar do Guardião", StatusEffects.WATER_BREATHING, 0, Powers::guardian, 120);
    }

    public static Power get(String id) {
        Power p = SPECIAL.get(id);
        if (p != null) return p;
        return GENERIC.computeIfAbsent(id, k -> {
            StatusEffect e = POOL[Math.floorMod(k.hashCode(), POOL.length)];
            return new Power(k, "Essência de " + pretty(k), e, 0, Powers::pulse, 120);
        });
    }

    private static String pretty(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        path = path.replace('_', ' ');
        return path.isEmpty() ? path : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    // ---------------------------------------------------------------- uso

    public static void applyPassives(ServerPlayerEntity p, PlayerDevour d) {
        for (String key : d.powers) {
            Power pw = get(key);
            if (pw.passive() != null) {
                p.addStatusEffect(new StatusEffectInstance(pw.passive(), 320, pw.amp(), true, false, true));
            }
        }
    }

    public static void cycle(ServerPlayerEntity p) {
        MinecraftServer s = p.getServer();
        if (s == null) return;
        PlayerDevour d = DevourState.of(s).get(p.getUuid());
        if (d.powers.isEmpty()) {
            p.sendMessage(Text.literal("Você ainda não devorou nenhuma criatura.").formatted(Formatting.GRAY), true);
            return;
        }
        d.selected = Math.floorMod(d.selected + 1, d.powers.size());
        DevourState.of(s).markDirty();
        Power pw = get(new ArrayList<>(d.powers).get(d.selected));
        p.sendMessage(Text.literal("Poder: ").formatted(Formatting.DARK_RED)
                .append(Text.literal(pw.name()).formatted(Formatting.GOLD)), true);
    }

    public static void activate(ServerPlayerEntity p) {
        MinecraftServer s = p.getServer();
        if (s == null) return;
        PlayerDevour d = DevourState.of(s).get(p.getUuid());
        if (d.powers.isEmpty()) {
            p.sendMessage(Text.literal("Você ainda não devorou nenhuma criatura.").formatted(Formatting.GRAY), true);
            return;
        }
        List<String> list = new ArrayList<>(d.powers);
        d.selected = Math.floorMod(d.selected, list.size());
        Power pw = get(list.get(d.selected));

        long now = p.getWorld().getTime();
        String ck = p.getUuid() + "|" + pw.key();
        Long until = COOLDOWN.get(ck);
        if (until != null && until > now) {
            p.sendMessage(Text.literal(pw.name() + " recarregando: " + ((until - now) / 20 + 1) + "s")
                    .formatted(Formatting.GRAY), true);
            return;
        }
        COOLDOWN.put(ck, now + pw.cooldown());
        pw.active().use(p);
        p.sendMessage(Text.literal(pw.name()).formatted(Formatting.DARK_RED, Formatting.BOLD), true);
    }

    // ---------------------------------------------------------------- poderes ativos

    private static ServerWorld sw(ServerPlayerEntity p) {
        return (ServerWorld) p.getWorld();
    }

    private static void sound(ServerPlayerEntity p, SoundEvent e, float pitch) {
        p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(), e, SoundCategory.PLAYERS, 1.0f, pitch);
    }

    private static List<LivingEntity> near(ServerPlayerEntity p, double r) {
        return p.getWorld().getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(r), e -> e != p && e.isAlive());
    }

    private static void bloodBurst(ServerPlayerEntity p) {
        sw(p).spawnParticles(ModParticles.BLOOD, p.getX(), p.getY() + 1, p.getZ(), 25, 0.4, 0.5, 0.4, 0.12);
    }

    private static void pulse(ServerPlayerEntity p) {
        for (LivingEntity e : near(p, 5)) {
            e.damage(p.getDamageSources().playerAttack(p), 5.0f);
            e.takeKnockback(0.8, p.getX() - e.getX(), p.getZ() - e.getZ());
        }
        bloodBurst(p);
        sound(p, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1.6f);
    }

    private static void creeper(ServerPlayerEntity p) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 20, 4, true, false));
        p.getWorld().createExplosion(p, p.getX(), p.getY(), p.getZ(), 3.2f, World.ExplosionSourceType.NONE);
        bloodBurst(p);
    }

    private static void skeleton(ServerPlayerEntity p) {
        for (int i = -2; i <= 2; i++) {
            ArrowEntity a = new ArrowEntity(p.getWorld(), p);
            a.setVelocity(p, p.getPitch(), p.getYaw() + i * 7f, 0f, 3.2f, 1.0f);
            a.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
            a.setDamage(4.0);
            p.getWorld().spawnEntity(a);
        }
        sound(p, SoundEvents.ENTITY_SKELETON_SHOOT, 0.8f);
    }

    private static void zombie(ServerPlayerEntity p) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 2));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 1));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 1));
        sound(p, SoundEvents.ENTITY_ZOMBIE_AMBIENT, 0.5f);
        bloodBurst(p);
    }

    private static void enderman(ServerPlayerEntity p) {
        Vec3d look = p.getRotationVec(1.0f);
        HitResult hit = p.raycast(28, 1.0f, false);
        Vec3d end = hit.getPos();
        if (hit.getType() == HitResult.Type.BLOCK) end = end.subtract(look.multiply(0.8));
        sound(p, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1f);
        p.requestTeleport(end.x, end.y, end.z);
        sound(p, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1f);
        p.fallDistance = 0;
    }

    private static void blaze(ServerPlayerEntity p) {
        Vec3d look = p.getRotationVec(1.0f);
        for (int i = -1; i <= 1; i++) {
            Vec3d dir = look.rotateY(i * 0.18f);
            SmallFireballEntity f = new SmallFireballEntity(p.getWorld(), p, dir.x, dir.y, dir.z);
            f.setPosition(p.getX() + dir.x, p.getEyeY() - 0.2, p.getZ() + dir.z);
            p.getWorld().spawnEntity(f);
        }
        sound(p, SoundEvents.ENTITY_BLAZE_SHOOT, 1f);
    }

    private static void ghast(ServerPlayerEntity p) {
        Vec3d look = p.getRotationVec(1.0f);
        FireballEntity f = new FireballEntity(p.getWorld(), p, look.x, look.y, look.z, 2);
        f.setPosition(p.getX() + look.x * 1.5, p.getEyeY() - 0.2 + look.y * 1.5, p.getZ() + look.z * 1.5);
        p.getWorld().spawnEntity(f);
        sound(p, SoundEvents.ENTITY_GHAST_SHOOT, 1f);
    }

    private static void spider(ServerPlayerEntity p) {
        for (LivingEntity e : near(p, 9)) {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 140, 3));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 140, 0));
        }
        sound(p, SoundEvents.ENTITY_SPIDER_AMBIENT, 0.6f);
    }

    private static void caveSpider(ServerPlayerEntity p) {
        for (LivingEntity e : near(p, 8)) e.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 160, 1));
        sound(p, SoundEvents.ENTITY_SPIDER_HURT, 0.6f);
    }

    private static void slime(ServerPlayerEntity p) {
        p.addVelocity(0, 1.3, 0);
        p.velocityModified = true;
        p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 80, 0));
        sound(p, SoundEvents.ENTITY_SLIME_JUMP, 1f);
    }

    private static void witherAura(ServerPlayerEntity p) {
        for (LivingEntity e : near(p, 7)) e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 120, 1));
        sound(p, SoundEvents.ENTITY_WITHER_SKELETON_AMBIENT, 0.7f);
    }

    private static void witch(ServerPlayerEntity p) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 160, 2));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 400, 2));
        sound(p, SoundEvents.ENTITY_WITCH_DRINK, 1f);
    }

    private static void golem(ServerPlayerEntity p) {
        for (LivingEntity e : near(p, 6)) {
            e.damage(p.getDamageSources().playerAttack(p), 8.0f);
            e.addVelocity(0, 0.9, 0);
            e.velocityModified = true;
        }
        sound(p, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 0.8f);
    }

    private static void wolf(ServerPlayerEntity p) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 3));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 200, 1));
        sound(p, SoundEvents.ENTITY_WOLF_GROWL, 0.8f);
    }

    private static void phantom(ServerPlayerEntity p) {
        Vec3d look = p.getRotationVec(1.0f).multiply(2.4);
        p.addVelocity(look.x, Math.max(0.25, look.y), look.z);
        p.velocityModified = true;
        p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
        p.fallDistance = 0;
        sound(p, SoundEvents.ENTITY_PHANTOM_SWOOP, 1f);
    }

    private static void guardian(ServerPlayerEntity p) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1.0f);
        for (LivingEntity e : near(p, 16)) {
            Vec3d to = e.getEyePos().subtract(eye).normalize();
            if (to.dotProduct(look) > 0.97) e.damage(p.getDamageSources().indirectMagic(p, p), 9.0f);
        }
        sound(p, SoundEvents.ENTITY_GUARDIAN_ATTACK, 1f);
    }

    private Powers() {
    }
}
