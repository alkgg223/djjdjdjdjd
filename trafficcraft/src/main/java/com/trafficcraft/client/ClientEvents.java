package com.trafficcraft.client;

import com.trafficcraft.TrafficCraft;
import com.trafficcraft.client.gui.VehicleScreen;
import com.trafficcraft.client.sound.VehicleSounds;
import com.trafficcraft.net.VehicleControlPayload;
import com.trafficcraft.vehicle.VehicleEntity;
import com.trafficcraft.vehicle.VehiclePhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TrafficCraft.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static int hornHeld;

    public static VehicleEntity drivenVehicle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof VehicleEntity v && v.getControllingPassenger() == mc.player) return v;
        return null;
    }

    public static void send(VehicleEntity v, int action) {
        PacketDistributor.sendToServer(new VehicleControlPayload(v.getId(), action));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        VehicleSounds.tick(mc);
        VehicleEntity v = drivenVehicle();
        if (v == null) {
            for (var k : VehicleKeys.ALL) while (k.consumeClick()) { }
            return;
        }
        while (VehicleKeys.LIGHTS.consumeClick()) send(v, VehicleEntity.A_LIGHTS);
        while (VehicleKeys.HIGH_BEAM.consumeClick()) send(v, VehicleEntity.A_HIGH);
        while (VehicleKeys.IND_LEFT.consumeClick()) send(v, VehicleEntity.A_IND_L);
        while (VehicleKeys.IND_RIGHT.consumeClick()) send(v, VehicleEntity.A_IND_R);
        while (VehicleKeys.HAZARD.consumeClick()) send(v, VehicleEntity.A_HAZARD);
        while (VehicleKeys.ENGINE.consumeClick()) send(v, VehicleEntity.A_ENGINE);
        while (VehicleKeys.INTERIOR.consumeClick()) send(v, VehicleEntity.A_INTERIOR);
        while (VehicleKeys.SIREN.consumeClick()) send(v, VehicleEntity.A_SIREN);
        while (VehicleKeys.GEAR_MODE.consumeClick()) send(v, VehicleEntity.A_GEARMODE);
        while (VehicleKeys.GEAR_UP.consumeClick()) shift(v, 1);
        while (VehicleKeys.GEAR_DOWN.consumeClick()) shift(v, -1);
        while (VehicleKeys.MENU.consumeClick()) mc.setScreen(new VehicleScreen(v));
        while (VehicleKeys.HORN.consumeClick()) { }
        if (VehicleKeys.HORN.isDown()) {
            if (hornHeld % 16 == 0) send(v, VehicleEntity.A_HORN);
            hornHeld++;
        } else hornHeld = 0;
    }

    public static void shift(VehicleEntity v, int dir) {
        if (!v.has(VehicleEntity.F_MANUAL)) return;
        int max = VehiclePhysics.gears(v.spec()).length;
        int g = v.gear + dir;
        if (g == 0) g = dir > 0 ? 1 : -1;
        if (g < -1) g = -1;
        if (g > max) g = max;
        if (g < 0 && v.speed > 1f) return;
        v.gear = g;
        VehicleSounds.event(v, "shift");
    }

    @SubscribeEvent
    public static void camera(CalculateDetachedCameraDistanceEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof VehicleEntity v) {
            e.setDistance(Math.max(e.getDistance(), v.spec().length * 0.75f + 2.6f));
        }
    }

    @SubscribeEvent
    public static void openScreen(ScreenEvent.Opening e) {
        if (e.getNewScreen() instanceof InventoryScreen && !(e.getNewScreen() instanceof VehicleScreen.InventoryScreenBypass)) {
            VehicleEntity v = drivenVehicle();
            if (v != null) e.setNewScreen(new VehicleScreen(v));
        }
    }

    @SubscribeEvent
    public static void hand(RenderHandEvent e) {
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getVehicle() instanceof VehicleEntity) e.setCanceled(true);
    }
}
