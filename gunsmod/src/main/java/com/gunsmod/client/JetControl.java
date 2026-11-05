package com.gunsmod.client;

import com.gunsmod.GunsMod;
import com.gunsmod.entity.JetEntity;
import com.gunsmod.net.Net;
import com.mojang.blaze3d.platform.InputConstants;
import javax.annotation.Nullable;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Flying from the cockpit with the view locked to the aircraft: the camera sits at the pilot's eye and turns, pitches
 * and banks with the jet; dragging the mouse (or a finger on a phone) is the stick, so the aircraft moves and the view
 * stays fixed in the cockpit. The view key switches back to steering by looking.
 */
@EventBusSubscriber(modid = GunsMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class JetControl {
    private JetControl() {}

    public static final KeyMapping VIEW = new KeyMapping("key.gunsmod.jet_view", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, GunClient.CATEGORY);
    /** degrees the view is dragged in a tick for a full stick deflection */
    private static final float FULL = 9f;
    public static boolean locked = true;
    private static float accX, accY, lastYaw = Float.NaN, lastPitch;
    private static boolean wasOn;

    public static void init() {
        JetEntity.lockedPilot = JetControl::active;
    }

    /** the local player flies this jet from the cockpit with the view locked */
    public static boolean active(JetEntity j) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        return locked && p != null && p.getVehicle() == j && j.getControllingPassenger() == p && mc.options.getCameraType().isFirstPerson()
                && mc.getCameraEntity() == p && !j.isCrashing();
    }

    @Nullable
    private static JetEntity flying() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.getVehicle() instanceof JetEntity j ? j : null;
    }

    /** every frame: whatever the mouse turned goes to the stick, the view is put back onto the aircraft */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        JetEntity j = flying();
        if (j == null || !active(j)) {
            lastYaw = Float.NaN;
            return;
        }
        LocalPlayer p = Minecraft.getInstance().player;
        if (!Float.isNaN(lastYaw)) {
            accX += Mth.wrapDegrees(p.getYRot() - lastYaw);
            accY += p.getXRot() - lastPitch;
        }
        float pt = (float) e.getPartialTick();
        float yaw = Mth.rotLerp(pt, j.yRotO, j.getYRot());
        float pitch = Mth.lerp(pt, j.prevPitch, j.visPitch);
        float roll = Mth.lerp(pt, j.prevRoll, j.roll);
        p.setYRot(yaw);
        p.setXRot(Mth.clamp(-pitch, -90f, 90f));
        p.yRotO = yaw;
        p.xRotO = p.getXRot();
        p.yHeadRot = yaw;
        p.yHeadRotO = yaw;
        lastYaw = yaw;
        lastPitch = p.getXRot();
        e.setYaw(yaw);
        e.setPitch(-pitch);
        e.setRoll(roll);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        while (VIEW.consumeClick()) {
            locked = !locked;
            if (mc.player != null) mc.player.displayClientMessage(Component.translatable(locked ? "gunsmod.msg.jet_view_locked" : "gunsmod.msg.jet_view_free"), true);
        }
        JetEntity j = flying();
        boolean on = j != null && active(j);
        if (j != null && !on) {
            j.stickInX = 0;
            j.stickInY = 0;
        }
        if (on) {
            j.stickInX = Mth.clamp(accX / FULL, -1f, 1f);
            j.stickInY = Mth.clamp(-accY / FULL, -1f, 1f);
            if (j.tickCount % 2 == 0) PacketDistributor.sendToServer(new Net.JetBank(j.bank, j.stickX, j.stickY, true));
        } else if (wasOn) {
            PacketDistributor.sendToServer(new Net.JetBank(0f, 0f, 0f, false));
        }
        wasOn = on;
        accX = 0;
        accY = 0;
    }

    /** no arms and gun in front of the instrument panel */
    @SubscribeEvent
    public static void hand(RenderHandEvent e) {
        if (flying() != null && Minecraft.getInstance().options.getCameraType().isFirstPerson()) e.setCanceled(true);
    }
}
