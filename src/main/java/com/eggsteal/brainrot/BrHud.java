package com.eggsteal.brainrot;

import com.eggsteal.Economy;
import com.eggsteal.Hud;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Money bar, leaderboards and the name plates over every base. */
public class BrHud {
    static final String[] last = new String[BrMap.BASES];

    public static void reset() { java.util.Arrays.fill(last, null); }

    public static void actionbar(MinecraftServer s, BrData data) {
        ServerLevel sl = s.overworld();
        Map<UUID, Double> rates = new HashMap<>();
        Map<UUID, Integer> counts = new HashMap<>();
        for (BrainrotEntity b : BrainrotEntity.all(sl)) {
            if (b.state() != BrainrotEntity.PODIUM || b.owner == null) continue;
            rates.merge(b.owner, BrData.rate(data.map.get(b.owner), b.brainrot(), b.mutation()), Double::sum);
            counts.merge(b.owner, 1, Integer::sum);
        }
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            BrData.Stats st = data.map.get(p.getUUID());
            if (st == null) continue;
            String carry = "";
            for (net.minecraft.world.entity.Entity e : p.getPassengers())
                if (e instanceof BrainrotEntity b) carry = " §c§l| CARRYING " + b.tagName() + " §c§l- RUN HOME!";
            p.displayClientMessage(Component.literal("§6$" + Economy.fmt(st.money) + " §a(+" + Economy.fmt(rates.getOrDefault(p.getUUID(), 0.0)) + "/s)"
                    + " §7| §fBase " + counts.getOrDefault(p.getUUID(), 0) + "/" + BrMap.capacity(st) + (st.rebirths > 0 ? " §d✦R" + st.rebirths : "") + carry), true);
        }
    }

    public static void boards(MinecraftServer s, BrData data) {
        ServerLevel sl = s.overworld();
        List<Map.Entry<UUID, BrData.Stats>> all = new ArrayList<>(data.map.entrySet());
        all.removeIf(e -> e.getValue().base < 0);
        all.sort((a, b) -> Double.compare(b.getValue().money, a.getValue().money));
        StringBuilder sb = new StringBuilder("§6§l★ RICHEST ★\n");
        for (int i = 0; i < Math.min(8, all.size()); i++) {
            BrData.Stats st = all.get(i).getValue();
            sb.append("§e").append(i + 1).append(". §f").append(st.name).append(st.bot ? " §7[BOT]" : "").append(" §a$").append(Economy.fmt(st.money)).append("\n");
        }
        Hud.setText(s, "br_lb1", sb.toString());

        List<Map.Entry<UUID, BrData.Stats>> rb = new ArrayList<>(all);
        rb.sort((a, b) -> a.getValue().rebirths != b.getValue().rebirths ? Integer.compare(b.getValue().rebirths, a.getValue().rebirths) : Double.compare(b.getValue().money, a.getValue().money));
        StringBuilder rs = new StringBuilder("§d§l✦ REBIRTHS ✦\n");
        for (int i = 0; i < Math.min(8, rb.size()); i++) {
            BrData.Stats st = rb.get(i).getValue();
            rs.append("§e").append(i + 1).append(". §f").append(st.name).append(st.bot ? " §7[BOT]" : "").append(" §d").append(st.rebirths).append(" §7(x").append(BrData.rebirthMult(st)).append(")\n");
        }
        Hud.setText(s, "br_lb2", rs.toString());

        Map<UUID, Integer> counts = new HashMap<>();
        Map<UUID, BrainrotEntity> bestOf = new HashMap<>();
        List<BrainrotEntity> rarest = new ArrayList<>();
        for (BrainrotEntity b : BrainrotEntity.all(sl)) {
            if (b.state() != BrainrotEntity.PODIUM || b.owner == null) continue;
            counts.merge(b.owner, 1, Integer::sum);
            BrainrotEntity cur = bestOf.get(b.owner);
            if (cur == null || b.value() > cur.value()) bestOf.put(b.owner, b);
            rarest.add(b);
        }
        StringBuilder bb = new StringBuilder("§b§l⚙ BOTS ⚙\n");
        for (Map.Entry<UUID, BrData.Stats> en : all) {
            BrData.Stats st = en.getValue();
            if (!st.bot) continue;
            BrainrotEntity best = bestOf.get(en.getKey());
            bb.append("§f").append(st.name).append(" §a$").append(Economy.fmt(st.money)).append(" §d✦").append(st.rebirths)
                    .append(" §7").append(counts.getOrDefault(en.getKey(), 0)).append(" brainrots")
                    .append(best != null ? " §7best: " + best.tagName() : "").append("\n");
        }
        Hud.setText(s, "br_lb3", bb.toString());

        rarest.sort((a, b) -> Double.compare(b.value(), a.value()));
        StringBuilder top = new StringBuilder("§c§l♛ BEST BRAINROTS ♛\n");
        for (int i = 0; i < Math.min(8, rarest.size()); i++) {
            BrainrotEntity b = rarest.get(i);
            BrData.Stats o = data.map.get(b.owner);
            top.append("§e").append(i + 1).append(". ").append(b.tagName()).append(" §7- ").append(o == null ? "?" : o.name).append(" §6$").append(Economy.fmt(b.value())).append("\n");
        }
        Hud.setText(s, "br_lb4", top.toString());

        for (int i = 0; i < BrMap.BASES; i++) {
            UUID o = data.baseOwner[i];
            BrData.Stats st = o == null ? null : data.map.get(o);
            String t;
            if (st == null) t = "§7Free base";
            else {
                int g = BrData.guard(st);
                t = BrMap.COLOR_CODES[i] + "§l" + st.name + "'s Base" + (st.bot ? " §7[BOT]" : " §a[PLAYER]") + "\n§6$" + Economy.fmt(st.money)
                        + (st.rebirths > 0 ? "  §d✦ Rebirth " + st.rebirths : "") + "\n§7Brainrots " + counts.getOrDefault(o, 0) + "/" + BrMap.capacity(st)
                        + (g > 0 ? "\n" + BrData.GUARD_NAMES[g] + " §7- stealing takes longer" : "");
            }
            if (!t.equals(last[i])) { last[i] = t; Hud.setText(s, "br_b" + i, t); }
        }
    }
}
