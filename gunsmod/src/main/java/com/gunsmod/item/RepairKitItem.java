package com.gunsmod.item;

import com.gunsmod.entity.VehicleEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The field repair kit: use it on a vehicle to mend its worst damaged part (engine, tracks / wheels, turret, gun,
 * fuel) a quarter at a time and patch the armour; sneak + use reads out the damage. It wears out.
 */
public class RepairKitItem extends Item {
    public RepairKitItem(Properties props) {
        super(props);
    }

    private static String pct(float v) {
        return Math.round(v * 100) + "%";
    }

    public static void use(VehicleEntity v, Player player, ItemStack kit) {
        if (player.getCooldowns().isOnCooldown(kit.getItem())) return;
        if (player.isShiftKeyDown()) {
            player.displayClientMessage(Component.translatable(v.hasTurret() ? "gunsmod.msg.zones" : "gunsmod.msg.zones_noturret",
                    pct(v.getHealth() / v.getMaxHealth()), pct(v.zone(VehicleEntity.Z_ENGINE)), pct(v.zone(VehicleEntity.Z_MOBILITY)),
                    pct(v.zone(VehicleEntity.Z_TURRET)), pct(v.zone(VehicleEntity.Z_GUN)), pct(v.zone(VehicleEntity.Z_FUEL))), true);
            return;
        }
        if (!v.repair()) {
            player.displayClientMessage(Component.translatable("gunsmod.msg.repair_none"), true);
            return;
        }
        v.level().playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.7f, 1.3f);
        player.getCooldowns().addCooldown(kit.getItem(), 30);
        if (!player.getAbilities().instabuild) kit.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        player.displayClientMessage(Component.translatable("gunsmod.msg.repaired", pct(v.getHealth() / v.getMaxHealth())), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("gunsmod.tip.repair1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("gunsmod.tip.repair2").withStyle(ChatFormatting.GRAY));
    }
}
