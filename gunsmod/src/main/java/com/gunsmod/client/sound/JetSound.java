package com.gunsmod.client.sound;

import com.gunsmod.Reg;
import com.gunsmod.client.ClientLights;
import com.gunsmod.entity.JetEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * F-16 engine: turbine whine at idle, the roar rising with the throttle, the deep crackle of the afterburner, all
 * Doppler shifted for anyone the jet flies past. A fast pass close by adds the tearing flyby roar.
 */
public class JetSound extends AbstractTickableSoundInstance {
    private static final int IDLE = 0, ENGINE = 1, BURNER = 2, FLYBY = 3;
    /** speed of sound in blocks per tick */
    private static final double C = 343.0 / 20.0;
    private static final Map<JetEntity, JetSound[]> ACTIVE = new WeakHashMap<>();
    private static final Map<JetEntity, Float> LAST_SPOOL = new WeakHashMap<>();
    private static final Map<JetEntity, Integer> LAST_FLYBY = new WeakHashMap<>();

    private final JetEntity jet;
    private final int kind;
    private int age;

    private JetSound(JetEntity jet, int kind) {
        super(Reg.sound(switch (kind) {
            case IDLE -> "jet_idle";
            case ENGINE -> "jet_engine";
            case BURNER -> "jet_afterburner";
            default -> "jet_flyby";
        }), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.jet = jet;
        this.kind = kind;
        this.looping = kind != FLYBY;
        this.delay = 0;
        this.volume = kind == FLYBY ? 9f : 0.01f;
        this.x = jet.getX();
        this.y = jet.getY();
        this.z = jet.getZ();
    }

    /** called every client tick for every jet */
    public static void tick(JetEntity j) {
        Minecraft mc = Minecraft.getInstance();
        float last = LAST_SPOOL.getOrDefault(j, j.spool);
        if (j.spool > 0.02f && last <= 0.02f && j.getControllingPassenger() != null) {
            j.level().playLocalSound(j.getX(), j.getY(), j.getZ(), Reg.sound("jet_start"), SoundSource.NEUTRAL, 5f, 1f, false);
        }
        LAST_SPOOL.put(j, j.spool);
        JetSound[] s = ACTIVE.get(j);
        if (j.spool > 0.03f && !playing(s)) {
            s = new JetSound[]{new JetSound(j, IDLE), new JetSound(j, ENGINE), new JetSound(j, BURNER)};
            ACTIVE.put(j, s);
            for (JetSound x : s) mc.getSoundManager().play(x);
        }
        flyby(mc, j);
        if (j.isAfterburner()) {
            double[] n = j.geo.nozzle();
            ClientLights.light("ab" + j.getId(), j.toWorld(n[0], n[1], n[2] + 3), 15, 2);
        }
    }

    private static boolean playing(JetSound[] s) {
        if (s == null) return false;
        for (JetSound x : s) if (x.isStopped()) return false;
        return true;
    }

    /** a fast jet about to pass close by: the flyby roar, timed so its peak is the moment of closest approach */
    private static void flyby(Minecraft mc, JetEntity j) {
        Entity me = mc.getCameraEntity();
        if (me == null || me.getVehicle() == j) return;
        Vec3 v = j.velocity;
        double vv = v.lengthSqr();
        if (vv < 1.3 * 1.3) return;
        Vec3 rel = j.position().subtract(me.getEyePosition());
        double t = -rel.dot(v) / vv;
        if (t < 14 || t > 32) return;
        double miss = rel.add(v.scale(t)).length();
        int now = j.tickCount;
        if (miss > 55 || now - LAST_FLYBY.getOrDefault(j, -1000) < 120) return;
        LAST_FLYBY.put(j, now);
        mc.getSoundManager().play(new JetSound(j, FLYBY));
    }

    /** Doppler factor for the listener: above 1 while the jet closes in, below once it has passed */
    private double doppler() {
        Minecraft mc = Minecraft.getInstance();
        Entity me = mc.getCameraEntity();
        if (me == null || me.getVehicle() == jet) return 1.0;
        Vec3 d = me.getEyePosition().subtract(jet.position());
        if (d.lengthSqr() < 1e-4) return 1.0;
        d = d.normalize();
        double vs = jet.velocity.dot(d), vl = me.getDeltaMovement().dot(d);
        return Mth.clamp((C - vl) / (C - Math.min(vs, C * 0.8)), 0.6, 1.7);
    }

    @Override
    public void tick() {
        age++;
        boolean alive = jet.isAlive() && !jet.isRemoved();
        if (kind == FLYBY) {
            if (!alive || age > 90) {
                this.stop();
                return;
            }
            follow();
            return;
        }
        if (!alive || jet.spool <= 0.02f && !jet.isCrashing()) {
            this.volume -= 0.15f;
            if (this.volume <= 0.02f) this.stop();
            return;
        }
        float thr = jet.isCrashing() ? 0.4f : jet.getThrottle(), sp = jet.isCrashing() ? 1f : jet.spool;
        float target, pitch;
        switch (kind) {
            case IDLE -> {
                target = 3.5f * sp * (1f - 0.55f * thr);
                pitch = 0.65f + 0.35f * sp + 0.1f * thr;
            }
            case ENGINE -> {
                target = (1.0f + 11f * thr) * sp * sp;
                pitch = 0.55f + 0.5f * thr;
            }
            default -> {
                target = jet.isAfterburner() ? 13f : 0.01f;
                pitch = 0.9f;
            }
        }
        this.pitch = (float) Mth.clamp(pitch * doppler(), 0.5, 2.0);
        this.volume += (target - this.volume) * 0.2f;
        follow();
    }

    private void follow() {
        Vec3 p = jet.position();
        this.x = p.x;
        this.y = p.y + 1.5;
        this.z = p.z;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
