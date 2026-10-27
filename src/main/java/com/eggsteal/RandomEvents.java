package com.eggsteal;

import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.joml.Vector3f;

/**
 * Eight random arena-wide events. One starts every 4-7 minutes and lasts 90 seconds, with a boss bar for everybody.
 * Mutation events make most new belt eggs mutated (they earn several times more money).
 */
public class RandomEvents {
    public enum Kind {
        RAINBOW("rainbow", "§c§lR§6§lA§e§lI§a§lN§b§lB§9§lO§d§lW §f§lEVENT", "A rainbow over the arena! Rainbow eggs (x5 money) on every belt", BossEvent.BossBarColor.PINK, Mutation.RAINBOW),
        SOFTWARE("software", "§a§l⌨ SOFTWARE EVENT ⌨", "The arena got hacked! Glitch eggs (x4 money) on every belt", BossEvent.BossBarColor.GREEN, Mutation.GLITCH),
        MONEY_RAIN("money", "§6§l$ MONEY RAIN $", "It is raining gold: every creature earns double money!", BossEvent.BossBarColor.YELLOW, Mutation.NONE),
        LAVA("lava", "§c§l♨ LAVA EVENT ♨", "The ground is boiling! Molten eggs (x3 money) on every belt", BossEvent.BossBarColor.RED, Mutation.MOLTEN),
        GALAXY("galaxy", "§5§l✦ GALAXY NIGHT ✦", "Night falls... Galaxy eggs (x6 money) on every belt", BossEvent.BossBarColor.PURPLE, Mutation.GALAXY),
        SPEED("speed", "§b§l⚡ SPEED FRENZY ⚡", "Everybody runs fast and all belts go double speed!", BossEvent.BossBarColor.BLUE, Mutation.NONE),
        LUCKY("lucky", "§a§l☘ LUCKY EGGS ☘", "Rare eggs are much more common + a bonus rare egg in the center!", BossEvent.BossBarColor.GREEN, Mutation.NONE),
        MOON("moon", "§f§l☾ MOON GRAVITY ☾", "Low gravity: jump high and float down slowly!", BossEvent.BossBarColor.WHITE, Mutation.NONE);

        public final String id, title, desc;
        public final BossEvent.BossBarColor color;
        public final Mutation mutation;

        Kind(String id, String title, String desc, BossEvent.BossBarColor color, Mutation mutation) {
            this.id = id; this.title = title; this.desc = desc; this.color = color; this.mutation = mutation;
        }
    }

    public static final int DURATION = 1800; // 90 s
    static final float[][] RAINBOW = {{1f, 0.1f, 0.1f}, {1f, 0.55f, 0f}, {1f, 0.95f, 0.1f}, {0.2f, 0.9f, 0.2f}, {0.2f, 0.7f, 1f}, {0.25f, 0.3f, 1f}, {0.7f, 0.25f, 1f}};
    static Kind active;
    static long endAt, nextAt;
    static ServerBossEvent bar;

    /** Static state belongs to one world: clear it whenever a server starts or stops. */
    public static void reset() {
        if (bar != null) bar.removeAllPlayers();
        bar = null;
        active = null;
        endAt = 0;
        nextAt = 0;
    }

    public static Kind active() { return active; }
    public static double incomeMult() { return active == Kind.MONEY_RAIN ? 2 : 1; }
    public static double beltSpeedMult() { return active == Kind.SPEED ? 2 : 1; }
    public static int luckBonus() { return active == Kind.LUCKY ? 25 : 0; }

    /** The mutation a new belt egg gets right now (most eggs during a mutation event, none otherwise). */
    public static int rollMutation(RandomSource r) {
        return active != null && active.mutation != Mutation.NONE && r.nextFloat() < 0.6f ? active.mutation.ordinal() : 0;
    }

    public static String status(MinecraftServer s) {
        long now = s.getTickCount();
        if (active != null) return active.title + "\n§f" + active.desc + "\n§7" + Math.max(0, (endAt - now) / 20) + "s left";
        return "§e§lRANDOM EVENTS\n§7Next event in §f" + Math.max(0, (nextAt - now) / 20) + "s";
    }

    public static void start(MinecraftServer s, Kind k) {
        if (active != null) stop(s);
        long now = s.getTickCount();
        active = k;
        endAt = now + DURATION;
        nextAt = endAt + 4800 + (long) (Math.random() * 3600);
        bar = new ServerBossEvent(Component.literal(k.title), k.color, BossEvent.BossBarOverlay.PROGRESS);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) bar.addPlayer(p);
        Hud.announce(s, k.title, "§f" + k.desc);
        ServerLevel sl = s.overworld();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 1f, 0.7f);
        if (k == Kind.GALAXY) sl.setDayTime(18000);
        if (k == Kind.LUCKY) Auction.spawn(s);
    }

    public static void stop(MinecraftServer s) {
        if (active == null) return;
        if (active == Kind.GALAXY) s.overworld().setDayTime(6000);
        s.getPlayerList().broadcastSystemMessage(Component.literal("§7The " + active.title + " §7is over."), false);
        if (bar != null) bar.removeAllPlayers();
        bar = null;
        active = null;
    }

    public static void tick(MinecraftServer s) {
        long now = s.getTickCount();
        if (nextAt == 0) nextAt = now + 3600 + (long) (Math.random() * 2400); // the first one after 3-5 minutes
        if (active == null) {
            if (now >= nextAt) start(s, Kind.values()[(int) (Math.random() * Kind.values().length)]);
            return;
        }
        if (now >= endAt) { stop(s); return; }
        ServerLevel sl = s.overworld();
        if (now % 20 == 0) {
            if (bar != null) {
                bar.setProgress(Math.max(0f, Math.min(1f, (endAt - now) / (float) DURATION)));
                for (ServerPlayer p : s.getPlayerList().getPlayers()) bar.addPlayer(p);
            }
            if (active == Kind.SPEED || active == Kind.MOON) {
                for (LivingEntity le : everybody(s, sl)) {
                    if (active == Kind.SPEED) le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, false, false));
                    else {
                        le.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 2, false, false));
                        le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false));
                    }
                }
            }
            if (active == Kind.GALAXY && sl.getDayTime() % 24000 != 18000) sl.setDayTime(18000);
        }
        if (active == Kind.SOFTWARE && now % 300 == 0) {
            StringBuilder bits = new StringBuilder("§a§o<SYSTEM> §2");
            for (int i = 0; i < 24; i++) bits.append(Math.random() < 0.5 ? '0' : '1');
            s.getPlayerList().broadcastSystemMessage(Component.literal(bits + " §a§oglitch eggs injected"), false);
        }
        if (now % 5 == 0) particles(s, sl, now);
    }

    static List<LivingEntity> everybody(MinecraftServer s, ServerLevel sl) {
        List<LivingEntity> l = new java.util.ArrayList<>(s.getPlayerList().getPlayers());
        l.addAll(sl.getEntities(EntityTypeTest.forClass(BotEntity.class), b -> true));
        return l;
    }

    static void around(ServerLevel sl, ServerPlayer p, ParticleOptions type, int n, double r, double y, double dy, double speed) {
        sl.sendParticles(p, type, true, p.getX(), p.getY() + y, p.getZ(), n, r, dy, r, speed);
    }

    static DustParticleOptions dust(float[] c, float size) { return new DustParticleOptions(new Vector3f(c[0], c[1], c[2]), size); }

    static void particles(MinecraftServer s, ServerLevel sl, long now) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            switch (active) {
                case RAINBOW -> {
                    if (now % 10 == 0) { // a giant rainbow arch over the whole arena (x from -60 to 60)
                        for (int band = 0; band < RAINBOW.length; band++) {
                            double rad = 60 - band * 1.6;
                            DustParticleOptions d = dust(RAINBOW[band], 4f);
                            for (int k = 0; k <= 40; k++) {
                                double a = Math.PI * k / 40.0;
                                sl.sendParticles(p, d, true, Math.cos(a) * rad, 64 + Math.sin(a) * rad * 0.55, 0, 1, 0, 0, 0, 0);
                            }
                        }
                    }
                    around(sl, p, dust(RAINBOW[(int) (now / 5 % RAINBOW.length)], 1.2f), 6, 4, 1, 1.5, 0);
                }
                case SOFTWARE -> {
                    around(sl, p, dust(new float[]{0.1f, 1f, 0.35f}, 1.1f), 10, 10, 3, 3, 0);
                    around(sl, p, ParticleTypes.ENCHANT, 25, 6, 2, 2, 0.6);
                    if (now % 20 == 0) around(sl, p, ParticleTypes.COMPOSTER, 12, 5, 1, 1, 0);
                }
                case MONEY_RAIN -> {
                    around(sl, p, ParticleTypes.FALLING_HONEY, 14, 9, 8, 2, 0);
                    around(sl, p, dust(new float[]{1f, 0.85f, 0.1f}, 1.4f), 6, 8, 5, 3, 0);
                    if (now % 40 == 0) p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.AMBIENT, 0.4f, 1.6f);
                }
                case LAVA -> {
                    around(sl, p, ParticleTypes.FALLING_LAVA, 10, 9, 7, 2, 0);
                    around(sl, p, ParticleTypes.LAVA, 4, 7, 0.2, 0.1, 0);
                    around(sl, p, ParticleTypes.SMALL_FLAME, 8, 6, 0.3, 0.2, 0.01);
                }
                case GALAXY -> {
                    around(sl, p, ParticleTypes.END_ROD, 6, 10, 6, 4, 0.01);
                    around(sl, p, ParticleTypes.PORTAL, 20, 6, 1, 1, 0.5);
                    around(sl, p, dust(new float[]{0.6f, 0.3f, 1f}, 1.6f), 6, 12, 9, 3, 0);
                }
                case SPEED -> around(sl, p, ParticleTypes.ELECTRIC_SPARK, 12, 4, 1, 1, 0.2);
                case LUCKY -> {
                    around(sl, p, ParticleTypes.HAPPY_VILLAGER, 8, 6, 1, 1.5, 0);
                    around(sl, p, dust(new float[]{0.2f, 1f, 0.3f}, 1.3f), 6, 6, 2, 2, 0);
                }
                case MOON -> {
                    around(sl, p, ParticleTypes.WHITE_ASH, 30, 8, 3, 3, 0);
                    around(sl, p, ParticleTypes.END_ROD, 2, 8, 6, 3, 0);
                }
            }
        }
    }
}
