package com.sentryvoid;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(SentryVoidMod.MODID)
public class SentryVoidMod {
    public static final String MODID = "sentryvoid";

    public SentryVoidMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITIES.register(modBus);
    }
}
