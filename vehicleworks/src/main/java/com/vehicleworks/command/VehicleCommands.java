package com.vehicleworks.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.vehicleworks.vehicle.*;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Comparator;

/** /vehicleworks spawn|build|info|disassemble|removeall|stats */
public final class VehicleCommands {
    private VehicleCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d, CommandBuildContext ctx) {
        d.register(Commands.literal("vehicleworks").requires(s -> s.hasPermission(2))
                .then(Commands.literal("spawn").then(Commands.argument("demo", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(DemoVehicles.Demo.values()).map(DemoVehicles.Demo::id), b))
                        .executes(c -> spawn(c, false))))
                .then(Commands.literal("build").then(Commands.argument("demo", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(DemoVehicles.Demo.values()).map(DemoVehicles.Demo::id), b))
                        .executes(c -> spawn(c, true))))
                .then(Commands.literal("info").executes(VehicleCommands::info))
                .then(Commands.literal("disassemble").executes(VehicleCommands::disassemble))
                .then(Commands.literal("removeall").executes(VehicleCommands::removeAll))
                .then(Commands.literal("stats").executes(VehicleCommands::stats))
                .then(Commands.literal("damage").executes(c -> {
                            c.getSource().sendSuccess(() -> Component.translatable("vehicleworks.cmd.damage_now", com.vehicleworks.Config.SERVER.aircraftDamageMultiplier.get(), com.vehicleworks.Config.SERVER.crashDamageScale.get()), false);
                            return 1;
                        })
                        .then(Commands.argument("multiplier", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(1, 10)).requires(s -> s.hasPermission(2)).executes(c -> {
                            double m = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "multiplier");
                            com.vehicleworks.Config.SERVER.aircraftDamageMultiplier.set(m);
                            com.vehicleworks.Config.SERVER_SPEC.save();
                            c.getSource().sendSuccess(() -> Component.translatable("vehicleworks.cmd.damage_set", m), true);
                            return 1;
                        })))
                .then(Commands.literal("emergency")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((c, sb) -> { for (String n : VehicleEntity.EMERGENCIES) sb.suggest(n); sb.suggest("clear"); return sb.buildFuture(); })
                                .executes(c -> {
                                    VehicleEntity v = nearest(c.getSource());
                                    if (v == null) { c.getSource().sendFailure(Component.translatable("vehicleworks.cmd.no_vehicle")); return 0; }
                                    String n = StringArgumentType.getString(c, "name");
                                    if (n.equals("clear")) { v.clearEmergencies(); c.getSource().sendSuccess(() -> Component.translatable("vehicleworks.cmd.emergency_cleared"), true); return 1; }
                                    boolean ok = v.triggerEmergency(n);
                                    if (ok) c.getSource().sendSuccess(() -> Component.translatable("vehicleworks.cmd.emergency", n), true);
                                    else c.getSource().sendFailure(Component.translatable("vehicleworks.cmd.emergency_unknown", n));
                                    return ok ? 1 : 0;
                                })))
                .then(Commands.literal("passengers")
                        .then(Commands.literal("board").executes(c -> {
                            VehicleEntity v = nearest(c.getSource());
                            if (v == null) return 0;
                            int n = v.boardPassengers(500);
                            c.getSource().sendSuccess(() -> Component.translatable("vehicleworks.crew.boarded", n), true);
                            return n;
                        }))
                        .then(Commands.literal("deplane").executes(c -> {
                            VehicleEntity v = nearest(c.getSource());
                            if (v == null) return 0;
                            int n = v.deplanePassengers();
                            c.getSource().sendSuccess(() -> Component.translatable("vehicleworks.crew.deplaned", n), true);
                            return n;
                        })))
                .then(Commands.literal("explode_engine").executes(c -> explodeEngine(c, -1))
                        .then(Commands.argument("engine", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 64))
                                .executes(c -> explodeEngine(c, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "engine") - 1)))));
    }

    /** /vehicleworks explode_engine [n]: blow up engine n (1-based) or all engines of the vehicle you ride / are nearest to. */
    private static int explodeEngine(CommandContext<CommandSourceStack> c, int index) {
        CommandSourceStack src = c.getSource();
        VehicleEntity v = null;
        if (src.getEntity() != null && src.getEntity().getVehicle() instanceof VehicleEntity ve) v = ve;
        if (v == null) {
            double best = 64 * 64;
            for (Entity e : src.getLevel().getEntities((Entity) null, new net.minecraft.world.phys.AABB(src.getPosition(), src.getPosition()).inflate(64), x -> x instanceof VehicleEntity)) {
                double d = e.distanceToSqr(src.getPosition());
                if (d < best) { best = d; v = (VehicleEntity) e; }
            }
        }
        if (v == null) { src.sendFailure(Component.translatable("vehicleworks.cmd.no_vehicle")); return 0; }
        int n = v.explodeEngine(index);
        if (n == 0) { src.sendFailure(Component.translatable("vehicleworks.cmd.no_engine")); return 0; }
        int fn = n;
        src.sendSuccess(() -> Component.translatable("vehicleworks.cmd.engine_exploded", fn), true);
        return n;
    }

    private static DemoVehicles.Demo demo(String s) {
        if (s.equalsIgnoreCase("a320")) return DemoVehicles.Demo.AIRLINER;
        if (s.equalsIgnoreCase("b787") || s.equalsIgnoreCase("787") || s.equalsIgnoreCase("boeing")) return DemoVehicles.Demo.BOEING787;
        for (DemoVehicles.Demo d : DemoVehicles.Demo.values()) if (d.id().equalsIgnoreCase(s)) return d;
        return null;
    }

    private static int spawn(CommandContext<CommandSourceStack> c, boolean asBlocks) {
        CommandSourceStack src = c.getSource();
        DemoVehicles.Demo demo = demo(StringArgumentType.getString(c, "demo"));
        if (demo == null) { src.sendFailure(Component.literal("Unknown demo")); return 0; }
        ServerLevel level = src.getLevel();
        Vec3 p = src.getPosition();
        Direction facing = src.getEntity() != null ? src.getEntity().getDirection() : Direction.NORTH;
        boolean big = demo == DemoVehicles.Demo.AIRLINER || demo == DemoVehicles.Demo.BOEING787;
        int ahead = big ? 30 : (demo == DemoVehicles.Demo.PELICAN || demo == DemoVehicles.Demo.STINGRAY ? 16 : 12);
        BlockPos origin = BlockPos.containing(p.add(facing.getStepX() * ahead, 0, facing.getStepZ() * ahead));
        origin = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin).above(big ? 4 : 3);
        if (demo == DemoVehicles.Demo.BOAT || demo == DemoVehicles.Demo.SUBMARINE) origin = origin.below(2);
        VehicleStructure s = DemoVehicles.build(demo);
        Rotation rot = switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        if (asBlocks || rot != Rotation.NONE) {
            // place as world blocks (rotated toward the player), then optionally assemble
            for (var e : s.blocks().long2ObjectEntrySet()) {
                BlockPos lp = BlockPos.of(e.getLongKey()).rotate(rot);
                BlockPos wp = origin.offset(lp);
                BlockState st = e.getValue().rotate(rot);
                level.setBlock(wp, st, Block.UPDATE_CLIENTS);
                CompoundTag be = s.blockEntity(e.getLongKey());
                BlockEntity wbe = level.getBlockEntity(wp);
                if (be != null && wbe != null) {
                    CompoundTag t = be.copy();
                    t.putInt("x", wp.getX()); t.putInt("y", wp.getY()); t.putInt("z", wp.getZ());
                    wbe.loadWithComponents(t, level.registryAccess());
                    wbe.setChanged();
                }
            }
            if (!asBlocks) {
                var r = VehicleAssembler.assemble(level, origin);
                src.sendSuccess(() -> r.message(), true);
                return r.ok() ? 1 : 0;
            }
            src.sendSuccess(() -> Component.translatable("vehicleworks.msg.demo_built", demo.id()), true);
            return 1;
        }
        VehicleEntity v = VehicleEntity.create(level, s, origin);
        level.addFreshEntity(v);
        src.sendSuccess(() -> Component.translatable("vehicleworks.msg.demo_spawned", demo.id(), s.size(), String.format("%.1f", v.layout().sim.mass() / 1000)), true);
        return 1;
    }

    private static VehicleEntity nearest(CommandSourceStack src) {
        Vec3 p = src.getPosition();
        return VehicleRegistry.all(src.getLevel()).stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(p))).orElse(null);
    }

    private static int info(CommandContext<CommandSourceStack> c) {
        VehicleEntity v = nearest(c.getSource());
        if (v == null) { c.getSource().sendFailure(Component.literal("No vehicle")); return 0; }
        CompoundTag t = EngineeringInfo.build(v);
        c.getSource().sendSuccess(() -> Component.literal(String.format(
                "%s blocks=%d mass=%.0fkg profile=%s wingArea=%.1f staticMargin=%.2f T/W=%.2f stall=%.1fm/s float=%.2f boxes=%d probes=%d strips=%d",
                v.getStringUUID().substring(0, 8), t.getInt("Blocks"), t.getDouble("Mass"), v.profile().id(), t.getDouble("WingArea"),
                t.getDouble("StaticMargin"), t.getDouble("ThrustToWeight"), t.getDouble("StallSpeed"), t.getDouble("FloatMargin"),
                t.getInt("CollisionBoxes"), t.getInt("Probes"), t.getInt("WingStrips"))), false);
        return 1;
    }

    private static int disassemble(CommandContext<CommandSourceStack> c) {
        VehicleEntity v = nearest(c.getSource());
        if (v == null) return 0;
        var r = VehicleAssembler.disassemble(v);
        c.getSource().sendSuccess(r::message, true);
        return r.ok() ? 1 : 0;
    }

    private static int removeAll(CommandContext<CommandSourceStack> c) {
        int n = 0;
        for (VehicleEntity v : VehicleRegistry.all(c.getSource().getLevel())) { v.ejectPassengers(); v.discard(); n++; }
        int fn = n;
        c.getSource().sendSuccess(() -> Component.literal("Removed " + fn + " vehicles"), true);
        return n;
    }

    private static int stats(CommandContext<CommandSourceStack> c) {
        var mgr = VehiclePhysicsManager.get(c.getSource().getLevel());
        c.getSource().sendSuccess(() -> Component.literal(String.format("vehicles=%d substeps=%d contacts=%d step=%.2fms terrainBlocks=%d",
                mgr.world.sims.size(), mgr.world.lastSubsteps, mgr.world.lastContactCount, mgr.lastNanos / 1e6, mgr.env.lastTerrainBlocks)), false);
        return 1;
    }
}
