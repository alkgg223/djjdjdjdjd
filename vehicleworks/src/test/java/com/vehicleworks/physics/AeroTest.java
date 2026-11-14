package com.vehicleworks.physics;

import com.vehicleworks.physics.aero.*;
import com.vehicleworks.physics.propulsion.*;
import com.vehicleworks.physics.ground.TireModel;
import org.joml.Matrix3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AeroTest {
    private static WingParams params() {
        WingParams p = new WingParams();
        p.applyTypeDefaults(WingType.STRAIGHT);
        p.profile = AirfoilProfile.SYMMETRIC;
        p.incidenceDeg = 0;
        return p;
    }

    @Test
    void isaAtmosphere() {
        assertEquals(1.225, Atmosphere.density(0), 1e-3);
        assertEquals(0.3639, Atmosphere.density(11000), 2e-3);
        assertEquals(340.3, Atmosphere.speedOfSound(0), 0.5);
        assertTrue(Atmosphere.density(3000) < Atmosphere.density(1000));
    }

    @Test
    void liftSlopeFollowsFiniteWingTheory() {
        AeroModel m = new AeroModel();
        WingParams p = params();
        double ar = 8;
        m.compute(p, Math.toRadians(4), 0, 0, 0, ar, 0, 0, 99);
        double a0 = p.profile.liftSlope;
        double k = a0 / (Math.PI * ar);
        double helmbold = a0 / (Math.sqrt(1 + k * k) + k);
        assertEquals(helmbold * Math.toRadians(4), m.cl, 0.02);
        // lower aspect ratio → lower slope
        double clHigh = m.cl;
        m.compute(p, Math.toRadians(4), 0, 0, 0, 3, 0, 0, 99);
        assertTrue(m.cl < clHigh);
    }

    @Test
    void stallReducesLiftAndRaisesDrag() {
        AeroModel m = new AeroModel();
        WingParams p = params();
        double best = 0, bestAlpha = 0;
        for (int deg = 0; deg <= 40; deg++) {
            m.compute(p, Math.toRadians(deg), 0, 0, 0, 8, 0, 0, 99);
            if (m.cl > best) { best = m.cl; bestAlpha = deg; }
        }
        assertTrue(bestAlpha >= 11 && bestAlpha <= 18, "clmax angle " + bestAlpha);
        m.compute(p, Math.toRadians(25), 0, 0, 0, 8, 0, 0, 99);
        assertTrue(m.stalled);
        assertTrue(m.cl < best * 0.85, "post-stall lift drop");
        double cdStall = m.cd;
        m.compute(p, Math.toRadians(4), 0, 0, 0, 8, 0, 0, 99);
        assertTrue(cdStall > 3 * m.cd);
    }

    @Test
    void inducedDragAndGroundEffect() {
        AeroModel m = new AeroModel();
        WingParams p = params();
        m.compute(p, Math.toRadians(6), 0, 0, 0, 6, 0, 0, 99);
        double cdiExpected = (1 - m.separation) * m.cl * m.cl / (Math.PI * 6 * p.type.oswald);
        assertEquals(cdiExpected, m.cdi, 1e-6);
        double cdiFree = m.cdi;
        m.compute(p, Math.toRadians(6), 0, 0, 0, 6, 0, 0, 0.05);
        assertTrue(m.cdi < cdiFree * 0.6, "ground effect reduces induced drag");
    }

    @Test
    void flapsIncreaseLiftAndSweepReducesSlope() {
        AeroModel m = new AeroModel();
        WingParams p = params();
        m.compute(p, Math.toRadians(3), 0, 0, 0, 8, 0, 0, 99);
        double clean = m.cl;
        m.compute(p, Math.toRadians(3), 0, Math.toRadians(30), 0, 8, 0, 0, 99);
        assertTrue(m.cl > clean + 0.3);
        m.compute(p, Math.toRadians(3), 0, 0, 0, 8, Math.toRadians(45), 0, 99);
        assertTrue(m.cl < clean * 0.85);
        m.compute(p, Math.toRadians(3), 0, 0, 1.0, 8, 0, 0, 99);
        assertTrue(m.cl < clean * 0.5, "spoilers dump lift");
    }

    @Test
    void wingStripLiftScalesWithDynamicPressure() {
        RigidBody body = new RigidBody();
        body.setMassProperties(1000, new Matrix3d().identity().scale(1000));
        WingParams p = params();
        p.incidenceDeg = 4;
        WingStrip s = new WingStrip(p);
        s.localCenter.set(0, 0, 0);
        s.forward.set(0, 0, -1);
        s.normal.set(0, 1, 0);
        s.area = 10;
        s.aspectRatio = 8;
        s.span = 10;
        org.joml.Vector3d com = new org.joml.Vector3d();
        body.linearVelocity.set(0, 0, -40);
        s.applyForces(body, com, 1.225, 0, 0, 0, 0.1, 1);
        double l1 = body.force.y;
        double expected = 0.5 * 1.225 * 40 * 40 * 10 * s.lastCl;
        assertEquals(expected, s.lastLift, 1e-6);
        assertTrue(l1 > 0);
        body.clearForces();
        body.linearVelocity.set(0, 0, -80);
        s.applyForces(body, com, 1.225, 0, 0, 0, 0.2, 1);
        assertEquals(4.0, body.force.y / l1, 0.15, "lift ∝ V²");
        assertTrue(body.force.z > 0, "drag opposes motion");
    }

    @Test
    void propellerThrustLimits() {
        double p = 200_000;
        double t0 = PropellerModel.thrust(p, 0, 1.225, 2.4, 0.82);
        double t50 = PropellerModel.thrust(p, 50, 1.225, 2.4, 0.82);
        assertTrue(t0 > 4000 && t0 < 12000, "static thrust " + t0);
        assertTrue(t50 < t0);
        assertTrue(t50 * 50 <= p * 0.83);
        double water = PropellerModel.thrust(p, 0, 1000, 1.0, 0.7);
        assertTrue(water > t0, "denser fluid → more static thrust");
    }

    @Test
    void engineStartRequiresStarterForTurbines() {
        EngineSim jet = new EngineSim(EngineType.TURBOJET, 1);
        jet.requestStart();
        jet.update(0.05, 1, 1, 0, 15, false, false);
        assertEquals(EngineSim.State.OFF, jet.state);
        assertEquals("no_starter", jet.lastFailure);
        jet.requestStart();
        jet.throttle = 1;
        for (int i = 0; i < 400; i++) jet.update(0.05, 1, 1, 0, 15, true, false);
        assertEquals(EngineSim.State.RUNNING, jet.state);
        assertTrue(jet.thrustN > 0.8 * EngineType.TURBOJET.maxThrustN, "thrust " + jet.thrustN);
        assertTrue(jet.fuelFlowKgS > 0);
        jet.afterburner = true;
        jet.update(0.05, 1, 1, 0, 15, true, false);
        double ab = jet.thrustN;
        assertTrue(ab > EngineType.TURBOJET.maxThrustN);
        // fuel starvation → flameout
        jet.update(0.05, 0, 1, 0, 15, true, false);
        assertEquals(EngineSim.State.STOPPING, jet.state);
    }

    @Test
    void pistonSpoolsWithThrottleAndLosesPowerAtAltitude() {
        EngineSim e = new EngineSim(EngineType.PISTON, 2);
        e.requestStart();
        e.throttle = 1;
        for (int i = 0; i < 200; i++) e.update(0.05, 1, 1, 30, 15, false, false);
        double sea = e.shaftPowerW;
        for (int i = 0; i < 20; i++) e.update(0.05, 1, 0.6, 30, 15, false, false);
        assertTrue(e.shaftPowerW < sea * 0.65);
        assertTrue(e.temperatureC > 60);
    }

    @Test
    void rotorThrustGrowsWithCollectiveAndGroundEffect() {
        RotorModel r = new RotorModel();
        r.radius = 6; r.chord = 0.45; r.omega = r.nominalOmega;
        r.collective = Math.toRadians(4);
        r.compute(1.225, 0, 0, 100);
        double low = r.lastThrust;
        r.collective = Math.toRadians(10);
        r.compute(1.225, 0, 0, 100);
        double high = r.lastThrust;
        assertTrue(high > 2 * low && low > 0);
        assertTrue(r.lastTorque > 0);
        r.compute(1.225, 0, 0, 2.0);
        assertTrue(r.lastThrust > high * 1.05, "in ground effect");
        // momentum theory check: P_induced ≈ T^1.5 / sqrt(2ρA)
        r.compute(1.225, 0, 0, 100);
        double pIdeal = Math.pow(r.lastThrust, 1.5) / Math.sqrt(2 * 1.225 * r.diskArea());
        double p = r.lastTorque * r.omega;
        assertTrue(p > pIdeal && p < pIdeal * 2.2, "power " + p + " ideal " + pIdeal);
    }

    @Test
    void tireMagicFormulaPeaksAndSaturates() {
        double peak = 0, peakSlip = 0;
        for (int i = 1; i < 100; i++) {
            double s = i / 100.0;
            double f = TireModel.longitudinal(s);
            if (f > peak) { peak = f; peakSlip = s; }
        }
        assertTrue(peakSlip > 0.05 && peakSlip < 0.25);
        assertEquals(1.0, peak, 0.05);
        assertTrue(TireModel.longitudinal(0.9) < peak);
        assertEquals(0.5, TireModel.frictionCircleScale(1.2, 1.6), 1e-9);
    }
}
