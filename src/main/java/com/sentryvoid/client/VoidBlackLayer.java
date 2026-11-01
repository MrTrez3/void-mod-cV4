package com.sentryvoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sentryvoid.SentryVoidMod;
import com.sentryvoid.VoidAbilities;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** Desenha o corpo todo de preto (com olhos brancos) por cima da skin, quando a Forma do Void esta ativa. */
public class VoidBlackLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SentryVoidMod.MODID, "textures/entity/void_black.png");

    public VoidBlackLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (player.isInvisible() || !VoidAbilities.isForm(player)) {
            return;
        }
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.getParentModel().renderToBuffer(pose, vc, light,
                LivingEntityRenderer.getOverlayCoords(player, 0.0F), -1);
    }
}
