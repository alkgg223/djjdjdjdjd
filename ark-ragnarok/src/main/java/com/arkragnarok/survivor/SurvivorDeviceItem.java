package com.arkragnarok.survivor;

import com.arkragnarok.registry.ModEntities;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** SURVIVOR COMMAND DEVICE: calls a new autonomous survivor to your side (one every 10 seconds) */
public class SurvivorDeviceItem extends Item {
    public SurvivorDeviceItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (!level.isClientSide) {
            Survivor s = ModEntities.SURVIVOR.get().create(level);
            if (s != null) {
                Vec3 at = player.position().add(player.getLookAngle().multiply(2, 0, 2));
                s.moveTo(at.x, player.getY(), at.z, player.getYRot() + 180f, 0f);
                s.init(level.random.nextInt(1000));
                level.addFreshEntity(s);
                player.displayClientMessage(Component.translatable("msg.arkragnarok.survivor_called", s.getName()).withStyle(ChatFormatting.AQUA), true);
            }
            player.getCooldowns().addCooldown(this, 200);
        }
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.arkragnarok.survivor_command_device.desc").withStyle(ChatFormatting.GRAY));
    }
}
