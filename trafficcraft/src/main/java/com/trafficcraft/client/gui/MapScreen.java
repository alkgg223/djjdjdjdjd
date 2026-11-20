package com.trafficcraft.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.trafficcraft.client.nav.MapView;
import com.trafficcraft.client.nav.Nav;
import com.trafficcraft.road.Place;
import com.trafficcraft.road.PlaceTypes;
import com.trafficcraft.road.ClientRoads;
import com.trafficcraft.vehicle.VehicleEntity;
import com.trafficcraft.vehicle.VehicleSpec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * Full map control (X while the Map app is open on the car screen): W A S D or drag to move the map, mouse wheel to zoom,
 * right-click a location (or any point) to set the destination - the shortest route is drawn in blue. Search bar on top,
 * "Cancel trip" and "Follow" (the map turns with the car) at the bottom right. Colours follow the car maker's screen theme.
 */
public class MapScreen extends Screen {
    final VehicleEntity v;
    final boolean focusSearch;
    EditBox search;
    List<Place> results = new ArrayList<>();
    Place hover;
    MapView view;
    boolean dragging;
    double dragX, dragY;
    MapView.Theme th;
    Button followBtn, cancelBtn;

    public MapScreen(VehicleEntity v, boolean focusSearch) {
        super(Component.translatable("map.trafficcraft.title"));
        this.v = v;
        this.focusSearch = focusSearch;
        if (Nav.follow || Nav.viewX == 0 && Nav.viewZ == 0) {
            Nav.viewX = v.getX();
            Nav.viewZ = v.getZ();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        VehicleSpec s = v.spec();
        th = CarScreen.theme(CarScreen.argb(s.uiBg, 1), CarScreen.argb(s.uiAccent, 1), CarScreen.argb(s.uiFg, 1));
        search = new EditBox(font, width / 2 - 140, 8, 280, 18, Component.translatable("map.trafficcraft.search"));
        search.setHint(Component.translatable("map.trafficcraft.search"));
        search.setResponder(t -> results = t.isBlank() ? new ArrayList<>() : Nav.search(t, v.position()));
        addRenderableWidget(search);
        if (focusSearch) setInitialFocus(search);
        cancelBtn = addRenderableWidget(Button.builder(Component.translatable("map.trafficcraft.cancel"), b -> Nav.cancel()).bounds(width - 214, height - 28, 100, 20).build());
        followBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            Nav.follow = !Nav.follow;
            if (Nav.follow) {
                Nav.viewX = v.getX();
                Nav.viewZ = v.getZ();
            }
        }).bounds(width - 108, height - 28, 100, 20).build());
    }

    @Override
    public void tick() {
        if (search.isFocused()) return;
        long w = minecraft.getWindow().getWindow();
        double step = 6 / Nav.zoom;
        boolean moved = false;
        if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_W)) { Nav.viewZ -= step; moved = true; }
        if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_S)) { Nav.viewZ += step; moved = true; }
        if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_A)) { Nav.viewX -= step; moved = true; }
        if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_D)) { Nav.viewX += step; moved = true; }
        if (moved) Nav.follow = false;
        if (Nav.follow) {
            Nav.viewX = v.getX();
            Nav.viewZ = v.getZ();
        }
    }

    MapView canvas(GuiGraphics g) {
        MapView mv = new MapView(new MapView.Canvas() {
            @Override
            public void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
                Matrix4f m = g.pose().last().pose();
                VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
                // both windings (the GUI culls back faces)
                vc.addVertex(m, x0, y0, 0).setColor(argb);
                vc.addVertex(m, x1, y1, 0).setColor(argb);
                vc.addVertex(m, x2, y2, 0).setColor(argb);
                vc.addVertex(m, x3, y3, 0).setColor(argb);
                vc.addVertex(m, x3, y3, 0).setColor(argb);
                vc.addVertex(m, x2, y2, 0).setColor(argb);
                vc.addVertex(m, x1, y1, 0).setColor(argb);
                vc.addVertex(m, x0, y0, 0).setColor(argb);
            }

            @Override
            public void text(String s, float x, float y, int argb, float scale, boolean centre) {
                g.flush();
                g.pose().pushPose();
                float k = scale * 2.2f;
                g.pose().translate(x, y, 0);
                g.pose().scale(k, k, 1);
                g.drawString(font, s, centre ? -font.width(s) / 2 : 0, 0, argb, false);
                g.pose().popPose();
            }
        }, th, width, height);
        mv.setView(Nav.viewX, Nav.viewZ, Nav.zoom, false, 0);
        return mv;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        view = canvas(g);
        hover = pick(mx, my);
        view.drawMap(v, hover);
        g.flush();
        // header / legend / footer
        g.fill(0, 0, width, 32, 0xC0101216);
        g.fill(0, height - 34, width, height, 0xC0101216);
        String info = Nav.active() ? Component.translatable("map.trafficcraft.to", Nav.destName, Nav.distText(Nav.routeLen)).getString()
                : Component.translatable("map.trafficcraft.help").getString();
        g.drawString(font, info, 8, height - 22, Nav.active() ? 0xFF8CC4FF : 0xFFB8BEC6, false);
        followBtn.setMessage(Component.translatable(Nav.follow ? "map.trafficcraft.follow_on" : "map.trafficcraft.follow_off"));
        cancelBtn.active = Nav.active();
        super.render(g, mx, my, pt);
        // search results under the bar
        int y = 30;
        for (int i = 0; i < Math.min(7, results.size()); i++) {
            Place p = results.get(i);
            boolean over = mx >= width / 2 - 140 && mx <= width / 2 + 140 && my >= y && my < y + 16;
            g.fill(width / 2 - 140, y, width / 2 + 140, y + 16, over ? 0xF0303844 : 0xF0181C22);
            g.fill(width / 2 - 136, y + 3, width / 2 - 126, y + 13, 0xFF000000 | PlaceTypes.color(p.type));
            g.drawString(font, p.name, width / 2 - 120, y + 4, 0xFFFFFFFF, false);
            String meta = Component.translatable("place.trafficcraft." + p.type).getString() + "  " + Nav.distText(p.pos().distanceTo(v.position()));
            g.drawString(font, meta, width / 2 + 136 - font.width(meta), y + 4, 0xFF9AA4B0, false);
            y += 16;
        }
        if (results.isEmpty() && !search.getValue().isBlank())
            g.drawCenteredString(font, Component.translatable("map.trafficcraft.none"), width / 2, 32, 0xFFFF8080);
        if (hover != null) {
            String t = hover.name + " - " + Component.translatable("place.trafficcraft." + hover.type).getString();
            g.fill(mx + 10, my - 4, mx + 16 + font.width(t), my + 10, 0xE0101216);
            g.drawString(font, t, mx + 13, my - 1, 0xFFFFFFFF, false);
        }
    }

    /** development test */
    public void testSearch(String q) {
        search.setValue(q);
        results = Nav.search(q, v.position());
    }

    Place pick(double mx, double my) {
        if (view == null) return null;
        Place best = null;
        double bd = 10;
        for (Place p : ClientRoads.net.places.values()) {
            float[] s = view.toScreen(p.x, p.z);
            double d = Math.hypot(s[0] - mx, s[1] - my);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    void go(Place p) {
        Nav.setDestination(p.pos(), p.name);
        Nav.compute(ClientRoads.net, v.position(), v.forward(), p.pos());
        search.setValue("");
        results.clear();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // search results
        int y = 30;
        for (int i = 0; i < Math.min(7, results.size()); i++) {
            if (mx >= width / 2 - 140 && mx <= width / 2 + 140 && my >= y && my < y + 16) {
                go(results.get(i));
                return true;
            }
            y += 16;
        }
        if (super.mouseClicked(mx, my, button)) return true;
        if (my < 32 || my > height - 34) return false;
        if (button == 1 && view != null) {
            // right click: a location, or that point on the map
            Place p = pick(mx, my);
            if (p != null) go(p);
            else {
                double[] w = view.toWorld((float) mx, (float) my);
                Vec3 at = new Vec3(w[0], v.getY(), w[1]);
                Nav.setDestination(at, Component.translatable("map.trafficcraft.pin").getString());
                Nav.compute(ClientRoads.net, v.position(), v.forward(), at);
            }
            return true;
        }
        if (button == 0) {
            setFocused(null);
            dragging = true;
            dragX = mx;
            dragY = my;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && button == 0) {
            Nav.viewX -= dx / Nav.zoom;
            Nav.viewZ -= dy / Nav.zoom;
            Nav.follow = false;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        Nav.zoom = (float) Math.max(0.25, Math.min(8, Nav.zoom * Math.pow(1.18, sy)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!search.isFocused() && key == GLFW.GLFW_KEY_X) {
            onClose();
            return true;
        }
        if (search.isFocused() && key == GLFW.GLFW_KEY_ENTER && !results.isEmpty()) {
            go(results.get(0));
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
