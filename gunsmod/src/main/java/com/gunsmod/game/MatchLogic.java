package com.gunsmod.game;

import com.gunsmod.Reg;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.entity.VehicleEntity;
import com.gunsmod.net.Net;
import com.gunsmod.world.Blast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The rules of the battle modes (server side). Every half second the capture points are pushed by whoever stands
 * round them, the flags are picked up, carried, dropped and returned, the demolition charges are planted and
 * defused, the convoy is checked against its destination; every second the tickets drain or the score grows, the
 * winner is decided and every player in the area is told the state of the battle and his side's mission.
 */
public final class MatchLogic {
    private MatchLogic() {}

    public static final double CARRY_REACH = 2.2, PLANT_REACH = 3.2, ARRIVE = 14;
    public static final int CHARGE_TICKS = 900, FLAG_RETURN = 600;
    /** the objectives of each running battle, refreshed every half second */
    private static final Map<Level, List<ObjectiveBlockEntity>> OBJ = new java.util.WeakHashMap<>();

    @Nullable
    public static Match running(Level level) {
        if (!(level instanceof ServerLevel sl)) return null;
        Match m = Match.get(sl);
        return m.running && m.control != null ? m : null;
    }

    public static List<ObjectiveBlockEntity> objectives(Level level) {
        return OBJ.getOrDefault(level, List.of());
    }

    /** soldiers and players count on an objective (not spectators, not the dead) */
    private static boolean unit(Entity e) {
        if (e instanceof BotEntity b) return b.isAlive();
        return e instanceof Player p && p.isAlive() && !p.isSpectator();
    }

    private static int[] count(ServerLevel level, Vec3 c, double r) {
        int[] n = new int[2];
        for (Entity e : level.getEntities((Entity) null, new AABB(c, c).inflate(r, 4, r), MatchLogic::unit)) {
            if (e.position().distanceToSqr(c.x, e.getY(), c.z) > r * r) continue;
            BotTeam t = BotTeam.of(e);
            if (t != null) n[Match.idx(t)]++;
        }
        return n;
    }

    // ------------------------------------------------------------------------------------ the control table
    public static void openControl(ServerPlayer sp, BlockPos pos) {
        Match m = Match.get(sp.serverLevel());
        PacketDistributor.sendToPlayer(sp, state(sp.serverLevel(), m, sp, pos, ""));
    }

    public static void control(ServerPlayer sp, Net.MatchControl p) {
        ServerLevel level = sp.serverLevel();
        if (sp.blockPosition().distSqr(p.pos()) > 10 * 10 || !(level.getBlockEntity(p.pos()) instanceof ObjectiveBlockEntity be)
                || be.kind() != ObjectiveBlock.Kind.CONTROL) return;
        Match m = Match.get(level);
        if (m.running && m.control != null && !m.control.equals(p.pos())) {
            sp.displayClientMessage(Component.translatable("gunsmod.match.elsewhere", m.control.getX(), m.control.getZ()).withStyle(ChatFormatting.RED), true);
            return;
        }
        if (p.action() == Net.MatchControl.STOP) {
            if (m.running) end(level, m, 2, "gunsmod.match.stopped");
        } else if (!m.running) {
            GameMode[] all = GameMode.values();
            GameMode mode = all[Mth.clamp(p.mode(), 0, all.length - 1)];
            if (mode != m.mode) {
                m.minutes = mode.minutes();
                m.limit = mode.limit();
            }
            m.mode = mode;
            m.attackers = BotTeam.byId(p.attackers());
            if (p.minutes() > 0) m.minutes = Mth.clamp(p.minutes(), 1, 120);
            if (p.limit() > 0) m.limit = Mth.clamp(p.limit(), 1, 2000);
            m.radius = Mth.clamp(p.radius() > 0 ? p.radius() : m.radius, 64, 1024);
            m.control = p.pos();
            m.touch();
            if (p.action() == Net.MatchControl.START) {
                String problem = start(level, m);
                if (problem != null) sp.displayClientMessage(Component.translatable(problem).withStyle(ChatFormatting.RED), false);
            }
        }
        PacketDistributor.sendToPlayer(sp, state(level, m, sp, p.pos(), ""));
    }

    /** start a battle; null, or the translation key of what is missing */
    @Nullable
    private static String start(ServerLevel level, Match m) {
        List<ObjectiveBlockEntity> objs = Objectives.near(level, m.control, m.radius);
        int points = 0, targets = 0, dest = 0;
        boolean[] flags = new boolean[2];
        for (ObjectiveBlockEntity o : objs) {
            switch (o.kind()) {
                case POINT -> points++;
                case TARGET -> {
                    if (o.owner() == m.defenders()) targets++;
                }
                case FLAG -> {
                    if (o.owner() != null) flags[Match.idx(o.owner())] = true;
                }
                case DESTINATION -> dest++;
                default -> { }
            }
        }
        if (m.mode.points() && points == 0) return "gunsmod.match.need_points";
        if (m.mode == GameMode.CTF && !(flags[0] && flags[1])) return "gunsmod.match.need_flags";
        if (m.mode.targets() && targets == 0) return "gunsmod.match.need_targets";
        m.convoy.clear();
        m.arrived.clear();
        if (m.mode.convoy()) {
            if (dest == 0) return "gunsmod.match.need_destination";
            BotTeam escort = m.escort();
            for (Entity e : level.getEntities((Entity) null, new AABB(m.control).inflate(m.radius, 128, m.radius),
                    e -> (e instanceof VehicleEntity v && !(v instanceof com.gunsmod.entity.DroneEntity) && v.isManned()
                            && !(v instanceof com.gunsmod.entity.Aircraft a && a.isAirborne()) || e instanceof JeepEntity j && !j.getPassengers().isEmpty())
                            && BotTeam.of(e) == escort)) {
                m.convoy.add(e.getUUID());
            }
            if (m.convoy.isEmpty()) return "gunsmod.match.need_convoy";
        }
        for (ObjectiveBlockEntity o : objs) o.reset(true);
        long now = level.getGameTime();
        m.running = true;
        m.winner = -1;
        m.timeLeft = m.minutes * 1200;
        for (int i = 0; i < 2; i++) {
            m.tickets[i] = m.mode.tickets() ? m.limit : 0;
            m.score[i] = m.vehicleKills[i] = m.returns[i] = 0;
            m.carrier[i] = null;
            m.dropped[i] = null;
            m.heldAll[i] = false;
        }
        if (m.mode == GameMode.ASSAULT) m.tickets[Match.idx(m.defenders())] = 0;
        m.convoyTotal = m.convoy.size();
        m.convoyLost = 0;
        m.targetLost = false;
        m.wave = 0;
        m.nextWave = now + 200;
        m.touch();
        OBJ.put(level, objs);
        for (ServerPlayer p : players(level, m)) PacketDistributor.sendToPlayer(p, state(level, m, p, null, "gunsmod.match.started"));
        level.playSound(null, m.control, Reg.sound("ui_confirm"), SoundSource.BLOCKS, 3f, 0.6f);
        return null;
    }

    /** the battle is over: who won (0 / 1, 2 a draw), the rewards, the banner */
    private static void end(ServerLevel level, Match m, int winner, String why) {
        m.running = false;
        m.winner = winner;
        m.touch();
        for (int i = 0; i < 2; i++) {
            Entity c = m.carrier[i] == null ? null : level.getEntity(m.carrier[i]);
            if (c != null) c.setGlowingTag(false);
            m.carrier[i] = null;
            m.dropped[i] = null;
        }
        for (ServerPlayer p : players(level, m)) {
            BotTeam t = BotTeam.of(p);
            boolean won = winner < 2 && t != null && Match.idx(t) == winner;
            // rewards: the winners get steel and experience, more for every optional objective done
            int extra = 0;
            if (t != null) for (Line l : mission(level, m, t)) if (l.optional() && l.state() == 1) extra++;
            if (won) {
                p.giveExperiencePoints(150 + 50 * extra);
                ItemStack prize = new ItemStack(Reg.STEEL_INGOT.get(), 8 + 4 * extra);
                if (!p.getInventory().add(prize)) p.drop(prize, false);
                p.displayClientMessage(Component.translatable("gunsmod.match.reward", 8 + 4 * extra, 150 + 50 * extra).withStyle(ChatFormatting.GOLD), false);
            } else {
                p.giveExperiencePoints(winner == 2 ? 80 : 40);
            }
            PacketDistributor.sendToPlayer(p, state(level, m, p, null, why));
        }
        level.playSound(null, m.control, Reg.sound("explosion_far"), SoundSource.BLOCKS, 2f, 1.3f);
    }

    private static List<ServerPlayer> players(ServerLevel level, Match m) {
        List<ServerPlayer> l = new ArrayList<>();
        for (ServerPlayer p : level.players()) if (m.covers(p.position()) || m.control != null && p.blockPosition().distSqr(m.control) < (m.radius + 96.0) * (m.radius + 96.0)) l.add(p);
        return l;
    }

    private static void tell(ServerLevel level, Match m, Component c) {
        for (ServerPlayer p : players(level, m)) p.displayClientMessage(c, true);
    }

    // ------------------------------------------------------------------------------------ every tick
    public static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            Match m = running(level);
            if (m == null) continue;
            if (level.isLoaded(m.control) && !(level.getBlockEntity(m.control) instanceof ObjectiveBlockEntity)) {
                end(level, m, 2, "gunsmod.match.table_gone");
                continue;
            }
            long t = level.getGameTime();
            m.timeLeft--;
            if (t % 10 == 0) {
                List<ObjectiveBlockEntity> objs = Objectives.near(level, m.control, m.radius);
                OBJ.put(level, objs);
                step(level, m, objs, t);
            }
            if (!m.running) continue;
            if (t % 20 == 0) second(level, m, objectives(level), t);
            if (m.running && m.timeLeft <= 0) timeUp(level, m);
            if (t % 100 == 0) m.touch();
        }
    }

    private static float rate(int n) {
        return 0.035f * Math.min(4, n);
    }

    private static void step(ServerLevel level, Match m, List<ObjectiveBlockEntity> objs, long t) {
        // capture points
        if (m.mode.points()) {
            for (ObjectiveBlockEntity o : objs) {
                if (o.kind() != ObjectiveBlock.Kind.POINT) continue;
                int[] n = count(level, o.center(), ObjectiveBlockEntity.RADIUS);
                BotTeam was = o.owner();
                if (n[0] > 0 && n[1] == 0) o.push(rate(n[0]));
                else if (n[1] > 0 && n[0] == 0) o.push(-rate(n[1]));
                if (o.owner() != was) {
                    Component who = o.owner() == null ? Component.translatable("gunsmod.obj.neutral")
                            : Component.translatable("gunsmod.team." + o.owner().name().toLowerCase());
                    tell(level, m, Component.translatable(o.owner() == null ? "gunsmod.match.point_lost" : "gunsmod.match.point_taken", String.valueOf(o.label()), who)
                            .withStyle(o.owner() == null ? ChatFormatting.YELLOW : o.owner().chat));
                    level.playSound(null, o.getBlockPos(), Reg.sound("ui_confirm"), SoundSource.BLOCKS, 3f, o.owner() == null ? 0.6f : 1.2f);
                }
            }
        }
        if (m.mode == GameMode.CTF) flags(level, m, objs, t);
        if (m.mode == GameMode.SEARCH_DESTROY) charges(level, m, objs);
        if (m.mode.convoy()) {
            for (UUID u : m.convoy) {
                if (m.arrived.contains(u)) continue;
                Entity e = level.getEntity(u);
                if (e == null) continue;
                for (ObjectiveBlockEntity o : objs) {
                    if (o.kind() == ObjectiveBlock.Kind.DESTINATION && e.position().distanceToSqr(o.center()) < ARRIVE * ARRIVE) {
                        m.arrived.add(u);
                        tell(level, m, Component.translatable("gunsmod.match.convoy_arrived", m.arrived.size(), m.convoyTotal).withStyle(ChatFormatting.AQUA));
                        break;
                    }
                }
            }
        }
        if (m.mode == GameMode.HQ_DEFENSE && t >= m.nextWave) wave(level, m, objs, t);
    }

    @Nullable
    private static ObjectiveBlockEntity flagOf(List<ObjectiveBlockEntity> objs, BotTeam team, Vec3 near) {
        ObjectiveBlockEntity best = null;
        double bd = Double.MAX_VALUE;
        for (ObjectiveBlockEntity o : objs) {
            if (o.kind() != ObjectiveBlock.Kind.FLAG || o.owner() != team) continue;
            double d = o.center().distanceToSqr(near);
            if (d < bd) {
                bd = d;
                best = o;
            }
        }
        return best;
    }

    /** capture the flag: pick up, carry, drop, return, capture */
    private static void flags(ServerLevel level, Match m, List<ObjectiveBlockEntity> objs, long t) {
        for (int i = 0; i < 2; i++) {
            BotTeam owner = BotTeam.byId(i), enemy = owner.enemy();
            ObjectiveBlockEntity stand = flagOf(objs, owner, Vec3.atCenterOf(m.control));
            if (stand == null) continue;
            if (m.carrier[i] != null) {
                Entity c = level.getEntity(m.carrier[i]);
                if (c == null || !c.isAlive() || !(c instanceof LivingEntity)) {
                    m.carrier[i] = null;
                    if (m.dropped[i] == null) {
                        m.dropped[i] = stand.center();
                        m.droppedAt[i] = t;
                    }
                    continue;
                }
                c.setGlowingTag(true);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, c.getX(), c.getY() + c.getBbHeight() + 0.6, c.getZ(), 1, 0.1, 0.1, 0.1, 0);
                // home with the enemy flag, own flag at home: a capture
                ObjectiveBlockEntity home = flagOf(objs, enemy, c.position());
                if (home != null && home.flagHome() && m.carrier[Match.idx(enemy)] == null && m.dropped[Match.idx(enemy)] == null
                        && c.position().distanceToSqr(home.center()) < CARRY_REACH * CARRY_REACH * 1.6) {
                    m.carrier[i] = null;
                    c.setGlowingTag(false);
                    stand.setFlagHome(true);
                    m.score[Match.idx(enemy)]++;
                    tell(level, m, Component.translatable("gunsmod.match.flag_captured", Component.translatable("gunsmod.team." + enemy.name().toLowerCase()),
                            m.score[Match.idx(enemy)], m.limit).withStyle(enemy.chat));
                    level.playSound(null, home.getBlockPos(), Reg.sound("ui_confirm"), SoundSource.BLOCKS, 4f, 1.4f);
                }
                continue;
            }
            if (m.dropped[i] != null) {
                Vec3 d = m.dropped[i];
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, d.x, d.y + 1.2, d.z, 2, 0.15, 0.5, 0.15, 0);
                if (t - m.droppedAt[i] > FLAG_RETURN) {
                    m.dropped[i] = null;
                    stand.setFlagHome(true);
                    tell(level, m, Component.translatable("gunsmod.match.flag_auto_return", Component.translatable("gunsmod.team." + owner.name().toLowerCase())));
                    continue;
                }
                for (Entity e : level.getEntities((Entity) null, new AABB(d, d).inflate(CARRY_REACH), MatchLogic::unit)) {
                    BotTeam et = BotTeam.of(e);
                    if (et == owner) {
                        m.dropped[i] = null;
                        stand.setFlagHome(true);
                        m.returns[i]++;
                        tell(level, m, Component.translatable("gunsmod.match.flag_returned", Component.translatable("gunsmod.team." + owner.name().toLowerCase())).withStyle(owner.chat));
                        break;
                    } else if (et == enemy) {
                        m.dropped[i] = null;
                        m.carrier[i] = e.getUUID();
                        tell(level, m, Component.translatable("gunsmod.match.flag_taken", e.getDisplayName(), Component.translatable("gunsmod.team." + owner.name().toLowerCase()))
                                .withStyle(enemy.chat));
                        break;
                    }
                }
                continue;
            }
            if (stand.flagHome()) {
                for (Entity e : level.getEntities((Entity) null, new AABB(stand.center(), stand.center()).inflate(CARRY_REACH), MatchLogic::unit)) {
                    if (BotTeam.of(e) != enemy) continue;
                    m.carrier[i] = e.getUUID();
                    stand.setFlagHome(false);
                    tell(level, m, Component.translatable("gunsmod.match.flag_taken", e.getDisplayName(), Component.translatable("gunsmod.team." + owner.name().toLowerCase()))
                            .withStyle(enemy.chat));
                    level.playSound(null, stand.getBlockPos(), Reg.sound("arm_switch"), SoundSource.BLOCKS, 3f, 0.8f);
                    break;
                }
            }
        }
    }

    /** search and destroy: attackers plant a charge by standing at a target, defenders defuse it the same way */
    private static void charges(ServerLevel level, Match m, List<ObjectiveBlockEntity> objs) {
        int att = Match.idx(m.attackers), def = Match.idx(m.defenders());
        for (ObjectiveBlockEntity o : objs) {
            if (o.kind() != ObjectiveBlock.Kind.TARGET || o.owner() != m.defenders() || o.destroyed()) continue;
            int[] n = count(level, o.center(), PLANT_REACH);
            if (o.charge() < 0) {
                if (n[att] > 0 && n[def] == 0) {
                    o.setPlant(o.plant() + 0.1f);
                    if (o.plant() >= 1f) {
                        o.setPlant(0);
                        o.setCharge(CHARGE_TICKS);
                        tell(level, m, Component.translatable("gunsmod.match.planted", CHARGE_TICKS / 20).withStyle(ChatFormatting.RED));
                        level.playSound(null, o.getBlockPos(), Reg.sound("arm_switch"), SoundSource.BLOCKS, 4f, 0.5f);
                    }
                } else if (o.plant() > 0) {
                    o.setPlant(Math.max(0, o.plant() - 0.05f));
                }
                continue;
            }
            o.setCharge(o.charge() - 10);
            if (o.charge() % 40 < 10) level.playSound(null, o.getBlockPos(), Reg.sound("ui_beep"), SoundSource.BLOCKS, 2f, o.charge() < 200 ? 1.6f : 1.1f);
            if (n[def] > 0 && n[att] == 0) {
                o.setDefuse(o.defuse() + 0.07f);
                if (o.defuse() >= 1f) {
                    o.setDefuse(0);
                    o.setCharge(-1);
                    tell(level, m, Component.translatable("gunsmod.match.defused").withStyle(m.defenders().chat));
                }
            } else if (o.defuse() > 0) {
                o.setDefuse(Math.max(0, o.defuse() - 0.05f));
            }
            if (o.charge() >= 0 && o.charge() <= 0) {
                o.setCharge(-1);
                Blast.explode(level, null, o.center(), 6f, false, Level.ExplosionInteraction.NONE, Blast.FX_HEAVY, 2.4f);
                o.damage(ObjectiveBlockEntity.TARGET_HP);
                m.targetLost = true;
                tell(level, m, Component.translatable("gunsmod.match.target_destroyed", String.valueOf(o.label())).withStyle(ChatFormatting.RED));
            }
        }
    }

    /** headquarters defence: waves of attackers coming at the HQ from all round, bigger every time */
    private static void wave(ServerLevel level, Match m, List<ObjectiveBlockEntity> objs, long t) {
        ObjectiveBlockEntity hq = null;
        for (ObjectiveBlockEntity o : objs) if (o.kind() == ObjectiveBlock.Kind.TARGET && o.owner() == m.defenders() && !o.destroyed()) hq = o;
        if (hq == null) return;
        m.wave++;
        m.nextWave = t + 1200;
        int n = Math.min(14, 4 + 2 * m.wave);
        double a0 = level.random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = a0 + (i % 3 - 1) * 0.35;
            double d = 75 + level.random.nextDouble() * 35;
            double x = hq.center().x + Math.cos(a) * d + level.random.nextGaussian() * 4, z = hq.center().z + Math.sin(a) * d + level.random.nextGaussian() * 4;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            if (!level.isLoaded(BlockPos.containing(x, y, z))) continue;
            BotEntity b = new BotEntity(Reg.BOT.get(), level);
            b.moveTo(x, y + 0.1, z, level.random.nextFloat() * 360f, 0);
            b.setup(m.attackers, level.random, null);
            b.home = hq.getBlockPos();
            level.addFreshEntity(b);
        }
        tell(level, m, Component.translatable("gunsmod.match.wave", m.wave, n).withStyle(ChatFormatting.RED));
    }

    private static int[] owned(List<ObjectiveBlockEntity> objs) {
        int[] n = new int[3];
        for (ObjectiveBlockEntity o : objs) {
            if (o.kind() != ObjectiveBlock.Kind.POINT) continue;
            n[2]++;
            if (o.owner() != null) n[Match.idx(o.owner())]++;
        }
        return n;
    }

    private static int targetsLeft(Match m, List<ObjectiveBlockEntity> objs) {
        int n = 0;
        for (ObjectiveBlockEntity o : objs) if (o.kind() == ObjectiveBlock.Kind.TARGET && o.owner() == m.defenders() && !o.destroyed()) n++;
        return n;
    }

    private static void second(ServerLevel level, Match m, List<ObjectiveBlockEntity> objs, long t) {
        int[] own = owned(objs);
        if (own[2] >= 2) for (int i = 0; i < 2; i++) if (own[i] == own[2]) m.heldAll[i] = true;
        if (m.mode == GameMode.CONQUEST || m.mode == GameMode.COMBINED) {
            // the side holding fewer points bleeds tickets
            if (t % 60 == 0 && own[0] != own[1]) {
                int loser = own[0] > own[1] ? 1 : 0;
                m.tickets[loser] = Math.max(0, m.tickets[loser] - Math.abs(own[0] - own[1]));
            }
        }
        if (m.mode == GameMode.DOMINATION) for (int i = 0; i < 2; i++) m.score[i] += own[i];
        // the winner
        int g = 0, r = 1;
        switch (m.mode) {
            case CONQUEST, COMBINED -> {
                if (m.tickets[g] <= 0) end(level, m, r, "gunsmod.match.why.tickets");
                else if (m.tickets[r] <= 0) end(level, m, g, "gunsmod.match.why.tickets");
            }
            case TDM, DOMINATION, CTF -> {
                if (m.score[g] >= m.limit) end(level, m, g, "gunsmod.match.why.score");
                else if (m.score[r] >= m.limit) end(level, m, r, "gunsmod.match.why.score");
            }
            case ASSAULT -> {
                if (targetsLeft(m, objs) == 0) end(level, m, Match.idx(m.attackers), "gunsmod.match.why.targets");
                else if (m.tickets[Match.idx(m.attackers)] <= 0) end(level, m, Match.idx(m.defenders()), "gunsmod.match.why.attack_spent");
            }
            case SEARCH_DESTROY, HQ_DEFENSE -> {
                if (m.targetLost || targetsLeft(m, objs) == 0) end(level, m, Match.idx(m.attackers), "gunsmod.match.why.targets");
            }
            case CONVOY_ESCORT, CONVOY_INTERCEPT -> {
                int need = (m.convoyTotal + 1) / 2;
                if (m.arrived.size() >= need) end(level, m, Match.idx(m.escort()), "gunsmod.match.why.convoy_through");
                else if (m.convoyLost > m.convoyTotal - need) end(level, m, Match.idx(m.attackers), "gunsmod.match.why.convoy_stopped");
            }
        }
        if (!m.running) return;
        for (ServerPlayer p : players(level, m)) PacketDistributor.sendToPlayer(p, state(level, m, p, null, ""));
    }

    private static void timeUp(ServerLevel level, Match m) {
        int g = 0, r = 1;
        switch (m.mode) {
            case CONQUEST, COMBINED -> end(level, m, m.tickets[g] == m.tickets[r] ? 2 : m.tickets[g] > m.tickets[r] ? g : r, "gunsmod.match.why.time");
            case TDM, DOMINATION, CTF -> end(level, m, m.score[g] == m.score[r] ? 2 : m.score[g] > m.score[r] ? g : r, "gunsmod.match.why.time");
            case CONVOY_ESCORT, CONVOY_INTERCEPT -> end(level, m, Match.idx(m.attackers), "gunsmod.match.why.convoy_late");
            default -> end(level, m, Match.idx(m.defenders()), "gunsmod.match.why.held");
        }
    }

    // ------------------------------------------------------------------------------------ events
    /** deaths: tickets, kills, vehicle kills, a dropped flag, a lost convoy vehicle */
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (!(v.level() instanceof ServerLevel level)) return;
        Match m = running(level);
        if (m == null || !m.covers(v.position())) return;
        BotTeam vt = BotTeam.of(v);
        Entity src = e.getSource().getEntity() != null ? e.getSource().getEntity() : e.getSource().getDirectEntity();
        BotTeam kt = src == null ? null : BotTeam.of(src);
        if (kt == null && src instanceof Projectile pr && pr.getOwner() != null) kt = BotTeam.of(pr.getOwner());
        if (vt == null) return;
        int vi = Match.idx(vt);
        if (v instanceof BotEntity || v instanceof Player) {
            if (m.mode.tickets() && (m.mode != GameMode.ASSAULT || vt == m.attackers)) m.tickets[vi] = Math.max(0, m.tickets[vi] - 1);
            if (m.mode == GameMode.TDM && kt != null && kt != vt) m.score[Match.idx(kt)]++;
            for (int i = 0; i < 2; i++) {
                if (v.getUUID().equals(m.carrier[i])) {
                    m.carrier[i] = null;
                    m.dropped[i] = v.position();
                    m.droppedAt[i] = level.getGameTime();
                    v.setGlowingTag(false);
                    tell(level, m, Component.translatable("gunsmod.match.flag_dropped", Component.translatable("gunsmod.team." + BotTeam.byId(i).name().toLowerCase())));
                }
            }
        } else if (v instanceof VehicleEntity || v instanceof JeepEntity) {
            if (kt != null && kt != vt) m.vehicleKills[Match.idx(kt)]++;
            if (m.mode == GameMode.COMBINED) m.tickets[vi] = Math.max(0, m.tickets[vi] - 3);
            if (m.convoy.remove(v.getUUID())) {
                m.convoyLost++;
                tell(level, m, Component.translatable("gunsmod.match.convoy_lost", m.convoyLost, m.convoyTotal).withStyle(ChatFormatting.RED));
            }
        }
        m.touch();
    }

    /** an explosion: targets of the defending side near it are damaged (assault, headquarters defence) */
    public static void blast(Level level, @Nullable Entity source, Vec3 at, float power) {
        if (level.isClientSide) return;
        Match m = running(level);
        if (m == null || !(m.mode == GameMode.ASSAULT || m.mode == GameMode.HQ_DEFENSE) || !m.covers(at)) return;
        BotTeam st = source == null ? null : BotTeam.of(source);
        if (st == null && source instanceof Projectile pr && pr.getOwner() != null) st = BotTeam.of(pr.getOwner());
        if (st == m.defenders()) return;
        double reach = Math.max(3, power * 2.2);
        for (ObjectiveBlockEntity o : objectives(level)) {
            if (o.kind() != ObjectiveBlock.Kind.TARGET || o.owner() != m.defenders() || o.destroyed()) continue;
            double d = o.center().distanceTo(at);
            if (d > reach) continue;
            o.damage((float) (power * 7 * (1 - d / reach)));
            if (o.destroyed()) {
                m.targetLost = true;
                tell((ServerLevel) level, m, Component.translatable("gunsmod.match.target_destroyed", String.valueOf(o.label())).withStyle(ChatFormatting.RED));
                Blast.fx(level, o.center(), Blast.FX_HEAVY, 1.6f);
            }
        }
    }

    // ------------------------------------------------------------------------------------ where soldiers and vehicles go
    /** an objective to make for: the place, how close is close enough, whether to stay and guard it there */
    public record Goal(Vec3 at, double radius, boolean guard, @Nullable Entity chase) {}

    @Nullable
    public static Goal goalFor(Entity unit, BotTeam team) {
        Match m = running(unit.level());
        if (m == null || team == null || !m.covers(unit.position())) return null;
        List<ObjectiveBlockEntity> objs = objectives(unit.level());
        int me = Match.idx(team);
        long salt = unit.getUUID().getLeastSignificantBits() & 0xFFFF;
        switch (m.mode) {
            case CONQUEST, DOMINATION, COMBINED -> {
                ObjectiveBlockEntity best = null, mine = null;
                double bs = Double.MAX_VALUE, ms = Double.MAX_VALUE;
                for (ObjectiveBlockEntity o : objs) {
                    if (o.kind() != ObjectiveBlock.Kind.POINT) continue;
                    double d = Math.sqrt(o.center().distanceToSqr(unit.position()));
                    boolean ours = o.owner() == team;
                    float bal = team == BotTeam.GREEN ? o.balance() : -o.balance();
                    if (ours && bal >= 0.99f) {
                        if (d < ms) {
                            ms = d;
                            mine = o;
                        }
                        continue;
                    }
                    // spread out: each soldier weighs the points a little differently
                    double score = d + (o.owner() != null ? 25 : 0) + ((salt + o.label()) % 5) * 18;
                    if (score < bs) {
                        bs = score;
                        best = o;
                    }
                }
                if (best != null && (mine == null || salt % 10 >= 3)) return new Goal(best.center(), ObjectiveBlockEntity.RADIUS - 2, true, null);
                return mine == null ? null : new Goal(mine.center(), ObjectiveBlockEntity.RADIUS + 4, true, null);
            }
            case CTF -> {
                BotTeam enemy = team.enemy();
                int ei = Match.idx(enemy);
                if (unit.getUUID().equals(m.carrier[ei])) {
                    ObjectiveBlockEntity home = flagOf(objs, team, unit.position());
                    return home == null ? null : new Goal(home.center(), 1.0, false, null);
                }
                if (m.carrier[me] != null && salt % 3 == 0) {
                    Entity c = ((ServerLevel) unit.level()).getEntity(m.carrier[me]);
                    if (c != null) return new Goal(c.position(), 3, false, c);
                }
                if (m.dropped[me] != null) return new Goal(m.dropped[me], 1.0, false, null);
                if (salt % 2 == 0) {
                    if (m.dropped[ei] != null) return new Goal(m.dropped[ei], 1.0, false, null);
                    ObjectiveBlockEntity theirs = flagOf(objs, enemy, unit.position());
                    if (theirs != null && theirs.flagHome()) return new Goal(theirs.center(), 1.0, false, null);
                }
                ObjectiveBlockEntity ours = flagOf(objs, team, unit.position());
                return ours == null ? null : new Goal(ours.center(), 12, true, null);
            }
            case ASSAULT, SEARCH_DESTROY, HQ_DEFENSE -> {
                boolean attack = team == m.attackers;
                List<ObjectiveBlockEntity> ts = new ArrayList<>();
                ObjectiveBlockEntity planted = null;
                for (ObjectiveBlockEntity o : objs) {
                    if (o.kind() != ObjectiveBlock.Kind.TARGET || o.owner() != m.defenders() || o.destroyed()) continue;
                    ts.add(o);
                    if (o.charge() >= 0) planted = o;
                }
                if (ts.isEmpty()) return null;
                if (planted != null) return new Goal(planted.center(), attack ? 9 : 2.5, attack, null);
                if (attack) {
                    ObjectiveBlockEntity best = null;
                    double bd = Double.MAX_VALUE;
                    for (ObjectiveBlockEntity o : ts) {
                        double d = o.center().distanceToSqr(unit.position()) + ((salt + o.label()) % 3) * 900;
                        if (d < bd) {
                            bd = d;
                            best = o;
                        }
                    }
                    return new Goal(best.center(), m.mode == GameMode.SEARCH_DESTROY ? 2.0 : 6, m.mode != GameMode.SEARCH_DESTROY, null);
                }
                ObjectiveBlockEntity guard = ts.get((int) (salt % ts.size()));
                return new Goal(guard.center(), 12, true, null);
            }
            case CONVOY_ESCORT, CONVOY_INTERCEPT -> {
                Entity best = null;
                double bd = Double.MAX_VALUE;
                for (UUID u : m.convoy) {
                    Entity e = ((ServerLevel) unit.level()).getEntity(u);
                    if (e == null || !e.isAlive()) continue;
                    double d = e.distanceToSqr(unit);
                    if (d < bd) {
                        bd = d;
                        best = e;
                    }
                }
                if (best == null) return null;
                return team == m.escort() ? new Goal(best.position(), 14, true, null) : new Goal(best.position(), 25, false, best);
            }
            default -> {
                return null;
            }
        }
    }

    /** a vehicle of a side in a battle patrols round its side's current objective instead of round its home */
    public static Vec3 vehicleHome(Entity v, Vec3 home) {
        BotTeam t = BotTeam.of(v);
        Goal g = t == null ? null : goalFor(v, t);
        return g == null ? home : g.at();
    }

    // ------------------------------------------------------------------------------------ what the players are told
    /** one line of a side's mission: state 0 under way, 1 done, 2 failed */
    public record Line(String key, String arg, int state, boolean optional) {}

    public static List<Line> mission(ServerLevel level, Match m, BotTeam team) {
        List<Line> l = new ArrayList<>();
        List<ObjectiveBlockEntity> objs = objectives(level);
        int me = Match.idx(team), them = 1 - me;
        int done = m.winner < 0 ? 0 : m.winner == me ? 1 : 2;
        int[] own = owned(objs);
        String lim = String.valueOf(m.limit);
        boolean attack = team == m.attackers;
        switch (m.mode) {
            case TDM -> l.add(new Line("gunsmod.mission.tdm", lim, done, false));
            case CONQUEST, COMBINED -> {
                l.add(new Line("gunsmod.mission.conquest", own[me] + "/" + own[2], done, false));
                l.add(new Line("gunsmod.mission.drain", String.valueOf(m.tickets[them]), done, false));
                l.add(new Line("gunsmod.mission.all_points", "", m.heldAll[me] ? 1 : 0, true));
                if (m.mode == GameMode.COMBINED) l.add(new Line("gunsmod.mission.vehicles", m.vehicleKills[me] + "/5", m.vehicleKills[me] >= 5 ? 1 : 0, true));
            }
            case DOMINATION -> {
                l.add(new Line("gunsmod.mission.domination", m.score[me] + "/" + lim, done, false));
                l.add(new Line("gunsmod.mission.all_points", "", m.heldAll[me] ? 1 : 0, true));
            }
            case CTF -> {
                l.add(new Line("gunsmod.mission.ctf", m.score[me] + "/" + lim, done, false));
                l.add(new Line("gunsmod.mission.ctf_guard", "", m.carrier[me] != null || m.dropped[me] != null ? 2 : done == 1 ? 1 : 0, false));
                l.add(new Line("gunsmod.mission.ctf_return", "", m.returns[me] > 0 ? 1 : 0, true));
            }
            case ASSAULT, HQ_DEFENSE, SEARCH_DESTROY -> {
                int left = targetsLeft(m, objs);
                String key = m.mode == GameMode.SEARCH_DESTROY ? (attack ? "gunsmod.mission.sd_attack" : "gunsmod.mission.sd_defend")
                        : m.mode == GameMode.HQ_DEFENSE ? (attack ? "gunsmod.mission.hq_attack" : "gunsmod.mission.hq_defend")
                        : attack ? "gunsmod.mission.assault_attack" : "gunsmod.mission.assault_defend";
                l.add(new Line(key, String.valueOf(left), done, false));
                if (!attack) l.add(new Line("gunsmod.mission.lose_none", "", m.targetLost ? 2 : done == 1 ? 1 : 0, true));
                if (m.mode == GameMode.HQ_DEFENSE && !attack) l.add(new Line("gunsmod.mission.waves", String.valueOf(m.wave), done, false));
                if (attack && m.mode == GameMode.ASSAULT) l.add(new Line("gunsmod.mission.tickets_left", String.valueOf(m.tickets[me]), done, false));
            }
            case CONVOY_ESCORT, CONVOY_INTERCEPT -> {
                boolean escort = team == m.escort();
                l.add(new Line(escort ? "gunsmod.mission.convoy_escort" : "gunsmod.mission.convoy_intercept",
                        escort ? m.arrived.size() + "/" + (m.convoyTotal + 1) / 2 : m.convoyLost + "/" + (m.convoyTotal / 2 + 1), done, false));
                l.add(new Line(escort ? "gunsmod.mission.convoy_all" : "gunsmod.mission.convoy_none", "",
                        escort ? (m.convoyLost > 0 ? 2 : done == 1 ? 1 : 0) : (m.arrived.isEmpty() ? (done == 1 ? 1 : 0) : 2), true));
            }
        }
        if (m.mode != GameMode.COMBINED) l.add(new Line("gunsmod.mission.vehicles", m.vehicleKills[me] + "/3", m.vehicleKills[me] >= 3 ? 1 : 0, true));
        return l;
    }

    /** the battle state for one player (and the table's settings when he opens it) */
    public static Net.MatchState state(ServerLevel level, Match m, ServerPlayer p, @Nullable BlockPos open, String banner) {
        List<Net.ObjInfo> objs = new ArrayList<>();
        if (m.running || open != null) {
            BlockPos c = open != null && !m.running ? open : m.control;
            for (ObjectiveBlockEntity o : c == null ? List.<ObjectiveBlockEntity>of() : Objectives.near(level, c, m.radius)) {
                if (o.kind() == ObjectiveBlock.Kind.CONTROL) continue;
                int flags = (o.flagHome() ? 1 : 0) | (o.charge() >= 0 ? 8 : 0);
                float prog = o.kind() == ObjectiveBlock.Kind.TARGET ? (o.charge() >= 0 ? o.defuse() : o.plant()) : o.balance();
                objs.add(new Net.ObjInfo((byte) o.kind().ordinal(), (byte) (o.owner() == null ? -1 : o.owner().ordinal()), prog, (byte) o.label(),
                        (float) o.center().x, (float) o.center().y, (float) o.center().z, o.hp(), (byte) flags, o.charge()));
            }
            for (int i = 0; i < 2; i++) {
                Vec3 at = m.dropped[i];
                int fl = 4;
                if (m.carrier[i] != null) {
                    Entity e = level.getEntity(m.carrier[i]);
                    at = e == null ? null : e.position();
                    fl = 2;
                }
                if (at != null) objs.add(new Net.ObjInfo((byte) ObjectiveBlock.Kind.FLAG.ordinal(), (byte) i, 0f, (byte) 'F', (float) at.x, (float) at.y + 2, (float) at.z, 0f,
                        (byte) fl, -1));
            }
            if (m.mode.convoy()) {
                for (UUID u : m.convoy) {
                    Entity e = level.getEntity(u);
                    if (e == null) continue;
                    objs.add(new Net.ObjInfo((byte) 5, (byte) m.escort().ordinal(), 0f, (byte) 'C', (float) e.getX(), (float) e.getY() + 2, (float) e.getZ(), 0f,
                            (byte) (m.arrived.contains(u) ? 16 : 0), -1));
                }
            }
        }
        BotTeam team = BotTeam.of(p);
        List<Net.MissionLine> lines = new ArrayList<>();
        if ((m.running || m.winner >= 0) && team != null) {
            for (Line l : mission(level, m, team)) lines.add(new Net.MissionLine(l.key(), l.arg(), (byte) l.state(), l.optional()));
        }
        return new Net.MatchState(m.mode.ordinal(), m.running, Math.max(0, m.timeLeft), m.tickets[0], m.tickets[1], m.score[0], m.score[1], m.winner,
                m.attackers.ordinal(), m.limit, m.minutes, m.radius, team == null ? 0 : team.ordinal(), objs, lines, open != null, open == null ? BlockPos.ZERO : open, banner);
    }
}
