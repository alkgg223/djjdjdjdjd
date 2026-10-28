package com.gunsmod.net;

import com.gunsmod.block.LauncherBlockEntity;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.gun.GunLogic;
import com.gunsmod.item.GunItem;
import com.gunsmod.item.RemoteItem;
import com.gunsmod.item.RemoteLinks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
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
        ServerLevel level = sp.serverLevel();
        if (!p.remote()) {
            // a station opened by hand: only usable while standing next to it
            if (sp.blockPosition().distSqr(p.station()) > 12 * 12) return;
            station(sp, level, p.station(), p);
            sendStationStatus(sp, p.station(), false);
            return;
        }
        ItemStack remote = RemoteItem.held(sp);
        if (remote.isEmpty()) return;
        RemoteLinks links = RemoteItem.links(remote);
        switch (p.action()) {
            case Net.LaunchAction.SELECT -> RemoteItem.setLinks(remote, links.select(p.x()));
            case Net.LaunchAction.UNLINK -> RemoteItem.unlink(remote, p.station(), sp);
            case Net.LaunchAction.FIRE_ALL -> fireAll(sp, level, links);
            case Net.LaunchAction.TARGET_ALL, Net.LaunchAction.ARM_ALL, Net.LaunchAction.LAUNCH_ALL, Net.LaunchAction.DISARM_ALL -> {
                int n = 0;
                for (BlockPos pos : links.stations()) {
                    LauncherBlockEntity be = LauncherBlockEntity.at(level, pos);
                    if (be == null) continue;
                    int k = n;
                    boolean ok = switch (p.action()) {
                        case Net.LaunchAction.TARGET_ALL -> be.setTarget(p.x(), p.y(), p.z());
                        case Net.LaunchAction.ARM_ALL -> be.getState() == LauncherBlockEntity.IDLE && be.arm() == null;
                        case Net.LaunchAction.LAUNCH_ALL -> be.launch(sp, ripple(k));
                        default -> {
                            boolean armed = be.getState() == LauncherBlockEntity.ARMED;
                            be.disarm();
                            yield armed;
                        }
                    };
                    if (ok) n++;
                }
                String key = switch (p.action()) {
                    case Net.LaunchAction.TARGET_ALL -> "gunsmod.msg.salvo_target";
                    case Net.LaunchAction.ARM_ALL -> "gunsmod.msg.salvo_armed";
                    case Net.LaunchAction.LAUNCH_ALL -> "gunsmod.msg.salvo_launch";
                    default -> "gunsmod.msg.salvo_disarmed";
                };
                sp.displayClientMessage(Component.translatable(key, n), true);
            }
            default -> {
                if (links.contains(p.station())) station(sp, level, p.station(), p);
            }
        }
        sendRemoteStatus(sp, remote, false);
    }

    /** stagger big salvos a little: up to 20 launches in a 3 second ripple, then the next 20 together with them */
    private static int ripple(int k) {
        return (k % 20) * 3;
    }

    /** arm and launch every linked station that has a missile and a target */
    private static void fireAll(ServerPlayer sp, ServerLevel level, RemoteLinks links) {
        int launched = 0, noTarget = 0;
        for (BlockPos pos : links.stations()) {
            LauncherBlockEntity be = LauncherBlockEntity.at(level, pos);
            if (be == null || !be.hasMissile() || be.isBusy()) continue;
            if (!be.hasTarget()) {
                noTarget++;
                continue;
            }
            if (be.getState() == LauncherBlockEntity.IDLE && be.arm() != null) continue;
            if (be.launch(sp, ripple(launched))) launched++;
        }
        if (launched == 0) {
            sp.displayClientMessage(Component.translatable(noTarget > 0 ? "gunsmod.msg.fire_all_notarget" : "gunsmod.msg.fire_all_none", noTarget), true);
        } else {
            sp.displayClientMessage(noTarget > 0 ? Component.translatable("gunsmod.msg.fire_all_some", launched, noTarget)
                    : Component.translatable("gunsmod.msg.fire_all", launched), true);
        }
    }

    /** an action on one station */
    private static void station(ServerPlayer sp, ServerLevel level, BlockPos pos, Net.LaunchAction p) {
        LauncherBlockEntity be = LauncherBlockEntity.at(level, pos);
        if (be == null) return;
        be.keepLoaded();
        switch (p.action()) {
            case Net.LaunchAction.TARGET -> sp.displayClientMessage(be.setTarget(p.x(), p.y(), p.z())
                    ? Component.translatable("gunsmod.msg.target_set", p.x(), p.y(), p.z()) : Component.translatable("gunsmod.msg.station_busy"), true);
            case Net.LaunchAction.ARM -> {
                String err = be.arm();
                if (err != null) sp.displayClientMessage(Component.translatable(err), true);
            }
            case Net.LaunchAction.LAUNCH -> {
                if (be.launch(sp)) sp.displayClientMessage(Component.translatable("gunsmod.msg.launching"), true);
            }
            case Net.LaunchAction.DISARM -> be.disarm();
            default -> { }
        }
    }

    /** status of every station linked to the remote (keeps their chunks loaded while the screen is open) */
    public static void sendRemoteStatus(ServerPlayer sp, ItemStack remote, boolean open) {
        RemoteLinks links = RemoteItem.links(remote);
        List<Net.StationInfo> list = new ArrayList<>();
        for (BlockPos pos : links.stations()) {
            LauncherBlockEntity be = LauncherBlockEntity.at(sp.serverLevel(), pos);
            if (be != null) {
                be.keepLoaded();
                list.add(be.info());
            } else {
                list.add(Net.StationInfo.missing(pos));
            }
        }
        PacketDistributor.sendToPlayer(sp, new Net.LaunchStatus(list, links.selected(), true, open));
    }

    public static void sendStationStatus(ServerPlayer sp, BlockPos pos, boolean open) {
        LauncherBlockEntity be = LauncherBlockEntity.at(sp.serverLevel(), pos);
        PacketDistributor.sendToPlayer(sp, new Net.LaunchStatus(List.of(be != null ? be.info() : Net.StationInfo.missing(pos)), 0, false, open));
    }
}
