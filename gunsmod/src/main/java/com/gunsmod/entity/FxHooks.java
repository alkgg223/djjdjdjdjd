package com.gunsmod.entity;

import java.util.function.BiConsumer;
import net.minecraft.world.entity.Entity;

/** Client effects that common entity code may ask for; filled in by the client setup, nothing on a server. */
public final class FxHooks {
    private FxHooks() {}

    /** a moving light source at the entity (flares, rocket motors) with this light level */
    public static BiConsumer<Entity, Integer> light = (e, level) -> { };
}
