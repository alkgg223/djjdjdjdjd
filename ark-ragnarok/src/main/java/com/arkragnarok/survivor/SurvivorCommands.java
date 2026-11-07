package com.arkragnarok.survivor;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.survivor.world.SurvivorWorld;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Managing the survivors from chat: /ark tribes (every tribe: temperament, stage, design and how far it is built,
 * stores, dinosaurs, raids, relations), /ark designs (the blueprints), /ark design &lt;id&gt; (one blueprint: rooms,
 * pieces, defences), /ark assign &lt;id&gt; (the nearest survivor builds that design; it moves its base if it must) and
 * /ark auto (it chooses again by itself).
 */
@EventBusSubscriber(modid = ArkRagnarok.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SurvivorCommands {
    private SurvivorCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("ark")
                .then(Commands.literal("tribes").executes(SurvivorCommands::tribes))
                .then(Commands.literal("ui").executes(c -> {
                    TribeMenu.open(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("designs").executes(SurvivorCommands::designs))
                .then(Commands.literal("design").then(Commands.argument("id", IntegerArgumentType.integer(0, BaseDesign.COUNT - 1))
                        .executes(c -> design(c, IntegerArgumentType.getInteger(c, "id")))))
                .then(Commands.literal("assign")
                        .then(Commands.argument("id", IntegerArgumentType.integer(0, BaseDesign.COUNT - 1))
                                .executes(c -> assign(c, IntegerArgumentType.getInteger(c, "id")))))
                .then(Commands.literal("auto").executes(c -> assign(c, -1)))
                .then(Commands.literal("spawning")
                        .then(Commands.literal("on").executes(c -> spawning(c, true)))
                        .then(Commands.literal("off").executes(c -> spawning(c, false))))
                .then(Commands.literal("density").then(Commands.argument("per3x3Chunks", IntegerArgumentType.integer(0, 40))
                        .executes(c -> density(c, IntegerArgumentType.getInteger(c, "per3x3Chunks")))))
                .then(Commands.literal("maxlevel").then(Commands.argument("level", IntegerArgumentType.integer(1, 500))
                        .executes(c -> maxLevel(c, IntegerArgumentType.getInteger(c, "level"))))));
    }

    private static int spawning(CommandContext<CommandSourceStack> c, boolean on) {
        com.arkragnarok.ArkConfig.SPAWNING.set(on);
        com.arkragnarok.ArkConfig.SPAWNING.save();
        say(c.getSource(), on ? "Wild dinosaurs spawn again (" + com.arkragnarok.ArkConfig.DENSITY.get() + " per 3x3 chunks)."
                : "Wild dinosaurs no longer spawn by themselves.", ChatFormatting.GREEN);
        return 1;
    }

    private static int density(CommandContext<CommandSourceStack> c, int n) {
        com.arkragnarok.ArkConfig.DENSITY.set(n);
        com.arkragnarok.ArkConfig.DENSITY.save();
        say(c.getSource(), "Up to " + n + " wild dinosaurs per 3x3 chunks" + (n == 0 ? " (spawning off)" : "") + ".", ChatFormatting.GREEN);
        return 1;
    }

    private static int maxLevel(CommandContext<CommandSourceStack> c, int n) {
        com.arkragnarok.ArkConfig.MAX_LEVEL.set(n);
        com.arkragnarok.ArkConfig.MAX_LEVEL.save();
        say(c.getSource(), "Wild dinosaurs now reach level " + n + " at most (high levels stay rare).", ChatFormatting.GREEN);
        return 1;
    }

    private static void say(CommandSourceStack src, String text, ChatFormatting f) {
        src.sendSuccess(() -> Component.literal(text).withStyle(f), false);
    }

    private static int tribes(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        ServerLevel level = src.getLevel();
        SurvivorWorld w = SurvivorWorld.get(level);
        if (w.all().isEmpty()) {
            say(src, "No survivors yet - use a Survivor Command Device.", ChatFormatting.GRAY);
            return 0;
        }
        for (SurvivorWorld.Tribe t : w.all()) {
            Personality p = t.personality();
            String base = t.base == null ? "wandering, no base yet"
                    : "#" + t.design + " " + BaseDesign.get(t.design).archetype.label + " at " + t.base.toShortString() + ", "
                    + Builder.builtCount(t) + "/" + Builder.plan(t).size() + " built" + (t.manualDesign ? " (assigned)" : "");
            say(src, t.name + " - " + p.temperament().replace('_', ' ') + " - stage " + t.stage + " " + SurvivorBrain.STAGES[Math.min(5, t.stage - 1)],
                    ChatFormatting.GOLD);
            say(src, "  base: " + base, ChatFormatting.AQUA);
            int stock = 0;
            for (int n : t.stock.values()) stock += n;
            say(src, "  stores " + stock + " items, " + t.tames.size() + " dinos, " + t.piecesBuilt + " pieces built, " + t.piecesLost + " lost, "
                    + t.raids + " raids, " + t.defended + " defences, skills: " + t.skills.summary(), ChatFormatting.GRAY);
            StringBuilder rel = new StringBuilder();
            for (Map.Entry<UUID, Integer> r : t.relations.entrySet()) {
                SurvivorWorld.Tribe o = w.find(r.getKey());
                String who = o != null ? o.name : level.getServer().getPlayerList().getPlayer(r.getKey()) != null
                        ? level.getServer().getPlayerList().getPlayer(r.getKey()).getName().getString() : "someone";
                rel.append(who).append(' ').append(r.getValue()).append("  ");
            }
            if (rel.length() > 0) say(src, "  relations: " + rel.toString().trim(), ChatFormatting.GRAY);
            if (t.ally != null && w.find(t.ally) != null) say(src, "  allied with " + w.find(t.ally).name, ChatFormatting.GREEN);
        }
        return w.all().size();
    }

    private static int designs(CommandContext<CommandSourceStack> c) {
        for (BaseDesign d : BaseDesign.all()) {
            say(c.getSource(), "#" + d.id + " " + d.describe() + " - " + d.pieceCount() + " pieces", ChatFormatting.AQUA);
        }
        return BaseDesign.COUNT;
    }

    private static int design(CommandContext<CommandSourceStack> c, int id) {
        BaseDesign d = BaseDesign.get(id);
        CommandSourceStack src = c.getSource();
        say(src, "#" + d.id + " " + d.describe(), ChatFormatting.GOLD);
        StringBuilder rooms = new StringBuilder();
        d.roomSummary().forEach((k, v) -> rooms.append(v).append(' ').append(k).append("  "));
        say(src, "rooms: " + rooms.toString().trim(), ChatFormatting.AQUA);
        int[] b = d.bounds();
        say(src, "size " + (b[1] - b[0] + 1) + " x " + (b[3] - b[2] + 1) + " blocks, " + d.pieceCount() + " pieces, " + d.penCells() + " pen cells, "
                + d.fieldCells() + " field cells, best on " + d.archetype.site + " ground", ChatFormatting.GRAY);
        // the plan from above: R rooms by use, P pens, F fields, T towers
        int minX = 99, minZ = 99, maxX = -99, maxZ = -99;
        for (int[] m : d.mapCells()) {
            minX = Math.min(minX, m[0]);
            minZ = Math.min(minZ, m[1]);
            maxX = Math.max(maxX, m[0]);
            maxZ = Math.max(maxZ, m[1]);
        }
        String key = "SsWmKLABMNH";
        char[][] g = new char[maxZ - minZ + 1][maxX - minX + 1];
        for (char[] row : g) java.util.Arrays.fill(row, '.');
        for (int[] m : d.mapCells()) {
            if (m[2] != 0) continue;
            char ch = m[3] >= 0 ? key.charAt(m[3]) : m[3] == -1 ? 'P' : m[3] == -2 ? 'F' : 'T';
            g[m[1] - minZ][m[0] - minX] = ch;
        }
        for (char[] row : g) say(src, "  " + new String(row).replace("", " ").trim(), ChatFormatting.WHITE);
        say(src, "S sleep s storage W workshop m smelter K kitchen L lab A armory B barn M market N nursery H hall P pen F field T tower", ChatFormatting.DARK_GRAY);
        return 1;
    }

    private static int assign(CommandContext<CommandSourceStack> c, int id) {
        CommandSourceStack src = c.getSource();
        ServerLevel level = src.getLevel();
        Survivor best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : level.getEntities(com.arkragnarok.registry.ModEntities.SURVIVOR.get(), x -> true)) {
            double dd = e.distanceToSqr(src.getPosition());
            if (dd < bd) {
                bd = dd;
                best = (Survivor) e;
            }
        }
        if (best == null) {
            say(src, "No survivor is loaded near you.", ChatFormatting.RED);
            return 0;
        }
        SurvivorWorld.Tribe t = best.tribe();
        if (id < 0) {
            t.manualDesign = false;
            say(src, t.name + " will choose its designs by itself again.", ChatFormatting.GREEN);
        } else {
            t.manualDesign = true;
            t.design = id;
            // a new blueprint on the same site: what already stands is reused where it matches, the rest is planned anew
            if (t.base != null) Builder.settle(t, t.base, id, t.rotation);
            say(src, t.name + " now builds #" + id + " " + BaseDesign.get(id).describe() + (t.base == null ? " when it settles." : "."), ChatFormatting.GREEN);
        }
        SurvivorWorld.get(level).setDirty();
        return 1;
    }
}
