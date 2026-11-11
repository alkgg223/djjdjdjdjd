package com.rpbusiness.entity;

import com.rpbusiness.block.ChairBlock;
import com.rpbusiness.core.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Invisible entity that a chair's sitter rides. Removed automatically when nobody sits on it. */
public class SeatEntity extends Entity {
    private BlockPos chair = BlockPos.ZERO;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvisible(true);
    }

    public BlockPos chair() { return chair; }

    public static boolean occupied(Level level, BlockPos chair) {
        for (SeatEntity s : level.getEntitiesOfClass(SeatEntity.class, new AABB(chair).inflate(0.5)))
            if (s.chair.equals(chair) && s.isVehicle()) return true;
        return false;
    }

    /** Seats an entity on the chair at pos. Returns false if the chair is taken or invalid. */
    public static boolean sit(Level level, BlockPos pos, Entity rider) {
        BlockState st = level.getBlockState(pos);
        if (!com.rpbusiness.server.BusinessManager.isSeat(level, pos) || occupied(level, pos)) return false;
        SeatEntity seat = new SeatEntity(ModEntities.SEAT.get(), level);
        seat.chair = pos.immutable();
        seat.setPos(pos.getX() + 0.5, pos.getY() + seatHeight(level, pos, st), pos.getZ() + 0.5);
        com.rpbusiness.business.Business biz = level instanceof net.minecraft.server.level.ServerLevel sl ? com.rpbusiness.business.BusinessData.get(sl).at(pos) : null;
        float yaw = com.rpbusiness.server.BusinessManager.seatFacing(level, biz, pos).toYRot();
        seat.setYRot(yaw);
        level.addFreshEntity(seat);
        if (!rider.startRiding(seat, true)) { seat.discard(); return false; }
        rider.setYRot(yaw);
        rider.setYHeadRot(yaw);
        if (rider instanceof LivingEntity le) le.setYBodyRot(yaw);
        return true;
    }

    /** Height of the sitting surface: chairs/sofas know theirs; stairs and slabs sit at half height; other blocks on top. */
    public static double seatHeight(Level level, BlockPos pos, BlockState st) {
        if (st.getBlock() instanceof ChairBlock cb) return cb.seatHeight();
        if (st.getBlock() instanceof net.minecraft.world.level.block.StairBlock || st.getBlock() instanceof net.minecraft.world.level.block.SlabBlock) {
            var shape = st.getCollisionShape(level, pos);
            return shape.isEmpty() ? 0.5 : Math.min(0.5, shape.max(net.minecraft.core.Direction.Axis.Y));
        }
        var shape = st.getCollisionShape(level, pos);
        return shape.isEmpty() ? 0.3 : Math.max(0.3, Math.min(1.0, shape.max(net.minecraft.core.Direction.Axis.Y)));
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {}

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if ((!isVehicle() && tickCount > 5) || level().getBlockState(chair).isAir()) {
            ejectPassengers();
            discard();
        }
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity e, EntityDimensions d, float pt) { return Vec3.ZERO; }

    @Override
    protected void positionRider(Entity passenger, MoveFunction fn) {
        super.positionRider(passenger, fn);
        if (passenger instanceof NpcEntity npc) {
            npc.setYBodyRot(getYRot());
            npc.setYRot(getYRot());
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockState st = level().getBlockState(chair);
        if (!st.isAir()) {
            com.rpbusiness.business.Business biz = level() instanceof net.minecraft.server.level.ServerLevel sl ? com.rpbusiness.business.BusinessData.get(sl).at(chair) : null;
            BlockPos front = chair.relative(com.rpbusiness.server.BusinessManager.seatFacing(level(), biz, chair));
            if (level().getBlockState(front).getCollisionShape(level(), front).isEmpty())
                return Vec3.atBottomCenterOf(front);
            for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos p = chair.relative(d);
                if (level().getBlockState(p).getCollisionShape(level(), p).isEmpty()) return Vec3.atBottomCenterOf(p);
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) { chair = NbtUtils.readBlockPos(t, "chair").orElse(blockPosition()); }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) { t.put("chair", NbtUtils.writeBlockPos(chair)); }
}
