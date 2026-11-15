package com.vehicleworks.network;

import com.vehicleworks.block.ComponentBlock;
import com.vehicleworks.block.ComponentBlockEntity;
import com.vehicleworks.block.WingOrientation;
import com.vehicleworks.component.ComponentType;
import com.vehicleworks.component.PropertyDef;
import com.vehicleworks.item.AmmoItem;
import com.vehicleworks.item.EngineeringWrenchItem;
import com.vehicleworks.item.FuelItem;
import com.vehicleworks.physics.aero.Atmosphere;
import com.vehicleworks.physics.propulsion.EngineSim;
import com.vehicleworks.physics.sim.TankUnit;
import com.vehicleworks.physics.sim.ThrusterUnit;
import com.vehicleworks.physics.sim.VehicleSimulation;
import com.vehicleworks.registry.ModBlockEntities;
import com.vehicleworks.registry.ModSounds;
import com.vehicleworks.vehicle.*;
import com.vehicleworks.weapon.WeaponSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** Server-side handling of player actions on components, in the world and on vehicles. */
public final class ServerActions {
    private ServerActions() {}

    public static final int SCREEN_CONFIG = 0, SCREEN_ENGINEERING = 1, SCREEN_RCU = 2, SCREEN_PANEL = 3;

    private static void msg(ServerPlayer p, String key, Object... args) {
        p.displayClientMessage(Component.translatable(key, args), true);
    }

    // ================================================================== world components

    public static boolean useComponentInWorld(ServerPlayer sp, BlockPos pos, BlockState state, ComponentBlock block) {
        ComponentType t = block.type;
        BlockEntity be = sp.level().getBlockEntity(pos);
        if (!(be instanceof ComponentBlockEntity cbe)) return false;
        switch (t) {
            case VEHICLE_CORE -> {
                CompoundTag d = new CompoundTag();
                d.putBoolean("World", true);
                d.put("Config", cbe.config().copy());
                Payloads.toPlayer(sp, new Payloads.OpenScreen(SCREEN_ENGINEERING, -1, pos.asLong(), d));
                return true;
            }
            case REMOTE_CONTROL_UNIT -> {
                Payloads.toPlayer(sp, new Payloads.OpenScreen(SCREEN_RCU, -1, pos.asLong(), rcuScreenData(cbe.data(), null, pos.asLong(), sp.level())));
                return true;
            }
            case FUEL_TANK -> {
                msg(sp, "vehicleworks.msg.fuel_level", String.format("%.0f", cbe.data().getDouble("Fuel")), String.format("%.0f", VehicleBuilder.FUEL_TANK_CAPACITY));
                return true;
            }
            case AMMO_BOX -> {
                msg(sp, "vehicleworks.msg.ammo_box", ammoSummary(cbe.data().getCompound("Ammo")));
                return true;
            }
            default -> {
                if (!t.isConfigurable()) return false;
                CompoundTag d = new CompoundTag();
                d.putString("Type", t.id);
                d.put("Config", cbe.config().copy());
                if (state.hasProperty(ComponentBlock.PLANE)) d.putInt("Plane", state.getValue(ComponentBlock.PLANE).ordinal());
                Payloads.toPlayer(sp, new Payloads.OpenScreen(SCREEN_CONFIG, -1, pos.asLong(), d));
                return true;
            }
        }
    }

    public static boolean useItemOnComponentInWorld(ServerPlayer sp, BlockPos pos, BlockState state, ComponentBlock block, ItemStack stack, InteractionHand hand) {
        BlockEntity be = sp.level().getBlockEntity(pos);
        if (!(be instanceof ComponentBlockEntity cbe)) return false;
        CompoundTag data = cbe.data().copy();
        if (applyItem(sp, block.type, data, stack)) {
            cbe.setData(data);
            return true;
        }
        return false;
    }

    /** Fuel, ammunition loading. Returns true if the item was used. */
    private static boolean applyItem(ServerPlayer sp, ComponentType type, CompoundTag data, ItemStack stack) {
        if (stack.getItem() instanceof FuelItem fuel && type == ComponentType.FUEL_TANK) {
            double cur = data.getDouble("Fuel");
            double cap = VehicleBuilder.FUEL_TANK_CAPACITY;
            if (cur >= cap - 1) { msg(sp, "vehicleworks.msg.tank_full"); return true; }
            int used = 0;
            while (used < stack.getCount() && cur + fuel.kg * 0.5 < cap) { cur = Math.min(cap, cur + fuel.kg); used++; if (!sp.isShiftKeyDown()) break; }
            if (!sp.getAbilities().instabuild) stack.shrink(used);
            data.putDouble("Fuel", cur);
            sp.level().playSound(null, sp.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.8f, 1.2f);
            msg(sp, "vehicleworks.msg.fuel_level", String.format("%.0f", cur), String.format("%.0f", cap));
            return true;
        }
        if (stack.getItem() instanceof AmmoItem ammo && type == ComponentType.AMMO_BOX) {
            CompoundTag a = data.getCompound("Ammo");
            int n = sp.isShiftKeyDown() ? stack.getCount() : 1;
            a.putInt(ammo.kind.name(), a.getInt(ammo.kind.name()) + ammo.rounds * n);
            data.put("Ammo", a);
            if (!sp.getAbilities().instabuild) stack.shrink(n);
            sp.level().playSound(null, sp.blockPosition(), ModSounds.RELOAD.get(), SoundSource.PLAYERS, 0.8f, 1.1f);
            msg(sp, "vehicleworks.msg.ammo_box", ammoSummary(a));
            return true;
        }
        if (stack.getItem() instanceof AmmoItem ammo && type.isWeapon()) {
            var spec = com.vehicleworks.weapon.WeaponSpec.of(type);
            if (spec == null || spec.ammo != ammo.kind) { msg(sp, "vehicleworks.msg.wrong_ammo"); return true; }
            int mag = data.getInt("Mag");
            int add = Math.min(spec.magazine - mag, ammo.rounds);
            if (add <= 0) { msg(sp, "vehicleworks.msg.mag_full"); return true; }
            data.putInt("Mag", mag + add);
            if (!sp.getAbilities().instabuild) stack.shrink(1);
            sp.level().playSound(null, sp.blockPosition(), ModSounds.RELOAD.get(), SoundSource.PLAYERS, 0.8f, 1f);
            msg(sp, "vehicleworks.msg.loaded", mag + add, spec.magazine);
            return true;
        }
        return false;
    }

    private static Component ammoSummary(CompoundTag a) {
        return Component.literal(a.getInt("BULLET") + " / " + a.getInt("SHELL") + " / " + a.getInt("MISSILE") + " / " + a.getInt("BOMB"));
    }

    public static boolean wrenchOnWorldBlock(ServerPlayer sp, BlockPos pos, ItemStack wrench) {
        BlockState st = sp.level().getBlockState(pos);
        if (!(st.getBlock() instanceof ComponentBlock cb)) return false;
        if (!sp.isShiftKeyDown()) return useComponentInWorld(sp, pos, st, cb);
        if (cb.type == ComponentType.REMOTE_CONTROL_UNIT) {
            CompoundTag sel = EngineeringWrenchItem.selection(wrench);
            if (sel.isEmpty() || !sel.getString("Vehicle").isEmpty()) { msg(sp, "vehicleworks.msg.select_first"); return true; }
            BlockPos target = BlockPos.of(sel.getLong("Pos"));
            if (target.distManhattan(pos) > 96) { msg(sp, "vehicleworks.msg.link_far"); return true; }
            BlockEntity be = sp.level().getBlockEntity(pos);
            if (be instanceof ComponentBlockEntity cbe) {
                CompoundTag d = cbe.data().copy();
                int ch = addLink(d, target.getX() - pos.getX(), target.getY() - pos.getY(), target.getZ() - pos.getZ());
                cbe.setData(d);
                EngineeringWrenchItem.clearSelection(wrench);
                msg(sp, "vehicleworks.msg.linked", ch + 1);
                sp.level().playSound(null, pos, ModSounds.ARM.get(), SoundSource.BLOCKS, 0.8f, 1.4f);
            }
            return true;
        }
        if (isLinkable(cb.type)) {
            EngineeringWrenchItem.setSelection(wrench, "", pos, "block.vehicleworks." + cb.type.id);
            msg(sp, "vehicleworks.msg.selected", Component.translatable("block.vehicleworks." + cb.type.id));
            sp.level().playSound(null, pos, ModSounds.CLICK.get(), SoundSource.BLOCKS, 0.8f, 1.2f);
            return true;
        }
        if (st.hasProperty(ComponentBlock.PLANE)) {
            sp.level().setBlock(pos, st.setValue(ComponentBlock.PLANE, st.getValue(ComponentBlock.PLANE).next()), 3);
            return true;
        }
        return false;
    }

    public static boolean isLinkable(ComponentType t) {
        return t.isWeapon();
    }

    /** Append a weapon link to RCU data; the channel defaults to the next free one. */
    private static int addLink(CompoundTag data, int dx, int dy, int dz) {
        ListTag links = data.getList("Links", Tag.TAG_COMPOUND);
        for (int i = 0; i < links.size(); i++) {
            int[] o = links.getCompound(i).getIntArray("Off");
            if (o.length == 3 && o[0] == dx && o[1] == dy && o[2] == dz) return links.getCompound(i).getInt("Ch");
        }
        CompoundTag l = new CompoundTag();
        l.putIntArray("Off", new int[]{dx, dy, dz});
        int ch = Math.min(VehicleControls.CHANNELS - 1, links.size());
        l.putInt("Ch", ch);
        l.putInt("Mode", 0);
        links.add(l);
        data.put("Links", links);
        return ch;
    }

    private static CompoundTag rcuScreenData(CompoundTag data, VehicleEntity v, long rcuPos, net.minecraft.world.level.Level level) {
        CompoundTag d = new CompoundTag();
        ListTag links = data.getList("Links", Tag.TAG_COMPOUND).copy();
        for (int i = 0; i < links.size(); i++) {
            CompoundTag l = links.getCompound(i);
            int[] o = l.getIntArray("Off");
            if (o.length != 3) continue;
            BlockPos target = BlockPos.of(rcuPos).offset(o[0], o[1], o[2]);
            BlockState st = v != null ? v.structure.get(target.asLong()) : level.getBlockState(target);
            l.putString("Name", st.getBlock().getDescriptionId());
            l.putBoolean("Valid", st.getBlock() instanceof ComponentBlock cb && isLinkable(cb.type));
        }
        d.put("Links", links);
        return d;
    }

    // ================================================================== packets

    private static VehicleEntity vehicle(ServerPlayer sp, int id, double reach) {
        Entity e = sp.level().getEntity(id);
        if (!(e instanceof VehicleEntity v)) return null;
        if (sp.getVehicle() == v) return v;
        AABB bb = v.getBoundingBox().inflate(reach);
        return bb.contains(sp.getEyePosition()) ? v : null;
    }

    public static void onInput(ServerPlayer sp, Payloads.Input p) {
        if (sp.getVehicle() instanceof VehicleEntity v && v.getId() == p.id()) v.receiveInput(sp, p.keys(), p.yaw(), p.pitch());
    }

    public static void onAction(ServerPlayer sp, Payloads.Action p) {
        VehicleEntity v = vehicle(sp, p.id(), 8);
        if (v == null || v.layout() == null) return;
        VehicleSimulation sim = v.simulation();
        VehicleControls c = v.controls;
        boolean isPilot = v.pilot() == sp || sp.getVehicle() == v;
        switch (p.action()) {
            case Payloads.ACT_ENGINES_START -> {
                if (!isPilot && p.arg() != Payloads.ARG_PANEL) return;
                if (!c.battery) { msg(sp, "vehicleworks.panel.battery_off"); return; }
                boolean any = false;
                for (ThrusterUnit t : sim.thrusters) if (t.engine.enabled) { t.engine.requestStart(); any = true; }
                if (any) v.level().playSound(null, v.getX(), v.getY(), v.getZ(), ModSounds.ENGINE_START.get(), SoundSource.NEUTRAL, 1.5f, 1f);
                if (!any) msg(sp, "vehicleworks.msg.no_engines");
                else if (!sim.hasStarter && sim.thrusters.stream().anyMatch(t -> t.engine.type.needsStarter)) msg(sp, "vehicleworks.msg.need_starter");
                else if (sim.fuelAmount <= 0 && com.vehicleworks.Config.SERVER.requireFuel.get()) msg(sp, "vehicleworks.msg.no_fuel");
                else msg(sp, "vehicleworks.msg.engines_starting");
            }
            case Payloads.ACT_ENGINES_STOP -> {
                if (!isPilot && p.arg() != Payloads.ARG_PANEL) return;
                for (ThrusterUnit t : sim.thrusters) t.engine.requestStop();
                msg(sp, "vehicleworks.msg.engines_stopping");
            }
            case Payloads.ACT_ENGINE_TOGGLE -> {
                if (p.arg() < 0 || p.arg() >= sim.thrusters.size()) return;
                EngineSim e = sim.thrusters.get(p.arg()).engine;
                if (e.state == EngineSim.State.OFF || e.state == EngineSim.State.STOPPING) {
                    if (!c.battery) { msg(sp, "vehicleworks.panel.battery_off"); return; }
                    e.requestStart();
                }
                else e.requestStop();
            }
            case Payloads.ACT_GEAR -> {
                if (!isPilot && p.arg() != Payloads.ARG_PANEL) return;
                c.gearDown = !c.gearDown;
                v.level().playSound(null, v.getX(), v.getY(), v.getZ(), ModSounds.GEAR_MOTOR.get(), SoundSource.NEUTRAL, 1f, 1f);
                msg(sp, c.gearDown ? "vehicleworks.msg.gear_down" : "vehicleworks.msg.gear_up");
            }
            case Payloads.ACT_FLAPS -> {
                if (!isPilot) return;
                c.flaps = c.flaps >= 0.99 ? 0 : Math.min(1, c.flaps + 1.0 / 3.0);
                msg(sp, "vehicleworks.msg.flaps", Math.round(c.flaps * 100));
            }
            case Payloads.ACT_BATTERY -> {
                c.battery = !c.battery;
                v.level().playSound(null, v.getX(), v.getY(), v.getZ(), ModSounds.CLICK.get(), SoundSource.NEUTRAL, 1f, c.battery ? 1.3f : 0.8f);
            }
            case Payloads.ACT_LIGHTS -> {
                c.lights = !c.lights;
                v.level().playSound(null, v.getX(), v.getY(), v.getZ(), ModSounds.CLICK.get(), SoundSource.NEUTRAL, 1f, c.lights ? 1.3f : 0.8f);
            }
            case Payloads.ACT_EMERGENCY_TEST -> emergencyTest(sp, v);
            case Payloads.ACT_AUTOPILOT -> {
                if (!isPilot) return;
                int a = p.arg();
                c.autopilot = (a & 1) != 0;
                c.apHeading = Math.floorMod((a >> 1) & 511, 360);
                c.apAltitude = ((a >> 10) & 16383) - 2048;
                if (c.autopilot) c.mouseAim = false;
                msg(sp, c.autopilot ? "vehicleworks.msg.autopilot_on" : "vehicleworks.msg.autopilot_off", (int) c.apHeading, (int) c.apAltitude);
            }
            case Payloads.ACT_FLAPS_SET -> { if (isPilot) c.flaps = Math.max(0, Math.min(100, p.arg())) / 100.0; }
            case Payloads.ACT_THROTTLE_SET -> {
                if (!isPilot) return;
                double t = Math.max(0, Math.min(100, p.arg())) / 100.0;
                c.throttle = t;
                c.shipThrottle = t;
            }
            case Payloads.ACT_ARM -> {
                if (!isPilot && !v.isGunner(sp)) return;
                c.armed = !c.armed;
                v.level().playSound(null, v.getX(), v.getY(), v.getZ(), ModSounds.ARM.get(), SoundSource.NEUTRAL, 1f, c.armed ? 1.2f : 0.8f);
                msg(sp, c.armed ? "vehicleworks.msg.armed" : "vehicleworks.msg.safe");
            }
            case Payloads.ACT_ASSIST -> {
                if (!isPilot) return;
                c.assist = !c.assist;
                msg(sp, c.assist ? "vehicleworks.msg.assist_on" : "vehicleworks.msg.assist_off");
            }
            case Payloads.ACT_MOUSE_AIM -> {
                if (!isPilot) return;
                c.mouseAim = !c.mouseAim;
                msg(sp, c.mouseAim ? "vehicleworks.msg.mouse_aim_on" : "vehicleworks.msg.mouse_aim_off");
            }
            case Payloads.ACT_AFTERBURNER -> { if (isPilot) c.afterburner = !c.afterburner; }
            case Payloads.ACT_PARKING -> {
                if (!isPilot) return;
                c.parkingBrake = !c.parkingBrake;
                msg(sp, c.parkingBrake ? "vehicleworks.msg.parking_on" : "vehicleworks.msg.parking_off");
            }
            case Payloads.ACT_DISASSEMBLE -> {
                var r = VehicleAssembler.disassemble(v);
                sp.displayClientMessage(r.message(), true);
            }
            case Payloads.ACT_REPAIR -> repair(sp, v);
            case Payloads.ACT_PROFILE -> {
                long core = BlockPos.asLong(0, 0, 0);
                CompoundTag be = v.structure.blockEntity(core);
                if (be == null) return;
                CompoundTag cfg = be.getCompound(ComponentBlockEntity.CONFIG);
                ComponentType.VEHICLE_CORE.property("profile").set(cfg, p.arg());
                be.put(ComponentBlockEntity.CONFIG, cfg);
                v.requestRebuild();
            }
            case Payloads.ACT_OPEN_ENGINEERING -> {
                CompoundTag d = new CompoundTag();
                d.putBoolean("World", false);
                d.put("Config", VehicleBuilder.config(v.structure, BlockPos.asLong(0, 0, 0)).copy());
                Payloads.toPlayer(sp, new Payloads.OpenScreen(SCREEN_ENGINEERING, v.getId(), BlockPos.asLong(0, 0, 0), d));
                Payloads.toPlayer(sp, new Payloads.Info(v.getId(), EngineeringInfo.build(v)));
            }
            case Payloads.ACT_REQUEST_INFO -> Payloads.toPlayer(sp, new Payloads.Info(v.getId(), EngineeringInfo.build(v)));
            case Payloads.ACT_REFUEL -> {
                if (!sp.getAbilities().instabuild) return;
                for (TankUnit t : sim.tanks) if (t.kind == TankUnit.Kind.FUEL) t.amount = t.capacity;
                v.writeRuntimeToStructure();
                msg(sp, "vehicleworks.msg.refuelled");
            }
            default -> {}
        }
    }

    private static void repair(ServerPlayer sp, VehicleEntity v) {
        var dm = v.structure.damageMap();
        if (dm.isEmpty() && v.simulation().thrusters.stream().allMatch(t -> t.engine.damage <= 0)) { msg(sp, "vehicleworks.msg.no_damage"); return; }
        int needed = Math.max(1, (int) Math.ceil(dm.values().doubleStream().sum() / 60.0));
        boolean creative = sp.getAbilities().instabuild;
        int have = 0;
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(net.minecraft.world.item.Items.IRON_INGOT)) have += inv.getItem(i).getCount();
        int use = creative ? needed : Math.min(needed, have);
        if (use <= 0) { msg(sp, "vehicleworks.msg.need_iron", needed); return; }
        if (!creative) {
            int left = use;
            for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.is(net.minecraft.world.item.Items.IRON_INGOT)) continue;
                int t = Math.min(left, s.getCount());
                s.shrink(t);
                left -= t;
            }
        }
        double fraction = (double) use / needed;
        var keys = dm.keySet().toLongArray();
        for (long k : keys) {
            float d = dm.get(k) * (float) (1 - fraction);
            v.structure.setDamage(k, d < 1 ? 0 : d);
        }
        for (ThrusterUnit t : v.simulation().thrusters) t.engine.repair(fraction);
        for (var w : v.simulation().wings) w.damageFactor = Math.min(1, w.damageFactor + fraction);
        v.requestRebuild();
        v.level().playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.8f, 1.2f);
        msg(sp, "vehicleworks.msg.repaired", Math.round(fraction * 100), use);
    }

    public static void onConfigure(ServerPlayer sp, Payloads.Configure p) {
        CompoundTag incoming = p.tag();
        if (incoming == null) return;
        if (p.vehicleId() < 0) {
            BlockPos pos = BlockPos.of(p.pos());
            if (sp.distanceToSqr(Vec3.atCenterOf(pos)) > 100) return;
            BlockState st = sp.level().getBlockState(pos);
            if (!(sp.level().getBlockEntity(pos) instanceof ComponentBlockEntity cbe) || !(st.getBlock() instanceof ComponentBlock cb)) return;
            if (p.isData()) {
                if (cb.type != ComponentType.REMOTE_CONTROL_UNIT) return;
                CompoundTag d = cbe.data().copy();
                d.put("Links", sanitizeLinks(incoming));
                cbe.setData(d);
            } else {
                cbe.setConfig(sanitizeConfig(cb.type, cbe.config(), incoming));
                if (incoming.contains("_plane") && st.hasProperty(ComponentBlock.PLANE)) {
                    sp.level().setBlock(pos, st.setValue(ComponentBlock.PLANE, WingOrientation.values()[Math.floorMod(incoming.getInt("_plane"), 3)]), 3);
                }
            }
            return;
        }
        VehicleEntity v = vehicle(sp, p.vehicleId(), 8);
        if (v == null) return;
        long key = p.pos();
        BlockState st = v.structure.get(key);
        if (!(st.getBlock() instanceof ComponentBlock cb)) return;
        CompoundTag be = v.structure.blockEntity(key);
        if (be == null) be = defaultBeTag(st);
        if (p.isData()) {
            if (cb.type != ComponentType.REMOTE_CONTROL_UNIT) return;
            CompoundTag d = be.getCompound(ComponentBlockEntity.DATA);
            d.put("Links", sanitizeLinks(incoming));
            be.put(ComponentBlockEntity.DATA, d);
        } else {
            be.put(ComponentBlockEntity.CONFIG, sanitizeConfig(cb.type, be.getCompound(ComponentBlockEntity.CONFIG), incoming));
            if (incoming.contains("_plane") && st.hasProperty(ComponentBlock.PLANE)) {
                st = st.setValue(ComponentBlock.PLANE, WingOrientation.values()[Math.floorMod(incoming.getInt("_plane"), 3)]);
            }
        }
        v.setBlockLocal(key, st, be);
        sp.level().playSound(null, sp.blockPosition(), ModSounds.CLICK.get(), SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    /** Cockpit panel "emergency test": checks every system, reports in chat and sounds the alarm. */
    private static void emergencyTest(ServerPlayer sp, VehicleEntity v) {
        VehicleControls c = v.controls;
        VehicleSimulation s = v.simulation();
        long now = v.level().getGameTime();
        if (now < c.emergencyUntil) return;
        c.emergencyUntil = now + 100;
        v.level().playSound(null, v.getX(), v.getY(), v.getZ(), ModSounds.STALL_WARNING.get(), SoundSource.NEUTRAL, 1.5f, 0.8f);
        int problems = 0;
        java.util.List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable("vehicleworks.panel.report").withStyle(net.minecraft.ChatFormatting.GOLD));
        int engines = s.thrusters.size(), okEngines = 0;
        boolean needsStarter = false;
        for (ThrusterUnit t : s.thrusters) {
            if (t.engine.state != EngineSim.State.FAILED && t.engine.damage < 0.5) okEngines++;
            if (t.engine.type.needsStarter) needsStarter = true;
        }
        double fuelPct = s.fuelCapacity > 0 ? 100 * s.fuelAmount / s.fuelCapacity : 0;
        double integrity = 100 * v.integrity();
        boolean[] ok = {c.battery, !needsStarter || s.hasStarter, okEngines == engines && engines > 0, fuelPct > 10 || engines == 0,
                s.fuelSupply > 0.5 || engines == 0, s.hydraulicAuthority > 0.5, s.wheels.isEmpty() || c.gearDown, integrity > 80, true};
        Component[] names = {Component.translatable("vehicleworks.panel.c_battery"), Component.translatable("vehicleworks.panel.c_starter"),
                Component.translatable("vehicleworks.panel.c_engines", okEngines, engines), Component.translatable("vehicleworks.panel.c_fuel", (int) fuelPct),
                Component.translatable("vehicleworks.panel.c_pumps"), Component.translatable("vehicleworks.panel.c_hydraulics", (int) (100 * s.hydraulicAuthority)),
                Component.translatable("vehicleworks.panel.c_gear"), Component.translatable("vehicleworks.panel.c_structure", (int) integrity),
                Component.translatable("vehicleworks.panel.c_lights")};
        for (int i = 0; i < ok.length; i++) {
            boolean good = ok[i];
            if (i == 8) good = c.lights;
            if (!good && i != 8 && i != 6) problems++;
            lines.add(Component.literal(good ? "  [OK]  " : "  [!!]  ").withStyle(good ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED).append(names[i].copy().withStyle(net.minecraft.ChatFormatting.WHITE)));
        }
        lines.add(problems == 0 ? Component.translatable("vehicleworks.panel.pass").withStyle(net.minecraft.ChatFormatting.GREEN)
                : Component.translatable("vehicleworks.panel.fail", problems).withStyle(net.minecraft.ChatFormatting.RED));
        for (Component l : lines) sp.sendSystemMessage(l);
    }

    private static CompoundTag sanitizeConfig(ComponentType type, CompoundTag current, CompoundTag incoming) {
        CompoundTag out = current.copy();
        for (PropertyDef def : type.properties) {
            if (!incoming.contains(def.key)) continue;
            double v = switch (def.kind) {
                case BOOL -> incoming.getBoolean(def.key) ? 1 : 0;
                case INT, ENUM -> incoming.getInt(def.key);
                case NUMBER -> incoming.getDouble(def.key);
            };
            def.set(out, v);
        }
        return out;
    }

    private static ListTag sanitizeLinks(CompoundTag incoming) {
        ListTag in = incoming.getList("Links", Tag.TAG_COMPOUND);
        ListTag out = new ListTag();
        for (int i = 0; i < Math.min(32, in.size()); i++) {
            CompoundTag l = in.getCompound(i);
            int[] o = l.getIntArray("Off");
            if (o.length != 3 || Math.abs(o[0]) > 256 || Math.abs(o[1]) > 256 || Math.abs(o[2]) > 256) continue;
            CompoundTag c = new CompoundTag();
            c.putIntArray("Off", o);
            c.putInt("Ch", Math.max(0, Math.min(VehicleControls.CHANNELS - 1, l.getInt("Ch"))));
            c.putInt("Mode", Math.max(0, Math.min(2, l.getInt("Mode"))));
            out.add(c);
        }
        return out;
    }

    public static CompoundTag defaultBeTag(BlockState st) {
        if (!(st.getBlock() instanceof ComponentBlock cb)) return null;
        CompoundTag t = new CompoundTag();
        t.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(ModBlockEntities.COMPONENT.get()).toString());
        CompoundTag cfg = new CompoundTag();
        ComponentBlockEntity.fillDefaults(cb.type, cfg);
        t.put(ComponentBlockEntity.CONFIG, cfg);
        t.put(ComponentBlockEntity.DATA, new CompoundTag());
        return t;
    }

    public static void onWorldAction(ServerPlayer sp, Payloads.WorldAction p) {
        BlockPos pos = BlockPos.of(p.pos());
        if (sp.distanceToSqr(Vec3.atCenterOf(pos)) > 100) return;
        if (p.action() == Payloads.WACT_ASSEMBLE) {
            var r = VehicleAssembler.assemble((ServerLevel) sp.level(), pos);
            sp.displayClientMessage(r.message(), false);
        }
    }

    // ================================================================== interaction with vehicle blocks

    public static void onInteract(ServerPlayer sp, Payloads.Interact p) {
        Entity e = sp.level().getEntity(p.id());
        if (!(e instanceof VehicleEntity v) || v.layout() == null) return;
        long key = p.block();
        if (!v.structure.contains(key)) return;
        Vector3d hitW = v.localToWorld(p.hx(), p.hy(), p.hz(), new Vector3d());
        double reach = sp.blockInteractionRange() + 2.0;
        if (sp.getEyePosition().distanceToSqr(hitW.x, hitW.y, hitW.z) > reach * reach) return;
        InteractionHand hand = p.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack stack = sp.getItemInHand(hand);
        BlockState st = v.structure.get(key);
        Direction face = Direction.from3DDataValue(p.face());
        if (p.action() == 1) {
            breakOnVehicle(sp, v, key, st);
            return;
        }
        if (p.action() == 2) {
            if (sp.getAbilities().instabuild) sp.getInventory().add(new ItemStack(st.getBlock().asItem()));
            return;
        }
        ComponentType type = VehicleBuilder.typeOf(st);
        boolean sneaking = sp.isShiftKeyDown();
        // items applied to components
        if (type != null) {
            CompoundTag be = v.structure.blockEntity(key);
            if (be == null) be = defaultBeTag(st);
            CompoundTag data = be.getCompound(ComponentBlockEntity.DATA).copy();
            if (applyItem(sp, type, data, stack)) {
                be.put(ComponentBlockEntity.DATA, data);
                v.structure.setBlockEntity(key, be);
                v.requestRebuild();
                return;
            }
            if (stack.getItem() instanceof EngineeringWrenchItem && sneaking) {
                wrenchOnVehicle(sp, v, key, type, stack);
                return;
            }
        }
        boolean placing = stack.getItem() instanceof BlockItem && (sneaking || type == null || !isInteractive(type));
        if (!placing) {
            if (type != null && useComponentOnVehicle(sp, v, key, st, type)) return;
            if (type == null && useVanillaOnVehicle(sp, v, key, st)) return;
        }
        if (stack.getItem() instanceof BlockItem bi) placeOnVehicle(sp, v, key, face, bi, stack, hand, p);
    }

    private static boolean isInteractive(ComponentType t) {
        return t == ComponentType.PILOT_SEAT || t == ComponentType.PASSENGER_SEAT || t == ComponentType.VEHICLE_CORE
                || t == ComponentType.REMOTE_CONTROL_UNIT || t.isConfigurable() || t == ComponentType.FUEL_TANK || t == ComponentType.AMMO_BOX;
    }

    private static boolean useComponentOnVehicle(ServerPlayer sp, VehicleEntity v, long key, BlockState st, ComponentType t) {
        switch (t) {
            case PILOT_SEAT, PASSENGER_SEAT -> {
                if (!v.trySit(sp, key)) msg(sp, "vehicleworks.msg.seat_taken");
                return true;
            }
            case VEHICLE_CORE -> {
                onAction(sp, new Payloads.Action(v.getId(), Payloads.ACT_OPEN_ENGINEERING, 0));
                return true;
            }
            case COCKPIT_PANEL -> {
                Payloads.toPlayer(sp, new Payloads.OpenScreen(SCREEN_PANEL, v.getId(), key, new CompoundTag()));
                return true;
            }
            case REMOTE_CONTROL_UNIT -> {
                Payloads.toPlayer(sp, new Payloads.OpenScreen(SCREEN_RCU, v.getId(), key, rcuScreenData(VehicleBuilder.data(v.structure, key), v, key, sp.level())));
                return true;
            }
            case FUEL_TANK -> {
                int ti = v.layout().tankIndex.get(key);
                double amt = ti >= 0 ? v.simulation().tanks.get(ti).amount : 0;
                msg(sp, "vehicleworks.msg.fuel_level", String.format("%.0f", amt), String.format("%.0f", VehicleBuilder.FUEL_TANK_CAPACITY));
                return true;
            }
            case AMMO_BOX -> {
                msg(sp, "vehicleworks.msg.ammo_box", ammoSummary(VehicleBuilder.data(v.structure, key).getCompound("Ammo")));
                return true;
            }
            default -> {
                if (!t.isConfigurable()) return false;
                CompoundTag d = new CompoundTag();
                d.putString("Type", t.id);
                d.put("Config", VehicleBuilder.config(v.structure, key).copy());
                if (st.hasProperty(ComponentBlock.PLANE)) d.putInt("Plane", st.getValue(ComponentBlock.PLANE).ordinal());
                Payloads.toPlayer(sp, new Payloads.OpenScreen(SCREEN_CONFIG, v.getId(), key, d));
                return true;
            }
        }
    }

    private static void wrenchOnVehicle(ServerPlayer sp, VehicleEntity v, long key, ComponentType t, ItemStack wrench) {
        if (t == ComponentType.REMOTE_CONTROL_UNIT) {
            CompoundTag sel = EngineeringWrenchItem.selection(wrench);
            if (sel.isEmpty() || !sel.getString("Vehicle").equals(v.getStringUUID())) { msg(sp, "vehicleworks.msg.select_first"); return; }
            long target = sel.getLong("Pos");
            CompoundTag be = v.structure.blockEntity(key);
            if (be == null) be = defaultBeTag(v.structure.get(key));
            CompoundTag d = be.getCompound(ComponentBlockEntity.DATA);
            int ch = addLink(d, BlockPos.getX(target) - BlockPos.getX(key), BlockPos.getY(target) - BlockPos.getY(key), BlockPos.getZ(target) - BlockPos.getZ(key));
            be.put(ComponentBlockEntity.DATA, d);
            v.structure.setBlockEntity(key, be);
            v.requestRebuild();
            EngineeringWrenchItem.clearSelection(wrench);
            msg(sp, "vehicleworks.msg.linked", ch + 1);
            v.level().playSound(null, sp.blockPosition(), ModSounds.ARM.get(), SoundSource.PLAYERS, 0.8f, 1.4f);
            return;
        }
        if (isLinkable(t)) {
            EngineeringWrenchItem.setSelection(wrench, v.getStringUUID(), BlockPos.of(key), "block.vehicleworks." + t.id);
            msg(sp, "vehicleworks.msg.selected", Component.translatable("block.vehicleworks." + t.id));
            v.level().playSound(null, sp.blockPosition(), ModSounds.CLICK.get(), SoundSource.PLAYERS, 0.8f, 1.2f);
            return;
        }
        BlockState st = v.structure.get(key);
        if (st.hasProperty(ComponentBlock.PLANE)) v.setBlockLocal(key, st.setValue(ComponentBlock.PLANE, st.getValue(ComponentBlock.PLANE).next()), v.structure.blockEntity(key));
    }

    private static boolean useVanillaOnVehicle(ServerPlayer sp, VehicleEntity v, long key, BlockState st) {
        CompoundTag be = v.structure.blockEntity(key);
        // containers: open an inventory backed by the stored block entity data
        if (be != null && (st.getBlock() instanceof ChestBlock || st.getBlock() instanceof BarrelBlock || st.getBlock() instanceof ShulkerBoxBlock)) {
            SimpleContainer container = new SimpleContainer(27);
            var items = net.minecraft.core.NonNullList.withSize(27, ItemStack.EMPTY);
            net.minecraft.world.ContainerHelper.loadAllItems(be, items, sp.registryAccess());
            for (int i = 0; i < 27; i++) container.setItem(i, items.get(i));
            container.addListener(c -> {
                CompoundTag cur = v.structure.blockEntity(key);
                if (cur == null) return;
                var list = net.minecraft.core.NonNullList.withSize(27, ItemStack.EMPTY);
                for (int i = 0; i < 27; i++) list.set(i, c.getItem(i));
                cur.remove("Items");
                net.minecraft.world.ContainerHelper.saveAllItems(cur, list, sp.registryAccess());
            });
            sp.openMenu(new SimpleMenuProvider((id, inv, pl) -> ChestMenu.threeRows(id, inv, container), st.getBlock().getName()));
            return true;
        }
        if (st.hasProperty(BlockStateProperties.OPEN) && !(st.getBlock() instanceof DoorBlock && st.is(Blocks.IRON_DOOR))) {
            BlockState ns = st.cycle(BlockStateProperties.OPEN);
            v.setBlockLocal(key, ns, be);
            if (st.getBlock() instanceof DoorBlock) {
                long other = st.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER ? BlockPos.offset(key, Direction.UP) : BlockPos.offset(key, Direction.DOWN);
                BlockState os = v.structure.get(other);
                if (os.getBlock() == st.getBlock()) v.setBlockLocal(other, os.setValue(BlockStateProperties.OPEN, ns.getValue(BlockStateProperties.OPEN)), v.structure.blockEntity(other));
            }
            v.level().playSound(null, sp.blockPosition(), ns.getValue(BlockStateProperties.OPEN) ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 1f, 1f);
            return true;
        }
        if (st.hasProperty(BlockStateProperties.POWERED) && st.getBlock() instanceof LeverBlock) {
            v.setBlockLocal(key, st.cycle(BlockStateProperties.POWERED), be);
            v.level().playSound(null, sp.blockPosition(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 1f);
            return true;
        }
        return false;
    }

    private static void breakOnVehicle(ServerPlayer sp, VehicleEntity v, long key, BlockState st) {
        if (!sp.mayBuild()) return;
        if (key == BlockPos.asLong(0, 0, 0) && v.structure.size() > 1) { msg(sp, "vehicleworks.msg.core_break"); return; }
        if (!sp.getAbilities().instabuild) {
            ItemStack drop = new ItemStack(st.getBlock().asItem());
            CompoundTag be = v.structure.blockEntity(key);
            if (be != null && be.contains("Items")) {
                var items = net.minecraft.core.NonNullList.withSize(27, ItemStack.EMPTY);
                net.minecraft.world.ContainerHelper.loadAllItems(be, items, sp.registryAccess());
                for (ItemStack it : items) if (!it.isEmpty()) sp.drop(it, false);
            }
            if (!drop.isEmpty() && !sp.getInventory().add(drop)) sp.drop(drop, false);
        }
        Vector3d w = v.localToWorld(BlockPos.getX(key) + 0.5, BlockPos.getY(key) + 0.5, BlockPos.getZ(key) + 0.5, new Vector3d());
        ((ServerLevel) v.level()).levelEvent(2001, BlockPos.containing(w.x, w.y, w.z), Block.getId(st));
        v.removeBlockLocal(key);
        v.splitDisconnected();
    }

    private static void placeOnVehicle(ServerPlayer sp, VehicleEntity v, long key, Direction face, BlockItem bi, ItemStack stack,
                                       InteractionHand hand, Payloads.Interact p) {
        if (!sp.mayBuild()) return;
        if (v.structure.size() >= com.vehicleworks.Config.SERVER.maxBlocks.get()) { msg(sp, "vehicleworks.msg.too_many", com.vehicleworks.Config.SERVER.maxBlocks.get()); return; }
        long target = BlockPos.offset(key, face);
        if (v.structure.contains(target)) return;
        Block block = bi.getBlock();
        BlockState st = placementState(block, sp, v, face, p);
        if (st == null) return;
        // do not place inside entities
        Vector3d c = v.localToWorld(BlockPos.getX(target) + 0.5, BlockPos.getY(target) + 0.5, BlockPos.getZ(target) + 0.5, new Vector3d());
        AABB box = new AABB(c.x - 0.45, c.y - 0.45, c.z - 0.45, c.x + 0.45, c.y + 0.45, c.z + 0.45);
        if (!v.level().getEntities(v, box, en -> en instanceof net.minecraft.world.entity.LivingEntity && !en.isSpectator() && en.getVehicle() != v).isEmpty()) return;
        CompoundTag be = defaultBeTag(st);
        v.setBlockLocal(target, st, be);
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        var sound = st.getSoundType();
        v.level().playSound(null, c.x, c.y, c.z, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
        sp.swing(hand, true);
    }

    /** Placement state in the vehicle frame (look direction converted to local coordinates). */
    private static BlockState placementState(Block block, ServerPlayer sp, VehicleEntity v, Direction face, Payloads.Interact p) {
        BlockState st = block.defaultBlockState();
        Vec3 look = sp.getLookAngle();
        Vector3d ll = v.worldDirToLocal(look.x, look.y, look.z, new Vector3d());
        Direction lookDir = Direction.getNearest(ll.x, ll.y, ll.z);
        Direction horiz = Direction.getNearest(ll.x, 0, ll.z);
        if (horiz.getAxis() == Direction.Axis.Y) horiz = Direction.NORTH;
        boolean component = block instanceof ComponentBlock;
        if (block instanceof ComponentBlock.Wing) {
            Direction.Axis span = face.getAxis();
            Direction.Axis fwd = horiz.getAxis();
            Direction.Axis normal = span == Direction.Axis.Y ? (fwd == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X) : Direction.Axis.Y;
            if (sp.isShiftKeyDown()) normal = span == Direction.Axis.Y ? Direction.Axis.Y : (span == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
            return st.setValue(ComponentBlock.PLANE, WingOrientation.forNormal(normal));
        }
        if (st.hasProperty(BlockStateProperties.FACING)) {
            Direction d = component ? lookDir : lookDir.getOpposite();
            if (block instanceof ComponentBlock cb && cb.type == ComponentType.MAIN_ROTOR) d = face;
            if (component && sp.isShiftKeyDown()) d = d.getOpposite();
            st = st.setValue(BlockStateProperties.FACING, d);
        }
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction d = component || block instanceof StairBlock || block instanceof DoorBlock || block instanceof FenceGateBlock ? horiz : horiz.getOpposite();
            st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
        }
        if (st.hasProperty(BlockStateProperties.AXIS)) st = st.setValue(BlockStateProperties.AXIS, face.getAxis());
        if (st.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            double fy = p.hy() - Math.floor(p.hy());
            boolean top = face == Direction.DOWN || (face != Direction.UP && fy > 0.5);
            st = st.setValue(BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
        }
        if (st.hasProperty(BlockStateProperties.HALF) && block instanceof StairBlock) {
            st = st.setValue(BlockStateProperties.HALF, face == Direction.DOWN ? Half.TOP : Half.BOTTOM);
        }
        if (st.hasProperty(BlockStateProperties.ATTACH_FACE) || st.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return null; // multi-block / attached blocks are not supported for direct placement on vehicles
        }
        if (block instanceof TorchBlock && face != Direction.UP) return null;
        return st;
    }
}
