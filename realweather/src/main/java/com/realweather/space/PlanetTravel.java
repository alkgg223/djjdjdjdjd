package com.realweather.space;

import com.realweather.RealWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.Registries;

/**
 * Going to another world and coming home: to its surface (on solid ground, where on its map you chose) or into orbit
 * high above it (falling slowly down through its sky), with a planet map in your pack to come back with.
 */
public final class PlanetTravel {
    public static final double ORBIT_Y = 900;
    private static final String HOME = "realweather_home";

    private PlanetTravel() {}

    /** Takes a player to a world: `orbit` high above, else to its ground, at a place on its map (radians) or a default. */
    public static boolean travel(ServerPlayer p, String id, boolean orbit, double lon, double lat) {
        Worlds.World w = Worlds.byId(id);
        ServerLevel target = p.server.getLevel(Worlds.DIMENSION);
        if (w == null || target == null) {
            return false;
        }
        if (p.level().dimension() != Worlds.DIMENSION) {
            // Remember where home is.
            CompoundTag t = new CompoundTag();
            t.putString("dim", p.level().dimension().location().toString());
            t.putDouble("x", p.getX());
            t.putDouble("y", p.getY());
            t.putDouble("z", p.getZ());
            p.getPersistentData().put(HOME, t);
        }
        double[] col = Double.isNaN(lon) ? new double[]{Worlds.centre(w), 0} : Worlds.column(w, lon, lat);
        int x = (int) Math.floor(col[0]), z = (int) Math.floor(col[1]);
        // On a world with seas, walk along until there is land.
        if (!orbit && w.ocean()) {
            for (int i = 0; i < 400; i++) {
                if (PlanetTerrain.height(w, x, z) >= PlanetTerrain.SEA_LEVEL) {
                    break;
                }
                x += 37;
                z += (i % 2 == 0 ? 23 : -11);
            }
        }
        double y = orbit ? ORBIT_Y : PlanetTerrain.height(w, x, z) + 1.05;
        target.getChunk(x >> 4, z >> 4);
        p.teleportTo(target, x + 0.5, y, z + 0.5, p.getYRot(), orbit ? 60F : p.getXRot());
        if (orbit) {
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, true, false));
        }
        // A planet map to come home with.
        boolean has = false;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(com.realweather.device.ModDevices.PLANET_MAP_ITEM.get())) {
                has = true;
                break;
            }
        }
        if (!has) {
            p.getInventory().add(new ItemStack(com.realweather.device.ModDevices.PLANET_MAP_ITEM.get()));
        }
        p.displayClientMessage(Component.translatable(orbit ? "realweather.travel.orbit" : "realweather.travel.arrived",
                Component.translatable("realweather.target." + id)), true);
        return true;
    }

    /** Back to where the player left from (or the world's spawn). */
    public static void home(ServerPlayer p) {
        CompoundTag t = p.getPersistentData().getCompound(HOME);
        ServerLevel level = null;
        double x, y, z;
        if (t.contains("dim")) {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("dim")));
            level = p.server.getLevel(key);
        }
        if (level == null || level.dimension() == Worlds.DIMENSION) {
            level = p.server.overworld();
            BlockPos s = level.getSharedSpawnPos();
            x = s.getX() + 0.5;
            z = s.getZ() + 0.5;
            y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, s.getX(), s.getZ()) + 0.1;
        } else {
            x = t.getDouble("x");
            y = t.getDouble("y");
            z = t.getDouble("z");
        }
        p.teleportTo(level, x, y, z, p.getYRot(), p.getXRot());
        p.displayClientMessage(Component.translatable("realweather.travel.home"), true);
    }

    /** High above a world you drift down slowly (so arriving in orbit isn't the end of you). */
    public static void tick(ServerPlayer p) {
        if (p.level().dimension() != Worlds.DIMENSION || p.onGround() || p.getAbilities().flying) {
            return;
        }
        if (p.getY() > 300 && (p.getEffect(MobEffects.SLOW_FALLING) == null || p.getEffect(MobEffects.SLOW_FALLING).getDuration() < 40)) {
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, true, false));
        }
    }

    static {
        RealWeather.LOG.debug("{} worlds to travel to", Worlds.ALL.size());
    }
}
