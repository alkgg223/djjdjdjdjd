package com.gunsmod.entity.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * A route set with the route planner: points joined one after the other, any point leading on to up to three others.
 * A soldier or a vehicle on the route heads for one point after the other and picks one of the branches at random
 * where the route splits. At the end of the route it stays there. The ground round a follower keeps running even
 * with no player near, so it carries on along its route.
 */
public final class Waypoints {
    public static final int MAX_POINTS = 96, MAX_BRANCHES = 3;
    public static final TicketType<ChunkPos> TICKET = TicketType.create("gunsmod_route", Comparator.comparingLong(ChunkPos::toLong), 60);

    public final List<BlockPos> points = new ArrayList<>();
    public final List<int[]> next = new ArrayList<>();
    /** the follower's next point (-1 = the route is done); for the planner the point new ones join on to */
    public int at;

    public boolean isEmpty() {
        return points.isEmpty();
    }

    public boolean active() {
        return at >= 0 && at < points.size();
    }

    @Nullable
    public Vec3 target() {
        return active() ? Vec3.atBottomCenterOf(points.get(at)) : null;
    }

    /** on to the next point (a random branch); false when the route is over */
    public boolean advance(RandomSource r) {
        if (!active()) return false;
        int[] n = next.get(at);
        at = n.length == 0 ? -1 : n[r.nextInt(n.length)];
        return active();
    }

    /** a new point after point {@code from} (or the first one); its index, -1 when it cannot branch any more */
    public int add(BlockPos p, int from) {
        if (points.size() >= MAX_POINTS) return -1;
        if (!points.isEmpty()) {
            if (from < 0 || from >= points.size()) from = points.size() - 1;
            int[] n = next.get(from);
            if (n.length >= MAX_BRANCHES) return -1;
            int[] m = new int[n.length + 1];
            System.arraycopy(n, 0, m, 0, n.length);
            m[n.length] = points.size();
            next.set(from, m);
        }
        points.add(p.immutable());
        next.add(new int[0]);
        return points.size() - 1;
    }

    /** the point closest to p within maxDist, or -1 */
    public int nearest(BlockPos p, double maxDist) {
        int best = -1;
        double bd = maxDist * maxDist;
        for (int i = 0; i < points.size(); i++) {
            double d = points.get(i).distSqr(p);
            if (d <= bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    /** undo: the last point and every link to it go */
    public void removeLast() {
        if (points.isEmpty()) return;
        int last = points.size() - 1;
        points.remove(last);
        next.remove(last);
        for (int i = 0; i < next.size(); i++) {
            int[] n = next.get(i);
            int k = 0;
            int[] m = new int[n.length];
            for (int v : n) if (v != last) m[k++] = v;
            if (k != n.length) {
                int[] c = new int[k];
                System.arraycopy(m, 0, c, 0, k);
                next.set(i, c);
            }
        }
        if (at >= points.size()) at = points.size() - 1;
    }

    public int branches(int i) {
        return i >= 0 && i < next.size() ? next.get(i).length : 0;
    }

    /** a copy for a follower, starting at the first point */
    public Waypoints fresh() {
        Waypoints w = load(save());
        w.at = w.points.isEmpty() ? -1 : 0;
        return w;
    }

    // ------------------------------------------------------------------------------------ followers
    /** where a vehicle should drive now: the next point, passed on within {@code reach}; null once the route is done */
    @Nullable
    public Vec3 goal(Entity e, double reach) {
        Vec3 t = target();
        if (t == null) return null;
        double dx = t.x - e.getX(), dz = t.z - e.getZ();
        if (dx * dx + dz * dz < reach * reach) {
            if (!advance(e.level().random)) return null;
            t = target();
        }
        return t;
    }

    /** keep the chunks round a follower running (and loaded ahead) while it moves along */
    public static void keepLoaded(Entity e) {
        if (e.level() instanceof ServerLevel sl && e.tickCount % 20 == 0) {
            ChunkPos cp = e.chunkPosition();
            sl.getChunkSource().addRegionTicket(TICKET, cp, 3, cp);
        }
    }

    // ------------------------------------------------------------------------------------ save
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        ListTag list = new ListTag();
        for (int i = 0; i < points.size(); i++) {
            BlockPos p = points.get(i);
            CompoundTag c = new CompoundTag();
            c.putInt("x", p.getX());
            c.putInt("y", p.getY());
            c.putInt("z", p.getZ());
            c.putIntArray("n", next.get(i));
            list.add(c);
        }
        t.put("p", list);
        t.putInt("at", at);
        return t;
    }

    public static Waypoints load(CompoundTag t) {
        Waypoints w = new Waypoints();
        ListTag list = t.getList("p", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && i < MAX_POINTS; i++) {
            CompoundTag c = list.getCompound(i);
            w.points.add(new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z")));
            w.next.add(c.getIntArray("n"));
        }
        // drop links that lead nowhere
        for (int i = 0; i < w.next.size(); i++) {
            int[] n = w.next.get(i);
            int k = 0;
            for (int v : n) if (v >= 0 && v < w.points.size()) k++;
            if (k != n.length) {
                int[] m = new int[k];
                k = 0;
                for (int v : n) if (v >= 0 && v < w.points.size()) m[k++] = v;
                w.next.set(i, m);
            }
        }
        w.at = t.contains("at") ? t.getInt("at") : 0;
        return w;
    }

    @Nullable
    public static Waypoints read(CompoundTag parent, String key) {
        if (!parent.contains(key)) return null;
        Waypoints w = load(parent.getCompound(key));
        return w.isEmpty() ? null : w;
    }
}
