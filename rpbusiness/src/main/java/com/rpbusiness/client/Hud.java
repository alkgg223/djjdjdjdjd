package com.rpbusiness.client;

import com.rpbusiness.RPBusiness;
import com.rpbusiness.core.ModComponents;
import com.rpbusiness.core.ModItems;
import com.rpbusiness.economy.Money;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

/** Small cash display + business planner selection outline. */
@EventBusSubscriber(modid = RPBusiness.MODID, value = Dist.CLIENT)
public final class Hud {
    private Hud() {}
    private static long cash = -1;
    private static int recount;

    public static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
        if (--recount <= 0) { cash = Money.countCash(mc.player); recount = 10; }
        String s = "§a" + Money.fmt(cash);
        int w = mc.font.width(s) + 22;
        int x = g.guiWidth() - w - 4, y = 4;
        g.fill(x, y, x + w, y + 14, 0x88000000);
        g.renderFakeItem(new ItemStack(ModItems.CASH_10.get()), x + 1, y - 1);
        g.drawString(mc.font, s, x + 18, y + 3, 0xFFFFFF, false);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.level == null || mc.level.getGameTime() % 8 != 0) return;
        ItemStack st = p.getMainHandItem();
        if (!st.is(ModItems.BUSINESS_PLANNER.get())) return;
        BlockPos a = st.get(ModComponents.CORNER_A.get()), b = st.get(ModComponents.CORNER_B.get());
        if (a == null) return;
        if (b == null) b = a;
        int x1 = Math.min(a.getX(), b.getX()), y1 = Math.min(a.getY(), b.getY()), z1 = Math.min(a.getZ(), b.getZ());
        int x2 = Math.max(a.getX(), b.getX()) + 1, y2 = Math.max(a.getY(), b.getY()) + 1, z2 = Math.max(a.getZ(), b.getZ()) + 1;
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(1f, 0.75f, 0.2f), 1.0f);
        double step = Math.max(1.0, Math.max(x2 - x1, z2 - z1) / 24.0);
        for (double x = x1; x <= x2; x += step) for (double[] yz : new double[][]{{y1, z1}, {y1, z2}, {y2, z1}, {y2, z2}}) mc.level.addParticle(dust, x, yz[0], yz[1], 0, 0, 0);
        for (double z = z1; z <= z2; z += step) for (double[] xy : new double[][]{{x1, y1}, {x2, y1}, {x1, y2}, {x2, y2}}) mc.level.addParticle(dust, xy[0], xy[1], z, 0, 0, 0);
        for (double y = y1; y <= y2; y += 1) for (double[] xz : new double[][]{{x1, z1}, {x1, z2}, {x2, z1}, {x2, z2}}) mc.level.addParticle(dust, xz[0], y, xz[1], 0, 0, 0);
    }
}
