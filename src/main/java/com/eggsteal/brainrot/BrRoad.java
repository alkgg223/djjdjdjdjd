package com.eggsteal.brainrot;

import com.eggsteal.EggSteal;
import com.eggsteal.Hud;
import com.eggsteal.RandomEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Sends brainrots out of the portal onto the red carpet every 3 seconds, and every 10 minutes a guaranteed
 * rare one (Brainrot God or better, always mutated) that everybody - players and bots - fights over.
 */
public class BrRoad {
    public static final int INTERVAL = 60, RARE_EVERY = 12000, MAX_ON_ROAD = 45;
    static int timer;
    static String lastTimerText;

    public static void reset() { timer = 0; lastTimerText = null; }

    /** The event that favours a mutation (rainbow event -> rainbow brainrots...). */
    static int favour() {
        RandomEvents.Kind k = RandomEvents.active();
        if (k == null) return 0;
        return switch (k) {
            case RAINBOW -> 27;
            case SOFTWARE -> 19;
            case LAVA -> 13;
            case GALAXY -> 28;
            default -> 0;
        };
    }

    static double luck() { return RandomEvents.luckBonus() > 0 ? 4 : 0; }

    public static void tick(MinecraftServer s, BrData data) {
        ServerLevel sl = s.overworld();
        long now = sl.getGameTime();
        if (data.nextRare <= 0 || data.nextRare > now + RARE_EVERY * 2L) { data.nextRare = now + RARE_EVERY; data.setDirty(); }
        if (--timer <= 0) {
            timer = (int) (INTERVAL / RandomEvents.beltSpeedMult());
            int onRoad = 0;
            for (BrainrotEntity b : BrainrotEntity.all(sl)) if (b.state() == BrainrotEntity.ROAD) onRoad++;
            if (onRoad < MAX_ON_ROAD) {
                Brainrot b = Brainrot.roll(sl.random, luck(), 0);
                spawn(sl, b.index, BrMutation.roll(sl.random, 1, favour(), 0), false);
            }
        }
        long left = data.nextRare - now;
        if (left <= 0) {
            spawnSpecial(s, true);
            data.nextRare = now + RARE_EVERY;
            data.setDirty();
        } else if (now % 20 == 0) {
            long sec = left / 20;
            String t = "§d§l★ RARE BRAINROT IN §f" + sec / 60 + ":" + String.format("%02d", sec % 60) + " §d§l★\n§7It walks out of this portal - everybody will fight for it!";
            if (!t.equals(lastTimerText)) { lastTimerText = t; Hud.setText(s, "br_timer", t); }
            if (sec == 60 || sec == 10) {
                s.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal("§d§l★ A rare brainrot walks onto the carpet in " + sec + " seconds!"), false);
                for (ServerPlayer p : s.getPlayerList().getPlayers()) p.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, 1f, 1.5f);
            }
        }
    }

    public static BrainrotEntity spawn(ServerLevel sl, int variant, int mut, boolean special) {
        BrainrotEntity e = EggSteal.BRAINROT.get().create(sl);
        if (e == null) return null;
        e.init(variant, mut);
        e.special = special;
        e.moveTo(BrMap.ROAD_START, 64, 0.5, -90f, 0f);
        if (special) e.setGlowingTag(true);
        sl.addFreshEntity(e);
        e.refreshName();
        return e;
    }

    /** The guaranteed rare brainrot (timer = true) or a bonus one from the lucky event. */
    public static void spawnSpecial(MinecraftServer s, boolean timer) {
        ServerLevel sl = s.overworld();
        Brainrot b = Brainrot.roll(sl.random, 6, timer ? 5 : 4);
        int mut = BrMutation.roll(sl.random, 3, favour(), timer ? 10 : 1);
        BrainrotEntity e = spawn(sl, b.index, mut, true);
        if (e == null) return;
        Hud.announce(s, "§d§l★ RARE BRAINROT! ★", e.tagName() + " §fis on the carpet for §6$" + com.eggsteal.Economy.fmt(e.price));
        for (ServerPlayer p : s.getPlayerList().getPlayers()) p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1f, 1f);
    }
}
