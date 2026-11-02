package com.gunsmod.gun;

import com.gunsmod.item.GunItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Lying down (the prone key, Z): the player crawls flat on the ground, the gun steadier. A gun with a bipod rests on it
 * when lying down, or when crouched with a block in front at chest height, and then barely moves.
 */
public final class Prone {
    private Prone() {}

    public static boolean isProne(Entity e) {
        if (e instanceof Player p) return p.getForcedPose() == Pose.SWIMMING;
        return e.getPose() == Pose.SWIMMING && !e.isInWater();
    }

    public static void set(Player p, boolean prone) {
        p.setForcedPose(prone ? Pose.SWIMMING : null);
    }

    /** nobody lies down in water, in a vehicle or while flying */
    public static boolean allowed(Player p) {
        return !p.isPassenger() && !p.isInWater() && !p.getAbilities().flying && !p.isFallFlying();
    }

    /** the gun in the hand has a bipod and rests on it */
    public static boolean bipodDown(Entity e) {
        if (!(e instanceof LivingEntity le)) return false;
        ItemStack st = le.getMainHandItem();
        if (!(st.getItem() instanceof GunItem) || !GunItem.attachments(st).has(Attachment.BIPOD)) return false;
        if (isProne(e)) return true;
        if (!e.isCrouching()) return false;
        Vec3 look = e.getLookAngle();
        BlockPos at = BlockPos.containing(e.getX() + look.x * 0.9, e.getY() + 0.9, e.getZ() + look.z * 0.9);
        return !e.level().getBlockState(at).getCollisionShape(e.level(), at).isEmpty();
    }
}
