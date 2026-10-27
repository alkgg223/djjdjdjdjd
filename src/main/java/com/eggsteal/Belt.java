package com.eggsteal;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Each base has a conveyor belt; eggs ride it from left to right. Faster/better with belt level. */
public class Belt {
    static final int[] timers = new int[Arena.BASES];

    public static double speed(int lvl) { return 0.04 * (1 + 0.2 * lvl); }

    public static Creature pick(RandomSource r, int lvl) {
        double[] w = {60, 25, 10, 4, 1.5, 0.4};
        double[] ww = new double[6];
        double tot = 0;
        for (int i = 0; i < 6; i++) { ww[i] = w[i] * (1 + lvl * 0.12 * i); tot += ww[i]; }
        double x = r.nextDouble() * tot;
        int rar = 0;
        for (; rar < 5; rar++) { x -= ww[rar]; if (x < 0) break; }
        List<Creature> pool = Creature.byRarity(rar);
        return pool.get(r.nextInt(pool.size()));
    }

    public static AABB box(int base) {
        int cx = Arena.cx(base), bz = Arena.beltZ(base);
        return new AABB(cx - 12, 63, bz - 2, cx + 12, 68, bz + 3);
    }

    public static void tick(ServerLevel sl, Economy eco) {
        for (int i = 0; i < Arena.BASES; i++) {
            if (eco.baseOwner[i] == null) continue;
            Economy.Stats st = eco.map.get(eco.baseOwner[i]);
            if (st == null) continue;
            double sp = speed(st.belt);
            int cx = Arena.cx(i);
            for (ItemEntity e : sl.getEntitiesOfClass(ItemEntity.class, box(i), it -> it.getPersistentData().contains("es_belt"))) {
                e.setDeltaMovement(Vec3.ZERO);
                if (e.getX() > cx + 10.4) { e.discard(); continue; }
                e.setPos(e.getX() + sp, e.getY(), e.getZ());
            }
            if (--timers[i] <= 0) {
                timers[i] = (int) (110 / (1 + 0.15 * st.belt));
                Creature c = pick(sl.random, st.belt);
                ItemEntity e = new ItemEntity(sl, cx - 10, 64.4, Arena.beltZ(i) + 0.5, new ItemStack(EggSteal.EGG_ITEMS.get(c.ordinal()).get()));
                e.setDeltaMovement(Vec3.ZERO);
                e.setNoGravity(true);
                e.setNeverPickUp();
                e.setUnlimitedLifetime();
                e.setCustomName(Component.literal(c.tag() + " §6$" + Economy.fmt(c.price)));
                e.setCustomNameVisible(true);
                e.getPersistentData().putInt("es_belt", c.ordinal());
                e.getPersistentData().putInt("es_base", i);
                sl.addFreshEntity(e);
            }
        }
    }
}
