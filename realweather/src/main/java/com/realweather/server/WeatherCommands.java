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
        root.then(tornado());
        root.then(tsunami());
        root.then(Commands.literal("hailstorm").then(Commands.argument("percent", IntegerArgumentType.integer(1, 100))
                .executes(c -> storm(c.getSource(), StormCell.HAIL, IntegerArgumentType.getInteger(c, "percent"), 8))
                .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 120))
                        .executes(c -> storm(c.getSource(), StormCell.HAIL, IntegerArgumentType.getInteger(c, "percent"), IntegerArgumentType.getInteger(c, "minutes"))))));
        root.then(Commands.literal("supercell").executes(c -> storm(c.getSource(), StormCell.SUPERCELL, 100, 20))
                .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 120))
                        .executes(c -> storm(c.getSource(), StormCell.SUPERCELL, 100, IntegerArgumentType.getInteger(c, "minutes")))));
        root.then(Commands.literal("thunderstorm").executes(c -> storm(c.getSource(), StormCell.STORM, 90, 12)));
        root.then(onOff("snowpile"));
        root.then(onOff("hail"));
        root.then(onOff("freezing"));
        root.then(onOff("villagers"));
        root.then(onOff("natural"));
        root.then(Commands.literal("auto").executes(c -> auto(c.getSource())));
        root.then(Commands.literal("clear").executes(c -> clear(c.getSource())));
        root.then(Commands.literal("info").executes(c -> info(c.getSource())));

        var node = dispatcher.register(root);
        dispatcher.register(Commands.literal("rw").requires(s -> s.hasPermission(2)).redirect(node));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tornado() {
        LiteralArgumentBuilder<CommandSourceStack> t = Commands.literal("tornado");
        // /rw tornado 3, /rw tornado f3 wedge, and "here" to spawn it right where you stand.
        var num = Commands.argument("rating", IntegerArgumentType.integer(0, 5))
                .executes(c -> spawnTornado(c.getSource(), IntegerArgumentType.getInteger(c, "rating"), null, false))
                .then(Commands.literal("here").executes(c -> spawnTornado(c.getSource(), IntegerArgumentType.getInteger(c, "rating"), null, true)));
        for (TornadoShape shape : TornadoShape.values()) {
            num.then(Commands.literal(shape.id())
                    .executes(c -> spawnTornado(c.getSource(), IntegerArgumentType.getInteger(c, "rating"), shape, false))
                    .then(Commands.literal("here").executes(c -> spawnTornado(c.getSource(), IntegerArgumentType.getInteger(c, "rating"), shape, true))));
        }
        t.then(num);
        // /rw tornado custom <shape> <width>: any shape, 1 to 100 blocks wide.
        var custom = Commands.literal("custom");
        for (TornadoShape shape : TornadoShape.values()) {
            custom.then(Commands.literal(shape.id()).then(Commands.argument("width", IntegerArgumentType.integer(1, 100))
                    .executes(c -> customTornado(c.getSource(), shape, IntegerArgumentType.getInteger(c, "width"), false))
                    .then(Commands.literal("here").executes(c -> customTornado(c.getSource(), shape, IntegerArgumentType.getInteger(c, "width"), true)))));
        }
        t.then(custom);
        for (int i = 0; i <= 5; i++) {
            int rating = i;
            var lit = Commands.literal("f" + i).executes(c -> spawnTornado(c.getSource(), rating, null, false))
                    .then(Commands.literal("here").executes(c -> spawnTornado(c.getSource(), rating, null, true)));
            for (TornadoShape shape : TornadoShape.values()) {
                lit.then(Commands.literal(shape.id()).executes(c -> spawnTornado(c.getSource(), rating, shape, false))
                        .then(Commands.literal("here").executes(c -> spawnTornado(c.getSource(), rating, shape, true))));
            }
            t.then(lit);
        }
        t.then(Commands.literal("stop").executes(c -> {
            WeatherData d = WeatherData.get(overworld(c.getSource()));
            d.tornadoes.forEach(Tornado::dissipate);
            d.setDirty();
            ok(c.getSource(), "tornado.stop");
            return 1;
        }));
        t.then(Commands.literal("damage")
                .then(Commands.literal("on").executes(c -> toggle(c, "tornado.damage", true)))
                .then(Commands.literal("off").executes(c -> toggle(c, "tornado.damage", false))));
        t.then(Commands.literal("natural")
                .then(Commands.literal("on").executes(c -> toggle(c, "tornado.natural", true)))
                .then(Commands.literal("off").executes(c -> toggle(c, "tornado.natural", false))));
        return t;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tsunami() {
        // /rw tsunami 300, /rw tsunami 800 west (the side it comes from), /rw tsunami stop, natural on|off
        var t = Commands.literal("tsunami");
        var strength = Commands.argument("strength", IntegerArgumentType.integer(1, 1000))
                .executes(c -> tsunami(c.getSource(), IntegerArgumentType.getInteger(c, "strength"), Float.NaN));
        String[] names = {"north", "east", "south", "west"};
        for (int i = 0; i < 4; i++) {
            float bearing = i * 90F;
            strength.then(Commands.literal(names[i]).executes(c -> tsunami(c.getSource(), IntegerArgumentType.getInteger(c, "strength"), bearing)));
        }
        t.then(strength);
        t.then(Commands.literal("stop").executes(c -> {
            TsunamiManager.stop(overworld(c.getSource()));
            ok(c.getSource(), "tsunami.stop");
            return 1;
        }));
        t.then(Commands.literal("natural")
                .then(Commands.literal("on").executes(c -> toggle(c, "tsunami.natural", true)))
                .then(Commands.literal("off").executes(c -> toggle(c, "tsunami.natural", false))));
        t.then(Commands.literal("water")
                .then(Commands.literal("on").executes(c -> toggle(c, "tsunami.water", true)))
                .then(Commands.literal("off").executes(c -> toggle(c, "tsunami.water", false))));
        return t;
    }

    private static int tsunami(CommandSourceStack src, int strength, float bearing) {
        ServerLevel level = overworld(src);
        if (TsunamiManager.WAVES.size() >= 3) {
            src.sendFailure(Component.translatable("realweather.cmd.tsunami.too_many"));
            return 0;
        }
        net.minecraft.world.phys.Vec3 pos = src.getLevel() == level ? src.getPosition() : level.getSharedSpawnPos().getCenter();
        Tsunami w = TsunamiManager.spawn(level, pos, strength, bearing, level.random);
        int dist = (int) Math.round(w.along(pos.x, pos.z));
        ok(src, "tsunami", strength, Math.round(Tsunami.heightFor(strength)), dist);
        return 1;
    }

    private static int storm(CommandSourceStack src, int type, int percent, int minutes) {
        ServerLevel level = overworld(src);
        WeatherData d = WeatherData.get(level);
        net.minecraft.world.phys.Vec3 pos = src.getLevel() == level ? src.getPosition() : level.getSharedSpawnPos().getCenter();
        StormManager.spawnFor(level, d, pos, type, percent / 100F, minutes, level.random);
        ok(src, "storm." + type, percent, minutes);
        return 1;
    }

    private static int customTornado(CommandSourceStack src, TornadoShape shape, int width, boolean here) {
        ServerLevel level = overworld(src);
        WeatherData d = WeatherData.get(level);
        if (d.tornadoes.size() >= 4) {
            src.sendFailure(Component.translatable("realweather.cmd.tornado.too_many"));
            return 0;
        }
        net.minecraft.world.phys.Vec3 pos = src.getLevel() == level ? src.getPosition() : level.getSharedSpawnPos().getCenter();
        Tornado t = TornadoManager.spawnCustom(level, d, pos, shape, width, here, level.random);
        int dist = (int) Math.sqrt((t.x - pos.x) * (t.x - pos.x) + (t.z - pos.z) * (t.z - pos.z));
        ok(src, "tornado.custom", Component.translatable("realweather.shape." + shape.id()), width + "x" + width, "F" + t.rating, dist);
        return 1;
    }

    private static int spawnTornado(CommandSourceStack src, int rating, TornadoShape shape, boolean here) {
        ServerLevel level = overworld(src);
        WeatherData d = WeatherData.get(level);
        if (d.tornadoes.size() >= 4) {
            src.sendFailure(Component.translatable("realweather.cmd.tornado.too_many"));
            return 0;
        }
        net.minecraft.world.phys.Vec3 pos = src.getLevel() == level ? src.getPosition() : level.getSharedSpawnPos().getCenter();
        Tornado t = here ? TornadoManager.spawnAt(level, d, pos, rating, shape, level.random)
                : TornadoManager.spawnApproaching(level, d, pos, rating, shape, 70 + level.random.nextInt(30), level.random);
        int dist = (int) Math.sqrt((t.x - pos.x) * (t.x - pos.x) + (t.z - pos.z) * (t.z - pos.z));
        ok(src, "tornado", Component.literal("F" + rating + " ").append(Component.translatable("realweather.shape." + t.shape.id())), dist);
        return 1;
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
            d.force = 0;
            d.forceTicks = 0;
            ok(src, "rain.stop");
        } else {
            d.rainOverride = percent / 100F;
            // Rain wherever you are, even in a desert or on snowy ground.
            d.force = Precip.RAIN;
            d.forceTicks = minutes * 1200;
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
            d.force = 0;
            d.forceTicks = 0;
            ok(src, "snow.stop");
        } else {
            d.snowOverride = percent / 100F;
            // Snow everywhere, not only in the cold biomes.
            d.force = Precip.SNOW;
            d.forceTicks = minutes * 1200;
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
            d.force = 0;
            d.forceTicks = 0;
            ok(src, "sandstorm.stop");
        } else {
            d.sandTarget = percent / 100F;
            d.sandTicks = minutes * 1200;
            // A sandstorm wherever you are.
            d.force = Precip.SAND;
            d.forceTicks = minutes * 1200;
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
            d.force = 0;
            d.forceTicks = 0;
            ok(src, "blizzard.stop");
        } else {
            d.blizzardTarget = percent / 100F;
            d.blizzardTicks = minutes * 1200;
            d.force = Precip.SNOW;
            d.forceTicks = minutes * 1200;
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
            case "tornado.damage" -> d.tornadoDamage = on;
            case "hail" -> d.hail = on;
            case "tornado.natural" -> d.tornadoNatural = on;
            case "tsunami.natural" -> d.tsunamiNatural = on;
            case "tsunami.water" -> d.tsunamiFlood = on;
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
        d.force = 0;
        d.forceTicks = 0;
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
        d.force = 0;
        d.forceTicks = 0;
        d.tornadoes.forEach(Tornado::dissipate);
        d.storms.clear();
        StormManager.sync(level, d);
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
