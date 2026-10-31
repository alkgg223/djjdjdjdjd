package com.realweather.server;

import com.realweather.net.StormSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Keeps a population of storm cells around the players while it rains: showers on ordinary rainy days,
 * thunderstorms, hailstorms and supercells when it thunders. Cells drift with the winds aloft, grow and decay;
 * mature supercells can put down tornadoes from their updraft.
 */
public final class StormManager {
    private static final double RANGE = 1500;
    /** Light rain falling everywhere between the cells while it rains (0..1). */
    public static float stratiform;

    private StormManager() {}

    static void tick(ServerLevel level, WeatherData d, List<ServerPlayer> players, RandomSource rng) {
        boolean raining = level.isRaining();
        boolean thunder = level.isThundering();
        // Steering winds: storms move with the wind aloft; supercells veer to the right of it.
        float steer = 0.11F + 0.14F * WeatherServer.wind;
        Iterator<StormCell> it = d.storms.iterator();
        while (it.hasNext()) {
            StormCell c = it.next();
            c.age++;
            c.x += c.vx;
            c.z += c.vz;
            if (c.age >= c.life || farFromAll(c, players)) {
                it.remove();
                d.setDirty();
            }
        }
        if (d.rainOverride >= 0) {
            stratiform = d.rainOverride;
        } else {
            // Thunderstorms are scattered cells with mostly dry air between them; plain rain is a widespread drizzle.
            stratiform = !raining ? 0F : thunder ? 0.03F + 0.1F * WeatherServer.rain : Math.max(0.12F, WeatherServer.rain * 0.4F);
        }
        long t = level.getGameTime();
        if (t % 20 == 0 && !players.isEmpty()) {
            int wanted = !raining ? 0 : thunder ? 5 + Math.round(WeatherServer.thunder * 5) : 3 + Math.round(WeatherServer.rain * 4);
            if (d.storms.size() < wanted && rng.nextInt(3) == 0) {
                spawn(level, d, players.get(rng.nextInt(players.size())).position(), thunder, steer, rng);
            }
            supercellTornadoes(level, d, rng);
        }
        if (t % 20 == 0) {
            sync(level, d);
        }
    }

    private static boolean farFromAll(StormCell c, List<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            if (Math.abs(p.getX() - c.x) < RANGE * 1.4 && Math.abs(p.getZ() - c.z) < RANGE * 1.4) {
                return false;
            }
        }
        return true;
    }

    static StormCell spawn(ServerLevel level, WeatherData d, Vec3 near, boolean thunder, float steer, RandomSource rng) {
        float angle = WeatherServer.windAngle;
        int type;
        float roll = rng.nextFloat();
        if (thunder) {
            type = roll < 0.45F ? StormCell.STORM : roll < 0.72F ? StormCell.HAIL : StormCell.SUPERCELL;
        } else {
            type = roll < 0.8F ? StormCell.SHOWER : StormCell.STORM;
        }
        if (type == StormCell.SUPERCELL) {
            angle += 0.5F;
        }
        float speed = steer * (0.8F + rng.nextFloat() * 0.5F);
        float vx = (float) Math.sin(angle) * speed;
        float vz = (float) -Math.cos(angle) * speed;
        // Born upwind so they pass over the players.
        double dist = 300 + rng.nextDouble() * (RANGE - 300);
        double side = (rng.nextDouble() - 0.5) * RANGE;
        double x = near.x - Math.sin(angle) * dist + Math.cos(angle) * side;
        double z = near.z + Math.cos(angle) * dist + Math.sin(angle) * side;
        float radius = switch (type) {
            case StormCell.SHOWER -> 110 + rng.nextFloat() * 110;
            case StormCell.STORM -> 140 + rng.nextFloat() * 120;
            case StormCell.HAIL -> 130 + rng.nextFloat() * 90;
            default -> 230 + rng.nextFloat() * 120;
        };
        float intensity = switch (type) {
            case StormCell.SHOWER -> 0.35F + rng.nextFloat() * 0.3F;
            case StormCell.STORM -> 0.6F + rng.nextFloat() * 0.35F;
            default -> 0.75F + rng.nextFloat() * 0.25F;
        };
        float hail = type == StormCell.HAIL ? 0.5F + rng.nextFloat() * 0.5F : type == StormCell.SUPERCELL ? 0.4F + rng.nextFloat() * 0.6F : 0F;
        int life = (type == StormCell.SUPERCELL ? 14 + rng.nextInt(10) : 8 + rng.nextInt(8)) * 1200;
        StormCell c = new StormCell(d.nextStormId++, x, z, vx, vz, radius, intensity, hail, type, life, rng.nextLong());
        d.storms.add(c);
        d.setDirty();
        return c;
    }

    /** A storm made to order (commands): a hailstorm right overhead, or a supercell approaching. */
    public static StormCell spawnFor(ServerLevel level, WeatherData d, Vec3 pos, int type, float intensity, int minutes, RandomSource rng) {
        float angle = WeatherServer.windAngle + (type == StormCell.SUPERCELL ? 0.5F : 0F);
        float speed = 0.08F + 0.06F * WeatherServer.wind;
        float vx = (float) Math.sin(angle) * speed;
        float vz = (float) -Math.cos(angle) * speed;
        double back = type == StormCell.SUPERCELL ? 320 : 30;
        double x = pos.x - Math.sin(angle) * back;
        double z = pos.z + Math.cos(angle) * back;
        float radius = type == StormCell.SUPERCELL ? 300 : type == StormCell.HAIL ? 180 : 200;
        float hail = type == StormCell.HAIL ? intensity : type == StormCell.SUPERCELL ? 0.7F : 0F;
        StormCell c = new StormCell(d.nextStormId++, x, z, vx, vz, radius, Math.max(0.5F, intensity), hail, type, minutes * 1200, rng.nextLong());
        c.age = c.life / 5;
        d.storms.add(c);
        d.setDirty();
        if (!level.isThundering()) {
            level.setWeatherParameters(0, minutes * 1200 + 1200, true, true);
        }
        sync(level, d);
        return c;
    }

    /** A mature supercell over open land may put down a tornado under its updraft. */
    private static void supercellTornadoes(ServerLevel level, WeatherData d, RandomSource rng) {
        if (!d.tornadoNatural || !level.isThundering() || d.tornadoes.size() >= 2) {
            return;
        }
        for (StormCell c : d.storms) {
            if (c.type != StormCell.SUPERCELL || c.tornadoId != 0 || c.strength() < 0.9F || rng.nextInt(120) != 0) {
                continue;
            }
            double[] u = StormField.updraft(c);
            if (!level.isLoaded(BlockPos.containing(u[0], 0, u[1]))) {
                continue;
            }
            int rating = TornadoManager.naturalRating(rng);
            Tornado t = TornadoManager.spawnAt(level, d, new Vec3(u[0], 0, u[1]), rating, rng);
            t.heading = (float) Math.atan2(c.vx, -c.vz);
            t.speed = (float) Math.hypot(c.vx, c.vz);
            c.tornadoId = t.id;
            return;
        }
    }

    /** Rain rate at a point: light stratiform rain everywhere plus the storm cells, 0..1. */
    public static float rainAt(WeatherData d, double x, double z) {
        return Math.min(1F, stratiform + StormField.sumRain(d.storms, x, z));
    }

    public static float hailAt(WeatherData d, double x, double z) {
        return StormField.sumHail(d.storms, x, z);
    }

    public static StormCell nearest(WeatherData d, double x, double z) {
        StormCell best = null;
        double bd = Double.MAX_VALUE;
        for (StormCell c : d.storms) {
            double dd = Mth.square(c.x - x) + Mth.square(c.z - z);
            if (dd < bd) {
                bd = dd;
                best = c;
            }
        }
        return best;
    }

    public static void sync(ServerLevel level, WeatherData d) {
        PacketDistributor.sendToPlayersInDimension(level, payload(d));
    }

    public static StormSyncPayload payload(WeatherData d) {
        List<StormSyncPayload.Entry> list = new ArrayList<>();
        for (StormCell c : d.storms) {
            list.add(new StormSyncPayload.Entry(c.id, c.x, c.z, c.vx, c.vz, c.radius, c.intensity, c.hail, c.type, c.age, c.life, c.seed));
        }
        return new StormSyncPayload(stratiform, list);
    }

    /** Ground height near a point if loaded. */
    static int ground(ServerLevel level, double x, double z) {
        BlockPos p = BlockPos.containing(x, 0, z);
        return level.isLoaded(p) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ()) : 64;
    }
}
