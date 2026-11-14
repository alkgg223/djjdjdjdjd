package com.vehicleworks.physics.aero;

/**
 * Finite-wing aerodynamic coefficient model.
 * <ul>
 *   <li>Helmbold finite-wing lift slope with simple sweep theory (cos Λ),</li>
 *   <li>Prandtl–Glauert compressibility and Korn-type wave drag,</li>
 *   <li>smooth post-stall blend toward flat-plate normal-force behaviour,</li>
 *   <li>plain-flap / control-surface camber change (Δα₀ = −τδ),</li>
 *   <li>Polhamus vortex lift for delta planforms,</li>
 *   <li>induced drag C<sub>Di</sub> = C<sub>L</sub>²/(πAR·e) with McCormick ground-effect factor,</li>
 *   <li>spoiler lift dumping.</li>
 * </ul>
 */
public final class AeroModel {
    /** Flap effectiveness τ for ~25 % chord plain flaps. */
    public static final double FLAP_EFFECTIVENESS = 0.52;
    /** Effectiveness of a whole-block control surface (all-moving at reduced authority). */
    public static final double CONTROL_EFFECTIVENESS = 0.60;

    public double cl, cd, cdi, clLinear;
    public boolean stalled;
    /** 0 = fully attached, 1 = fully separated. */
    public double separation;

    /**
     * @param alpha         angle of attack (rad), any value in (−π, π]
     * @param controlDefl   control surface deflection (rad, + = trailing edge down)
     * @param flapDefl      flap deflection (rad)
     * @param spoiler       spoiler deployment 0..1
     * @param aspectRatio   panel aspect ratio
     * @param sweepRad      effective leading edge sweep
     * @param mach          flight Mach number
     * @param hOverB        height above ground / span (ground effect); large = none
     */
    public AeroModel compute(WingParams p, double alpha, double controlDefl, double flapDefl, double spoiler,
                             double aspectRatio, double sweepRad, double mach, double hOverB) {
        AirfoilProfile pf = p.profile;
        double cosL = Math.cos(sweepRad);
        double ar = Math.max(0.5, aspectRatio);
        // 2D slope, compressibility corrected (Prandtl-Glauert on normal Mach)
        double mn = Math.min(0.95, mach * cosL);
        double beta = Math.sqrt(Math.max(0.15, 1.0 - mn * mn));
        double a0 = pf.liftSlope * cosL / beta;
        // Helmbold equation for finite wings
        double k = a0 / (Math.PI * ar);
        double a = a0 / (Math.sqrt(1.0 + k * k) + k);
        a *= p.liftScale;

        double tauControl = CONTROL_EFFECTIVENESS * controlDefl + FLAP_EFFECTIVENESS * flapDefl;
        double cl0 = pf.cl0 * p.liftScale * cosL;

        double stall = Math.toRadians(p.stallAngleDeg()) * (1.0 + 0.35 * Math.sin(sweepRad));
        // flaps reduce stall angle a little but raise clmax
        stall -= 0.1 * Math.abs(flapDefl);

        // Fold alpha for attached-flow model (reverse flow treated symmetric)
        double aAtt = alpha;
        double clLin = cl0 + a * (aAtt + tauControl);
        // vortex lift (deltas) — grows with sin²α, only meaningful in attached range
        double vortex = 0.0;
        if (p.type.vortexLiftK > 0) {
            double s = Math.sin(aAtt);
            vortex = p.type.vortexLiftK * Math.signum(s) * s * s * Math.cos(aAtt) * p.liftScale;
        }
        clLin += vortex;
        double clMaxEff = (pf.clMax + 0.9 * Math.abs(flapDefl)) * p.liftScale * (p.type.vortexLiftK > 0 ? 1.35 : 1.0);
        clLin = Math.max(-clMaxEff, Math.min(clMaxEff, clLin));
        this.clLinear = clLin;

        // separation blend (sigmoid around stall angle, width ~2.5°)
        double absA = Math.abs(alpha + controlDefl * 0.25);
        double width = Math.toRadians(1.2);
        double sep = 1.0 / (1.0 + Math.exp(-(absA - stall - Math.toRadians(1.0)) / width));
        if (Math.abs(alpha) > Math.PI / 2) sep = 1.0; // reverse flow
        this.separation = sep;
        this.stalled = sep > 0.5;

        // separated (flat-plate like) flow: Cl ≈ 0.85 sin 2α, Cd ≈ 1.3 sin²α
        double ae = alpha + 0.5 * tauControl;
        double clFp = 0.85 * Math.sin(2 * ae) * Math.max(0.5, Math.min(1.2, p.liftScale));
        double cdFp = 1.3 * Math.sin(ae) * Math.sin(ae);

        double clv = (1.0 - sep) * clLin + sep * clFp;

        // ground effect: induced drag reduction and mild lift increase
        double phi = 1.0;
        if (hOverB < 3.0) {
            double x = 16.0 * Math.max(0.01, hOverB);
            phi = (x * x) / (1.0 + x * x);
            clv *= 1.0 + 0.12 * (1.0 - phi) * (1.0 - sep);
        }

        // spoilers dump lift
        clv *= 1.0 - 0.65 * spoiler;

        double cdProfile = (pf.cd0 + pf.cdAlpha2 * aAtt * aAtt * (1.0 - sep)) * p.dragScale;
        double cdFlap = 1.2 * flapDefl * flapDefl + 0.3 * controlDefl * controlDefl;
        double cdSpoiler = 0.45 * spoiler;
        double e = p.type.oswald;
        double cdInduced = (1.0 - sep) * clv * clv / (Math.PI * ar * e) * phi;
        // wave drag above drag-divergence Mach
        double mdd = pf.dragDivergenceMach / Math.max(0.3, cosL);
        double cdWave = mach > mdd ? 20.0 * Math.pow(mach - mdd, 4) + 0.1 * (mach - mdd) : 0.0;
        double cdTot = cdProfile + cdFlap + cdSpoiler + cdInduced + cdWave;
        cdTot = (1.0 - sep) * cdTot + sep * (cdFp + pf.cd0 + cdSpoiler);

        this.cl = clv;
        this.cd = cdTot;
        this.cdi = cdInduced;
        return this;
    }

    /** Convenience: section lift coefficient curve for UI plots (no controls, AR given). */
    public static double[] polar(WingParams p, double aspectRatio, double alphaDeg) {
        AeroModel m = new AeroModel();
        m.compute(p, Math.toRadians(alphaDeg + p.incidenceDeg), 0, 0, 0, aspectRatio, Math.toRadians(p.sweepDeg), 0.2, 99);
        return new double[]{m.cl, m.cd};
    }
}
