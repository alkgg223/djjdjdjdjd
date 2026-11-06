package com.arkragnarok.building;

import com.arkragnarok.registry.ModBuilding;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** the doorframe's master block: which door hangs in it, and the client side swing animation */
public class PieceBlockEntity extends BlockEntity {
    private int doorTier = -1;
    public float swing, swingO;

    public PieceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBuilding.PIECE_BE.get(), pos, state);
    }

    @Nullable
    public Tier doorTier() {
        return doorTier < 0 ? null : Tier.values()[doorTier];
    }

    public void setDoorTier(@Nullable Tier t) {
        doorTier = t == null ? -1 : t.ordinal();
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    void clientTick(BlockState s) {
        swingO = swing;
        float target = s.getValue(PieceBlock.DOOR) == DoorState.OPEN ? 1f : 0f;
        float d = target - swing;
        swing += Math.signum(d) * Math.min(Math.abs(d), 0.09f);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putInt("DoorTier", doorTier);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        doorTier = tag.contains("DoorTier") ? tag.getInt("DoorTier") : -1;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        CompoundTag t = super.getUpdateTag(reg);
        t.putInt("DoorTier", doorTier);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
