package com.sentryvoid;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * /sentryvoid <habilidade>
 * As teclas do cliente chamam estes comandos. Nao precisa ser operador.
 */
@Mod.EventBusSubscriber(modid = SentryVoidMod.MODID)
public class VoidCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("sentryvoid")
                .requires(src -> src.getEntity() instanceof ServerPlayer);

        for (VoidAbilities.Ability ability : VoidAbilities.Ability.values()) {
            root.then(Commands.literal(ability.id).executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                VoidAbilities.use(player, ability);
                return 1;
            }));
        }
        event.getDispatcher().register(root);
    }
}
