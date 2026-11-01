package com.sentryvoid;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Sombra deixada no lugar de um mob apagado. Guarda a "forma" do mob original. */
public class ShadowEntity extends Entity {

    /** Quanto tempo a sombra dura (em ticks). 12000 = 10 minutos. */
    public static final int LIFETIME = 12000;

    private static final EntityDataAccessor<String> MOB =
            SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> BODY_YAW =
            SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEAD_YAW =
            SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH =
            SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> BABY =
            SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> POSE =
            SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.INT);

    private int age;

    public ShadowEntity(EntityType<? extends ShadowEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Cria a sombra no chao, copiando a pose do alvo. */
    public static void spawnFrom(ServerLevel level, LivingEntity target) {
        ShadowEntity s = new ShadowEntity(ModEntities.SHADOW.get(), level);
        s.moveTo(target.getX(), groundY(level, target), target.getZ(), 0.0F, 0.0F);

        s.entityData.set(MOB, BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
        s.entityData.set(BODY_YAW, target.yBodyRot);
        s.entityData.set(HEAD_YAW, target.yHeadRot);
        s.entityData.set(PITCH, target.getXRot());
        s.entityData.set(BABY, target.isBaby());
        Pose pose = target.getPose();
        if (pose == Pose.DYING || pose == Pose.SLEEPING) {
            pose = Pose.STANDING;
        }
        s.entityData.set(POSE, pose.ordinal());

        level.addFreshEntity(s);
    }

    private static double groundY(ServerLevel level, LivingEntity target) {
        Vec3 from = target.position().add(0, 0.2, 0);
        BlockHitResult hit = level.clip(new ClipContext(from, from.add(0, -48, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
        return hit.getType() == HitResult.Type.MISS ? target.getY() : hit.getLocation().y;
    }

    // ---- dados para o cliente ----
    public String getMobId() { return this.entityData.get(MOB); }
    public float getBodyYaw() { return this.entityData.get(BODY_YAW); }
    public float getHeadYaw() { return this.entityData.get(HEAD_YAW); }
    public float getPitch() { return this.entityData.get(PITCH); }
    public boolean isBabyShadow() { return this.entityData.get(BABY); }
    public Pose getShadowPose() {
        Pose[] all = Pose.values();
        int i = this.entityData.get(POSE);
        return i >= 0 && i < all.length ? all[i] : Pose.STANDING;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MOB, "minecraft:zombie");
        builder.define(BODY_YAW, 0.0F);
        builder.define(HEAD_YAW, 0.0F);
        builder.define(PITCH, 0.0F);
        builder.define(BABY, false);
        builder.define(POSE, Pose.STANDING.ordinal());
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && ++this.age > LIFETIME) {
            this.discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(MOB, tag.getString("Mob"));
        this.entityData.set(BODY_YAW, tag.getFloat("BodyYaw"));
        this.entityData.set(HEAD_YAW, tag.getFloat("HeadYaw"));
        this.entityData.set(PITCH, tag.getFloat("Pitch"));
        this.entityData.set(BABY, tag.getBoolean("Baby"));
        this.entityData.set(POSE, tag.getInt("Pose"));
        this.age = tag.getInt("Age");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Mob", this.entityData.get(MOB));
        tag.putFloat("BodyYaw", this.entityData.get(BODY_YAW));
        tag.putFloat("HeadYaw", this.entityData.get(HEAD_YAW));
        tag.putFloat("Pitch", this.entityData.get(PITCH));
        tag.putBoolean("Baby", this.entityData.get(BABY));
        tag.putInt("Pose", this.entityData.get(POSE));
        tag.putInt("Age", this.age);
    }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }
}
