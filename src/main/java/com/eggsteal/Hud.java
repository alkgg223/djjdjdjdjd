package com.eggsteal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.entity.EntityTypeTest;

/** Floating text (leaderboards, base names) and the money action bar. */
public class Hud {
    public static void cmd(MinecraftServer s, String command) {
        try {
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(), command);
        } catch (Exception ignored) { }
    }

    static String json(MinecraftServer s, String text) {
        return StringTag.quoteAndEscape(Component.Serializer.toJson(Component.literal(text), s.registryAccess()));
    }

    public static void summon(MinecraftServer s, String tag, double x, double y, double z, float scale, String text) {
        String sc = scale + "f";
        cmd(s, "summon minecraft:text_display " + x + " " + y + " " + z + " {Tags:[\"eggsteal\",\"" + tag + "\"],billboard:\"center\",line_width:400,text:" + json(s, text)
                + ",transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[" + sc + "," + sc + "," + sc + "]}}");
    }

    static String rawJson(MinecraftServer s, String text) {
        return Component.Serializer.toJson(Component.literal(text), s.registryAccess());
    }

    /** Big on-screen notice for everybody + a chat line. */
    public static void announce(MinecraftServer s, String title, String subtitle) {
        cmd(s, "title @a times 10 100 20");
        cmd(s, "title @a subtitle " + rawJson(s, subtitle));
        cmd(s, "title @a title " + rawJson(s, title));
        s.getPlayerList().broadcastSystemMessage(Component.literal(title + " " + subtitle), false);
    }

    public static void setText(MinecraftServer s, String tag, String text) {
        cmd(s, "data merge entity @e[type=minecraft:text_display,tag=" + tag + ",limit=1] {text:" + json(s, text) + "}");
    }

    /** Summons the floating boards that worlds made by older versions of the mod do not have yet. */
    public static void ensureBoards(MinecraftServer s) {
        ServerLevel sl = s.overworld();
        String[][] boards = {{"es_lb3", "0.5", "69", "-15.5", "1.6"}, {"es_event", "0.5", "77.5", "0.5", "2.2"}};
        for (String[] b : boards) {
            if (!sl.getEntities(EntityType.TEXT_DISPLAY, e -> e.getTags().contains(b[0])).isEmpty()) continue;
            summon(s, b[0], Double.parseDouble(b[1]), Double.parseDouble(b[2]), Double.parseDouble(b[3]), Float.parseFloat(b[4]), "Loading...");
        }
    }

    /** Top money list, best bots, rebirths and base name plates. */
    public static void leaderboard(MinecraftServer s) {
        ensureBoards(s);
        Economy eco = Economy.get(s);
        List<Economy.Stats> all = new ArrayList<>(eco.map.values());
        all.sort((a, b) -> Double.compare(b.money, a.money));
        StringBuilder sb = new StringBuilder("§6§l★ TOP MONEY ★\n");
        for (int i = 0; i < Math.min(8, all.size()); i++) {
            Economy.Stats st = all.get(i);
            sb.append("§e").append(i + 1).append(". ").append(Economy.rebirthTag(st)).append("§f").append(st.name).append(st.bot ? " §7[BOT]" : "").append(" §a$").append(Economy.fmt(st.money)).append("\n");
        }
        setText(s, "es_lb1", sb.toString());

        List<Economy.Stats> bots = new ArrayList<>();
        for (Economy.Stats st : all) if (st.bot) bots.add(st);
        StringBuilder bb = new StringBuilder("§b§l⚙ BEST BOTS ⚙\n");
        if (bots.isEmpty()) bb.append("§7No bots yet");
        else {
            Economy.Stats richest = bots.get(0);
            Economy.Stats fastest = bots.get(0);
            for (Economy.Stats b : bots) if (b.speed > fastest.speed || (b.speed == fastest.speed && b.money > fastest.money)) fastest = b;
            Economy.Stats best = bots.get(0);
            int bestScore = Integer.MAX_VALUE;
            List<Economy.Stats> bySpeed = new ArrayList<>(bots);
            bySpeed.sort((a, b) -> Integer.compare(b.speed, a.speed));
            for (Economy.Stats b : bots) {
                int score = bots.indexOf(b) + bySpeed.indexOf(b);
                if (score < bestScore) { bestScore = score; best = b; }
            }
            bb.append("§6Best overall: §f").append(best.name).append("\n§7($").append(Economy.fmt(best.money)).append(" · speed Lv").append(best.speed).append(" · ").append(best.rebirths).append(" rebirths)\n\n");
            bb.append("§aRichest: §f").append(richest.name).append(" §a$").append(Economy.fmt(richest.money)).append("\n");
            bb.append("§bFastest: §f").append(fastest.name).append(" §bLv").append(fastest.speed).append("\n\n§7All bots:\n");
            for (Economy.Stats b : bots) bb.append("§f").append(b.name).append(" §a$").append(Economy.fmt(b.money)).append(" §d✦").append(b.rebirths).append(" §7spd ").append(b.speed).append(" inc ").append(b.income).append(" belt ").append(b.belt).append("\n");
        }
        setText(s, "es_lb2", bb.toString());

        List<Economy.Stats> rb = new ArrayList<>(all);
        rb.sort((a, b) -> a.rebirths != b.rebirths ? Integer.compare(b.rebirths, a.rebirths) : Double.compare(b.money, a.money));
        StringBuilder rs = new StringBuilder("§d§l✦ REBIRTHS ✦\n");
        for (int i = 0; i < Math.min(8, rb.size()); i++) {
            Economy.Stats st = rb.get(i);
            rs.append("§e").append(i + 1).append(". §f").append(st.name).append(st.bot ? " §7[BOT]" : " §a[YOU]").append(" §d").append(st.rebirths)
                    .append(st.rebirths == 1 ? " rebirth" : " rebirths").append(" §7(x").append(Economy.rebirthMult(st)).append(")\n");
        }
        setText(s, "es_lb3", rs.toString());

        for (int i = 0; i < Arena.BASES; i++) {
            UUID o = eco.baseOwner[i];
            Economy.Stats st = o == null ? null : eco.map.get(o);
            String t = st == null ? "§7Free base" : "§l" + st.name + (st.bot ? " §7[BOT]" : " §a[PLAYER]") + "\n§6$" + Economy.fmt(st.money)
                    + (st.rebirths > 0 ? "  §d✦ Rebirth " + st.rebirths : "") + "\n§7Belt Lv" + st.belt + " · Slots " + Arena.available(st) + (st.floor2 ? " · 2F" : "");
            setText(s, "es_b" + i, t);
        }
    }

    public static void actionbar(MinecraftServer s) {
        ServerLevel sl = s.overworld();
        Economy eco = Economy.get(s);
        Map<UUID, Double> rates = new HashMap<>();
        for (CreatureEntity c : sl.getEntities(EntityTypeTest.forClass(CreatureEntity.class), x -> !x.hatching() && x.owner() != null)) {
            Economy.Stats st = eco.map.get(c.owner());
            if (st != null) rates.merge(c.owner(), Economy.rate(st, c.creature(), c.mutation()), Double::sum);
        }
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            Economy.Stats st = eco.map.get(p.getUUID());
            if (st == null) continue;
            p.displayClientMessage(Component.literal("§6$" + Economy.fmt(st.money) + " §a(+" + Economy.fmt(rates.getOrDefault(p.getUUID(), 0.0)) + "/s)"
                    + (st.rebirths > 0 ? " §d✦R" + st.rebirths : "")), true);
        }
    }
}
