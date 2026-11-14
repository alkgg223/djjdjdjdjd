package com.realweather.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.realweather.ClientConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.Locale;

/**
 * /rwgraphics: switch the tornado graphics and the weather (rain, snow, clouds) detail between LOW, MEDIUM, HIGH and
 * ULTRA right in game; the choice is saved to the client config.
 */
public final class ClientCommands {
    private ClientCommands() {}

    public static void register(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("rwgraphics").executes(c -> show(c.getSource()));
        LiteralArgumentBuilder<CommandSourceStack> tornado = Commands.literal("tornado");
        for (ClientConfig.TornadoQuality q : ClientConfig.TornadoQuality.values()) {
            tornado.then(Commands.literal(q.name().toLowerCase(Locale.ROOT)).executes(c -> {
                ClientConfig.TORNADO_QUALITY.set(q);
                ClientConfig.SPEC.save();
                return show(c.getSource());
            }));
        }
        LiteralArgumentBuilder<CommandSourceStack> weather = Commands.literal("weather");
        for (ClientConfig.Quality q : ClientConfig.Quality.values()) {
            weather.then(Commands.literal(q.name().toLowerCase(Locale.ROOT)).executes(c -> {
                ClientConfig.QUALITY.set(q);
                ClientConfig.SPEC.save();
                return show(c.getSource());
            }));
        }
        LiteralArgumentBuilder<CommandSourceStack> space = Commands.literal("space");
        for (ClientConfig.SpaceQuality q : ClientConfig.SpaceQuality.values()) {
            space.then(Commands.literal(q.name().toLowerCase(Locale.ROOT)).executes(c -> {
                ClientConfig.SPACE_QUALITY.set(q);
                ClientConfig.SPEC.save();
                return show(c.getSource());
            }));
        }
        root.then(tornado).then(weather).then(space);
        event.getDispatcher().register(root);
    }

    private static int show(CommandSourceStack src) {
        src.sendSuccess(() -> Component.translatable("realweather.cmd.graphics", ClientConfig.TORNADO_QUALITY.get().name(),
                ClientConfig.QUALITY.get().name()), false);
        src.sendSuccess(() -> Component.translatable("realweather.cmd.space_graphics", ClientConfig.SPACE_QUALITY.get().name()), false);
        return 1;
    }
}
