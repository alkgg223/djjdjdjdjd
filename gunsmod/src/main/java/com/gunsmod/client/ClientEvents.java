package com.gunsmod.client;

import com.gunsmod.GunsMod;
import com.gunsmod.client.render.GunRenderer;
import com.gunsmod.client.sound.FlameSound;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.entity.ArmoredCarEntity;
import com.gunsmod.entity.TankEntity;
import com.gunsmod.entity.VehicleEntity;
import com.gunsmod.item.GunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = GunsMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        GunClient.tick();
        GunRenderer.clientTick();
        FlameSound.clientTick();
        ClientFx.tick();
        FlashlightFx.tick();
        VehicleAlerts.tick();
        ClientLights.tick();
    }

    /** nearby explosions shake the camera */
    @SubscribeEvent
    public static void shake(ViewportEvent.ComputeCameraAngles e) {
        if (ClientFx.shake <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        float t = (mc.level != null ? mc.level.getGameTime() : 0) + (float) e.getPartialTick();
        float s = ClientFx.shake;
        e.setPitch(e.getPitch() + Mth.sin(t * 2.9f) * s * 0.6f);
        e.setYaw(e.getYaw() + Mth.sin(t * 2.3f + 1.7f) * s * 0.5f);
        e.setRoll(e.getRoll() + Mth.sin(t * 3.7f + 0.4f) * s * 0.8f);
    }

    /** guns use the attack key to fire and the use key to aim, so vanilla must not punch / break / use with them */
    @SubscribeEvent
    public static void interact(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (p.getVehicle() instanceof JeepEntity jeep && e.isAttack()) {
            e.setCanceled(true);
            e.setSwingHand(false);
            return;
        }
        if (p.getVehicle() instanceof VehicleEntity v && v.firesFor(p) && (e.isAttack() || e.isUseItem())) {
            e.setCanceled(true);
            e.setSwingHand(false);
            return;
        }
        if (e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND || !(p.getMainHandItem().getItem() instanceof GunItem)) return;
        if (e.isAttack()) {
            e.setCanceled(true);
            e.setSwingHand(false);
        } else if (e.isUseItem() && !GunClient.wantsVanillaUse(mc)) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        if (!e.usedConfiguredFov()) return;
        float a = GunClient.aim((float) e.getPartialTick());
        if (a <= 0f) return;
        float z = GunClient.zoom();
        e.setFOV(e.getFOV() / (1.0 + (z - 1.0) * a));
    }

    @SubscribeEvent
    public static void turn(CalculatePlayerTurnEvent e) {
        float a = GunClient.aim;
        if (a > 0.5f) e.setMouseSensitivity(e.getMouseSensitivity() / Math.pow(GunClient.zoom(), 0.7));
    }

    @SubscribeEvent
    public static void hideCrosshair(RenderGuiLayerEvent.Pre e) {
        if (!e.getName().equals(VanillaGuiLayers.CROSSHAIR)) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        if (GunClient.holdingGun(p) || p.getVehicle() instanceof JeepEntity j && j.isGunner(p)
                || p.getVehicle() instanceof TankEntity t && t.getControllingPassenger() == p
                || p.getVehicle() instanceof ArmoredCarEntity c && c.getControllingPassenger() == p
                || p.getVehicle() instanceof com.gunsmod.entity.JetEntity) e.setCanceled(true);
    }
}
