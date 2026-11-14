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
        saveHome(p);
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
        // (A gas giant has no ground: you arrive at its cloud tops, and sink.)
        double y = orbit ? ORBIT_Y : w.gas() ? GAS_TOP : PlanetTerrain.height(w, x, z) + 1.05;
        target.getChunk(x >> 4, z >> 4);
        p.teleportTo(target, x + 0.5, y, z + 0.5, p.getYRot(), orbit ? 60F : p.getXRot());
        if (orbit) {
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, true, false));
        }
        giveMap(p);
        p.displayClientMessage(Component.translatable(w.gas() && !orbit ? "realweather.travel.gas" : orbit ? "realweather.travel.orbit"
                : "realweather.travel.arrived", Component.translatable("realweather.target." + id)), true);
        return true;
    }

    /** Where to come home to, if leaving from home. */
    private static void saveHome(ServerPlayer p) {
        if (p.level().dimension() != Worlds.DIMENSION) {
            CompoundTag t = new CompoundTag();
            t.putString("dim", p.level().dimension().location().toString());
            t.putDouble("x", p.getX());
            t.putDouble("y", p.getY());
            t.putDouble("z", p.getZ());
            p.getPersistentData().put(HOME, t);
        }
    }

    /** A planet map to come home with. */
    private static void giveMap(ServerPlayer p) {
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
    }

    /** Out into deep space beside a star, pulsar, black hole, nebula or comet (facing it). */
    public static boolean toSite(ServerPlayer p, String id) {
        ServerLevel target = p.server.getLevel(Worlds.DIMENSION);
        if (!Sites.IDS.contains(id) || target == null) {
            return false;
        }
        saveHome(p);
        double x = Sites.centre(id) + 0.5;
        target.getChunk((int) Math.floor(x) >> 4, 0);
        p.teleportTo(target, x, Sites.FLOOR, 0.5, 180F, 0F);
        giveMap(p);
        p.displayClientMessage(Component.translatable("realweather.travel.site", Component.translatable("realweather.target." + id)), true);
        return true;
    }

    /** Off at the speed of light: out into the space between the worlds of the solar system. */
    public static void launch(ServerPlayer p) {
        ServerLevel target = p.server.getLevel(Worlds.DIMENSION);
        if (target == null) {
            return;
        }
        saveHome(p);
        double x = Sites.centre(Sites.FLIGHT) + 0.5;
        target.getChunk((int) Math.floor(x) >> 4, 0);
        p.teleportTo(target, x, Sites.FLOOR, 0.5, p.getYRot(), p.getXRot());
    }

    /** Something out in space that can't be survived: a star's fire, a black hole's horizon, a pulsar's radiation. */
    public static void hazard(ServerPlayer p, int kind) {
        if (p.level().dimension() != Worlds.DIMENSION || !Sites.isSpace(p.getX()) || p.isCreative() || p.isSpectator()) {
            return;
        }
        var key = switch (kind) {
            case 1 -> SpaceDamage.BLACK_HOLE;
            case 2 -> SpaceDamage.PULSAR;
            default -> SpaceDamage.STAR;
        };
        p.hurt(SpaceDamage.source(p.serverLevel(), key), Float.MAX_VALUE);
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

    /**
     * High above a world you drift down slowly (so arriving in orbit isn't the end of you); out in space you stay on
     * your platform; in a gas giant there is nothing to stand on, and you sink towards its hot core, which kills you.
     */
    public static void tick(ServerPlayer p) {
        if (p.level().dimension() != Worlds.DIMENSION) {
            return;
        }
        Worlds.World w = Worlds.at(p.getX());
        if (w == null) {
            p.resetFallDistance();
            if (p.getY() < Sites.FLOOR - 6 && !p.getAbilities().flying) {
                p.teleportTo(p.serverLevel(), Math.round(p.getX() / (double) Worlds.REGION) * (double) Worlds.REGION + 0.5, Sites.FLOOR, 0.5,
                        p.getYRot(), p.getXRot());
            }
            return;
        }
        if (w.gas() && !p.isCreative() && !p.isSpectator()) {
            sink(p, w);
        }
        if (p.onGround() || p.getAbilities().flying) {
            return;
        }
        if (p.getY() > 300 && (p.getEffect(MobEffects.SLOW_FALLING) == null || p.getEffect(MobEffects.SLOW_FALLING).getDuration() < 40)) {
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, true, false));
        }
    }

    /** Sinking through a gas giant: deeper, hotter and crushing; near the core, death. */
    private static void sink(ServerPlayer p, Worlds.World w) {
        double y = p.getY();
        if (y < GAS_TOP - 20 && p.getDeltaMovement().y < -0.55) {
            p.setDeltaMovement(p.getDeltaMovement().x, -0.55, p.getDeltaMovement().z);
            p.hurtMarked = true;
        }
        if (p.tickCount % 20 != 0) {
            return;
        }
        String name = "realweather.target." + w.id();
        if (y < 40) {
            p.hurt(SpaceDamage.source(p.serverLevel(), SpaceDamage.CORE), Float.MAX_VALUE);
            return;
        }
        if (y < 230) {
            p.hurt(SpaceDamage.source(p.serverLevel(), SpaceDamage.CORE), (float) ((230 - y) / 45.0));
            if (y < 140) {
                p.setRemainingFireTicks(60);
            }
            p.displayClientMessage(Component.translatable("realweather.gas.heat", Component.translatable(name)), true);
        } else if (y < GAS_TOP - 10) {
            p.displayClientMessage(Component.translatable("realweather.gas.sinking", Component.translatable(name)), true);
        }
    }

    /** The tops of a gas giant's clouds (where you arrive). */
    public static final double GAS_TOP = 310;

    static {
        RealWeather.LOG.debug("{} worlds to travel to", Worlds.ALL.size());
    }
}
