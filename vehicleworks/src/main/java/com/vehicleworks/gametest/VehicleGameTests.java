package com.vehicleworks.gametest;

import com.vehicleworks.VehicleWorks;
import com.vehicleworks.command.DemoVehicles;
import com.vehicleworks.component.ComponentType;
import com.vehicleworks.physics.sim.ThrusterUnit;
import com.vehicleworks.registry.ModBlocks;
import com.vehicleworks.vehicle.*;
import com.vehicleworks.weapon.ProjectileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** In-game tests run on a real server: `gradle runGameTestServer`. */
@GameTestHolder(VehicleWorks.MODID)
@PrefixGameTestTemplate(false)
public final class VehicleGameTests {
    private VehicleGameTests() {}

    static void debug(String name, VehicleEntity v) {
        var s = v.simulation();
        StringBuilder b = new StringBuilder();
        var mgr = com.vehicleworks.vehicle.VehiclePhysicsManager.get((net.minecraft.server.level.ServerLevel) v.level());
        b.append(String.format("terrain=%d ticking=%s loaded=%s floorBlock=%s ", mgr.env.terrainFor(s).count, ((net.minecraft.server.level.ServerLevel) v.level()).isPositionEntityTicking(v.blockPosition()),
                v.level().isLoaded(v.blockPosition()), v.level().getBlockState(v.blockPosition().below(3))));
        b.append(String.format("%s contacts=%d substeps=%d ", name, com.vehicleworks.vehicle.VehiclePhysicsManager.get((net.minecraft.server.level.ServerLevel) v.level()).world.lastContactCount,
                com.vehicleworks.vehicle.VehiclePhysicsManager.get((net.minecraft.server.level.ServerLevel) v.level()).world.lastSubsteps));
        b.append(String.format("%s t=%d pos=(%.1f,%.1f,%.1f) v=%.2f thr=%.2f in.thr=%.2f fuel=%.0f supply=%.2f thrust=%.0f lift=%.0f ground=%s sleeping=%s frozen=%s",
                name, v.tickCount, v.getX(), v.getY(), v.getZ(), s.body.linearVelocity.length(), v.controls.throttle, s.input.throttle, s.fuelAmount, s.fuelSupply, s.totalThrust, s.totalLift, s.onGround, s.body.sleeping, s.frozen));
        var fwd = v.localDirToWorld(0, 0, -1, new org.joml.Vector3d());
        b.append(String.format(" aoa=%.1f pitch=%.1f effPitch=%.2f drag=%.0f vy=%.2f ", Math.toDegrees(s.angleOfAttack), Math.toDegrees(Math.asin(fwd.y)), s.effPitch, s.totalDrag, s.body.linearVelocity.y));
        for (var t : s.thrusters) b.append(String.format(" [%s %s spool=%.2f P=%.0f T=%.0f dev=%s]", t.engine.type, t.engine.state, t.engine.spool, t.engine.shaftPowerW, t.lastThrust, t.device));
        for (var w : s.wheels) b.append(String.format(" {g=%s L=%.0f om=%.1f slip=%.2f br=%.1f drv=%.0f}", w.grounded, w.load, w.omega, w.slipRatio, w.brakeInput, w.driveTorque));
        VehicleWorks.LOGGER.info(b.toString());
    }

    /** Lay a stone runway northward from the test area (the GameTest world is void). */
    static void runway(GameTestHelper h, int startZ, int width, int length) {
        runway(h, startZ, width, length, length + 64);
    }

    static void runway(GameTestHelper h, int startZ, int width, int length, int corridor) {
        BlockPos o = h.absolutePos(new BlockPos(12, 0, startZ));
        var level = h.getLevel();
        var stone = Blocks.SMOOTH_STONE.defaultBlockState();
        for (int z = -12; z < length; z++)
            for (int x = -width / 2; x <= width / 2; x++) {
                level.setBlock(o.offset(x, 0, -z), stone, 2);
                // clear leftovers of neighbouring test structures above the runway
                for (int y = 1; y <= 14; y++) {
                    BlockPos p = o.offset(x, y, -z);
                    if (level.getBlockState(p).is(Blocks.BARRIER)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        // keep the runway chunks entity-ticking (the test server only ticks chunks near tests)
        for (int z = -16; z < corridor; z += 16)
            for (int x = -width / 2 - 40; x <= width / 2 + 40; x += 16) {
                BlockPos p = o.offset(x, 0, -z);
                level.setChunkForced(p.getX() >> 4, p.getZ() >> 4, true);
            }
        // open the test enclosure (barrier wall) along the runway
        BlockPos edge = h.absolutePos(new BlockPos(12, 0, 0));
        for (int x = -width / 2 - 1; x <= width / 2 + 1; x++)
            for (int y = 0; y <= 14; y++)
                for (int z = -4; z <= 1; z++) {
                    BlockPos p = edge.offset(x, y, z);
                    if (level.getBlockState(p).is(Blocks.BARRIER)) level.setBlock(p, y == 0 ? stone : Blocks.AIR.defaultBlockState(), 2);
                }
    }

    private static VehicleEntity spawnDemo(GameTestHelper h, DemoVehicles.Demo demo, BlockPos rel) {
        VehicleStructure s = DemoVehicles.build(demo);
        VehicleEntity v = VehicleEntity.create(h.getLevel(), s, h.absolutePos(rel));
        h.getLevel().addFreshEntity(v);
        return v;
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void assembleAndDisassembleKeepsBlocks(GameTestHelper h) {
        BlockPos core = new BlockPos(10, 3, 10);
        h.setBlock(core, ModBlocks.get(ComponentType.VEHICLE_CORE).defaultBlockState());
        for (int i = 1; i <= 4; i++) h.setBlock(core.east(i), Blocks.OAK_PLANKS);
        h.setBlock(core.north(), Blocks.CHEST);
        if (h.getBlockEntity(core.north()) instanceof ChestBlockEntity chest) chest.setItem(3, new ItemStack(Items.DIAMOND, 7));
        h.setBlock(core.above(), Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.EAST));
        var r = VehicleAssembler.assemble(h.getLevel(), h.absolutePos(core));
        h.assertTrue(r.ok(), "assembly failed: " + r.message().getString());
        h.assertBlockPresent(Blocks.AIR, core);
        h.assertBlockPresent(Blocks.AIR, core.east(2));
        VehicleEntity v = r.vehicle();
        h.assertTrue(v.structure.size() == 7, "expected 7 blocks, got " + v.structure.size());
        h.assertTrue(v.structure.blockEntity(BlockPos.asLong(0, 0, -1)) != null, "chest data kept");
        h.runAfterDelay(60, () -> {
            v.linearVelocity.zero();
            v.angularVelocity.zero();
            if (v.simulation() != null) { v.simulation().body.linearVelocity.zero(); v.simulation().body.angularVelocity.zero(); }
            var d = VehicleAssembler.disassemble(v);
            h.assertTrue(d.ok(), "disassembly failed: " + d.message().getString());
            // find the chest again (the vehicle fell to the floor)
            BlockPos origin = h.absolutePos(BlockPos.ZERO);
            boolean found = false;
            for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(23, 11, 23))) {
                if (h.getLevel().getBlockEntity(p) instanceof ChestBlockEntity c && c.getItem(3).is(Items.DIAMOND) && c.getItem(3).getCount() == 7) found = true;
            }
            h.assertTrue(found, "chest with its diamonds restored");
            h.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void structureFallsAndRestsOnFloor(GameTestHelper h) {
        VehicleEntity v = spawnDemo(h, DemoVehicles.Demo.CAR, new BlockPos(12, 7, 12));
        double floorTop = h.absolutePos(BlockPos.ZERO).getY() + 1;
        h.runAfterDelay(120, () -> {
            double bottom = v.getBoundingBox().minY;
            h.assertTrue(bottom > floorTop - 0.3 && bottom < floorTop + 1.6, "car rests on floor: bottom=" + bottom + " floor=" + floorTop);
            h.assertTrue(v.linearVelocity.length() < 0.5, "at rest: v=" + v.linearVelocity.length());
            h.succeed();
        });
    }

    @GameTest(template = "pool", timeoutTicks = 300)
    public static void boatFloats(GameTestHelper h) {
        VehicleEntity v = spawnDemo(h, DemoVehicles.Demo.BOAT, new BlockPos(12, 7, 12));
        double water = h.absolutePos(BlockPos.ZERO).getY() + 7;
        h.runAfterDelay(200, () -> {
            double bottom = v.getBoundingBox().minY;
            h.assertTrue(bottom < water && bottom > water - 2.5, "boat floats at the surface: bottom=" + bottom + " water=" + water);
            h.assertTrue(v.simulation().inWater, "in water");
            h.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 400, batch = "drive_car")
    public static void carDrivesForward(GameTestHelper h) {
        runway(h, -100, 9, 200);
        VehicleEntity v = spawnDemo(h, DemoVehicles.Demo.CAR, new BlockPos(12, 2, -104));
        v.controls.testPilot = true;
        h.runAfterDelay(20, () -> {
            v.startEngines(true);
        });
        double startZ = v.getZ();
        h.runAfterDelay(80, () -> v.controls.keys = VehicleControls.FORWARD);

        h.runAfterDelay(260, () -> {
            double moved = startZ - v.getZ();
            h.assertTrue(moved > 25, "car drove north: " + moved);
            h.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 900, batch = "flight_trainer")
    public static void trainerTakesOff(GameTestHelper h) {
        runway(h, -100, 17, 700, 1600);
        VehicleEntity v = spawnDemo(h, DemoVehicles.Demo.TRAINER, new BlockPos(12, 3, -104));
        v.controls.testPilot = true;
        v.controls.mouseAim = true;          // fly with the mouse-aim instructor
        v.controls.lookYaw = 180;            // look north along the runway
        v.controls.lookPitch = 0;
        double[] ground = new double[1];
        h.runAfterDelay(30, () -> {
            ground[0] = v.getY();
            v.startEngines(true);
            v.controls.throttle = 1;
            v.controls.flaps = 0.33;
        });
        h.onEachTick(() -> {
            if (v.simulation() != null && v.tickCount % 20 == 0) debug("trainer", v);
            if (v.tickCount == 2) VehicleWorks.LOGGER.info("trainer layout: " + com.vehicleworks.vehicle.EngineeringInfo.build(v));
            if (v.simulation() == null || ground[0] == 0) return;
            boolean fast = v.simulation().airspeed > 33;
            v.controls.keys = VehicleControls.THROTTLE_UP;
            double alt = v.getY() - ground[0];
            v.controls.lookPitch = fast ? (alt < 3 ? -8 : -6) : 0; // rotate, then a sustainable ~5° climb
            if (alt > 6) v.controls.flaps = 0;                    // clean up after lift-off
        });
        h.runAfterDelay(860, () -> {
            double climb = v.getY() - ground[0];
            h.assertTrue(climb > 8, "trainer climbed " + climb + " m (airspeed " + v.simulation().airspeed + ")");
            h.assertTrue(v.simulation().airspeed > 25, "flying speed");
            h.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 900, batch = "flight_fighter")
    public static void fighterTakesOffWithKeyboard(GameTestHelper h) {
        runway(h, -100, 17, 900, 1800);
        VehicleEntity v = spawnDemo(h, DemoVehicles.Demo.FIGHTER, new BlockPos(12, 3, -104));
        v.controls.testPilot = true;
        double[] ground = new double[1];
        h.runAfterDelay(20, () -> {
            ground[0] = v.getY();
            v.startEngines(true);
            v.controls.flaps = 0.33;
            VehicleWorks.LOGGER.info("fighter layout: " + com.vehicleworks.vehicle.EngineeringInfo.build(v));
        });
        h.onEachTick(() -> {
            if (v.simulation() == null || ground[0] == 0) return;
            if (v.tickCount % 40 == 0) debug("fighter", v);
            double alt = v.getY() - ground[0];
            boolean fast = v.simulation().airspeed > 60;
            // Space = throttle up; S (back) to rotate, released once climbing (attitude hold keeps the climb)
            v.controls.keys = VehicleControls.JUMP | (fast && alt < 6 ? VehicleControls.BACK : 0);
            if (alt > 8) { v.controls.gearDown = false; v.controls.flaps = 0; }
        });
        h.runAfterDelay(860, () -> {
            double climb = v.getY() - ground[0];
            h.assertTrue(climb > 15, "fighter climbed " + climb + " m (airspeed " + v.simulation().airspeed + ")");
            h.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 1400, batch = "flight_airliner")
    public static void airlinerTakesOffWithKeyboard(GameTestHelper h) {
        runway(h, -100, 33, 1400, 2600);
        VehicleEntity v = spawnDemo(h, DemoVehicles.Demo.AIRLINER, new BlockPos(12, 5, -116)); // outside the test enclosure
        v.controls.testPilot = true;
        double[] ground = new double[1];
        h.runAfterDelay(20, () -> {
            ground[0] = v.getY();
            v.startEngines(true);
            v.controls.flaps = 0.33;
            VehicleWorks.LOGGER.info("airliner layout: " + com.vehicleworks.vehicle.EngineeringInfo.build(v));
        });
        h.onEachTick(() -> {
            if (v.simulation() == null || ground[0] == 0) return;
            if (v.tickCount % 40 == 0) debug("airliner", v);
            double alt = v.getY() - ground[0];
            boolean fast = v.simulation().airspeed > 62;
            v.controls.keys = (v.controls.throttle < 1 ? VehicleControls.JUMP : 0) | (fast && alt < 5 ? VehicleControls.BACK : 0);
            if (alt > 10) { v.controls.gearDown = false; v.controls.flaps = 0; }
        });
        h.runAfterDelay(1360, () -> {
            double climb = v.getY() - ground[0];
            var up = v.localDirToWorld(0, 1, 0, new org.joml.Vector3d());
            h.assertTrue(climb > 20, "airliner climbed " + climb + " m (airspeed " + v.simulation().airspeed + ")");
            h.assertTrue(up.y > 0.8, "airliner wings level: " + up.y);
            h.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 1500, batch = "flight_b787")
    public static void boeing787TakesOffEasyMode(GameTestHelper h) {
        runway(h, -100, 33, 1400, 2600);
        VehicleEntity v = spawnDemo(h, DemoVehicles.Demo.BOEING787, new BlockPos(12, 5, -118));
        v.controls.testPilot = true;
        double[] ground = new double[1];
        h.runAfterDelay(60, () -> {
            ground[0] = v.getY();
            v.startEngines(true);
            VehicleWorks.LOGGER.info("b787 layout: " + com.vehicleworks.vehicle.EngineeringInfo.build(v));
        });
        h.onEachTick(() -> {
            if (v.simulation() == null || ground[0] == 0) return;
            if (v.tickCount % 40 == 0) debug("b787", v);
            double alt = v.getY() - ground[0];
            // only Space: the easy-flight assist rotates by itself; bank left after climbing
            v.controls.keys = (v.controls.throttle < 1 ? VehicleControls.JUMP : 0);
            if (alt > 10) v.controls.gearDown = false;
            if (alt > 40 && !v.controls.autopilot) {
                v.controls.autopilot = true;
                v.controls.apHeading = 90;
                v.controls.apAltitude = ground[0] + 150;
            }
        });
        h.runAfterDelay(1460, () -> {
            double climb = v.getY() - ground[0];
            var fw = v.localDirToWorld(0, 0, -1, new org.joml.Vector3d());
            double hdg = (Math.toDegrees(Math.atan2(fw.x, -fw.z)) + 360) % 360;
            VehicleWorks.LOGGER.info("b787 autopilot result: heading " + hdg + " alt " + climb);
            h.assertTrue(climb > 20, "b787 climbed " + climb + " m (airspeed " + v.simulation().airspeed + ")");
            h.assertTrue(hdg > 5 && hdg < 120, "autopilot turning towards 090: heading " + hdg); // leaves the loaded test area while turning
            h.assertTrue(Math.abs(climb - 150) < 50, "autopilot altitude " + climb);
            h.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 1000, batch = "flight_pelican")
    public static void pelicanTakesOffEasyMode(GameTestHelper h) {
        runway(h, -100, 33, 1000, 1800);
        VehicleEntity v = spawnDemo(h, DemoVehicles.Demo.PELICAN, new BlockPos(12, 3, -104));
        v.controls.testPilot = true;
        double[] ground = new double[1];
        h.runAfterDelay(30, () -> {
            ground[0] = v.getY();
            v.startEngines(true);
            VehicleWorks.LOGGER.info("pelican layout: " + com.vehicleworks.vehicle.EngineeringInfo.build(v));
        });
        h.onEachTick(() -> {
            if (v.simulation() == null || ground[0] == 0) return;
            if (v.tickCount % 40 == 0) debug("pelican", v);
            double alt = v.getY() - ground[0];
            v.controls.keys = (v.controls.throttle < 1 ? VehicleControls.JUMP : 0);
            if (alt > 10) v.controls.gearDown = false;
        });
        h.runAfterDelay(900, () -> {
            double climb = v.getY() - ground[0];
            var up = v.localDirToWorld(0, 1, 0, new org.joml.Vector3d());
            h.assertTrue(climb > 15, "pelican climbed " + climb + " m (airspeed " + v.simulation().airspeed + ")");
            h.assertTrue(up.y > 0.8, "pelican wings level: " + up.y);
            h.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 1000, batch = "flight_stingray")
    public static void stingrayTakesOffEasyMode(GameTestHelper h) {
        runway(h, -100, 33, 1000, 1800);
        VehicleEntity v = spawnDemo(h, DemoVehicles.Demo.STINGRAY, new BlockPos(12, 3, -104));
        v.controls.testPilot = true;
        double[] ground = new double[1];
        h.runAfterDelay(30, () -> {
            ground[0] = v.getY();
            v.startEngines(true);
            VehicleWorks.LOGGER.info("stingray layout: " + com.vehicleworks.vehicle.EngineeringInfo.build(v));
        });
        h.onEachTick(() -> {
            if (v.simulation() == null || ground[0] == 0) return;
            if (v.tickCount % 40 == 0) debug("stingray", v);
            double alt = v.getY() - ground[0];
            v.controls.keys = (v.controls.throttle < 1 ? VehicleControls.JUMP : 0);
            if (alt > 10) v.controls.gearDown = false;
        });
        h.runAfterDelay(900, () -> {
            double climb = v.getY() - ground[0];
            var up = v.localDirToWorld(0, 1, 0, new org.joml.Vector3d());
            h.assertTrue(climb > 15, "stingray climbed " + climb + " m (airspeed " + v.simulation().airspeed + ")");
            h.assertTrue(up.y > 0.8, "stingray wings level: " + up.y);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ cockpit systems

    static boolean allRunning(VehicleEntity v) {
        return v.simulation().thrusters.stream().allMatch(t -> t.engine.isRunning());
    }

    static boolean noneRunning(VehicleEntity v) {
        return v.simulation().thrusters.stream().noneMatch(t -> t.engine.isRunning());
    }

    static void log(VehicleEntity v, String what) {
        StringBuilder b = new StringBuilder(what);
        b.append(String.format(" bus=%s starter=%d", v.controls.busPowered, v.controls.starterTicks));
        for (var t : v.simulation().thrusters) b.append(String.format(" [%s %s %s spool=%.3f egt=%.0f fail=%s ign=%s fuel=%s sup=%.2f gf=%.2f]", t.engine.type, t.engine.state, t.engine.phase, t.engine.spool, t.engine.temperatureC, t.engine.lastFailure, t.engine.ignitionCmd, t.engine.fuelCmd, v.simulation().fuelSupply, t.gradeFactor));
        VehicleWorks.LOGGER.info(b.toString());
    }

    /** New aircraft are cold and dark; a piston engine cranks on the starter and catches only with magnetos and fuel. */
    @GameTest(template = "platform", timeoutTicks = 600, batch = "systems")
    public static void pistonStartFollowsTheProcedure(GameTestHelper h) {
        VehicleEntity v = spawnDemo(h, DemoVehicles.Demo.TRAINER, new BlockPos(12, 3, 12));
        v.controls.testPilot = true;
        h.runAfterDelay(20, () -> {
            h.assertTrue(!v.controls.battery && !v.controls.masterElec && !v.controls.busPowered, "spawns cold and dark");
            h.assertTrue("vehicleworks.start.no_power".equals(v.engageStarter()), "no battery: starter refused");
            v.controls.battery = true;
        });
        h.runAfterDelay(25, () -> {
            h.assertTrue("vehicleworks.start.no_bus".equals(v.engageStarter()), "master off: no bus");
            v.controls.masterElec = true;
        });
        h.runAfterDelay(30, () -> {
            h.assertTrue(v.controls.busPowered, "battery + master power the bus");
            h.assertTrue(v.engageStarter() == null, "starter engages");
        });
        h.runAfterDelay(90, () -> {
            log(v, "piston cranking, no ignition/fuel:");
            var e = v.simulation().thrusters.get(0).engine;
            h.assertTrue(e.state == com.vehicleworks.physics.propulsion.EngineSim.State.STARTING && e.phase == com.vehicleworks.physics.propulsion.EngineSim.Phase.CRANK, "cranking");
            h.assertTrue(e.spool > 0.06 && e.spool < 0.12, "turned by the starter only: " + e.spool);
            v.controls.ignition = true;
            v.controls.engineMaster = true;
            v.controls.fuelPump = true;
        });
        h.runAfterDelay(200, () -> {
            log(v, "piston after ignition + fuel:");
            h.assertTrue(allRunning(v), "piston catches and runs");
            h.assertTrue(v.controls.starterTicks == 0, "starter released once running");
            h.assertTrue(v.controls.generator, "alternator online");
            v.controls.ignition = false;
        });
        h.runAfterDelay(320, () -> {
            h.assertTrue(noneRunning(v), "magnetos off stop a piston engine");
            h.succeed();
        });
    }

    /** Turbofan: starter spools N2, fuel + igniters light it, it accelerates, the starter cuts out; igniters off do not stop it. */
    @GameTest(template = "platform", timeoutTicks = 1200, batch = "systems")
    public static void jetStartFollowsTheProcedure(GameTestHelper h) {
        VehicleEntity v = spawnDemo(h, DemoVehicles.Demo.AIRLINER, new BlockPos(12, 3, 12));
        v.controls.testPilot = true;
        h.runAfterDelay(20, () -> {
            v.controls.battery = v.controls.masterElec = v.controls.fuelPump = v.controls.avionics = true;
        });
        h.runAfterDelay(25, () -> h.assertTrue(v.engageStarter() == null, "starter engages"));
        h.runAfterDelay(200, () -> {
            log(v, "turbofan dry crank:");
            for (var t : v.simulation().thrusters) {
                h.assertTrue(t.engine.phase == com.vehicleworks.physics.propulsion.EngineSim.Phase.CRANK, "still cranking without fuel");
                h.assertTrue(t.engine.spool > t.engine.type.idleFraction * 0.3, "starter spools N2: " + t.engine.spool);
            }
            v.controls.ignition = true;
            v.controls.engineMaster = true;
        });
        h.runAfterDelay(240, () -> {
            log(v, "turbofan light-off:");
            for (var t : v.simulation().thrusters)
                h.assertTrue(t.engine.phase != com.vehicleworks.physics.propulsion.EngineSim.Phase.CRANK, "light-off after fuel + igniters");
        });
        h.runAfterDelay(800, () -> {
            log(v, "turbofan after start:");
            h.assertTrue(allRunning(v), "turbofans at stable idle");
            h.assertTrue(v.controls.starterTicks == 0, "starter cut out");
            v.controls.ignition = false;
        });
        h.runAfterDelay(860, () -> {
            h.assertTrue(allRunning(v), "a running turbine does not need the igniters");
            v.controls.engineMaster = false;
        });
        h.runAfterDelay(1000, () -> {
            log(v, "turbofan after fuel cut-off:");
            h.assertTrue(noneRunning(v), "engine master off shuts the fuel and the engines");
            h.succeed();
        });
    }

    /** Passengers adjust only their own seat; the cockpit switches answer the flight crew only. */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "systems")
    public static void passengersHaveNoCockpitAccess(GameTestHelper h) {
        VehicleEntity v = spawnDemo(h, DemoVehicles.Demo.AIRLINER, new BlockPos(12, 3, 12));
        var sp = h.makeMockServerPlayerInLevel();
        long business = BlockPos.asLong(-1, 1, -7), pilot = BlockPos.asLong(-1, 1, -11);
        h.runAfterDelay(20, () -> {
            sp.teleportTo(v.getX(), v.getY(), v.getZ());
            h.assertTrue(v.trySit(sp, business), "sits in business class");
            h.assertTrue(v.isPassengerSeated(sp) && v.seatClassOf(sp) == 4, "business class passenger");
            boolean before = v.controls.battery;
            com.vehicleworks.network.ServerActions.onAction(sp, new com.vehicleworks.network.Payloads.Action(v.getId(), com.vehicleworks.network.Payloads.ACT_SWITCH, VehicleControls.SW_BATTERY));
            h.assertTrue(v.controls.battery == before, "a passenger cannot switch the battery");
            v.cabinControl(sp, com.vehicleworks.vehicle.CabinSeat.RECLINE_DOWN);
            v.cabinControl(sp, com.vehicleworks.vehicle.CabinSeat.FLAT);
            v.cabinControl(sp, com.vehicleworks.vehicle.CabinSeat.DOOR); // business has no door
            int st = v.cabin.get(business);
            h.assertTrue(com.vehicleworks.vehicle.CabinSeat.flat(st) && com.vehicleworks.vehicle.CabinSeat.recline(st) == 10, "lie-flat: " + st);
            h.assertTrue(!com.vehicleworks.vehicle.CabinSeat.door(st), "no door on a business seat");
            sp.stopRiding();
            h.assertTrue(v.trySit(sp, pilot), "sits in the captain seat");
            com.vehicleworks.network.ServerActions.onAction(sp, new com.vehicleworks.network.Payloads.Action(v.getId(), com.vehicleworks.network.Payloads.ACT_SWITCH, VehicleControls.SW_BATTERY));
            h.assertTrue(v.controls.battery != before, "the pilot switches the battery");
            h.assertTrue(v.simulation().fbw, "A320 flies with side sticks (fly-by-wire)");
            h.succeed();
        });
    }

    /** Releasing the starter before self-sustaining speed gives a hung start. */
    @GameTest(template = "platform", timeoutTicks = 900, batch = "systems")
    public static void jetHungStartWhenStarterReleasedEarly(GameTestHelper h) {
        VehicleEntity v = spawnDemo(h, DemoVehicles.Demo.FIGHTER, new BlockPos(12, 3, 12));
        v.controls.testPilot = true;
        h.runAfterDelay(20, () -> {
            v.controls.battery = v.controls.masterElec = v.controls.fuelPump = v.controls.ignition = v.controls.engineMaster = true;
        });
        h.runAfterDelay(25, () -> h.assertTrue(v.engageStarter() == null, "starter engages"));
        h.runAfterDelay(110, () -> {
            log(v, "jet before starter release:");
            var e = v.simulation().thrusters.get(0).engine;
            h.assertTrue(e.state == com.vehicleworks.physics.propulsion.EngineSim.State.STARTING, "starting");
            h.assertTrue("vehicleworks.start.released".equals(v.engageStarter()), "starter released");
        });
        h.runAfterDelay(700, () -> {
            log(v, "jet after early release:");
            h.assertTrue(noneRunning(v), "hung start: never reaches idle");
            h.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 300, batch = "systems")
    public static void batteryDrainsWithLoadAndPowerFails(GameTestHelper h) {
        VehicleEntity v = spawnDemo(h, DemoVehicles.Demo.PELICAN, new BlockPos(12, 3, 12));
        v.controls.testPilot = true;
        h.runAfterDelay(10, () -> {
            v.controls.batteryCharge = 0.002;
            v.controls.battery = v.controls.masterElec = true;
            v.controls.landingLights = true; v.controls.taxiLights = true; v.controls.lights = true; v.controls.beacon = true; v.controls.strobe = true;
        });
        h.runAfterDelay(200, () -> {
            h.assertTrue(v.controls.batteryCharge <= 1e-6, "battery drained: " + v.controls.batteryCharge);
            h.assertTrue(!v.controls.busPowered, "bus unpowered without battery and generator");
            h.assertTrue(v.engageStarter() != null, "flat battery cannot start");
            h.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 600, batch = "systems")
    public static void refuelPortFillsTanksGradually(GameTestHelper h) {
        VehicleEntity v = spawnDemo(h, DemoVehicles.Demo.PELICAN, new BlockPos(12, 3, 12));
        v.controls.testPilot = true;
        var sp = h.makeMockServerPlayerInLevel();
        sp.getAbilities().instabuild = true;
        double[] f0 = new double[1];
        h.runAfterDelay(20, () -> {
            sp.teleportTo(v.getX(), v.getY(), v.getZ());
            for (var t : v.simulation().tanks) { t.amount = 10; t.fuelType = 0; }
            f0[0] = v.simulation().tanks.stream().mapToDouble(t -> t.amount).sum();
            v.startRefuel(sp, 2, 400);
        });
        h.runAfterDelay(80, () -> {
            double f = v.simulation().tanks.stream().mapToDouble(t -> t.amount).sum();
            h.assertTrue(f > f0[0] + 20 && f < f0[0] + 400, "fuel rises gradually: " + f0[0] + " -> " + f);
            h.assertTrue(v.isRefueling(), "still refuelling");
        });
        h.runAfterDelay(560, () -> {
            double f = v.simulation().tanks.stream().mapToDouble(t -> t.amount).sum();
            h.assertTrue(Math.abs(f - (f0[0] + 400)) < 2, "stops at the requested amount: " + f);
            h.assertTrue(!v.isRefueling(), "pump stopped");
            h.assertTrue(v.simulation().tanks.stream().anyMatch(t -> t.fuelType == 2), "JET-A in the tanks");
            h.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 200, batch = "systems")
    public static void wrongFuelGradeStopsCombustion(GameTestHelper h) {
        VehicleEntity v = spawnDemo(h, DemoVehicles.Demo.PELICAN, new BlockPos(12, 3, 12)); // turboprops: JET-A
        v.controls.testPilot = true;
        h.runAfterDelay(10, () -> { for (var t : v.simulation().tanks) t.fuelType = 1; }); // AVGAS in a turbine
        h.runAfterDelay(30, () -> {
            h.assertTrue(v.simulation().wrongFuel, "wrong fuel grade detected");
            h.assertTrue(v.simulation().thrusters.stream().allMatch(t -> t.gradeFactor == 0), "no combustion with the wrong grade");
            h.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 120, batch = "systems")
    public static void damageMultiplierScalesDamageOnce(GameTestHelper h) {
        VehicleEntity a = spawnDemo(h, DemoVehicles.Demo.CAR, new BlockPos(6, 3, 6));
        VehicleEntity b = spawnDemo(h, DemoVehicles.Demo.CAR, new BlockPos(18, 3, 18));
        long key = BlockPos.asLong(1, 0, 1);
        double old = com.vehicleworks.Config.SERVER.aircraftDamageMultiplier.get();
        h.runAfterDelay(10, () -> {
            com.vehicleworks.Config.SERVER.aircraftDamageMultiplier.set(1.0);
            a.damageBlock(key, 2f);
        });
        h.runAfterDelay(12, () -> {
            com.vehicleworks.Config.SERVER.aircraftDamageMultiplier.set(5.0);
            b.damageBlock(key, 2f);
        });
        h.runAfterDelay(16, () -> {
            com.vehicleworks.Config.SERVER.aircraftDamageMultiplier.set(old);
            float da = a.structure.damage(key), db = b.structure.damage(key);
            h.assertTrue(Math.abs(da - 2f) < 1e-3, "1x damage = 2: " + da);
            h.assertTrue(Math.abs(db - 10f) < 1e-3, "5x damage = 10 (not 50): " + db);
            h.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void wingTypesHaveDifferentAerodynamics(GameTestHelper h) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (var wt : com.vehicleworks.physics.aero.WingType.values()) {
            var p = new com.vehicleworks.physics.aero.WingParams().applyTypeDefaults(wt);
            double[] c8 = com.vehicleworks.physics.aero.AeroModel.polar(p, 6, 8);
            double[] c20 = com.vehicleworks.physics.aero.AeroModel.polar(p, 6, 20);
            seen.add(String.format("%.4f/%.4f/%.4f", c8[0], c8[1], c20[0]));
        }
        h.assertTrue(seen.size() == com.vehicleworks.physics.aero.WingType.values().length, "every wing type has its own polar: " + seen.size());
        h.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 400, batch = "walk")
    public static void entityIsCarriedByMovingVehicle(GameTestHelper h) {
        runway(h, -100, 9, 200);
        VehicleEntity v = spawnDemo(h, DemoVehicles.Demo.CAR, new BlockPos(12, 2, -104));
        v.controls.testPilot = true;
        var stand = new net.minecraft.world.entity.decoration.ArmorStand(h.getLevel(), 0, 0, 0);
        h.runAfterDelay(30, () -> {
            var top = v.localToWorld(1.5, 1.05, 1.5, new org.joml.Vector3d());
            stand.setPos(top.x, top.y, top.z);
            h.getLevel().addFreshEntity(stand);
            v.startEngines(true);
        });
        h.runAfterDelay(80, () -> v.controls.keys = VehicleControls.FORWARD);
        h.runAfterDelay(300, () -> {
            var local = v.worldToLocal(stand.getX(), stand.getY(), stand.getZ(), new org.joml.Vector3d());
            double moved = v.getZ();
            h.assertTrue(v.getDeltaMovement() != null, "ok");
            h.assertTrue(Math.abs(local.x - 1.5) < 1.2 && Math.abs(local.z - 1.5) < 1.2 && local.y > 0.8 && local.y < 1.6,
                    "armor stand stays on the moving car (local " + local + ")");
            h.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 700, batch = "flight_heli")
    public static void helicopterLiftsOff(GameTestHelper h) {
        VehicleEntity v = spawnDemo(h, DemoVehicles.Demo.HELICOPTER, new BlockPos(12, 3, 12));
        v.controls.testPilot = true;
        double[] ground = new double[1];
        h.runAfterDelay(20, () -> {
            ground[0] = v.getY();
            v.startEngines(true);
            VehicleWorks.LOGGER.info("heli layout: " + com.vehicleworks.vehicle.EngineeringInfo.build(v));
        });
        // hold Space (collective up) after the rotor spun up on its own
        // Space: climb at 5 m/s; release: hover; then W to fly forward, release to stop
        h.runAfterDelay(260, () -> v.controls.keys = VehicleControls.JUMP);
        h.runAfterDelay(300, () -> v.controls.keys = 0);
        double[] hoverY = new double[1];
        h.runAfterDelay(420, () -> { hoverY[0] = v.getY(); v.controls.keys = VehicleControls.FORWARD; });
        h.onEachTick(() -> { if (v.simulation() != null && v.tickCount % 20 == 0) debug("heli", v); });
        h.runAfterDelay(500, () -> {
            var fw = v.localDirToWorld(0, 0, -1, new org.joml.Vector3d());
            double vf = v.linearVelocity.x * fw.x + v.linearVelocity.z * fw.z;
            h.assertTrue(vf > 4, "flies forward with W: " + vf);
            h.assertTrue(Math.abs(v.getY() - hoverY[0]) < 4, "holds altitude while flying forward: " + (v.getY() - hoverY[0]));
            v.controls.keys = 0;
        });
        h.runAfterDelay(660, () -> {
            double climb = v.getY() - ground[0];
            var up = v.localDirToWorld(0, 1, 0, new org.joml.Vector3d());
            h.assertTrue(climb > 5, "helicopter hovering " + climb + " m above ground");
            h.assertTrue(up.y > 0.9, "helicopter stays upright: " + up.y);
            h.assertTrue(v.linearVelocity.length() < 3, "comes to a hover when released: " + v.linearVelocity.length());
            h.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void weaponsFireThroughRemoteControl(GameTestHelper h) {
        VehicleEntity v = spawnDemo(h, DemoVehicles.Demo.FIGHTER, new BlockPos(12, 3, 12));
        v.controls.testPilot = true;
        h.runAfterDelay(10, () -> {
            h.assertTrue(v.weapons.slots().size() == 4, "4 weapons, got " + v.weapons.slots().size());
            h.assertTrue(v.weapons.slots().stream().allMatch(s -> s.channel >= 0 && s.def.mounted), "all weapons mounted and linked");
            v.controls.armed = true;
            v.controls.keys = 1 << VehicleControls.FIRE_SHIFT; // channel 1: guns
        });
        h.runAfterDelay(30, () -> {
            List<ProjectileEntity> shots = h.getLevel().getEntitiesOfClass(ProjectileEntity.class, new AABB(v.blockPosition()).inflate(400));
            int ammo = v.weapons.slots().stream().filter(s -> s.def.type == ComponentType.MACHINE_GUN).mapToInt(s -> s.ammo).sum();
            h.assertTrue(ammo < 500, "machine guns consumed ammunition: " + ammo);
            h.assertTrue(v.weapons.slots().stream().filter(s -> s.def.type == ComponentType.MISSILE_LAUNCHER).allMatch(s -> s.ammo == 2), "missiles not fired on channel 1");
            v.controls.keys = 0;
            v.controls.armed = false;
            h.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void safetyPreventsAccidentalFire(GameTestHelper h) {
        VehicleEntity v = spawnDemo(h, DemoVehicles.Demo.FIGHTER, new BlockPos(12, 3, 12));
        v.controls.testPilot = true;
        h.runAfterDelay(5, () -> v.controls.keys = (1 << VehicleControls.FIRE_SHIFT) | (2 << VehicleControls.FIRE_SHIFT));
        h.runAfterDelay(40, () -> {
            int ammo = v.weapons.slots().stream().mapToInt(s -> s.ammo).sum();
            h.assertTrue(ammo == 250 * 2 + 2 * 2 || ammo >= 500, "nothing fired while master arm is safe: " + ammo);
            h.succeed();
        });
    }
}
