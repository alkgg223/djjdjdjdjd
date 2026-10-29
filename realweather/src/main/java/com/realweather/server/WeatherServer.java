package com.realweather.server;

import com.realweather.net.WeatherSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Drives the weather in the overworld: intensities, wind, storms, lightning and the world effects. */
public final class WeatherServer {
    // Effective values of the current tick, read by the other server systems.
    public static float rain, snow, thunder, wind, windAngle, sand, blizzard;
    private static boolean wasRaining;
    private static WeatherSyncPayload lastSent;
    private static int forceSync;

    private WeatherServer() {}

    public static void requestSync() {
        forceSync = 2;
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        WeatherData d = WeatherData.get(level);
        RandomSource rng = level.random;
        long t = level.getGameTime();
        boolean raining = level.isRaining();
        boolean thundering = level.isThundering();

        if (raining && !wasRaining) {
            startRainEvent(level, d, rng);
        }
        wasRaining = raining;

        // Natural sandstorms on dry, clear days (on average about once every two in-game days).
        if (d.natural && !raining && d.sandTicks <= 0 && d.sandStrength < 0.01F && rng.nextInt(48000) == 0) {
            d.sandTarget = 0.55F + rng.nextFloat() * 0.45F;
            d.sandTicks = (4 + rng.nextInt(6)) * 1200;
            d.setDirty();
        }

        if (d.sandTicks > 0) {
            d.sandTicks--;
        }
        if (d.blizzardTicks > 0) {
            d.blizzardTicks--;
        }
        // Storms build up and die down over roughly half a minute.
        d.sandStrength = approach(d.sandStrength, d.sandTicks > 0 ? d.sandTarget : 0F, 1F / 600F);
        d.blizzardStrength = approach(d.blizzardStrength, d.blizzardTicks > 0 && raining ? d.blizzardTarget : 0F, 1F / 600F);

        float n1 = noise(t, 2400, d.noiseSeed);
        float n2 = noise(t, 700, d.noiseSeed + 17);
        float n3 = noise(t, 4000, d.noiseSeed + 31);

        float autoRain = Mth.clamp(d.eventBase + 0.2F * n1 + 0.08F * n2 + (thundering ? 0.3F : 0F), 0.08F, 1F);
        float r = d.rainOverride >= 0 ? d.rainOverride : autoRain;
        float s = d.snowOverride >= 0 ? d.snowOverride : Mth.clamp(autoRain * 0.95F + 0.05F * n2, 0.1F, 1F);
        float th = 0F;
        if (thundering) {
            th = d.thunderOverride >= 0 ? d.thunderOverride : Mth.clamp(0.45F + 0.3F * n2 + 0.2F * n1, 0.2F, 1F);
        }
        r = Math.max(r, d.blizzardStrength * 0.9F);
        s = Math.max(s, d.blizzardStrength);

        d.windAngle = (float) ((d.windAngle + n3 * 0.0006F) % (Math.PI * 2));
        float autoWind = Mth.clamp(0.12F + 0.12F * (n3 + 1F) + (raining ? 0.32F * r : 0F) + 0.3F * th, 0F, 1F);
        float w = d.windOverride >= 0 ? d.windOverride : autoWind;
        if (d.blizzardStrength > 0.02F) {
            w = Math.max(w, 0.55F + 0.45F * d.blizzardStrength);
        }
        if (d.sandStrength > 0.02F) {
            w = Math.max(w, 0.5F + 0.5F * d.sandStrength);
        }

        rain = r;
        snow = s;
        thunder = th;
        wind = w;
        windAngle = d.windAngle;
        sand = d.sandStrength;
        blizzard = d.blizzardStrength;

        if (t % 10 == 0 || forceSync > 0) {
            forceSync = Math.max(0, forceSync - 1);
            WeatherSyncPayload p = payload();
            if (forceSync > 0 || !p.equals(lastSent)) {
                PacketDistributor.sendToPlayersInDimension(level, p);
                lastSent = p;
            }
        }
        if (t % 200 == 0) {
            d.setDirty();
        }

        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }
        // (tornadoes keep their state while nobody is around)
        lightning(level, players, rng);
        TornadoManager.tick(level, d, players, rng);
        FloodManager.tick(level, d, players, rng);
        SnowPile.tick(level, d, players, rng);
        if (t % 20 == 0) {
            VillagerShelter.tick(level, d);
        }
    }

    private static void startRainEvent(ServerLevel level, WeatherData d, RandomSource rng) {
        // Every rain has its own character: drizzle, steady rain or a downpour.
        float roll = rng.nextFloat();
        d.eventBase = roll < 0.3F ? 0.18F + rng.nextFloat() * 0.15F : roll < 0.75F ? 0.4F + rng.nextFloat() * 0.2F : 0.68F + rng.nextFloat() * 0.25F;
        d.noiseSeed = rng.nextLong();
        if (d.natural && d.blizzardTicks <= 0 && rng.nextFloat() < 0.12F) {
            d.blizzardTarget = 0.6F + rng.nextFloat() * 0.4F;
            d.blizzardTicks = (4 + rng.nextInt(5)) * 1200;
        }
        d.setDirty();
    }

    public static WeatherSyncPayload payload() {
        return new WeatherSyncPayload(rain, snow, thunder, windAngle, wind, sand, blizzard);
    }

    private static void lightning(ServerLevel level, List<ServerPlayer> players, RandomSource rng) {
        if (!level.isThundering() || thunder <= 0.01F) {
            return;
        }
        float chance = 0.002F + thunder * thunder * 0.03F;
        if (rng.nextFloat() >= chance) {
            return;
        }
        ServerPlayer player = players.get(rng.nextInt(players.size()));
        double dist = 24 + 150 * Math.sqrt(rng.nextDouble());
        double ang = rng.nextDouble() * Math.PI * 2;
        int x = Mth.floor(player.getX() + Math.cos(ang) * dist);
        int z = Mth.floor(player.getZ() + Math.sin(ang) * dist);
        // Lightning prefers the highest point around: trees, towers, hill tops.
        BlockPos best = null;
        for (int i = 0; i < 5; i++) {
            BlockPos col = new BlockPos(x + rng.nextInt(9) - 4, 0, z + rng.nextInt(9) - 4);
            if (!level.isLoaded(col)) {
                continue;
            }
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, col);
            if (best == null || top.getY() > best.getY()) {
                best = top;
            }
        }
        if (best == null || !level.isRainingAt(best)) {
            return;
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }
        bolt.moveTo(Vec3.atBottomCenterOf(best));
        boolean real = rng.nextFloat() < (dist < 64 ? 0.3F : 0.12F);
        bolt.setVisualOnly(!real);
        level.addFreshEntity(bolt);
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD || blizzard < 0.25F || player.isCreative() || player.isSpectator() || !player.canFreeze()) {
            return;
        }
        WeatherData d = WeatherData.get(level);
        if (!d.freezing) {
            return;
        }
        BlockPos head = BlockPos.containing(player.getEyePosition());
        if (!level.canSeeSky(head)) {
            return;
        }
        Holder<Biome> biome = level.getBiome(head);
        if (biome.value().getPrecipitationAt(head) != Biome.Precipitation.SNOW) {
            return;
        }
        // Exposed to a blizzard without leather clothes: the cold slowly gets through (frost on screen, slower walking).
        int cap = (int) (player.getTicksRequiredToFreeze() * Math.min(1F, (blizzard - 0.2F) * 1.4F));
        int cur = player.getTicksFrozen();
        if (cur < cap) {
            player.setTicksFrozen(Math.min(cap, cur + 3));
        }
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, payload());
            PacketDistributor.sendToPlayer(player, TornadoManager.payload(WeatherData.get(player.server.overworld())));
        }
    }

    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, payload());
            PacketDistributor.sendToPlayer(player, TornadoManager.payload(WeatherData.get(player.server.overworld())));
        }
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, payload());
        }
    }

    public static boolean isDryBiome(Biome biome) {
        return !biome.hasPrecipitation() && biome.getBaseTemperature() >= 1.0F;
    }

    static float approach(float cur, float target, float step) {
        return cur < target ? Math.min(target, cur + step) : Math.max(target, cur - step);
    }

    /** Smooth value noise in [-1, 1] that changes over about `period` ticks. */
    static float noise(long t, int period, long seed) {
        long i = Math.floorDiv(t, period);
        float f = (float) (t - i * period) / period;
        float a = hash(i, seed);
        float b = hash(i + 1, seed);
        float s = f * f * (3 - 2 * f);
        return a + (b - a) * s;
    }

    private static float hash(long i, long seed) {
        long h = i * 0x9E3779B97F4A7C15L ^ seed;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return ((h >>> 40) / (float) (1L << 24)) * 2F - 1F;
    }
}
