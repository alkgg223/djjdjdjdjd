package com.gunsmod.client;

import com.gunsmod.Reg;
import com.gunsmod.gun.Attachment;
import com.gunsmod.item.GunItem;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PostChain;

/**
 * Looking through the night vision scope: the world is drawn again through an image intensifier shader (green
 * phosphor running into white, tube grain, vignette). Runs before any other HUD layer. If the shader cannot be
 * loaded, the scope still works with its green reticle.
 */
public final class NightVision {
    private NightVision() {}

    private static PostChain chain;
    private static boolean failed;
    private static int w, h;

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !GunClient.scopeView()) return false;
        return GunItem.attachments(mc.player.getMainHandItem()).get(Attachment.Slot.SCOPE) == Attachment.NV_SCOPE;
    }

    /** GUI layer below all others: the post effect over the world */
    public static void layer(GuiGraphics g, DeltaTracker dt) {
        if (failed || !active()) return;
        Minecraft mc = Minecraft.getInstance();
        int fw = mc.getWindow().getWidth(), fh = mc.getWindow().getHeight();
        try {
            if (chain == null) {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), Reg.id("shaders/post/nightvision.json"));
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
