package com.vehicleworks.registry;

import com.vehicleworks.VehicleWorks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, VehicleWorks.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> PISTON_LOOP = reg("engine.piston");
    public static final DeferredHolder<SoundEvent, SoundEvent> TURBINE_LOOP = reg("engine.turbine");
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_LOOP = reg("engine.jet");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROTOR_LOOP = reg("engine.rotor");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELECTRIC_LOOP = reg("engine.electric");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_START = reg("engine.start");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND_LOOP = reg("vehicle.wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> STALL_WARNING = reg("vehicle.stall_warning");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEAR_MOTOR = reg("vehicle.gear");
    public static final DeferredHolder<SoundEvent, SoundEvent> ASSEMBLE = reg("vehicle.assemble");
    public static final DeferredHolder<SoundEvent, SoundEvent> MG_FIRE = reg("weapon.machine_gun");
    public static final DeferredHolder<SoundEvent, SoundEvent> CANNON_FIRE = reg("weapon.cannon");
    public static final DeferredHolder<SoundEvent, SoundEvent> MISSILE_LAUNCH = reg("weapon.missile");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOMB_RELEASE = reg("weapon.bomb");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELOAD = reg("weapon.reload");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARM = reg("weapon.arm");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLICK = reg("ui.click");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUMBLE = reg("vehicle.rumble");
    public static final DeferredHolder<SoundEvent, SoundEvent> TOUCHDOWN = reg("vehicle.touchdown");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIRE_SCREECH = reg("vehicle.tire_screech");
    public static final DeferredHolder<SoundEvent, SoundEvent> HARD_LANDING = reg("vehicle.hard_landing");
    public static final DeferredHolder<SoundEvent, SoundEvent> ALARM_STALL = reg("alarm.stall");
    public static final DeferredHolder<SoundEvent, SoundEvent> ALARM_OVERSPEED = reg("alarm.overspeed");
    public static final DeferredHolder<SoundEvent, SoundEvent> ALARM_CAUTION = reg("alarm.caution");
    public static final DeferredHolder<SoundEvent, SoundEvent> ALARM_FIRE = reg("alarm.fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> ALARM_WHOOP = reg("alarm.whoop");
    public static final DeferredHolder<SoundEvent, SoundEvent> ALARM_WARNING = reg("alarm.warning");

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(VehicleWorks.MODID, name)));
    }

    private ModSounds() {}
}
