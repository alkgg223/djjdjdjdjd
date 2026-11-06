package com.arkragnarok.survivor;

import com.arkragnarok.entity.ArkCreature;
import com.arkragnarok.entity.ResourceNode;
import com.arkragnarok.species.Diet;
import com.arkragnarok.species.Temper;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Eyesight.  Every tick a few sight rays go out from the eyes inside a 140 degree view cone (and all around now and
 * then, when it looks about), up to {@link #RANGE} blocks; whatever a ray hits first is what it sees - nothing
 * behind walls or underground (no x-ray).  An underground air space seen from outside (dark, below the surface) is
 * a cave opening.  Entities count only when an unobstructed line of sight reaches them.
 */
public final class Perception {
    public static final double RANGE = 150.0;
    private static final int RAYS_PER_TICK = 10;
    private final Survivor s;
    private int entityScan;

    public Perception(Survivor s) {
        this.s = s;
    }

    public void tick() {
        Level level = s.level();
        long now = level.getGameTime();
        RandomSource r = s.getRandom();
        Vec3 eye = s.getEyePosition();
        float yaw = s.getYHeadRot();
        boolean around = s.tickCount % 200 < 20;           // looks all around every 10 s
        for (int i = 0; i < RAYS_PER_TICK; i++) {
            float dy = around ? r.nextFloat() * 360f : (r.nextFloat() - 0.5f) * 140f;
            float dp = (float) (Math.pow(r.nextFloat(), 0.7) * (r.nextBoolean() ? 1 : -1)) * 45f + 8f;
            Vec3 dir = Vec3.directionFromRotation(dp, yaw + dy);
            Vec3 end = eye.add(dir.scale(RANGE));
            BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, s));
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            BlockPos p = hit.getBlockPos();
            BlockState st = level.getBlockState(p);
            if (st.is(BlockTags.LOGS)) s.memory.see(Memory.Kind.TREE, p, now, null, "");
            else if (st.getBlock() instanceof com.arkragnarok.building.PieceBlock && !s.ownsPosition(p)) s.memory.see(Memory.Kind.BASE, p, now, null, "");
            else if (!st.getFluidState().isEmpty() && st.getFluidState().isSource()) s.memory.see(Memory.Kind.WATER, p, now, null, "");
            else if (st.is(BlockTags.BASE_STONE_OVERWORLD) && level.isEmptyBlock(p.above()) && !s.ownsPosition(p)) s.memory.see(Memory.Kind.STONE, p, now, null, "");
            BlockPos before = BlockPos.containing(hit.getLocation().subtract(dir.scale(0.4)));
            if (level.isEmptyBlock(before)) {
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, before.getX(), before.getZ());
                if (before.getY() < surface - 4 && level.getBrightness(LightLayer.SKY, before) < 6) {
                    s.memory.see(Memory.Kind.CAVE, before, now, null, "");
                }
                if (st.isSolidRender(level, p) && hit.getDirection() == net.minecraft.core.Direction.UP && before.getY() >= surface - 1) {
                    s.memory.see(Memory.Kind.FLAT, before, now, null, "");
                }
            }
        }
        if (++entityScan >= 20) {
            entityScan = 0;
            int budget = 24;
            AABB box = s.getBoundingBox().inflate(RANGE, 48, RANGE);
            for (Entity e : level.getEntities(s, box, e -> e instanceof ResourceNode || e instanceof ArkCreature
                    || e instanceof net.minecraft.world.entity.animal.Animal)) {
                if (budget-- <= 0) break;
                if (e.distanceToSqr(s) > RANGE * RANGE || !visible(e)) continue;
                if (e instanceof ResourceNode n && !n.depleted()) {
                    s.memory.see(Memory.Kind.NODE, e.blockPosition(), now, e.getUUID(), n.kind().id);
                } else if (e instanceof ArkCreature c && !c.isTamed()) {
                    if (dangerous(c)) s.memory.see(Memory.Kind.THREAT, e.blockPosition(), now, e.getUUID(), c.species().id);
                    s.memory.see(Memory.Kind.DINO, e.blockPosition(), now, e.getUUID(), c.species().id);
                } else if (e instanceof net.minecraft.world.entity.animal.Animal) {
                    s.memory.see(Memory.Kind.PREY, e.blockPosition(), now, e.getUUID(), "");
                }
            }
        }
    }

    public boolean visible(Entity e) {
        Vec3 eye = s.getEyePosition();
        Vec3 to = e.getBoundingBox().getCenter();
        BlockHitResult hit = s.level().clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, s));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(eye) >= to.distanceToSqr(eye) - Mth.square(e.getBbWidth());
    }

    public static boolean dangerous(ArkCreature c) {
        return c.species().diet != Diet.HERBIVORE && c.species().temper == Temper.AGGRESSIVE && c.getBbHeight() > 0.9f
                || (c.species().temper == Temper.TERRITORIAL && c.getBbWidth() > 1.6f);
    }
}
