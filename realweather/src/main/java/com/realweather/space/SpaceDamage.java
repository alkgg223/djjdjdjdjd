package com.realweather.space;

import com.realweather.RealWeather;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/** The ways space kills: a star's fire, a black hole, a pulsar's radiation, a gas giant's core. */
public final class SpaceDamage {
    public static final ResourceKey<DamageType> STAR = key("star"), BLACK_HOLE = key("black_hole"), PULSAR = key("pulsar"),
            CORE = key("planet_core");

    private SpaceDamage() {}

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, RealWeather.id(name));
    }

    public static DamageSource source(ServerLevel level, ResourceKey<DamageType> key) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key));
    }
}
