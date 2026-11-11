package com.rpbusiness.core;

import com.rpbusiness.RPBusiness;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> REG = DeferredRegister.create(Registries.SOUND_EVENT, RPBusiness.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> CHOP = reg("chop");
    public static final DeferredHolder<SoundEvent, SoundEvent> SIZZLE = reg("sizzle");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUBBLE = reg("bubble");
    public static final DeferredHolder<SoundEvent, SoundEvent> POUR = reg("pour");
    public static final DeferredHolder<SoundEvent, SoundEvent> ORDER_BELL = reg("order_bell");
    public static final DeferredHolder<SoundEvent, SoundEvent> CASH_REGISTER = reg("cash_register");
    public static final DeferredHolder<SoundEvent, SoundEvent> CARD_BEEP = reg("card_beep");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLEAN = reg("clean");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLACE_FOOD = reg("place_food");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRIDGE_OPEN = reg("fridge_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRIDGE_CLOSE = reg("fridge_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> EAT = reg("eat");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAPPY = reg("happy");
    public static final DeferredHolder<SoundEvent, SoundEvent> UNHAPPY = reg("unhappy");

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
        return REG.register(name, () -> SoundEvent.createVariableRangeEvent(RPBusiness.id(name)));
    }

    private ModSounds() {}
}
