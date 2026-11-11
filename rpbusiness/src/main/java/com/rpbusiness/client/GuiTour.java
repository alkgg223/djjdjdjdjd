package com.rpbusiness.client;

import com.rpbusiness.RPBusiness;
import com.rpbusiness.client.screen.BusinessScreen;
import com.rpbusiness.client.screen.RecipeBookScreen;
import com.rpbusiness.network.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Developer-only: with -Drpbusiness.guitour, cycles through every screen for automated screenshots. */
@EventBusSubscriber(modid = RPBusiness.MODID, value = Dist.CLIENT)
public final class GuiTour {
    private GuiTour() {}
    private static final boolean ON = System.getProperty("rpbusiness.guitour") != null;
    private static final String[] STEPS = {"overview", "menu", "orders", "staff", "hire", "finance", "settings", "empire", "supplier", "recipes"};
    private static int ticks, step = -1;
    private static java.util.UUID biz;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (!ON) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen instanceof BusinessScreen bs) biz = bs.businessId();
        if (biz == null || ++ticks % 120 != 0) return;
        step++;
        if (step >= STEPS.length) return;
        String s = STEPS[step];
        RPBusiness.LOG.info("[GUITOUR] " + s);
        if (s.equals("supplier")) {
            CompoundTag t = new CompoundTag();
            t.putUUID("biz", biz);
            Net.toServer("open_supplier", t);
        } else if (s.equals("recipes")) {
            mc.setScreen(new RecipeBookScreen(null));
        } else if (mc.screen instanceof BusinessScreen bs) bs.switchTab(s);
    }
}
