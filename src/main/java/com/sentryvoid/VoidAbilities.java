package com.sentryvoid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Todas as habilidades do Void. Nenhuma gasta fome, vida ou item. */
public final class VoidAbilities {

    /** O estado "Forma do Void" e um modificador de velocidade com este id (o cliente consegue ler). */
    public static final ResourceLocation FORM_ID =
            ResourceLocation.fromNamespaceAndPath(SentryVoidMod.MODID, "void_form");

    public enum Ability {
        FORM("form", 10),
        BLAST("blast", 10),
        GRASP("grasp", 20),
        DASH("dash", 20),
        SINGULARITY("singularity", 60),
        ENGULF("engulf", 100),
        ERASE("erase", 20);

        public final String id;
        public final int cooldown; // ticks (20 = 1 segundo)

        Ability(String id, int cooldown) {
            this.id = id;
            this.cooldown = cooldown;
        }
    }

    private static final Map<String, Long> NEXT_USE = new HashMap<>();

    private VoidAbilities() {}

    public static boolean isForm(Player p) {
        AttributeInstance a = p.getAttribute(Attributes.MOVEMENT_SPEED);
        return a != null && a.hasModifier(FORM_ID);
    }

    public static void use(ServerPlayer p, Ability ability) {
        long now = p.level().getGameTime();
        String key = p.getUUID() + ":" + ability.id;
        if (NEXT_USE.getOrDefault(key, 0L) > now) {
            return;
        }
        ServerLevel level = p.serverLevel();
        boolean ok = switch (ability) {
            case FORM -> toggleForm(level, p);
            case BLAST -> blast(level, p);
            case GRASP -> grasp(level, p);
            case DASH -> dash(level, p);
            case SINGULARITY -> singularity(level, p);
            case ENGULF -> engulf(level, p);
            case ERASE -> erase(level, p);
        };
        if (ok) {
            NEXT_USE.put(key, now + ability.cooldown);
        }
    }

    // ---------------- Forma do Void ----------------

    private static final List<Holder<MobEffect>> BUFFS = List.of(
            MobEffects.DAMAGE_RESISTANCE, MobEffects.DAMAGE_BOOST, MobEffects.NIGHT_VISION,
            MobEffects.SLOW_FALLING, MobEffects.WATER_BREATHING, MobEffects.FIRE_RESISTANCE);

    private static boolean toggleForm(ServerLevel level, ServerPlayer p) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return false;
        }
        boolean on = !speed.hasModifier(FORM_ID);
        Abilities ab = p.getAbilities();

        if (on) {
            speed.addPermanentModifier(new AttributeModifier(FORM_ID, 0.6,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 2, true, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobEffectInstance.INFINITE_DURATION, 2, true, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, true, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, MobEffectInstance.INFINITE_DURATION, 0, true, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, MobEffectInstance.INFINITE_DURATION, 0, true, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, true, false, false));
            ab.mayfly = true;
            ab.setFlyingSpeed(0.12F);
            p.onUpdateAbilities();
        } else {
            speed.removeModifier(FORM_ID);
            for (Holder<MobEffect> h : BUFFS) {
                MobEffectInstance inst = p.getEffect(h);
                if (inst != null && inst.isInfiniteDuration()) {
                    p.removeEffect(h);
                }
            }
            if (!p.isCreative() && !p.isSpectator()) {
                ab.mayfly = false;
                ab.flying = false;
            }
            ab.setFlyingSpeed(0.05F);
            p.onUpdateAbilities();
        }

        p.displayClientMessage(Component.translatable(
                on ? "message.sentryvoid.form_on" : "message.sentryvoid.form_off")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        level.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0f, on ? 0.4f : 0.8f);
        level.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1.0, p.getZ(),
                80, 0.5, 0.9, 0.5, 0.1);
        return true;
    }

    // ---------------- Rajada ----------------

    private static boolean blast(ServerLevel level, ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        double dist = rayDistance(level, p, 50.0);

        for (double d = 1.5; d < dist; d += 0.6) {
            Vec3 pt = eye.add(look.scale(d)).add(0, -0.2, 0);
            level.sendParticles(ParticleTypes.SQUID_INK, pt.x, pt.y, pt.z, 2, 0.08, 0.08, 0.08, 0.0);
            level.sendParticles(ParticleTypes.SMOKE, pt.x, pt.y, pt.z, 1, 0.1, 0.1, 0.1, 0.0);
        }

        LivingEntity target = findTarget(p, dist);
        if (target != null) {
            target.hurt(voidDamage(level, p), 12.0f);
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100));
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1));
            target.push(look.x * 0.8, 0.3, look.z * 0.8);
            target.hurtMarked = true;
        }
        level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 1.0f, 0.6f);
        return true;
    }

    // ---------------- Garra de Sombra ----------------

    private static boolean grasp(ServerLevel level, ServerPlayer p) {
        LivingEntity target = findTarget(p, rayDistance(level, p, 30.0));
        if (target == null) {
            noTarget(p);
            return false;
        }
        Vec3 from = target.position().add(0, target.getBbHeight() / 2, 0);
        Vec3 to = p.position().add(0, p.getBbHeight() / 2, 0);
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        for (double d = 0; d < len; d += 0.5) {
            Vec3 pt = from.add(dir.scale(d / len));
            level.sendParticles(ParticleTypes.SQUID_INK, pt.x, pt.y, pt.z, 2, 0.05, 0.05, 0.05, 0.0);
        }
        target.setDeltaMovement(dir.normalize().scale(1.8).add(0, 0.3, 0));
        target.hurtMarked = true;
        target.hurt(voidDamage(level, p), 2.0f);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
        level.playSound(null, p.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK,
                SoundSource.PLAYERS, 0.6f, 1.4f);
        return true;
    }

    // ---------------- Impulso ----------------

    private static boolean dash(ServerLevel level, ServerPlayer p) {
        p.setDeltaMovement(p.getLookAngle().scale(2.6));
        p.hurtMarked = true;
        p.fallDistance = 0.0F;
        level.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1.0, p.getZ(),
                50, 0.4, 0.6, 0.4, 0.15);
        level.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0f, 0.3f);
        return true;
    }

    // ---------------- Singularidade ----------------

    private static boolean singularity(ServerLevel level, ServerPlayer p) {
        double dist = Math.min(rayDistance(level, p, 30.0), 30.0);
        Vec3 center = p.getEyePosition().add(p.getLookAngle().scale(dist));

        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(12.0), e -> e != p && e.isAlive());
        for (LivingEntity e : victims) {
            Vec3 to = center.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
            double len = to.length();
            Vec3 v = len > 0.5 ? to.normalize().scale(Math.min(2.0, 0.4 + len * 0.15)) : Vec3.ZERO;
            e.setDeltaMovement(v);
            e.hurtMarked = true;
            e.hurt(voidDamage(level, p), 6.0f);
            e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100));
        }

        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 120, 1.5, 1.5, 1.5, 0.15);
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48.0;
            level.sendParticles(ParticleTypes.SQUID_INK, center.x + Math.cos(a) * 4, center.y,
                    center.z + Math.sin(a) * 4, 1, 0, 0, 0, 0.0);
        }
        level.playSound(null, center.x, center.y, center.z, SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.5f, 0.3f);
        return true;
    }

    // ---------------- Engolir ----------------

    private static boolean engulf(ServerLevel level, ServerPlayer p) {
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(20.0), e -> e != p && e.isAlive());
        for (LivingEntity e : victims) {
            e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 300));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200));
            e.hurt(voidDamage(level, p), 4.0f);
        }
        level.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1.0, p.getZ(),
                400, 8.0, 3.0, 8.0, 0.05);
        level.sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 1.0, p.getZ(),
                80, 6.0, 2.0, 6.0, 0.03);
        level.playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN,
                SoundSource.PLAYERS, 0.7f, 0.5f);
        return true;
    }

    // ---------------- Apagar (deixa so a sombra) ----------------

    private static boolean erase(ServerLevel level, ServerPlayer p) {
        LivingEntity target = findTarget(p, rayDistance(level, p, 50.0));
        if (target == null) {
            noTarget(p);
            return false;
        }
        if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
            p.displayClientMessage(Component.translatable("message.sentryvoid.immune")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (target instanceof EnderDragon || target instanceof WitherBoss) {
            target.hurt(voidDamage(level, p), 100.0f); // chefes nao sao apagados
            return true;
        }

        double x = target.getX();
        double y = target.getY();
        double z = target.getZ();
        double h = target.getBbHeight();
        double w = Math.max(0.3, target.getBbWidth() / 2.0);

        ShadowEntity.spawnFrom(level, target);

        level.sendParticles(ParticleTypes.SQUID_INK, x, y + h / 2, z, 60, w, h / 2, w, 0.03);
        level.sendParticles(ParticleTypes.SCULK_SOUL, x, y + h / 2, z, 20, w, h / 2, w, 0.04);
        level.playSound(null, x, y, z, SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 0.4f, 0.4f);

        if (target instanceof Player) {
            target.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
        } else {
            target.discard(); // some sem drops e sem XP
        }
        return true;
    }

    // ---------------- utilidades ----------------

    private static void noTarget(ServerPlayer p) {
        p.displayClientMessage(Component.translatable("message.sentryvoid.no_target")
                .withStyle(ChatFormatting.GRAY), true);
    }

    private static DamageSource voidDamage(ServerLevel level, Player p) {
        return level.damageSources().indirectMagic(p, p);
    }

    private static double rayDistance(ServerLevel level, Player p, double max) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(max));
        BlockHitResult hit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? max : eye.distanceTo(hit.getLocation());
    }

    private static LivingEntity findTarget(Player p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        AABB box = p.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        Predicate<Entity> filter = e -> e instanceof LivingEntity && e != p && !e.isSpectator() && e.isPickable();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, box, filter, range * range);
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }
}
