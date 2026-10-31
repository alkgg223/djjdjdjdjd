package com.realweather.device;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MonitorBlockEntity extends BlockEntity {
    public MonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ModDevices.MONITOR_BE.get(), pos, state);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) {
            DeviceClientHooks.monitorRemoved(worldPosition);
        }
    }
}
