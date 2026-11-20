package com.trafficcraft.client.nav;

import com.trafficcraft.road.ClientRoads;
import com.trafficcraft.road.EdgeGeom;
import com.trafficcraft.road.Place;
import com.trafficcraft.road.PlaceTypes;
import com.trafficcraft.road.RoadEdge;
import com.trafficcraft.road.RoadNetwork;
import com.trafficcraft.vehicle.VehicleEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the navigation map (roads built from the connected nodes, locations, the route in blue, the car arrow) on any canvas:
 * the in-car screen (world-space quads) or the full map screen (GUI). Colours come from the car maker's screen theme.
 */
public final class MapView {
    public interface Canvas {
        void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int argb);

        void text(String s, float x, float y, int argb, float scale, boolean centre);
    }

    public static final class Theme {
        public int bg = 0xFF15181C, road = 0xFF8C939C, roadEdge = 0xFF3A3F46, major = 0xFFC8CDD4, route = 0xFF2F80FF, text = 0xFFE6E8EB, car = 0xFFFFFFFF,
                accent = 0xFF2F80FF;
    }

    /** current transform */
    public float W, H, scale, ox, oy;
    double cx, cz, ca = 1, sa = 0;
    boolean rotate;
    final Canvas c;
    public final Theme th;

    public MapView(Canvas c, Theme th, float W, float H) {
        this.c = c;
        this.th = th;
        this.W = W;
        this.H = H;
    }

    /** North-up view centred on (x, z), or heading-up following the car (car drawn low on the screen). */
    public void setView(double x, double z, float scale, boolean headingUp, float yawDeg) {
        cx = x;
        cz = z;
        this.scale = scale;
        rotate = headingUp;
        double y = Math.toRadians(yawDeg);
        ca = Math.cos(y);
        sa = Math.sin(y);
        ox = W / 2;
        oy = headingUp ? H * 0.64f : H / 2;
    }

    public float[] toScreen(double x, double z) {
        double dx = x - cx, dz = z - cz;
        if (!rotate) return new float[]{(float) (ox + dx * scale), (float) (oy + dz * scale)};
        // forward = (-sin, cos), right = (-cos, -sin)
        double f = -dx * sa + dz * ca, r = -dx * ca - dz * sa;
        return new float[]{(float) (ox + r * scale), (float) (oy - f * scale)};
    }

    public double[] toWorld(float sx, float sy) {
        double r = (sx - ox) / scale, f = -(sy - oy) / scale;
        if (!rotate) return new double[]{cx + r, cz + (sy - oy) / scale};
        double dx = -f * sa - r * ca, dz = f * ca - r * sa;
        return new double[]{cx + dx, cz + dz};
    }

    // ------------------------------------------------------------------------------------------ primitives
    public void line(float x0, float y0, float x1, float y1, float w, int argb) {
        // clip to the canvas (Liang-Barsky)
        float dx = x1 - x0, dy = y1 - y0;
        float t0 = 0, t1 = 1;
        float m = 0;
        float[] p = {-dx, dx, -dy, dy}, q = {x0 + m, W + m - x0, y0 + m, H + m - y0};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) {
                if (q[i] < 0) return;
            } else {
                float t = q[i] / p[i];
                if (p[i] < 0) t0 = Math.max(t0, t);
                else t1 = Math.min(t1, t);
            }
        }
        if (t0 > t1) return;
        float ax = x0 + dx * t0, ay = y0 + dy * t0, bx = x0 + dx * t1, by = y0 + dy * t1;
        float len = (float) Math.hypot(bx - ax, by - ay);
        if (len < 1e-3) return;
        float nx = -(by - ay) / len * w / 2, ny = (bx - ax) / len * w / 2;
        // extend a little along the line so joints between segments close up
        float ex = (bx - ax) / len * w * 0.35f, ey = (by - ay) / len * w * 0.35f;
        c.quad(ax - ex + nx, ay - ey + ny, bx + ex + nx, by + ey + ny, bx + ex - nx, by + ey - ny, ax - ex - nx, ay - ey - ny, argb);
    }

    public void disc(float x, float y, float r, int argb) {
        if (x < -r || y < -r || x > W + r || y > H + r) return;
        int n = 10;
        for (int i = 0; i < n; i += 2) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n, a2 = 2 * Math.PI * (i + 2) / n;
            c.quad(x, y, x + (float) Math.cos(a0) * r, y + (float) Math.sin(a0) * r, x + (float) Math.cos(a1) * r, y + (float) Math.sin(a1) * r,
                    x + (float) Math.cos(a2) * r, y + (float) Math.sin(a2) * r, argb);
        }
    }

    public void rect(float x, float y, float w, float h, int argb) {
        c.quad(x, y, x, y + h, x + w, y + h, x + w, y, argb);
    }

    // ------------------------------------------------------------------------------------------ map
    static int cacheVersion = -1;
    static final List<float[]> ROADS = new ArrayList<>();      // per edge: width, then x,z pairs
    static final List<Boolean> MAJOR = new ArrayList<>();

    static void cache(RoadNetwork net) {
        if (cacheVersion == net.version) return;
        cacheVersion = net.version;
        ROADS.clear();
        MAJOR.clear();
        for (RoadEdge e : net.edges.values()) {
            if (e.ped) continue;
            EdgeGeom g = net.geom(e);
            if (g == null) continue;
            int n = Math.max(1, (int) Math.ceil(g.length / 4));
            float[] a = new float[1 + 2 * (n + 1)];
            a[0] = e.width;
            for (int i = 0; i <= n; i++) {
                Vec3 p = g.point(g.length * i / n, 0);
                a[1 + 2 * i] = (float) p.x;
                a[2 + 2 * i] = (float) p.z;
            }
            ROADS.add(a);
            MAJOR.add(e.lanes.size() >= 4);
        }
    }

    public void drawMap(VehicleEntity car, Place selected) {
        RoadNetwork net = ClientRoads.net;
        cache(net);
        rect(0, 0, W, H, th.bg);
        // roads: dark casing, then the road colour (wide roads brighter)
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < ROADS.size(); k++) {
                float[] a = ROADS.get(k);
                float w = Math.max(pass == 0 ? 2.4f : 1.4f, a[0] * scale * (pass == 0 ? 1.0f : 0.78f));
                int col = pass == 0 ? th.roadEdge : MAJOR.get(k) ? th.major : th.road;
                float[] prev = null;
                for (int i = 1; i + 1 < a.length; i += 2) {
                    float[] p = toScreen(a[i], a[i + 1]);
                    if (prev != null) line(prev[0], prev[1], p[0], p[1], w, col);
                    prev = p;
                }
            }
        }
        // route
        if (Nav.active() && Nav.route.size() > 1) {
            float w = Math.max(2.2f, 3.2f * scale);
            float[] prev = null;
            for (Vec3 p : Nav.route) {
                float[] s = toScreen(p.x, p.z);
                if (prev != null) line(prev[0], prev[1], s[0], s[1], w, th.route);
                prev = s;
            }
        }
        // locations
        for (Place p : net.places.values()) {
            float[] s = toScreen(p.x, p.z);
            if (s[0] < 6 || s[1] < 6 || s[0] > W - 6 || s[1] > H - 6) continue;      // keep pins and labels inside the screen
            boolean sel = selected != null && selected.id == p.id;
            float r = sel ? 6.5f : 4.6f;
            disc(s[0], s[1], r + 1.2f, sel ? 0xFFFFFFFF : 0xFF101214);
            disc(s[0], s[1], r, 0xFF000000 | PlaceTypes.color(p.type));
            c.text(PlaceTypes.glyph(p.type), s[0], s[1] - 2.2f, 0xFFFFFFFF, 0.42f, true);
            if ((scale > 1.2f || sel) && s[0] > W * 0.12f && s[0] < W * 0.88f && s[1] < H - 10) c.text(p.name, s[0], s[1] + r + 1.5f, th.text, 0.45f, true);
        }
        // destination pin
        if (Nav.dest != null) {
            float[] s = toScreen(Nav.dest.x, Nav.dest.z);
            disc(s[0], s[1] - 6, 4.2f, 0xFFE53935);
            line(s[0], s[1] - 3, s[0], s[1], 1.6f, 0xFFE53935);
            disc(s[0], s[1] - 6, 1.6f, 0xFFFFFFFF);
        }
        // the car: arrow pointing where it is heading
        if (car != null) {
            float[] s = toScreen(car.getX(), car.getZ());
            Vec3 f = car.forward();
            float[] t = toScreen(car.getX() + f.x * 4 / scale, car.getZ() + f.z * 4 / scale);
            float hx = t[0] - s[0], hy = t[1] - s[1];
            float L = (float) Math.hypot(hx, hy);
            if (L > 1e-3) {
                hx /= L;
                hy /= L;
                float sz = 6.5f;
                float tipx = s[0] + hx * sz, tipy = s[1] + hy * sz;
                float lx = s[0] - hx * sz * 0.7f - hy * sz * 0.75f, ly = s[1] - hy * sz * 0.7f + hx * sz * 0.75f;
                float rx = s[0] - hx * sz * 0.7f + hy * sz * 0.75f, ry = s[1] - hy * sz * 0.7f - hx * sz * 0.75f;
                float bx = s[0] - hx * sz * 0.25f, by = s[1] - hy * sz * 0.25f;
                c.quad(tipx, tipy, lx, ly, bx, by, rx, ry, 0xFF0B0D10);
                float k = 0.72f;
                c.quad(s[0] + (tipx - s[0]) * k, s[1] + (tipy - s[1]) * k, s[0] + (lx - s[0]) * k, s[1] + (ly - s[1]) * k, bx, by,
                        s[0] + (rx - s[0]) * k, s[1] + (ry - s[1]) * k, th.car);
            }
        }
    }
}
