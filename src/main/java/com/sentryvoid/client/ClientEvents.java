package com.sentryvoid.client;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import com.sentryvoid.SentryVoidMod;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SentryVoidMod.MODID, value = Dist.CLIENT)
public class ClientEvents {

    private static final Set<PlayerRenderer> PATCHED = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Quando uma tecla e apertada, manda o comando da habilidade para o servidor. */
    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || event.getAction() != 1) { // 1 = apertou
            return;
        }
        for (Map.Entry<KeyMapping, String> e : ClientSetup.KEYS.entrySet()) {
            if (e.getKey().matches(event.getKey(), event.getScanCode())) {
                mc.player.connection.sendCommand("sentryvoid " + e.getValue());
            }
        }
    }

    /** Adiciona a camada preta (skin full black) nos renderizadores de jogador. */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        PlayerRenderer renderer = event.getRenderer();
        if (PATCHED.add(renderer)) {
            renderer.addLayer(new VoidBlackLayer(renderer));
        }
    }
}
