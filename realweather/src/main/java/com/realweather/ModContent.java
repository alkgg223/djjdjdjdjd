package com.realweather;

import com.realweather.server.FloodFluid;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModContent {
    private static final String MODID = RealWeather.MODID;

    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, MODID);

    // Flood water: muddy rain water that collects in low ground during heavy rain and drains afterwards.
    public static final DeferredHolder<FluidType, FluidType> FLOOD_TYPE = FLUID_TYPES.register("flood_water", () -> new FluidType(
            FluidType.Properties.create()
                    .descriptionId("block.realweather.flood_water")
                    .canSwim(true).canDrown(true).supportsBoating(true).canExtinguish(true).canHydrate(true).canPushEntity(true)
                    .fallDistanceModifier(0F).motionScale(0.014D).density(1000).viscosity(1200).temperature(285)
                    .pathType(PathType.WATER).adjacentPathType(null)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
                    .sound(SoundActions.FLUID_VAPORIZE, SoundEvents.FIRE_EXTINGUISH)));

    public static final DeferredHolder<Fluid, FloodFluid> FLOOD = FLUIDS.register("flood_water", () -> new FloodFluid(floodProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOOD_FLOWING = FLUIDS.register("flowing_flood_water",
            () -> new BaseFlowingFluid.Flowing(floodProperties()));
    public static final DeferredBlock<LiquidBlock> FLOOD_BLOCK = BLOCKS.register("flood_water",
            () -> new LiquidBlock(FLOOD.get(), BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).noLootTable()));

    public static final DeferredHolder<SoundEvent, SoundEvent> RAIN_LIGHT = sound("weather.rain_light");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAIN_HEAVY = sound("weather.rain_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAIN_ROOF = sound("weather.rain_roof");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND = sound("weather.wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLIZZARD = sound("weather.blizzard");
    public static final DeferredHolder<SoundEvent, SoundEvent> SANDSTORM = sound("weather.sandstorm");
    public static final DeferredHolder<SoundEvent, SoundEvent> THUNDER_CLOSE = sound("weather.thunder_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> THUNDER_MID = sound("weather.thunder_mid");
    public static final DeferredHolder<SoundEvent, SoundEvent> THUNDER_FAR = sound("weather.thunder_far");

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPLASH = particle("splash");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RIPPLE = particle("ripple");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RAIN_DROP = particle("rain_drop");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SNOW_FLAKE = particle("snow_flake");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLIZZARD_FLAKE = particle("blizzard_flake");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DUST = particle("dust");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SAND_GRAIN = particle("sand_grain");

    private ModContent() {}

    private static BaseFlowingFluid.Properties floodProperties() {
        return new BaseFlowingFluid.Properties(FLOOD_TYPE, FLOOD, FLOOD_FLOWING)
                .block(FLOOD_BLOCK).slopeFindDistance(1).levelDecreasePerBlock(4).tickRate(5).explosionResistance(100F);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(RealWeather.id(name)));
    }

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> particle(String name) {
        return PARTICLES.register(name, () -> new SimpleParticleType(false));
    }

    public static void register(IEventBus bus) {
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
        BLOCKS.register(bus);
        SOUNDS.register(bus);
        PARTICLES.register(bus);
    }
}
