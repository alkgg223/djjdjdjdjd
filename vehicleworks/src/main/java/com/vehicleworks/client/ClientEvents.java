package com.vehicleworks.client;

import com.vehicleworks.Config;
import com.vehicleworks.network.Payloads;
import com.vehicleworks.vehicle.ControlProfile;
import com.vehicleworks.vehicle.VehicleControls;
import com.vehicleworks.vehicle.VehicleEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;

/** Client input: pilot controls, vehicle block interaction, camera behaviour. */
public final class ClientEvents {
    private ClientEvents() {}

    public static VehicleEntity ridden() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.getVehicle() instanceof VehicleEntity v ? v : null;
    }

    public static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        VehicleEntity v = ridden();
        if (v == null || mc.player == null) {
            // consume toggles so they do not fire later
            for (KeyMapping k : Keybinds.ALL) while (k.consumeClick()) { }
            VoiceCallouts.reset();
            return;
        }
        var o = mc.options;
        int keys = 0;
        boolean ui = mc.screen != null;
        if (!ui) {
            if (o.keyUp.isDown()) keys |= VehicleControls.FORWARD;
            if (o.keyDown.isDown()) keys |= VehicleControls.BACK;
            if (o.keyLeft.isDown()) keys |= VehicleControls.LEFT;
            if (o.keyRight.isDown()) keys |= VehicleControls.RIGHT;
            if (o.keyJump.isDown()) keys |= VehicleControls.JUMP;
            if (o.keySprint.isDown()) keys |= VehicleControls.SPRINT;
            if (Keybinds.YAW_LEFT.isDown()) keys |= VehicleControls.YAW_LEFT;
            if (Keybinds.YAW_RIGHT.isDown()) keys |= VehicleControls.YAW_RIGHT;
            if (Keybinds.THROTTLE_UP.isDown()) keys |= VehicleControls.THROTTLE_UP;
            if (Keybinds.THROTTLE_DOWN.isDown()) keys |= VehicleControls.THROTTLE_DOWN;
            if (Keybinds.PITCH_UP.isDown()) keys |= VehicleControls.PITCH_UP;
            if (Keybinds.PITCH_DOWN.isDown()) keys |= VehicleControls.PITCH_DOWN;
            if (Keybinds.BRAKE.isDown()) keys |= VehicleControls.BRAKE;
            int fire = 0;
            if (o.keyAttack.isDown()) fire |= 1;
            if (o.keyUse.isDown()) fire |= 2;
            if (Keybinds.FIRE_3.isDown()) fire |= 4;
            if (Keybinds.FIRE_4.isDown()) fire |= 8;
            if (Keybinds.FIRE_5.isDown()) fire |= 16;
            if (Keybinds.FIRE_6.isDown()) fire |= 32;
            keys |= fire << VehicleControls.FIRE_SHIFT;
        }
        Payloads.toServer(new Payloads.Input(v.getId(), keys, mc.player.getYRot(), mc.player.getXRot()));
        while (Keybinds.ENGINES.consumeClick()) {
            boolean anyRunning = false;
            for (byte st : v.telemetry.engineState) if (st == 1 || st == 2) anyRunning = true;
            action(v, anyRunning ? Payloads.ACT_ENGINES_STOP : Payloads.ACT_ENGINES_START);
        }
        while (Keybinds.GEAR.consumeClick()) action(v, Payloads.ACT_GEAR);
        while (Keybinds.FLAPS.consumeClick()) action(v, Payloads.ACT_FLAPS);
        while (Keybinds.ARM.consumeClick()) action(v, Payloads.ACT_ARM);
        while (Keybinds.ASSIST.consumeClick()) action(v, Payloads.ACT_ASSIST);
        while (Keybinds.MOUSE_AIM.consumeClick()) action(v, Payloads.ACT_MOUSE_AIM);
        while (Keybinds.PARKING.consumeClick()) action(v, Payloads.ACT_PARKING);
        while (Keybinds.MENU.consumeClick()) action(v, Payloads.ACT_OPEN_ENGINEERING);
        while (Keybinds.AUTOPILOT.consumeClick()) Minecraft.getInstance().setScreen(new AutopilotScreen(v));
        while (Keybinds.VOICE.consumeClick()) VoiceCallouts.toggle();
        VoiceCallouts.tick(v);
        while (Keybinds.PANEL.consumeClick()) {
            boolean has = false;
            for (var st : v.structure.blocks().values())
                if (com.vehicleworks.vehicle.VehicleBuilder.typeOf(st) == com.vehicleworks.component.ComponentType.COCKPIT_PANEL) { has = true; break; }
            if (has) Minecraft.getInstance().setScreen(new PanelScreen(v));
            else if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component.translatable("vehicleworks.panel.none"), true);
        }
    }

    static void action(VehicleEntity v, int a) {
        Payloads.toServer(new Payloads.Action(v.getId(), a, 0));
    }

    /** Vehicle block interaction (use / break / pick) and fire buttons while seated. */
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (ridden() != null) {
            if (e.isAttack() || e.isUseItem()) {
                e.setCanceled(true);
                e.setSwingHand(false);
            }
            return;
        }
        if (!(mc.hitResult instanceof VehicleHitResult hit)) return;
        int action = e.isAttack() ? 1 : (e.isUseItem() ? 0 : 2);
        var lh = hit.localHit();
        Payloads.toServer(new Payloads.Interact(hit.vehicle().getId(), hit.block(), hit.face().get3DDataValue(),
                (float) lh.x, (float) lh.y, (float) lh.z, action, e.getHand() == InteractionHand.MAIN_HAND));
        e.setCanceled(true);
        e.setSwingHand(action != 2);
    }

    /** Roll the first-person camera with the aircraft. */
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        VehicleEntity v = ridden();
        if (v == null || !Config.CLIENT.cameraRoll.get()) return;
        if (Minecraft.getInstance().options.getCameraType() != CameraType.FIRST_PERSON) return;
        if (v.telemetry.mouseAim && VehicleHud.isFlying(v)) return;
        ControlProfile p = ControlProfile.byIndex(v.telemetry.profile);
        if (p != ControlProfile.AIRCRAFT && p != ControlProfile.HELICOPTER) return;
        float partial = (float) e.getPartialTick();
        var q = v.renderOrientation(partial, new org.joml.Quaterniond());
        var right = q.transform(new org.joml.Vector3d(1, 0, 0));
        var up = q.transform(new org.joml.Vector3d(0, 1, 0));
        var fw = q.transform(new org.joml.Vector3d(0, 0, -1));
        // bank seen from the pilot looking along the vehicle's forward axis
        double bank = Math.toDegrees(Math.atan2(-right.y, up.y));
        double yawCam = Math.toRadians(e.getYaw());
        double lookX = -Math.sin(yawCam), lookZ = Math.cos(yawCam);
        double align = lookX * fw.x + lookZ * fw.z;
        e.setRoll(e.getRoll() + (float) (bank * align));
    }

    /** Pull the third-person camera back for large vehicles. */
    public static void onCameraDistance(CalculateDetachedCameraDistanceEvent e) {
        VehicleEntity v = ridden();
        if (v == null) return;
        double size = v.getBoundingBox().getSize();
        e.setDistance((float) Math.max(e.getDistance(), Math.min(48, size * 0.9 + 3)));
    }

    /** The swap-hands key (F) is the throttle-down key while piloting. */
    public static void onSwapHands(LivingSwapItemsEvent.Hands e) {
        if (e.getEntity().level().isClientSide && e.getEntity() == Minecraft.getInstance().player && ridden() != null) e.setCanceled(true);
    }
}
