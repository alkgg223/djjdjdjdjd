package com.arkragnarok.client.hud;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.entity.ArkCreature;
import com.arkragnarok.registry.ModItems;
import com.arkragnarok.species.Species;
import com.arkragnarok.taming.Taming;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Super Spyglass read-out: while the spyglass is in a hand, the creature under the cross-hair (up to 150 blocks, not
 * through walls) shows level, health, torpor, what tames it and how much of it, the arrows needed to knock it out,
 * and the taming progress while it sleeps.
 */
@EventBusSubscriber(modid = ArkRagnarok.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SpyglassHud {
    private SpyglassHud() {}

    public static final double RANGE = 150.0;

    @SubscribeEvent
    public static void layers(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ArkRagnarok.id("super_spyglass"), SpyglassHud::render);
    }

    private static ArkCreature pick(Minecraft mc, Player p, float pt) {
        Vec3 eye = p.getEyePosition(pt);
        Vec3 look = p.getViewVector(pt);
        Vec3 far = eye.add(look.scale(RANGE));
        HitResult block = mc.level.clip(new ClipContext(eye, far, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
        double max = block.getType() == HitResult.Type.MISS ? RANGE : block.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(max));
        AABB box = p.getBoundingBox().expandTowards(look.scale(max)).inflate(2.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, box, e -> e instanceof ArkCreature && e.isAlive(), max * max);
        if (hit == null) return null;
        Entity e = hit.getEntity();
        return e instanceof ArkCreature c ? c : null;
    }

    private static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.level == null || mc.options.hideGui) return;
        if (!p.getMainHandItem().is(ModItems.SUPER_SPYGLASS.get()) && !p.getOffhandItem().is(ModItems.SUPER_SPYGLASS.get())) return;
        ArkCreature c = pick(mc, p, dt.getGameTimeDeltaPartialTick(true));
        Font f = mc.font;
        int w = 200;
        int x = g.guiWidth() - w - 8;
        int y = 8;
        if (c == null) {
            g.fill(x, y, x + w, y + 16, 0xA0101418);
            g.drawString(f, Component.translatable("hud.arkragnarok.aim"), x + 6, y + 4, 0xC8C8C8, false);
            return;
        }
        Species s = c.species();
        int lines = 6;
        boolean wild = !c.isTamed();
        if (wild) lines += Taming.passive(s) ? 3 : 4;
        if (c.isUnconscious()) lines += 2;
        int h = 10 + lines * 11;
        g.fill(x, y, x + w, y + h, 0xB0101418);
        g.fill(x, y, x + w, y + 1, 0xFFD9A441);
        int ty = y + 6;
        g.drawString(f, Component.literal(s.name + "  ").append(Component.translatable("hud.arkragnarok.level", c.getLevel())), x + 6, ty, 0xFFE9C46A, true);
        ty += 12;
        String owner = !wild ? (p.getUUID().equals(c.getOwnerId()) ? "hud.arkragnarok.yours" : "hud.arkragnarok.tamed")
                : c.isUnconscious() ? "hud.arkragnarok.unconscious" : "hud.arkragnarok.wild";
        g.drawString(f, Component.translatable(owner), x + 6, ty, wild ? (c.isUnconscious() ? 0xFF8AB4FF : 0xFFE07A5F) : 0xFF81C784, false);
        ty += 12;
        bar(g, f, x + 6, ty, w - 12, Component.translatable("hud.arkragnarok.health"), c.realHealth(), c.realMaxHealth(), 0xFF3FA34D);
        ty += 12;
        bar(g, f, x + 6, ty, w - 12, Component.translatable("hud.arkragnarok.torpor"), c.getTorpor(), c.maxTorpor(), 0xFF8E5CC2);
        ty += 12;
        g.drawString(f, Component.translatable("hud.arkragnarok.diet." + s.diet.name().toLowerCase())
                .append("  ").append(Component.translatable("hud.arkragnarok.temper." + s.temper.name().toLowerCase())), x + 6, ty, 0xFFB0B0B0, false);
        ty += 12;
        if (wild) {
            float remaining = c.requiredAffinity() - c.getAffinity();
            ItemStack kib = Taming.preferredKibble(s);
            ItemStack alt = Taming.fallbackFood(s);
            int nk = Taming.countNeeded(remaining, Taming.foodValue(s, kib));
            int na = Taming.countNeeded(remaining, Taming.foodValue(s, alt));
            g.drawString(f, Component.translatable(Taming.passive(s) ? "hud.arkragnarok.passive" : "hud.arkragnarok.knockout"), x + 6, ty, 0xFFE0E0E0, false);
            ty += 12;
            g.renderItem(kib, x + 6, ty - 3);
            g.drawString(f, Component.literal(nk + " x ").append(kib.getHoverName()), x + 26, ty + 1, 0xFFFFFFFF, false);
            ty += 12;
            g.renderItem(alt, x + 6, ty - 1);
            g.drawString(f, Component.translatable("hud.arkragnarok.or").append(" " + na + " x ").append(alt.getHoverName()), x + 26, ty + 3, 0xFFC8C8C8, false);
            ty += 14;
            if (!Taming.passive(s)) {
                int arrows = (int) Math.ceil(Math.max(0, c.maxTorpor() - c.getTorpor()) / Taming.TRANQ_ARROW);
                if (!c.isUnconscious()) {
                    g.drawString(f, Component.translatable("hud.arkragnarok.arrows", arrows), x + 6, ty, 0xFFC8C8C8, false);
                    ty += 12;
                }
            }
            if (c.isUnconscious()) {
                int pct = Math.round(100f * c.getAffinity() / c.requiredAffinity());
                bar(g, f, x + 6, ty, w - 12, Component.translatable("hud.arkragnarok.taming"), c.getAffinity(), c.requiredAffinity(), 0xFFD9A441);
                ty += 12;
                int secs = Math.max(0, nk) * Taming.eatIntervalTicks(s) / 20;
                g.drawString(f, Component.translatable("hud.arkragnarok.effectiveness", Math.round(c.getEffectiveness() * 100), pct, secs / 60, secs % 60),
                        x + 6, ty, 0xFFC8C8C8, false);
            }
        }
    }

    private static void bar(GuiGraphics g, Font f, int x, int y, int w, Component label, float v, float max, int colour) {
        int lw = 52;
        g.drawString(f, label, x, y, 0xFFD0D0D0, false);
        int bx = x + lw, bw = w - lw;
        g.fill(bx, y, bx + bw, y + 8, 0xFF2A2A2A);
        int fw = max <= 0 ? 0 : Math.round(bw * Math.min(1f, Math.max(0f, v / max)));
        g.fill(bx, y, bx + fw, y + 8, colour);
        String t = Math.round(v) + " / " + Math.round(max);
        g.drawString(f, t, bx + (bw - f.width(t)) / 2, y, 0xFFFFFFFF, true);
    }
}
