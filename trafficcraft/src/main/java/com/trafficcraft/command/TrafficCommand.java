package com.trafficcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.trafficcraft.TrafficCraft;
import com.trafficcraft.ai.TrafficManager;
import com.trafficcraft.ai.TrafficStats;
import com.trafficcraft.registry.TCRegistry;
import com.trafficcraft.road.RoadData;
import com.trafficcraft.road.RoadNetwork;
import com.trafficcraft.road.RoadNode;
import com.trafficcraft.road.TrafficLights;
import com.trafficcraft.vehicle.VehicleEntity;
import com.trafficcraft.vehicle.VehicleSpecs;
import com.trafficcraft.world.DemoTown;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** /traffic demo | spawn <model> | clear [ai|all] | stats | lights <auto|flash|off> | debug */
@EventBusSubscriber(modid = TrafficCraft.MODID)
public final class TrafficCommand {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("traffic").requires(s -> s.hasPermission(2))
                .then(Commands.literal("demo").executes(c -> {
                    ServerLevel l = c.getSource().getLevel();
                    BlockPos p = BlockPos.containing(c.getSource().getPosition());
                    DemoTown.build(l, p);
                    c.getSource().sendSuccess(() -> Component.translatable("msg.trafficcraft.demo_built"), true);
                    return 1;
                }))
                .then(Commands.literal("spawn").then(Commands.argument("model", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(VehicleSpecs.ids(), b)).executes(c -> {
                            String m = StringArgumentType.getString(c, "model");
                            if (!VehicleSpecs.exists(m)) return 0;
                            ServerLevel l = c.getSource().getLevel();
                            VehicleEntity v = new VehicleEntity(TCRegistry.VEHICLE.get(), l);
                            v.setModel(m);
                            v.setColor(VehicleSpecs.get(m).colors[0]);
                            v.setFuel(VehicleSpecs.get(m).fuelCapacity);
                            v.setPlate(VehicleEntity.randomPlate(l.random));
                            var pos = c.getSource().getPosition();
                            v.moveTo(pos.x, pos.y, pos.z, c.getSource().getRotation().y, 0);
                            l.addFreshEntity(v);
                            return 1;
                        })))
                .then(Commands.literal("clear").executes(c -> clear(c.getSource(), false)).then(Commands.literal("all").executes(c -> clear(c.getSource(), true))))
                .then(Commands.literal("stats").executes(c -> {
                    ServerLevel l = c.getSource().getLevel();
                    RoadNetwork net = RoadData.get(l);
                    TrafficManager tm = TrafficManager.get(l);
                    c.getSource().sendSuccess(() -> Component.literal("Nodes " + net.nodes.size() + ", edges " + net.edges.size() + ", AI vehicles " + tm.aiCount()
                            + ", spawned " + tm.spawnedTotal + ", despawned " + tm.despawnedTotal), false);
                    c.getSource().sendSuccess(() -> Component.literal(TrafficStats.C.toString()), false);
                    return 1;
                }))
                .then(Commands.literal("lights").then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"auto", "flash", "off", "manual", "next"}, b)).executes(c -> {
                            String mode = StringArgumentType.getString(c, "mode");
                            ServerLevel l = c.getSource().getLevel();
                            RoadNetwork net = RoadData.get(l);
                            for (RoadNode n : net.nodes.values()) if (n.has(RoadNode.F_LIGHTS)) TrafficLights.command(net, n.id, mode, l.getGameTime());
                            return 1;
                        })))
                .then(Commands.literal("debug").executes(c -> {
                    ServerLevel l = c.getSource().getLevel();
                    for (Entity en : l.getAllEntities()) {
                        if (en instanceof VehicleEntity v && v.driver != null && v.distanceToSqr(c.getSource().getPosition()) < 60 * 60) {
                            c.getSource().sendSuccess(() -> Component.literal(v.getModel() + " #" + v.getId() + " " + v.driver.debug() + String.format(" %.0f km/h", v.speed * 3.6)), false);
                        }
                    }
                    return 1;
                })));
    }

    static int clear(CommandSourceStack src, boolean all) {
        int n = 0;
        for (Entity e : src.getLevel().getAllEntities()) {
            if (e instanceof VehicleEntity v && (all || v.getMode() == VehicleEntity.MODE_AI)) {
                v.discard();
                n++;
            }
        }
        int k = n;
        src.sendSuccess(() -> Component.literal("Removed " + k + " vehicles"), true);
        return n;
    }
}
