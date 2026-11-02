package com.gunsmod.client.sound;

import com.gunsmod.Reg;
import com.gunsmod.entity.BomberEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** The bomber's deep engine drone, heard from far below, following it round its circuit. */
public class BomberSound extends AbstractTickableSoundInstance {
    private static final Map<BomberEntity, BomberSound> ACTIVE = new WeakHashMap<>();
    private final BomberEntity bomber;

    private BomberSound(BomberEntity b) {
        super(Reg.sound("b2_drone"), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.bomber = b;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.attenuation = Attenuation.NONE;
        this.x = b.getX();
        this.y = b.getY();
        this.z = b.getZ();
    }

    /** every client tick for every bomber */
    public static void tick(BomberEntity b) {
        BomberSound s = ACTIVE.get(b);
        if (b.isAlive() && (s == null || s.isStopped())) {
            s = new BomberSound(b);
            ACTIVE.put(b, s);
            Minecraft.getInstance().getSoundManager().play(s);
        }
    }

    @Override
    public void tick() {
        if (!bomber.isAlive() || bomber.isRemoved()) {
            stop();
            return;
        }
        x = bomber.getX();
        y = bomber.getY();
        z = bomber.getZ();
        Entity me = Minecraft.getInstance().getCameraEntity();
        double d = me == null ? 200 : me.distanceTo(bomber);
        // no fall-off curve of the engine: it is loud, high and far, and fades slowly with distance
        volume = (float) Mth.clamp(1.25 - d / 260.0, 0.0, 1.0) * (bomber.isCrashing() ? 1.2f : 0.85f);
        pitch = bomber.isCrashing() ? 0.8f : 1.0f;
    }
}
