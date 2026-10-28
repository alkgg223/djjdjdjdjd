package com.gunsmod.net;

import com.gunsmod.block.LauncherBlockEntity;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.gun.GunLogic;
import com.gunsmod.item.GunItem;
import com.gunsmod.item.RemoteItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-side packet handlers (run on the server thread). */
public final class ServerNet {
    private ServerNet() {}

    public static void shoot(Net.Shoot p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer sp && Float.isFinite(p.yaw()) && Float.isFinite(p.pitch())) {
            GunLogic.playerShoot(sp, p.yaw(), p.pitch(), p.seed(), p.aiming());
        }
    }

    public static void reload(Net.Reload p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer sp) {
            ItemStack st = sp.getMainHandItem();
            if (st.getItem() instanceof GunItem) GunLogic.startReload(sp, st);
        }
    }

    public static void turret(Net.Turret p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer sp && sp.getVehicle() instanceof JeepEntity jeep && jeep.isGunner(sp)) {
            jeep.setGunnerFiring(p.firing());
        }
    }

    public static void vehicleAction(Net.VehicleAction p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp) || !(sp.getVehicle() instanceof JeepEntity jeep)) return;
        switch (p.action()) {
            case 0 -> jeep.switchSeat(sp);
            case 1 -> jeep.honk();
            case 2 -> jeep.orderDriveTo(sp, p.x(), p.y(), p.z());
            default -> { }
        }
    }

    public static void launchAction(Net.LaunchAction p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        boolean near = sp.blockPosition().distSqr(p.station()) < 12 * 12;
        boolean linked = RemoteItem.linkedTo(sp.getMainHandItem(), p.station()) || RemoteItem.linkedTo(sp.getOffhandItem(), p.station());
        if (!near && !linked) return;
        // make sure the station chunk is loaded (remote launches from far away)
        sp.serverLevel().getChunkAt(p.station());
        BlockEntity be = sp.serverLevel().getBlockEntity(p.station());
        if (!(be instanceof LauncherBlockEntity station)) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new Net.LaunchStatus(p.station(), false, -1, false, 0, 0, 0, 0, false));
            return;
        }
        switch (p.action()) {
            case 1 -> station.setTarget(p.x(), p.y(), p.z(), sp);
            case 2 -> station.arm(sp);
            case 3 -> station.launch(sp);
            case 4 -> station.disarm();
            default -> { }
        }
        station.sendStatus(sp, false);
    }
}
