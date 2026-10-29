package com.realweather.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /realweather (short: /rw) controls the weather:
 * rain, snow, thunder, wind, sandstorm, blizzard, flood, snowpile, freezing, villagers, natural, auto, clear, info.
 */
public final class WeatherCommands {
    private WeatherCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("realweather").requires(s -> s.hasPermission(2));

        root.then(intensity("rain", 10, WeatherCommands::rain));
        root.then(intensity("snow", 10, WeatherCommands::snow));
        root.then(intensity("thunder", 10, WeatherCommands::thunder));
        root.then(intensity("sandstorm", 6, WeatherCommands::sandstorm)
                .then(Commands.literal("stop").executes(c -> sandstorm(c.getSource(), 0, 0))));
        root.then(intensity("blizzard", 6, WeatherCommands::blizzard)
                .then(Commands.literal("stop").executes(c -> blizzard(c.getSource(), 0, 0))));
        root.then(Commands.literal("wind")
                .then(Commands.literal("auto").executes(c -> wind(c.getSource(), -1, -1)))
                .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                        .executes(c -> wind(c.getSource(), IntegerArgumentType.getInteger(c, "percent"), -1))
                        .then(Commands.argument("direction", IntegerArgumentType.integer(0, 359))
                                .executes(c -> wind(c.getSource(), IntegerArgumentType.getInteger(c, "percent"), IntegerArgumentType.getInteger(c, "direction"))))));
        root.then(Commands.literal("flood")
                .then(Commands.literal("on").executes(c -> toggle(c, "flood", true)))
                .then(Commands.literal("off").executes(c -> toggle(c, "flood", false)))
                .then(Commands.literal("drain").executes(WeatherCommands::drain))
                .then(Commands.literal("depth").then(Commands.argument("blocks", IntegerArgumentType.integer(1, 4))
                        .executes(c -> floodDepth(c.getSource(), IntegerArgumentType.getInteger(c, "blocks"))))));
        root.then(onOff("snowpile"));
        root.then(onOff("freezing"));
        root.then(onOff("villagers"));
        root.then(onOff("natural"));
        root.then(Commands.literal("auto").executes(c -> auto(c.getSource())));
        root.then(Commands.literal("clear").executes(c -> clear(c.getSource())));
        root.then(Commands.literal("info").executes(c -> info(c.getSource())));

        var node = dispatcher.register(root);
        dispatcher.register(Commands.literal("rw").requires(s -> s.hasPermission(2)).redirect(node));
    }

    private interface IntensityAction {
        int run(CommandSourceStack src, int percent, int minutes);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> intensity(String name, int defaultMinutes, IntensityAction action) {
        return Commands.literal(name)
                .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                        .executes(c -> action.run(c.getSource(), IntegerArgumentType.getInteger(c, "percent"), defaultMinutes))
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 1440))
                                .executes(c -> action.run(c.getSource(), IntegerArgumentType.getInteger(c, "percent"), IntegerArgumentType.getInteger(c, "minutes")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> onOff(String name) {
        return Commands.literal(name)
                .then(Commands.literal("on").executes(c -> toggle(c, name, true)))
                .then(Commands.literal("off").executes(c -> toggle(c, name, false)));
    }

    private static ServerLevel overworld(CommandSourceStack src) {
        return src.getServer().overworld();
    }

    private static void ok(CommandSourceStack src, String key, Object... args) {
        src.sendSuccess(() -> Component.translatable("realweather.cmd." + key, args), true);
        WeatherServer.requestSync();
    }

    private static int rain(CommandSourceStack src, int percent, int minutes) {
        ServerLevel level = overworld(src);
        WeatherData d = WeatherData.get(level);
        if (percent == 0) {
            d.rainOverride = -1F;
            d.thunderOverride = -1F;
            d.blizzardTicks = 0;
            level.setWeatherParameters(12000, 0, false, false);
            ok(src, "rain.stop");
        } else {
            d.rainOverride = percent / 100F;
            level.setWeatherParameters(0, minutes * 1200, true, level.isThundering());
            ok(src, "rain", percent, minutes);
        }
        d.setDirty();
        return 1;
    }

    private static int snow(CommandSourceStack src, int percent, int minutes) {
        ServerLevel level = overworld(src);
        WeatherData d = WeatherData.get(level);
        if (percent == 0) {
            d.snowOverride = -1F;
            d.blizzardTicks = 0;
            level.setWeatherParameters(12000, 0, false, false);
            ok(src, "snow.stop");
        } else {
            d.snowOverride = percent / 100F;
            level.setWeatherParameters(0, minutes * 1200, true, level.isThundering());
            ok(src, "snow", percent, minutes);
        }
        d.setDirty();
        return 1;
    }

    private static int thunder(CommandSourceStack src, int percent, int minutes) {
        ServerLevel level = overworld(src);
        WeatherData d = WeatherData.get(level);
        if (percent == 0) {
            d.thunderOverride = -1F;
            level.setWeatherParameters(0, Math.max(1200, minutes * 1200), level.isRaining(), false);
            ok(src, "thunder.stop");
        } else {
            d.thunderOverride = percent / 100F;
            level.setWeatherParameters(0, minutes * 1200, true, true);
            ok(src, "thunder", percent, minutes);
        }
        d.setDirty();
        return 1;
    }

    private static int sandstorm(CommandSourceStack src, int percent, int minutes) {
        WeatherData d = WeatherData.get(overworld(src));
        if (percent == 0) {
            d.sandTicks = 0;
            ok(src, "sandstorm.stop");
        } else {
            d.sandTarget = percent / 100F;
            d.sandTicks = minutes * 1200;
            ok(src, "sandstorm", percent, minutes);
        }
        d.setDirty();
        return 1;
    }

    private static int blizzard(CommandSourceStack src, int percent, int minutes) {
        ServerLevel level = overworld(src);
        WeatherData d = WeatherData.get(level);
        if (percent == 0) {
            d.blizzardTicks = 0;
            ok(src, "blizzard.stop");
        } else {
            d.blizzardTarget = percent / 100F;
            d.blizzardTicks = minutes * 1200;
            level.setWeatherParameters(0, minutes * 1200 + 600, true, level.isThundering());
            ok(src, "blizzard", percent, minutes);
        }
        d.setDirty();
        return 1;
    }

    private static int wind(CommandSourceStack src, int percent, int direction) {
        WeatherData d = WeatherData.get(overworld(src));
        if (percent < 0) {
            d.windOverride = -1F;
            ok(src, "wind.auto");
        } else {
            d.windOverride = percent / 100F;
            if (direction >= 0) {
                // Compass bearing the wind blows towards: 0 = north, 90 = east.
                d.windAngle = (float) Math.toRadians(direction);
            }
            ok(src, "wind", percent);
        }
        d.setDirty();
        return 1;
    }

    private static int toggle(CommandContext<CommandSourceStack> c, String what, boolean on) {
        WeatherData d = WeatherData.get(overworld(c.getSource()));
        switch (what) {
            case "flood" -> d.flood = on;
            case "snowpile" -> d.snowPile = on;
            case "freezing" -> d.freezing = on;
            case "villagers" -> d.villagers = on;
            case "natural" -> d.natural = on;
            default -> {
            }
        }
        d.setDirty();
        ok(c.getSource(), what + (on ? ".on" : ".off"));
        return 1;
    }

    private static int floodDepth(CommandSourceStack src, int blocks) {
        WeatherData d = WeatherData.get(overworld(src));
        d.floodDepth = blocks;
        d.setDirty();
        ok(src, "flood.depth", blocks);
        return 1;
    }

    private static int drain(CommandContext<CommandSourceStack> c) {
        ServerLevel level = overworld(c.getSource());
        WeatherData d = WeatherData.get(level);
        int removed = FloodManager.drain(level, d, Integer.MAX_VALUE / 8);
        ok(c.getSource(), "flood.drain", removed);
        return 1;
    }

    private static int auto(CommandSourceStack src) {
        WeatherData d = WeatherData.get(overworld(src));
        d.rainOverride = -1F;
        d.snowOverride = -1F;
        d.thunderOverride = -1F;
        d.windOverride = -1F;
        d.setDirty();
        ok(src, "auto");
        return 1;
    }

    private static int clear(CommandSourceStack src) {
        ServerLevel level = overworld(src);
        WeatherData d = WeatherData.get(level);
        d.rainOverride = -1F;
        d.snowOverride = -1F;
        d.thunderOverride = -1F;
        d.windOverride = -1F;
        d.sandTicks = 0;
        d.blizzardTicks = 0;
        level.setWeatherParameters(12000, 0, false, false);
        d.setDirty();
        ok(src, "clear");
        return 1;
    }

    private static int info(CommandSourceStack src) {
        ServerLevel level = overworld(src);
        WeatherData d = WeatherData.get(level);
        int dir = Math.floorMod(Math.round((float) Math.toDegrees(WeatherServer.windAngle)), 360);
        src.sendSuccess(() -> Component.translatable("realweather.cmd.info",
                pct(level.isRaining() ? WeatherServer.rain : 0F), pct(level.isRaining() ? WeatherServer.snow : 0F), pct(WeatherServer.thunder),
                pct(WeatherServer.wind), dir, pct(WeatherServer.sand), pct(WeatherServer.blizzard),
                onOff(d.flood), d.floodDepth, d.floodList.size(), onOff(d.snowPile), onOff(d.freezing), onOff(d.villagers), onOff(d.natural)), false);
        return 1;
    }

    private static int pct(float v) {
        return Math.round(v * 100F);
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "realweather.on" : "realweather.off");
    }
}
