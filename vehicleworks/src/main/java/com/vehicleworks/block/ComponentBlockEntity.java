package com.vehicleworks.block;

import com.vehicleworks.component.ComponentType;
import com.vehicleworks.component.PropertyDef;
import com.vehicleworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stores the player configuration ("Config") and runtime data ("Data": fuel, ammunition,
 * remote control bindings…) of a component. The same tags travel with the block when it is
 * assembled into a vehicle, so nothing is lost.
 */
public class ComponentBlockEntity extends BlockEntity {
    public static final String CONFIG = "Config";
    public static final String DATA = "Data";
    private CompoundTag config = new CompoundTag();
    private CompoundTag data = new CompoundTag();

    public ComponentBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COMPONENT.get(), pos, state);
        ComponentType type = typeOf(state);
        if (type != null) fillDefaults(type, config);
    }

    public static ComponentType typeOf(BlockState state) {
        return state.getBlock() instanceof ComponentBlock cb ? cb.type : null;
    }

    public static void fillDefaults(ComponentType type, CompoundTag config) {
        for (PropertyDef p : type.properties) if (!config.contains(p.key)) p.set(config, p.def);
    }

    public CompoundTag config() {
        return config;
    }

    public CompoundTag data() {
        return data;
    }

    public void setConfig(CompoundTag tag) {
        this.config = tag.copy();
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void setData(CompoundTag tag) {
        this.data = tag.copy();
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(CONFIG, config.copy());
        tag.put(DATA, data.copy());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        config = tag.getCompound(CONFIG).copy();
        data = tag.getCompound(DATA).copy();
        ComponentType type = typeOf(getBlockState());
        if (type != null) fillDefaults(type, config);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put(CONFIG, config.copy());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
