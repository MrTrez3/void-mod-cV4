package com.sentryvoid.client;

import java.util.LinkedHashMap;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.sentryvoid.ModEntities;
import com.sentryvoid.SentryVoidMod;
import com.sentryvoid.VoidAbilities;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SentryVoidMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    public static final String CATEGORY = "key.categories.sentryvoid";

    /** tecla -> id da habilidade */
    public static final Map<KeyMapping, String> KEYS = new LinkedHashMap<>();

    // Z X C V B N M, na ordem das habilidades (mude em Opcoes > Controles)
    private static final int[] DEFAULT_KEYS = {
            GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V,
            GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_M
    };

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        VoidAbilities.Ability[] all = VoidAbilities.Ability.values();
        for (int i = 0; i < all.length; i++) {
            KeyMapping km = new KeyMapping("key.sentryvoid." + all[i].id,
                    InputConstants.Type.KEYSYM, DEFAULT_KEYS[i], CATEGORY);
            event.register(km);
            KEYS.put(km, all[i].id);
        }
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SHADOW.get(), ShadowRenderer::new);
    }
}
