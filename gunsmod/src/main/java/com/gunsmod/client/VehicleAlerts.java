package com.gunsmod.client;

import com.gunsmod.Reg;
import com.gunsmod.entity.ArmoredCarEntity;
import com.gunsmod.entity.HeliType;
import com.gunsmod.entity.HelicopterEntity;
import com.gunsmod.entity.JetEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;

/** Cockpit tones: missile approach warning, radar warning, seeker growl while locking and the steady lock tone. */
public final class VehicleAlerts {
    private VehicleAlerts() {}

    private static int t;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        t++;
        if (p.getVehicle() instanceof HelicopterEntity h) {
            int w = h.getWarning();
            if (w == 2 && t % 5 == 0) beep(mc, p, "missile_warning", 0.9f, 1.0f);
            else if (w == 1 && t % 20 == 0) beep(mc, p, "lock_tone", 0.5f, 0.7f);
            if (h.firesFor(p) && h.getLockTarget() >= 0 && h.getWeapon().guide == HeliType.Guide.LOCK) {
                lockTone(mc, p, h.getLockProgress() >= HelicopterEntity.LOCK_TICKS);
            }
        } else if (p.getVehicle() instanceof JetEntity j) {
            int w = j.getWarning();
            if (w == 2 && t % 5 == 0) beep(mc, p, "missile_warning", 0.9f, 1.0f);
            else if (w == 1 && t % 20 == 0) beep(mc, p, "lock_tone", 0.5f, 0.7f);
            if (j.getLockTarget() >= 0 && j.missilesLeft(j.getWeapon()) > 0) lockTone(mc, p, j.getLockProgress() >= JetEntity.LOCK_TICKS);
            // stall warning horn
            if (!j.onGround() && j.getAirSpeed() < JetEntity.STALL * 1.1 && t % 8 == 0) beep(mc, p, "ui_beep", 0.6f, 0.6f);
        } else if (p.getVehicle() instanceof ArmoredCarEntity c && c.kind.antiAir() && c.getControllingPassenger() == p) {
            if (t % 40 == 0) beep(mc, p, "radar_ping", 0.35f, 1f);
            if (c.getTrack() >= 0) lockTone(mc, p, c.isLocked());
        }
    }

    private static void lockTone(Minecraft mc, LocalPlayer p, boolean locked) {
        if (locked) {
            if (t % 3 == 0) beep(mc, p, "lock_tone", 0.45f, 1.6f);
        } else if (t % 6 == 0) {
            beep(mc, p, "lock_tone", 0.4f, 1.0f);
        }
    }

    private static void beep(Minecraft mc, LocalPlayer p, String sound, float vol, float pitch) {
        mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), Reg.sound(sound), SoundSource.PLAYERS, vol, pitch, false);
    }
}
