package com.rpbusiness.block;

import com.rpbusiness.core.ModBlockEntities;
import com.rpbusiness.core.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Fridge / freezer with a GeckoLib animated door or lid that opens for players and for NPC staff. */
public class AnimatedStorageBE extends StorageBE implements GeoBlockEntity {
    private static final RawAnimation OPEN = RawAnimation.begin().thenPlayAndHold("open");
    private static final RawAnimation CLOSE = RawAnimation.begin().thenPlayAndHold("close");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int npcOpenTicks;
    private boolean clientOpen, everOpened, sentOpen;

    public AnimatedStorageBE(BlockPos pos, BlockState state) { super(ModBlockEntities.ANIMATED_STORAGE.get(), pos, state); }

    @Override
    protected void onOpenersChanged() { updateOpen(); }

    /** An employee opens the door for a moment while taking or storing ingredients. */
    public void npcOpen(int ticks) {
        npcOpenTicks = Math.max(npcOpenTicks, ticks);
        updateOpen();
    }

    private void updateOpen() {
        if (level == null || level.isClientSide) return;
        boolean open = openers > 0 || npcOpenTicks > 0;
        if (open != sentOpen) {
            sentOpen = open;
            level.blockEvent(worldPosition, getBlockState().getBlock(), 1, open ? 1 : 0);
            level.playSound(null, worldPosition, open ? ModSounds.FRIDGE_OPEN.get() : ModSounds.FRIDGE_CLOSE.get(), SoundSource.BLOCKS, 0.6f, 1.0f);
        }
    }

    public void serverTick() {
        if (npcOpenTicks > 0 && --npcOpenTicks == 0) updateOpen();
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == 1) {
            clientOpen = param > 0;
            if (clientOpen) everOpened = true;
            return true;
        }
        return super.triggerEvent(id, param);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "door", 2, state -> {
            if (clientOpen) return state.setAndContinue(OPEN);
            return state.setAndContinue(everOpened ? CLOSE : IDLE);
        }));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
