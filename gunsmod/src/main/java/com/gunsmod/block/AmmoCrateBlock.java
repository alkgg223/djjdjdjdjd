package com.gunsmod.block;

import com.gunsmod.Reg;
import com.gunsmod.item.GunItem;
import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Supply crate: use it while holding a gun to receive a box of matching ammunition (30 s cooldown). */
public class AmmoCrateBlock extends FacingBlock {
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    public AmmoCrateBlock(Properties props) {
        super(props, box(0.5, 0, 1.5, 15.5, 11.5, 14.5));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(AmmoCrateBlock::new);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof GunItem gun)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            long now = level.getGameTime();
            Long last = COOLDOWN.get(player.getUUID());
            if (last != null && now - last < 600 && !player.getAbilities().instabuild) {
                player.displayClientMessage(Component.translatable("gunsmod.msg.crate_wait", (600 - (now - last)) / 20), true);
            } else {
                COOLDOWN.put(player.getUUID(), now);
                ItemStack ammo = new ItemStack(Reg.AMMO_ITEMS.get(gun.type.ammo).get(), gun.type.ammo.isBox() ? 1 : 4);
                if (!player.getInventory().add(ammo)) player.drop(ammo, false);
                level.playSound(null, pos, Reg.sound("mag_in"), SoundSource.BLOCKS, 1f, 0.9f);
                player.displayClientMessage(Component.translatable("gunsmod.msg.crate_given", ammo.getHoverName()), true);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) player.displayClientMessage(Component.translatable("gunsmod.msg.crate_hint"), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
