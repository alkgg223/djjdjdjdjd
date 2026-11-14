package com.vehicleworks.client;

import com.vehicleworks.block.WingOrientation;
import com.vehicleworks.component.ComponentType;
import com.vehicleworks.component.PropertyDef;
import com.vehicleworks.component.WingConfig;
import com.vehicleworks.network.Payloads;
import com.vehicleworks.physics.aero.AeroModel;
import com.vehicleworks.physics.aero.WingParams;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic engineering configuration screen for any component. Wings additionally show a live
 * lift / drag polar computed with the same aerodynamic model the physics engine uses.
 */
public class ComponentScreen extends Screen {
    private final int vehicleId;
    private final long pos;
    private final ComponentType type;
    private final CompoundTag config;
    private int plane;
    private int page;
    private static final int PER_PAGE = 8;
    private final List<PropertyDef> defs = new ArrayList<>();

    public ComponentScreen(int vehicleId, long pos, CompoundTag data) {
        super(Component.translatable("block.vehicleworks." + data.getString("Type")));
        this.vehicleId = vehicleId;
        this.pos = pos;
        this.type = ComponentType.byId(data.getString("Type"));
        this.config = data.getCompound("Config").copy();
        this.plane = data.contains("Plane") ? data.getInt("Plane") : -1;
        if (type != null) defs.addAll(type.properties);
    }

    private boolean isWing() {
        return type != null && type.isWing();
    }

    @Override
    protected void init() {
        clearWidgets();
        int left = isWing() ? width / 2 - 200 : width / 2 - 100;
        int y = 40;
        int from = page * PER_PAGE, to = Math.min(defs.size(), from + PER_PAGE);
        for (int i = from; i < to; i++) {
            PropertyDef d = defs.get(i);
            int yy = y + (i - from) * 22;
            switch (d.kind) {
                case NUMBER -> {
                    if (Math.abs(d.max - d.min) > 100000) {
                        EditBox box = new EditBox(font, left, yy, 200, 20, Component.translatable(d.translationKey()));
                        box.setValue(String.valueOf((long) d.get(config)));
                        box.setResponder(s -> { try { d.set(config, Double.parseDouble(s)); } catch (NumberFormatException ignored) {} });
                        addRenderableWidget(box);
                    } else addRenderableWidget(new PropertySlider(left, yy, 200, 20, d, config, () -> {}));
                }
                case INT -> {
                    if (d.max - d.min > 1000) {
                        EditBox box = new EditBox(font, left, yy, 200, 20, Component.translatable(d.translationKey()));
                        box.setValue(String.valueOf((int) d.get(config)));
                        box.setResponder(s -> { try { d.set(config, Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {} });
                        addRenderableWidget(box);
                    } else addRenderableWidget(new PropertySlider(left, yy, 200, 20, d, config, () -> {}));
                }
                case BOOL -> addRenderableWidget(Button.builder(boolLabel(d), b -> {
                    d.set(config, d.getBool(config) ? 0 : 1);
                    b.setMessage(boolLabel(d));
                }).bounds(left, yy, 200, 20).build());
                case ENUM -> addRenderableWidget(Button.builder(enumLabel(d), b -> {
                    int v = (d.getInt(config) + 1) % d.options.length;
                    d.set(config, v);
                    if (d.key.equals("wing_type")) {
                        // apply the planform defaults of the new wing type
                        var wt = com.vehicleworks.physics.aero.WingType.byIndex(v);
                        type.property("sweep").set(config, wt.defaultSweepDeg);
                        type.property("sweep_max").set(config, wt == com.vehicleworks.physics.aero.WingType.VARIABLE_SWEEP ? 68 : wt.defaultSweepDeg);
                        type.property("profile").set(config, wt.defaultProfile.ordinal());
                        init();
                    }
                    b.setMessage(enumLabel(d));
                }).bounds(left, yy, 200, 20).build());
            }
        }
        int by = y + PER_PAGE * 22 + 6;
        if (defs.size() > PER_PAGE) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page = Math.max(0, page - 1); init(); }).bounds(left, by, 30, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page = Math.min((defs.size() - 1) / PER_PAGE, page + 1); init(); }).bounds(left + 170, by, 30, 20).build());
        }
        if (plane >= 0) {
            addRenderableWidget(Button.builder(planeLabel(), b -> { plane = (plane + 1) % 3; b.setMessage(planeLabel()); })
                    .bounds(left + 34, by, 132, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("vehicleworks.ui.apply"), b -> apply(false)).bounds(width / 2 - 104, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("vehicleworks.ui.apply_close"), b -> apply(true)).bounds(width / 2 + 4, height - 28, 100, 20).build());
    }

    private Component planeLabel() {
        return Component.translatable("vehicleworks.ui.plane", Component.translatable("vehicleworks.plane." + WingOrientation.values()[plane].getSerializedName()));
    }

    private Component boolLabel(PropertyDef d) {
        return Component.translatable(d.translationKey()).append(": ").append(Component.translatable(d.getBool(config) ? "options.on" : "options.off"));
    }

    private Component enumLabel(PropertyDef d) {
        return Component.translatable(d.translationKey()).append(": ").append(Component.translatable("vehicleworks.opt." + d.options[Math.floorMod(d.getInt(config), d.options.length)]));
    }

    private void apply(boolean close) {
        CompoundTag t = config.copy();
        if (plane >= 0) t.putInt("_plane", plane);
        Payloads.toServer(new Payloads.Configure(vehicleId, pos, false, t));
        if (close) onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        if (type != null) g.drawCenteredString(font, Component.translatable("vehicleworks.tooltip." + type.id), width / 2, 26, 0xAAAAAA);
        if (isWing()) renderPolar(g, width / 2 + 10, 40, 190, 150);
    }

    /** Lift and drag coefficient versus angle of attack, with the stall marked. */
    private void renderPolar(GuiGraphics g, int x, int y, int w, int h) {
        WingParams p = WingConfig.toParams(type, config);
        g.fill(x, y, x + w, y + h, 0xAA101820);
        g.renderOutline(x, y, w, h, 0x66FFFFFF);
        double clMin = -1.2, clMax = 2.4;
        int zeroY = y + (int) (h * (clMax / (clMax - clMin)));
        g.fill(x, zeroY, x + w, zeroY + 1, 0x44FFFFFF);
        int zeroX = x + w / 3;
        g.fill(zeroX, y, zeroX + 1, y + h, 0x44FFFFFF);
        AeroModel m = new AeroModel();
        double ar = 8;
        int prevL = -1, prevD = -1;
        double best = -9, bestA = 0;
        for (int i = 0; i < w; i++) {
            double alpha = -15 + 45.0 * i / w;
            m.compute(p, Math.toRadians(alpha + p.incidenceDeg), 0, 0, 0, ar, Math.toRadians(p.sweepDeg), 0.2, 99);
            if (m.cl > best) { best = m.cl; bestA = alpha; }
            int ly = y + (int) (h * (clMax - m.cl) / (clMax - clMin));
            int dy = y + (int) (h * (clMax - m.cd) / (clMax - clMin));
            if (prevL >= 0) {
                g.fill(x + i, Math.min(ly, prevL), x + i + 1, Math.max(ly, prevL) + 1, 0xFF60FF80);
                g.fill(x + i, Math.min(dy, prevD), x + i + 1, Math.max(dy, prevD) + 1, 0xFFFF7050);
            }
            prevL = ly;
            prevD = dy;
        }
        int sx = x + (int) (w * (bestA + 15) / 45.0);
        g.fill(sx, y, sx + 1, y + h, 0x88FFC040);
        g.drawString(font, Component.translatable("vehicleworks.ui.polar_cl"), x + 4, y + 4, 0x60FF80);
        g.drawString(font, Component.translatable("vehicleworks.ui.polar_cd"), x + 4, y + 14, 0xFF7050);
        g.drawString(font, Component.translatable("vehicleworks.ui.clmax", String.format("%.2f", best), String.format("%.1f", bestA)), x + 4, y + h - 12, 0xFFC040);
        g.drawString(font, "-15°", x + 2, zeroY + 2, 0x888888);
        g.drawString(font, "30°", x + w - 18, zeroY + 2, 0x888888);
        // lift per block at 50 m/s, 4°
        m.compute(p, Math.toRadians(4 + p.incidenceDeg), 0, 0, 0, ar, Math.toRadians(p.sweepDeg), 0.15, 99);
        double lift = 0.5 * 1.225 * 2500 * p.chord * m.cl;
        g.drawString(font, Component.translatable("vehicleworks.ui.lift_example", String.format("%.0f", lift), String.format("%.1f", m.cl / Math.max(1e-4, m.cd))), x, y + h + 4, 0xCCCCCC);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
