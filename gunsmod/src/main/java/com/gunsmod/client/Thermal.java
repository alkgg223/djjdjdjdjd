package com.gunsmod.client;

import com.gunsmod.Reg;
import com.gunsmod.gun.Attachment;
import com.gunsmod.item.GunItem;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * The thermal scope: the world in white-hot greys (cold ground dark, warm things bright), and every living thing and
 * running vehicle in view outlined in white so the heat stands out even behind leaves.
 */
public final class Thermal {
    private Thermal() {}

    private static PostChain chain;
    private static boolean failed;
    private static int w, h;
    private static final Set<Integer> LIT = new HashSet<>();

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        if (DroneClient.thermalOn()) return true;
        if (mc.player == null || !GunClient.scopeView()) return false;
        return GunItem.attachments(mc.player.getMainHandItem()).get(Attachment.Slot.SCOPE) == Attachment.THERMAL_SCOPE;
    }

    /** every client tick: light up the warm bodies while looking through the scope, put them back after */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            LIT.clear();
            return;
        }
        boolean on = active();
        Set<Integer> now = new HashSet<>();
        if (on) {
            Entity eye = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
            AABB box = eye.getBoundingBox().inflate(220);
            for (Entity e : mc.level.getEntities(mc.player, box, x -> x instanceof LivingEntity le && le.isAlive())) {
                e.setGlowingTag(true);
                now.add(e.getId());
            }
        }
        // enemies the side's drones have spotted stand out through walls
        for (Integer id : DroneClient.SPOTTED) {
            Entity e = mc.level.getEntity(id);
            if (e == null || !e.isAlive()) continue;
            e.setGlowingTag(true);
            now.add(id);
        }
        for (Integer id : LIT) {
            if (now.contains(id)) continue;
            Entity e = mc.level.getEntity(id);
            if (e != null) e.setGlowingTag(false);
        }
        LIT.clear();
        LIT.addAll(now);
    }

    /** GUI layer below all others: the post effect over the world */
    public static void layer(GuiGraphics g, DeltaTracker dt) {
        if (failed || !active()) return;
        Minecraft mc = Minecraft.getInstance();
        int fw = mc.getWindow().getWidth(), fh = mc.getWindow().getHeight();
        try {
            if (chain == null) {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), Reg.id("shaders/post/thermal.json"));
                w = h = -1;
            }
            if (fw != w || fh != h) {
                chain.resize(fw, fh);
                w = fw;
                h = fh;
            }
            g.flush();
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            chain.process(dt.getGameTimeDeltaTicks());
            mc.getMainRenderTarget().bindWrite(true);
        } catch (Exception | LinkageError e) {
            failed = true;
            if (chain != null) chain.close();
            chain = null;
        }
    }
}
