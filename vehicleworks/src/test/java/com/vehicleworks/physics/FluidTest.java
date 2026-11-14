package com.vehicleworks.physics;

import com.vehicleworks.physics.fluid.StructureAnalysis;
import com.vehicleworks.physics.sim.PhysicsWorld;
import com.vehicleworks.physics.sim.VehicleSimulation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FluidTest {

    private static VoxelSet shellFlags(int sx, int sy, int sz, boolean top, boolean holeBottom, int extra) {
        VoxelSet v = new VoxelSet();
        for (int x = 0; x < sx; x++)
            for (int y = 0; y < sy; y++)
                for (int z = 0; z < sz; z++) {
                    boolean wall = x == 0 || z == 0 || x == sx - 1 || z == sz - 1 || y == 0 || (top && y == sy - 1);
                    if (holeBottom && y == 0 && x == sx / 2 && z == sz / 2) wall = false;
                    if (wall) v.put(x, y, z, StructureAnalysis.FLAG_SOLID | extra);
                }
        return v;
    }

    @Test
    void sealedOpenAndHoledHulls() {
        StructureAnalysis sealed = new StructureAnalysis(shellFlags(6, 4, 6, true, false, 0), 1 << 22);
        assertEquals(1, sealed.pockets.size());
        assertTrue(sealed.pockets.get(0).sealed);
        assertEquals(4 * 2 * 4, sealed.pockets.get(0).volume, 1e-9);

        StructureAnalysis open = new StructureAnalysis(shellFlags(6, 4, 6, false, false, 0), 1 << 22);
        assertEquals(1, open.pockets.size());
        assertFalse(open.pockets.get(0).sealed);
        assertEquals(4 * 3 * 4, open.pockets.get(0).volume, 1e-9);
        assertTrue(open.pockets.get(0).rim.length > 0);

        StructureAnalysis holed = new StructureAnalysis(shellFlags(6, 4, 6, false, true, 0), 1 << 22);
        assertEquals(0, holed.pockets.size(), "water enters through the hole");
    }

    @Test
    void balloonWithOpenMouthHoldsGas() {
        // envelope shell open at the bottom (mouth)
        VoxelSet v = new VoxelSet();
        int n = 7;
        for (int x = 0; x < n; x++) for (int y = 0; y < n; y++) for (int z = 0; z < n; z++) {
            boolean wall = x == 0 || z == 0 || x == n - 1 || z == n - 1 || y == n - 1 || y == 0;
            if (y == 0 && x >= 2 && x <= 4 && z >= 2 && z <= 4) wall = false; // mouth
            if (wall) v.put(x, y, z, StructureAnalysis.FLAG_SOLID | StructureAnalysis.FLAG_ENVELOPE);
        }
        StructureAnalysis a = new StructureAnalysis(v, 1 << 22);
        assertEquals(1, a.gasPockets.size());
        // interior plus the 3x3 mouth layer (gas fills down to the lower edge of the opening)
        assertEquals(5 * 5 * 5 + 9, a.gasPockets.get(0).volume, 1e-9);
        assertTrue(a.gasPockets.get(0).envelopeFraction > 0.9);
        assertEquals(0, a.pockets.size(), "open bottom: water would enter");
    }

    private static double settle(VehicleSimulation s, TestEnv env, int ticks) {
        PhysicsWorld w = new PhysicsWorld();
        w.sims.add(s);
        for (int i = 0; i < ticks; i++) w.step(0.05, env);
        return s.body.position.y;
    }

    @Test
    void woodenBlockFloatsAtArchimedesDraft() {
        TestEnv env = new TestEnv();
        env.ground = false;
        env.waterY = 0;
        SimBuilder sb = new SimBuilder();
        sb.box(0, 0, 0, 3, 3, 3, 500); // density 500 kg/m³
        VehicleSimulation s = sb.build(0, 3, 0);
        double y = settle(s, env, 600);
        // half submerged: COM at the waterline
        assertEquals(0.0, y, 0.25, "COM y");
        assertTrue(Math.abs(s.body.linearVelocity.y) < 0.1);
    }

    @Test
    void solidIronSinks() {
        TestEnv env = new TestEnv();
        env.groundY = -30;
        env.waterY = 0;
        SimBuilder sb = new SimBuilder();
        sb.box(0, 0, 0, 1, 1, 1, 2500);
        VehicleSimulation s = sb.build(0, 2, 0);
        double y = settle(s, env, 300);
        assertTrue(y < -10, "sank to " + y);
    }

    @Test
    void openSteelHullFloatsButHoledHullSinks() {
        TestEnv env = new TestEnv();
        env.groundY = -40;
        env.waterY = 0;
        SimBuilder ok = new SimBuilder();
        ok.shell(0, 0, 0, 5, 3, 5, 1200, false);
        VehicleSimulation s = ok.build(0, 1, 0);
        double y = settle(s, env, 600);
        assertTrue(y > -2.5, "intact hull floats, y=" + y);

        SimBuilder bad = new SimBuilder();
        for (int x = 0; x <= 5; x++) for (int y2 = 0; y2 <= 3; y2++) for (int z = 0; z <= 5; z++) {
            boolean wall = x == 0 || x == 5 || z == 0 || z == 5 || y2 == 0;
            if (y2 == 0 && x == 2 && z == 2) wall = false;
            if (wall) bad.voxel(x, y2, z, 1200);
        }
        VehicleSimulation h = bad.build(0, 1, 0);
        double yh = settle(h, env, 600);
        assertTrue(yh < -10, "holed hull sinks, y=" + yh);
    }
}
