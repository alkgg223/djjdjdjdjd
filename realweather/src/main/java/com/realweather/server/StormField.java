package com.realweather.server;

/**
 * The shape of storms on the ground, shared by the server (rain, floods, hail) and the client (clouds, radar).
 * Ordinary cells are round cores; supercells have a long forward flank of rain and hail ahead of a rotating
 * updraft with a hook-shaped rear flank, where tornadoes form.
 */
public final class StormField {
    private StormField() {}

    /** A cell as the field needs it (server cells and client copies both implement this). */
    public interface Cell {
        double cx();
        double cz();
        float cvx();
        float cvz();
        float cradius();
        float cintensity();
        float chail();
        int ctype();
        float cstrength();
    }

    /** Rain rate under a cell, 0..1. */
    public static float rain(Cell c, double x, double z) {
        float s = c.cstrength() * c.cintensity();
        if (s <= 0.001F) {
            return 0F;
        }
        double dx = x - c.cx();
        double dz = z - c.cz();
        double r = c.cradius();
        if (c.ctype() == StormCell.SUPERCELL) {
            // Frame aligned with the storm's motion: forward flank ahead, updraft and hook behind.
            double sp = Math.max(1e-4, Math.hypot(c.cvx(), c.cvz()));
            double fx = c.cvx() / sp;
            double fz = c.cvz() / sp;
            double along = dx * fx + dz * fz;
            double across = -dx * fz + dz * fx;
            double ff = Math.exp(-sq((along - r * 0.25) / (r * 0.9)) - sq((across + r * 0.25) / (r * 0.45)));
            double core = Math.exp(-sq(along / (r * 0.35)) - sq(across / (r * 0.35)));
            // The hook wraps around the back of the updraft.
            double ha = Math.atan2(across, -along);
            double hr = Math.hypot(along, across) / r;
            double hook = Math.exp(-sq((hr - 0.32) / 0.1)) * Math.max(0, Math.cos(ha - 0.6)) * 0.7;
            double notch = Math.exp(-sq((along + r * 0.25) / (r * 0.12)) - sq((across - r * 0.05) / (r * 0.12)));
            return (float) Math.min(1.0, s * (0.75 * ff + 0.9 * core + hook) * (1 - 0.8 * notch));
        }
        double d2 = (dx * dx + dz * dz) / (r * r);
        return (float) Math.min(1.0, s * Math.exp(-d2 * 2.2) * (1.0 + 0.25 * Math.sin(dx * 0.031 + c.cx() * 0.01) * Math.sin(dz * 0.027)));
    }

    /** Hail rate under a cell, 0..1 (a smaller core inside the rain). */
    public static float hail(Cell c, double x, double z) {
        if (c.chail() <= 0.01F || c.ctype() < StormCell.HAIL) {
            return 0F;
        }
        double dx = x - c.cx();
        double dz = z - c.cz();
        double r = c.cradius() * (c.ctype() == StormCell.SUPERCELL ? 0.3 : 0.28);
        double ox = 0;
        double oz = 0;
        if (c.ctype() == StormCell.SUPERCELL) {
            // Hail falls just ahead of the updraft, in the forward flank.
            double sp = Math.max(1e-4, Math.hypot(c.cvx(), c.cvz()));
            ox = c.cvx() / sp * c.cradius() * 0.2;
            oz = c.cvz() / sp * c.cradius() * 0.2;
        }
        double d2 = (sq(dx - ox) + sq(dz - oz)) / (r * r);
        return (float) Math.min(1.0, c.chail() * c.cstrength() * Math.exp(-d2 * 2.0));
    }

    /** Rotation (mesocyclone) strength near the updraft of a supercell, 0..1. */
    public static float rotation(Cell c, double x, double z) {
        if (c.ctype() != StormCell.SUPERCELL) {
            return 0F;
        }
        double[] u = updraft(c);
        double d2 = (sq(x - u[0]) + sq(z - u[1])) / sq(c.cradius() * 0.22);
        return (float) (c.cstrength() * Math.exp(-d2));
    }

    /** Position of a supercell's updraft (where the wall cloud and tornado sit): behind the core, slightly right. */
    public static double[] updraft(Cell c) {
        double sp = Math.max(1e-4, Math.hypot(c.cvx(), c.cvz()));
        double fx = c.cvx() / sp;
        double fz = c.cvz() / sp;
        double back = -c.cradius() * 0.3;
        double right = -c.cradius() * 0.06;
        return new double[]{c.cx() + fx * back - fz * right, c.cz() + fz * back + fx * right};
    }

    public static float sumRain(Iterable<? extends Cell> cells, double x, double z) {
        float total = 0F;
        for (Cell c : cells) {
            total += rain(c, x, z);
        }
        return Math.min(1F, total);
    }

    public static float sumHail(Iterable<? extends Cell> cells, double x, double z) {
        float total = 0F;
        for (Cell c : cells) {
            total = Math.max(total, hail(c, x, z));
        }
        return total;
    }

    private static double sq(double v) {
        return v * v;
    }
}
