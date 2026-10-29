package com.gunsmod.world;

import com.gunsmod.entity.BotTeam;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * What the soldiers of an orchard battlefield know about it: where both bases are and the road network between them.
 * Local coordinates: x to the right, z from the green base (z = -250) towards the red base (z = +249).
 * Roads: the main track down the middle, two flank tracks at x = +-80, cross tracks at z = -100, 0, +100 and links in
 * front of each base (z = +-170) joining the flank tracks to the main one.
 */
public record BattleMap(BlockPos origin, Direction fwd) {
    public static final int HW = OrchardBuilder.W / 2, HL = OrchardBuilder.L / 2;
    public static final int FLANK = 80, LINK = 170, BASE = 232;
    private static final int[] CROSS = {-100, 0, 100};

    // ------------------------------------------------------------------------------------ the road network
    /** is this local column part of a road (a dirt track) */
    public static boolean isRoad(int x, int z) {
        if (Math.abs(x) <= 2) return true;
        if (Math.abs(Math.abs(x) - FLANK) <= 2 && Math.abs(z) <= LINK + 1) return true;
        if (Math.abs(Math.abs(z) - LINK) <= 1 && Math.abs(x) <= FLANK + 2) return true;
        for (int c : CROSS) if (Math.abs(z - c) <= 1 && Math.abs(x) < HW - 2) return true;
        return false;
    }

    /** within {@code r} blocks of a road (no trees there: the roads stay clear for tanks) */
    public static boolean nearRoad(int x, int z, int r) {
        if (Math.abs(x) <= 2 + r) return true;
        if (Math.abs(Math.abs(x) - FLANK) <= 2 + r && Math.abs(z) <= LINK + 1 + r) return true;
        if (Math.abs(Math.abs(z) - LINK) <= 1 + r && Math.abs(x) <= FLANK + 2 + r) return true;
        for (int c : CROSS) if (Math.abs(z - c) <= 1 + r) return true;
        return false;
    }

    // ------------------------------------------------------------------------------------ coordinates
    public Vec3 world(double x, double z) {
        Direction right = fwd.getClockWise();
        return new Vec3(origin.getX() + 0.5 + right.getStepX() * x + fwd.getStepX() * z, origin.getY() + 1,
                origin.getZ() + 0.5 + right.getStepZ() * x + fwd.getStepZ() * z);
    }

    /** world position -> local {x, z} */
    public double[] local(Vec3 p) {
        Direction right = fwd.getClockWise();
        double dx = p.x - origin.getX() - 0.5, dz = p.z - origin.getZ() - 0.5;
        return new double[]{dx * right.getStepX() + dz * right.getStepZ(), dx * fwd.getStepX() + dz * fwd.getStepZ()};
    }

    public boolean contains(Vec3 p) {
        double[] l = local(p);
        return Math.abs(l[0]) <= HW + 8 && Math.abs(l[1]) <= HL + 8;
    }

    public Vec3 center() {
        return world(0, 0);
    }

    /** where the team's own base is (and the enemy's, for the other team) */
    public Vec3 base(BotTeam team) {
        return world(0, team == BotTeam.RED ? BASE : -BASE);
    }

    public Vec3 enemyBase(BotTeam team) {
        return world(0, team == BotTeam.RED ? -BASE : BASE);
    }

    /** where a player of the team (re)spawns: a free corner of the team's base yard */
    public Vec3 spawn(BotTeam team) {
        return team == BotTeam.RED ? world(8, HL - 1 - 44) : world(-8, -HL + 44);
    }

    /** facing the enemy */
    public float yaw(BotTeam team) {
        return (team == BotTeam.RED ? fwd.getOpposite() : fwd).toYRot();
    }

    /** the middle of the enemy half: where the team's aircraft patrol */
    public Vec3 front(BotTeam team) {
        return world(0, team == BotTeam.RED ? -70 : 70);
    }

    /** half the soldiers take the main track, a quarter each flank track */
    public static int randomLane(RandomSource r) {
        int n = r.nextInt(4);
        return n < 2 ? 0 : n == 2 ? -1 : 1;
    }

    /**
     * The point itself when it is within {@code margin} blocks of a road, otherwise the closest point that is:
     * soldiers fight from the roads and the roadside, they never wander off into the orchards.
     */
    public Vec3 keepNear(Vec3 p, double margin) {
        double[] l = local(p);
        double x = l[0], z = l[1];
        // nearest point on each road centre line: {x, z} of the best one so far
        double[] best = {0, Mth.clamp(z, -HL, HL)};
        best = closer(x, z, best, new double[]{FLANK, Mth.clamp(z, -LINK, LINK)});
        best = closer(x, z, best, new double[]{-FLANK, Mth.clamp(z, -LINK, LINK)});
        best = closer(x, z, best, new double[]{Mth.clamp(x, -FLANK, FLANK), LINK});
        best = closer(x, z, best, new double[]{Mth.clamp(x, -FLANK, FLANK), -LINK});
        for (int c : CROSS) best = closer(x, z, best, new double[]{Mth.clamp(x, -HW + 3, HW - 3), c});
        double dx = x - best[0], dz = z - best[1], d = Math.sqrt(dx * dx + dz * dz);
        if (d <= margin) return p;
        Vec3 w = world(best[0] + dx * margin / d, best[1] + dz * margin / d);
        return new Vec3(w.x, p.y, w.z);
    }

    private static double[] closer(double x, double z, double[] a, double[] b) {
        double da = (x - a[0]) * (x - a[0]) + (z - a[1]) * (z - a[1]), db = (x - b[0]) * (x - b[0]) + (z - b[1]) * (z - b[1]);
        return db < da ? b : a;
    }

    // ------------------------------------------------------------------------------------ routes
    /**
     * The way from the team's base to the enemy base along the roads, as points every few blocks:
     * lane 0 straight down the main track, lane -1 / +1 round by the left / right flank track.
     */
    public List<Vec3> route(BotTeam team, int lane, double step) {
        int s = team == BotTeam.RED ? -1 : 1;    // direction of advance in local z
        List<double[]> nodes = new ArrayList<>();
        nodes.add(new double[]{0, -s * BASE});
        nodes.add(new double[]{0, -s * LINK});
        if (lane == 0) {
            for (int c : new int[]{-100, 0, 100}) nodes.add(new double[]{0, s * c});
        } else {
            int x = lane * FLANK * s;      // mirrored for red so that "left" is the same side of the field for both
            nodes.add(new double[]{x, -s * LINK});
            for (int c : new int[]{-100, 0, 100}) nodes.add(new double[]{x, s * c});
            nodes.add(new double[]{x, s * LINK});
        }
        nodes.add(new double[]{0, s * LINK});
        nodes.add(new double[]{0, s * BASE});
        List<Vec3> out = new ArrayList<>();
        for (int i = 0; i + 1 < nodes.size(); i++) {
            double[] a = nodes.get(i), b = nodes.get(i + 1);
            double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
            int n = Math.max(1, (int) Math.ceil(len / step));
            for (int k = 0; k < n; k++) {
                double t = k / (double) n;
                out.add(world(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t));
            }
        }
        double[] last = nodes.get(nodes.size() - 1);
        out.add(world(last[0], last[1]));
        return out;
    }

    /** the route point to head for from here: the closest one, then the next along (never back towards home) */
    public static int resume(List<Vec3> route, Vec3 from, int atLeast) {
        int best = Math.max(0, atLeast);
        double bestD = Double.MAX_VALUE;
        for (int i = Math.max(0, atLeast); i < route.size(); i++) {
            double d = route.get(i).distanceToSqr(from.x, route.get(i).y, from.z);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** is this route point a crossroads (a short halt there) */
    public boolean junction(Vec3 p) {
        double[] l = local(p);
        double ax = Math.abs(l[0]), az = Math.abs(l[1]);
        boolean onCross = Math.abs(az - LINK) < 1.5 || Math.abs(az - 100) < 1.5 || az < 1.5;
        return onCross && (ax < 1.5 || Math.abs(ax - FLANK) < 1.5);
    }

    // ------------------------------------------------------------------------------------ save
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.put("Origin", NbtUtils.writeBlockPos(origin));
        t.putInt("Fwd", fwd.get2DDataValue());
        return t;
    }

    @Nullable
    public static BattleMap load(CompoundTag t) {
        if (!t.contains("Origin")) return null;
        BlockPos o = NbtUtils.readBlockPos(t, "Origin").orElse(null);
        return o == null ? null : new BattleMap(o, Direction.from2DDataValue(t.getInt("Fwd")));
    }
}
