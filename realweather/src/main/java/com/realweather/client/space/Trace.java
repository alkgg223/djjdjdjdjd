package com.realweather.client.space;

/**
 * Sets up a ray-traced pass over a round body: the part of the screen it covers and each pixel's ray in the body's
 * own frame (its radius 1), from where the camera is.
 */
final class Trace {
    float x0, y0, x1, y1;
    /** How far (radii) the rays' starting plane is from the camera. */
    float near;
    final double[] o = new double[3], ox = new double[3], oy = new double[3], d = new double[3], dx = new double[3], dy = new double[3];

    private Trace() {}

    /**
     * For a body at view-space centre c, of radius r (view units) and reaching out to `extent` radii (its air, rings),
     * turned into view space by `rot` (its frame to view, column-major): null if it is off the screen.
     */
    static Trace sphere(double cx, double cy, double cz, double r, double extent, double[] rot) {
        double dist = Math.sqrt(cx * cx + cy * cy + cz * cz);
        double reach = r * extent;
        double px = Space3D.projX(), py = Space3D.projY();
        double x0, x1, y0, y1;
        if (dist < reach * 2.5 || cz > -reach) {
            x0 = -1;
            y0 = -1;
            x1 = 1;
            y1 = 1;
        } else {
            double a = Math.asin(Math.min(1, reach / dist));
            double ncx = px * cx / -cz, ncy = py * cy / -cz;
            // A little more than the angle it covers, wider when it is off to the side (perspective stretches it).
            double off = Math.sqrt(ncx * ncx / (px * px) + ncy * ncy / (py * py));
            double grow = 1.15 * (1 + off * off);
            double hx = px * Math.tan(a) * grow, hy = py * Math.tan(a) * grow;
            x0 = Math.max(ncx - hx, -1.02);
            x1 = Math.min(ncx + hx, 1.02);
            y0 = Math.max(ncy - hy, -1.02);
            y1 = Math.min(ncy + hy, 1.02);
            if (x0 >= x1 || y0 >= y1) {
                return null;
            }
        }
        Trace t = new Trace();
        t.x0 = (float) x0;
        t.y0 = (float) y0;
        t.x1 = (float) x1;
        t.y1 = (float) y1;
        double[] dv = {(x0 + x1) * 0.5 / px, (y0 + y1) * 0.5 / py, -1}, dxv = {(x1 - x0) * 0.5 / px, 0, 0}, dyv = {0, (y1 - y0) * 0.5 / py, 0};
        // The rays start on a plane just in front of the body (not back at the camera, far off: in the GPU's single
        // precision the sums would lose all their detail there), each where the camera's own ray crosses it.
        double t0 = Math.max(0, -cz - reach * 2.2);
        t.near = (float) (t0 / r);
        local(rot, (-cx + dv[0] * t0) / r, (-cy + dv[1] * t0) / r, (-cz + dv[2] * t0) / r, t.o);
        local(rot, dxv[0] * t0 / r, dxv[1] * t0 / r, dxv[2] * t0 / r, t.ox);
        local(rot, dyv[0] * t0 / r, dyv[1] * t0 / r, dyv[2] * t0 / r, t.oy);
        local(rot, dv[0], dv[1], dv[2], t.d);
        local(rot, dxv[0], dxv[1], dxv[2], t.dx);
        local(rot, dyv[0], dyv[1], dyv[2], t.dy);
        return t;
    }

    /** A view-space vector in the body's frame (rot transposed). */
    static void local(double[] rot, double x, double y, double z, double[] out) {
        out[0] = rot[0] * x + rot[1] * y + rot[2] * z;
        out[1] = rot[3] * x + rot[4] * y + rot[5] * z;
        out[2] = rot[6] * x + rot[7] * y + rot[8] * z;
    }

    /** Its size on the screen, in pixels across its radius (for detail). */
    static double pixels(double cx, double cy, double cz, double r) {
        double dist = Math.sqrt(cx * cx + cy * cy + cz * cz);
        int h = Math.max(1, net.minecraft.client.Minecraft.getInstance().getWindow().getHeight());
        return r / Math.max(1.0E-9, dist) * Space3D.projY() * h * 0.5;
    }
}
