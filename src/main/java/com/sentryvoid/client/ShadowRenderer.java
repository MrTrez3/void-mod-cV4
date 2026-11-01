package com.sentryvoid.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joml.Matrix4f;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sentryvoid.ShadowEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/**
 * Desenha a sombra: o mob original, so que todo preto.
 * FLAT = true  -> sombra "projetada" deitada no chao (como no video).
 * FLAT = false -> silhueta preta em pe.
 */
public class ShadowRenderer extends EntityRenderer<ShadowEntity> {

    private static final boolean FLAT = true;

    // direcao/comprimento da sombra projetada (x e z por bloco de altura)
    private static final float SHEAR_X = 0.9F;
    private static final float SHEAR_Z = 0.5F;

    // x' = x + SHEAR_X*y ; y' = 0.01*y + 0.02 ; z' = z + SHEAR_Z*y   (matriz em ordem de colunas)
    private static final Matrix4f SHADOW_MATRIX = new Matrix4f(
            1.0F, 0.0F, 0.0F, 0.0F,
            SHEAR_X, 0.01F, SHEAR_Z, 0.0F,
            0.0F, 0.0F, 1.0F, 0.0F,
            0.0F, 0.02F, 0.0F, 1.0F);

    private final Map<String, Entity> dummies = new HashMap<>();

    public ShadowRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowEntity entity) {
        return VoidBlackLayer.TEXTURE;
    }

    @Override
    public void render(ShadowEntity shadow, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        Entity dummy = getDummy(shadow);
        if (dummy == null) {
            return;
        }
        applyShape(dummy, shadow);

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderer<? super Entity> renderer = dispatcher.getRenderer(dummy);
        MultiBufferSource black = renderType -> new BlackConsumer(buffer.getBuffer(renderType));

        pose.pushPose();
        if (FLAT) {
            pose.mulPose(SHADOW_MATRIX);
        }
        renderer.render(dummy, shadow.getBodyYaw(), partialTick, pose, black, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private Entity getDummy(ShadowEntity shadow) {
        String id = shadow.getMobId();
        Level level = shadow.level();
        Entity dummy = dummies.get(id);
        if (dummy == null || dummy.level() != level) {
            dummy = createDummy(id, level);
            if (dummy == null) {
                return null;
            }
            dummies.put(id, dummy);
        }
        return dummy;
    }

    private static Entity createDummy(String id, Level level) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) {
                return null;
            }
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
            if (type == null) {
                return null;
            }
            if (type == EntityType.PLAYER && level instanceof ClientLevel cl) {
                return new RemotePlayer(cl, new GameProfile(UUID.randomUUID(), "shadow"));
            }
            return type.create(level);
        } catch (Exception e) {
            return null;
        }
    }

    private static void applyShape(Entity dummy, ShadowEntity shadow) {
        float body = shadow.getBodyYaw();
        float head = shadow.getHeadYaw();
        float pitch = shadow.getPitch();

        dummy.setPos(shadow.getX(), shadow.getY(), shadow.getZ());
        dummy.setYRot(body);
        dummy.yRotO = body;
        dummy.setXRot(pitch);
        dummy.xRotO = pitch;
        dummy.tickCount = 0;
        dummy.setPose(shadow.getShadowPose());

        if (dummy instanceof LivingEntity living) {
            living.yBodyRot = body;
            living.yBodyRotO = body;
            living.yHeadRot = head;
            living.yHeadRotO = head;
            if (living instanceof AgeableMob ageable) {
                ageable.setBaby(shadow.isBabyShadow());
            } else if (living instanceof Zombie zombie) {
                zombie.setBaby(shadow.isBabyShadow());
            }
        }
    }
}
