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
    public static final DeferredHolder<Fluid, FloodFluid.Flowing> FLOOD_FLOWING = FLUIDS.register("flowing_flood_water",
            () -> new FloodFluid.Flowing(floodProperties()));
    public static final DeferredBlock<LiquidBlock> FLOOD_BLOCK = BLOCKS.register("flood_water",
            () -> new LiquidBlock(FLOOD.get(), BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).noLootTable()));

    public static final DeferredBlock<com.realweather.server.CoverBlock> HAIL_COVER = BLOCKS.register("hail",
            () -> new com.realweather.server.CoverBlock(BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                    .replaceable().noCollission().strength(0.1F).sound(net.minecraft.world.level.block.SoundType.POWDER_SNOW).noOcclusion()
                    .randomTicks().noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY), true));
    public static final DeferredBlock<com.realweather.server.CoverBlock> HAIL_SNOW = BLOCKS.register("hail_snow",
            () -> new com.realweather.server.CoverBlock(BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                    .replaceable().forceSolidOff().strength(0.1F).sound(net.minecraft.world.level.block.SoundType.SNOW).randomTicks()
                    .noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                    .isViewBlocking((s, l, p) -> s.getValue(com.realweather.server.CoverBlock.LAYERS) >= 8), false));

    public static final DeferredHolder<SoundEvent, SoundEvent> RAIN_LIGHT = sound("weather.rain_light");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAIN_HEAVY = sound("weather.rain_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAIN_ROOF = sound("weather.rain_roof");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND = sound("weather.wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLIZZARD = sound("weather.blizzard");
    public static final DeferredHolder<SoundEvent, SoundEvent> SANDSTORM = sound("weather.sandstorm");
    public static final DeferredHolder<SoundEvent, SoundEvent> THUNDER_CLOSE = sound("weather.thunder_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> THUNDER_MID = sound("weather.thunder_mid");
    public static final DeferredHolder<SoundEvent, SoundEvent> THUNDER_FAR = sound("weather.thunder_far");
    public static final DeferredHolder<SoundEvent, SoundEvent> THUNDER_STRIKE = sound("weather.thunder_strike");
    public static final DeferredHolder<SoundEvent, SoundEvent> TORNADO_ROAR = sound("weather.tornado_roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> TORNADO_DEBRIS = sound("weather.tornado_debris");
    public static final DeferredHolder<SoundEvent, SoundEvent> TORNADO_FAR = sound("weather.tornado_far");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAIL = sound("weather.hail");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAIL_ROOF = sound("weather.hail_roof");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEHICLE_ENGINE = sound("vehicle.engine");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEHICLE_DIESEL = sound("vehicle.diesel");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEHICLE_START = sound("vehicle.start");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEHICLE_HORN = sound("vehicle.horn");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEHICLE_HORN_TRUCK = sound("vehicle.horn_truck");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEHICLE_WIPER = sound("vehicle.wiper");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEHICLE_HYDRAULIC = sound("vehicle.hydraulic");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEHICLE_SKID = sound("vehicle.skid");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEHICLE_CRASH = sound("vehicle.crash");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEHICLE_DOOR = sound("vehicle.door");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEHICLE_BEEP = sound("vehicle.beep");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEHICLE_WIND_BUFFET = sound("vehicle.wind_buffet");

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPLASH = particle("splash");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RIPPLE = particle("ripple");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RAIN_DROP = particle("rain_drop");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SNOW_FLAKE = particle("snow_flake");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLIZZARD_FLAKE = particle("blizzard_flake");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DUST = particle("dust");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SAND_GRAIN = particle("sand_grain");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TORNADO_DUST = particle("tornado_dust");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HAILSTONE = particle("hailstone");

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
