package com.arkragnarok.registry;

import com.arkragnarok.ArkRagnarok;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ArkRagnarok.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> REX_ROAR = reg("rex.roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> REX_GROWL = reg("rex.growl");
    public static final DeferredHolder<SoundEvent, SoundEvent> REX_HURT = reg("rex.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> REX_DEATH = reg("rex.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> REX_BITE = reg("rex.bite");
    public static final DeferredHolder<SoundEvent, SoundEvent> REX_STEP = reg("rex.step");

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ArkRagnarok.id(name)));
    }
}
