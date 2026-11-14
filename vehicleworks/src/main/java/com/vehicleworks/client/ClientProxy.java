package com.vehicleworks.client;

import com.vehicleworks.CommonProxy;
import com.vehicleworks.network.Payloads;
import com.vehicleworks.network.ServerActions;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleTelemetry;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import org.joml.Vector3d;

/** Client implementation of side-specific hooks and payload handlers. */
public final class ClientProxy extends CommonProxy {
    public static CompoundTag lastInfo = new CompoundTag();
    public static int lastInfoVehicle = -1;

    static VehicleEntity vehicle(int id) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        Entity e = level.getEntity(id);
        return e instanceof VehicleEntity v ? v : null;
    }

    public static VehicleClientState state(VehicleEntity v) {
        if (!(v.clientState instanceof VehicleClientState)) v.clientState = new VehicleClientState();
        return (VehicleClientState) v.clientState;
    }

    @Override
    public void initClientState(VehicleEntity v) {
        state(v);
    }

    @Override
    public void clientTick(VehicleEntity v) {
        VehicleClientState s = state(v);
        s.advance(v);
        s.telemetryAge++;
        // carry the local pilot's view yaw with the vehicle in keyboard mode
        LocalPlayer p = Minecraft.getInstance().player;
        Vector3d a = v.localDirToWorld(0, 0, 1, new Vector3d());
        double yaw = Math.toDegrees(Math.atan2(-a.x, a.z));
        if (p != null && p.getVehicle() == v && !Double.isNaN(s.lastYaw) && !(v.telemetry.mouseAim && VehicleHud.isFlying(v))) {
            float d = (float) net.minecraft.util.Mth.wrapDegrees(yaw - s.lastYaw);
            p.setYRot(p.getYRot() + d);
            p.yRotO += d;
        }
        s.lastYaw = yaw;
        VehicleEffects.tick(v, s);
    }

    @Override
    public void disposeClientState(VehicleEntity v) {
        if (v.clientState instanceof VehicleClientState s) {
            s.dispose();
            VehicleSounds.stop(s);
        }
    }

    @Override
    public void onState(Payloads.State p) {
        VehicleEntity v = vehicle(p.id());
        if (v == null) return;
        VehicleClientState s = state(v);
        s.addSnapshot(p.tick(), p.x(), p.y(), p.z(), p.qx(), p.qy(), p.qz(), p.qw(), p.vx(), p.vy(), p.vz(), p.wx(), p.wy(), p.wz());
        v.comLocal.set(p.cx(), p.cy(), p.cz());
    }

    @Override
    public void onTelemetry(Payloads.Telemetry p) {
        VehicleEntity v = vehicle(p.id());
        if (v == null) return;
        VehicleTelemetry t = new VehicleTelemetry();
        t.read(new FriendlyByteBuf(Unpooled.wrappedBuffer(p.data())));
        v.telemetry = t;
        state(v).telemetryAge = 0;
    }

    @Override
    public void onBlocks(Payloads.Blocks p) {
        VehicleEntity v = vehicle(p.id());
        if (v == null) return;
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.wrappedBuffer(p.data()));
        int n = b.readVarInt();
        for (int i = 0; i < n; i++) {
            long k = b.readLong();
            var st = Block.stateById(b.readVarInt());
            CompoundTag be = b.readBoolean() ? b.readNbt() : null;
            if (st.isAir()) v.structure.remove(k);
            else v.structure.put(k, st, be);
        }
        v.structure.damageMap().clear();
        int d = b.readVarInt();
        for (int i = 0; i < d; i++) v.structure.setDamage(b.readLong(), b.readFloat());
        v.structure.version++;
        v.setBoundingBox(v.getBoundingBox());
    }

    @Override
    public void onSeats(Payloads.Seats p) {
        VehicleEntity v = vehicle(p.id());
        if (v == null) return;
        VehicleClientState s = state(v);
        s.seatOf.clear();
        v.seats.clear();
        for (int i = 0; i < p.passengers().length; i++) {
            s.seatOf.put(p.passengers()[i], p.seats()[i]);
            var level = Minecraft.getInstance().level;
            Entity e = level != null ? level.getEntity(p.passengers()[i]) : null;
            if (e != null && p.seats()[i] != Long.MIN_VALUE) v.seats.put(e.getUUID(), p.seats()[i]);
        }
    }

    @Override
    public void onOpenScreen(Payloads.OpenScreen p) {
        Minecraft mc = Minecraft.getInstance();
        switch (p.screen()) {
            case ServerActions.SCREEN_CONFIG -> mc.setScreen(new ComponentScreen(p.vehicleId(), p.pos(), p.data()));
            case ServerActions.SCREEN_ENGINEERING -> mc.setScreen(new EngineeringScreen(p.vehicleId(), p.pos(), p.data()));
            case ServerActions.SCREEN_RCU -> mc.setScreen(new RemoteControlScreen(p.vehicleId(), p.pos(), p.data()));
            default -> {}
        }
    }

    @Override
    public void onInfo(Payloads.Info p) {
        lastInfo = p.info();
        lastInfoVehicle = p.vehicleId();
    }
}
