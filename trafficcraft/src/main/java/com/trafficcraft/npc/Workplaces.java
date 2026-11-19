package com.trafficcraft.npc;

import com.trafficcraft.ai.BorderControl;
import com.trafficcraft.ai.TrafficDriver;
import com.trafficcraft.ai.TrafficManager;
import com.trafficcraft.block.FacingBlock;
import com.trafficcraft.block.GenericBE;
import com.trafficcraft.net.OpenEditorPayload;
import com.trafficcraft.registry.TCRegistry;
import com.trafficcraft.road.NodeType;
import com.trafficcraft.road.RoadData;
import com.trafficcraft.road.RoadNetwork;
import com.trafficcraft.road.RoadNode;
import com.trafficcraft.vehicle.VehicleEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Jobs at gas stations and border checkpoints: NPC worker behaviour and the roles a player can take
 * (station owner, cashier, fuel attendant, border officer).
 */
public final class Workplaces {
    // =========================================================================== player side
    /** Right-click on a cashier counter / booth desk opens the role menu. */
    public static void use(ServerPlayer p, BlockPos pos, String role) {
        ServerLevel level = p.serverLevel();
        RoadNode n = station(level, pos, role.equals("officer") ? NodeType.BORDER : NodeType.GAS_STATION);
        ensureWorker(level, pos, role);
        CompoundTag t = new CompoundTag();
        t.putLong("pos", pos.asLong());
        t.putString("role", role);
        if (n != null) {
            t.putInt("node", n.id);
            t.putString("owner", n.cfg.getString("ownerName"));
            t.putFloat("price", n.cfg.contains("price") ? n.cfg.getFloat("price") : 0.05f);
            t.putInt("till", tillOf(level, n));
            t.putFloat("stock", stockOf(level, n));
            t.putString("worker", n.cfg.getString("playerRole_" + p.getUUID()));
        }
        PacketDistributor.sendToPlayer(p, new OpenEditorPayload("workplace", t));
    }

    /** Actions from the workplace screen. */
    public static void action(ServerPlayer p, CompoundTag d) {
        ServerLevel level = p.serverLevel();
        BlockPos pos = BlockPos.of(d.getLong("pos"));
        if (pos.distSqr(p.blockPosition()) > 100) return;
        String role = d.getString("role");
        RoadNode n = station(level, pos, role.equals("officer") ? NodeType.BORDER : NodeType.GAS_STATION);
        if (n == null) {
            p.displayClientMessage(Component.translatable("msg.trafficcraft.no_station"), false);
            return;
        }
        RoadNetwork net = RoadData.get(level);
        String act = d.getString("action");
        String owner = n.cfg.getString("owner");
        boolean isOwner = owner.equals(p.getUUID().toString()) || p.isCreative();
        switch (act) {
            case "claim" -> {
                if (owner.isEmpty() || p.isCreative()) {
                    n.cfg.putString("owner", p.getUUID().toString());
                    n.cfg.putString("ownerName", p.getName().getString());
                    p.displayClientMessage(Component.translatable("msg.trafficcraft.station_owned"), false);
                } else p.displayClientMessage(Component.translatable("msg.trafficcraft.station_taken", n.cfg.getString("ownerName")), false);
            }
            case "cashier", "attendant", "officer" -> {
                n.cfg.putString("playerRole_" + p.getUUID(), act);
                n.cfg.putString("player_" + act, p.getUUID().toString());
                if (act.equals("officer")) p.getInventory().placeItemBackInInventory(new ItemStack(TCRegistry.INSPECTION_STAMP.get()));
                p.displayClientMessage(Component.translatable("msg.trafficcraft.now_working", Component.translatable("role.trafficcraft." + act)), false);
            }
            case "stop" -> {
                String r = n.cfg.getString("playerRole_" + p.getUUID());
                n.cfg.remove("playerRole_" + p.getUUID());
                if (!r.isEmpty()) n.cfg.remove("player_" + r);
                p.displayClientMessage(Component.translatable("msg.trafficcraft.stopped_working"), false);
            }
            case "price_up", "price_down" -> {
                if (!isOwner) break;
                float pr = n.cfg.contains("price") ? n.cfg.getFloat("price") : 0.05f;
                pr = Math.max(0.01f, Math.min(1f, pr + (act.equals("price_up") ? 0.01f : -0.01f)));
                n.cfg.putFloat("price", Math.round(pr * 100) / 100f);
                for (GenericBE be : pumps(level, n)) {
                    be.data.putFloat("price", n.cfg.getFloat("price"));
                    be.sync();
                }
            }
            case "collect" -> {
                if (!isOwner) break;
                int sum = 0;
                for (GenericBE be : pumps(level, n)) {
                    sum += be.data.getInt("till");
                    be.data.putInt("till", 0);
                    be.sync();
                }
                sum += n.cfg.getInt("shopTill");
                n.cfg.putInt("shopTill", 0);
                if (sum > 0) p.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, sum));
                p.displayClientMessage(Component.translatable("msg.trafficcraft.till_collected", sum), false);
            }
            case "order_fuel" -> {
                if (!isOwner) break;
                if (!p.isCreative()) {
                    int have = 0;
                    for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(Items.EMERALD)) have += p.getInventory().getItem(i).getCount();
                    if (have < 10) {
                        p.displayClientMessage(Component.translatable("msg.trafficcraft.need_emeralds", 10), false);
                        break;
                    }
                    int left = 10;
                    for (int i = 0; i < p.getInventory().getContainerSize() && left > 0; i++) {
                        ItemStack s = p.getInventory().getItem(i);
                        if (s.is(Items.EMERALD)) {
                            int k = Math.min(left, s.getCount());
                            s.shrink(k);
                            left -= k;
                        }
                    }
                }
                var tank = com.trafficcraft.station.FuelStations.nearestTank(level, BlockPos.containing(n.pos()));
                if (tank != null) {
                    tank.data.putFloat("stock", (tank.data.contains("stock") ? tank.data.getFloat("stock") : 20000f) + 5000f);
                    tank.sync();
                }
                p.displayClientMessage(Component.translatable("msg.trafficcraft.fuel_delivered"), false);
            }
            default -> { }
        }
        net.changed();
    }

    /** A cashier sale: emeralds go to the station shop till. */
    public static void sale(PedestrianEntity cashier, int emeralds) {
        if (!(cashier.level() instanceof ServerLevel level) || cashier.workplace == null) return;
        RoadNode n = station(level, cashier.workplace, NodeType.GAS_STATION);
        if (n != null) {
            n.cfg.putInt("shopTill", n.cfg.getInt("shopTill") + emeralds);
            RoadData.get(level).dirty = true;
        }
    }

    public static void officerTalk(PedestrianEntity officer, Player p) {
        if (p.getVehicle() instanceof VehicleEntity v && officer.level() instanceof ServerLevel level) {
            BorderControl.Case c = TrafficManager.get(level).border.cases.get(v.getId());
            if (c != null && !c.approved) {
                RoadNode n = RoadData.get(level).nodes.get(c.node);
                boolean needPass = n == null || !n.cfg.contains("passport") || n.cfg.getBoolean("passport");
                if (!needPass || hasPassport(p)) {
                    TrafficManager.get(level).border.approve(level, n, c);
                    p.displayClientMessage(Component.translatable("msg.trafficcraft.border_ok"), false);
                } else p.displayClientMessage(Component.translatable("msg.trafficcraft.border_passport"), false);
                return;
            }
        }
        p.displayClientMessage(Component.translatable("npc.trafficcraft.officer_hello"), true);
    }

    public static boolean hasPassport(Player p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(TCRegistry.PASSPORT.get())) return true;
        return false;
    }

    /** Player officer uses the stamp on a waiting vehicle: approve (or sneak to send it to secondary inspection). */
    public static boolean stamp(Player p, VehicleEntity v) {
        if (!(v.level() instanceof ServerLevel level)) return false;
        BorderControl bc = TrafficManager.get(level).border;
        BorderControl.Case c = bc.cases.get(v.getId());
        if (c == null) {
            p.displayClientMessage(Component.translatable("msg.trafficcraft.not_waiting"), true);
            return false;
        }
        c.playerOfficer = true;
        RoadNode n = RoadData.get(level).nodes.get(c.node);
        if (p.isShiftKeyDown()) {
            c.denied = true;
            v.setFlag(VehicleEntity.F_HAZARD, true);
            p.displayClientMessage(Component.translatable("msg.trafficcraft.sent_secondary"), true);
        } else {
            bc.approve(level, n, c);
            p.displayClientMessage(Component.translatable("msg.trafficcraft.approved"), true);
            p.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, 1));
        }
        return true;
    }

    // =========================================================================== NPC workers
    public static RoadNode station(ServerLevel level, BlockPos pos, NodeType t) {
        RoadNode best = null;
        double bd = 48 * 48;
        for (RoadNode n : RoadData.get(level).nodesOfType(t)) {
            double d = n.pos().distanceToSqr(Vec3.atCenterOf(pos));
            if (d < bd) {
                bd = d;
                best = n;
            }
        }
        return best;
    }

    static List<GenericBE> pumps(ServerLevel level, RoadNode n) {
        List<GenericBE> out = new java.util.ArrayList<>();
        BlockPos c = BlockPos.containing(n.pos());
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-20, -3, -20), c.offset(20, 3, 20))) {
            if (level.getBlockEntity(q) instanceof GenericBE g && level.getBlockState(q).getBlock() instanceof com.trafficcraft.block.BEBlock b && b.kind.equals("fuel_pump")) out.add(g);
        }
        return out;
    }

    static int tillOf(ServerLevel level, RoadNode n) {
        int s = n.cfg.getInt("shopTill");
        for (GenericBE g : pumps(level, n)) s += g.data.getInt("till");
        return s;
    }

    static float stockOf(ServerLevel level, RoadNode n) {
        var t = com.trafficcraft.station.FuelStations.nearestTank(level, BlockPos.containing(n.pos()));
        return t == null ? -1 : t.data.contains("stock") ? t.data.getFloat("stock") : 20000f;
    }

    /** Spawns the NPC for a workplace if there is none yet. */
    public static PedestrianEntity ensureWorker(ServerLevel level, BlockPos pos, String role) {
        for (PedestrianEntity e : level.getEntitiesOfClass(PedestrianEntity.class, new AABB(pos).inflate(24))) {
            if (e.kind().equals(role) && pos.equals(e.workplace)) return e;
        }
        PedestrianEntity e = new PedestrianEntity(TCRegistry.PEDESTRIAN.get(), level);
        e.setKind(role);
        e.setSkin(PedSkins.forKind(role, level.random));
        e.workplace = pos.immutable();
        Vec3 home = home(level, e);
        e.moveTo(home.x, home.y, home.z, 0, 0);
        level.addFreshEntity(e);
        return e;
    }

    static Vec3 home(ServerLevel level, PedestrianEntity e) {
        BlockPos w = e.workplace;
        var st = level.getBlockState(w);
        Direction f = st.hasProperty(FacingBlock.FACING) ? st.getValue(FacingBlock.FACING) : Direction.NORTH;
        // stand behind the counter / desk (opposite the side facing the customers)
        BlockPos h = w.relative(f.getOpposite());
        // attendant waits beside the pump on the side away from the car bay
        if (e.kind().equals("attendant")) h = w.relative(f.getCounterClockWise()).relative(f.getOpposite());
        return Vec3.atBottomCenterOf(h);
    }

    public static void workTick(PedestrianEntity e, ServerLevel level, RoadNetwork net, PedBrain b) {
        if (e.workplace == null) return;
        Vec3 home = home(level, e);
        switch (e.kind()) {
            case "cashier" -> {
                RoadNode n = station(level, e.workplace, NodeType.GAS_STATION);
                boolean playerCashier = n != null && !n.cfg.getString("player_cashier").isEmpty();
                Vec3 stand = playerCashier ? home.add(0, 0, 0).add(Vec3.atLowerCornerOf(level.getBlockState(e.workplace).getValue(FacingBlock.FACING).getOpposite().getNormal()).scale(2)) : home;
                if (b.walkTo(stand, 0.5)) {
                    boolean customer = !level.getEntitiesOfClass(Player.class, new AABB(e.workplace).inflate(3.5)).isEmpty()
                            || level.getEntitiesOfClass(PedestrianEntity.class, new AABB(e.workplace).inflate(3), p -> !p.isWorker()).size() > 0;
                    e.setAnim(customer && !playerCashier ? PedestrianEntity.A_SCAN : PedestrianEntity.A_IDLE);
                    Direction f = level.getBlockState(e.workplace).getValue(FacingBlock.FACING);
                    e.setYRot(f.toYRot());
                    e.yBodyRot = f.toYRot();
                } else e.setAnim(PedestrianEntity.A_WALK);
            }
            case "attendant" -> {
                RoadNode n = station(level, e.workplace, NodeType.GAS_STATION);
                if (n != null && !n.cfg.getString("player_attendant").isEmpty()) {
                    if (b.walkTo(home, 0.5)) e.setAnim(PedestrianEntity.A_IDLE);
                    return;
                }
                VehicleEntity target = null;
                for (VehicleEntity v : level.getEntitiesOfClass(VehicleEntity.class, new AABB(e.workplace).inflate(18))) {
                    if (v.driver != null && v.driver.state == TrafficDriver.State.FUELING) {
                        target = v;
                        break;
                    }
                }
                if (target != null) {
                    float[] fl = target.spec().filler;
                    Vec3 cap = target.position().add(VehicleEntity.toWorldOffset(fl[0] * 1.35, 0, fl[2], target.getYRot()));
                    var pump = TrafficManager.get(level).fuel.pumpFor(target.getId());
                    if (b.walkTo(cap, 0.6)) {
                        e.setAnim(PedestrianEntity.A_REFUEL);
                        e.getLookControl().setLookAt(target);
                        if (pump != null && level.getBlockEntity(pump) instanceof GenericBE pb && !pb.data.getBoolean("attended")) {
                            pb.data.putBoolean("attended", true);
                            pb.sync();
                        }
                    } else e.setAnim(PedestrianEntity.A_WALK);
                } else if (b.walkTo(home, 0.5)) e.setAnim(PedestrianEntity.A_IDLE);
                else e.setAnim(PedestrianEntity.A_WALK);
            }
            case "officer" -> {
                RoadNode n = station(level, e.workplace, NodeType.BORDER);
                BorderControl bc = TrafficManager.get(level).border;
                BorderControl.Case job = null;
                if (n != null) for (BorderControl.Case c : bc.cases.values()) if (c.node == n.id && !c.approved && level.getEntity(c.vehicle) instanceof VehicleEntity) {
                    job = c;
                    break;
                }
                if (job != null && level.getEntity(job.vehicle) instanceof VehicleEntity v) {
                    // stand at the driver's window (left side)
                    Vec3 window = v.position().add(VehicleEntity.toWorldOffset(v.spec().width / 2 + 0.8, 0, v.spec().length * 0.1, v.getYRot()));
                    if (b.walkTo(window, 0.6)) {
                        job.officerPresent = true;
                        e.setAnim(PedestrianEntity.A_INSPECT);
                        e.getLookControl().setLookAt(v);
                    } else e.setAnim(PedestrianEntity.A_WALK);
                } else if (b.walkTo(home, 0.5)) e.setAnim(PedestrianEntity.A_IDLE);
                else e.setAnim(PedestrianEntity.A_WALK);
            }
            default -> { }
        }
    }

    public static boolean officerNear(ServerLevel level, RoadNode n) {
        return !level.getEntitiesOfClass(PedestrianEntity.class, new AABB(BlockPos.containing(n.pos())).inflate(30), p -> p.kind().equals("officer")).isEmpty();
    }

    public static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            return null;
        }
    }
}
