package com.vehicleworks.physics.fluid;

import com.vehicleworks.physics.RigidBody;
import org.joml.Matrix3d;
import org.joml.Vector3d;

/**
 * Buoyancy (Archimedes, per element submerged fraction), Newtonian pressure drag on
 * exposed faces in water and air, and gas-cell lift.
 */
public final class FluidForces {
    public double faceDragCoefficient = 0.9;
    public double gravity = 9.81;

    // diagnostics of the last step
    public double submergedVolume;
    public double displacedMass;
    public double buoyancyForce;
    public double maxDepth;
    public double wettedFraction;
    public boolean anyWater;

    private final double[] sample = new double[4];
    private final Vector3d tmp = new Vector3d();
    private final Vector3d vel = new Vector3d();

    /**
     * @param flood per-pocket flood fraction (0 dry … 1 flooded), may be null
     */
    public void apply(RigidBody body, Vector3d comLocal, FluidElements el, FluidSampler sampler, double[] flood) {
        submergedVolume = 0;
        displacedMass = 0;
        buoyancyForce = 0;
        maxDepth = 0;
        anyWater = false;
        double wetted = 0, total = 0;
        Matrix3d r = body.rotation;
        for (int i = 0; i < el.count; i++) {
            double lx = el.x[i] - comLocal.x, ly = el.y[i] - comLocal.y, lz = el.z[i] - comLocal.z;
            double wx = r.m00 * lx + r.m10 * ly + r.m20 * lz + body.position.x;
            double wy = r.m01 * lx + r.m11 * ly + r.m21 * lz + body.position.y;
            double wz = r.m02 * lx + r.m12 * ly + r.m22 * lz + body.position.z;
            double hh = el.halfHeight[i];
            sampler.sample(wx, wy, wz, sample);
            double surface = sample[0];
            double rho = sample[1];
            double f = 0;
            if (surface > Double.NEGATIVE_INFINITY) {
                f = (surface - (wy - hh)) / (2 * hh);
                f = f < 0 ? 0 : (f > 1 ? 1 : f);
            }
            int pk = el.pocket[i];
            double vol = el.volume[i];
            if (pk >= 0 && flood != null && pk < flood.length) vol *= (1.0 - flood[pk]);
            total += el.volume[i];
            if (f > 0) {
                anyWater = true;
                double v = vol * f;
                submergedVolume += v;
                displacedMass += v * rho;
                double fb = rho * gravity * v;
                buoyancyForce += fb;
                double cy = wy - hh + f * hh;
                body.applyForceAtPoint(0, fb, 0, wx, cy, wz);
                maxDepth = Math.max(maxDepth, surface - (wy - hh));
                wetted += el.volume[i] * f;
            }
            body.velocityAtPoint(wx, wy, wz, vel);
            double fx = 0, fy = 0, fz = 0;
            if (f > 0) {
                // radiation / viscous damping of hull motion in water (heave strongest)
                double area = Math.cbrt(el.volume[i] * el.volume[i]);
                double c = rho * area * 1.2 * f;
                fx -= (vel.x - sample[2]) * c * 0.35;
                fy -= vel.y * c;
                fz -= (vel.z - sample[3]) * c * 0.35;
            }
            if (pk < 0) {
                // Newtonian pressure drag on exposed faces
                double airRho = sampler.airDensity(wy);
                int base = i * 6;
                for (int k = 0; k < 6; k++) {
                    float area = el.faceArea[base + k];
                    if (area <= 0) continue;
                    double nlx = k == 0 ? -1 : (k == 1 ? 1 : 0);
                    double nly = k == 2 ? -1 : (k == 3 ? 1 : 0);
                    double nlz = k == 4 ? -1 : (k == 5 ? 1 : 0);
                    double nwx = r.m00 * nlx + r.m10 * nly + r.m20 * nlz;
                    double nwy = r.m01 * nlx + r.m11 * nly + r.m21 * nlz;
                    double nwz = r.m02 * nlx + r.m12 * nly + r.m22 * nlz;
                    double vx = vel.x - (f > 0 ? sample[2] : 0), vy = vel.y, vz = vel.z - (f > 0 ? sample[3] : 0);
                    double vn = vx * nwx + vy * nwy + vz * nwz;
                    if (vn <= 0) continue;
                    double effRho = rho * f + airRho * (1 - f);
                    double mag = 0.5 * effRho * faceDragCoefficient * area * vn * vn;
                    fx -= nwx * mag; fy -= nwy * mag; fz -= nwz * mag;
                }
            }
            if (fx != 0 || fy != 0 || fz != 0) body.applyForceAtPoint(fx, fy, fz, wx, wy, wz);
        }
        wettedFraction = total > 0 ? wetted / total : 0;
    }

    /**
     * Lift from gas pockets: (ρ_air − ρ_gas)·g·V at the pocket centroid.
     *
     * @param gasDensity per gas pocket gas density (kg/m³); pockets with NaN are skipped
     * @param scale gameplay lift scale
     * @return total lift force (N)
     */
    public double applyGasLift(RigidBody body, Vector3d comLocal, FluidElements el, FluidSampler sampler, double[] gasDensity, double[] gasFill, double scale) {
        double total = 0;
        for (int i = 0; i < el.gasPockets.size(); i++) {
            if (gasDensity == null || i >= gasDensity.length || Double.isNaN(gasDensity[i])) continue;
            StructureAnalysis.Pocket p = el.gasPockets.get(i);
            body.localToWorld(p.cx - comLocal.x, p.cy - comLocal.y, p.cz - comLocal.z, tmp);
            double air = sampler.airDensity(tmp.y);
            double fill = gasFill != null && i < gasFill.length ? gasFill[i] : 1.0;
            double lift = (air - gasDensity[i]) * gravity * p.volume * fill * scale;
            if (lift <= 0) continue;
            body.applyForceAtPoint(0, lift, 0, tmp.x, tmp.y, tmp.z);
            total += lift;
        }
        return total;
    }
}
