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

    private static VehicleEntity lastRidden;
    private static Long lastSeat;

    public static void onClientTick(ClientTickEvent.Post e) {
        SeatVideo.tick();
        Minecraft mc = Minecraft.getInstance();
        // stepping out of a seat: stand next to the seat on the vehicle as this client shows it
        VehicleEntity riding = ridden();
        if (mc.player != null) {
            if (riding != null) {
                lastRidden = riding;
                Long s = riding.seats.get(mc.player.getUUID());
                if (s != null) lastSeat = s;
            } else if (lastRidden != null) {
                if (!lastRidden.isRemoved() && lastSeat != null && lastRidden.distanceToSqr(mc.player) < 400 * 400) {
                    long s = lastSeat;
                    com.vehicleworks.vehicle.EntityVehicleCollision.placeOnVehicle(mc.player, lastRidden,
                            net.minecraft.core.BlockPos.getX(s) + 0.5, net.minecraft.core.BlockPos.getY(s) + 1.05, net.minecraft.core.BlockPos.getZ(s) + 0.5);
                }
                lastRidden = null;
                lastSeat = null;
            }
        }
        // vehicles may tick after the player: re-apply the carry so the player never lags behind
        if (mc.player != null && mc.player.getVehicle() == null) com.vehicleworks.vehicle.EntityVehicleCollision.carry(mc.player);
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
        boolean passenger = v.isPassengerSeated(mc.player);
        while (Keybinds.CAMERA_LOCK.consumeClick()) {
            cameraLock = !cameraLock;
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(cameraLock ? "vehicleworks.msg.camera_lock_on" : "vehicleworks.msg.camera_lock_off"), true);
        }
        if (v.isAttendantSeated(mc.player)) {
            // cabin crew: no flight controls; crew panel (X) and announcements (K)
            Payloads.toServer(new Payloads.Input(v.getId(), 0, mc.player.getYRot(), mc.player.getXRot()));
            while (Keybinds.PANEL.consumeClick()) mc.setScreen(new CrewScreen(v));
            while (Keybinds.ANNOUNCE.consumeClick()) mc.setScreen(new AnnouncementScreen(v));
            for (var k : new net.minecraft.client.KeyMapping[]{Keybinds.ENGINES, Keybinds.GEAR, Keybinds.FLAPS, Keybinds.ARM, Keybinds.ASSIST, Keybinds.MOUSE_AIM,
                    Keybinds.PARKING, Keybinds.MENU, Keybinds.AUTOPILOT, Keybinds.HUD_MODE, Keybinds.HUD_TOGGLE, Keybinds.COPILOT}) while (k.consumeClick()) {}
            while (Keybinds.VOICE.consumeClick()) VoiceCallouts.toggle();
            return;
        }
        if (passenger) {
            // a passenger flies nothing: no flight inputs, no cockpit keys; X adjusts the seat
            Payloads.toServer(new Payloads.Input(v.getId(), 0, mc.player.getYRot(), mc.player.getXRot()));
            while (Keybinds.PANEL.consumeClick()) {
                if (v.seatClassOf(mc.player) > 0) mc.setScreen(new SeatScreen(v));
                else mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("vehicleworks.msg.passenger_no_controls"), true);
            }
            for (var k : new net.minecraft.client.KeyMapping[]{Keybinds.ENGINES, Keybinds.GEAR, Keybinds.FLAPS, Keybinds.ARM, Keybinds.ASSIST, Keybinds.MOUSE_AIM,
                    Keybinds.PARKING, Keybinds.MENU, Keybinds.AUTOPILOT, Keybinds.HUD_MODE, Keybinds.HUD_TOGGLE, Keybinds.ANNOUNCE, Keybinds.COPILOT}) while (k.consumeClick()) {}
            while (Keybinds.VOICE.consumeClick()) VoiceCallouts.toggle();
            return;
        }
        while (Keybinds.ANNOUNCE.consumeClick()) mc.setScreen(new AnnouncementScreen(v));
        while (Keybinds.COPILOT.consumeClick()) action(v, Payloads.ACT_COPILOT);
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
            if (o.keyUse.isDown() && panelClickMask <= 0) fire |= 2;
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
        if (panelClickMask > 0 && !mc.options.keyUse.isDown()) panelClickMask--;
        while (Keybinds.VOICE.consumeClick()) VoiceCallouts.toggle();
        while (Keybinds.HUD_TOGGLE.consumeClick()) {
            boolean vis = !Config.CLIENT.hudVisible.get();
            Config.CLIENT.hudVisible.set(vis);
            Config.CLIENT_SPEC.save();
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(vis ? "vehicleworks.hud.shown" : "vehicleworks.hud.hidden"), true);
        }
        while (Keybinds.HUD_MODE.consumeClick()) {
            int m = (Config.CLIENT.hudMode.get() + 1) % 4;
            Config.CLIENT.hudMode.set(m);
            Config.CLIENT.hudVisible.set(true);
            Config.CLIENT_SPEC.save();
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("vehicleworks.hud.mode." + m), true);
        }
        VoiceCallouts.tick(v);
        while (Keybinds.PANEL.consumeClick()) {
            boolean has = false;
            for (var st : v.structure.blocks().values())
                if (com.vehicleworks.vehicle.VehicleBuilder.typeOf(st) == com.vehicleworks.component.ComponentType.COCKPIT_PANEL) { has = true; break; }
            // the overhead panel block shows the switches in 3D; the screen works in every cockpit
            Minecraft.getInstance().setScreen(new PanelScreen(v));
        }
    }

    private static int panelClickMask;

    /**
     * Seated right-click on the overhead cockpit panel: finds which of its 6 buttons the
     * crosshair points at (the plate is 3 x 2 blocks under the ceiling) and presses it.
     */
    static boolean clickPanel(VehicleEntity v) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition(1f), look = mc.player.getViewVector(1f);
        org.joml.Vector3d a = v.worldToLocal(eye.x, eye.y, eye.z, new org.joml.Vector3d());
        org.joml.Vector3d d = v.worldDirToLocal(look.x, look.y, look.z, new org.joml.Vector3d());
        if (Math.abs(d.y) < 1e-4) return false;
        double best = 4.5;
        int button = -1;
        for (long k : v.structure.blockEntities().keySet()) {
            var st = v.structure.get(k);
            if (com.vehicleworks.vehicle.VehicleBuilder.typeOf(st) != com.vehicleworks.component.ComponentType.COCKPIT_PANEL) continue;
            int bx = net.minecraft.core.BlockPos.getX(k), by = net.minecraft.core.BlockPos.getY(k), bz = net.minecraft.core.BlockPos.getZ(k);
            double t = (by + 14.5 / 16.0 - a.y) / d.y;
            if (t <= 0 || t > best) continue;
            double rx = a.x + d.x * t - bx - 0.5, rz = a.z + d.z * t - bz - 0.5; // relative to block centre
            // back into model space (model front = north)
            net.minecraft.core.Direction f = com.vehicleworks.vehicle.VehicleBuilder.facing(st);
            double mx, mz;
            switch (f) {
                case SOUTH -> { mx = -rx; mz = -rz; }
                case EAST -> { mx = rz; mz = -rx; }
                case WEST -> { mx = -rz; mz = rx; }
                default -> { mx = rx; mz = rz; }
            }
            if (mx < -1.5 || mx > 1.5 || mz < -1.0 || mz > 1.0) continue;
            int col = Math.max(0, Math.min(CockpitRenderer.COLS - 1, (int) Math.floor((1.5 - mx) / CockpitRenderer.CW)));
            int row = Math.max(0, Math.min(CockpitRenderer.ROWS - 1, (int) Math.floor((mz + 1.0) / CockpitRenderer.CD)));
            CockpitRenderer.Ctl c = CockpitRenderer.at(col, row);
            if (c == null) continue;
            best = t;
            button = c.id();
        }
        if (button < 0) return false;
        PanelScreen.pressSwitch(v, button);
        return true;
    }

    /**
     * Seated pilot: right-click on the centre pedestal (top surface) or the FCU (front face).
     * Standing players reach the pedestal through the normal block interaction and the FCU here.
     */
    static boolean clickCockpit(VehicleEntity v) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition(1f), look = mc.player.getViewVector(1f);
        org.joml.Vector3d a0 = v.worldToLocal(eye.x, eye.y, eye.z, new org.joml.Vector3d());
        org.joml.Vector3d d0 = v.worldDirToLocal(look.x, look.y, look.z, new org.joml.Vector3d());
        double best = 4.0;
        int action = -1, arg = -1;
        for (long k : v.structure.blockEntities().keySet()) {
            var st = v.structure.get(k);
            var ct = com.vehicleworks.vehicle.VehicleBuilder.typeOf(st);
            if (ct != com.vehicleworks.component.ComponentType.CENTER_PEDESTAL && ct != com.vehicleworks.component.ComponentType.FCU_PANEL) continue;
            double rot = Math.toRadians(CockpitRenderer.facingRot(com.vehicleworks.vehicle.VehicleBuilder.facing(st)));
            org.joml.Vector3d a = new org.joml.Vector3d(a0).sub(net.minecraft.core.BlockPos.getX(k) + 0.5, net.minecraft.core.BlockPos.getY(k), net.minecraft.core.BlockPos.getZ(k) + 0.5).rotateY(rot);
            org.joml.Vector3d d = new org.joml.Vector3d(d0).rotateY(rot);
            if (ct == com.vehicleworks.component.ComponentType.CENTER_PEDESTAL) {
                if (Math.abs(d.y) < 1e-4) continue;
                double t = (com.vehicleworks.vehicle.CockpitLayout.PED_TOP - a.y) / d.y;
                if (t <= 0 || t > best) continue;
                int c = com.vehicleworks.vehicle.CockpitLayout.pedestalAt(a.x + d.x * t, a.z + d.z * t);
                if (c < 0) continue;
                best = t; action = Payloads.ACT_PEDESTAL; arg = c;
            } else {
                if (Math.abs(d.z) < 1e-4) continue;
                double t = (com.vehicleworks.vehicle.CockpitLayout.FCU_FACE_Z - a.z) / d.z;
                if (t <= 0 || t > best) continue;
                int c = com.vehicleworks.vehicle.CockpitLayout.fcuAt(a.x + d.x * t, a.y + d.y * t);
                if (c < 0) continue;
                best = t; action = Payloads.ACT_FCU; arg = c;
            }
        }
        if (action < 0) return false;
        Payloads.toServer(new Payloads.Action(v.getId(), action, arg));
        CockpitRenderer.pressed(v, -1);
        return true;
    }

    /** A passenger right-clicks one of the buttons on its seat's arm rest. */
    static boolean clickSeatButton(VehicleEntity v) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        Long seat = v.seats.get(mc.player.getUUID());
        if (seat == null) return false;
        var st = v.structure.get(seat);
        var ct = com.vehicleworks.vehicle.VehicleBuilder.typeOf(st);
        if (ct == null || ct.seatClass() == 0) return false;
        int cls = ct.seatClass();
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition(1f), look = mc.player.getViewVector(1f);
        org.joml.Vector3d a = v.worldToLocal(eye.x, eye.y, eye.z, new org.joml.Vector3d());
        org.joml.Vector3d d = v.worldDirToLocal(look.x, look.y, look.z, new org.joml.Vector3d());
        // into the seat frame (the renderer turns the seat by -facing about its floor centre)
        double rot = Math.toRadians(CockpitRenderer.facingRot(com.vehicleworks.vehicle.VehicleBuilder.facing(st)));
        a.sub(net.minecraft.core.BlockPos.getX(seat) + 0.5, net.minecraft.core.BlockPos.getY(seat), net.minecraft.core.BlockPos.getZ(seat) + 0.5).rotateY(rot);
        d.rotateY(rot);
        a.z -= CabinRenderer.slideOffset(v.cabin.get((long) seat));
        if (Math.abs(d.y) < 1e-4) return false;
        java.util.List<Integer> btns = CabinRenderer.buttons(cls);
        int best = -1;
        double bestD = 0.032;
        for (int i = 0; i < btns.size(); i++) {
            float[] p = CabinRenderer.buttonPos(cls, i);
            double t = (p[1] - a.y) / d.y;
            if (t <= 0 || t > 3) continue;
            double hx = a.x + d.x * t - p[0], hz = a.z + d.z * t - p[2];
            double dist = Math.max(Math.abs(hx), Math.abs(hz));
            if (dist < bestD) { bestD = dist; best = btns.get(i); }
        }
        if (best < 0) return false;
        CabinRenderer.pressed(v, seat, best);
        Payloads.toServer(new Payloads.Action(v.getId(), Payloads.ACT_CABIN, best));
        return true;
    }

    static void action(VehicleEntity v, int a) {
        Payloads.toServer(new Payloads.Action(v.getId(), a, 0));
    }

    /** Vehicle block interaction (use / break / pick) and fire buttons while seated. */
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (ridden() != null) {
            if (e.isUseItem() && ridden().isPassengerSeated(mc.player)) {
                // passenger: press the arm-rest button under the crosshair, or open the seat controls
                if (!clickSeatButton(ridden()) && ridden().seatClassOf(mc.player) > 0) mc.setScreen(new SeatScreen(ridden()));
                e.setCanceled(true);
                e.setSwingHand(false);
                return;
            }
            if (e.isUseItem() && !ridden().isPassengerSeated(mc.player) && !ridden().isAttendantSeated(mc.player) && clickCockpit(ridden())) {
                e.setCanceled(true);
                e.setSwingHand(false);
                panelClickMask = 6;
                return;
            }
            if (e.isUseItem() && !ridden().isPassengerSeated(mc.player) && clickPanel(ridden())) {
                e.setCanceled(true);
                e.setSwingHand(false);
                panelClickMask = 6;
                return;
            }
            if (e.isAttack() || e.isUseItem()) {
                e.setCanceled(true);
                e.setSwingHand(false);
            }
            return;
        }
        if (e.isUseItem()) {
            VehicleEntity near = null;
            double bd = 36;
            for (VehicleEntity ov : com.vehicleworks.vehicle.VehicleRegistry.all(mc.level)) { double d = ov.distanceToSqr(mc.player); if (d < bd * bd) { bd = Math.sqrt(d); near = ov; } }
            if (near != null && clickCockpit(near)) { e.setCanceled(true); e.setSwingHand(false); return; }
        }
        if (!(mc.hitResult instanceof VehicleHitResult hit)) return;
        int action = e.isAttack() ? 1 : (e.isUseItem() ? 0 : 2);
        var lh = hit.localHit();
        Payloads.toServer(new Payloads.Interact(hit.vehicle().getId(), hit.block(), hit.face().get3DDataValue(),
                (float) lh.x, (float) lh.y, (float) lh.z, action, e.getHand() == InteractionHand.MAIN_HAND));
        e.setCanceled(true);
        e.setSwingHand(action != 2);
    }

    /** camera locked to the vehicle (toggled with the camera lock key while riding) */
    public static boolean cameraLock;

    /** Roll the first-person camera with the aircraft. */
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        VehicleEntity v = ridden();
        if (v == null) return;
        if (cameraLock) {
            // locked: look along the vehicle (seat direction) and follow its pitch, heading and bank; the mouse does not move the view
            float partial = (float) e.getPartialTick();
            var q = v.renderOrientation(partial, new org.joml.Quaterniond());
            Long seat = v.seats.get(Minecraft.getInstance().player.getUUID());
            net.minecraft.core.Direction sf = seat != null ? com.vehicleworks.vehicle.VehicleBuilder.facing(v.structure.get(seat)) : net.minecraft.core.Direction.NORTH;
            if (sf.getAxis() == net.minecraft.core.Direction.Axis.Y) sf = net.minecraft.core.Direction.NORTH;
            var fw = q.transform(new org.joml.Vector3d(sf.getStepX(), 0, sf.getStepZ()));
            var up = q.transform(new org.joml.Vector3d(0, 1, 0));
            float yaw = (float) Math.toDegrees(Math.atan2(-fw.x, fw.z));
            float pitch = (float) -Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, fw.y))));
            net.minecraft.core.Direction sr = sf.getClockWise();
            var right = q.transform(new org.joml.Vector3d(sr.getStepX(), 0, sr.getStepZ()));
            double roll = Math.toDegrees(Math.atan2(-right.y, up.y));
            var p = Minecraft.getInstance().player;
            p.setYRot(yaw); p.yRotO = yaw; p.setXRot(pitch); p.xRotO = pitch; p.setYHeadRot(yaw);
            e.setYaw(yaw);
            e.setPitch(pitch);
            if (Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON) e.setRoll((float) roll);
            return;
        }
        float[] sh = GroundFx.offsets(v);
        if (sh[0] != 0 || sh[1] != 0 || sh[2] != 0) {
            e.setPitch(e.getPitch() + sh[0]);
            e.setYaw(e.getYaw() + sh[1]);
            e.setRoll(e.getRoll() + sh[2]);
        }
        if (!Config.CLIENT.cameraRoll.get()) return;
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
