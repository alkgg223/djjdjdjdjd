package com.gunsmod.block;

import com.gunsmod.Reg;
import com.gunsmod.item.VehicleItem;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Vehicle spawner: holds one vehicle item and keeps that vehicle in service. It is placed on top of the pad and is
 * only replaced after it has been destroyed (never just because it drove away).
 */
public class VehicleSpawnerBlockEntity extends BlockEntity {
    public static final int DELAY = 200;

    private ItemStack vehicle = ItemStack.EMPTY;
    @Nullable private UUID current;
    private long respawnAt;
    public int clientTicks;

    public VehicleSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(Reg.VEHICLE_SPAWNER_BE.get(), pos, state);
    }

    public ItemStack vehicle() {
        return vehicle;
    }

    /** a vehicle is set but none is out: the countdown / hologram is shown */
    public boolean waiting() {
        return !vehicle.isEmpty() && current == null;
    }

    public int secondsLeft() {
        return level == null ? 0 : (int) Math.max(0, (respawnAt - level.getGameTime() + 19) / 20);
    }

    public boolean hasVehicleOut() {
        return current != null;
    }

    /** put a vehicle on the pad; the first one comes out at once */
    public ItemStack setVehicle(ItemStack stack) {
        ItemStack old = vehicle;
        vehicle = stack.copyWithCount(1);
        respawnAt = 0;
        changed();
        return old;
    }

    public ItemStack takeVehicle() {
        ItemStack old = vehicle;
        vehicle = ItemStack.EMPTY;
        changed();
        return old;
    }

    public void tick() {
        if (level == null) return;
        if (level.isClientSide) {
            clientTicks++;
            return;
        }
        if (!waiting() || level.getGameTime() < respawnAt || level.getGameTime() % 10 != 0) return;
        Direction facing = getBlockState().hasProperty(FacingBlock.FACING) ? getBlockState().getValue(FacingBlock.FACING).getOpposite() : Direction.NORTH;
        Mob m = VehicleItem.create(level, vehicle, worldPosition.getX() + 0.5, worldPosition.getY() + 0.3, worldPosition.getZ() + 0.5, facing.toYRot());
        if (m == null) return;
        if (!level.noCollision(m, m.getBoundingBox())) {
            respawnAt = level.getGameTime() + 40;    // something is parked on the pad: try again soon
            return;
        }
        VehicleItem.link(m, worldPosition, worldPosition);
        level.addFreshEntity(m);
        current = m.getUUID();
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(Reg.HOLO.get(), m.getX(), m.getY() + 1, m.getZ(), 40, 1.4, 0.8, 1.4, 0.02);
            sl.playSound(null, worldPosition, Reg.sound("ui_confirm"), SoundSource.BLOCKS, 1.5f, 0.7f);
        }
        changed();
    }

    /** called by a vehicle when it is destroyed (or picked up): a new one comes out after the delay */
    public static void vehicleGone(Level level, @Nullable BlockPos pos, UUID id) {
        if (pos == null || !(level instanceof ServerLevel sl)) return;
        sl.getChunkAt(pos);
        if (sl.getBlockEntity(pos) instanceof VehicleSpawnerBlockEntity be && id.equals(be.current)) {
            be.current = null;
            be.respawnAt = sl.getGameTime() + DELAY;
            be.changed();
        }
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        if (!vehicle.isEmpty()) tag.put("Vehicle", vehicle.save(reg));
        if (current != null) tag.putUUID("Current", current);
        tag.putLong("RespawnAt", respawnAt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        vehicle = tag.contains("Vehicle") ? ItemStack.parse(reg, tag.getCompound("Vehicle")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        current = tag.hasUUID("Current") ? tag.getUUID("Current") : null;
        respawnAt = tag.getLong("RespawnAt");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        CompoundTag t = new CompoundTag();
        saveAdditional(t, reg);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
