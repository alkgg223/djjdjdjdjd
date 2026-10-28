package com.gunsmod.client.sound;

import com.gunsmod.Reg;
import com.gunsmod.entity.JeepEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;

/** Looping engine sound that follows the jeep and cross-fades between idle and driving. */
public class JeepSound extends AbstractTickableSoundInstance {
    private static final Map<JeepEntity, JeepSound[]> ACTIVE = new WeakHashMap<>();

    private final JeepEntity jeep;
    private final boolean drive;

    private JeepSound(JeepEntity jeep, boolean drive) {
        super(Reg.sound(drive ? "jeep_drive" : "jeep_idle"), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.jeep = jeep;
        this.drive = drive;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.x = jeep.getX();
        this.y = jeep.getY();
        this.z = jeep.getZ();
    }

    /** called every client tick for every jeep */
    public static void tickJeep(JeepEntity jeep) {
        Minecraft mc = Minecraft.getInstance();
        boolean engine = jeep.getControllingPassenger() != null && jeep.isAlive();
        JeepSound[] s = ACTIVE.get(jeep);
        if (engine && (s == null || s[0].isStopped() || s[1].isStopped())) {
            s = new JeepSound[]{new JeepSound(jeep, false), new JeepSound(jeep, true)};
            ACTIVE.put(jeep, s);
            mc.getSoundManager().play(s[0]);
            mc.getSoundManager().play(s[1]);
        }
        // exhaust smoke and damage smoke
        if (engine && jeep.tickCount % 4 == 0) {
            var p = jeep.toWorld(16.0, 3.5, 30.0);
            jeep.level().addParticle(ParticleTypes.SMOKE, p.x, p.y, p.z, 0, 0.02, 0);
        }
        float hp = jeep.getHealth() / jeep.getMaxHealth();
        if (hp < 0.5f && jeep.tickCount % (hp < 0.25f ? 2 : 6) == 0) {
            var p = jeep.toWorld(8.0, 11.5, -9.0);
            jeep.level().addParticle(hp < 0.25f ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, p.x, p.y, p.z, 0, 0.06, 0);
            if (hp < 0.2f && jeep.tickCount % 6 == 0) jeep.level().addParticle(ParticleTypes.FLAME, p.x, p.y, p.z, 0, 0.03, 0);
        }
    }

    @Override
    public void tick() {
        if (!jeep.isAlive() || jeep.isRemoved() || jeep.getControllingPassenger() == null) {
            this.volume -= 0.1f;
            if (this.volume <= 0.02f) this.stop();
            return;
        }
        this.x = jeep.getX();
        this.y = jeep.getY();
        this.z = jeep.getZ();
        double sp = Math.min(1.0, Math.abs(jeep.getDisplaySpeed()) / JeepEntity.MAX_SPEED);
        float target = drive ? (float) (0.15 + 0.85 * sp) : (float) (1.0 - sp * 0.85);
        this.volume += (target * 1.1f - this.volume) * 0.2f;
        this.pitch = drive ? (float) (0.7 + sp * 0.6) : 1.0f;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
