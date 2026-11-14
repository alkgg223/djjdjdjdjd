package com.realweather.client;

import com.realweather.net.StormSyncPayload;
import com.realweather.server.StormCell;
import com.realweather.server.StormField;

import java.util.ArrayList;
import java.util.List;

/** The storm cells the server told us about, moved along between updates. */
public final class ClientStorms {
    public static final class Cell implements StormField.Cell {
        public final int id;
        public final int type;
        public final long seed;
        public double x, z;
        public float vx, vz, radius, intensity, hail;
        public int age, life;

        Cell(StormSyncPayload.Entry e) {
            id = e.id();
            type = e.type();
            seed = e.seed();
            x = e.x();
            z = e.z();
            vx = e.vx();
            vz = e.vz();
            radius = e.radius();
            intensity = e.intensity();
            hail = e.hail();
            age = e.age();
            life = e.life();
        }

        @Override public double cx() { return x; }
        @Override public double cz() { return z; }
        @Override public float cvx() { return vx; }
        @Override public float cvz() { return vz; }
        @Override public float cradius() { return radius; }
        @Override public float cintensity() { return intensity; }
        @Override public float chail() { return hail; }
        @Override public int ctype() { return type; }
        @Override public float cstrength() { return StormCell.lifeCurve(age, life); }
    }

    private static final List<Cell> CELLS = new ArrayList<>();
    public static float stratiform;
    /** True once a server with the mod has sent storms. */
    public static boolean synced;

    private ClientStorms() {}

    public static List<Cell> cells() {
        return CELLS;
    }

    public static void onSync(StormSyncPayload p) {
        CELLS.clear();
        for (StormSyncPayload.Entry e : p.cells()) {
            CELLS.add(new Cell(e));
        }
        stratiform = p.stratiform();
        synced = true;
    }

    public static void clear() {
        CELLS.clear();
        synced = false;
        stratiform = 0F;
    }

    static void tick() {
        for (Cell c : CELLS) {
            c.x += c.vx;
            c.z += c.vz;
            c.age++;
        }
    }

    public static float rainAt(double x, double z) {
        return Math.min(1F, stratiform + StormField.sumRain(CELLS, x, z));
    }

    public static float hailAt(double x, double z) {
        return StormField.sumHail(CELLS, x, z);
    }
}
