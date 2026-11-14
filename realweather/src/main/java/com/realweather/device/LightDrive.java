package com.realweather.device;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The light drive: right-click to set its speed, from 1% of the speed of light to all of it, and switch it on.
 * Holding it switched on, look up at a world in the sky and walk forward: you leave the ground and fly through space
 * to it (out between the planets, you steer with where you look and the movement keys).
 */
public class LightDrive extends Item {
    public LightDrive(Properties props) {
        super(props);
    }

    public static int percent(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        int p = d == null ? 0 : d.copyTag().getInt("percent");
        return p <= 0 ? 10 : Mth.clamp(p, 1, 100);
    }

    public static boolean on(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d != null && d.copyTag().getBoolean("on");
    }

    public static void set(ItemStack s, int percent, boolean on) {
        CompoundTag t = new CompoundTag();
        t.putInt("percent", Mth.clamp(percent, 1, 100));
        t.putBoolean("on", on);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (level.isClientSide) {
            DeviceClientHooks.openDrive(hand == InteractionHand.OFF_HAND);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack s) {
        return on(s);
    }

    @Override
    public void appendHoverText(ItemStack s, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.realweather.light_drive.speed", percent(s)));
        lines.add(Component.translatable(on(s) ? "item.realweather.light_drive.on" : "item.realweather.light_drive.off"));
    }
}
