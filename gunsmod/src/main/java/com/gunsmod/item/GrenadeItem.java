package com.gunsmod.item;

import com.gunsmod.entity.GrenadeEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class GrenadeItem extends Item {
    public final boolean smoke;

    public GrenadeItem(boolean smoke, Properties props) {
        super(props);
        this.smoke = smoke;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.7f, 0.5f);
        if (!level.isClientSide) {
            GrenadeEntity g = new GrenadeEntity(level, player, smoke);
            Vec3 eye = player.getEyePosition();
            g.setPos(eye.x, eye.y - 0.1, eye.z);
            g.shootFromRotation(player, player.getXRot() - 8f, player.getYRot(), 0f, 1.1f, 1.0f);
            level.addFreshEntity(g);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        player.getCooldowns().addCooldown(this, 15);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable(smoke ? "gunsmod.desc.smoke_grenade" : "gunsmod.desc.frag_grenade").withStyle(ChatFormatting.GRAY));
    }
}
