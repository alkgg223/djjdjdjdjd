package com.vehicleworks.client;

import com.vehicleworks.component.ComponentType;
import com.vehicleworks.network.Payloads;
import com.vehicleworks.physics.propulsion.EngineSim;
import com.vehicleworks.physics.propulsion.EngineType;
import com.vehicleworks.physics.sim.ThrusterUnit;
import com.vehicleworks.vehicle.ControlProfile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The vehicle engineering interface: overview, mass distribution with centre of mass,
 * engines, aerodynamics, fuel, damage, weapons and controls help. In the world (before
 * assembly) it offers the Assemble button.
 */
public class EngineeringScreen extends Screen {
    private static final String[] TABS = {"overview", "mass", "engines", "aero", "fuel", "damage", "weapons", "controls"};
    private final int vehicleId;
    private final long pos;
    private final boolean world;
    private final CompoundTag coreConfig;
    private int tab;
    private int ticks;

    public EngineeringScreen(int vehicleId, long pos, CompoundTag data) {
        super(Component.translatable("vehicleworks.ui.engineering"));
        this.vehicleId = vehicleId;
        this.pos = pos;
        this.world = data.getBoolean("World");
        this.coreConfig = data.getCompound("Config");
    }

    private CompoundTag info() {
        return ClientProxy.lastInfoVehicle == vehicleId ? ClientProxy.lastInfo : new CompoundTag();
    }

    @Override
    protected void init() {
        clearWidgets();
        if (world) {
            addRenderableWidget(Button.builder(Component.translatable("vehicleworks.ui.assemble"), b -> {
                Payloads.toServer(new Payloads.WorldAction(pos, Payloads.WACT_ASSEMBLE));
                onClose();
            }).bounds(width / 2 - 100, height / 2 + 40, 200, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("vehicleworks.ui.core_settings"), b -> {
                CompoundTag d = new CompoundTag();
                d.putString("Type", ComponentType.VEHICLE_CORE.id);
                d.put("Config", coreConfig.copy());
                minecraft.setScreen(new ComponentScreen(-1, pos, d));
            }).bounds(width / 2 - 100, height / 2 + 64, 200, 20).build());
            return;
        }
        int tw = Math.min(70, (width - 20) / TABS.length);
        int x0 = width / 2 - tw * TABS.length / 2;
        for (int i = 0; i < TABS.length; i++) {
            int idx = i;
            Button b = Button.builder(Component.translatable("vehicleworks.tab." + TABS[i]), btn -> { tab = idx; init(); })
                    .bounds(x0 + i * tw, 20, tw - 2, 18).build();
            b.active = i != tab;
            addRenderableWidget(b);
        }
        int by = height - 26;
        if (tab == 0) {
            addRenderableWidget(Button.builder(Component.translatable("vehicleworks.ui.disassemble"), b -> {
                Payloads.toServer(new Payloads.Action(vehicleId, Payloads.ACT_DISASSEMBLE, 0));
                onClose();
            }).bounds(width / 2 - 154, by, 100, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("vehicleworks.ui.profile_cycle"), b -> {
                int cur = info().getInt("Profile");
                Payloads.toServer(new Payloads.Action(vehicleId, Payloads.ACT_PROFILE, (cur + 1) % ControlProfile.values().length));
            }).bounds(width / 2 - 50, by, 100, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("vehicleworks.ui.core_settings"), b -> {
                CompoundTag d = new CompoundTag();
                d.putString("Type", ComponentType.VEHICLE_CORE.id);
                d.put("Config", coreConfig.copy());
                minecraft.setScreen(new ComponentScreen(vehicleId, 0L, d));
            }).bounds(width / 2 + 54, by, 100, 20).build());
        } else if (tab == 2) {
            addRenderableWidget(Button.builder(Component.translatable("vehicleworks.ui.start_all"), b ->
                    Payloads.toServer(new Payloads.Action(vehicleId, Payloads.ACT_ENGINES_START, 0))).bounds(width / 2 - 104, by, 100, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("vehicleworks.ui.stop_all"), b ->
                    Payloads.toServer(new Payloads.Action(vehicleId, Payloads.ACT_ENGINES_STOP, 0))).bounds(width / 2 + 4, by, 100, 20).build());
            ListTag engines = info().getList("Engines", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(10, engines.size()); i++) {
                int idx = i;
                addRenderableWidget(Button.builder(Component.literal("⏻"), b ->
                        Payloads.toServer(new Payloads.Action(vehicleId, Payloads.ACT_ENGINE_TOGGLE, idx))).bounds(width / 2 + 170, 54 + i * 16, 18, 14).build());
            }
        } else if (tab == 5) {
            addRenderableWidget(Button.builder(Component.translatable("vehicleworks.ui.repair"), b ->
                    Payloads.toServer(new Payloads.Action(vehicleId, Payloads.ACT_REPAIR, 0))).bounds(width / 2 - 50, by, 100, 20).build());
        } else if (tab == 4 && minecraft.player.getAbilities().instabuild) {
            addRenderableWidget(Button.builder(Component.translatable("vehicleworks.ui.refuel_creative"), b ->
                    Payloads.toServer(new Payloads.Action(vehicleId, Payloads.ACT_REFUEL, 0))).bounds(width / 2 - 50, by, 100, 20).build());
        } else if (tab == 6) {
            addRenderableWidget(Button.builder(Component.translatable("vehicleworks.ui.toggle_arm"), b ->
                    Payloads.toServer(new Payloads.Action(vehicleId, Payloads.ACT_ARM, 0))).bounds(width / 2 - 50, by, 100, 20).build());
        } else if (tab == 7) {
            int bx = width / 2 - 154;
            addRenderableWidget(Button.builder(Component.translatable("vehicleworks.ui.toggle_assist"), b -> act(Payloads.ACT_ASSIST)).bounds(bx, by, 100, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("vehicleworks.ui.toggle_mouse_aim"), b -> act(Payloads.ACT_MOUSE_AIM)).bounds(bx + 104, by, 100, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("vehicleworks.ui.toggle_gear"), b -> act(Payloads.ACT_GEAR)).bounds(bx + 208, by, 100, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("vehicleworks.ui.flaps"), b -> act(Payloads.ACT_FLAPS)).bounds(bx, by - 24, 100, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("vehicleworks.ui.parking"), b -> act(Payloads.ACT_PARKING)).bounds(bx + 104, by - 24, 100, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("vehicleworks.ui.throttle_idle"), b ->
                    Payloads.toServer(new Payloads.Action(vehicleId, Payloads.ACT_THROTTLE_SET, 0))).bounds(bx + 208, by - 24, 100, 20).build());
        }
    }

    private void act(int a) {
        Payloads.toServer(new Payloads.Action(vehicleId, a, 0));
    }

    @Override
    public void tick() {
        super.tick();
        if (!world && ticks++ % 10 == 0) Payloads.toServer(new Payloads.Action(vehicleId, Payloads.ACT_REQUEST_INFO, 0));
        if (!world && tab == 2 && ticks % 20 == 0) init();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        g.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF);
        if (world) {
            int y = height / 2 - 60;
            for (int i = 1; i <= 6; i++) {
                g.drawCenteredString(font, Component.translatable("vehicleworks.help.assemble." + i), width / 2, y, 0xCCCCCC);
                y += 12;
            }
            return;
        }
        CompoundTag t = info();
        if (t.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("vehicleworks.ui.loading"), width / 2, height / 2, 0xAAAAAA);
            return;
        }
        int x = width / 2 - 180, y = 48;
        switch (tab) {
            case 0 -> overview(g, t, x, y);
            case 1 -> mass(g, t, x, y);
            case 2 -> engines(g, t, x, y);
            case 3 -> aero(g, t, x, y);
            case 4 -> fuel(g, t, x, y);
            case 5 -> damage(g, t, x, y);
            case 6 -> weapons(g, t, x, y);
            case 7 -> controls(g, x, y);
        }
    }

    private int line(GuiGraphics g, String key, int x, int y, Object... args) {
        g.drawString(font, Component.translatable(key, args), x, y, 0xE0E0E0);
        return y + 11;
    }

    private static String f1(double v) { return String.format("%.1f", v); }
    private static String f2(double v) { return String.format("%.2f", v); }
    private static String f0(double v) { return String.format("%.0f", v); }

    private void overview(GuiGraphics g, CompoundTag t, int x, int y) {
        y = line(g, "vehicleworks.info.blocks", x, y, t.getInt("Blocks"));
        y = line(g, "vehicleworks.info.mass", x, y, f0(t.getDouble("Mass")), f0(t.getDouble("DryMass")));
        y = line(g, "vehicleworks.info.size", x, y, f0(t.getDouble("SizeX")), f0(t.getDouble("SizeY")), f0(t.getDouble("SizeZ")));
        y = line(g, "vehicleworks.info.com", x, y, f2(t.getDouble("ComX")), f2(t.getDouble("ComY")), f2(t.getDouble("ComZ")));
        y = line(g, "vehicleworks.info.inertia", x, y, f0(t.getDouble("Ixx")), f0(t.getDouble("Iyy")), f0(t.getDouble("Izz")));
        y = line(g, "vehicleworks.info.profile", x, y, Component.translatable("vehicleworks.profile." + ControlProfile.byIndex(t.getInt("Profile")).id()),
                Component.translatable("vehicleworks.profile." + ControlProfile.byIndex(t.getInt("Detected")).id()));
        y = line(g, "vehicleworks.info.speed", x, y, f1(t.getDouble("Airspeed") * 3.6), f1(t.getDouble("AoA")));
        y = line(g, "vehicleworks.info.forces", x, y, f1(t.getDouble("Lift") / 1000), f1(t.getDouble("Drag") / 1000), f1(t.getDouble("Thrust") / 1000), f1(t.getDouble("Buoyancy") / 1000));
        y += 6;
        y = line(g, "vehicleworks.info.physics", x, y, t.getInt("CollisionBoxes"), t.getInt("Probes"), t.getInt("WingStrips"), t.getInt("FluidElements"));
        y = line(g, "vehicleworks.info.perf", x, y, t.getInt("Vehicles"), t.getInt("Substeps"), t.getInt("Contacts"), f2(t.getDouble("StepMs")), t.getInt("TerrainBlocks"));
        line(g, t.getBoolean("Sleeping") ? "vehicleworks.info.sleeping" : "vehicleworks.info.awake", x, y);
    }

    private void mass(GuiGraphics g, CompoundTag t, int x, int y) {
        int gw = t.getInt("GridW"), gh = t.getInt("GridH");
        int[] grid = t.getIntArray("Grid");
        if (gw <= 0 || gh <= 0 || grid.length < gw * gh) return;
        int max = 1;
        for (int v : grid) max = Math.max(max, v);
        int cell = Math.max(2, Math.min(160 / Math.max(gw, gh), 12));
        int ox = width / 2 - gw * cell / 2, oy = y + 10;
        g.drawString(font, Component.translatable("vehicleworks.info.mass_map"), x, y, 0xE0E0E0);
        for (int j = 0; j < gh; j++)
            for (int i = 0; i < gw; i++) {
                int v = grid[j * gw + i];
                if (v <= 0) continue;
                float f = (float) Math.sqrt((double) v / max);
                int r = (int) (40 + 215 * f), gg = (int) (180 * (1 - f) + 40), b = (int) (220 * (1 - f));
                // forward is up on screen
                int sx = ox + i * cell, sy = oy + (gh - 1 - j) * cell;
                g.fill(sx, sy, sx + cell - 1, sy + cell - 1, 0xFF000000 | (r << 16) | (gg << 8) | b);
            }
        double cellM = t.getDouble("GridCell");
        double comR = t.getDouble("ComRight"), comF = t.getDouble("ComFwd");
        int cx = ox + (int) ((comR - t.getDouble("GridMinR")) / cellM * cell);
        int cy = oy + gh * cell - (int) ((comF - t.getDouble("GridMinF")) / cellM * cell);
        g.fill(cx - 4, cy, cx + 5, cy + 1, 0xFFFFFF00);
        g.fill(cx, cy - 4, cx + 1, cy + 5, 0xFFFFFF00);
        g.drawString(font, "CoM", cx + 5, cy - 9, 0xFFFF00);
        if (t.getDouble("WingArea") > 0) {
            int np = oy + gh * cell - (int) ((t.getDouble("NeutralPoint") - t.getDouble("GridMinF")) / cellM * cell);
            g.fill(ox - 6, np, ox + gw * cell + 6, np + 1, 0xFF40C0FF);
            g.drawString(font, Component.translatable("vehicleworks.info.np"), ox + gw * cell + 8, np - 4, 0x40C0FF);
        }
        g.drawString(font, "▲ " + Component.translatable("vehicleworks.info.forward").getString(), ox, oy - 10 + gh * cell + 14, 0xAAAAAA);
    }

    private void engines(GuiGraphics g, CompoundTag t, int x, int y) {
        ListTag engines = t.getList("Engines", Tag.TAG_COMPOUND);
        if (engines.isEmpty()) { line(g, "vehicleworks.info.no_engines", x, y); return; }
        g.drawString(font, Component.translatable("vehicleworks.info.engine_header"), x, y - 2, 0xAAAAAA);
        y += 8;
        for (int i = 0; i < Math.min(10, engines.size()); i++) {
            CompoundTag e = engines.getCompound(i);
            EngineType type = EngineType.byIndex(e.getInt("Type"));
            EngineSim.State st = EngineSim.State.values()[Math.floorMod(e.getInt("State"), 5)];
            ThrusterUnit.Device dev = ThrusterUnit.Device.values()[Math.floorMod(e.getInt("Device"), 5)];
            int c = switch (st) { case RUNNING -> 0x7CFF8A; case STARTING -> 0xFFC040; case FAILED -> 0xFF4040; default -> 0xAAAAAA; };
            String out = type.isShaft() ? f0(e.getDouble("Power") / 1000) + " kW" : f1(e.getDouble("Thrust") / 1000) + " kN";
            String s = String.format("%d %-9s %-9s %-8s %5.0f rpm %4.0f/%-4.0f°C dmg %3.0f%% %s fuel %.2fkg/s grp %d %s",
                    i + 1, type.id, Component.translatable("vehicleworks.device." + dev.name().toLowerCase()).getString(),
                    Component.translatable("vehicleworks.state." + st.name().toLowerCase()).getString(), e.getDouble("Rpm"), e.getDouble("Temp"), e.getDouble("MaxTemp"),
                    e.getDouble("Damage") * 100, out, e.getDouble("Fuel"), e.getInt("Group"), e.getString("Failure").isEmpty() ? "" : "(" + e.getString("Failure") + ")");
            g.drawString(font, s, x - 20, y + 2 + i * 16, c);
        }
        int yy = y + Math.min(10, engines.size()) * 16 + 10;
        line(g, "vehicleworks.info.systems", x, yy, t.getBoolean("Starter") ? "✔" : "✘", t.getInt("Pumps"), t.getInt("Hydraulics"));
    }

    private void aero(GuiGraphics g, CompoundTag t, int x, int y) {
        y = line(g, "vehicleworks.info.wing_area", x, y, f1(t.getDouble("WingArea")), f0(t.getDouble("WingLoading")));
        y = line(g, "vehicleworks.info.clmax", x, y, f2(t.getDouble("ClMax")));
        y = line(g, "vehicleworks.info.stall_speed", x, y, f0(t.getDouble("StallSpeed") * 3.6));
        y = line(g, "vehicleworks.info.thrust", x, y, f1(t.getDouble("StaticThrust") / 1000), f2(t.getDouble("ThrustToWeight")));
        double sm = t.getDouble("StaticMargin");
        int c = sm < 0 ? 0xFF4040 : (sm < 0.05 ? 0xFFC040 : 0x7CFF8A);
        g.drawString(font, Component.translatable("vehicleworks.info.static_margin", String.format("%.0f%%", sm * 100)), x, y, c);
        y += 11;
        g.drawString(font, Component.translatable(sm < 0 ? "vehicleworks.info.unstable_hint" : "vehicleworks.info.stable_hint"), x, y, 0xAAAAAA);
        y += 14;
        if (t.getDouble("RotorOffset") > 0) {
            double ro = t.getDouble("RotorOffset");
            g.drawString(font, Component.translatable("vehicleworks.info.rotor_offset", f2(ro)), x, y, ro > 1.0 ? 0xFFC040 : 0x7CFF8A);
            y += 11;
        }
        y = line(g, "vehicleworks.info.live_aero", x, y, f1(t.getDouble("Lift") / 1000), f1(t.getDouble("Drag") / 1000), f1(t.getDouble("AoA")));
        y = line(g, "vehicleworks.info.gas", x, y, f0(t.getDouble("GasVolume")), f1(t.getDouble("GasLift") / 1000));
        line(g, "vehicleworks.info.float", x, y, f0(t.getDouble("Displacement")), t.getInt("Pockets"), f2(t.getDouble("FloatMargin")));
    }

    private void fuel(GuiGraphics g, CompoundTag t, int x, int y) {
        double fuel = t.getDouble("Fuel"), cap = t.getDouble("FuelCap"), flow = t.getDouble("FuelFlow");
        y = line(g, "vehicleworks.info.fuel", x, y, f0(fuel), f0(cap), t.getInt("Tanks"));
        y = line(g, "vehicleworks.info.fuel_flow", x, y, f2(flow), flow > 1e-4 ? f0(fuel / flow / 60) : "∞");
        y = line(g, "vehicleworks.info.fuel_supply", x, y, f0(t.getDouble("FuelSupply") * 100), t.getInt("Pumps"));
        if (t.getDouble("BallastCap") > 0) y = line(g, "vehicleworks.info.ballast", x, y, f0(t.getDouble("Ballast")), f0(t.getDouble("BallastCap")));
        int bw = 300;
        g.fill(x, y + 6, x + bw, y + 18, 0x66000000);
        g.fill(x, y + 6, x + (int) (bw * (cap > 0 ? Mth.clamp(fuel / cap, 0, 1) : 0)), y + 18, 0xFFFFB040);
        g.drawString(font, Component.translatable("vehicleworks.info.refuel_hint"), x, y + 24, 0xAAAAAA);
    }

    private void damage(GuiGraphics g, CompoundTag t, int x, int y) {
        float integ = t.getFloat("Integrity");
        y = line(g, "vehicleworks.info.integrity", x, y, String.format("%.0f%%", integ * 100), t.getInt("DamagedBlocks"));
        y = line(g, "vehicleworks.info.stress", x, y, String.format("%.0f%%", t.getDouble("Stress") * 100));
        int bw = 300;
        g.fill(x, y + 4, x + bw, y + 16, 0x66000000);
        g.fill(x, y + 4, x + (int) (bw * integ), y + 16, integ > 0.6 ? 0xFF60E070 : (integ > 0.3 ? 0xFFFFC040 : 0xFFFF4040));
        ListTag engines = t.getList("Engines", Tag.TAG_COMPOUND);
        y += 24;
        for (int i = 0; i < engines.size(); i++) {
            double d = engines.getCompound(i).getDouble("Damage");
            if (d > 0.01) y = line(g, "vehicleworks.info.engine_damage", x, y, i + 1, String.format("%.0f%%", d * 100));
        }
        line(g, "vehicleworks.info.repair_hint", x, y + 6);
    }

    private void weapons(GuiGraphics g, CompoundTag t, int x, int y) {
        y = line(g, t.getBoolean("Armed") ? "vehicleworks.info.armed" : "vehicleworks.info.safe", x, y);
        y = line(g, "vehicleworks.info.rcu", x, y, t.getInt("Rcus"), t.getInt("AmmoBoxes"));
        ListTag ws = t.getList("Weapons", Tag.TAG_COMPOUND);
        for (int i = 0; i < ws.size(); i++) {
            CompoundTag w = ws.getCompound(i);
            String ch = w.getInt("Channel") >= 0 ? String.valueOf(w.getInt("Channel") + 1) : "-";
            int c = !w.getBoolean("Mounted") ? 0xFF4040 : 0xE0E0E0;
            g.drawString(font, Component.translatable("vehicleworks.info.weapon_line", i + 1, Component.translatable("block.vehicleworks." + w.getString("Type")),
                    ch, w.getInt("Ammo"), w.getInt("Mag"), w.getInt("Reserve"),
                    Component.translatable(w.getBoolean("Mounted") ? (w.getBoolean("Turret") ? "vehicleworks.info.turret" : "vehicleworks.info.mounted") : "vehicleworks.info.unmounted")), x, y, c);
            y += 11;
        }
        line(g, "vehicleworks.info.weapons_hint", x, y + 6);
    }

    private void controls(GuiGraphics g, int x, int y) {
        for (int i = 1; i <= 14; i++) {
            g.drawString(font, Component.translatable("vehicleworks.help.controls." + i), x, y, 0xD0D0D0);
            y += 11;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
