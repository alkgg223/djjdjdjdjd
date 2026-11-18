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
    public static final DeferredHolder<SoundEvent, SoundEvent> TURBOPROP_LOOP = reg("engine.turboprop");
    public static final DeferredHolder<SoundEvent, SoundEvent> TURBOFAN_LOOP = reg("engine.turbofan");
    public static final DeferredHolder<SoundEvent, SoundEvent> HELI_TURBINE_LOOP = reg("engine.heli_turbine");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_ROUGH = reg("engine.rough");
    public static final DeferredHolder<SoundEvent, SoundEvent> LIGHT_OFF = reg("engine.light_off");
    public static final DeferredHolder<SoundEvent, SoundEvent> PISTON_CATCH = reg("engine.piston_catch");
    public static final DeferredHolder<SoundEvent, SoundEvent> STARTER_PISTON = reg("engine.starter_piston");
    public static final DeferredHolder<SoundEvent, SoundEvent> STARTER_TURBINE = reg("engine.starter_turbine");
    public static final DeferredHolder<SoundEvent, SoundEvent> IGNITION = reg("engine.ignition");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPOOL_DOWN = reg("engine.spool_down");
    public static final DeferredHolder<SoundEvent, SoundEvent> TOGGLE = reg("cockpit.toggle");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELAY = reg("cockpit.relay");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUTTON = reg("cockpit.button");
    public static final DeferredHolder<SoundEvent, SoundEvent> KNOB = reg("cockpit.knob");
    public static final DeferredHolder<SoundEvent, SoundEvent> GUARD = reg("cockpit.guard");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVER = reg("cockpit.lever");
    public static final DeferredHolder<SoundEvent, SoundEvent> AVIONICS = reg("cockpit.avionics");
    public static final DeferredHolder<SoundEvent, SoundEvent> AP_DISCONNECT = reg("cockpit.ap_disconnect");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEAR_HORN = reg("cockpit.gear_horn");
    public static final DeferredHolder<SoundEvent, SoundEvent> HYDRAULIC = reg("cockpit.hydraulic");
    public static final DeferredHolder<SoundEvent, SoundEvent> COCKPIT_AMBIENT = reg("cockpit.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> CABIN_DOOR = reg("cabin.door");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEAT_MOTOR = reg("cabin.seat_motor");
    public static final DeferredHolder<SoundEvent, SoundEvent> TRAY = reg("cabin.tray");
    public static final DeferredHolder<SoundEvent, SoundEvent> IFE_CHIME = reg("cabin.ife");
    public static final DeferredHolder<SoundEvent, SoundEvent> AIRFLOW = reg("vehicle.airflow");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEAR_EXTEND = reg("vehicle.gear_extend");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEAR_RETRACT = reg("vehicle.gear_retract");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLAPS = reg("vehicle.flaps");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPOILER = reg("vehicle.spoiler");
    public static final DeferredHolder<SoundEvent, SoundEvent> BRAKE = reg("vehicle.brake");

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(VehicleWorks.MODID, name)));
    }

    private ModSounds() {}
}
