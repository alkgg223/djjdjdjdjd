package com.realweather.device;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RadarBlockEntity extends BlockEntity {
    public RadarBlockEntity(BlockPos pos, BlockState state) {
        super(ModDevices.RADAR_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            DeviceClientHooks.radarLoaded(worldPosition, true);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) {
            DeviceClientHooks.radarLoaded(worldPosition, false);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && level.isClientSide) {
            DeviceClientHooks.radarLoaded(worldPosition, false);
        }
    }
}
